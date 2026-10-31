package com.arbdev.devbox.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.arbdev.devbox.BuildConfig
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("size") val size: Long = 0L
)

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val apkSizeMb: Double = 0.0,
    val expectedSizeBytes: Long = 0L
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val info: UpdateInfo) : UpdateStatus()
    data class Downloading(val progressPercent: Int) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

object UpdateManager {
    private const val GITHUB_REPO = "arbdevai/devbox"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    var lastCheckedInfo: UpdateInfo? = null
        private set

    private var lastUpdateCheckTime = 0L

    fun cleanupOldApks(context: Context, keepVersion: String? = null) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val flavorTag = if (com.arbdev.devbox.FlavorConfig.IS_ROOT_FLAVOR) "root" else "noroot"
            val keepFileName = if (keepVersion != null) "devbox-$keepVersion-$flavorTag.apk" else null

            downloadDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".part") || file.name.startsWith("devbox-"))
            }?.forEach { file ->
                if (keepFileName == null || file.name != keepFileName) {
                    val sizeMb = file.length() / (1024.0 * 1024.0)
                    val deleted = file.delete()
                    AppLogger.i("UpdateManager", "Membersihkan file APK usang: ${file.name} (%.2f MB, terhapus: $deleted)".format(sizeMb))
                }
            }
        } catch (e: Exception) {
            AppLogger.w("UpdateManager", "Gagal membersihkan APK lama: ${e.message}")
        }
    }

    suspend fun checkForUpdates(context: Context? = null, force: Boolean = false): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = lastCheckedInfo
        if (!force && cached != null && (now - lastUpdateCheckTime < 15 * 60 * 1000L)) {
            return@withContext Result.success(cached)
        }
        lastUpdateCheckTime = now
        _updateStatus.value = UpdateStatus.Checking
        AppLogger.i("UpdateManager", "Memeriksa pembaruan dari $API_URL...")

        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "DevBox-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful || body.isBlank()) {
                val err = "Gagal menghubungi GitHub API (HTTP ${response.code})"
                AppLogger.w("UpdateManager", err)
                _updateStatus.value = UpdateStatus.Error(err)
                return@withContext Result.failure(Exception(err))
            }

            val release = json.decodeFromString<GitHubReleaseDto>(body)
            val latestTag = release.tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

            AppLogger.i("UpdateManager", "Versi saat ini: $currentVersion, Versi rilis: $latestTag")

            val isNewer = isVersionNewer(latestTag, currentVersion)
            val targetFlavorKeyword = if (com.arbdev.devbox.FlavorConfig.IS_ROOT_FLAVOR) "-root-" else "-noroot-"
            val apkAsset = release.assets.firstOrNull { it.name.contains(targetFlavorKeyword) && it.name.endsWith(".apk") }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }

            val downloadUrl = apkAsset?.downloadUrl ?: ""
            val sizeMb = if (apkAsset != null && apkAsset.size > 0) apkAsset.size / (1024.0 * 1024.0) else 0.0
            val expectedBytes = apkAsset?.size ?: 0L

            val info = UpdateInfo(
                hasUpdate = isNewer && downloadUrl.isNotBlank(),
                latestVersion = release.tagName,
                currentVersion = "v${BuildConfig.VERSION_NAME}",
                releaseNotes = release.body,
                downloadUrl = downloadUrl,
                apkSizeMb = sizeMb,
                expectedSizeBytes = expectedBytes
            )
            lastCheckedInfo = info

            if (info.hasUpdate) {
                _updateStatus.value = UpdateStatus.Available(info)
                if (context != null) {
                    cleanupOldApks(context, keepVersion = info.latestVersion)
                }
            } else {
                _updateStatus.value = UpdateStatus.Idle
                if (context != null) {
                    cleanupOldApks(context, keepVersion = null)
                }
            }

            Result.success(info)
        } catch (e: Exception) {
            val msg = "Gagal cek update: ${e.message}"
            AppLogger.e("UpdateManager", msg, e)
            _updateStatus.value = UpdateStatus.Error(msg)
            Result.failure(e)
        }
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
            val length = maxOf(remoteParts.size, localParts.size)

            for (i in 0 until length) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (_: Exception) {}
        return remote != local && remote.isNotBlank()
    }

    private fun isApkValid(context: Context, apkFile: File, expectedBytes: Long = 0L): Boolean {
        if (!apkFile.exists() || !apkFile.isFile || apkFile.length() < 10_000_000) return false
        if (expectedBytes > 0L && Math.abs(apkFile.length() - expectedBytes) > 2048L) {
            AppLogger.w("UpdateManager", "Ukuran file APK tidak sesuai: ${apkFile.length()} vs expected $expectedBytes")
            return false
        }
        return try {
            val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES or PackageManager.GET_ACTIVITIES)
            }
            if (pkg == null) {
                AppLogger.w("UpdateManager", "Gagal mem-parse archive APK (${apkFile.absolutePath})")
                return false
            }
            if (pkg.packageName != context.packageName) {
                AppLogger.w("UpdateManager", "Flavor APK tidak cocok: ${pkg.packageName} vs ${context.packageName}")
                return false
            }
            val remoteVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            if (remoteVersionCode <= BuildConfig.VERSION_CODE) {
                AppLogger.w("UpdateManager", "APK yang ada bukan versi lebih baru: $remoteVersionCode <= ${BuildConfig.VERSION_CODE}")
                return false
            }
            true
        } catch (e: Exception) {
            AppLogger.w("UpdateManager", "Validasi APK gagal: ${e.message}")
            false
        }
    }

    suspend fun startDownload(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir

        val flavorTag = if (com.arbdev.devbox.FlavorConfig.IS_ROOT_FLAVOR) "root" else "noroot"
        val fileName = "devbox-${info.latestVersion}-$flavorTag.apk"
        val apkFile = File(downloadDir, fileName)
        val partFile = File(downloadDir, "$fileName.part")

        // Jika APK yang sudah terunduh sebelumnya memang valid dan lebih baru, langsung ke instalasi
        if (isApkValid(context, apkFile, info.expectedSizeBytes)) {
            AppLogger.i("UpdateManager", "File APK rilis sudah ada dan valid (${apkFile.length()} bytes). Langsung ke instalasi.")
            _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
            installApk(context, apkFile)
            return@withContext
        }

        // Hapus sisa file lama yang tidak lengkap atau korup
        if (apkFile.exists()) apkFile.delete()
        if (partFile.exists()) partFile.delete()

        AppLogger.i("UpdateManager", "Memulai unduhan streaming OkHttp dari: ${info.downloadUrl}")
        _updateStatus.value = UpdateStatus.Downloading(0)

        try {
            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "DevBox-Android")
                .header("Accept", "application/octet-stream")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IOException("Response body kosong")
                val totalBytes = if (info.expectedSizeBytes > 0L) info.expectedSizeBytes else body.contentLength()

                var downloadedBytes = 0L
                var lastProgress = 0
                body.byteStream().use { input ->
                    partFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                val progress = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    _updateStatus.value = UpdateStatus.Downloading(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Verifikasi integritas APK yang baru diunduh sebelum direname
            if (isApkValid(context, partFile, info.expectedSizeBytes)) {
                if (partFile.renameTo(apkFile)) {
                    AppLogger.i("UpdateManager", "Unduhan selesai dan terverifikasi valid (${apkFile.length()} bytes).")
                    _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
                    installApk(context, apkFile)
                } else {
                    throw IOException("Gagal mengubah nama file part menjadi apk")
                }
            } else {
                throw IOException("Verifikasi paket APK gagal atau file korup (${partFile.length()} bytes).")
            }
        } catch (e: Exception) {
            val err = "Unduhan APK gagal: ${e.message}"
            AppLogger.e("UpdateManager", err, e)
            partFile.delete()
            apkFile.delete()
            _updateStatus.value = UpdateStatus.Error(err)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() < 10_000_000) {
                val err = "File APK tidak ditemukan atau tidak lengkap (${apkFile.length()} bytes)"
                AppLogger.e("UpdateManager", err)
                _updateStatus.value = UpdateStatus.Error(err)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    AppLogger.w("UpdateManager", "Izin REQUEST_INSTALL_PACKAGES belum diberikan. Membuka pengaturan.")
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            AppLogger.i("UpdateManager", "Membuka installer FileProvider untuk: ${apkFile.absolutePath}")
            val authority = "${context.packageName}.provider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            val err = "Gagal memicu instalasi APK: ${e.message}"
            AppLogger.e("UpdateManager", err, e)
            _updateStatus.value = UpdateStatus.Error(err)
        }
    }
}

package com.arbdev.devbox.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.arbdev.devbox.BuildConfig
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val apkSizeMb: Double = 0.0
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    fun cleanupOldApks(context: Context, keepVersion: String? = null) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val keepFileName = if (keepVersion != null) "devbox-$keepVersion.apk" else null

            downloadDir.listFiles { file -> file.isFile && (file.name.endsWith(".apk") || file.name.startsWith("devbox-")) }?.forEach { file ->
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

    suspend fun checkForUpdates(context: Context? = null): Result<UpdateInfo> = withContext(Dispatchers.IO) {
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
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: release.assets.firstOrNull()

            val downloadUrl = apkAsset?.downloadUrl ?: ""
            val sizeMb = if (apkAsset != null && apkAsset.size > 0) apkAsset.size / (1024.0 * 1024.0) else 0.0

            val info = UpdateInfo(
                hasUpdate = isNewer && downloadUrl.isNotBlank(),
                latestVersion = release.tagName,
                currentVersion = "v${BuildConfig.VERSION_NAME}",
                releaseNotes = release.body,
                downloadUrl = downloadUrl,
                apkSizeMb = sizeMb
            )

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
        } catch (_: Exception) {
        }
        return remote != local && remote.isNotBlank()
    }

    suspend fun startDownload(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        cleanupOldApks(context, keepVersion = info.latestVersion)

        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir

        val fileName = "devbox-${info.latestVersion}.apk"
        val apkFile = File(downloadDir, fileName)

        // Cek anti double-download: Jika file sudah pernah diunduh dan valid
        if (apkFile.exists() && apkFile.length() > 5_000_000) {
            AppLogger.i("UpdateManager", "File APK rilis sudah ada di lokal (${apkFile.length()} bytes). Langsung ke instalasi.")
            _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
            installApk(context, apkFile)
            return@withContext
        }

        if (apkFile.exists()) {
            apkFile.delete()
        }

        AppLogger.i("UpdateManager", "Memulai unduhan APK dari: ${info.downloadUrl}")
        _updateStatus.value = UpdateStatus.Downloading(0)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl)).apply {
            setTitle("DevBox ${info.latestVersion}")
            setDescription("Mengunduh pembaruan DevBox...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(apkFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)
        activeDownloadId = downloadId

        // Pantau progres unduhan
        var downloading = true
        while (downloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                if (bytesTotal > 0) {
                    val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                    _updateStatus.value = UpdateStatus.Downloading(progress)
                }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloading = false
                        AppLogger.i("UpdateManager", "Unduhan selesai. File: ${apkFile.absolutePath}")
                        _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
                        installApk(context, apkFile)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloading = false
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val err = "Unduhan gagal (alasan code: $reason)"
                        AppLogger.e("UpdateManager", err)
                        _updateStatus.value = UpdateStatus.Error(err)
                    }
                }
            }
            cursor.close()
            delay(800)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
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

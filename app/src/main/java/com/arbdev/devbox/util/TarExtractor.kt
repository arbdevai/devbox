package com.arbdev.devbox.util

import android.system.Os
import android.system.OsConstants
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * Utilitas ekstraksi tar.gz murni berbasis Java/Kotlin.
 * Menghilangkan dependensi pada binary toybox tar bawaan Android yang memblokir
 * hard links (Permission denied) dan absolute symlinks (not under target dir).
 */
object TarExtractor {

    suspend fun extractTarGz(
        archiveFile: File,
        targetDir: File,
        onProgress: ((Float, String) -> Unit)? = null,
        onLogLine: ((String) -> Unit)? = null
    ): Result<Unit> {
        return try {
            targetDir.mkdirs()
            val totalBytes = archiveFile.length()
            var entryCount = 0
            var lastUpdateMs = System.currentTimeMillis()

            onLogLine?.invoke("[TarExtractor] Memulai ekstraksi archive ke ${targetDir.absolutePath}...")

            FileInputStream(archiveFile).use { fis ->
                GZIPInputStream(fis.buffered(128 * 1024)).use { gzis ->
                    TarArchiveInputStream(gzis).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.removePrefix("./").trim()
                            if (entryName.isNotEmpty()) {
                                val destFile = File(targetDir, entryName)

                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else if (entry.isSymbolicLink) {
                                    destFile.parentFile?.mkdirs()
                                    safeUnlink(destFile)
                                    try {
                                        Os.symlink(entry.linkName, destFile.absolutePath)
                                    } catch (e: Exception) {
                                        AppLogger.w("TarExtractor", "Gagal membuat symlink $entryName -> ${entry.linkName}: ${e.message}")
                                    }
                                } else if (entry.isLink) {
                                    // Hard link: Di unprivileged userspace Android, link() diblokir kernel.
                                    // Kita salin file jika sumber sudah ada, atau buat symlink sebagai fail-safe.
                                    destFile.parentFile?.mkdirs()
                                    safeUnlink(destFile)
                                    val srcFile = File(targetDir, entry.linkName.removePrefix("./").trim())
                                    if (srcFile.exists()) {
                                        try {
                                            srcFile.copyTo(destFile, overwrite = true)
                                        } catch (_: Exception) {
                                            try {
                                                Os.symlink(entry.linkName, destFile.absolutePath)
                                            } catch (_: Exception) {}
                                        }
                                    } else {
                                        try {
                                            Os.symlink(entry.linkName, destFile.absolutePath)
                                        } catch (_: Exception) {}
                                    }
                                } else {
                                    // Normal file
                                    destFile.parentFile?.mkdirs()
                                    safeUnlink(destFile)
                                    FileOutputStream(destFile).use { fos ->
                                        tarIn.copyTo(fos)
                                    }
                                    if (entry.mode > 0) {
                                        try {
                                            Os.chmod(destFile.absolutePath, entry.mode)
                                        } catch (_: Exception) {}
                                    }
                                }

                                entryCount++
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateMs > 350) {
                                    val progressMsg = "Mengekstrak berkas #$entryCount (${entryName.take(35)}...)"
                                    onProgress?.invoke(0.5f, progressMsg)
                                    lastUpdateMs = now
                                }
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
            onLogLine?.invoke("[TarExtractor] Ekstraksi selesai: $entryCount berkas berhasil dipasang.")
            Result.success(Unit)
        } catch (e: Exception) {
            val err = "Gagal ekstraksi tar.gz: ${e.localizedMessage ?: e.message}"
            AppLogger.e("TarExtractor", err, e)
            onLogLine?.invoke("[TarExtractor] ERROR: $err")
            Result.failure(e)
        }
    }

    private fun safeUnlink(file: File) {
        try {
            if (isSymlink(file)) {
                Os.unlink(file.absolutePath)
            } else if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) {}
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val stat = Os.lstat(file.absolutePath)
            OsConstants.S_ISLNK(stat.st_mode)
        } catch (_: Exception) {
            false
        }
    }
}

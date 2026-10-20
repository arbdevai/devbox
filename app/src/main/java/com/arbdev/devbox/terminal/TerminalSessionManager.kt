package com.arbdev.devbox.terminal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Sesi Terminal Native murni Kotlin berbasis Linux PTY (/usr/bin/script).
 * Tanpa library C/JNI luar, tanpa ketergantungan Termux, zero input latency.
 */
class NativeTerminalSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "Sesi 1"
) {
    var isRunning by mutableStateOf(false)
    var currentTitle by mutableStateOf("coder@devbox:~")
    var currentDir by mutableStateOf("/home/coder")

    val buffer = TerminalGridBuffer(maxCols = 80, maxRows = 30)

    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var readerJob: Job? = null

    private val sessionScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    var onClipboardCopy: ((String) -> Unit)? = null

    init {
        initializeProcess()
    }

    private fun findSuPath(): String {
        val candidates = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "su"
        )
        for (candidate in candidates) {
            if (candidate == "su" || File(candidate).exists()) {
                return candidate
            }
        }
        return "su"
    }

    fun initializeProcess() {
        try {
            val targetRootfs = ChrootManager.activeRootfs
            val suBinary = findSuPath()

            AppLogger.i("NativeTerminal", "Meluncurkan sesi terminal [$name] di $targetRootfs via $suBinary")

            // Menggunakan /usr/bin/script untuk mengalokasikan PTY sejati tanpa JNI,
            // dan setpriv untuk menjalankan shell sebagai user coder (UID 1000).
            val chrootCmd = "mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp' 2>/dev/null; " +
                    "mountpoint -q '$targetRootfs/dev' || mount --bind /dev '$targetRootfs/dev'; " +
                    "mountpoint -q '$targetRootfs/dev/pts' || mount -t devpts devpts '$targetRootfs/dev/pts' 2>/dev/null || mount --bind /dev/pts '$targetRootfs/dev/pts'; " +
                    "mountpoint -q '$targetRootfs/proc' || mount -t proc proc '$targetRootfs/proc'; " +
                    "mountpoint -q '$targetRootfs/sys' || mount -t sysfs sysfs '$targetRootfs/sys'; " +
                    "mountpoint -q '$targetRootfs/mnt/sdcard' || mount --bind /sdcard '$targetRootfs/mnt/sdcard' 2>/dev/null; " +
                    "chown -R 1000:1000 '$targetRootfs/home/coder' 2>/dev/null; chmod -R u+rwX '$targetRootfs/home/coder' 2>/dev/null; " +
                    "cd '$targetRootfs' && exec chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color COLORTERM=truecolor PATH=/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /usr/bin/setpriv --reuid 1000 --regid 1000 --init-groups /bin/bash -c 'cd /home/coder 2>/dev/null || cd /; if [ -x /usr/bin/script ]; then exec /usr/bin/script -qefc \"stty rows 30 cols 80 2>/dev/null; exec /bin/bash -l\" /dev/null; else exec /bin/bash -l; fi'"

            val pb = ProcessBuilder(suBinary, "-c", chrootCmd)
            pb.redirectErrorStream(true) // Gabungkan stderr ke stdout stream
            val proc = pb.start()
            process = proc
            outputStream = proc.outputStream
            inputStream = proc.inputStream

            isRunning = true
            AppLogger.i("NativeTerminal", "Sesi [$name] aktif (PID terhubung, PTY siap)")

            // Mulai membaca output secara asynchronous di background IO thread
            readerJob = sessionScope.launch {
                val byteBuffer = ByteArray(4096)
                val stream = inputStream ?: return@launch
                try {
                    while (isActive && isRunning) {
                        val count = stream.read(byteBuffer)
                        if (count == -1) break
                        if (count > 0) {
                            buffer.write(byteBuffer, 0, count)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("NativeTerminal", "Stream pembaca sesi [$name] terputus: ${e.message}")
                } finally {
                    mainHandler.post {
                        isRunning = false
                        val exitCode = try { proc.exitValue() } catch (_: Exception) { -1 }
                        AppLogger.i("NativeTerminal", "Sesi [$name] telah selesai (exit code: $exitCode)")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("NativeTerminal", "Gagal memulai sesi PTY [$name]: ${e.message}", e)
            isRunning = false
        }
    }

    fun sendText(text: String) {
        val out = outputStream ?: return
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            AppLogger.e("NativeTerminal", "Gagal kirim teks ke [$name]: ${e.message}")
        }
    }

    fun sendBytes(bytes: ByteArray) {
        val out = outputStream ?: return
        try {
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            AppLogger.e("NativeTerminal", "Gagal kirim bytes ke [$name]: ${e.message}")
        }
    }

    fun cancelActiveProcess() {
        sendBytes(byteArrayOf(3)) // Send Ctrl+C (0x03)
    }

    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        sendText("$trimmed\r")
    }

    fun getDisplayDir(): String {
        return when {
            currentDir == "/home/coder" -> "~"
            currentDir.startsWith("/home/coder/") -> "~" + currentDir.removePrefix("/home/coder")
            else -> currentDir
        }
    }

    fun finish() {
        isRunning = false
        readerJob?.cancel()
        try {
            outputStream?.close()
            inputStream?.close()
            process?.destroy()
        } catch (_: Exception) {}
    }
}

object TerminalSessionManager {
    val sessions = mutableStateListOf<NativeTerminalSession>()
    val activeIndex = mutableIntStateOf(0)

    init {
        createNewSession("Sesi 1")
    }

    fun getActiveSession(): NativeTerminalSession {
        if (sessions.isEmpty()) {
            createNewSession("Sesi 1")
        }
        val safeIdx = activeIndex.intValue.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
        return sessions[safeIdx]
    }

    fun createNewSession(customName: String? = null): NativeTerminalSession {
        val sessionNumber = sessions.size + 1
        val name = customName ?: "Sesi $sessionNumber"
        val newSession = NativeTerminalSession(name = name)
        sessions.add(newSession)
        activeIndex.intValue = sessions.size - 1
        return newSession
    }

    fun closeSession(index: Int) {
        if (sessions.size <= 1) return // Jangan tutup sesi terakhir
        if (index in sessions.indices) {
            val sessionToClose = sessions[index]
            sessionToClose.finish()
            sessions.removeAt(index)

            val newActive = activeIndex.intValue.coerceIn(0, sessions.size - 1)
            activeIndex.intValue = newActive
        }
    }

    fun closeActiveSession() {
        closeSession(activeIndex.intValue)
    }

    fun destroyAll() {
        sessions.forEach { it.finish() }
        sessions.clear()
        activeIndex.intValue = 0
    }

    fun terminateAll() {
        destroyAll()
    }
}

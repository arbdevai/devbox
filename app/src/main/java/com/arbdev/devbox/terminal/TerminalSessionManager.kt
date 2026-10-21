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
import kotlinx.coroutines.delay
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
    initialName: String = "Sesi 1"
) {
    var name by mutableStateOf(initialName)
    var isCustomName by mutableStateOf(false)
    var isRunning by mutableStateOf(false)
    var currentTitle by mutableStateOf("coder@devbox:~")
    var currentDir by mutableStateOf("/home/coder")
    var activeAgentName by mutableStateOf<String?>(null)
    var isAgentRunning by mutableStateOf(false)

    val buffer = TerminalGridBuffer(maxCols = 80, maxRows = 30)

    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var readerJob: Job? = null
    private var processMonitorJob: Job? = null

    private val sessionScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    var onClipboardCopy: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null

    init {
        buffer.onTitleChanged = { title ->
            mainHandler.post {
                currentTitle = title
                if (!isCustomName && title.isNotBlank()) {
                    name = cleanSessionTitle(title)
                }
            }
        }
        buffer.onClipboardCopy = { text ->
            mainHandler.post {
                onClipboardCopy?.invoke(text)
            }
        }
        buffer.onBell = {
            mainHandler.post {
                onBell?.invoke()
            }
        }
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
                    "mountpoint -q '$targetRootfs/dev' || { mount --bind /dev '$targetRootfs/dev' && mount --make-private '$targetRootfs/dev'; }; " +
                    "mountpoint -q '$targetRootfs/dev/pts' || mount -t devpts devpts '$targetRootfs/dev/pts' 2>/dev/null || mount --bind /dev/pts '$targetRootfs/dev/pts'; " +
                    "mountpoint -q '$targetRootfs/proc' || mount -t proc proc '$targetRootfs/proc'; " +
                    "mountpoint -q '$targetRootfs/sys' || mount -t sysfs sysfs '$targetRootfs/sys'; " +
                    "mountpoint -q '$targetRootfs/mnt/sdcard' || { mount --bind /sdcard '$targetRootfs/mnt/sdcard' 2>/dev/null && mount --make-private '$targetRootfs/mnt/sdcard'; }; " +
                    "mount --make-rprivate '$targetRootfs' 2>/dev/null; " +
                    "chown -R 1000:1000 '$targetRootfs/home/coder' 2>/dev/null; chmod -R u+rwX '$targetRootfs/home/coder' 2>/dev/null; " +
                    "cd '$targetRootfs' && exec chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color COLORTERM=truecolor DEVBOX=1 DEVBOX_SESSION_ID='$id' PATH=/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /usr/bin/setpriv --reuid 1000 --regid 1000 --init-groups /bin/bash -c 'cd /home/coder 2>/dev/null || cd /; if [ -x /usr/bin/script ]; then exec /usr/bin/script -qefc \"stty rows 30 cols 80 2>/dev/null; exec /bin/bash -l\" /dev/null; else exec /bin/bash -l; fi'"

            val pb = ProcessBuilder(suBinary, "-c", chrootCmd)
            pb.redirectErrorStream(true) // Gabungkan stderr ke stdout stream
            val proc = pb.start()
            process = proc
            outputStream = proc.outputStream
            inputStream = proc.inputStream

            isRunning = true
            AppLogger.i("NativeTerminal", "Sesi [$name] aktif (PID terhubung, PTY siap, SID: $id)")

            // Pantau proses aktif / agent CLI (agy, python, node, dll) di sesi ini (dithrottle 30s, silent mode agar hemat baterai/CPU & tidak spam su)
            val sessionId = id
            processMonitorJob = sessionScope.launch {
                while (isActive && isRunning) {
                    kotlinx.coroutines.delay(30000)
                    try {
                        val checkCmd = "for p in /proc/[0-9]*; do " +
                                "if [ \"\$(readlink \$p/root 2>/dev/null)\" = '$targetRootfs' ]; then " +
                                "if grep -z '^DEVBOX_SESSION_ID=$sessionId' \"\$p/environ\" 2>/dev/null >/dev/null; then " +
                                "comm=\$(cat \$p/comm 2>/dev/null); " +
                                "if [ \"\$comm\" != 'script' ] && [ \"\$comm\" != 'bash' ] && [ \"\$comm\" != 'stty' ] && [ \"\$comm\" != 'setpriv' ]; then " +
                                "echo \"\$comm\"; break; " +
                                "fi; fi; fi; done"
                        val res = com.arbdev.devbox.data.RootExecutor.executeSync(checkCmd, silent = true)
                        val detected = res.getOrNull()?.stdout?.trim().orEmpty()
                        mainHandler.post {
                            if (detected.isNotBlank()) {
                                activeAgentName = detected
                                isAgentRunning = true
                            } else {
                                activeAgentName = null
                                isAgentRunning = false
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

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

    private var lastCols = 0
    private var lastRows = 0

    fun sendText(text: String) {
        if (!isRunning || outputStream == null) {
            AppLogger.w("NativeTerminal", "Sesi [$name] mati/terputus. Meluncurkan ulang sesi...")
            initializeProcess()
        }
        sessionScope.launch(Dispatchers.IO) {
            val out = outputStream ?: return@launch
            try {
                val bytes = text.toByteArray(Charsets.UTF_8)
                out.write(bytes)
                out.flush()
            } catch (e: Exception) {
                AppLogger.e("NativeTerminal", "Gagal kirim teks ke [$name]: ${e.message}")
            }
        }
    }

    fun sendBytes(bytes: ByteArray) {
        if (!isRunning || outputStream == null) {
            AppLogger.w("NativeTerminal", "Sesi [$name] mati/terputus. Meluncurkan ulang sesi...")
            initializeProcess()
        }
        sessionScope.launch(Dispatchers.IO) {
            val out = outputStream ?: return@launch
            try {
                out.write(bytes)
                out.flush()
            } catch (e: Exception) {
                AppLogger.e("NativeTerminal", "Gagal kirim bytes ke [$name]: ${e.message}")
            }
        }
    }

    fun pasteText(text: String) {
        // Normalisasi karakter baris baru dan kirim teks murni langsung ke PTY (persis seperti Termux).
        val sanitized = text.replace("\r\n", "\r").replace("\n", "\r")
        sendText(sanitized)
    }

    fun sendMouseEvent(button: Int, col: Int, row: Int, isPress: Boolean) {
        if (!buffer.isMouseTrackingEnabled) return
        val c = (col + 1).coerceAtLeast(1)
        val r = (row + 1).coerceAtLeast(1)
        if (buffer.isSgrMouseMode) {
            val action = if (isPress) 'M' else 'm'
            sendText("\u001B[<$button;$c;${r}$action")
        } else {
            if (isPress) {
                val b = (button + 32).toChar()
                val cx = (c + 32).coerceAtMost(255).toChar()
                val cy = (r + 32).coerceAtMost(255).toChar()
                sendText("\u001B[M$b$cx$cy")
            }
        }
    }

    private var resizeJob: Job? = null

    fun updateWindowSize(cols: Int, rows: Int) {
        if (cols == lastCols && rows == lastRows) return
        lastCols = cols
        lastRows = rows
        buffer.resize(cols, rows)
        val pid = getProcessPid() ?: return

        // Debounce: batalkan pemanggilan stty sebelumnya jika ukuran berubah cepat (anti-fork explosion Magisk)
        resizeJob?.cancel()
        resizeJob = sessionScope.launch(Dispatchers.IO) {
            delay(150)
            try {
                val cmd = "for p in \$(pgrep -P $pid 2>/dev/null); do " +
                        "for fd in /proc/\$p/fd/*; do " +
                        "target=\$(readlink \$fd 2>/dev/null); " +
                        "if [[ \$target == /dev/pts/* ]]; then " +
                        "stty -F \"\$target\" rows $rows cols $cols 2>/dev/null; " +
                        "fi; done; done"
                com.arbdev.devbox.data.RootExecutor.executeSync(cmd, silent = true)
            } catch (_: Exception) {}
        }
    }

    fun getProcessPid(): Long? {
        val proc = process ?: return null
        return try {
            val method = proc.javaClass.getMethod("pid")
            method.invoke(proc) as? Long
        } catch (_: Exception) {
            try {
                val field = proc.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                (field.getInt(proc)).toLong()
            } catch (_: Exception) { null }
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
        processMonitorJob?.cancel()
        val sessionId = id
        val targetRootfs = ChrootManager.activeRootfs
        val suBinary = findSuPath()

        // Matikan seluruh pohon proses turunan (agy, python, node, script, bash) di dalam chroot
        sessionScope.launch(Dispatchers.IO) {
            try {
                val killCmd = "for p in /proc/[0-9]*; do " +
                        "if [ \"\$(readlink \$p/root 2>/dev/null)\" = '$targetRootfs' ]; then " +
                        "if grep -z '^DEVBOX_SESSION_ID=$sessionId' \"\$p/environ\" 2>/dev/null >/dev/null; then " +
                        "kill -9 \"\$(basename \$p)\" 2>/dev/null; " +
                        "fi; fi; done"
                com.arbdev.devbox.data.RootExecutor.executeSync(killCmd, silent = true)
            } catch (_: Exception) {}
        }

        try {
            outputStream?.close()
            inputStream?.close()
            process?.destroy()
        } catch (_: Exception) {}
    }

    companion object {
        fun cleanSessionTitle(raw: String): String {
            val clean = raw.trim()
            val afterColon = if (clean.contains(":")) clean.substringAfter(":").trim() else clean
            val afterAt = if (afterColon.contains("@")) afterColon.substringAfter("@").trim() else afterColon
            val folder = if (afterAt.contains("/")) afterAt.substringAfterLast("/").trim() else afterAt
            return when {
                folder.isBlank() || folder == "~" -> "bash"
                folder.length > 14 -> folder.take(14)
                else -> folder
            }
        }
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
        val newSession = NativeTerminalSession(initialName = name)
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

            val current = activeIndex.intValue
            when {
                index < current -> activeIndex.intValue = current - 1
                index == current -> activeIndex.intValue = current.coerceAtMost(sessions.size - 1)
            }
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

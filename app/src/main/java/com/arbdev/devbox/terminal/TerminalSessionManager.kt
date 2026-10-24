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
                if (title.contains(":")) {
                    val rawPath = title.substringAfter(":").trim()
                    when {
                        rawPath == "~" -> currentDir = "/home/coder"
                        rawPath.startsWith("~/") -> currentDir = "/home/coder/" + rawPath.removePrefix("~/")
                        rawPath.startsWith("/") -> currentDir = rawPath
                    }
                }
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
        buffer.onSendResponse = { responseText ->
            sessionScope.launch(Dispatchers.IO) {
                try {
                    val out = outputStream ?: return@launch
                    out.write(responseText.toByteArray(Charsets.UTF_8))
                    out.flush()
                } catch (_: Exception) {}
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

            // Pastikan lingkungan user coder, passwordless sudo, direktori ~/projects, dan .bashrc modern siap
            ChrootManager.ensureEnvironmentReadySync(targetRootfs)

            // Menggunakan /usr/bin/script untuk mengalokasikan PTY sejati tanpa JNI,
            // dan menjalankan login shell interaktif dengan privilege root dan environment user coder.
            val initRows = if (lastRows > 0) lastRows else 30
            val initCols = if (lastCols > 0) lastCols else 80

            val chrootCmd = "mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp' 2>/dev/null; " +
                    "chmod 1777 '$targetRootfs/tmp' 2>/dev/null; " +
                    "mountpoint -q '$targetRootfs/dev' || { mount --bind /dev '$targetRootfs/dev' 2>/dev/null && mount --make-private '$targetRootfs/dev' 2>/dev/null; }; " +
                    "mountpoint -q '$targetRootfs/dev/pts' || mount --bind /dev/pts '$targetRootfs/dev/pts' 2>/dev/null || mount -t devpts devpts '$targetRootfs/dev/pts' 2>/dev/null; " +
                    "mountpoint -q '$targetRootfs/proc' || mount -t proc proc '$targetRootfs/proc' 2>/dev/null; " +
                    "mountpoint -q '$targetRootfs/sys' || mount -t sysfs sysfs '$targetRootfs/sys' 2>/dev/null; " +
                    "mountpoint -q '$targetRootfs/mnt/sdcard' || { mount --bind /sdcard '$targetRootfs/mnt/sdcard' 2>/dev/null && mount --make-private '$targetRootfs/mnt/sdcard' 2>/dev/null; }; " +
                    "chown 1000:1000 '$targetRootfs/home/coder' 2>/dev/null; " +
                    "cd '$targetRootfs' && exec chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=sahal SHELL=/bin/bash TERM=xterm-256color COLORTERM=truecolor DEVBOX=1 DEVBOX_SESSION_ID='$id' GH_NO_KEYRING=1 PATH=/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash -c 'cd /home/coder 2>/dev/null || cd /; if [ -x /usr/bin/script ]; then exec /usr/bin/script -qefc \"shopt -s checkwinsize 2>/dev/null; echo \\\$\\\$ > /tmp/.devbox_pid_$id 2>/dev/null; tty > /tmp/.devbox_pts_$id 2>/dev/null; echo \\\$(tty) > /home/coder/.devbox_pts_$id 2>/dev/null; ws=\\\$(cat /tmp/.devbox_ws_$id 2>/dev/null); if [ -n \\\"\\\$ws\\\" ]; then stty \\\$ws opost onlcr 2>/dev/null; else stty rows $initRows cols $initCols opost onlcr 2>/dev/null; fi; exec /bin/bash -l\" /dev/null; else exec /bin/bash -l; fi'"

            val pb = ProcessBuilder(suBinary, "-c", chrootCmd)
            pb.redirectErrorStream(true) // Gabungkan stderr ke stdout stream
            val proc = pb.start()
            process = proc
            outputStream = proc.outputStream
            inputStream = proc.inputStream

            isRunning = true
            AppLogger.i("NativeTerminal", "Sesi [$name] aktif (PID terhubung, PTY siap, SID: $id)")

            // Pantau proses aktif, xdg-open URL trigger, dan port web server aktif
            val sessionId = id
            processMonitorJob = sessionScope.launch {
                var cycle = 0
                val checkPorts = listOf(
                    3000 to "0BB8",
                    5173 to "1435",
                    8080 to "1F90",
                    8000 to "1F40",
                    4200 to "1068",
                    5000 to "1388",
                    8081 to "1F91"
                )
                while (isActive && isRunning) {
                    kotlinx.coroutines.delay(2000)
                    cycle++

                    // 1. Cek permintaan buka URL & notifikasi dari CLI (xdg-open / devbox-bridge)
                    try {
                        for (path in listOf("$targetRootfs/tmp/.devbox_open_url", "$targetRootfs/tmp/devbox-bridge/url.txt")) {
                            val triggerFile = File(path)
                            if (triggerFile.exists()) {
                                val lines = triggerFile.readLines()
                                triggerFile.delete()
                                for (u in lines) {
                                    if (u.isNotBlank()) {
                                        mainHandler.post {
                                            ChrootManager.openUrlInAndroidBrowser(u.trim())
                                        }
                                    }
                                }
                            }
                        }

                        val notifyFile = File("$targetRootfs/tmp/devbox-bridge/notify.txt")
                        if (notifyFile.exists()) {
                            val lines = notifyFile.readLines()
                            notifyFile.delete()
                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    val title = line.substringBefore("|", "DevBox Agent").trim()
                                    val msg = line.substringAfter("|", line).trim()
                                    mainHandler.post {
                                        ChrootManager.postBridgeNotification(title, msg)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // 2. Cek port web server lokal aktif via /proc/net/tcp (<1ms) setiap 4 detik
                    if (cycle % 2 == 0) {
                        try {
                            val tcpFile = File("/proc/net/tcp")
                            if (tcpFile.exists()) {
                                val lines = tcpFile.readLines()
                                var foundPort: Int? = null
                                for ((port, hex) in checkPorts) {
                                    if (lines.any { it.contains(":$hex ") && it.contains(" 0A ") }) {
                                        foundPort = port
                                        break
                                    }
                                }
                                mainHandler.post {
                                    TerminalSessionManager.detectedWebPort = foundPort
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. Cek working directory (CWD) & nama agent aktif per sesi secara pasif via trigger file tanpa memanggil su berkala
                    try {
                        val cwdFile = File("$targetRootfs/tmp/.devbox_cwd_$sessionId")
                        if (cwdFile.exists()) {
                            val cwd = cwdFile.readText().trim()
                            if (cwd.isNotBlank()) {
                                mainHandler.post {
                                    currentDir = cwd
                                }
                            }
                        }

                        val sessionAgentFile = File("$targetRootfs/tmp/.devbox_active_agent_$sessionId")
                        val globalAgentFile = File("$targetRootfs/tmp/.devbox_active_agent")
                        val detected = when {
                            sessionAgentFile.exists() -> sessionAgentFile.readText().trim()
                            globalAgentFile.exists() -> globalAgentFile.readText().trim()
                            else -> ""
                        }
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
        if (text.isEmpty()) return
        // Normalisasikan CRLF (\r\n) dan standalone CR (\r) menjadi Unix newline (\n) tanpa mengubah karakter lain
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')

        // Jika DECSET 2004 (Bracketed Paste Mode) aktif di terminal (misal di bash, zsh, nano, vim),
        // bungkus dengan escape sequence \u001B[200~ dan \u001B[201~.
        if (buffer.isBracketedPasteMode) {
            sendText("\u001B[200~$normalized\u001B[201~")
        } else {
            sendText(normalized)
        }
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

    private var cachedPtsDev: String? = null
    private var resizeJob: Job? = null

    fun updateWindowSize(cols: Int, rows: Int, isImmediate: Boolean = false) {
        if (cols == lastCols && rows == lastRows) return
        lastCols = cols
        lastRows = rows
        buffer.resize(cols, rows)
        val targetRootfs = ChrootManager.activeRootfs
        val sessionId = id

        // Debounce: 180ms saat pinch zoom agar Magisk tidak spam notif, atau 15ms saat keyboard buka/tutup agar TUI instan
        resizeJob?.cancel()
        val debounceMs = if (isImmediate) 15L else 180L
        resizeJob = sessionScope.launch(Dispatchers.IO) {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            try {
                val currentPtsFd = cachedPtsDev
                if (currentPtsFd != null) {
                    val directCmd = "echo 'rows $rows cols $cols' > '$targetRootfs/tmp/.devbox_ws_$sessionId' 2>/dev/null; " +
                            "if [ -e '$currentPtsFd' ]; then " +
                            "stty -F '$currentPtsFd' rows $rows cols $cols 2>/dev/null || chroot '$targetRootfs' /usr/bin/stty -F '$currentPtsFd' rows $rows cols $cols 2>/dev/null; " +
                            "else exit 1; fi"
                    val res = com.arbdev.devbox.data.RootExecutor.executeSync(directCmd, silent = true)
                    if (res.isSuccess) return@launch
                    cachedPtsDev = null
                }

                // Resolusi PTY slave via /proc/<pid>/fd/0 sehingga 100% menembus isolasi mount namespace su
                val resolveAndSetCmd = "echo 'rows $rows cols $cols' > '$targetRootfs/tmp/.devbox_ws_$sessionId' 2>/dev/null; " +
                        "pts_fd=''; " +
                        "spid=\$(cat '$targetRootfs/tmp/.devbox_pid_$sessionId' 2>/dev/null); " +
                        "if [ -n \"\$spid\" ] && [ -e \"/proc/\$spid/fd/0\" ]; then " +
                        "pts_fd=\"/proc/\$spid/fd/0\"; " +
                        "else " +
                        "for env_file in \$(grep -l 'DEVBOX_SESSION_ID=$sessionId' /proc/[0-9]*/environ 2>/dev/null); do " +
                        "p=\$(echo \"\$env_file\" | cut -d/ -f3); " +
                        "for fd in /proc/\$p/fd/0 /proc/\$p/fd/1 /proc/\$p/fd/2; do " +
                        "t=\$(readlink \$fd 2>/dev/null); " +
                        "if [[ \$t == */dev/pts/* ]]; then pts_fd=\"\$fd\"; break 2; fi; " +
                        "done; done; fi; " +
                        "if [ -n \"\$pts_fd\" ]; then " +
                        "stty -F \"\$pts_fd\" rows $rows cols $cols 2>/dev/null || chroot '$targetRootfs' /usr/bin/stty -F \"\$pts_fd\" rows $rows cols $cols 2>/dev/null; " +
                        "echo \"\$pts_fd\"; " +
                        "fi"

                var res = com.arbdev.devbox.data.RootExecutor.executeSync(resolveAndSetCmd, silent = true)
                var resolvedFd = res.getOrNull()?.stdout?.trim()
                if (resolvedFd.isNullOrBlank()) {
                    // Jika sesi baru saja diluncurkan dan /usr/bin/script belum selesai inisialisasi, tunggu sebentar lalu coba sekali lagi
                    delay(220)
                    res = com.arbdev.devbox.data.RootExecutor.executeSync(resolveAndSetCmd, silent = true)
                    resolvedFd = res.getOrNull()?.stdout?.trim()
                }
                if (!resolvedFd.isNullOrBlank() && resolvedFd.startsWith("/proc/")) {
                    cachedPtsDev = resolvedFd
                }
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
        sendBytes(byteArrayOf(3)) // Send Ctrl+C (0x03) ke stream PTY
        val pid = getProcessPid() ?: return
        sessionScope.launch(Dispatchers.IO) {
            try {
                // Kirim juga sinyal kernel SIGINT (kill -2) langsung ke foreground child process di sesi ini
                val targetRootfs = ChrootManager.activeRootfs
                val sessionId = id
                val cmd = "for p in /proc/[0-9]*; do " +
                        "if [ \"\$(readlink \$p/root 2>/dev/null)\" = '$targetRootfs' ]; then " +
                        "if grep -z '^DEVBOX_SESSION_ID=$sessionId' \"\$p/environ\" 2>/dev/null >/dev/null; then " +
                        "comm=\$(cat \$p/comm 2>/dev/null); " +
                        "if [ \"\$comm\" != 'script' ] && [ \"\$comm\" != 'bash' ] && [ \"\$comm\" != 'stty' ] && [ \"\$comm\" != 'su' ]; then " +
                        "kill -2 \${p##*/} 2>/dev/null || true; " +
                        "fi; fi; fi; done"
                com.arbdev.devbox.data.RootExecutor.executeSync(cmd, silent = true)
            } catch (_: Exception) {}
        }
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
            cachedPtsDev = null
            sessionScope.launch(Dispatchers.IO) {
                val cleanupCmd = "rm -f '$targetRootfs/tmp/.devbox_pts_$sessionId' '$targetRootfs/tmp/.devbox_pid_$sessionId' '$targetRootfs/tmp/.devbox_ws_$sessionId' '$targetRootfs/tmp/.devbox_cwd_$sessionId' '$targetRootfs/tmp/.devbox_active_agent_$sessionId' '$targetRootfs/home/coder/.devbox_pts_$sessionId' 2>/dev/null"
                com.arbdev.devbox.data.RootExecutor.executeSync(cleanupCmd, silent = true)
            }
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
    var detectedWebPort by mutableStateOf<Int?>(null)

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

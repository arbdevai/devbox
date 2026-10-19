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
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.util.UUID

class NativeTerminalSession(
    val id: String = UUID.randomUUID().toString().take(8),
    var name: String = "Sesi 1"
) : TerminalSessionClient {

    private val mainHandler = Handler(Looper.getMainLooper())
    var termSession: TerminalSession? = null
    var isRunning: Boolean by mutableStateOf(false)
    var currentDir: String by mutableStateOf("/home/coder")
    var currentTitle: String by mutableStateOf(name)

    init {
        initializeNativeSession()
    }

    private fun findSuPath(): String {
        val candidates = listOf(
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

    fun initializeNativeSession() {
        try {
            val targetRootfs = ChrootManager.activeRootfs
            val suBinary = findSuPath()

            val chrootCmd = "mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp' 2>/dev/null; " +
                    "mountpoint -q '$targetRootfs/dev' || mount --bind /dev '$targetRootfs/dev'; " +
                    "mountpoint -q '$targetRootfs/dev/pts' || mount --bind /dev/pts '$targetRootfs/dev/pts' 2>/dev/null || mount -t devpts devpts '$targetRootfs/dev/pts'; " +
                    "mountpoint -q '$targetRootfs/proc' || mount -t proc proc '$targetRootfs/proc'; " +
                    "mountpoint -q '$targetRootfs/sys' || mount -t sysfs sysfs '$targetRootfs/sys'; " +
                    "mountpoint -q '$targetRootfs/mnt/sdcard' || mount --bind /sdcard '$targetRootfs/mnt/sdcard' 2>/dev/null; " +
                    "cd '$targetRootfs' && exec chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color COLORTERM=truecolor PATH=/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash -c 'cd /home/coder 2>/dev/null || cd /; if id -u coder >/dev/null 2>&1; then exec /usr/sbin/runuser -u coder -- /bin/bash -c \"cd /home/coder 2>/dev/null || cd /; exec /bin/bash -l\"; else exec /bin/bash -l; fi'"

            // In POSIX execvp(cmd, argv), argv[0] must be the program name!
            val args = arrayOf(suBinary, "-c", chrootCmd)
            val env = arrayOf(
                "TERM=xterm-256color",
                "COLORTERM=truecolor"
            )

            val session = TerminalSession(
                suBinary,
                "$targetRootfs/home/coder",
                args,
                env,
                3000,
                this
            )
            // Pre-initialize emulator with standard dimensions (80 cols x 24 rows)
            session.initializeEmulator(80, 24)
            termSession = session
            isRunning = true
            AppLogger.i("NativeTerminal", "Sesi Terminal PTY [$name] berhasil dialokasikan via $suBinary")
        } catch (e: Exception) {
            AppLogger.e("NativeTerminal", "Gagal inisialisasi sesi PTY [$name]: ${e.message}")
        }
    }

    var onClipboardCopy: ((String) -> Unit)? = null

    fun getDisplayDir(): String {
        return when {
            currentDir == "/home/coder" -> "~"
            currentDir.startsWith("/home/coder/") -> "~" + currentDir.removePrefix("/home/coder")
            else -> currentDir
        }
    }

    fun sendText(text: String) {
        val session = termSession ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        session.write(bytes, 0, bytes.size)
    }

    fun sendBytes(bytes: ByteArray) {
        termSession?.write(bytes, 0, bytes.size)
    }

    fun cancelActiveProcess() {
        sendBytes(byteArrayOf(3)) // Send byte 0x03 (SIGINT / Ctrl+C)
    }

    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        sendText("$trimmed\r")
    }

    fun finish() {
        try {
            termSession?.finishIfRunning()
        } catch (_: Exception) {}
        isRunning = false
    }

    // --- TerminalSessionClient implementation ---
    override fun onTextChanged(changedSession: TerminalSession) {}

    override fun onTitleChanged(changedSession: TerminalSession) {
        val title = changedSession.title
        if (!title.isNullOrBlank()) {
            mainHandler.post {
                currentTitle = title
            }
        }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        mainHandler.post {
            isRunning = false
            AppLogger.i("NativeTerminal", "Sesi [$name] telah selesai (exit code: ${finishedSession.exitStatus})")
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        mainHandler.post {
            onClipboardCopy?.invoke(text)
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {}

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) {
        AppLogger.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        AppLogger.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        AppLogger.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {}

    override fun logVerbose(tag: String, message: String) {}

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        AppLogger.e(tag, "$message: ${e.message}")
    }

    override fun logStackTrace(tag: String, e: Exception) {
        AppLogger.e(tag, "Stack trace: ${e.message}")
    }
}

object TerminalSessionManager {
    val sessions = mutableStateListOf<NativeTerminalSession>()
    val activeIndex = mutableIntStateOf(0)

    init {
        sessions.add(NativeTerminalSession(name = "Sesi 1"))
    }

    fun getActiveSession(): NativeTerminalSession {
        if (sessions.isEmpty()) {
            sessions.add(NativeTerminalSession(name = "Sesi 1"))
            activeIndex.intValue = 0
        }
        val idx = activeIndex.intValue.coerceIn(0, sessions.size - 1)
        return sessions[idx]
    }

    fun createNewSession(): NativeTerminalSession {
        val newName = "Sesi ${sessions.size + 1}"
        val session = NativeTerminalSession(name = newName)
        sessions.add(session)
        activeIndex.intValue = sessions.size - 1
        AppLogger.i("TerminalManager", "Sesi baru dibuat: $newName (Total: ${sessions.size})")
        return session
    }

    fun closeSession(index: Int) {
        if (index in sessions.indices) {
            val session = sessions.removeAt(index)
            session.finish()
            AppLogger.i("TerminalManager", "Menutup ${session.name}")
            if (sessions.isEmpty()) {
                sessions.add(NativeTerminalSession(name = "Sesi 1"))
                activeIndex.intValue = 0
            } else {
                activeIndex.intValue = (index - 1).coerceAtLeast(0)
            }
        }
    }

    fun closeActiveSession() {
        closeSession(activeIndex.intValue)
    }

    fun terminateAll() {
        AppLogger.i("TerminalManager", "Mematikan semua sesi aktif...")
        sessions.forEach { it.finish() }
        sessions.clear()
        ChrootManager.killDevboxProcessesAndUnmountSync()
        sessions.add(NativeTerminalSession(name = "Sesi 1"))
        activeIndex.intValue = 0
    }
}

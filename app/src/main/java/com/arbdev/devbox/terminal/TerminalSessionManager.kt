package com.arbdev.devbox.terminal

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.util.AppLogger
import com.arbdev.devbox.util.LineType
import com.arbdev.devbox.util.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NativeTerminalSession(
    val id: String = UUID.randomUUID().toString().take(8),
    var name: String = "Sesi 1"
) {
    var currentDir: String by mutableStateOf("/home/coder")
    val logLines = mutableStateListOf<TerminalLine>()
    val history = mutableListOf<String>()
    var historyIndex: Int = -1
    var isRunning: Boolean by mutableStateOf(false)
    var currentProcess: Process? = null
    var runningJob: Job? = null

    init {
        logLines.add(TerminalLine("DevBox Native Terminal [Ubuntu 24.04 Chroot Engine]", LineType.SYSTEM))
        logLines.add(TerminalLine("Tekan lama pada teks mana saja untuk seleksi & copy khas Android.", LineType.SYSTEM))
        logLines.add(TerminalLine("Mendukung ANSI escape colors, multi-sesi, dan Termux extra keys.", LineType.SYSTEM))
        logLines.add(TerminalLine("------------------------------------------------------------", LineType.SYSTEM))
    }

    fun getDisplayDir(): String {
        return when {
            currentDir == "/home/coder" -> "~"
            currentDir.startsWith("/home/coder/") -> "~" + currentDir.removePrefix("/home/coder")
            else -> currentDir
        }
    }

    fun cancelActiveProcess() {
        try {
            currentProcess?.destroy()
        } catch (_: Exception) {}
        runningJob?.cancel()
        isRunning = false
        currentProcess = null
        logLines.add(TerminalLine("^C", LineType.SYSTEM))
    }

    fun runCommand(cmd: String, scope: CoroutineScope) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || isRunning) return

        history.add(trimmed)
        historyIndex = history.size
        val promptStr = "coder@devbox:${getDisplayDir()}$ "
        logLines.add(TerminalLine("$promptStr$trimmed", LineType.INPUT))

        if (trimmed == "clear") {
            logLines.clear()
            return
        }

        if (trimmed == "exit") {
            TerminalSessionManager.closeActiveSession()
            return
        }

        val targetRootfs = ChrootManager.activeRootfs
        runningJob = scope.launch(Dispatchers.IO) {
            isRunning = true
            AppLogger.i("NativeTerminal", "Menjalankan perintah di [$name]: $trimmed (cwd: $currentDir)")
            try {
                ChrootManager.mountAll(targetRootfs)

                val scriptContent = """
cd "$currentDir" 2>/dev/null || cd /home/coder 2>/dev/null || cd /
$trimmed
__code=$?
printf "\n___DEVBOX_PWD:%s\n" "${'$'}(pwd)"
exit ${'$'}__code
""".trimIndent()

                val b64Script = Base64.encodeToString(scriptContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

                val executionCmd = """
if chroot '$targetRootfs' id -u coder >/dev/null 2>&1; then
    echo '$b64Script' | base64 -d | chroot '$targetRootfs' /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash 2>&1
else
    echo '$b64Script' | base64 -d | chroot '$targetRootfs' /usr/bin/$envPrefix /bin/bash 2>&1
fi
""".trimIndent()

                val process = ProcessBuilder("su", "-c", executionCmd)
                    .redirectErrorStream(true)
                    .start()
                currentProcess = process

                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.startsWith("___DEVBOX_PWD:")) {
                            val newPath = l.substringAfter("___DEVBOX_PWD:").trim()
                            if (newPath.isNotEmpty() && newPath.startsWith("/")) {
                                currentDir = newPath
                            }
                        } else {
                            val type = when {
                                l.contains("[ERROR]", ignoreCase = true) || l.contains("Permission denied", ignoreCase = true) -> LineType.ERROR
                                l.contains("[WARN]", ignoreCase = true) -> LineType.SYSTEM
                                else -> LineType.OUTPUT
                            }
                            withContext(Dispatchers.Main) {
                                logLines.add(TerminalLine(l, type))
                                if (logLines.size > 2000) {
                                    logLines.removeRange(0, 500)
                                }
                            }
                        }
                    }
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    AppLogger.w("NativeTerminal", "Perintah selesai dengan kode exit: $exitCode")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logLines.add(TerminalLine("Error eksekusi: ${e.message}", LineType.ERROR))
                }
            } finally {
                isRunning = false
                currentProcess = null
            }
        }
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
            session.cancelActiveProcess()
            AppLogger.i("TerminalManager", "Menutup ${session.name}")
            if (sessions.isEmpty()) {
                ChrootManager.killDevboxProcessesAndUnmountSync()
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
        sessions.forEach { it.cancelActiveProcess() }
        sessions.clear()
        sessions.add(NativeTerminalSession(name = "Sesi 1"))
        activeIndex.intValue = 0
    }
}

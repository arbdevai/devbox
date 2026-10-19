package com.arbdev.devbox.terminal

import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class TerminalSession(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "Sesi 1"
) {
    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var readerJob: Job? = null

    private val outputBuffer = StringBuilder()
    private val bufferLock = Any()
    private val maxBufferLength = 120_000

    var isAlive: Boolean = false
        private set

    private var outputListener: ((String) -> Unit)? = null

    fun setListener(listener: ((String) -> Unit)?) {
        outputListener = listener
        if (listener != null) {
            val snapshot = synchronized(bufferLock) { outputBuffer.toString() }
            if (snapshot.isNotEmpty()) {
                val b64 = Base64.encodeToString(snapshot.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                listener(b64)
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (isAlive) return
        val targetRootfs = ChrootManager.activeRootfs
        AppLogger.i("TerminalSession", "Memulai sesi $name pada rootfs: $targetRootfs")

        try {
            val pb = ProcessBuilder("su")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc
            outputStream = proc.outputStream
            inputStream = proc.inputStream
            isAlive = true

            // Inisialisasi mount container mandiri devbox-chroot dan jalankan shell interaktif
            val initScript = """
                mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp'
                chmod 1777 '$targetRootfs/tmp' 2>/dev/null || true
                mountpoint -q '$targetRootfs/dev' || mount --bind /dev '$targetRootfs/dev'
                mountpoint -q '$targetRootfs/dev/pts' || mount -t devpts devpts '$targetRootfs/dev/pts'
                mountpoint -q '$targetRootfs/proc' || mount -t proc proc '$targetRootfs/proc'
                mountpoint -q '$targetRootfs/sys' || mount -t sysfs sysfs '$targetRootfs/sys'
                mountpoint -q '$targetRootfs/mnt/sdcard' || mount --bind /sdcard '$targetRootfs/mnt/sdcard'
                grep -qE '^127\.0\.0\.1[[:space:]]+localhost' '$targetRootfs/etc/hosts' 2>/dev/null || echo '127.0.0.1 localhost' >> '$targetRootfs/etc/hosts' 2>/dev/null || true
                
                if [ -x '$targetRootfs/bin/bash' ] || [ -x '$targetRootfs/usr/bin/bash' ]; then
                    chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /usr/bin/script -q -c "/usr/sbin/runuser -u coder -- /bin/bash -l" /dev/null 2>/dev/null || chroot '$targetRootfs' /bin/bash -i
                elif [ -x '$targetRootfs/bin/sh' ]; then
                    chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/sh TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/sh -i
                else
                    echo -e "\r\n\033[1;33m[DevBox Container Notice]\033[0m"
                    echo -e "Direktori rootfs di \033[1;36m$targetRootfs\033[0m belum dipasang binary sistem."
                    echo -e "Silakan buka tab \033[1;32mStatus\033[0m untuk kloning atau unduh Ubuntu resmi."
                    echo -e "Menyediakan emergency shell host...\r\n"
                    /bin/sh -i
                fi
                exit $?
            """.trimIndent() + "\n"

            outputStream?.write(initScript.toByteArray(Charsets.UTF_8))
            outputStream?.flush()

            readerJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(4096)
                val stream = inputStream ?: return@launch
                try {
                    while (isActive) {
                        val readBytes = stream.read(buffer)
                        if (readBytes == -1) break
                        if (readBytes > 0) {
                            val chunk = String(buffer, 0, readBytes, Charsets.UTF_8)
                            synchronized(bufferLock) {
                                outputBuffer.append(chunk)
                                if (outputBuffer.length > maxBufferLength) {
                                    outputBuffer.delete(0, outputBuffer.length - maxBufferLength)
                                }
                            }
                            val b64 = Base64.encodeToString(buffer.copyOf(readBytes), Base64.NO_WRAP)
                            outputListener?.invoke(b64)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("TerminalSession", "Reader exception: ${e.message}")
                } finally {
                    isAlive = false
                    AppLogger.i("TerminalSession", "Sesi $name keluar. Membersihkan sisa container...")
                    ChrootManager.killDevboxProcessesAndUnmountSync()
                    val exitMsg = "\r\n\r\n\u001b[1;31m[Sesi DevBox Berakhir - Semua Proses Container Ditutup]\u001b[0m\r\n"
                    synchronized(bufferLock) {
                        outputBuffer.append(exitMsg)
                    }
                    val b64 = Base64.encodeToString(exitMsg.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    outputListener?.invoke(b64)
                }
            }
        } catch (e: Exception) {
            isAlive = false
            AppLogger.e("TerminalSession", "Gagal memulai root shell", e)
            val errorMsg = "\r\n\u001b[1;31mGagal menjalankan root shell: ${e.message}\u001b[0m\r\n"
            synchronized(bufferLock) {
                outputBuffer.append(errorMsg)
            }
            val b64 = Base64.encodeToString(errorMsg.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            outputListener?.invoke(b64)
        }
    }

    fun writeInput(data: String) {
        if (!isAlive || outputStream == null) return
        try {
            outputStream?.write(data.toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        } catch (_: Exception) {
            isAlive = false
        }
    }

    fun resize(cols: Int, rows: Int) {
        if (!isAlive) return
        try {
            val sttyCmd = "stty cols $cols rows $rows 2>/dev/null\n"
            outputStream?.write(sttyCmd.toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        } catch (_: Exception) {
        }
    }

    fun terminate() {
        isAlive = false
        readerJob?.cancel()
        try {
            outputStream?.write("exit\n".toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        } catch (_: Exception) {
        }
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        outputStream = null
        inputStream = null
        ChrootManager.killDevboxProcessesAndUnmountSync()
    }
}

object TerminalSessionManager {
    val sessions = mutableStateListOf<TerminalSession>()
    val activeIndex = mutableIntStateOf(0)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getActiveSession(): TerminalSession {
        if (sessions.isEmpty()) {
            val session = TerminalSession(name = "Sesi 1")
            sessions.add(session)
            session.start(scope)
            return session
        }
        val safeIdx = activeIndex.intValue.coerceIn(0, sessions.size - 1)
        val current = sessions[safeIdx]
        if (!current.isAlive) {
            current.start(scope)
        }
        return current
    }

    fun createNewSession(): TerminalSession {
        val nextNum = sessions.size + 1
        val session = TerminalSession(name = "Sesi $nextNum")
        sessions.add(session)
        activeIndex.intValue = sessions.size - 1
        session.start(scope)
        return session
    }

    fun closeSession(index: Int) {
        if (index in sessions.indices) {
            val session = sessions.removeAt(index)
            session.terminate()
            if (sessions.isEmpty()) {
                createNewSession()
            } else {
                activeIndex.intValue = activeIndex.intValue.coerceIn(0, sessions.size - 1)
            }
        }
    }

    fun terminateAll() {
        sessions.forEach { it.terminate() }
        sessions.clear()
        activeIndex.intValue = 0
        ChrootManager.killDevboxProcessesAndUnmountSync()
    }
}

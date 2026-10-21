package com.arbdev.devbox.data

import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object RootExecutor {

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    // Persistent interactive root shell: hanya menjalankan 'su' 1 KALI saja sepanjang aplikasi hidup
    // Mencegah Magisk memunculkan pop-up izin berulang kali / looping
    private var sharedShell: Process? = null
    private var shellWriter: BufferedWriter? = null
    private var shellReader: BufferedReader? = null
    private val shellLock = Any()

    private fun getOrCreateShell(): Boolean {
        synchronized(shellLock) {
            val p = sharedShell
            if (p != null) {
                try {
                    p.exitValue()
                    destroyShell()
                } catch (_: IllegalThreadStateException) {
                    return true
                }
            }
            return try {
                val newProc = ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start()
                sharedShell = newProc
                shellWriter = newProc.outputStream.bufferedWriter()
                shellReader = newProc.inputStream.bufferedReader()
                true
            } catch (e: Exception) {
                destroyShell()
                false
            }
        }
    }

    private fun destroyShell() {
        synchronized(shellLock) {
            try { shellWriter?.close() } catch (_: Exception) {}
            try { shellReader?.close() } catch (_: Exception) {}
            try { sharedShell?.destroy() } catch (_: Exception) {}
            shellWriter = null
            shellReader = null
            sharedShell = null
        }
    }

    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!forceCheck && cachedRootAvailable == true) {
            return@withContext true
        }
        val res = executeSync("id", silent = true)
        val cmdResult = res.getOrNull()
        val available = res.isSuccess && (cmdResult?.stdout?.contains("uid=0") == true)
        cachedRootAvailable = available
        if (available) {
            AppLogger.i("RootExecutor", "Akses root (su) terverifikasi: ${cmdResult?.stdout}")
        } else {
            AppLogger.w("RootExecutor", "Akses root gagal / ditolak (exit: ${cmdResult?.exitCode}, out: ${cmdResult?.stdout})")
        }
        available
    }

    suspend fun execute(command: String, silent: Boolean = false): Result<CommandResult> = withContext(Dispatchers.IO) {
        executeSync(command, silent)
    }

    fun executeSync(command: String, silent: Boolean = false): Result<CommandResult> {
        synchronized(shellLock) {
            if (!getOrCreateShell()) {
                return executeDirect(command, silent)
            }
            return try {
                val writer = shellWriter ?: return executeDirect(command, silent)
                val reader = shellReader ?: return executeDirect(command, silent)
                val token = "__DEVBOX_TOKEN_${System.nanoTime()}__"

                writer.write("echo '$token'\n")
                writer.write("($command)\n")
                writer.write("echo \"$token $?\"\n")
                writer.flush()

                val startTime = System.currentTimeMillis()
                val timeoutMs = 12000L // Batas waktu 12 detik untuk mencegah deadlock shell

                var tokenFound = false
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (reader.ready()) {
                        val line = reader.readLine()
                        if (line == token) {
                            tokenFound = true
                            break
                        }
                    } else {
                        Thread.sleep(10)
                    }
                }
                if (!tokenFound) {
                    throw java.util.concurrent.TimeoutException("Perintah root timeout menunggu token mulai: $command")
                }

                val stdoutBuilder = StringBuilder()
                var exitCode = 0
                var endTokenFound = false
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (reader.ready()) {
                        val cur = reader.readLine() ?: break
                        if (cur.startsWith(token)) {
                            val codeStr = cur.substringAfter(token).trim()
                            exitCode = codeStr.toIntOrNull() ?: 0
                            endTokenFound = true
                            break
                        }
                        if (stdoutBuilder.isNotEmpty()) stdoutBuilder.append('\n')
                        stdoutBuilder.append(cur)
                    } else {
                        Thread.sleep(10)
                    }
                }
                if (!endTokenFound) {
                    throw java.util.concurrent.TimeoutException("Perintah root timeout membaca output: $command")
                }

                val res = CommandResult(exitCode, stdoutBuilder.toString().trim(), "")
                if (!silent) {
                    AppLogger.cmd(command, exitCode, res.stdout, "")
                }
                cachedRootAvailable = true
                Result.success(res)
            } catch (e: Exception) {
                destroyShell()
                executeDirect(command, silent)
            }
        }
    }

    private fun executeDirect(command: String, silent: Boolean = false): Result<CommandResult> {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            val res = CommandResult(exitCode, stdout.trim(), stderr.trim())
            if (!silent) {
                AppLogger.cmd(command, exitCode, res.stdout, res.stderr)
            }
            Result.success(res)
        } catch (e: Exception) {
            if (!silent) {
                AppLogger.e("RootExecutor", "Gagal mengeksekusi perintah: $command", e)
            }
            Result.failure(e)
        }
    }

    suspend fun executeStream(
        command: String,
        onOutputLine: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        AppLogger.i("RootExecutor", "Memulai stream eksekusi: $command")
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        AppLogger.i("Stream", it)
                        onOutputLine(it)
                    }
                }
            }
            val exit = process.waitFor()
            AppLogger.i("RootExecutor", "Stream selesai (exit: $exit): $command")
            exit
        } catch (e: Exception) {
            val errMsg = "Error: ${e.localizedMessage}"
            AppLogger.e("RootExecutor", errMsg, e)
            onOutputLine(errMsg)
            -1
        }
    }
}

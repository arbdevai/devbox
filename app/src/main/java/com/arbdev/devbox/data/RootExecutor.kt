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
        return executeDirect(command, silent)
    }

    private fun executeDirect(command: String, silent: Boolean = false, timeoutMs: Long = 10000L): Result<CommandResult> {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (stdoutBuilder.isNotEmpty()) stdoutBuilder.append('\n')
                            stdoutBuilder.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (stderrBuilder.isNotEmpty()) stderrBuilder.append('\n')
                            stderrBuilder.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutThread.interrupt()
                stderrThread.interrupt()
                val timeoutErr = "Perintah root timeout ($timeoutMs ms): $command"
                if (!silent) AppLogger.w("RootExecutor", timeoutErr)
                return Result.failure(java.util.concurrent.TimeoutException(timeoutErr))
            }

            stdoutThread.join(400)
            stderrThread.join(400)

            val exitCode = process.exitValue()
            val res = CommandResult(exitCode, stdoutBuilder.toString().trim(), stderrBuilder.toString().trim())
            if (!silent) {
                AppLogger.cmd(command, exitCode, res.stdout, res.stderr)
            }
            cachedRootAvailable = true
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

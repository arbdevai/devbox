package com.arbdev.devbox.data

import com.arbdev.devbox.util.AppLogger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootExecutor {

    @Volatile
    var hasAppliedSystemTweaks: Boolean = false

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    init {
        try {
            // Konfigurasi Shell libsu: Mount Master (-mm) & redirect stderr untuk kecepatan maksimal (~2ms)
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        } catch (_: Throwable) {}
    }

    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!forceCheck && cachedRootAvailable == true) {
            return@withContext true
        }
        val isGranted = try {
            Shell.isAppGrantedRoot() == true
        } catch (_: Throwable) { false }

        if (isGranted) {
            cachedRootAvailable = true
            AppLogger.i("RootExecutor", "Akses root (libsu persistent shell) terverifikasi.")
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

    suspend fun execute(command: String, silent: Boolean = false, timeoutMs: Long = 10000L): Result<CommandResult> = withContext(Dispatchers.IO) {
        executeSync(command, silent, timeoutMs)
    }

    fun executeSync(command: String, silent: Boolean = false, timeoutMs: Long = 10000L): Result<CommandResult> {
        return try {
            // 1. Eksekusi ultra-cepat via Persistent Root Shell libsu (1-3ms)
            val result = Shell.cmd(command).exec()
            val stdout = result.out.joinToString("\n").trim()
            val stderr = result.err.joinToString("\n").trim()
            val res = CommandResult(result.code, stdout, stderr)
            if (!silent) {
                AppLogger.cmd(command, result.code, res.stdout, res.stderr)
            }
            cachedRootAvailable = true
            Result.success(res)
        } catch (_: Throwable) {
            // 2. Fallback aman ke direct ProcessBuilder su jika daemon libsu belum siap
            executeDirect(command, silent, timeoutMs)
        }
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

package com.arbdev.devbox.data

import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Eksekutor perintah untuk Flavor No-Root (Userspace).
 * Berjalan murni di userspace Android tanpa ketergantungan libsu atau biner su.
 */
object RootExecutor {

    @Volatile
    var hasAppliedSystemTweaks: Boolean = true // Di no-root tidak butuh root system tweaks

    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean {
        return false // Flavor no-root secara definitif tidak memerlukan root
    }

    suspend fun execute(command: String, silent: Boolean = false, timeoutMs: Long = 10000L): Result<CommandResult> = withContext(Dispatchers.IO) {
        executeSync(command, silent, timeoutMs)
    }

    fun executeSync(command: String, silent: Boolean = false, timeoutMs: Long = 10000L): Result<CommandResult> {
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
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

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutThread.interrupt()
                stderrThread.interrupt()
                val timeoutErr = "Perintah userspace timeout ($timeoutMs ms): $command"
                if (!silent) AppLogger.w("NoRootExecutor", timeoutErr)
                return Result.failure(TimeoutException(timeoutErr))
            }

            stdoutThread.join(400)
            stderrThread.join(400)

            val exitCode = process.exitValue()
            val res = CommandResult(exitCode, stdoutBuilder.toString().trim(), stderrBuilder.toString().trim())
            if (!silent) {
                AppLogger.cmd(command, exitCode, res.stdout, res.stderr)
            }
            Result.success(res)
        } catch (e: Exception) {
            if (!silent) {
                AppLogger.e("NoRootExecutor", "Gagal eksekusi perintah: $command", e)
            }
            Result.failure(e)
        }
    }

    suspend fun executeStream(
        command: String,
        onOutputLine: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        AppLogger.i("NoRootExecutor", "Memulai stream userspace: $command")
        try {
            val process = ProcessBuilder("sh", "-c", command)
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
            AppLogger.i("NoRootExecutor", "Stream selesai (exit: $exit): $command")
            exit
        } catch (e: Exception) {
            val errMsg = "Error: ${e.localizedMessage}"
            AppLogger.e("NoRootExecutor", errMsg, e)
            onOutputLine(errMsg)
            -1
        }
    }
}

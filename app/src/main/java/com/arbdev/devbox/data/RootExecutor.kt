package com.arbdev.devbox.data

import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object RootExecutor {

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val exit = process.waitFor()
            val available = exit == 0 && stdout.contains("uid=0")
            if (available) {
                AppLogger.i("RootExecutor", "Akses root (su) terverifikasi: $stdout")
            } else {
                AppLogger.w("RootExecutor", "Akses root gagal / ditolak (exit: $exit, out: $stdout)")
            }
            available
        } catch (e: Exception) {
            AppLogger.e("RootExecutor", "Exception saat cek su", e)
            false
        }
    }

    suspend fun execute(command: String): Result<CommandResult> = withContext(Dispatchers.IO) {
        executeSync(command)
    }

    fun executeSync(command: String): Result<CommandResult> {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            val res = CommandResult(exitCode, stdout.trim(), stderr.trim())
            AppLogger.cmd(command, exitCode, res.stdout, res.stderr)
            Result.success(res)
        } catch (e: Exception) {
            AppLogger.e("RootExecutor", "Gagal mengeksekusi perintah: $command", e)
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

package com.arbdev.devbox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

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
            exit == 0 && stdout.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun execute(command: String): Result<CommandResult> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            Result.success(CommandResult(exitCode, stdout.trim(), stderr.trim()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeStream(
        command: String,
        onOutputLine: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { onOutputLine(it) }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            onOutputLine("Error: ${e.localizedMessage}")
            -1
        }
    }
}

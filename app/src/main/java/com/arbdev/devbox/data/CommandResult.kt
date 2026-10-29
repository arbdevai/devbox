package com.arbdev.devbox.data

/**
 * Hasil eksekusi perintah shell Linux (stdout, stderr, exitCode).
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

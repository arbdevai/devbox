package com.arbdev.devbox.backend

import android.content.Context
import com.arbdev.devbox.data.ChrootProcessInfo
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.data.CommandResult

/**
 * Interface abstraksi tunggal untuk backend Linux DevBox.
 * Mengikuti Hukum 2 Dokumen Aturan Arsitektur:
 * - Flavor 'root' (src/root/) mengimplementasikan ini via native chroot & libsu (ChrootBackend).
 * - Flavor 'noroot' (src/noroot/) mengimplementasikan ini via PRoot userspace (PRootBackend).
 */
interface LinuxEnvironment {
    val backendType: String // "root" atau "noroot"
    val isRootBackend: Boolean
    val defaultRootfsPath: String

    fun init(context: Context)
    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean

    fun startTerminalProcess(
        sessionId: String,
        targetRootfs: String,
        initCols: Int,
        initRows: Int
    ): Process

    fun updateTerminalWindowSize(
        sessionId: String,
        proc: Process?,
        cols: Int,
        rows: Int,
        targetRootfs: String
    )

    fun terminateSession(sessionId: String, targetRootfs: String)
    fun sendInterruptSignal(sessionId: String, targetRootfs: String)

    suspend fun getEnvironmentState(rootfs: String): ChrootState
    suspend fun mountAll(rootfs: String): Result<String>
    suspend fun unmountAll(rootfs: String): Result<String>
    fun killProcessesAndUnmountSync(rootfs: String): Result<String>

    suspend fun ensureEnvironmentReady(rootfs: String): Result<Unit>
    fun ensureEnvironmentReadySync(rootfs: String): Result<Unit>

    suspend fun runEnvironmentDiagnostics(): String
}

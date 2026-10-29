package com.arbdev.devbox.backend

import android.content.Context
import com.arbdev.devbox.data.ChrootProcessInfo
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.data.CommandResult

/**
 * Interface abstraksi tunggal untuk backend Linux DevBox.
 * Mengikuti Hukum 2 Dokumen Aturan Arsitektur:
 * - Flavor 'root' mengimplementasikan ini via native chroot & libsu (performa 100% native).
 * - Flavor 'noroot' mengimplementasikan ini via PRoot userspace (kompatibel tanpa root).
 */
interface LinuxEnvironment {
    val backendType: String // "root" atau "noroot"
    val isRootBackend: Boolean
    val defaultRootfsPath: String
    var activeRootfs: String
    var hasAppliedSystemTweaks: Boolean

    fun init(context: Context)
    fun setActiveRootfs(context: Context, path: String)
    fun applySystemTweaks(context: Context)
    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean
    suspend fun isEnvironmentReady(): Boolean

    fun startTerminalProcess(
        sessionId: String,
        name: String,
        initCols: Int,
        initRows: Int,
        targetRootfs: String
    ): Process

    fun updateTerminalWindowSize(
        sessionId: String,
        proc: Process?,
        cols: Int,
        rows: Int
    )

    fun terminateSession(sessionId: String, proc: Process?)

    fun cleanStaleLocks(): Int
    fun cleanDeadTmpSessions()
    fun getOrphanProcesses(activeIds: Set<String>, activePids: Set<Int>): List<ChrootProcessInfo>
    fun killOrphanProcesses(activeIds: Set<String>, activePids: Set<Int>): Int
    fun killAllProcessesAndCleanupSync()

    suspend fun cloneExistingChroot(sourceChroot: String, targetRootfs: String): Boolean
    suspend fun downloadUbuntuOfficialRootfs(targetRootfs: String, onProgress: (String) -> Unit): Boolean
    suspend fun uninstallRootfs(targetRootfs: String, onProgress: (String) -> Unit): Boolean

    suspend fun executeCommand(command: String, timeoutSec: Long = 30, silent: Boolean = false): Result<CommandResult>
    fun executeCommandSync(command: String, timeoutSec: Long = 10, silent: Boolean = false): Result<CommandResult>
    suspend fun executeCommandStream(command: String, onLine: (String) -> Unit): Int

    fun runDiagnostics(): String
    fun openUrlInAndroidBrowser(url: String)
    fun postBridgeNotification(title: String, msg: String)
}

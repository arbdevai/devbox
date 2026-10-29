package com.arbdev.devbox.backend

import android.content.Context
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementasi backend Linux userspace berbasis PRoot untuk Flavor No-Root.
 * Memungkinkan eksekusi distro Linux lengkap (Ubuntu, Debian, dll.) tanpa izin root.
 */
class PRootBackend(private val context: Context) : LinuxEnvironment {
    override val backendType: String = "noroot"
    override val isRootBackend: Boolean = false
    override val defaultRootfsPath: String = File(context.filesDir, "rootfs").absolutePath

    override fun init(context: Context) {
        AppLogger.i("PRootBackend", "Inisialisasi PRootBackend (No-Root Userspace via libproot.so)")
        File(defaultRootfsPath).mkdirs()
    }

    override suspend fun isRootAvailable(forceCheck: Boolean): Boolean {
        return false // Flavor no-root secara definitif tidak membutuhkan root
    }

    private fun getProotBinaryPath(): String {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val primaryProot = File(nativeLibDir, "libproot.so")
        if (primaryProot.exists() && primaryProot.length() > 100_000) {
            return primaryProot.absolutePath
        }

        // Fail-safe 1: Periksa direktori lib turunan dari sourceDir APK
        val parentLib = File(context.applicationInfo.sourceDir).parentFile?.let { File(it, "lib/arm64/libproot.so") }
        if (parentLib != null && parentLib.exists() && parentLib.length() > 100_000) {
            return parentLib.absolutePath
        }

        // Fail-safe 2: Ekstraksi darurat langsung dari APK sourceDir jika installer OS tidak mengekstraknya
        try {
            val fallbackBin = File(context.filesDir, "bin/libproot.so")
            if (fallbackBin.exists() && fallbackBin.length() > 100_000) {
                fallbackBin.setExecutable(true, false)
                return fallbackBin.absolutePath
            }
            fallbackBin.parentFile?.mkdirs()
            java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                val entry = zip.getEntry("lib/arm64-v8a/libproot.so")
                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        fallbackBin.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    fallbackBin.setExecutable(true, false)
                    AppLogger.i("PRootBackend", "Ekstraksi fail-safe libproot.so selesai: ${fallbackBin.absolutePath}")
                    return fallbackBin.absolutePath
                }
            }
        } catch (e: Exception) {
            AppLogger.e("PRootBackend", "Gagal ekstraksi darurat libproot.so: ${e.message}")
        }

        return primaryProot.absolutePath
    }

    override fun startTerminalProcess(
        sessionId: String,
        targetRootfs: String,
        initCols: Int,
        initRows: Int
    ): Process {
        val prootBin = getProotBinaryPath()
        val prootTmp = File(context.cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath
        File(targetRootfs, "home/coder").mkdirs()
        File(targetRootfs, "tmp").mkdirs()

        val shellScript = "cd /home/coder 2>/dev/null || cd /; if [ -x /usr/bin/script ]; then exec /usr/bin/script -qefc \"shopt -s checkwinsize 2>/dev/null; echo \\\$\$ > /tmp/.devbox_pid_$sessionId 2>/dev/null; ws=\\\$(cat /tmp/.devbox_ws_$sessionId 2>/dev/null); if [ -n \\\"\\\$ws\\\" ]; then stty \\\$ws opost onlcr 2>/dev/null; else stty rows $initRows cols $initCols opost onlcr 2>/dev/null; fi; exec /bin/bash -l\" /dev/null; else exec /bin/bash -l; fi"

        val pb = ProcessBuilder(
            prootBin,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-r", targetRootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard:/mnt/sdcard",
            "-b", "$prootTmp:/tmp",
            "-w", "/home/coder",
            "/usr/bin/env", "-i",
            "HOME=/home/coder",
            "USER=coder",
            "LOGNAME=coder",
            "SHELL=/bin/bash",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "DEVBOX=1",
            "DEVBOX_SESSION_ID=$sessionId",
            "GH_NO_KEYRING=1",
            "PATH=/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/bash", "-c",
            shellScript
        )
        pb.environment()["PROOT_TMP_DIR"] = prootTmp
        pb.redirectErrorStream(true)
        return pb.start()
    }

    override fun updateTerminalWindowSize(
        sessionId: String,
        proc: Process?,
        cols: Int,
        rows: Int,
        targetRootfs: String
    ) {
        try {
            val prootTmp = File(context.cacheDir, "proot_tmp")
            File(prootTmp, ".devbox_ws_$sessionId").writeText("rows $rows cols $cols")
            File(targetRootfs, "tmp/.devbox_ws_$sessionId").writeText("rows $rows cols $cols")
        } catch (_: Exception) {}
    }

    override fun terminateSession(sessionId: String, targetRootfs: String) {
        try {
            val prootTmp = File(context.cacheDir, "proot_tmp")
            File(prootTmp, ".devbox_ws_$sessionId").delete()
            File(prootTmp, ".devbox_pid_$sessionId").delete()
            File(targetRootfs, "tmp/.devbox_ws_$sessionId").delete()
            File(targetRootfs, "tmp/.devbox_pid_$sessionId").delete()
            File(targetRootfs, "tmp/.devbox_pts_$sessionId").delete()
            File(targetRootfs, "tmp/.devbox_cwd_$sessionId").delete()
            File(targetRootfs, "tmp/.devbox_active_agent_$sessionId").delete()
        } catch (_: Exception) {}
    }

    override fun sendInterruptSignal(sessionId: String, targetRootfs: String) {
        // Pada PRoot, sinyal Ctrl+C (0x03) dikirim langsung ke stdin PTY stream
    }

    override suspend fun getEnvironmentState(rootfs: String): ChrootState = withContext(Dispatchers.IO) {
        val rootfsDir = File(rootfs)
        val bashFile = File(rootfsDir, "bin/bash")
        val exists = rootfsDir.exists() && (bashFile.exists() || File(rootfsDir, "usr/bin/bash").exists())

        var ramAvailable = 0L
        var swapUsed = 0L
        try {
            var st = 0L
            var sf = 0L
            File("/proc/meminfo").forEachLine { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    when (parts[0]) {
                        "MemAvailable:" -> ramAvailable = (parts[1].toLongOrNull() ?: 0L) / 1024L
                        "SwapTotal:" -> st = (parts[1].toLongOrNull() ?: 0L) / 1024L
                        "SwapFree:" -> sf = (parts[1].toLongOrNull() ?: 0L) / 1024L
                    }
                }
            }
            swapUsed = (st - sf).coerceAtLeast(0L)
        } catch (_: Exception) {}

        ChrootState(
            isRootGranted = true, // Di No-Root selalu dianggap siap tanpa dialog root
            isRootfsExists = exists,
            isMounted = exists, // Pada PRoot, mount point divirtualisasi secara dinamis saat proses jalan
            activeProcesses = 0,
            ramAvailableMb = ramAvailable,
            swapUsedMb = swapUsed,
            isCodeServerRunning = false,
            isSshRunning = false,
            rootfsPath = rootfs
        )
    }

    override suspend fun mountAll(rootfs: String): Result<String> = withContext(Dispatchers.IO) {
        ensureEnvironmentReadySync(rootfs)
        Result.success("PRoot memvirtualisasi mount points secara otomatis.")
    }

    override suspend fun unmountAll(rootfs: String): Result<String> = withContext(Dispatchers.IO) {
        Result.success("PRoot membersihkan mount otomatis saat proses selesai.")
    }

    override fun killProcessesAndUnmountSync(rootfs: String): Result<String> {
        return Result.success("Proses PRoot dibersihkan.")
    }

    override suspend fun ensureEnvironmentReady(rootfs: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureEnvironmentReadySync(rootfs)
    }

    override fun ensureEnvironmentReadySync(rootfs: String): Result<Unit> {
        try {
            val base = File(rootfs)
            File(base, "home/coder/projects").mkdirs()
            File(base, "tmp/devbox-bridge").mkdirs()
            File(base, "etc").mkdirs()
            File(base, "usr/local/bin").mkdirs()

            // Setup resolv.conf jika belum ada
            val resolv = File(base, "etc/resolv.conf")
            if (!resolv.exists()) {
                resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }

            // Setup hosts jika belum ada
            val hosts = File(base, "etc/hosts")
            if (!hosts.exists()) {
                hosts.writeText("127.0.0.1 localhost\n")
            }

            // Setup xdg-open bridge script
            val xdgOpen = File(base, "usr/local/bin/xdg-open")
            if (!xdgOpen.exists()) {
                xdgOpen.writeText("""
#!/bin/bash
TARGET="${'$'}1"
if [ -z "${'$'}TARGET" ]; then exit 0; fi
echo ""
echo "=================================================="
echo " [DevBox Browser / Open URL]"
echo " URL: ${'$'}TARGET"
echo "=================================================="
echo ""
echo "${'$'}TARGET" >> /tmp/.devbox_open_url 2>/dev/null || true
echo "${'$'}TARGET" > /tmp/devbox_latest_url 2>/dev/null || true
if [ -x /system/bin/am ]; then
    /system/bin/am start -a android.intent.action.VIEW -d "${'$'}TARGET" >/dev/null 2>&1 || true
fi
exit 0
                """.trimIndent())
                xdgOpen.setExecutable(true, false)
            }

            // Setup devbox-bridge script
            val bridge = File(base, "usr/local/bin/devbox-bridge")
            if (!bridge.exists()) {
                bridge.writeText("""
#!/bin/sh
BRIDGE_DIR="/tmp/devbox-bridge"
mkdir -p "${'$'}BRIDGE_DIR" 2>/dev/null || true

CMD="${'$'}1"
case "${'$'}CMD" in
    copy|clip)
        shift
        TEXT="${'$'}*"
        if [ -z "${'$'}TEXT" ]; then
            TEXT=${'$'}(cat)
        fi
        printf "%s" "${'$'}TEXT" > "${'$'}BRIDGE_DIR/clipboard.txt" 2>/dev/null || true
        printf "\033]52;c;%s\a" "${'$'}(printf "%s" "${'$'}TEXT" | base64 | tr -d '\r\n')" 2>/dev/null || true
        echo "DevBox Bridge: Teks disalin ke clipboard."
        ;;
    notify)
        shift
        TITLE="${'$'}{1:-DevBox Agent}"
        shift
        MSG="${'$'}*"
        printf "%s|%s\n" "${'$'}TITLE" "${'$'}MSG" >> "${'$'}BRIDGE_DIR/notifications.txt" 2>/dev/null || true
        echo "DevBox Bridge: Notifikasi dikirim ke Android."
        ;;
    *)
        echo "Penggunaan: devbox-bridge {copy|clip|notify} [argumen]"
        ;;
esac
exit 0
                """.trimIndent())
                bridge.setExecutable(true, false)
            }
        } catch (_: Exception) {}
        return Result.success(Unit)
    }

    override suspend fun runEnvironmentDiagnostics(): String = withContext(Dispatchers.IO) {
        val prootPath = getProotBinaryPath()
        val prootBin = File(prootPath)
        """
=== INFORMASI ENVIRONMENT NO-ROOT ===
Mode: PRoot Userspace (No Root Required)
PRoot Binary: $prootPath (Ukuran: ${prootBin.length()} bytes, Exists: ${prootBin.exists()})
Default Rootfs: $defaultRootfsPath
FilesDir: ${context.filesDir.absolutePath}
OS Architecture: ${System.getProperty("os.arch")}
        """.trimIndent()
    }
}

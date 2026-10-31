package com.arbdev.devbox.backend

import android.content.Context
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.util.AppLogger
import com.arbdev.devbox.util.TarExtractor
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
        if (primaryProot.exists() && primaryProot.length() > 50_000) {
            return primaryProot.absolutePath
        }

        // Fail-safe 1: Periksa direktori lib turunan dari sourceDir APK
        val parentLib = File(context.applicationInfo.sourceDir).parentFile?.let { File(it, "lib/arm64/libproot.so") }
        if (parentLib != null && parentLib.exists() && parentLib.length() > 50_000) {
            return parentLib.absolutePath
        }

        // Fail-safe 2: Ekstraksi darurat langsung dari APK sourceDir jika installer OS tidak mengekstraknya
        try {
            val binDir = File(context.filesDir, "bin").apply { mkdirs() }
            val fallbackBin = File(binDir, "libproot.so")
            if (fallbackBin.exists() && fallbackBin.length() > 50_000) {
                fallbackBin.setExecutable(true, false)
                return fallbackBin.absolutePath
            }
            java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                val libsToExtract = listOf(
                    "libproot.so",
                    "libproot-loader.so",
                    "libtalloc.so",
                    "libandroid-shmem.so"
                )
                for (libName in libsToExtract) {
                    val entry = zip.getEntry("lib/arm64-v8a/$libName")
                    if (entry != null) {
                        val outFile = File(binDir, libName)
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        outFile.setExecutable(true, false)
                    }
                }
            }
            if (fallbackBin.exists() && fallbackBin.length() > 50_000) {
                AppLogger.i("PRootBackend", "Ekstraksi fail-safe libproot.so selesai: ${fallbackBin.absolutePath}")
                return fallbackBin.absolutePath
            }
        } catch (e: Exception) {
            AppLogger.e("PRootBackend", "Gagal ekstraksi darurat libproot.so: ${e.message}")
        }

        return primaryProot.absolutePath
    }

    private fun getProotLoaderPath(): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val primaryLoader = File(nativeLibDir, "libproot-loader.so")
        if (primaryLoader.exists() && primaryLoader.length() > 10_000) {
            return primaryLoader.absolutePath
        }

        val parentLib = File(context.applicationInfo.sourceDir).parentFile?.let { File(it, "lib/arm64/libproot-loader.so") }
        if (parentLib != null && parentLib.exists() && parentLib.length() > 10_000) {
            return parentLib.absolutePath
        }

        try {
            val fallbackBin = File(context.filesDir, "bin/libproot-loader.so")
            if (fallbackBin.exists() && fallbackBin.length() > 10_000) {
                fallbackBin.setExecutable(true, false)
                return fallbackBin.absolutePath
            }
            fallbackBin.parentFile?.mkdirs()
            java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                val entry = zip.getEntry("lib/arm64-v8a/libproot-loader.so")
                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        fallbackBin.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    fallbackBin.setExecutable(true, false)
                    AppLogger.i("PRootBackend", "Ekstraksi fail-safe libproot-loader.so selesai: ${fallbackBin.absolutePath}")
                    return fallbackBin.absolutePath
                }
            }
        } catch (e: Exception) {
            AppLogger.e("PRootBackend", "Gagal ekstraksi darurat libproot-loader.so: ${e.message}")
        }

        return if (primaryLoader.exists()) primaryLoader.absolutePath else null
    }

    override fun startTerminalProcess(
        sessionId: String,
        targetRootfs: String,
        initCols: Int,
        initRows: Int
    ): Process {
        val prootBin = getProotBinaryPath()
        val prootTmp = File(context.filesDir, "proot_tmp").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }.absolutePath
        val prootShm = File(context.filesDir, "proot_shm").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }.absolutePath

        File(targetRootfs, "home/coder").mkdirs()
        File(targetRootfs, "tmp").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }
        File(targetRootfs, "dev/shm").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }
        File(targetRootfs, "proc").mkdirs()

        val debugLog = File(context.filesDir, "proot_debug.log")
        try {
            if (debugLog.exists()) debugLog.delete()
            debugLog.createNewFile()
        } catch (_: Exception) {}

        val shellCmd = """export TMPDIR=/tmp TEMP=/tmp TMP=/tmp LANG=C.UTF-8 LC_ALL=C.UTF-8 DEBIAN_FRONTEND=noninteractive; chmod 1777 /tmp /dev/shm 2>/dev/null || true; if [ -f /var/lib/dpkg/status ] && grep -q "half-configured" /var/lib/dpkg/status 2>/dev/null; then dpkg --configure -a 2>/dev/null || true; fi; if [ ! -f /etc/ssl/certs/ca-certificates.crt ] && [ -x /usr/sbin/update-ca-certificates ]; then update-ca-certificates --fresh 2>/dev/null || true; fi; if [ -x /usr/bin/script ] && /usr/bin/script -qefc "exit 0" /dev/null 2>/dev/null; then exec /usr/bin/script -qefc "shopt -s checkwinsize 2>/dev/null; tty > /tmp/.devbox_pts_$sessionId 2>/dev/null; echo ${'$'}${'$'} > /tmp/.devbox_pid_$sessionId 2>/dev/null; stty rows $initRows cols $initCols opost onlcr icrnl 2>/dev/null; exec /bin/bash -l" /dev/null; else exec /bin/bash -l -i; fi"""

        val pbArgs = mutableListOf(
            prootBin,
            "-0",
            "--link2symlink",
            "--sysvipc",
            "-r", targetRootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/dev/urandom:/dev/random",
            "-b", "/proc/self/fd:/dev/fd",
            "-b", "/proc/self/fd/0:/dev/stdin",
            "-b", "/proc/self/fd/1:/dev/stdout",
            "-b", "/proc/self/fd/2:/dev/stderr",
            "-b", "$prootTmp:/tmp",
            "-b", "$prootShm:/dev/shm"
        )

        // Binding penyimpanan internal Android (/sdcard)
        try {
            val sdcardDir = android.os.Environment.getExternalStorageDirectory()
            if (sdcardDir != null && sdcardDir.exists()) {
                File(targetRootfs, "sdcard").mkdirs()
                File(targetRootfs, "mnt/sdcard").mkdirs()
                pbArgs.add("-b")
                pbArgs.add("${sdcardDir.absolutePath}:/sdcard")
                pbArgs.add("-b")
                pbArgs.add("${sdcardDir.absolutePath}:/mnt/sdcard")
            }
        } catch (_: Exception) {}

        pbArgs.addAll(listOf(
            "-w", "/home/coder",
            "/bin/bash", "-c", shellCmd
        ))

        val pb = ProcessBuilder(pbArgs)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binDir = File(context.filesDir, "bin").absolutePath
        pb.environment().apply {
            put("LD_LIBRARY_PATH", "$nativeLibDir:$binDir:/system/lib64")
            put("GLIBC_TUNABLES", "glibc.pthread.rseq=0")
            put("HOME", "/home/coder")
            put("USER", "coder")
            put("LOGNAME", "coder")
            put("SHELL", "/bin/bash")
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("DEVBOX", "1")
            put("DEVBOX_SESSION_ID", sessionId)
            put("GH_NO_KEYRING", "1")
            put("PATH", "/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("TMPDIR", "/tmp")
            put("TEMP", "/tmp")
            put("TMP", "/tmp")
            put("LANG", "C.UTF-8")
            put("LC_ALL", "C.UTF-8")
            put("DEBIAN_FRONTEND", "noninteractive")
            put("PROOT_TMP_DIR", prootTmp)
            val loaderPath = getProotLoaderPath()
            if (loaderPath != null) {
                put("PROOT_LOADER", loaderPath)
                AppLogger.i("PRootBackend", "Menggunakan PROOT_LOADER: $loaderPath")
            }
            put("PROOT_IGNORE_MISSING_BINDINGS", "1")
            put("COLUMNS", initCols.toString())
            put("LINES", initRows.toString())
            remove("LD_PRELOAD")
            remove("ANDROID_DATA")
            remove("ANDROID_ROOT")
            remove("ANDROID_RUNTIME_ROOT")
            remove("ANDROID_I18N_ROOT")
            remove("ANDROID_TZDATA_ROOT")
            remove("EXTERNAL_STORAGE")
            remove("BOOTCLASSPATH")
            remove("DEX2OATBOOTCLASSPATH")
            remove("ASEC_MOUNTPOINT")
        }
        pb.redirectError(ProcessBuilder.Redirect.to(debugLog))
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
            val prootTmp = File(context.filesDir, "proot_tmp")
            File(prootTmp, ".devbox_ws_$sessionId").writeText("rows $rows cols $cols")
            File(targetRootfs, "tmp/.devbox_ws_$sessionId").writeText("rows $rows cols $cols")

            val ptsFile = File(prootTmp, ".devbox_pts_$sessionId")
            val pidFile = File(prootTmp, ".devbox_pid_$sessionId")
            val ptsDev = if (ptsFile.exists()) ptsFile.readText().trim() else null
            val bashPidStr = if (pidFile.exists()) pidFile.readText().trim() else null

            if (!ptsDev.isNullOrBlank() && ptsDev.startsWith("/dev/pts/")) {
                com.arbdev.devbox.terminal.NativeVTerm.setPtyWindowSize(ptsDev, cols, rows)
            }

            if (!bashPidStr.isNullOrBlank()) {
                val pid = bashPidStr.toIntOrNull()
                if (pid != null && pid > 0) {
                    try {
                        android.system.Os.kill(pid, 28) // 28 = SIGWINCH
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    override fun terminateSession(sessionId: String, targetRootfs: String) {
        try {
            val prootTmp = File(context.filesDir, "proot_tmp")
            val myPid = android.os.Process.myPid()
            val pidFiles = listOf(
                File(prootTmp, ".devbox_pid_$sessionId"),
                File(targetRootfs, "tmp/.devbox_pid_$sessionId")
            )
            for (f in pidFiles) {
                if (f.exists()) {
                    val pid = try { f.readText().trim().toIntOrNull() } catch (_: Throwable) { null }
                    if (pid != null && pid > 1 && pid != myPid) {
                        try { android.system.Os.kill(-pid, 9) } catch (_: Throwable) {}
                        try { android.system.Os.kill(pid, 9) } catch (_: Throwable) {}
                    }
                }
            }
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

        var activeProcs = 0
        var isCodeServer = false
        var isSsh = false
        val myPid = android.os.Process.myPid()
        val myUid = android.os.Process.myUid()

        try {
            val procDir = File("/proc")
            procDir.listFiles()?.forEach { entry ->
                val pid = entry.name.toIntOrNull() ?: return@forEach
                if (pid <= 1 || pid == myPid) return@forEach
                try {
                    val statusFile = File(entry, "status")
                    if (statusFile.exists()) {
                        val uidLine = statusFile.bufferedReader().useLines { lines ->
                            lines.firstOrNull { it.startsWith("Uid:") }
                        }
                        if (uidLine != null) {
                            val uid = uidLine.substringAfter("Uid:").trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
                            if (uid == myUid) {
                                activeProcs++
                                val cmdline = try { File(entry, "cmdline").readText() } catch (_: Throwable) { "" }
                                if (cmdline.contains("code-server")) isCodeServer = true
                                if (cmdline.contains("sshd")) isSsh = true
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        ChrootState(
            isRootGranted = true, // Di No-Root selalu dianggap siap tanpa dialog root
            isRootfsExists = exists,
            isMounted = exists, // Pada PRoot, mount point divirtualisasi secara dinamis saat proses jalan
            activeProcesses = activeProcs,
            ramAvailableMb = ramAvailable,
            swapUsedMb = swapUsed,
            isCodeServerRunning = isCodeServer,
            isSshRunning = isSsh,
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
        val myPid = android.os.Process.myPid()
        val myUid = android.os.Process.myUid()
        val prootTmp = File(context.filesDir, "proot_tmp")
        val killedPids = mutableListOf<Int>()

        // 1. Matikan proses dari file session PID
        try {
            prootTmp.listFiles()?.filter { it.name.startsWith(".devbox_pid_") }?.forEach { f ->
                val pid = try { f.readText().trim().toIntOrNull() } catch (_: Throwable) { null }
                if (pid != null && pid > 1 && pid != myPid) {
                    try { android.system.Os.kill(-pid, 9) } catch (_: Throwable) {}
                    try { android.system.Os.kill(pid, 9) } catch (_: Throwable) {}
                    killedPids.add(pid)
                }
            }
            val rootfsTmp = File(rootfs, "tmp")
            if (rootfsTmp.exists()) {
                rootfsTmp.listFiles()?.filter { it.name.startsWith(".devbox_pid_") }?.forEach { f ->
                    val pid = try { f.readText().trim().toIntOrNull() } catch (_: Throwable) { null }
                    if (pid != null && pid > 1 && pid != myPid) {
                        try { android.system.Os.kill(-pid, 9) } catch (_: Throwable) {}
                        try { android.system.Os.kill(pid, 9) } catch (_: Throwable) {}
                        killedPids.add(pid)
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Scan seluruh /proc untuk child/orphaned process milik UID aplikasi
        try {
            val procDir = File("/proc")
            procDir.listFiles()?.forEach { entry ->
                val pid = entry.name.toIntOrNull() ?: return@forEach
                if (pid <= 1 || pid == myPid) return@forEach
                try {
                    val statusFile = File(entry, "status")
                    if (statusFile.exists()) {
                        val uidLine = statusFile.bufferedReader().useLines { lines ->
                            lines.firstOrNull { it.startsWith("Uid:") }
                        }
                        if (uidLine != null) {
                            val uid = uidLine.substringAfter("Uid:").trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
                            if (uid == myUid) {
                                try { android.system.Os.kill(-pid, 9) } catch (_: Throwable) {}
                                try { android.system.Os.kill(pid, 9) } catch (_: Throwable) {}
                                killedPids.add(pid)
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // 3. Bersihkan file soket dan file sementara PRoot
        try {
            prootTmp.listFiles()?.forEach { file ->
                if (file.name.startsWith(".devbox_") || file.name.startsWith("proot-") || file.name.endsWith(".sock")) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {}

        return Result.success("Proses PRoot dihentikan: ${killedPids.distinct().size} proses dibersihkan.")
    }

    override suspend fun ensureEnvironmentReady(rootfs: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureEnvironmentReadySync(rootfs)
    }

    override fun ensureEnvironmentReadySync(rootfs: String): Result<Unit> {
        try {
            val base = File(rootfs)
            File(context.filesDir, "proot_tmp").apply { mkdirs(); setReadable(true, false); setWritable(true, false); setExecutable(true, false) }
            File(context.filesDir, "proot_shm").apply { mkdirs(); setReadable(true, false); setWritable(true, false); setExecutable(true, false) }
            File(base, "tmp").apply { mkdirs(); setReadable(true, false); setWritable(true, false); setExecutable(true, false) }
            File(base, "dev/shm").apply { mkdirs(); setReadable(true, false); setWritable(true, false); setExecutable(true, false) }
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

            // Setup /etc/passwd dan /etc/group untuk user coder
            val etcDir = File(base, "etc").apply { mkdirs() }
            val passwdFile = File(etcDir, "passwd")
            if (passwdFile.exists()) {
                val content = passwdFile.readText()
                if (!content.contains("coder:")) {
                    passwdFile.appendText("coder:x:1000:1000:DevBox User:/home/coder:/bin/bash\n")
                }
            } else {
                passwdFile.writeText("root:x:0:0:root:/root:/bin/bash\ncoder:x:1000:1000:DevBox User:/home/coder:/bin/bash\n")
            }
            val groupFile = File(etcDir, "group")
            val existingGroupText = if (groupFile.exists()) groupFile.readText() else ""
            val groupSb = StringBuilder(existingGroupText)
            if (!existingGroupText.contains("root:x:0:")) {
                groupSb.append("root:x:0:\n")
            }
            if (!existingGroupText.contains("coder:")) {
                groupSb.append("coder:x:1000:\n")
            }

            // Daftarkan seluruh supplementary GID Android ke /etc/group agar 'groups' tidak mengeluarkan pesan error
            try {
                val statusFile = File("/proc/self/status")
                if (statusFile.exists()) {
                    statusFile.forEachLine { line ->
                        if (line.startsWith("Groups:")) {
                            val gids = line.substringAfter("Groups:").trim().split("\\s+".toRegex())
                            for (gid in gids) {
                                val g = gid.trim()
                                if (g.isNotEmpty() && g.all { it.isDigit() }) {
                                    val gidPattern = ":$g:"
                                    if (!groupSb.contains(gidPattern)) {
                                        val groupName = when (g) {
                                            "3003" -> "aid_inet"
                                            "1079" -> "aid_sdcard"
                                            "9997" -> "aid_everybody"
                                            else -> "aid_$g"
                                        }
                                        groupSb.append("$groupName:x:$g:coder\n")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val finalGroupText = groupSb.toString()
            if (finalGroupText != existingGroupText) {
                groupFile.writeText(finalGroupText)
            }
            val sudoersDir = File(etcDir, "sudoers.d").apply { mkdirs() }
            val sudoersCoder = File(sudoersDir, "coder")
            if (!sudoersCoder.exists()) {
                sudoersCoder.writeText("coder ALL=(ALL:ALL) NOPASSWD:ALL\n")
            }

            // Setup /etc/environment dengan TMPDIR, locale UTF-8, dan GLIBC_TUNABLES
            val etcEnv = File(base, "etc/environment")
            val defaultEnv = """
                TMPDIR="/tmp"
                TEMP="/tmp"
                TMP="/tmp"
                LANG="C.UTF-8"
                LC_ALL="C.UTF-8"
                DEBIAN_FRONTEND="noninteractive"
                GLIBC_TUNABLES="glibc.pthread.rseq=0"
                PATH="/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            """.trimIndent() + "\n"
            etcEnv.writeText(defaultEnv)

            // Aktifkan universe repository jika ada ubuntu.sources
            try {
                val ubuntuSources = File(base, "etc/apt/sources.list.d/ubuntu.sources")
                if (ubuntuSources.exists()) {
                    val srcText = ubuntuSources.readText()
                    if (srcText.contains("Components: main\n") || srcText.contains("Components: main ")) {
                        ubuntuSources.writeText(srcText.replace("Components: main", "Components: main universe restricted multiverse"))
                    }
                }
            } catch (_: Exception) {}

            // Setup shortcut link ~/storage ke /sdcard
            try {
                val storageLink = File(base, "home/coder/storage")
                if (!storageLink.exists()) {
                    android.system.Os.symlink("/sdcard", storageLink.absolutePath)
                }
            } catch (_: Exception) {}

            // Setup .bashrc, .profile, .bash_profile modern DevBox untuk user coder
            val homeCoder = File(base, "home/coder").apply { mkdirs() }
            val bashrcFile = File(homeCoder, ".bashrc")
            if (!bashrcFile.exists() || !bashrcFile.readText().contains("Environment v4")) {
                bashrcFile.writeText(com.arbdev.devbox.data.ChrootManager.getBashrcContent())
                bashrcFile.setReadable(true, false)
                bashrcFile.setWritable(true, true)
            }
            val profileFile = File(homeCoder, ".profile")
            if (!profileFile.exists()) {
                profileFile.writeText(com.arbdev.devbox.data.ChrootManager.getProfileContent())
                profileFile.setReadable(true, false)
                profileFile.setWritable(true, true)
            }
            val bashProfileFile = File(homeCoder, ".bash_profile")
            if (!bashProfileFile.exists()) {
                bashProfileFile.writeText(com.arbdev.devbox.data.ChrootManager.getProfileContent())
                bashProfileFile.setReadable(true, false)
                bashProfileFile.setWritable(true, true)
            }

            // Sediakan juga di /root/.bashrc & /root/.profile
            val rootDir = File(base, "root").apply { mkdirs() }
            val rootBashrc = File(rootDir, ".bashrc")
            if (!rootBashrc.exists()) {
                rootBashrc.writeText(com.arbdev.devbox.data.ChrootManager.getBashrcContent())
            }
            val rootProfile = File(rootDir, ".profile")
            if (!rootProfile.exists()) {
                rootProfile.writeText(com.arbdev.devbox.data.ChrootManager.getProfileContent())
            }

            // Pastikan binary bash dan direktori esensial selalu executable
            File(base, "usr/bin/bash").let { if (it.exists()) it.setExecutable(true, false) }
            File(base, "bin/bash").let { if (it.exists()) it.setExecutable(true, false) }
            homeCoder.setReadable(true, false)
            homeCoder.setWritable(true, true)
            homeCoder.setExecutable(true, false)
            File(base, "tmp").let { if (it.exists()) { it.setReadable(true, false); it.setWritable(true, true); it.setExecutable(true, false) } }
        } catch (_: Exception) {}
        return Result.success(Unit)
    }

    override suspend fun extractRootfs(
        tarPath: String,
        targetRootfs: String,
        onProgress: ((Float, String) -> Unit)?,
        onLogLine: ((String) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetDir = File(targetRootfs)
        targetDir.mkdirs()

        onLogLine?.invoke("Membuat direktori target $targetRootfs...")
        onLogLine?.invoke("Mengekstrak file archive ke $targetRootfs via TarExtractor...")

        val archiveFile = File(tarPath)
        val extractRes = TarExtractor.extractTarGz(archiveFile, targetDir, onProgress, onLogLine)
        if (extractRes.isFailure) {
            val err = extractRes.exceptionOrNull()?.message ?: "Gagal mengekstrak archive"
            onLogLine?.invoke("INSTALL_FAILED: $err")
            return@withContext Result.failure(Exception(err))
        }

        onLogLine?.invoke("Konfigurasi dasar jaringan dan user...")
        try {
            ensureEnvironmentReadySync(targetRootfs)

            val etcDir = File(targetDir, "etc").apply { mkdirs() }
            val sudoersDir = File(etcDir, "sudoers.d").apply { mkdirs() }
            File(targetDir, "home/coder").mkdirs()
            File(targetDir, "tmp").mkdirs()

            // Setup resolv.conf
            File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

            // Setup hosts
            File(etcDir, "hosts").writeText("127.0.0.1 localhost\n")

            // Setup passwd
            val passwdFile = File(etcDir, "passwd")
            val passwdContent = if (passwdFile.exists()) passwdFile.readText() else ""
            if (!passwdContent.contains("coder:")) {
                passwdFile.appendText("coder:x:1000:1000:DevBox User:/home/coder:/bin/bash\n")
            }

            // Setup group
            val groupFile = File(etcDir, "group")
            val groupContent = if (groupFile.exists()) groupFile.readText() else ""
            if (!groupContent.contains("coder:")) {
                groupFile.appendText("coder:x:1000:\n")
            }

            // Setup passwordless sudo untuk coder
            val sudoersCoder = File(sudoersDir, "coder")
            sudoersCoder.writeText("coder ALL=(ALL:ALL) NOPASSWD:ALL\n")
            try {
                android.system.Os.chmod(sudoersCoder.absolutePath, 288) // 0440 octal
            } catch (_: Exception) {}

            // Post-extraction sweep: Pastikan seluruh file binary dan direktori memiliki hak akses eksekusi
            fun applyPermissionsRecursively(file: File) {
                if (file.isDirectory) {
                    file.setReadable(true, false)
                    file.setWritable(true, true)
                    file.setExecutable(true, false)
                    try { android.system.Os.chmod(file.absolutePath, 493) } catch (_: Exception) {}
                    file.listFiles()?.forEach { applyPermissionsRecursively(it) }
                } else if (file.isFile) {
                    file.setReadable(true, false)
                    val p = file.absolutePath
                    if (p.contains("/bin/") || p.contains("/sbin/") || p.contains("ld-linux") || file.name.endsWith(".so") || file.name.endsWith(".sh")) {
                        file.setExecutable(true, false)
                        try { android.system.Os.chmod(file.absolutePath, 493) } catch (_: Exception) {}
                    }
                }
            }
            applyPermissionsRecursively(targetDir)
        } catch (e: Exception) {
            AppLogger.w("PRootBackend", "Peringatan pasca konfigurasi rootfs: ${e.message}")
        }

        val bashExists = File(targetDir, "bin/bash").exists() || File(targetDir, "usr/bin/bash").exists()
        if (bashExists) {
            onLogLine?.invoke("INSTALL_SUCCESS")
            onLogLine?.invoke("=== UBUNTU BASE RESMI BERHASIL DIPASANG ===")
            Result.success(Unit)
        } else {
            val err = "INSTALL_FAILED: Binary bash tidak ditemukan setelah ekstraksi."
            onLogLine?.invoke(err)
            Result.failure(Exception(err))
        }
    }

    override suspend fun runEnvironmentDiagnostics(): String = withContext(Dispatchers.IO) {
        val prootPath = getProotBinaryPath()
        val prootBin = File(prootPath)
        val loaderPath = getProotLoaderPath()
        val prootTmp = File(context.filesDir, "proot_tmp").apply { mkdirs() }.absolutePath

        val report = StringBuilder()
        report.appendLine("=== INFORMASI ENVIRONMENT NO-ROOT ===")
        report.appendLine("Mode: PRoot Userspace (No Root Required)")
        report.appendLine("PRoot Binary: $prootPath (Ukuran: ${prootBin.length()} bytes, Exists: ${prootBin.exists()}, Executable: ${prootBin.canExecute()})")
        report.appendLine("PRoot Loader: ${loaderPath ?: "Embedded Fallback"}")
        report.appendLine("Default Rootfs: $defaultRootfsPath")
        report.appendLine("FilesDir: ${context.filesDir.absolutePath}")
        report.appendLine("OS Architecture: ${System.getProperty("os.arch")}")

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binDir = File(context.filesDir, "bin").absolutePath

        // Test 1: Jalankan binary proot mandiri (proot --version)
        try {
            val pb1 = ProcessBuilder(prootPath, "--version")
            pb1.environment()["LD_LIBRARY_PATH"] = "$nativeLibDir:$binDir:/system/lib64"
            pb1.redirectErrorStream(true)
            val p1 = pb1.start()
            val out1 = p1.inputStream.bufferedReader().readText().trim()
            val code1 = p1.waitFor()
            report.appendLine("--- TEST 1: proot --version ---")
            report.appendLine("Exit code: $code1, Output:\n$out1")
        } catch (e: Exception) {
            report.appendLine("--- TEST 1 ERROR: ${e.message} ---")
        }

        // Test 2: Jalankan proot dengan command minimal
        try {
            val pb2Args = mutableListOf(
                prootPath,
                "-0",
                "--link2symlink",
                "--sysvipc",
                "-r", defaultRootfsPath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/dev/urandom:/dev/random",
                "-b", "/proc/self/fd:/dev/fd",
                "-b", "/proc/self/fd/0:/dev/stdin",
                "-b", "/proc/self/fd/1:/dev/stdout",
                "-b", "/proc/self/fd/2:/dev/stderr",
                "-b", "$prootTmp:/tmp",
                "/bin/sh", "-c", "echo PROOT_BASIC_OK"
            )
            val pb2 = ProcessBuilder(pb2Args)
            pb2.environment().apply {
                put("LD_LIBRARY_PATH", "$nativeLibDir:$binDir:/system/lib64")
                put("GLIBC_TUNABLES", "glibc.pthread.rseq=0")
                loaderPath?.let { put("PROOT_LOADER", it) }
                put("PROOT_IGNORE_MISSING_BINDINGS", "1")
                put("PROOT_TMP_DIR", prootTmp)
            }
            pb2.redirectErrorStream(true)
            val p2 = pb2.start()
            val out2 = p2.inputStream.bufferedReader().readText().trim()
            val code2 = p2.waitFor()
            report.appendLine("--- TEST 2: proot minimal shell ---")
            report.appendLine("Exit code: $code2, Output:\n$out2")
        } catch (e: Exception) {
            report.appendLine("--- TEST 2 ERROR: ${e.message} ---")
        }

        // Test 3: Tarik Logcat Sistem Android (Kernel audit, seccomp, linker errors)
        try {
            val pLog = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "100", "-v", "threadtime"))
            val lines = pLog.inputStream.bufferedReader().readLines()
            val filtered = lines.filter { line ->
                val l = line.lowercase()
                l.contains("proot") || l.contains("seccomp") || l.contains("audit") ||
                l.contains("denied") || l.contains("fatal") || l.contains("sig") ||
                l.contains("linker") || l.contains("crash") || l.contains("tombstone") ||
                l.contains("devbox")
            }.takeLast(35)
            report.appendLine("--- RELEVANT LOGCAT (Entri Sistem: ${filtered.size}) ---")
            if (filtered.isNotEmpty()) {
                filtered.forEach { report.appendLine(it) }
            } else {
                report.appendLine("Tidak ada catatan penolakan seccomp/audit kernel di 100 baris logcat terakhir.")
            }
        } catch (e: Exception) {
            report.appendLine("--- LOGCAT READ ERROR: ${e.message} ---")
        }

        val fullReport = report.toString()
        AppLogger.i("Diagnostics", fullReport)
        fullReport
    }
}

package com.arbdev.devbox.data

data class ChrootState(
    val isRootGranted: Boolean = false,
    val isRootfsExists: Boolean = false,
    val isMounted: Boolean = false,
    val activeProcesses: Int = 0,
    val ramAvailableMb: Long = 0,
    val swapUsedMb: Long = 0,
    val isCodeServerRunning: Boolean = false,
    val isSshRunning: Boolean = false,
    val rootfsPath: String = ChrootManager.DEFAULT_ROOTFS
)

object ChrootManager {
    // Mandiri dan terisolasi penuh dari chroot lain dan Termux
    const val DEFAULT_ROOTFS = "/data/local/devbox-chroot"

    suspend fun getChrootState(rootfs: String = DEFAULT_ROOTFS): ChrootState {
        val rootOk = RootExecutor.isRootAvailable()
        if (!rootOk) {
            return ChrootState(isRootGranted = false, rootfsPath = rootfs)
        }

        val existsRes = RootExecutor.execute("[ -d '$rootfs' ] && [ -x '$rootfs/bin/bash' -o -x '$rootfs/bin/sh' ] && echo 'EXISTS'")
        val exists = existsRes.getOrNull()?.stdout?.contains("EXISTS") == true

        if (!exists) {
            return ChrootState(isRootGranted = true, isRootfsExists = false, rootfsPath = rootfs)
        }

        val mountRes = RootExecutor.execute("mountpoint -q '$rootfs/proc' && echo 'MOUNTED'")
        val isMounted = mountRes.getOrNull()?.stdout?.contains("MOUNTED") == true

        // Hitung proses yang berjalan khusus di dalam rootfs DevBox
        val procCountRes = RootExecutor.execute(
            "ps -eo pid,args | grep -E 'chroot $rootfs' | grep -v grep | wc -l"
        )
        val activeProcs = procCountRes.getOrNull()?.stdout?.trim()?.toIntOrNull() ?: 0

        // Parse informasi RAM dan Swap
        val memRes = RootExecutor.execute("cat /proc/meminfo")
        var ramAvailable = 0L
        var swapTotal = 0L
        var swapFree = 0L

        memRes.getOrNull()?.stdout?.lines()?.forEach { line ->
            when {
                line.startsWith("MemAvailable:") -> {
                    val kb = line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                    ramAvailable = kb / 1024
                }
                line.startsWith("SwapTotal:") -> {
                    val kb = line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                    swapTotal = kb / 1024
                }
                line.startsWith("SwapFree:") -> {
                    val kb = line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                    swapFree = kb / 1024
                }
            }
        }
        val swapUsed = (swapTotal - swapFree).coerceAtLeast(0L)

        // Cek Code-Server port 8080
        val port8080 = RootExecutor.execute("netstat -tuln 2>/dev/null | grep -q ':8080 ' && echo 'LISTEN'")
        val isCodeServer = port8080.getOrNull()?.stdout?.contains("LISTEN") == true

        // Cek SSH port 2222
        val portSsh = RootExecutor.execute("netstat -tuln 2>/dev/null | grep -q ':2222 ' && echo 'LISTEN'")
        val isSsh = portSsh.getOrNull()?.stdout?.contains("LISTEN") == true

        return ChrootState(
            isRootGranted = true,
            isRootfsExists = true,
            isMounted = isMounted,
            activeProcesses = activeProcs,
            ramAvailableMb = ramAvailable,
            swapUsedMb = swapUsed,
            isCodeServerRunning = isCodeServer,
            isSshRunning = isSsh,
            rootfsPath = rootfs
        )
    }

    suspend fun mountAll(rootfs: String = DEFAULT_ROOTFS): Result<String> {
        val script = """
            mkdir -p '$rootfs/dev' '$rootfs/dev/pts' '$rootfs/proc' '$rootfs/sys' '$rootfs/mnt/sdcard' '$rootfs/tmp'
            chmod 1777 '$rootfs/tmp' 2>/dev/null || true
            mountpoint -q '$rootfs/dev' || mount --bind /dev '$rootfs/dev'
            mountpoint -q '$rootfs/dev/pts' || mount -t devpts devpts '$rootfs/dev/pts'
            mountpoint -q '$rootfs/proc' || mount -t proc proc '$rootfs/proc'
            mountpoint -q '$rootfs/sys' || mount -t sysfs sysfs '$rootfs/sys'
            mountpoint -q '$rootfs/mnt/sdcard' || mount --bind /sdcard '$rootfs/mnt/sdcard'
            grep -qE '^127\.0\.0\.1[[:space:]]+localhost' '$rootfs/etc/hosts' 2>/dev/null || echo '127.0.0.1 localhost' >> '$rootfs/etc/hosts'
            echo 'SUCCESS'
        """.trimIndent()

        val res = RootExecutor.execute(script)
        return if (res.getOrNull()?.isSuccess == true && res.getOrNull()?.stdout?.contains("SUCCESS") == true) {
            Result.success("Mount chroot DevBox berhasil dipasang.")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal memasang mount chroot DevBox."))
        }
    }

    suspend fun unmountAll(rootfs: String = DEFAULT_ROOTFS): Result<String> {
        val script = """
            fuser -k -9 '$rootfs' 2>/dev/null || true
            umount -l '$rootfs/mnt/sdcard' 2>/dev/null || true
            umount -l '$rootfs/sys' 2>/dev/null || true
            umount -l '$rootfs/proc' 2>/dev/null || true
            umount -l '$rootfs/dev/pts' 2>/dev/null || true
            umount -l '$rootfs/dev' 2>/dev/null || true
            echo 'CLEAN'
        """.trimIndent()

        val res = RootExecutor.execute(script)
        return if (res.getOrNull()?.isSuccess == true) {
            Result.success("Semua mount chroot DevBox berhasil dibersihkan.")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal unmount chroot DevBox."))
        }
    }

    fun killDevboxProcessesAndUnmountSync(rootfs: String = DEFAULT_ROOTFS): Result<String> {
        val script = """
            fuser -k -9 '$rootfs' 2>/dev/null || true
            ps -eo pid,args | grep 'chroot $rootfs' | grep -v grep | awk '{print ${'$'}1}' | xargs -r kill -9 2>/dev/null || true
            umount -l '$rootfs/mnt/sdcard' 2>/dev/null || true
            umount -l '$rootfs/sys' 2>/dev/null || true
            umount -l '$rootfs/proc' 2>/dev/null || true
            umount -l '$rootfs/dev/pts' 2>/dev/null || true
            umount -l '$rootfs/dev' 2>/dev/null || true
            echo 'TERMINATED'
        """.trimIndent()

        val res = RootExecutor.executeSync(script)
        return if (res.getOrNull()?.isSuccess == true) {
            Result.success("Semua proses dan container DevBox berhasil dihentikan total.")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal mematikan container DevBox."))
        }
    }

    suspend fun killDevboxProcessesAndUnmount(rootfs: String = DEFAULT_ROOTFS): Result<String> {
        return killDevboxProcessesAndUnmountSync(rootfs)
    }

    suspend fun toggleCodeServer(enable: Boolean, rootfs: String = DEFAULT_ROOTFS): Result<Unit> {
        return if (enable) {
            mountAll(rootfs)
            val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            val cmd = "chroot '$rootfs' /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash -lc 'nohup code-server --bind-addr 127.0.0.1:8080 >/tmp/code-server.log 2>&1 &'"
            RootExecutor.execute(cmd)
            Result.success(Unit)
        } else {
            RootExecutor.execute("fuser -k -9 8080/tcp 2>/dev/null || pkill -f code-server || true")
            Result.success(Unit)
        }
    }

    suspend fun initDevboxRootfs(sourceTarGz: String? = null, rootfs: String = DEFAULT_ROOTFS): Result<String> {
        val script = if (!sourceTarGz.isNullOrBlank()) {
            """
                mkdir -p '$rootfs'
                tar -xzf '$sourceTarGz' -C '$rootfs'
                echo 'INIT_OK'
            """.trimIndent()
        } else {
            """
                mkdir -p '$rootfs/bin' '$rootfs/etc' '$rootfs/dev' '$rootfs/proc' '$rootfs/sys' '$rootfs/home/coder' '$rootfs/tmp'
                chmod 1777 '$rootfs/tmp' 2>/dev/null || true
                echo 'INIT_DIR_OK'
            """.trimIndent()
        }

        val res = RootExecutor.execute(script)
        return if (res.getOrNull()?.isSuccess == true) {
            Result.success("Inisialisasi direktori rootfs DevBox berhasil.")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal inisialisasi rootfs."))
        }
    }
}

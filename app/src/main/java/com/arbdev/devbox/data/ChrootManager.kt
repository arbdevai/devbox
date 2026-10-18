package com.arbdev.devbox.data

data class ChrootState(
    val isRootGranted: Boolean = false,
    val isRootfsExists: Boolean = false,
    val isMounted: Boolean = false,
    val activeProcesses: Int = 0,
    val ramAvailableMb: Long = 0,
    val swapUsedMb: Long = 0,
    val isCodeServerRunning: Boolean = false,
    val isSshRunning: Boolean = false
)

object ChrootManager {
    const val DEFAULT_ROOTFS = "/data/local/ubuntu-chroot"

    suspend fun getChrootState(rootfs: String = DEFAULT_ROOTFS): ChrootState {
        val rootOk = RootExecutor.isRootAvailable()
        if (!rootOk) {
            return ChrootState(isRootGranted = false)
        }

        val existsRes = RootExecutor.execute("[ -d '$rootfs' ] && echo 'EXISTS'")
        val exists = existsRes.getOrNull()?.stdout?.contains("EXISTS") == true

        if (!exists) {
            return ChrootState(isRootGranted = true, isRootfsExists = false)
        }

        val mountRes = RootExecutor.execute("mountpoint -q '$rootfs/proc' && echo 'MOUNTED'")
        val isMounted = mountRes.getOrNull()?.stdout?.contains("MOUNTED") == true

        // Count chroot processes
        val procCountRes = RootExecutor.execute(
            "ps -eo pid,args | grep -E 'chroot $rootfs|runuser.*coder' | grep -v grep | wc -l"
        )
        val activeProcs = procCountRes.getOrNull()?.stdout?.trim()?.toIntOrNull() ?: 0

        // Parse meminfo
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

        // Check if Code-Server port 8080 is listening
        val port8080 = RootExecutor.execute("netstat -tuln 2>/dev/null | grep -q ':8080 ' && echo 'LISTEN'")
        val isCodeServer = port8080.getOrNull()?.stdout?.contains("LISTEN") == true

        // Check SSH port 22 or 2222
        val portSsh = RootExecutor.execute("netstat -tuln 2>/dev/null | grep -E ':(22|2222) ' && echo 'LISTEN'")
        val isSsh = portSsh.getOrNull()?.stdout?.contains("LISTEN") == true

        return ChrootState(
            isRootGranted = true,
            isRootfsExists = true,
            isMounted = isMounted,
            activeProcesses = activeProcs,
            ramAvailableMb = ramAvailable,
            swapUsedMb = swapUsed,
            isCodeServerRunning = isCodeServer,
            isSshRunning = isSsh
        )
    }

    suspend fun mountAll(rootfs: String = DEFAULT_ROOTFS): Result<String> {
        val script = """
            mkdir -p '$rootfs/dev' '$rootfs/proc' '$rootfs/sys' '$rootfs/mnt/sdcard'
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
            Result.success("Chroot mounts mounted successfully.")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Failed to mount chroot."))
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
            Result.success("Semua mount chroot berhasil dibersihkan.")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal unmount chroot."))
        }
    }

    suspend fun toggleCodeServer(enable: Boolean, rootfs: String = DEFAULT_ROOTFS): Result<Unit> {
        return if (enable) {
            mountAll(rootfs)
            val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            val cmd = "chroot '$rootfs' /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash -lc 'nohup code-server --bind-addr 127.0.0.1:8080 >/tmp/code-server.log 2>&1 &'"
            RootExecutor.execute(cmd)
            Result.success(Unit)
        } else {
            RootExecutor.execute("pkill -f code-server || true")
            Result.success(Unit)
        }
    }
}

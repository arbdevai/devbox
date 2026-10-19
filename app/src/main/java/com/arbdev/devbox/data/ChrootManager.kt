package com.arbdev.devbox.data

import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    const val DEFAULT_ROOTFS = "/data/local/devbox-chroot"
    var activeRootfs: String = DEFAULT_ROOTFS

    suspend fun getChrootState(rootfs: String = activeRootfs): ChrootState {
        val rootOk = RootExecutor.isRootAvailable()
        if (!rootOk) {
            AppLogger.w("ChrootManager", "Superuser (su) tidak terdeteksi.")
            return ChrootState(isRootGranted = false, rootfsPath = rootfs)
        }

        val checkCmd = """
            if [ -d '$rootfs' ]; then
                if [ -x '$rootfs/bin/bash' ] || [ -x '$rootfs/usr/bin/bash' ] || [ -x '$rootfs/bin/sh' ]; then
                    echo 'EXISTS'
                else
                    echo 'DIR_EMPTY'
                fi
            else
                echo 'NOT_FOUND'
            fi
        """.trimIndent()

        val existsRes = RootExecutor.execute(checkCmd)
        val statusText = existsRes.getOrNull()?.stdout?.trim() ?: "NOT_FOUND"

        val isRootfsExists = statusText == "EXISTS"
        if (!isRootfsExists) {
            AppLogger.w("ChrootManager", "Status rootfs di $rootfs: $statusText")
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

    suspend fun mountAll(rootfs: String = activeRootfs): Result<String> {
        AppLogger.i("ChrootManager", "Memasang mount points untuk $rootfs...")
        val script = """
            mkdir -p '$rootfs/dev' '$rootfs/dev/pts' '$rootfs/proc' '$rootfs/sys' '$rootfs/mnt/sdcard' '$rootfs/tmp'
            chmod 1777 '$rootfs/tmp' 2>/dev/null || true
            mountpoint -q '$rootfs/dev' || mount --bind /dev '$rootfs/dev'
            mountpoint -q '$rootfs/dev/pts' || mount -t devpts devpts '$rootfs/dev/pts'
            mountpoint -q '$rootfs/proc' || mount -t proc proc '$rootfs/proc'
            mountpoint -q '$rootfs/sys' || mount -t sysfs sysfs '$rootfs/sys'
            mountpoint -q '$rootfs/mnt/sdcard' || mount --bind /sdcard '$rootfs/mnt/sdcard'
            grep -qE '^127\.0\.0\.1[[:space:]]+localhost' '$rootfs/etc/hosts' 2>/dev/null || echo '127.0.0.1 localhost' >> '$rootfs/etc/hosts' 2>/dev/null || true
            echo 'SUCCESS'
        """.trimIndent()

        val res = RootExecutor.execute(script)
        return if (res.getOrNull()?.isSuccess == true && res.getOrNull()?.stdout?.contains("SUCCESS") == true) {
            AppLogger.i("ChrootManager", "Mount DevBox berhasil dipasang.")
            Result.success("Mount chroot DevBox berhasil dipasang.")
        } else {
            val err = res.getOrNull()?.stderr ?: "Gagal memasang mount chroot DevBox."
            AppLogger.e("ChrootManager", err)
            Result.failure(Exception(err))
        }
    }

    suspend fun unmountAll(rootfs: String = activeRootfs): Result<String> {
        AppLogger.i("ChrootManager", "Membersihkan mount points untuk $rootfs...")
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
            AppLogger.i("ChrootManager", "Mount DevBox berhasil dibersihkan.")
            Result.success("Semua mount chroot DevBox berhasil dibersihkan.")
        } else {
            val err = res.getOrNull()?.stderr ?: "Gagal unmount chroot DevBox."
            AppLogger.e("ChrootManager", err)
            Result.failure(Exception(err))
        }
    }

    fun killDevboxProcessesAndUnmountSync(rootfs: String = activeRootfs): Result<String> {
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
            AppLogger.i("ChrootManager", "Semua proses & container DevBox dimatikan total.")
            Result.success("Semua proses dan container DevBox berhasil dihentikan total.")
        } else {
            val err = res.getOrNull()?.stderr ?: "Gagal mematikan container DevBox."
            AppLogger.e("ChrootManager", err)
            Result.failure(Exception(err))
        }
    }

    suspend fun killDevboxProcessesAndUnmount(rootfs: String = activeRootfs): Result<String> {
        return killDevboxProcessesAndUnmountSync(rootfs)
    }

    suspend fun toggleCodeServer(enable: Boolean, rootfs: String = activeRootfs): Result<Unit> {
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

    suspend fun cloneExistingChroot(
        sourceChroot: String = "/data/local/ubuntu-chroot",
        targetRootfs: String = activeRootfs
    ): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i("ChrootManager", "Memulai duplikasi dari $sourceChroot ke $targetRootfs...")
        val script = """
            if [ ! -d '$sourceChroot' ] || [ ! -x '$sourceChroot/bin/bash' ]; then
                echo "SOURCE_NOT_FOUND: $sourceChroot tidak memiliki binary bash."
                exit 1
            fi
            mkdir -p '$targetRootfs'
            echo "Menyalin file rootfs... (ini memakan waktu 15-45 detik)"
            tar --exclude='./proc/*' --exclude='./sys/*' --exclude='./dev/*' --exclude='./mnt/*' --exclude='./tmp/*' -C '$sourceChroot' -cf - . | tar -C '$targetRootfs' -xf -
            mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp'
            chmod 1777 '$targetRootfs/tmp' 2>/dev/null || true
            echo "CLONE_SUCCESS"
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val out = res.getOrNull()?.stdout ?: ""
        return@withContext if (out.contains("CLONE_SUCCESS")) {
            AppLogger.i("ChrootManager", "Duplikasi rootfs berhasil ke $targetRootfs!")
            Result.success("Duplikasi rootfs mandiri berhasil ke $targetRootfs!")
        } else {
            val err = "Gagal kloning: ${res.getOrNull()?.stderr.takeIf { !it.isNullOrBlank() } ?: out}"
            AppLogger.e("ChrootManager", err)
            Result.failure(Exception(err))
        }
    }

    suspend fun downloadUbuntuOfficialRootfs(
        targetRootfs: String = activeRootfs
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "https://partner-images.canonical.com/core/noble/current/ubuntu-noble-core-cloudimg-arm64-root.tar.gz"
        AppLogger.i("ChrootManager", "Mengunduh Ubuntu 24.04 ARM64 resmi dari Canonical ($url)...")

        val script = """
            mkdir -p '$targetRootfs'
            TAR_FILE="/sdcard/ubuntu-noble-arm64.tar.gz"
            echo "Mengunduh archive rootfs resmi..."
            curl -k -L '$url' -o "${'$'}TAR_FILE" || wget --no-check-certificate '$url' -O "${'$'}TAR_FILE"
            if [ ! -s "${'$'}TAR_FILE" ]; then
                echo "DOWNLOAD_FAILED: File unduhan kosong atau gagal."
                exit 1
            fi
            echo "Mengekstrak rootfs ke $targetRootfs..."
            tar -xzf "${'$'}TAR_FILE" -C '$targetRootfs'
            rm -f "${'$'}TAR_FILE"
            mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp'
            chmod 1777 '$targetRootfs/tmp' 2>/dev/null || true
            echo "nameserver 8.8.8.8" > '$targetRootfs/etc/resolv.conf'
            echo "127.0.0.1 localhost" > '$targetRootfs/etc/hosts'
            echo "DOWNLOAD_EXTRACT_SUCCESS"
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val out = res.getOrNull()?.stdout ?: ""
        return@withContext if (out.contains("DOWNLOAD_EXTRACT_SUCCESS")) {
            AppLogger.i("ChrootManager", "Ubuntu 24.04 ARM64 resmi berhasil dipasang di $targetRootfs!")
            Result.success("Ubuntu 24.04 ARM64 resmi berhasil dipasang!")
        } else {
            val err = "Gagal unduh/ekstrak: ${res.getOrNull()?.stderr.takeIf { !it.isNullOrBlank() } ?: out}"
            AppLogger.e("ChrootManager", err)
            Result.failure(Exception(err))
        }
    }

    suspend fun runEnvironmentDiagnostics(): String = withContext(Dispatchers.IO) {
        AppLogger.i("Diagnostics", "Menjalankan diagnostik lingkungan menyeluruh...")
        val diagScript = """
            echo "=== INFORMASI SUPERUSER & KERNEL ==="
            id
            uname -a
            which su
            echo ""
            echo "=== CEK DIREKTORI /data/local ==="
            ls -la /data/local
            echo ""
            echo "=== CEK ROOTFS TARGET ($activeRootfs) ==="
            if [ -d '$activeRootfs' ]; then
                echo "Direktori $activeRootfs DITEMUKAN:"
                ls -la '$activeRootfs'
            else
                echo "Direktori $activeRootfs TIDAK ADA."
            fi
            echo ""
            echo "=== CEK CHROOT LAMA (/data/local/ubuntu-chroot) ==="
            if [ -d '/data/local/ubuntu-chroot' ]; then
                echo "ubuntu-chroot lama DITEMUKAN. File /bin/bash:"
                ls -lh '/data/local/ubuntu-chroot/bin/bash' 2>/dev/null || echo "bash tidak ditemukan"
            else
                echo "ubuntu-chroot lama TIDAK DITEMUKAN."
            fi
            echo ""
            echo "=== STATUS MEMORI & DISK ==="
            free -m
            df -h /data
            echo ""
            echo "=== ACTIVE MOUNTS TERKAIT CHROOT ==="
            mount | grep -E 'chroot|ubuntu|devbox' || echo "Tidak ada mount chroot aktif"
            echo "=== SELESAI ==="
        """.trimIndent()

        val res = RootExecutor.execute(diagScript)
        val report = res.getOrNull()?.stdout ?: "Gagal mendapatkan laporan diagnostik."
        AppLogger.i("Diagnostics", "Laporan diagnostik selesai didapatkan.")
        report
    }
}

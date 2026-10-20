package com.arbdev.devbox.data

import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChrootProcessInfo(
    val pid: Int,
    val name: String,
    val sessionId: String?,
    val memoryKb: Long = 0
)

data class ChrootState(
    val isRootGranted: Boolean = false,
    val isRootfsExists: Boolean = false,
    val isMounted: Boolean = false,
    val activeProcesses: Int = 0,
    val ramAvailableMb: Long = 0,
    val swapUsedMb: Long = 0,
    val isCodeServerRunning: Boolean = false,
    val isSshRunning: Boolean = false,
    val rootfsPath: String = ChrootManager.DEFAULT_ROOTFS,
    val isSourceChrootAvailable: Boolean = false
)

object ChrootManager {
    const val DEFAULT_ROOTFS = "/data/local/devbox-chroot"
    var activeRootfs: String = DEFAULT_ROOTFS

    // State cache agar Dashboard dapat langsung ditampilkan instan tanpa buffering saat switch tab
    @Volatile
    var lastKnownState: ChrootState? = null

    suspend fun getChrootState(rootfs: String = activeRootfs): ChrootState {
        val rootOk = RootExecutor.isRootAvailable()
        if (!rootOk) {
            AppLogger.w("ChrootManager", "Superuser (su) tidak terdeteksi.")
            val state = ChrootState(isRootGranted = false, rootfsPath = rootfs)
            lastKnownState = state
            return state
        }

        // Optimasi atomic: Jalankan seluruh verifikasi status dalam 1 script bash tunggal via su (70ms vs 3500ms)
        val script = """
            rf='$rootfs'
            if [ -d "${'$'}rf" ]; then
                if [ -x "${'$'}rf/bin/bash" ] || [ -x "${'$'}rf/usr/bin/bash" ] || [ -x "${'$'}rf/bin/sh" ]; then
                    status="EXISTS"
                else
                    status="DIR_EMPTY"
                fi
            else
                status="NOT_FOUND"
            fi

            if [ -d '/data/local/ubuntu-chroot' ] && { [ -x '/data/local/ubuntu-chroot/bin/bash' ] || [ -x '/data/local/ubuntu-chroot/usr/bin/bash' ]; }; then
                src_ok=1
            else
                src_ok=0
            fi

            if [ "${'$'}status" = "EXISTS" ]; then
                mountpoint -q "${'$'}rf/proc" && mnt=1 || mnt=0

                procs=0
                for p in /proc/[0-9]*; do
                    if [ "$(readlink ${'$'}p/root 2>/dev/null)" = "${'$'}rf" ]; then
                        procs=${'$'}((procs + 1))
                    fi
                done

                ram=0
                st=0
                sf=0
                while read -r key val rest; do
                    case "${'$'}key" in
                        MemAvailable:) ram=${'$'}((val / 1024)) ;;
                        SwapTotal:) st=${'$'}((val / 1024)) ;;
                        SwapFree:) sf=${'$'}((val / 1024)) ;;
                    esac
                done < /proc/meminfo
                swap=${'$'}((st - sf))
                [ ${'$'}swap -lt 0 ] && swap=0

                cs=0
                ssh=0
                if command -v netstat >/dev/null 2>&1; then
                    ns_out=${'$'}(netstat -tuln 2>/dev/null)
                    echo "${'$'}ns_out" | grep -q ':8080 ' && cs=1
                    echo "${'$'}ns_out" | grep -q ':2222 ' && ssh=1
                fi
                echo "${'$'}status|${'$'}src_ok|${'$'}mnt|${'$'}procs|${'$'}ram|${'$'}swap|${'$'}cs|${'$'}ssh"
            else
                echo "${'$'}status|${'$'}src_ok|0|0|0|0|0|0"
            fi
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val line = res.getOrNull()?.stdout?.trim()?.lines()?.lastOrNull() ?: ""
        val parts = line.split("|")

        if (parts.size >= 8) {
            val statusText = parts[0]
            val isSourceOk = parts[1] == "1"
            val isMounted = parts[2] == "1"
            val activeProcs = parts[3].toIntOrNull() ?: 0
            val ramAvailable = parts[4].toLongOrNull() ?: 0L
            val swapUsed = parts[5].toLongOrNull() ?: 0L
            val isCodeServer = parts[6] == "1"
            val isSsh = parts[7] == "1"

            val isRootfsExists = statusText == "EXISTS"
            if (!isRootfsExists) {
                AppLogger.w("ChrootManager", "Status rootfs di $rootfs: $statusText")
            }

            val resultState = ChrootState(
                isRootGranted = true,
                isRootfsExists = isRootfsExists,
                isMounted = isMounted,
                activeProcesses = activeProcs,
                ramAvailableMb = ramAvailable,
                swapUsedMb = swapUsed,
                isCodeServerRunning = isCodeServer,
                isSshRunning = isSsh,
                rootfsPath = rootfs,
                isSourceChrootAvailable = isSourceOk
            )
            lastKnownState = resultState
            return resultState
        }

        // Fallback jika parsing gagal
        val fallbackState = ChrootState(
            isRootGranted = true,
            isRootfsExists = false,
            rootfsPath = rootfs
        )
        lastKnownState = fallbackState
        return fallbackState
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
            grep -q 'nameserver' '$rootfs/etc/resolv.conf' 2>/dev/null || echo 'nameserver 8.8.8.8' >> '$rootfs/etc/resolv.conf' 2>/dev/null || true
            if [ ! -f '$rootfs/usr/local/bin/xdg-open' ]; then
                mkdir -p '$rootfs/usr/local/bin'
                cat <<'XDG_EOF' > '$rootfs/usr/local/bin/xdg-open'
#!/bin/bash
TARGET="${'$'}1"
if [ -z "${'$'}TARGET" ]; then exit 0; fi
echo ""
echo "=================================================="
echo " [DevBox Browser / Open URL]"
echo " URL: ${'$'}TARGET"
echo "=================================================="
echo ""
echo "${'$'}TARGET" > /tmp/devbox_latest_url 2>/dev/null || true
if [ -x /system/bin/am ]; then
    /system/bin/am start -a android.intent.action.VIEW -d "${'$'}TARGET" >/dev/null 2>&1 || true
fi
exit 0
XDG_EOF
                chmod 755 '$rootfs/usr/local/bin/xdg-open' 2>/dev/null || true
                ln -sf /usr/local/bin/xdg-open '$rootfs/usr/bin/xdg-open' 2>/dev/null || true
                ln -sf /usr/local/bin/xdg-open '$rootfs/usr/bin/x-www-browser' 2>/dev/null || true
            fi
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
            for p in /proc/[0-9]*; do
                if [ "$(readlink ${'$'}p/root 2>/dev/null)" = '$rootfs' ]; then
                    kill -9 "${'$'}(basename ${'$'}p)" 2>/dev/null || true
                fi
            done
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

    /**
     * Memindai proses di dalam chroot yang tergolong orphan / sesi usang.
     * Suatu proses dianggap orphan jika root-nya adalah chroot dan DEVBOX_SESSION_ID-nya
     * tidak terdapat di dalam daftar activeSessionIds saat ini.
     */
    suspend fun getOrphanChrootProcesses(
        activeSessionIds: List<String>,
        activePids: List<Long> = emptyList(),
        rootfs: String = activeRootfs
    ): List<ChrootProcessInfo> = withContext(Dispatchers.IO) {
        val pidListStr = activePids.filter { it > 0 }.joinToString(" ")
        val script = """
            active_pids="$pidListStr"
            active_descendants=""
            for ap in ${'$'}active_pids; do
                for child in ${'$'}(pgrep -P ${'$'}ap 2>/dev/null); do
                    active_descendants="${'$'}active_descendants ${'$'}child"
                    for grandchild in ${'$'}(pgrep -P ${'$'}child 2>/dev/null); do
                        active_descendants="${'$'}active_descendants ${'$'}grandchild"
                    done
                done
            done

            for p in /proc/[0-9]*; do
              if [ "$(readlink ${'$'}p/root 2>/dev/null)" = '$rootfs' ]; then
                pid=${'$'}(basename ${'$'}p)

                # Proteksi 1: Lewati jika PID termasuk di pohon proses sesi aktif
                is_active=0
                for ap in ${'$'}active_pids ${'$'}active_descendants; do
                    if [ -n "${'$'}ap" ] && [ "${'$'}ap" = "${'$'}pid" ]; then
                        is_active=1
                        break
                    fi
                done
                if [ "${'$'}is_active" -eq 1 ]; then
                    continue
                fi

                comm=${'$'}(cat ${'$'}p/comm 2>/dev/null)
                sid=${'$'}(tr '\0' '\n' < ${'$'}p/environ 2>/dev/null | grep -a '^DEVBOX_SESSION_ID=' | head -n1 | cut -d= -f2)
                rss=${'$'}(awk '/VmRSS/{print ${'$'}2}' ${'$'}p/status 2>/dev/null || echo 0)
                echo "${'$'}pid|${'$'}comm|${'$'}sid|${'$'}rss"
              fi
            done
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val lines = res.getOrNull()?.stdout?.lines().orEmpty()
        val orphans = mutableListOf<ChrootProcessInfo>()

        for (line in lines) {
            val parts = line.split("|")
            if (parts.size >= 4) {
                val pid = parts[0].toIntOrNull() ?: continue
                val comm = parts[1].trim()
                val rawSid = parts[2].trim()
                val rssKb = parts[3].toLongOrNull() ?: 0L

                // Proteksi 2: Jika sid berisi "Binary file" atau error string, jangan anggap orphan
                if (rawSid.contains("Binary file") || rawSid.contains("matches")) {
                    continue
                }

                val sid = rawSid.takeIf { it.isNotEmpty() }
                // Sesi usang: sid tidak ada (legacy/crash lama) atau sid tidak aktif di DevBox
                if (sid == null || !activeSessionIds.contains(sid)) {
                    orphans.add(ChrootProcessInfo(pid, comm, sid, rssKb))
                }
            }
        }
        orphans
    }

    /**
     * Mematikan semua proses orphan yang tertinggal di chroot dari crash/force close sebelumnya.
     * Tidak akan mematikan proses sesi aktif DevBox!
     */
    suspend fun killOrphanChrootProcesses(
        activeSessionIds: List<String>,
        activePids: List<Long> = emptyList(),
        rootfs: String = activeRootfs
    ): Int = withContext(Dispatchers.IO) {
        val orphans = getOrphanChrootProcesses(activeSessionIds, activePids, rootfs)
        if (orphans.isEmpty()) return@withContext 0

        // Proteksi 3: Filter agar tidak ada activePids yang terbunuh
        val safeOrphans = orphans.filter { orphan ->
            !activePids.contains(orphan.pid.toLong())
        }
        if (safeOrphans.isEmpty()) return@withContext 0

        val pidsToKill = safeOrphans.map { it.pid }.joinToString(" ")
        RootExecutor.execute("kill -9 $pidsToKill 2>/dev/null || true")
        AppLogger.i("ChrootManager", "Berhasil membersihkan ${safeOrphans.size} proses orphan di chroot: $pidsToKill")
        safeOrphans.size
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
        AppLogger.i("ChrootManager", "Memulai duplikasi sistem dari $sourceChroot ke $targetRootfs...")
        val script = """
            if [ ! -d '$sourceChroot' ] || { [ ! -x '$sourceChroot/bin/bash' ] && [ ! -x '$sourceChroot/usr/bin/bash' ]; }; then
                echo "SOURCE_NOT_FOUND: $sourceChroot tidak memiliki binary bash."
                exit 1
            fi
            echo "Unmounting mount points target lama jika ada..."
            umount -l '$targetRootfs/mnt/sdcard' 2>/dev/null || true
            umount -l '$targetRootfs/sys' 2>/dev/null || true
            umount -l '$targetRootfs/proc' 2>/dev/null || true
            umount -l '$targetRootfs/dev/pts' 2>/dev/null || true
            umount -l '$targetRootfs/dev' 2>/dev/null || true

            mkdir -p '$targetRootfs'
            cd '$sourceChroot' || exit 1
            echo "Membersihkan direktori target..."
            rm -rf '$targetRootfs/bin' '$targetRootfs/boot' '$targetRootfs/etc' '$targetRootfs/lib' '$targetRootfs/opt' '$targetRootfs/root' '$targetRootfs/sbin' '$targetRootfs/srv' '$targetRootfs/usr' '$targetRootfs/var'
            echo "Menyalin direktori sistem Ubuntu... (ini membutuhkan waktu sekitar 15-20 detik)"
            cp -a bin boot etc lib opt root sbin srv usr var '$targetRootfs/' 2>/dev/null || true
            mkdir -p '$targetRootfs/home/coder' '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp'
            chmod 1777 '$targetRootfs/tmp' 2>/dev/null || true
            chown -R 1000:1000 '$targetRootfs/home/coder' 2>/dev/null || true
            grep -qE '^127\.0\.0\.1[[:space:]]+localhost' '$targetRootfs/etc/hosts' 2>/dev/null || echo '127.0.0.1 localhost' >> '$targetRootfs/etc/hosts' 2>/dev/null || true
            echo "nameserver 8.8.8.8" > '$targetRootfs/etc/resolv.conf' 2>/dev/null || true

            if [ -x '$targetRootfs/bin/bash' ] || [ -x '$targetRootfs/usr/bin/bash' ] || [ -x '$targetRootfs/bin/sh' ]; then
                echo "CLONE_SUCCESS"
            else
                echo "CLONE_FAILED: Binary bash tidak ditemukan setelah penyalinan."
                exit 1
            fi
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val out = res.getOrNull()?.stdout ?: ""
        return@withContext if (out.contains("CLONE_SUCCESS")) {
            AppLogger.i("ChrootManager", "Kloning rootfs berhasil! Container DevBox di $targetRootfs siap digunakan.")
            Result.success("Kloning rootfs berhasil! Container DevBox siap digunakan.")
        } else {
            val err = "Gagal kloning: ${res.getOrNull()?.stderr.takeIf { !it.isNullOrBlank() } ?: out}"
            AppLogger.e("ChrootManager", err)
            Result.failure(Exception(err))
        }
    }

    suspend fun downloadUbuntuOfficialRootfs(
        targetRootfs: String = activeRootfs
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "http://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
        AppLogger.i("ChrootManager", "Menyiapkan Ubuntu 24.04 ARM64 resmi...")

        val script = """
            umount -l '$targetRootfs/mnt/sdcard' 2>/dev/null || true
            umount -l '$targetRootfs/sys' 2>/dev/null || true
            umount -l '$targetRootfs/proc' 2>/dev/null || true
            umount -l '$targetRootfs/dev/pts' 2>/dev/null || true
            umount -l '$targetRootfs/dev' 2>/dev/null || true
            mkdir -p '$targetRootfs'

            EXTRACTED=0

            # Cek 1: Jika sudah ada archive tar.xz dari unduhan sebelumnya di /sdcard dan ubuntu-chroot ada
            if [ -f /sdcard/ubuntu-noble-arm64.tar.xz ] && [ -x /data/local/ubuntu-chroot/usr/bin/tar ]; then
                echo "Mengekstrak archive /sdcard/ubuntu-noble-arm64.tar.xz yang sudah ada..."
                mkdir -p /data/local/ubuntu-chroot/mnt/devbox_dest
                mountpoint -q /data/local/ubuntu-chroot/mnt/devbox_dest || mount --bind '$targetRootfs' /data/local/ubuntu-chroot/mnt/devbox_dest
                mountpoint -q /data/local/ubuntu-chroot/mnt/sdcard || mount --bind /sdcard /data/local/ubuntu-chroot/mnt/sdcard
                chroot /data/local/ubuntu-chroot /usr/bin/tar -xf /mnt/sdcard/ubuntu-noble-arm64.tar.xz -C /mnt/devbox_dest
                umount -l /data/local/ubuntu-chroot/mnt/devbox_dest 2>/dev/null || true
                if [ -x '$targetRootfs/bin/bash' ] || [ -x '$targetRootfs/usr/bin/bash' ]; then
                    EXTRACTED=1
                    echo "EXTRACT_SUCCESS_FROM_EXISTING"
                fi
            fi

            # Cek 2: Jika belum terekstrak, unduh Ubuntu Base 24.04 ARM64 (.tar.gz, 29 MB)
            if [ "${'$'}EXTRACTED" -eq 0 ]; then
                TAR_FILE="/sdcard/ubuntu-base-24.04-arm64.tar.gz"
                echo "Mengunduh archive Ubuntu Base 24.04 ARM64 resmi (29 MB)..."
                curl -k -L -f '$url' -o "${'$'}TAR_FILE" || wget --no-check-certificate '$url' -O "${'$'}TAR_FILE"
                FILE_SIZE=${'$'}(wc -c < "${'$'}TAR_FILE" 2>/dev/null || echo 0)
                if [ "${'$'}FILE_SIZE" -lt 15000000 ]; then
                    echo "DOWNLOAD_FAILED: File unduhan tidak lengkap (hanya ${'$'}FILE_SIZE bytes)."
                    rm -f "${'$'}TAR_FILE"
                    exit 1
                fi
                echo "Mengekstrak rootfs ke $targetRootfs..."
                gzip -dc "${'$'}TAR_FILE" | tar -xf - -C '$targetRootfs' 2>/dev/null || tar -xzf "${'$'}TAR_FILE" -C '$targetRootfs' 2>/dev/null || tar -xf "${'$'}TAR_FILE" -C '$targetRootfs'
                rm -f "${'$'}TAR_FILE"
            fi

            mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp' '$targetRootfs/home/coder'
            chmod 1777 '$targetRootfs/tmp' 2>/dev/null || true
            echo "nameserver 8.8.8.8" > '$targetRootfs/etc/resolv.conf' 2>/dev/null || true
            echo "127.0.0.1 localhost" > '$targetRootfs/etc/hosts' 2>/dev/null || true

            # Buat user coder jika belum ada
            chroot '$targetRootfs' /usr/sbin/useradd -m -s /bin/bash coder 2>/dev/null || chroot '$targetRootfs' /bin/sh -c 'useradd -m -s /bin/bash coder 2>/dev/null || true'
            chown -R 1000:1000 '$targetRootfs/home/coder' 2>/dev/null || true

            if [ -x '$targetRootfs/bin/bash' ] || [ -x '$targetRootfs/usr/bin/bash' ] || [ -x '$targetRootfs/bin/sh' ]; then
                echo "DOWNLOAD_EXTRACT_SUCCESS"
            else
                echo "EXTRACT_FAILED"
                exit 1
            fi
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val out = res.getOrNull()?.stdout ?: ""
        return@withContext if (out.contains("DOWNLOAD_EXTRACT_SUCCESS") || out.contains("EXTRACT_SUCCESS_FROM_EXISTING")) {
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

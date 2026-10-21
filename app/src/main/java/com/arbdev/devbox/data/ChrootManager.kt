package com.arbdev.devbox.data

import android.content.Context
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
    const val LEGACY_ROOTFS = "/data/local/ubuntu-chroot"
    private const val PREFS_NAME = "devbox_prefs"
    private const val KEY_ACTIVE_ROOTFS = "key_active_rootfs"

    var activeRootfs: String = DEFAULT_ROOTFS
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ACTIVE_ROOTFS, null)
        if (!saved.isNullOrBlank()) {
            activeRootfs = saved
            AppLogger.i("ChrootManager", "Memuat rootfs dari SharedPreferences: $activeRootfs")
        }
    }

    fun setActiveRootfs(context: Context, newPath: String) {
        val trimmed = newPath.trim()
        activeRootfs = trimmed
        lastKnownState = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_ROOTFS, trimmed).apply()
        AppLogger.i("ChrootManager", "Jalur rootfs aktif disimpan secara permanen: $trimmed")
    }

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

        // Optimasi atomic: Jalankan seluruh verifikasi status dalam 1 script bash kilat via su (<30ms)
        val script = """
            rf='$rootfs'
            if [ -d "${'$'}rf" ] && { [ -x "${'$'}rf/bin/bash" ] || [ -x "${'$'}rf/usr/bin/bash" ] || [ -x "${'$'}rf/bin/sh" ]; }; then
                status="EXISTS"
            else
                if [ -d "${'$'}rf" ]; then
                    status="DIR_EMPTY"
                else
                    status="NOT_FOUND"
                fi
            fi

            if [ -d '/data/local/ubuntu-chroot' ] && { [ -x '/data/local/ubuntu-chroot/bin/bash' ] || [ -x '/data/local/ubuntu-chroot/usr/bin/bash' ]; }; then
                src_ok=1
            else
                src_ok=0
            fi

            if [ "${'$'}status" = "EXISTS" ]; then
                grep -q " ${'$'}rf/proc " /proc/mounts 2>/dev/null && mnt=1 || mnt=0

                # Hitung proses chroot milik DevBox secara instan
                procs=${'$'}(grep -sl "DEVBOX" /proc/[0-9]*/environ 2>/dev/null | wc -l)

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

                # Cek port 8080 (1F90) & port 2222 (08AE) langsung via /proc/net/tcp (<1ms)
                cs=0
                ssh=0
                if [ -f /proc/net/tcp ]; then
                    grep -q ":1F90 " /proc/net/tcp && cs=1
                    grep -q ":08AE " /proc/net/tcp && ssh=1
                fi
                echo "${'$'}status|${'$'}src_ok|${'$'}mnt|${'$'}procs|${'$'}ram|${'$'}swap|${'$'}cs|${'$'}ssh|${'$'}rf"
            else
                echo "${'$'}status|${'$'}src_ok|0|0|0|0|0|0|${'$'}rf"
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
                rootfsPath = activeRootfs,
                isSourceChrootAvailable = isSourceOk
            )
            lastKnownState = resultState
            return resultState
        }

        // Fallback jika parsing gagal
        val fallbackState = ChrootState(
            isRootGranted = true,
            isRootfsExists = false,
            rootfsPath = activeRootfs
        )
        lastKnownState = fallbackState
        return fallbackState
    }

    suspend fun mountAll(rootfs: String = activeRootfs): Result<String> {
        AppLogger.i("ChrootManager", "Memasang mount points untuk $rootfs...")
        val script = """
            mkdir -p '$rootfs/dev' '$rootfs/dev/pts' '$rootfs/proc' '$rootfs/sys' '$rootfs/mnt/sdcard' '$rootfs/tmp'
            chmod 1777 '$rootfs/tmp' 2>/dev/null || true
            mountpoint -q '$rootfs/dev' || { mount --bind /dev '$rootfs/dev' && mount --make-private '$rootfs/dev'; }
            mountpoint -q '$rootfs/dev/pts' || mount -t devpts devpts '$rootfs/dev/pts' 2>/dev/null || mount --bind /dev/pts '$rootfs/dev/pts'
            mountpoint -q '$rootfs/proc' || mount -t proc proc '$rootfs/proc'
            mountpoint -q '$rootfs/sys' || mount -t sysfs sysfs '$rootfs/sys'
            mountpoint -q '$rootfs/mnt/sdcard' || { mount --bind /sdcard '$rootfs/mnt/sdcard' && mount --make-private '$rootfs/mnt/sdcard'; }
            mount --make-rprivate '$rootfs' 2>/dev/null || true
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

            mkdir -p '$rootfs/tmp/devbox-bridge'
            chmod 777 '$rootfs/tmp/devbox-bridge' 2>/dev/null || true
            if [ ! -f '$rootfs/usr/local/bin/devbox-bridge' ]; then
                mkdir -p '$rootfs/usr/local/bin'
                cat <<'BRIDGE_EOF' > '$rootfs/usr/local/bin/devbox-bridge'
#!/bin/sh
BRIDGE_DIR="/tmp/devbox-bridge"
mkdir -p "${'$'}BRIDGE_DIR" 2>/dev/null || true
chmod 777 "${'$'}BRIDGE_DIR" 2>/dev/null || true

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
        printf "%s|%s\n" "${'$'}TITLE" "${'$'}MSG" > "${'$'}BRIDGE_DIR/notify.txt" 2>/dev/null || true
        echo "DevBox Bridge: Notifikasi dikirim: ${'$'}TITLE - ${'$'}MSG"
        ;;
    open|url)
        shift
        URL="${'$'}1"
        if [ -n "${'$'}URL" ]; then
            printf "%s\n" "${'$'}URL" > "${'$'}BRIDGE_DIR/url.txt" 2>/dev/null || true
            /usr/local/bin/xdg-open "${'$'}URL" 2>/dev/null || true
        fi
        ;;
    transcript|last-agent)
        BRAIN_DIR="${'$'}HOME/.gemini/antigravity-cli/brain"
        [ -d "${'$'}BRAIN_DIR" ] || BRAIN_DIR="/home/coder/.gemini/antigravity-cli/brain"
        LATEST_LOG=${'$'}(find "${'$'}BRAIN_DIR" -name "transcript.jsonl" -type f 2>/dev/null | xargs ls -t 2>/dev/null | head -n1)
        if [ -f "${'$'}LATEST_LOG" ]; then
            grep '"type":"PLANNER_RESPONSE"' "${'$'}LATEST_LOG" | tail -n1
        else
            echo "Belum ada riwayat Antigravity CLI."
        fi
        ;;
    *)
        echo "DevBox Bridge v1.0.48"
        echo "Usage: devbox-bridge <copy|notify|open|transcript> [args...]"
        ;;
esac
BRIDGE_EOF
                chmod 755 '$rootfs/usr/local/bin/devbox-bridge' 2>/dev/null || true
                ln -sf /usr/local/bin/devbox-bridge '$rootfs/usr/bin/devbox-bridge' 2>/dev/null || true
                ln -sf /usr/local/bin/devbox-bridge '$rootfs/usr/local/bin/devbox-clip' 2>/dev/null || true
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
            umount -l '$rootfs/mnt/sdcard' 2>/dev/null || true
            umount -l '$rootfs/sdcard' 2>/dev/null || true
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
            # 1. Matikan HANYA proses yang diluncurkan oleh DevBox (memiliki tag DEVBOX di environ)
            for p in /proc/[0-9]*; do
                if grep -q 'DEVBOX' "${'$'}p/environ" 2>/dev/null; then
                    kill -9 "${'$'}(basename ${'$'}p)" 2>/dev/null || true
                fi
            done

            # 2. Lepaskan mountpoint jika ini container devbox-chroot mandiri, atau jika Termux tidak ada
            if [ '$rootfs' = '/data/local/devbox-chroot' ] || [ ! -d '/data/data/com.termux' ]; then
                umount -l '$rootfs/mnt/sdcard' 2>/dev/null || true
                umount -l '$rootfs/sdcard' 2>/dev/null || true
                umount -l '$rootfs/sys' 2>/dev/null || true
                umount -l '$rootfs/proc' 2>/dev/null || true
                umount -l '$rootfs/dev/pts' 2>/dev/null || true
                umount -l '$rootfs/dev' 2>/dev/null || true
            fi
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
     * Menghapus seluruh file dan sistem Linux (rootfs) untuk mengosongkan 2-5 GB memori HP.
     * Dilakukan sebelum mencopot pemasangan (uninstall) aplikasi DevBox.
     * Dilengkapi proteksi ketat agar TIDAK menyentuh atau menghapus rootfs milik Termux!
     */
    suspend fun uninstallRootfs(
        rootfs: String = activeRootfs,
        onLogLine: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val script = """
            echo "=== MEMULAI PENGHAPUSAN CONTAINER DEVBOX ==="
            echo "Target: $rootfs"

            echo "[1/4] Menghentikan seluruh proses container DevBox..."
            for p in /proc/[0-9]*; do
                if grep -q "DEVBOX" "${'$'}p/environ" 2>/dev/null; then
                    pid=${'$'}(basename "${'$'}p" 2>/dev/null || true)
                    echo "Menghentikan PID DevBox ${'$'}pid..."
                    kill -9 "${'$'}pid" 2>/dev/null || true
                fi
            done
            sleep 1

            # Proteksi Termux: Jangan hapus /data/local/ubuntu-chroot jika digunakan Termux
            if [ '$rootfs' = '/data/local/ubuntu-chroot' ]; then
                echo "[PROTEKSI AKTIF] Direktori ini adalah /data/local/ubuntu-chroot (Chroot Milik Termux)."
                echo "DevBox TIDAK AKAN menghapus folder sistem ini demi melindungi data Termux Anda."
                echo "Membersihkan file integrasi bridge, socket, dan log DevBox..."
                rm -rf '$rootfs/tmp/devbox-bridge' '$rootfs/tmp/devbox_latest_url' 2>/dev/null || true
                rm -rf /tmp/devbox* /data/local/tmp/devbox* 2>/dev/null || true
                if [ -d '/data/local/devbox-chroot' ]; then
                    echo "Menghapus container /data/local/devbox-chroot terpisah..."
                    rm -rf '/data/local/devbox-chroot' 2>/dev/null || true
                fi
                echo "SUCCESS_UNINSTALLED"
                echo "=== PEMBERSIHAN SELESAI (DATA TERMUX AMAN 100%) ==="
                exit 0
            fi

            echo "[2/4] Melepaskan semua mount point terkait..."
            awk -v r='$rootfs/' '${'$'}2 ~ ("^" r) {print ${'$'}2}' /proc/mounts 2>/dev/null | sort -r | while read -r m; do
                echo "Unmounting ${'$'}m..."
                umount -l "${'$'}m" 2>/dev/null || true
            done
            umount -l '$rootfs/mnt/sdcard' 2>/dev/null || true
            umount -l '$rootfs/sdcard' 2>/dev/null || true
            umount -l '$rootfs/sys' 2>/dev/null || true
            umount -l '$rootfs/proc' 2>/dev/null || true
            umount -l '$rootfs/dev/pts' 2>/dev/null || true
            umount -l '$rootfs/dev' 2>/dev/null || true
            sleep 1

            if grep -q " $rootfs/mnt/sdcard " /proc/mounts 2>/dev/null || grep -q " $rootfs/sdcard " /proc/mounts 2>/dev/null; then
                echo "PERINGATAN: Unmount storage sdcard gagal! Penghapusan dibatalkan demi keamanan data HP."
                exit 1
            fi

            if [ ! -e '$rootfs' ]; then
                echo "Direktori $rootfs tidak ditemukan (sudah bersih)."
                echo "SUCCESS_UNINSTALLED"
                exit 0
            fi

            echo "[3/4] Memberikan izin tulis penuh dan menghapus file container..."
            chmod -R 777 '$rootfs' 2>/dev/null || true

            for d in bin boot dev etc home lib lib64 media mnt opt proc root run sbin srv sys tmp usr var; do
                if [ -e '$rootfs/'"${'$'}d" ]; then
                    echo "Menghapus $rootfs/${'$'}d..."
                    rm -rf '$rootfs/'"${'$'}d" 2>&1 || true
                fi
            done

            echo "Menghapus sisa direktori induk container..."
            rm -rf '$rootfs' 2>&1 || true

            echo "[4/4] Verifikasi hasil penghapusan..."
            if [ ! -d '$rootfs' ]; then
                echo "SUCCESS_UNINSTALLED"
                echo "Pembersihan selesai tanpa sisa."
            else
                rem=${'$'}(ls -A '$rootfs' 2>/dev/null || true)
                if [ -z "${'$'}rem" ]; then
                    rmdir '$rootfs' 2>/dev/null || true
                    echo "SUCCESS_UNINSTALLED"
                    echo "Pembersihan direktori kosong berhasil."
                else
                    echo "GAGAL: Terdapat file/folder terkunci di $rootfs:"
                    echo "${'$'}rem"
                    exit 1
                fi
            fi
        """.trimIndent()

        val logOutput = StringBuilder()
        var isSuccess = false

        val exitCode = RootExecutor.executeStream(script) { line ->
            logOutput.appendLine(line)
            if (line.contains("SUCCESS_UNINSTALLED")) {
                isSuccess = true
            }
            onLogLine?.invoke(line)
        }

        if (isSuccess || exitCode == 0) {
            lastKnownState = null
            AppLogger.i("ChrootManager", "Rootfs $rootfs berhasil di-uninstall dan dibersihkan.")
            Result.success("Rootfs $rootfs berhasil dibersihkan total. Penyimpanan HP telah dibebaskan.")
        } else {
            val errLog = logOutput.toString().trim()
            val finalErr = if (errLog.isNotEmpty()) errLog else "Gagal menghapus rootfs (Exit code: $exitCode)."
            AppLogger.e("ChrootManager", finalErr)
            Result.failure(Exception(finalErr))
        }
    }

    /**
     * Membersihkan proses Antigravity CLI usang atau lock file dari crash / FC sesi sebelumnya.
     * HANYA mematikan proses milik DevBox, tidak akan pernah menyentuh antigravity di Termux!
     */
    suspend fun cleanStaleAgyLocks(rootfs: String = activeRootfs): Int = withContext(Dispatchers.IO) {
        val script = """
            count=0
            for p in /proc/[0-9]*; do
                if grep -q "DEVBOX" "${'$'}p/environ" 2>/dev/null; then
                    comm=${'$'}(cat "${'$'}p/comm" 2>/dev/null || true)
                    cmdline=${'$'}(cat "${'$'}p/cmdline" 2>/dev/null || true)
                    if [ "${'$'}comm" = "antigravity" ] || [ "${'$'}{cmdline#*antigravity}" != "${'$'}cmdline" ]; then
                        kill -9 "${'$'}(basename ${'$'}p)" 2>/dev/null && count=${'$'}((count + 1))
                    fi
                fi
            done
            echo "${'$'}count"
        """.trimIndent()
        val res = RootExecutor.execute(script)
        val count = res.getOrNull()?.stdout?.trim()?.toIntOrNull() ?: 0
        if (count > 0) {
            AppLogger.i("ChrootManager", "Berhasil membersihkan $count proses agy usang milik DevBox.")
        }
        count
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
              [ -d "${'$'}p" ] || continue
              if [ "$(readlink ${'$'}p/root 2>/dev/null)" = '$rootfs' ]; then
                pid=${'$'}(basename ${'$'}p)

                # Proteksi 1: Lewati jika PID termasuk di pohon proses sesi aktif (Cek cepat O(1))
                case " ${'$'}active_pids ${'$'}active_descendants " in
                    *" ${'$'}pid "*) continue ;;
                esac

                comm=${'$'}(cat ${'$'}p/comm 2>/dev/null)
                sid=${'$'}(grep -s -a -z '^DEVBOX_SESSION_ID=' ${'$'}p/environ 2>/dev/null | tr -d '\0' | cut -d= -f2)
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
                // Sesi usang HANYA jika proses ini pernah dibuat oleh DevBox (ada sid), tapi sesinya sudah mati/crash
                // Jangan anggap proses tanpa sid sebagai orphan, karena bisa merupakan proses eksternal Termux!
                if (sid != null && !activeSessionIds.contains(sid)) {
                    orphans.add(ChrootProcessInfo(pid, comm, sid, rssKb))
                }
            }
        }
        orphans
    }

    /**
     * Mematikan semua proses orphan yang tertinggal di chroot dari crash/force close sebelumnya.
     * Tidak akan mematikan proses sesi aktif DevBox dan tidak akan menyentuh proses Termux!
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
        AppLogger.i("ChrootManager", "Berhasil membersihkan ${safeOrphans.size} proses orphan DevBox: $pidsToKill")
        safeOrphans.size
    }

    suspend fun toggleCodeServer(enable: Boolean, rootfs: String = activeRootfs): Result<Unit> {
        return if (enable) {
            mountAll(rootfs)
            val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color DEVBOX=1 DEVBOX_SERVICE=code-server PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            val cmd = "chroot '$rootfs' /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash -lc 'nohup code-server --bind-addr 127.0.0.1:8080 >/tmp/devbox-code-server.log 2>&1 & echo \$! > /tmp/devbox-code-server.pid'"
            RootExecutor.execute(cmd)
            Result.success(Unit)
        } else {
            val cmd = """
                pid=${'$'}(cat '$rootfs/tmp/devbox-code-server.pid' 2>/dev/null || cat /tmp/devbox-code-server.pid 2>/dev/null || true)
                if [ -n "${'$'}pid" ]; then
                    kill -9 "${'$'}pid" 2>/dev/null || true
                    rm -f '$rootfs/tmp/devbox-code-server.pid' /tmp/devbox-code-server.pid 2>/dev/null || true
                fi
                for p in /proc/[0-9]*; do
                    if grep -q 'DEVBOX_SERVICE=code-server' "${'$'}p/environ" 2>/dev/null; then
                        kill -9 "${'$'}(basename ${'$'}p)" 2>/dev/null || true
                    fi
                done
            """.trimIndent()
            RootExecutor.execute(cmd)
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

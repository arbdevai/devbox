package com.arbdev.devbox.backend

import android.content.Context
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.data.RootExecutor
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementasi backend Linux native berbasis Chroot dan libsu untuk Flavor Root.
 * Memberikan kecepatan native 100% tanpa overhead userspace.
 */
class ChrootBackend(private val context: Context) : LinuxEnvironment {
    override val backendType: String = "root"
    override val isRootBackend: Boolean = true
    override val defaultRootfsPath: String = "/data/local/devbox-chroot"

    private var cachedPtsDev: String? = null

    override fun init(context: Context) {
        AppLogger.i("ChrootBackend", "Inisialisasi ChrootBackend (Native Linux Root)")
    }

    override suspend fun isRootAvailable(forceCheck: Boolean): Boolean {
        return RootExecutor.isRootAvailable(forceCheck)
    }

    private fun findSuPath(): String {
        val candidates = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "su"
        )
        for (candidate in candidates) {
            if (candidate == "su" || File(candidate).exists()) {
                return candidate
            }
        }
        return "su"
    }

    override fun startTerminalProcess(
        sessionId: String,
        targetRootfs: String,
        initCols: Int,
        initRows: Int
    ): Process {
        val suBinary = findSuPath()
        val chrootCmd = "mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp' 2>/dev/null; " +
                "chmod 1777 '$targetRootfs/tmp' 2>/dev/null; " +
                "mountpoint -q '$targetRootfs/dev' || { mount --bind /dev '$targetRootfs/dev' 2>/dev/null && mount --make-private '$targetRootfs/dev' 2>/dev/null; }; " +
                "mountpoint -q '$targetRootfs/dev/pts' || mount --bind /dev/pts '$targetRootfs/dev/pts' 2>/dev/null || mount -t devpts devpts '$targetRootfs/dev/pts' 2>/dev/null; " +
                "mountpoint -q '$targetRootfs/proc' || mount -t proc proc '$targetRootfs/proc' 2>/dev/null; " +
                "mountpoint -q '$targetRootfs/sys' || mount -t sysfs sysfs '$targetRootfs/sys' 2>/dev/null; " +
                "mountpoint -q '$targetRootfs/mnt/sdcard' || { mount --bind /sdcard '$targetRootfs/mnt/sdcard' 2>/dev/null && mount --make-private '$targetRootfs/mnt/sdcard' 2>/dev/null; }; " +
                "chown 1000:1000 '$targetRootfs/home/coder' 2>/dev/null; " +
                "cd '$targetRootfs' && exec chroot '$targetRootfs' /usr/bin/env -i HOME=/home/coder USER=coder LOGNAME=sahal SHELL=/bin/bash TERM=xterm-256color COLORTERM=truecolor TMPDIR=/tmp TEMP=/tmp TMP=/tmp LANG=C.UTF-8 LC_ALL=C.UTF-8 DEBIAN_FRONTEND=noninteractive CLAUDE_CODE_DISABLE_SANDBOX=1 CHROMIUM_FLAGS=\"--no-sandbox --disable-setuid-sandbox\" DEVBOX=1 DEVBOX_SESSION_ID='$sessionId' GH_NO_KEYRING=1 PATH=/home/coder/.local/bin:/home/coder/.cargo/bin:/home/coder/go/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash -c 'cd /home/coder 2>/dev/null || cd /; export TMPDIR=/tmp TEMP=/tmp TMP=/tmp LANG=C.UTF-8 LC_ALL=C.UTF-8 DEBIAN_FRONTEND=noninteractive CLAUDE_CODE_DISABLE_SANDBOX=1 CHROMIUM_FLAGS=\"--no-sandbox --disable-setuid-sandbox\"; git config --system --add safe.directory \"*\" 2>/dev/null || true; if [ -f /var/lib/dpkg/status ] && grep -q \"half-configured\" /var/lib/dpkg/status 2>/dev/null; then dpkg --configure -a 2>/dev/null || true; fi; if [ ! -f /etc/ssl/certs/ca-certificates.crt ] && [ -x /usr/sbin/update-ca-certificates ]; then update-ca-certificates --fresh 2>/dev/null || true; fi; if [ -x /usr/bin/script ]; then exec /usr/bin/script -qefc \"shopt -s checkwinsize 2>/dev/null; echo \\\$\\\$ > /tmp/.devbox_pid_$sessionId 2>/dev/null; tty > /tmp/.devbox_pts_$sessionId 2>/dev/null; echo \\\$(tty) > /home/coder/.devbox_pts_$sessionId 2>/dev/null; ws=\\\$(cat /tmp/.devbox_ws_$sessionId 2>/dev/null); if [ -n \\\"\\\$ws\\\" ]; then stty \\\$ws opost onlcr 2>/dev/null; else stty rows $initRows cols $initCols opost onlcr 2>/dev/null; fi; exec /bin/bash -l\" /dev/null; else exec /bin/bash -l; fi'"

        val pb = ProcessBuilder(suBinary, "-c", chrootCmd)
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
        val ptsFile = File("$targetRootfs/tmp/.devbox_pts_$sessionId")
        val pidFile = File("$targetRootfs/tmp/.devbox_pid_$sessionId")
        var nativeSuccess = false
        if (ptsFile.exists()) {
            val ptsDev = try { ptsFile.readText().trim() } catch (_: Throwable) { "" }
            if (ptsDev.isNotBlank() && ptsDev.startsWith("/dev/pts/")) {
                if (com.arbdev.devbox.terminal.NativeVTerm.setPtyWindowSize(ptsDev, cols, rows)) {
                    nativeSuccess = true
                    val pidStr = try { pidFile.readText().trim() } catch (_: Throwable) { "" }
                    val pid = pidStr.toIntOrNull()
                    if (pid != null && pid > 0) {
                        try {
                            android.system.Os.kill(pid, 28) // SIGWINCH
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

        if (nativeSuccess) {
            try {
                File("$targetRootfs/tmp/.devbox_ws_$sessionId").writeText("rows $rows cols $cols")
            } catch (_: Throwable) {}
            return
        }

        val currentPtsFd = cachedPtsDev
        if (currentPtsFd != null) {
            val directCmd = "echo 'rows $rows cols $cols' > '$targetRootfs/tmp/.devbox_ws_$sessionId' 2>/dev/null; " +
                    "spid=\$(cat '$targetRootfs/tmp/.devbox_pid_$sessionId' 2>/dev/null); " +
                    "if [ -e '$currentPtsFd' ]; then " +
                    "stty -F '$currentPtsFd' rows $rows cols $cols 2>/dev/null || chroot '$targetRootfs' /usr/bin/stty -F '$currentPtsFd' rows $rows cols $cols 2>/dev/null; " +
                    "[ -n \"\$spid\" ] && kill -28 \"\$spid\" 2>/dev/null || true; " +
                    "else exit 1; fi"
            val res = RootExecutor.executeSync(directCmd, silent = true)
            if (res.isSuccess) return
            cachedPtsDev = null
        }

        val resolveAndSetCmd = "echo 'rows $rows cols $cols' > '$targetRootfs/tmp/.devbox_ws_$sessionId' 2>/dev/null; " +
                "pts_fd=''; " +
                "spid=\$(cat '$targetRootfs/tmp/.devbox_pid_$sessionId' 2>/dev/null); " +
                "if [ -n \"\$spid\" ] && [ -e \"/proc/\$spid/fd/0\" ]; then " +
                "pts_fd=\"/proc/\$spid/fd/0\"; " +
                "else " +
                "for env_file in \$(grep -l 'DEVBOX_SESSION_ID=$sessionId' /proc/[0-9]*/environ 2>/dev/null); do " +
                "p=\$(echo \"\$env_file\" | cut -d/ -f3); " +
                "for fd in /proc/\$p/fd/0 /proc/\$p/fd/1 /proc/\$p/fd/2; do " +
                "t=\$(readlink \$fd 2>/dev/null); " +
                "if [[ \$t == */dev/pts/* ]]; then pts_fd=\"\$fd\"; break 2; fi; " +
                "done; done; fi; " +
                "if [ -n \"\$pts_fd\" ]; then " +
                "stty -F \"\$pts_fd\" rows $rows cols $cols 2>/dev/null || chroot '$targetRootfs' /usr/bin/stty -F \"\$pts_fd\" rows $rows cols $cols 2>/dev/null; " +
                "[ -n \"\$spid\" ] && kill -28 \"\$spid\" 2>/dev/null || true; " +
                "echo \"\$pts_fd\"; " +
                "fi"

        val res = RootExecutor.executeSync(resolveAndSetCmd, silent = true)
        val resolvedFd = res.getOrNull()?.stdout?.trim()
        if (!resolvedFd.isNullOrBlank() && resolvedFd.startsWith("/proc/")) {
            cachedPtsDev = resolvedFd
        }
    }

    override fun terminateSession(sessionId: String, targetRootfs: String) {
        val killCmd = "for p in /proc/[0-9]*; do " +
                "if [ \"\$(readlink \$p/root 2>/dev/null)\" = '$targetRootfs' ]; then " +
                "if grep -z '^DEVBOX_SESSION_ID=$sessionId' \"\$p/environ\" 2>/dev/null >/dev/null; then " +
                "kill -9 \"\$(basename \$p)\" 2>/dev/null; " +
                "fi; fi; done; " +
                "rm -f '$targetRootfs/tmp/.devbox_pid_$sessionId' " +
                "'$targetRootfs/tmp/.devbox_pts_$sessionId' " +
                "'$targetRootfs/tmp/.devbox_ws_$sessionId' " +
                "'$targetRootfs/tmp/.devbox_cwd_$sessionId' " +
                "'$targetRootfs/tmp/.devbox_active_agent_$sessionId' " +
                "'$targetRootfs/home/coder/.devbox_pts_$sessionId' 2>/dev/null"
        RootExecutor.executeSync(killCmd, silent = true)
    }

    override fun sendInterruptSignal(sessionId: String, targetRootfs: String) {
        val cmd = "for p in /proc/[0-9]*; do " +
                "if [ \"\$(readlink \$p/root 2>/dev/null)\" = '$targetRootfs' ]; then " +
                "if grep -z '^DEVBOX_SESSION_ID=$sessionId' \"\$p/environ\" 2>/dev/null >/dev/null; then " +
                "comm=\$(cat \$p/comm 2>/dev/null); " +
                "if [ \"\$comm\" != 'script' ] && [ \"\$comm\" != 'bash' ] && [ \"\$comm\" != 'stty' ] && [ \"\$comm\" != 'su' ]; then " +
                "kill -2 \${p##*/} 2>/dev/null || true; " +
                "fi; fi; fi; done"
        RootExecutor.executeSync(cmd, silent = true)
    }

    override suspend fun getEnvironmentState(rootfs: String): ChrootState = withContext(Dispatchers.IO) {
        val rootOk = RootExecutor.isRootAvailable()
        if (!rootOk) {
            return@withContext ChrootState(isRootGranted = false, rootfsPath = rootfs)
        }

        val script = """
            rf='$rootfs'
            if [ -d "${'$'}rf" ] && { [ -x "${'$'}rf/bin/bash" ] || [ -x "${'$'}rf/usr/bin/bash" ] || [ -x "${'$'}rf/bin/sh" ]; }; then
                status="EXISTS"
            else
                if [ -d "${'$'}rf" ]; then status="DIR_EMPTY"; else status="NOT_FOUND"; fi
            fi

            if [ -d '/data/local/ubuntu-chroot' ] && { [ -x '/data/local/ubuntu-chroot/bin/bash' ] || [ -x '/data/local/ubuntu-chroot/usr/bin/bash' ]; }; then
                src_ok=1
            else
                src_ok=0
            fi

            if [ "${'$'}status" = "EXISTS" ]; then
                grep -q " ${'$'}rf/proc " /proc/mounts 2>/dev/null && mnt=1 || mnt=0
                procs=${'$'}(grep -sl "DEVBOX" /proc/[0-9]*/environ 2>/dev/null | wc -l)
                ram=0; st=0; sf=0
                while read -r key val rest; do
                    case "${'$'}key" in
                        MemAvailable:) ram=${'$'}((val / 1024)) ;;
                        SwapTotal:) st=${'$'}((val / 1024)) ;;
                        SwapFree:) sf=${'$'}((val / 1024)) ;;
                    esac
                done < /proc/meminfo
                swap=${'$'}((st - sf))
                [ "${'$'}swap" -lt 0 ] && swap=0

                cs=0; ssh=0
                if [ -f /proc/net/tcp ]; then
                    grep -q ":1F90 " /proc/net/tcp && cs=1
                    grep -q ":08AE " /proc/net/tcp && ssh=1
                fi
                if [ "${'$'}cs" -eq 0 ] && [ "${'$'}mnt" -eq 1 ]; then
                    pgrep -f "code-server" >/dev/null 2>&1 && cs=1 || true
                fi
                if [ "${'$'}ssh" -eq 0 ] && [ "${'$'}mnt" -eq 1 ]; then
                    pgrep -f "sshd" >/dev/null 2>&1 && ssh=1 || true
                fi

                echo "${'$'}status|${'$'}src_ok|${'$'}mnt|${'$'}procs|${'$'}ram|${'$'}swap|${'$'}cs|${'$'}ssh"
            else
                echo "${'$'}status|${'$'}src_ok|0|0|0|0|0|0"
            fi
        """.trimIndent()

        val res = RootExecutor.execute(script)
        val line = res.getOrNull()?.stdout?.lines()?.lastOrNull { it.contains("|") }?.trim()
        if (line != null) {
            val parts = line.split("|")
            if (parts.size >= 8) {
                return@withContext ChrootState(
                    isRootGranted = true,
                    isRootfsExists = (parts[0] == "EXISTS"),
                    isSourceChrootAvailable = (parts[1] == "1"),
                    isMounted = (parts[2] == "1"),
                    activeProcesses = parts[3].toIntOrNull() ?: 0,
                    ramAvailableMb = parts[4].toLongOrNull() ?: 0L,
                    swapUsedMb = parts[5].toLongOrNull() ?: 0L,
                    isCodeServerRunning = (parts[6] == "1"),
                    isSshRunning = (parts[7] == "1"),
                    rootfsPath = rootfs
                )
            }
        }
        ChrootState(isRootGranted = true, rootfsPath = rootfs)
    }

    override suspend fun mountAll(rootfs: String): Result<String> = withContext(Dispatchers.IO) {
        val script = """
            rf='$rootfs'
            mkdir -p "${'$'}rf/dev" "${'$'}rf/dev/pts" "${'$'}rf/proc" "${'$'}rf/sys" "${'$'}rf/mnt/sdcard" "${'$'}rf/tmp"
            chmod 1777 "${'$'}rf/tmp" 2>/dev/null || true
            mountpoint -q "${'$'}rf/dev" || { mount --bind /dev "${'$'}rf/dev" && mount --make-private "${'$'}rf/dev"; }
            mountpoint -q "${'$'}rf/dev/pts" || mount -t devpts devpts "${'$'}rf/dev/pts" 2>/dev/null || mount --bind /dev/pts "${'$'}rf/dev/pts"
            mountpoint -q "${'$'}rf/proc" || mount -t proc proc "${'$'}rf/proc"
            mountpoint -q "${'$'}rf/sys" || mount -t sysfs sysfs "${'$'}rf/sys"
            mountpoint -q "${'$'}rf/mnt/sdcard" || { mount --bind /sdcard "${'$'}rf/mnt/sdcard" && mount --make-private "${'$'}rf/mnt/sdcard"; }
            mount --make-rprivate "${'$'}rf" 2>/dev/null || true

            grep -qE '^127\.0\.0\.1[[:space:]]+localhost' "${'$'}rf/etc/hosts" 2>/dev/null || echo '127.0.0.1 localhost' >> "${'$'}rf/etc/hosts" 2>/dev/null || true
            android_dns1=${'$'}(getprop net.dns1 2>/dev/null || true)
            android_dns2=${'$'}(getprop net.dns2 2>/dev/null || true)
            if [ -n "${'$'}android_dns1" ] && ! grep -q "${'$'}android_dns1" "${'$'}rf/etc/resolv.conf" 2>/dev/null; then
                printf "nameserver %s\n" "${'$'}android_dns1" > "${'$'}rf/etc/resolv.conf" 2>/dev/null || true
                [ -n "${'$'}android_dns2" ] && printf "nameserver %s\n" "${'$'}android_dns2" >> "${'$'}rf/etc/resolv.conf" 2>/dev/null || true
                printf "nameserver 8.8.8.8\nnameserver 1.1.1.1\n" >> "${'$'}rf/etc/resolv.conf" 2>/dev/null || true
            else
                grep -q 'nameserver' "${'$'}rf/etc/resolv.conf" 2>/dev/null || printf "nameserver 8.8.8.8\nnameserver 1.1.1.1\n" >> "${'$'}rf/etc/resolv.conf" 2>/dev/null || true
            fi

            if [ ! -f "${'$'}rf/usr/local/bin/xdg-open" ]; then
                mkdir -p "${'$'}rf/usr/local/bin"
                cat <<'XDG_EOF' > "${'$'}rf/usr/local/bin/xdg-open"
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
chmod 666 /tmp/.devbox_open_url 2>/dev/null || true
echo "${'$'}TARGET" > /tmp/devbox_latest_url 2>/dev/null || true
if [ -x /system/bin/am ]; then
    /system/bin/am start -a android.intent.action.VIEW -d "${'$'}TARGET" >/dev/null 2>&1 || true
fi
exit 0
XDG_EOF
                chmod 755 "${'$'}rf/usr/local/bin/xdg-open" 2>/dev/null || true
                ln -sf /usr/local/bin/xdg-open "${'$'}rf/usr/bin/xdg-open" 2>/dev/null || true
                ln -sf /usr/local/bin/xdg-open "${'$'}rf/usr/bin/x-www-browser" 2>/dev/null || true
            fi

            mkdir -p "${'$'}rf/tmp/devbox-bridge"
            chmod 777 "${'$'}rf/tmp/devbox-bridge" 2>/dev/null || true
            if [ ! -f "${'$'}rf/usr/local/bin/devbox-bridge" ]; then
                mkdir -p "${'$'}rf/usr/local/bin"
                cat <<'BRIDGE_EOF' > "${'$'}rf/usr/local/bin/devbox-bridge"
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
        printf "%s|%s\n" "${'$'}TITLE" "${'$'}MSG" >> "${'$'}BRIDGE_DIR/notifications.txt" 2>/dev/null || true
        echo "DevBox Bridge: Notifikasi dikirim ke Android."
        ;;
    *)
        echo "Penggunaan: devbox-bridge {copy|clip|notify} [argumen]"
        ;;
esac
exit 0
BRIDGE_EOF
                chmod 755 "${'$'}rf/usr/local/bin/devbox-bridge" 2>/dev/null || true
                ln -sf /usr/local/bin/devbox-bridge "${'$'}rf/usr/bin/devbox-bridge" 2>/dev/null || true
                ln -sf /usr/local/bin/devbox-bridge "${'$'}rf/usr/local/bin/devbox-clip" 2>/dev/null || true
            fi

            if [ ! -f "${'$'}rf/usr/local/bin/bwrap" ]; then
                mkdir -p "${'$'}rf/usr/local/bin"
                echo "IyEvYmluL2Jhc2gKYXJncz0oIiRAIikKaT0wCnRhcmdldF9kaXI9IiIKZW52X3ZhcnM9KCkKaWYgWyAiJDEiID0gIi0tdmVyc2lvbiIgXTsgdGhlbgogICAgZWNobyAiYnViYmxld3JhcCAwLjkuMCAoZGV2Ym94LWNocm9vdC1zaGltKSIKICAgIGV4aXQgMApmaQp3aGlsZSBbICRpIC1sdCAkeyNhcmdzW0BdfSBdOyBkbwogICAgYXJnPSIke2FyZ3NbJGldfSIKICAgIGNhc2UgIiRhcmciIGluCiAgICAgICAgLS0pIGk9JCgoaSArIDEpKTsgYnJlYWsgOzsKICAgICAgICAtLWNoZGlyKSB0YXJnZXRfZGlyPSIke2FyZ3NbJCgoaSArIDEpKV19IjsgaT0kKChpICsgMikpIDs7CiAgICAgICAgLS1zZXRlbnYpIGVudl92YXJzKz0oIiR7YXJnc1skKChpICsgMSkpXX09JHthcmdzWyQoKGkgKyAyKSldfSIpOyBpPSQoKGkgKyAzKSkgOzsKICAgICAgICAtLXVuc2V0ZW52KSB1bnNldCAiJHthcmdzWyQoKGkgKyAxKSldfSI7IGk9JCgoaSArIDIpKSA7OwogICAgICAgIC0tcm8tYmluZHwtLWJpbmR8LS1kZXYtYmluZHwtLXJvLWJpbmQtdHJ5fC0tYmluZC10cnkpIGk9JCgoaSArIDMpKSA7OwogICAgICAgIC0tZGV2fC0tcHJvY3wtLXRtcGZzfC0tZGlyfC0tZmlsZXwtLWJpbmQtZGF0YXwtLXJvLWJpbmQtZGF0YSkgaT0kKChpICsgMikpIDs7CiAgICAgICAgLS1zeW1saW5rKSBpPSQoKGkgKyAzKSkgOzsKICAgICAgICAtLXVuc2hhcmUtKnwtLWRpZS13aXRoLXBhcmVudHwtLW5ldy1zZXNzaW9ufC0tYXMtcGlkLTF8LS1jbGVhcmVudikgaT0kKChpICsgMSkpIDs7CiAgICAgICAgLS11aWR8LS1naWR8LS1ob3N0bmFtZSkgaT0kKChpICsgMikpIDs7CiAgICAgICAgLSopIGk9JCgoaSArIDEpKSA7OwogICAgICAgICopIGJyZWFrIDs7CiAgICBlc2FjCmRvbmUKY21kPSgiJHthcmdzW0BdOiRpfSIpClsgJHsjY21kW0BdfSAtZXEgMCBdICYmIGV4aXQgMApbIC1uICIkdGFyZ2V0X2RpciIgXSAmJiBbIC1kICIkdGFyZ2V0X2RpciIgXSAmJiBjZCAiJHRhcmdldF9kaXIiIHx8IHRydWUKZm9yIGV2IGluICIke2Vudl92YXJzW0BdfSI7IGRvIGV4cG9ydCAiJGV2IjsgZG9uZQpleGVjICIke2NtZFtAXX0iCg==" | base64 -d > "${'$'}rf/usr/local/bin/bwrap"
                chmod 755 "${'$'}rf/usr/local/bin/bwrap" 2>/dev/null || true
            fi
            echo 'SUCCESS'
        """.trimIndent()
        val res = RootExecutor.execute(script)
        if (res.isSuccess && res.getOrNull()?.stdout?.contains("SUCCESS") == true) {
            Result.success("Mount berhasil dipasang")
        } else {
            Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal mount"))
        }
    }

    override suspend fun unmountAll(rootfs: String): Result<String> = withContext(Dispatchers.IO) {
        val script = """
            rf='$rootfs'
            umount -l "${'$'}rf/mnt/sdcard" 2>/dev/null || true
            umount -l "${'$'}rf/sdcard" 2>/dev/null || true
            umount -l "${'$'}rf/dev/pts" 2>/dev/null || true
            umount -l "${'$'}rf/dev" 2>/dev/null || true
            umount -l "${'$'}rf/proc" 2>/dev/null || true
            umount -l "${'$'}rf/sys" 2>/dev/null || true
        """.trimIndent()
        val res = RootExecutor.execute(script)
        if (res.isSuccess) Result.success("Mount berhasil dibersihkan")
        else Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal unmount"))
    }

    override fun killProcessesAndUnmountSync(rootfs: String): Result<String> {
        val script = """
            rf='$rootfs'
            for p in /proc/[0-9]*; do
                if [ -d "${'$'}p" ]; then
                    rootlink=${'$'}(readlink "${'$'}p/root" 2>/dev/null)
                    if [ "${'$'}rootlink" = "${'$'}rf" ] || grep -q 'DEVBOX' "${'$'}p/environ" 2>/dev/null; then
                        pid=${'$'}(basename "${'$'}p")
                        kill -9 "${'$'}pid" 2>/dev/null || true
                    fi
                fi
            done
            umount -l "${'$'}rf/mnt/sdcard" 2>/dev/null || true
            umount -l "${'$'}rf/sdcard" 2>/dev/null || true
            umount -l "${'$'}rf/dev/pts" 2>/dev/null || true
            umount -l "${'$'}rf/dev" 2>/dev/null || true
            umount -l "${'$'}rf/proc" 2>/dev/null || true
            umount -l "${'$'}rf/sys" 2>/dev/null || true
        """.trimIndent()
        val res = RootExecutor.executeSync(script)
        return if (res.isSuccess) Result.success("Proses dimatikan dan unmount selesai")
        else Result.failure(Exception(res.getOrNull()?.stderr ?: "Gagal matikan proses"))
    }

    override suspend fun ensureEnvironmentReady(rootfs: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureEnvironmentReadySync(rootfs)
    }

    override fun ensureEnvironmentReadySync(rootfs: String): Result<Unit> {
        val script = """
            rf='$rootfs'
            mkdir -p "${'$'}rf/home/coder/projects" "${'$'}rf/root" "${'$'}rf/tmp" "${'$'}rf/dev/pts" 2>/dev/null || true
            chmod 1777 "${'$'}rf/tmp" 2>/dev/null || true
            chown 1000:1000 "${'$'}rf/home/coder" "${'$'}rf/home/coder/projects" 2>/dev/null || true
        """.trimIndent()
        RootExecutor.executeSync(script, silent = true)
        return Result.success(Unit)
    }

    override suspend fun extractRootfs(
        tarPath: String,
        targetRootfs: String,
        onProgress: ((Float, String) -> Unit)?,
        onLogLine: ((String) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val script = """
            echo "Membuat direktori target $targetRootfs..."
            mkdir -p '$targetRootfs'

            # Unmount sub-mount lama jika ada
            awk -v r='$targetRootfs/' '${'$'}2 ~ ("^" r) {print ${'$'}2}' /proc/mounts 2>/dev/null | sort -r | while read -r m; do
                umount -l "${'$'}m" 2>/dev/null || true
            done

            TAR_BIN=""
            if [ -x /data/adb/magisk/busybox ]; then
                TAR_BIN="/data/adb/magisk/busybox tar"
            elif [ -x /data/local/ubuntu-chroot/usr/bin/tar ]; then
                TAR_BIN="/data/local/ubuntu-chroot/usr/bin/tar"
            elif which tar >/dev/null 2>&1; then
                TAR_BIN="tar"
            fi

            echo "Mengekstrak file archive ke $targetRootfs..."
            if [ -n "${'$'}TAR_BIN" ]; then
                ${'$'}TAR_BIN -xzf '$tarPath' -C '$targetRootfs' 2>&1 || gzip -dc '$tarPath' | ${'$'}TAR_BIN -xf - -C '$targetRootfs' 2>&1
            else
                gzip -dc '$tarPath' | tar -xf - -C '$targetRootfs' 2>&1 || tar -xzf '$tarPath' -C '$targetRootfs' 2>&1
            fi

            echo "Konfigurasi dasar jaringan dan user..."
            mkdir -p '$targetRootfs/dev' '$targetRootfs/dev/pts' '$targetRootfs/proc' '$targetRootfs/sys' '$targetRootfs/mnt/sdcard' '$targetRootfs/tmp' '$targetRootfs/home/coder'
            chmod 1777 '$targetRootfs/tmp' 2>/dev/null || true
            echo "nameserver 8.8.8.8" > '$targetRootfs/etc/resolv.conf' 2>/dev/null || true
            echo "127.0.0.1 localhost" > '$targetRootfs/etc/hosts' 2>/dev/null || true

            if ! grep -q "^coder:" '$targetRootfs/etc/passwd' 2>/dev/null; then
                echo "coder:x:1000:1000:DevBox User:/home/coder:/bin/bash" >> '$targetRootfs/etc/passwd' 2>/dev/null || true
                echo "coder:x:1000:" >> '$targetRootfs/etc/group' 2>/dev/null || true
            fi
            chown -R 1000:1000 '$targetRootfs/home/coder' 2>/dev/null || true
            chmod 755 '$targetRootfs/home/coder' 2>/dev/null || true

            if [ -x '$targetRootfs/bin/bash' ] || [ -x '$targetRootfs/usr/bin/bash' ]; then
                echo "INSTALL_SUCCESS"
                echo "=== UBUNTU BASE RESMI BERHASIL DIPASANG ==="
            else
                echo "INSTALL_FAILED: Binary bash tidak ditemukan setelah ekstraksi."
                exit 1
            fi
        """.trimIndent()

        var isSuccess = false
        val exitCode = RootExecutor.executeStream(script) { line ->
            onLogLine?.invoke(line)
            if (line.contains("INSTALL_SUCCESS")) {
                isSuccess = true
            }
        }

        if (isSuccess || exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Gagal ekstraksi Ubuntu Base via root tar (Exit code: $exitCode)"))
        }
    }

    override suspend fun runEnvironmentDiagnostics(): String = withContext(Dispatchers.IO) {
        val diagScript = """
            echo "=== INFORMASI SUPERUSER & KERNEL ==="
            id
            uname -a
            echo ""
            echo "=== INFORMASI MOUNT CHROOT ==="
            grep -E "devbox|chroot" /proc/mounts 2>/dev/null || echo "Tidak ada mount chroot aktif"
        """.trimIndent()
        RootExecutor.execute(diagScript).getOrNull()?.stdout ?: "Gagal menjalankan diagnostik"
    }
}

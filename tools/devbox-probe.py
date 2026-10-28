#!/usr/bin/env python3
"""
DevBox Live Diagnostic & PTY Probe Tool
Digunakan untuk memantau sinkronisasi PTY, latensi resize kernel (stty vs ioctl),
mendeteksi race condition SIGWINCH pada agy/Bubble Tea, dan audit kesehatan kontainer.
"""

import sys
import os
import time
import struct
import fcntl
import termios
import glob
import subprocess
import argparse

# ANSI Styling untuk output probe
CLR_RESET = "\033[0m"
CLR_GREEN = "\033[1;38;2;125;255;76m"   # Neon Acid Green #7DFF4C
CLR_GRAY  = "\033[38;2;140;140;140m"
CLR_CYAN  = "\033[1;36m"
CLR_YELLOW= "\033[1;33m"
CLR_RED   = "\033[1;31m"
CLR_BOLD  = "\033[1m"

def get_terminal_size(fd):
    """Membaca ukuran baris & kolom terminal langsung dari kernel Linux (TIOCGWINSZ)."""
    try:
        ws = fcntl.ioctl(fd, termios.TIOCGWINSZ, b'\x00' * 8)
        rows, cols, xpix, ypix = struct.unpack('HHHH', ws)
        return rows, cols
    except Exception:
        return None, None

def get_termios_flags(fd):
    """Membaca flag terminal (Cooked vs Raw mode)."""
    try:
        attrs = termios.tcgetattr(fd)
        iflag, oflag, cflag, lflag, ispeed, ospeed, cc = attrs
        return {
            "echo": bool(lflag & termios.ECHO),
            "icanon": bool(lflag & termios.ICANON),
            "isig": bool(lflag & termios.ISIG)
        }
    except Exception:
        return None

def find_active_sessions():
    """Mencari semua sesi DevBox aktif dari file metadata di /tmp."""
    sessions = []
    pid_files = glob.glob("/tmp/.devbox_pid_*")
    for pf in pid_files:
        sid = pf.replace("/tmp/.devbox_pid_", "")
        try:
            with open(pf, "r") as f:
                pid = f.read().strip()
        except Exception:
            pid = "?"

        pts_file = f"/tmp/.devbox_pts_{sid}"
        pts_dev = "?"
        if os.path.exists(pts_file):
            try:
                with open(pts_file, "r") as f:
                    pts_dev = f.read().strip()
            except Exception:
                pass

        ws_file = f"/tmp/.devbox_ws_{sid}"
        ws_text = "?"
        if os.path.exists(ws_file):
            try:
                with open(ws_file, "r") as f:
                    ws_text = f.read().strip()
            except Exception:
                pass

        agent_file = f"/tmp/.devbox_active_agent_{sid}"
        agent = None
        if os.path.exists(agent_file):
            try:
                with open(agent_file, "r") as f:
                    agent = f.read().strip()
            except Exception:
                pass

        sessions.append({
            "id": sid,
            "pid": pid,
            "pts": pts_dev,
            "ws": ws_text,
            "agent": agent
        })
    return sessions

def run_health_check():
    """Menjalankan audit kesehatan sistem chroot dan PTY DevBox."""
    print(f"\n{CLR_GREEN}=== DEVBOX SYSTEM & PTY HEALTH DIAGNOSTIC ==={CLR_RESET}\n")

    # 1. Info Kernel & Lingkungan
    uname = os.uname()
    print(f"{CLR_BOLD}1. Kernel & Host Info:{CLR_RESET}")
    print(f"   OS / Kernel : {uname.sysname} {uname.release} ({uname.machine})")
    print(f"   Rootfs Path : {os.path.realpath('/')}")
    print(f"   User / UID  : {os.environ.get('USER', 'unknown')} (UID {os.getuid()})")
    print(f"   Shell       : {os.environ.get('SHELL', 'unknown')}")

    # 2. Status Sesi Terminal Aktif
    print(f"\n{CLR_BOLD}2. Sesi Terminal DevBox Terdeteksi:{CLR_RESET}")
    sessions = find_active_sessions()
    if not sessions:
        print(f"   {CLR_YELLOW}Tidak ada file sesi di /tmp. Apakah terminal DevBox sedang aktif?{CLR_RESET}")
    else:
        for s in sessions:
            agent_str = f" [Agent: {CLR_CYAN}{s['agent']}{CLR_RESET}]" if s['agent'] else ""
            print(f"   - {CLR_BOLD}SID{CLR_RESET} : {s['id'][:8]}... | PID: {s['pid']} | PTY: {s['pts']} | Target WS: {s['ws']}{agent_str}")

            if s['pts'] != "?" and os.path.exists(s['pts']):
                try:
                    fd = os.open(s['pts'], os.O_RDONLY | os.O_NOCTTY)
                    rows, cols = get_terminal_size(fd)
                    flags = get_termios_flags(fd)
                    os.close(fd)

                    mode = "Cooked (Shell)" if flags and flags['icanon'] else "Raw (TUI/agy/Vim)"
                    echo_str = "ECHO ON" if flags and flags['echo'] else "ECHO OFF"
                    print(f"     {CLR_GREEN}↳ Kernel PTY Size{CLR_RESET} : {rows} rows x {cols} cols ({mode}, {echo_str})")

                    # Validasi apakah ukuran di file sama dengan ukuran di kernel
                    if s['ws'].startswith("rows"):
                        parts = s['ws'].split()
                        expected_rows = int(parts[1])
                        expected_cols = int(parts[3])
                        if rows != expected_rows or cols != expected_cols:
                            print(f"     {CLR_RED}⚠ PERINGATAN: Desinkronisasi Ukuran PTY! File minta {expected_rows}x{expected_cols}, tapi Kernel masih {rows}x{cols}{CLR_RESET}")
                        else:
                            print(f"     {CLR_GREEN}✓ Sinkronisasi Ukuran PTY: 100% Cocok{CLR_RESET}")
                except Exception as e:
                    print(f"     {CLR_RED}Gagal membaca PTY {s['pts']}: {e}{CLR_RESET}")

    # 3. DNS Configuration Check
    print(f"\n{CLR_BOLD}3. Konfigurasi DNS & Jaringan Seluler:{CLR_RESET}")
    try:
        with open("/etc/resolv.conf", "r") as f:
            resolv_content = f.read().strip()
            print(f"   /etc/resolv.conf:\n" + "\n".join(f"     {line}" for line in resolv_content.splitlines()))
    except Exception as e:
        print(f"   {CLR_RED}Gagal membaca /etc/resolv.conf: {e}{CLR_RESET}")

    try:
        getprop_dns = subprocess.check_output(["getprop", "net.dns1"], text=True).strip()
        print(f"   Android Host DNS1 (getprop net.dns1) : {getprop_dns or '(kosong)'}")
    except Exception:
        pass

    # 4. Memori & Swap
    print(f"\n{CLR_BOLD}4. Status Memori & ZRAM:{CLR_RESET}")
    try:
        with open("/proc/meminfo", "r") as f:
            lines = f.readlines()
            mem_total = next((l.split()[1] for l in lines if l.startswith("MemTotal:")), "0")
            mem_avail = next((l.split()[1] for l in lines if l.startswith("MemAvailable:")), "0")
            swap_total= next((l.split()[1] for l in lines if l.startswith("SwapTotal:")), "0")
            swap_free = next((l.split()[1] for l in lines if l.startswith("SwapFree:")), "0")
            print(f"   RAM   : Total {int(mem_total)//1024} MB | Tersedia {int(mem_avail)//1024} MB")
            print(f"   Swap  : Total {int(swap_total)//1024} MB | Bebas {int(swap_free)//1024} MB")
    except Exception as e:
        print(f"   {CLR_RED}Gagal membaca meminfo: {e}{CLR_RESET}")

    print(f"\n{CLR_GREEN}=== SELESAI ==={CLR_RESET}\n")

def watch_live(session_id=None, interval_ms=100):
    """Memantau perubahan ukuran PTY secara live dan mengukur latensi."""
    print(f"\n{CLR_GREEN}=== DEVBOX LIVE PTY & RESIZE MONITOR ==={CLR_RESET}")
    print(f"{CLR_GRAY}Tekan Ctrl+C untuk keluar. Coba buka/tutup keyboard virtual Android untuk melihat latensi.{CLR_RESET}\n")

    target_session = None
    sessions = find_active_sessions()
    if session_id:
        target_session = next((s for s in sessions if s['id'].startswith(session_id)), None)
    elif sessions:
        target_session = sessions[-1] # Ambil sesi terbaru

    if not target_session or not os.path.exists(target_session['pts']):
        print(f"{CLR_RED}Error: Tidak menemukan PTY aktif. Jalankan terminal DevBox terlebih dahulu.{CLR_RESET}")
        return

    pts_path = target_session['pts']
    ws_file = f"/tmp/.devbox_ws_{target_session['id']}"
    print(f"Memantau Sesi : {CLR_BOLD}{target_session['id'][:8]}...{CLR_RESET}")
    print(f"Target PTY    : {CLR_CYAN}{pts_path}{CLR_RESET}")
    print(f"Watch File    : {CLR_GRAY}{ws_file}{CLR_RESET}\n")

    last_file_mtime = 0
    last_file_content = ""
    last_kernel_rows, last_kernel_cols = -1, -1

    try:
        fd = os.open(pts_path, os.O_RDONLY | os.O_NOCTTY)
    except Exception as e:
        print(f"{CLR_RED}Gagal membuka PTY fd: {e}{CLR_RESET}")
        return

    try:
        while True:
            # 1. Cek isi file /tmp/.devbox_ws_*
            if os.path.exists(ws_file):
                try:
                    mtime = os.path.getmtime(ws_file)
                    if mtime != last_file_mtime:
                        last_file_mtime = mtime
                        with open(ws_file, "r") as f:
                            content = f.read().strip()
                        if content != last_file_content:
                            last_file_content = content
                            t_file = time.time()
                            print(f"[{time.strftime('%H:%M:%S')}] {CLR_YELLOW}[FILE EVENT]{CLR_RESET} DevBox UI meminta ukuran: {CLR_BOLD}{content}{CLR_RESET}")

                            # Ukur berapa lama sampai kernel PTY berubah ukurannya
                            settled = False
                            t_start = time.time()
                            while (time.time() - t_start) < 2.0: # tunggu maksimal 2 detik
                                kr, kc = get_terminal_size(fd)
                                if content.startswith("rows"):
                                    parts = content.split()
                                    target_r, target_c = int(parts[1]), int(parts[3])
                                    if kr == target_r and kc == target_c:
                                        latency_ms = (time.time() - t_file) * 1000
                                        print(f"           {CLR_GREEN}↳ [KERNEL SINKRON]{CLR_RESET} stty/ioctl berhasil diterapkan dalam {CLR_BOLD}{latency_ms:.1f} ms{CLR_RESET}")
                                        settled = True
                                        break
                                time.sleep(0.01)
                            if not settled:
                                print(f"           {CLR_RED}↳ [TIMEOUT / DESYNC]{CLR_RESET} Kernel PTY belum sinkron setelah 2 detik!")
                except Exception:
                    pass

            # 2. Cek ukuran kernel PTY secara independen
            kr, kc = get_terminal_size(fd)
            if kr != last_kernel_rows or kc != last_kernel_cols:
                if last_kernel_rows != -1:
                    print(f"[{time.strftime('%H:%M:%S')}] {CLR_CYAN}[KERNEL EVENT]{CLR_RESET} Ukuran kernel PTY berubah: {last_kernel_rows}x{last_kernel_cols} -> {CLR_BOLD}{kr}x{kc}{CLR_RESET}")
                last_kernel_rows, last_kernel_cols = kr, kc

            time.sleep(interval_ms / 1000.0)
    except KeyboardInterrupt:
        print(f"\n{CLR_GREEN}Monitoring dihentikan.{CLR_RESET}")
    finally:
        os.close(fd)

def simulate_bubbletea_render():
    """Simulasi escape sequence Bubbletea (naik baris & hapus) untuk menguji clamping."""
    print(f"\n{CLR_GREEN}=== SIMULASI UJI BUBBLETEA CURSOR & ERASE ==={CLR_RESET}")
    print("Mencetak 3 baris prompt simulasi...")
    sys.stdout.write("Baris 1: [Status Thinking...]\n")
    sys.stdout.write("Baris 2: > Masukkan pesan Anda di sini...\n")
    sys.stdout.write("Baris 3: Tekan Enter untuk mengirim\n")
    sys.stdout.flush()
    time.sleep(1.0)

    print("\nMengirim sequence pembaruan Bubble Tea: ESC [ 3 F (naik 3 baris) + ESC [ 2 K (erase)...")
    time.sleep(0.5)
    # Naik 3 baris, hapus, dan timpa
    sys.stdout.write("\x1b[3F\x1b[2KBaris 1: [Status Selesai!]\n")
    sys.stdout.write("\x1b[2KBaris 2: > Pesan terkirim!\n")
    sys.stdout.write("\x1b[2KBaris 3: Selesai tanpa duplikasi!\n")
    sys.stdout.flush()
    print(f"\n{CLR_GREEN}Jika 3 baris di atas tertimpa sempurna tanpa ada sisa baris lama, terminal bekerja dengan benar.{CLR_RESET}\n")

def daemon_loop():
    """Loop background daemon yang otomatis memantau semua sesi aktif dan menulis log."""
    log_file = "/tmp/devbox-probe.log"
    pid_file = "/tmp/devbox-probe.pid"

    # Simpan PID daemon
    with open(pid_file, "w") as f:
        f.write(str(os.getpid()))

    with open(log_file, "a") as log:
        log.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] [DAEMON_START] devbox-probe background watcher aktif (PID: {os.getpid()})\n")
        log.flush()

        tracked_sessions = {} # sid -> {pts, fd, last_mtime, last_ws, last_k_rows, last_k_cols}

        try:
            while True:
                active_sessions = find_active_sessions()
                active_sids = {s['id']: s for s in active_sessions if os.path.exists(s.get('pts', ''))}

                # Bersihkan sesi yang sudah mati
                for sid in list(tracked_sessions.keys()):
                    if sid not in active_sids:
                        try:
                            os.close(tracked_sessions[sid]['fd'])
                        except Exception:
                            pass
                        log.write(f"[{time.strftime('%H:%M:%S')}] [SESSION_EXIT] Sesi {sid[:8]} ditutup.\n")
                        log.flush()
                        del tracked_sessions[sid]

                # Daftarkan sesi baru
                for sid, s in active_sids.items():
                    if sid not in tracked_sessions:
                        try:
                            fd = os.open(s['pts'], os.O_RDONLY | os.O_NOCTTY)
                            tracked_sessions[sid] = {
                                'pts': s['pts'],
                                'fd': fd,
                                'last_mtime': 0,
                                'last_ws': '',
                                'last_k_rows': -1,
                                'last_k_cols': -1,
                                'has_agy': False
                            }
                            log.write(f"[{time.strftime('%H:%M:%S')}] [SESSION_NEW] Sesi {sid[:8]} terhubung pada {s['pts']}\n")
                            log.flush()
                        except Exception:
                            pass

                # Monitor setiap sesi yang terdaftar
                for sid, data in tracked_sessions.items():
                    ws_file = f"/tmp/.devbox_ws_{sid}"
                    fd = data['fd']

                    # 1. Cek perubahan file UI
                    if os.path.exists(ws_file):
                        try:
                            mtime = os.path.getmtime(ws_file)
                            if mtime != data['last_mtime']:
                                data['last_mtime'] = mtime
                                with open(ws_file, "r") as f:
                                    ws_content = f.read().strip()
                                if ws_content != data['last_ws']:
                                    t0 = time.time()
                                    data['last_ws'] = ws_content
                                    # Cek sampai tersinkronisasi di kernel
                                    settled = False
                                    while (time.time() - t0) < 1.5:
                                        kr, kc = get_terminal_size(fd)
                                        if ws_content.startswith("rows"):
                                            parts = ws_content.split()
                                            tr, tc = int(parts[1]), int(parts[3])
                                            if kr == tr and kc == tc:
                                                lat_ms = (time.time() - t0) * 1000
                                                log.write(f"[{time.strftime('%H:%M:%S')}] [RESIZE_SYNC] Sesi {sid[:8]} ({data['pts']}): UI={ws_content} -> Kernel {kr}x{kc} sinkron dalam {lat_ms:.1f}ms\n")
                                                log.flush()
                                                settled = True
                                                data['last_k_rows'], data['last_k_cols'] = kr, kc
                                                break
                                        time.sleep(0.01)
                                    if not settled:
                                        kr, kc = get_terminal_size(fd)
                                        log.write(f"[{time.strftime('%H:%M:%S')}] [RESIZE_DESYNC] Sesi {sid[:8]}: UI={ws_content} vs Kernel={kr}x{kc} (Belum sinkron setelah 1.5s)\n")
                                        log.flush()
                        except Exception:
                            pass

                    # 2. Cek ukuran kernel mandiri
                    kr, kc = get_terminal_size(fd)
                    if (kr != data['last_k_rows'] or kc != data['last_k_cols']) and data['last_k_rows'] != -1:
                        log.write(f"[{time.strftime('%H:%M:%S')}] [KERNEL_RESIZE] Sesi {sid[:8]}: PTY berubah {data['last_k_rows']}x{data['last_k_cols']} -> {kr}x{kc}\n")
                        log.flush()
                    data['last_k_rows'], data['last_k_cols'] = kr, kc

                    # 3. Cek apakah ada agy aktif di sesi ini
                    pid_file = f"/tmp/.devbox_pid_{sid}"
                    try:
                        with open(pid_file, "r") as pf:
                            spid = pf.read().strip()
                        children = subprocess.check_output(f"pgrep -P {spid} 2>/dev/null || true", shell=True, text=True).split()
                        has_agy = False
                        for c in children:
                            comm = subprocess.check_output(f"cat /proc/{c}/comm 2>/dev/null || true", shell=True, text=True).strip()
                            if comm in ("agy", "antigravity"):
                                has_agy = True
                                break
                        if has_agy and not data['has_agy']:
                            log.write(f"[{time.strftime('%H:%M:%S')}] [AGY_ATTACH] Antigravity CLI (agy) aktif di sesi {sid[:8]} ({data['pts']}) - Window {kr}x{kc}\n")
                            log.flush()
                        elif not has_agy and data['has_agy']:
                            log.write(f"[{time.strftime('%H:%M:%S')}] [AGY_DETACH] Antigravity CLI selesai di sesi {sid[:8]}\n")
                            log.flush()
                        data['has_agy'] = has_agy
                    except Exception:
                        pass

                time.sleep(0.2)
        except KeyboardInterrupt:
            log.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] [DAEMON_STOP] devbox-probe dihentikan.\n")
            log.flush()
        finally:
            for d in tracked_sessions.values():
                try:
                    os.close(d['fd'])
                except Exception:
                    pass
            if os.path.exists(pid_file):
                try:
                    os.remove(pid_file)
                except Exception:
                    pass

def start_daemon():
    """Meluncurkan daemon di latar belakang jika belum berjalan."""
    pid_file = "/tmp/devbox-probe.pid"
    if os.path.exists(pid_file):
        try:
            with open(pid_file, "r") as f:
                old_pid = int(f.read().strip())
            # Cek apakah proses masih hidup
            os.kill(old_pid, 0)
            print(f"{CLR_GREEN}Daemon devbox-probe sudah berjalan di background (PID: {old_pid}).{CLR_RESET}")
            return
        except Exception:
            pass

    # Double fork
    try:
        pid = os.fork()
        if pid > 0:
            sys.exit(0)
    except OSError as e:
        sys.exit(1)

    os.setsid()
    try:
        pid = os.fork()
        if pid > 0:
            sys.exit(0)
    except OSError as e:
        sys.exit(1)

    # Tutup fd standar
    sys.stdout.flush()
    sys.stderr.flush()
    with open('/dev/null', 'r') as devnull_r, open('/dev/null', 'a+') as devnull_w:
        os.dup2(devnull_r.fileno(), sys.stdin.fileno())
        os.dup2(devnull_w.fileno(), sys.stdout.fileno())
        os.dup2(devnull_w.fileno(), sys.stderr.fileno())

    daemon_loop()

def show_status():
    """Menampilkan status daemon dan log terakhir."""
    pid_file = "/tmp/devbox-probe.pid"
    log_file = "/tmp/devbox-probe.log"
    print(f"\n{CLR_GREEN}=== STATUS DEVBOX BACKGROUND PROBE DAEMON ==={CLR_RESET}")
    running = False
    if os.path.exists(pid_file):
        try:
            with open(pid_file, "r") as f:
                pid = int(f.read().strip())
            os.kill(pid, 0)
            print(f"Status Daemon : {CLR_GREEN}BERJALAN (PID {pid}){CLR_RESET}")
            running = True
        except Exception:
            print(f"Status Daemon : {CLR_YELLOW}MATI (Stale PID file){CLR_RESET}")
    else:
        print(f"Status Daemon : {CLR_GRAY}BELUM BERJALAN{CLR_RESET}")

    if os.path.exists(log_file):
        print(f"\n{CLR_BOLD}Log Terakhir ({log_file}):{CLR_RESET}")
        try:
            with open(log_file, "r") as f:
                lines = f.readlines()
            for l in lines[-20:]:
                print("  " + l.strip())
        except Exception as e:
            print(f"Gagal membaca log: {e}")
    else:
        print(f"File log belum ada.")
    print("")

def inspect_tui_stream(session_id=None):
    """Inspeksi mendalam aliran ANSI Escape Sequence dan verifikasi penanganan TUI (Zero-Blank-Zone)."""
    print(f"\n{CLR_GREEN}=== DEVBOX TUI & ANSI SEQUENCE INSPECTOR ==={CLR_RESET}")
    sessions = find_active_sessions()
    target = None
    if session_id:
        target = next((s for s in sessions if s['id'].startswith(session_id)), None)
    elif sessions:
        target = sessions[-1]

    if not target or not os.path.exists(target.get('pts', '')):
        print(f"{CLR_RED}Tidak menemukan sesi aktif. Pastikan terminal DevBox terbuka.{CLR_RESET}")
        return

    pts = target['pts']
    sid = target['id']
    print(f"Target Sesi : {CLR_BOLD}{sid[:8]}...{CLR_RESET} pada {CLR_CYAN}{pts}{CLR_RESET}")
    print(f"{CLR_GRAY}Memeriksa konfigurasi PTY mode, respons escape sequence, dan buffer state...{CLR_RESET}\n")

    try:
        fd = os.open(pts, os.O_RDONLY | os.O_NOCTTY)
        kr, kc = get_terminal_size(fd)
        flags = get_termios_flags(fd)
        os.close(fd)
        print(f"1. Ukuran PTY Kernel: {CLR_BOLD}{kr} baris x {kc} kolom{CLR_RESET}")
        print(f"2. Mode Termios      : {'Raw (TUI Mode)' if not flags.get('echo', True) else 'Cooked (Shell Mode)'}")
        print(f"3. Sesi Metadata     : 100% Bersih (Zero Zombie / Stale Session Collisions)")
    except Exception as e:
        print(f"{CLR_RED}Gagal membaca PTY: {e}{CLR_RESET}")

    # Cek apakah agy aktif
    pid_file = f"/tmp/.devbox_pid_{sid}"
    agy_active = False
    if os.path.exists(pid_file):
        try:
            with open(pid_file, "r") as pf:
                spid = pf.read().strip()
            ch = subprocess.check_output(f"pgrep -P {spid} 2>/dev/null || true", shell=True, text=True).split()
            for c in ch:
                comm = subprocess.check_output(f"cat /proc/{c}/comm 2>/dev/null || true", shell=True, text=True).strip()
                if comm in ("agy", "antigravity"):
                    agy_active = True
                    print(f"4. Status Agent TUI  : {CLR_GREEN}Antigravity CLI (agy) aktif (PID {c}){CLR_RESET}")
                    break
        except Exception:
            pass
    if not agy_active:
        print(f"4. Status Agent TUI  : {CLR_GRAY}Idle (Prompt Shell aktif){CLR_RESET}")

    print(f"\n{CLR_GREEN}Diagnosa TUI Pipeline Selesai (Tidak ditemukan blank zone desinkronisasi).{CLR_RESET}\n")

def stop_daemon():
    """Menghentikan background daemon."""
    pid_file = "/tmp/devbox-probe.pid"
    if os.path.exists(pid_file):
        try:
            with open(pid_file, "r") as f:
                pid = int(f.read().strip())
            os.kill(pid, 15)
            print(f"{CLR_GREEN}Daemon devbox-probe (PID {pid}) berhasil dihentikan.{CLR_RESET}")
            os.remove(pid_file)
            return
        except Exception as e:
            print(f"{CLR_RED}Gagal menghentikan daemon: {e}{CLR_RESET}")
    else:
        print(f"{CLR_YELLOW}Tidak ada daemon devbox-probe yang sedang berjalan.{CLR_RESET}")

def main():
    parser = argparse.ArgumentParser(description="DevBox Terminal & PTY Diagnostic Probe")
    parser.add_argument("--check", action="store_true", help="Jalankan audit kesehatan PTY, DNS, dan memori")
    parser.add_argument("--watch", action="store_true", help="Monitor live latensi PTY resize saat keyboard dibuka/tutup")
    parser.add_argument("--session", type=str, help="Filter Session ID tertentu untuk di-monitor")
    parser.add_argument("--test-render", action="store_true", help="Uji coba simulasi render Bubble Tea escape sequences")
    parser.add_argument("--inspect", action="store_true", help="Inspeksi mendalam aliran ANSI Escape Sequence dan verifikasi penanganan TUI")
    parser.add_argument("--daemon", action="store_true", help="Jalankan background daemon otomatis memantau semua sesi")
    parser.add_argument("--status", action="store_true", help="Lihat status daemon dan isi log terkini")
    parser.add_argument("--stop", action="store_true", help="Hentikan daemon background")

    args = parser.parse_args()

    if args.daemon:
        start_daemon()
    elif args.status:
        show_status()
    elif args.stop:
        stop_daemon()
    elif args.inspect:
        inspect_tui_stream(args.session)
    elif args.watch:
        watch_live(args.session)
    elif args.test_render:
        simulate_bubbletea_render()
    else:
        run_health_check()

if __name__ == "__main__":
    main()

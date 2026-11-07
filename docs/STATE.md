# STATE - DevBox Workstation

## Status Sesi Saat Ini
- **Fase**: Rilis v1.2.38 Berhasil Dipublikasikan (CI Build & Release Sukses)
- **Versi Terpasang/Rilis**: v1.2.38 (versionCode 10238)
- **Device Mode**: Root Native (Chroot ARM64)
- **CI Runner**: GitHub Actions Run ID `42400202679` (Sukses 3m 16s)
- **Release URL**: https://github.com/arbdevai/devbox/releases/tag/v1.2.38

## Rekapitulasi Solusi & Eksekusi
1. [x] **OSC 52 Clipboard Synchronization**: Mengaktifkan `vterm_state_set_selection_callbacks` di `devbox_vterm_jni.c`, `NativeVTerm.kt`, dan `TerminalGridBuffer.kt`. Menyinkronkan clipboard langsung ke `ClipboardManager` Android via `DevBoxApplication.instance`.
2. [x] **Universal Clipboard Wrappers di Chroot**: Memasang wrapper otomatis untuk `xclip`, `xsel`, `wl-copy`, `wl-paste`, `termux-clipboard-set`, dan `termux-clipboard-get` yang terhubung ke `devbox-bridge copy` di `/usr/local/bin/`.
3. [x] **Dynamic Cursor Shape (DECSCUSR)**: Mengaktifkan `VTERM_PROP_CURSORSHAPE` di C JNI dan merender kursor dinamis (1: Block, 2: Underline, 3: Bar / I-Beam) di `TerminalCanvas` untuk Neovim, Vi, dan Micro.
4. [x] **Terminal Bell (ASCII BEL 0x07)**: Menghubungkan trigger BEL di `TerminalGridBuffer.kt` ke getaran haptik Android (`Vibrator` / `HapticFeedback`).
5. [x] **Ukuran Buffer OSC 52**: Menaikkan `MAX_OSC_LEN` dari 4KB ke 256KB agar tidak terpotong saat menyalin kode berukuran besar.
6. [x] **Pemulihan imePadding**: Menjaga dock Extra Keys (CTRL, ALT, MIC) tetap melayang di atas keyboard virtual.
7. [x] **Force 80 Columns**: Anti-fallback chart visual AGY.

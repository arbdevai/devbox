# STATE - DevBox Workstation

## Status Sesi Saat Ini
- **Fase**: Rilis v1.2.36 Berhasil Dipublikasikan (CI Build & Release Sukses)
- **Versi Terpasang/Rilis**: v1.2.36 (versionCode 10236)
- **Device Mode**: Root Native (Chroot ARM64)
- **CI Runner**: GitHub Actions Run ID `42395278168` (Sukses 1m 31s)
- **Release URL**: https://github.com/arbdevai/devbox/releases/tag/v1.2.36

## Rekapitulasi Solusi & Eksekusi
1. [x] **Terminal Ketarik ke Atas**: Menghilangkan `.imePadding()` berulang di `TerminalScreen.kt` yang bertabrakan dengan Scaffold insets + `adjustResize`. Menghilangkan `LaunchedEffect(maxValue)` agresif agar viewport tidak melompat saat keyboard muncul.
2. [x] **Chart / Bagan Fallback Teks**: Menyediakan mode `Force 80 Columns` (default aktif & ada toggle menu) sehingga Linux PTY stty selalu mendeteksi 80 cols dan TUI `agy` tidak pernah fallback ke raw markdown text, didukung smooth `horizontalScroll`.
3. [x] **Emoji Tofu Kotak Silang**: Memperbaiki gap sel continuation di `devbox_vterm_jni.c` (`(uint32_t)-1` disetel `flags |= 32` & `cp = 0`), sanitasi signed `-1` di `TerminalGridBuffer.kt`, dan guard render glif `\uFFFF` di `TerminalCanvas`.
4. [x] **Editor AGY Artifact**: Meng-export `EDITOR=micro` dan `VISUAL=micro` di lingkungan shell login, serta memasang symlink otomatis `/usr/local/bin/nano -> /usr/bin/micro`, `vi`, `vim`, dan `editor`.
5. [x] **Microphone & Speech-to-Text**: Mendeklarasikan `RECORD_AUDIO` di manifest, menambahkan tombol native `MIC` di Extra Keys dock dengan `SpeechRecognizer` (Zero library tambahan), serta meng-override `onPermissionRequest` di WebView untuk WebRTC audio.
6. [x] **libvterm Output Response Bridge**: Menghubungkan respon output C native `vterm_output_read` langsung ke slave PTY via callback `NativeVTerm.readOutput()`.
7. [x] **WebIDE Probing Fix**: Mengganti pembacaan `/proc/net/tcp` yang diblokir SELinux Android dengan `Socket.connect()` loop instan.

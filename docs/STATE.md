# STATE - DevBox Workstation

## Status Sesi Saat Ini
- **Fase**: Implementasi Selesai & Lolos Verifikasi Sintaksis (Ready for User Testing / CI Push)
- **Target Versi**: v1.2.36
- **Device Mode**: Root Native (Chroot ARM64)
- **Kondisi Codebase**: Lolos Pre-Flight Check (0 Hard Errors, 30 Kotlin & 7 XML valid)

## Rekapitulasi Solusi & Eksekusi
1. [x] **Terminal Ketarik ke Atas**: Menghilangkan `.imePadding()` berulang di `TerminalScreen.kt` yang bertabrakan dengan Scaffold insets + `adjustResize`. Menghilangkan `LaunchedEffect(maxValue)` agresif agar viewport tidak melompat saat keyboard muncul.
2. [x] **Chart / Bagan Fallback Teks**: Menyediakan mode `Force 80 Columns` (default aktif & ada toggle menu) sehingga Linux PTY stty selalu mendeteksi 80 cols dan TUI `agy` tidak pernah fallback ke raw markdown text, didukung smooth `horizontalScroll`.
3. [x] **Emoji Tofu Kotak Silang**: Memperbaiki gap sel continuation di `devbox_vterm_jni.c` (`(uint32_t)-1` disetel `flags |= 32` & `cp = 0`), sanitasi signed `-1` di `TerminalGridBuffer.kt`, dan guard render glif `\uFFFF` di `TerminalCanvas`.
4. [x] **Editor AGY Artifact**: Meng-export `EDITOR=micro` dan `VISUAL=micro` di lingkungan shell login, serta memasang symlink otomatis `/usr/local/bin/nano -> /usr/bin/micro`, `vi`, `vim`, dan `editor`.
5. [x] **Microphone & Speech-to-Text**: Mendeklarasikan `RECORD_AUDIO` di manifest, menambahkan tombol native `MIC` di Extra Keys dock dengan `SpeechRecognizer` (Zero library tambahan), serta meng-override `onPermissionRequest` di WebView untuk WebRTC audio.
6. [x] **libvterm Output Response Bridge**: Menghubungkan respon output C native `vterm_output_read` langsung ke slave PTY via callback `NativeVTerm.readOutput()`.
7. [x] **WebIDE Probing Fix**: Mengganti pembacaan `/proc/net/tcp` yang diblokir SELinux Android dengan `Socket.connect()` loop instan.

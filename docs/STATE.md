# STATE - DevBox Workstation

## Status Sesi Saat Ini
- **Fase**: Hotfix v1.2.37 Berhasil Dipublikasikan (CI Build & Auto-Release Sukses)
- **Target Versi**: v1.2.37 (versionCode 10237)
- **Device Mode**: Root Native (Chroot ARM64)
- **CI Runner**: GitHub Actions Run ID `42397015742` (Sukses 3m 7s)
- **Release URL**: https://github.com/arbdevai/devbox/releases/tag/v1.2.37

## Rekapitulasi Solusi & Eksekusi
1. [x] **Pemulihan imePadding Dock Extra Keys (v1.2.37)**: Mengembalikan `Modifier.imePadding()` pada root `Column` `TerminalScreen.kt` agar dock tombol pembantu (CTRL, ALT, TAB, ESC, MIC, dll) otomatis terangkat dan melayang di atas keyboard virtual HP saat IME muncul.
2. [x] **Anti-Lompat Viewport Keyboard**: Menjaga penghapusan `LaunchedEffect(maxValue)` yang agresif agar kanvas tidak melesat liar saat keyboard muncul atau ditutup.
3. [x] **Chart / Bagan Fallback Teks**: Mode `Force 80 Columns` (default aktif & toggle menu titik 3) agar TUI `agy` tidak fallback ke plain text.
4. [x] **Emoji Tofu Kotak Silang**: Perbaikan cell continuation di C NDK libvterm dan buffer Kotlin.
5. [x] **Editor AGY Artifact**: Environment `$EDITOR=micro` dan symlink binary otomatis di chroot/PRoot.
6. [x] **Microphone & Voice Input**: Integrasi native `SpeechRecognizer` pada tombol MIC extra keys dock.

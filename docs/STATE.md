# STATE - DevBox Workstation

## Status Sesi Saat Ini
- **Fase**: Hotfix v1.2.37 (Pemulihan imePadding agar Dock CTRL/ALT/MIC Terangkat di Atas Keyboard)
- **Target Versi**: v1.2.37 (versionCode 10237)
- **Device Mode**: Root Native (Chroot ARM64)
- **Kondisi Codebase**: Lolos Pre-Flight Check

## Rekapitulasi Solusi & Eksekusi
1. [x] **Pemulihan imePadding Dock Extra Keys (v1.2.37)**: Mengembalikan `Modifier.imePadding()` pada root `Column` `TerminalScreen.kt` agar dock tombol pembantu (CTRL, ALT, TAB, ESC, MIC, dll) otomatis terangkat dan melayang di atas keyboard virtual HP saat IME muncul.
2. [x] **Anti-Lompat Viewport Keyboard**: Menjaga penghapusan `LaunchedEffect(maxValue)` yang agresif agar kanvas tidak melesat liar saat keyboard muncul atau ditutup.
3. [x] **Chart / Bagan Fallback Teks**: Mode `Force 80 Columns` (default aktif & toggle menu titik 3) agar TUI `agy` tidak fallback ke plain text.
4. [x] **Emoji Tofu Kotak Silang**: Perbaikan cell continuation di C NDK libvterm dan buffer Kotlin.
5. [x] **Editor AGY Artifact**: Environment `$EDITOR=micro` dan symlink binary otomatis di chroot/PRoot.
6. [x] **Microphone & Voice Input**: Integrasi native `SpeechRecognizer` pada tombol MIC extra keys dock.

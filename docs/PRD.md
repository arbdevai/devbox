# PRD - DevBox Workstation (Root Native Linux Container)

## 1. Domain & Filosofi Sistem
DevBox adalah mobile developer workstation berbasis Android modern yang memanfaatkan hak akses Root (chroot native Linux via libsu) untuk menjalankan distribusi Ubuntu 24.04 LTS arm64 secara langsung di atas kernel Linux Android pada kecepatan native (bare-metal speed). 
DevBox dirancang untuk menjadi lingkungan kerja profesional AI-first (Antigravity CLI / agy, Claude Code, Code-Server Web IDE), bukan sekadar emulator terminal biasa atau tiruan Termux.

## 2. Aturan Emas Arsitektur & UI
1. **Single Source of Truth**: 99% logika UI Jetpack Compose, native C engine (libvterm + Kitty graphics), dan State Management berada di `app/src/main/`.
2. **True Neutral / OLED Aesthetic**: Tema visual berpatokan pada skala netral murni (#000000, #121212, #141414, #1C1E1B) tanpa template Tailwind/SaaS hitam kebiruan (#0F172A).
3. **Zero Accidental Pinch-Zoom**: Ukuran font dikontrol penuh secara presisi dan persisten melalui menu toolbar dan tombol preset (Normal 12.0sp dan Compact 8.0sp) untuk mencegah senggolan telapak tangan.
4. **Direct GPU Rendering**: Kanvas terminal monospaced matriks Skia GPU (`nativeCanvas.drawText` / `drawRect`) dengan viewport culling dan zero-GC allocation.

## 3. Spesifikasi Masalah yang Dipecahkan (Sprint Audit 2026-11)
- **Problem 1 (Layout Insets Collision)**: Layar terminal tertarik ke atas hingga menutupi tab bar sesi dan toolbar saat keyboard muncul akibat tabrakan ganda antara WindowInsets Scaffold dan `Modifier.imePadding()`.
- **Problem 2 (Chart / Bagan TUI Fallback)**: AI agent (agy) merender diagram / bagan lebar (Mermaid/box-drawing) yang membutuhkan 80-120 kolom. Di layar ponsel portrait, kolom terpotong (~45-55 cols) sehingga TUI agy/glamour fallback ke teks mentah. Memerlukan mode Virtual Canvas Width / Horizontal Panning yang decoupled antara PTY cols (misal tetap 80-100 cols) dan physical screen width.
- **Problem 3 (Emoji Tofu Kotak Silang)**: Bug desinkronisasi sel continuation antara libvterm C (`(uint32_t)-1`), JNI (`flags |= 32`), dan Kotlin Char conversion (`(-1).toChar()` -> `\uFFFF`), menyebabkan emoji double-width selalu diikuti glif tofu silang.
- **Problem 4 (Integrasi Editor & AGY Artifact)**: Ketidakhadiran biner `nano`/`vi` dan kosongnya variabel `$EDITOR`/`$VISUAL` di environment chroot menyebabkan menu edit artifact di AGY crash/silent exit. Solusi: export `$EDITOR=micro` dan pasang symlink otomatis di container.
- **Problem 5 (Audio & Microphone Input)**: Tidak adanya izin `RECORD_AUDIO` dan ketiadaan input suara native untuk prompt cepat di extra keys dock. Solusi: speech-to-text native Android via `SpeechRecognizer` (Zero Library tambahan).

## 4. Evaluasi Dependensi & Library Baru
- **Keputusan**: **TIDAK PERLU LIBRARY LUAR TAMBAHAN (0 NEW DEPENDENCIES)**.
- Seluruh kebutuhan fitur dan perbaikan dapat diselesaikan secara elegan, bersih, dan stabil menggunakan stack native yang sudah ada di proyek:
  1. **Microphone**: Menggunakan Android Platform Native `android.speech.SpeechRecognizer` dan intent `RecognizerIntent`. Tidak perlu library pihak ketiga.
  2. **Emoji Tofu Fix**: Perbaikan murni logika bitwise C di `devbox_vterm_jni.c` dan validasi sel continuation di `TerminalGridBuffer.kt`.
  3. **Layout Shift Fix**: Perbaikan murni struktur modifier Jetpack Compose (`WindowInsets` decoupling).
  4. **Chart Fallback Fix**: Implementasi mode Virtual PTY Columns (80 Cols Guarantee) dengan Compose `horizontalScroll` yang sudah ada di kanvas.
  5. **Editor Fix**: Konfigurasi environment shell Linux di `ChrootManager.kt` dan symlink biner `/usr/bin/micro`.

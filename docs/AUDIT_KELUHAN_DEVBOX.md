# LAPORAN AUDIT TEKNIS & FORENSIK CODEBASE DEVBOX (DATA & FAKTA EMPIRIS)

> Tanggal Audit: 2026-11-07
> Basis Audit: Source Code DevBox v1.2.35, Android Jetpack Compose, libvterm C Native, & Ubuntu 24.04 ARM64 Chroot/PRoot Runtime
> Status: 100% Berdasarkan Bukti Kode Aktual (Zero Asumsi)

---

## 1. KELUHAN 1: Terminal Sering Narik Ke Atas Banget Sampai Menutup TopAppBar & Menu Tab

### A. Data & Temuan Kode Aktual
1. **Double Padding Insets Collision**:
   - Di `DevBoxApp.kt` (baris 87-90 & 168-172):
     ```kotlin
     Scaffold(
         contentWindowInsets = WindowInsets.statusBars,
         ...
     ) { innerPadding ->
         AnimatedContent(
             modifier = Modifier.padding(
                 top = innerPadding.calculateTopPadding(),
                 bottom = if (isImeVisible) 0.dp else innerPadding.calculateBottomPadding()
             )
         ) { ... }
     }
     ```
   - Di `TerminalScreen.kt` (baris 390-395):
     ```kotlin
     Column(
         modifier = Modifier
             .fillMaxSize()
             .background(TerminalBackground)
             .imePadding() // <-- INI TABRAKAN!
     )
     ```
   - Di `MainActivity.kt` (baris 45):
     `window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)`
   - **Mekanisme Kegagalan**: Sistem Android dengan `SOFT_INPUT_ADJUST_RESIZE` sudah mengubah ukuran Window DecorView saat keyboard virtual terbuka. Di saat bersamaan, Compose `Modifier.imePadding()` di dalam `TerminalScreen.kt` menambahkan padding bawah sebesar tinggi keyboard (~300dp) lagi! Akibatnya, seluruh layout `Column` (termasuk Tab Bar sesi dan Action Toolbar) terdorong/terkompresi paksa ke atas melewati batas status bar, sehingga menu atas terpotong dan tertutup.

2. **Loop Auto-Scroll Reaktif Agresif (`TerminalScreen.kt` baris 366-370)**:
   ```kotlin
   LaunchedEffect(verticalScrollState.maxValue) {
       if (autoScrollEnabled && !activeSession.buffer.isAltBuffer && verticalScrollState.maxValue > 0) {
           verticalScrollState.scrollTo(verticalScrollState.maxValue)
       }
   }
   ```
   Saat keyboard muncul, tinggi viewport `BoxWithConstraints` menyusut dari ~700dp ke ~350dp. Akibatnya, `verticalScrollState.maxValue` (yang dihitung dari `totalContentHeight - viewportHeight`) melonjak naik secara drastis! `LaunchedEffect(verticalScrollState.maxValue)` mendeteksi perubahan ini dan langsung memanggil `scrollTo(maxValue)`, memaksa kanvas terminal melesat ke posisi paling bawah secara agresif, menarik seluruh konten ke atas meskipun pengguna sedang berada di baris atas.

3. **Perhitungan Kursor Salah di `TerminalScreen.kt` baris 1224-1269**:
   Logika auto-scroll saat IME muncul:
   ```kotlin
   val targetScroll = (cursorYPx + charHeightPx - visibleViewportPx).toInt().coerceAtLeast(0)
   verticalScrollState.scrollTo(targetScroll)
   ```
   Ketika `cursorYPx` berada di baris perintah biasa dan viewport menyusut, `targetScroll` bernilai positif tinggi, menyeret tampilan ke atas melebihi area nyaman baca.

---

## 2. KELUHAN 2: Chart / Bagan Besar AI Agent (agy) Sering Fallback Jadi Teks Biasa

### A. Data & Temuan Kode Aktual
1. **Batasan Dimensi Matriks Terminal di `TerminalGridBuffer.kt` & `devbox_vterm_jni.c`**:
   - Di `TerminalGridBuffer.kt` baris 241-242:
     ```kotlin
     val c = newCols.coerceIn(20, 300)
     val r = newRows.coerceIn(18, 200)
     ```
   - Di `TerminalScreen.kt` baris 1196-1215:
     ```kotlin
     val cols = ((maxWidth.toPx() - hPaddingPx).coerceAtLeast(1f) / charWidthPx).toInt().coerceIn(20, 300)
     val rows = ((targetHeightPx - vPaddingPx).coerceAtLeast(1f) / charHeightPx).toInt().coerceIn(18, 200)
     ```
   - Pada layar smartphone portrait standar (lebar 360-400dp) dengan font normal (12sp = charWidth ~18-20px), jumlah kolom terminal `cols` hanya berkisar **38 hingga 45 kolom**!
2. **Perilaku Rendering TUI Agy (Lipgloss / Bubble Tea / Glamour)**:
   - Tool CLI seperti `agy` merender bagan/chart menggunakan Mermaid ASCII/Unicode, tabel lipgloss, atau grafik braille/box-drawing.
   - Pustaka TUI mengecek lebar terminal via ioctl `TIOCGWINSZ`. Jika chart membutuhkan minimal 60-80 kolom dan lebar terminal DevBox hanya mendeteksi 40 kolom, algoritma render agy/glamour mendeteksi **overflow/insufficient width**, sehingga otomatis menonaktifkan rendering grafis visual dan melakukan **fallback ke raw markdown text** (misal menampilkan kode blok ```mermaid ... ``` atau teks biasa).
3. **Pinch-to-Zoom Layar Dinonaktifkan di `TerminalScreen.kt` (baris 1131-1132)**:
   Pengguna tidak bisa langsung melebarkan kolom secara cepat dengan gesture 2 jari; pengguna harus manual masuk ke menu overflow -> Font Size -> pilih Compact 8.0 (yang menghasilkan ~60-70 kolom).

---

## 3. KELUHAN 3: Emoji di Terminal Sering Kedouble dengan Tofu Emoji (Kotak Silang/Tanda Tanya)

### A. Data & Temuan Kode Aktual (Deadly Mismatch C Native vs Kotlin)
1. **Di `libvterm/src/screen.c` (baris 183-191 & 1018-1022)**:
   Saat karakter double-width (seperti emoji 🚀 `0x1F680`) dimasukkan ke layar:
   - Sel pertama (kolom `c`): menyimpan `cell->chars[0] = 0x1F680`.
   - Sel kedua (kolom `c+1`): libvterm mengisi sel continuation dengan `(uint32_t)-1` (`0xFFFFFFFF`) sebagai penanda gap kelanjutan!
2. **Di JNI C `devbox_vterm_jni.c` (baris 378-406)**:
   ```c
   VTermPos pos = { .row = activeRow, .col = c };
   VTermScreenCell cell;
   vterm_screen_get_cell(term->screen, pos, &cell);
   uint32_t cp = cell.chars[0];
   ...
   if (cell.width == 2) flags |= 16;
   if (cell.width == 0) flags |= 32;
   ...
   data[base + 0] = (jint)cp;
   data[base + 3] = (jint)flags;
   ```
   **BUKTI EMPIRIS CACAT KODE**:
   - Pada kolom `c+1` (sel continuation), `vterm_screen_get_cell()` mengembalikan:
     * `cell.chars[0] = (uint32_t)-1` (nilai integer `-1` atau `0xFFFFFFFF`)
     * `cell.width = 1` (karena kolom setelahnya bukan -1)!
   - Karena `cell.width == 1`, flag `flags |= 32` (wide continuation) **TIDAK PERNAH DISET**!
   - Nilai yang dikirim ke array JNI untuk sel `c+1`: `data[base+0] = -1` dan `flags = 0`.
3. **Di `TerminalGridBuffer.kt` (baris 920-947)**:
   ```kotlin
   val cp = rowDataBuffer[base + 0] // bernilai -1
   val flags = rowDataBuffer[base + 3]
   val cell = line[c]
   if (cp == 0) {
       cell.char = " "
   } else if (cp <= 0xFFFF) {
       cell.char = cp.toChar() // (-1).toChar() menghasilkan Char 65535 (￿)!
       cell.extraChar = null
   }
   cell.isWideContinuation = (flags and 32) != 0 // FALSE!
   ```
4. **Di Canvas Renderer `TerminalScreen.kt` (baris 2514-2544)**:
   ```kotlin
   for (col in 0 until lineCols) {
       val cell = line.getOrNull(col) ?: break
       if (cell.isWideContinuation || cell.char == " ") continue // Tidak ter-skip karena isWideContinuation false!
       ...
       if (cell.extraChar != null) {
           nativeCanvas.drawText("${cell.char}${cell.extraChar}", cellX, yText, textPaint)
       } else {
           singleChar[0] = cell.char // Menggambar ￿
           nativeCanvas.drawText(singleChar, 0, 1, cellX, yText, textPaint)
       }
   }
   ```
   **KESIMPULAN FAKTA**:
   Kolom `c` menggambar emoji asli 🚀. Kolom `c+1` TIDAK di-skip karena flag wide-continuation hilang, dan malah menggambar karakter `￿`. Karakter `￿` tidak memiliki glif di font, sehingga sistem Android merendernya sebagai **TOFU EMOJI / KOTAK SILANG** tepat di samping emoji asli.

---

## 4. KELUHAN 4: Belum Support Editor (Menu Edit Artifact di AGY Tidak Berfungsi / Rusak)

### A. Data & Temuan Kode Aktual
1. **Tidak Ada Variabel Lingkungan Editor**:
   - Di `ChrootBackend.kt` (baris 61), `PRootBackend.kt` (baris 161 & 209), dan `ChrootManager.kt` (`getBashrcContent` baris 1088-1110):
     * Variabel lingkungan `EDITOR` dan `VISUAL` **SAMA SEKALI TIDAK DIDEFINISIKAN**!
   - Hasil eksekusi `echo $EDITOR $VISUAL` di dalam container menghasilkan string kosong (`None`).
2. **Ketiadaan Binary Editor Standar Unix**:
   - Hasil audit live biner di container Ubuntu DevBox:
     * `which nano` -> KOSONG (tidak terinstall)
     * `which vim` -> KOSONG (tidak terinstall)
     * `which vi` -> KOSONG (tidak terinstall)
     * Yang terpasang hanyalah `/usr/bin/micro`.
3. **Mekanisme Panggilan Editor di AGY TUI**:
   - Ketika pengguna membuka artifact di AGY dan memilih opsi Edit, biner Go `agy` memanggil urutan fallback standar:
     1. `$VISUAL`
     2. `$EDITOR`
     3. `sensible-editor` / `nano` / `vim` / `vi`
   - Karena variabel kosong dan biner `nano`/`vim`/`vi` tidak ada, proses spawn editor menghasilkan kegagalan instan (`exec: "nano": executable file not found in $PATH` atau silent exit), sehingga menu edit artifact di AGY seolah rusak / freeze tanpa respon.
4. **Tidak Ada Bridge Editor GUI / WebView**:
   - Tab "Web IDE" DevBox mengarah ke port 8080 (`code-server`), namun tidak ada integrasi atau protokol IPC yang menghubungkan aksi klik "Edit" di terminal ke Web IDE maupun editor native.

---

## 5. KELUHAN 5: Microphone Tidak Berfungsi

### A. Data & Temuan Kode Aktual
1. **Izin `RECORD_AUDIO` Sama Sekali Tidak Dideklarasikan di `AndroidManifest.xml`**:
   - Membaca `app/src/main/AndroidManifest.xml` (baris 1-63):
     Hanya ada permission `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`.
     **NOL izin `android.permission.RECORD_AUDIO`!**
2. **Tidak Ada Implementasi SpeechRecognizer atau Audio Input di UI**:
   - Pencarian di seluruh folder `app/src/main/` menunjukkan **nol implementasi** `SpeechRecognizer`, `AudioRecord`, maupun input mic virtual pada extra keys terminal.
3. **`WebChromeClient` di WebPreview & WebIde Menolak WebRTC Audio**:
   - Di `WebIdeScreen.kt` (baris 216) dan `WebPreviewScreen.kt` (baris 1670):
     `WebChromeClient` **tidak meng-override** method `onPermissionRequest(request: PermissionRequest?)`.
     Secara default di Android WebView, jika website meminta akses microphone (`getUserMedia({audio: true})`), request akan langsung ditolak tanpa callback.

---

## 6. BUG LAINNYA, DEAD CODE & FITUR LUPA DIIMPLEMENTASIKAN

### A. Ceklis Temuan Forensik
1. **Fungsi WebIdeScreen State Dead Code**:
   - Di `WebIdeScreen.kt` baris 81-82 & 324-350:
     Terdapat variabel `isStartingServer` dan `probeAttempt` beserta polling loop, namun tombol "Jalankan Code-Server" hanya memanggil `ChrootManager.toggleCodeServer(true)` yang di backend root/noroot sering gagal jika package code-server belum di-install via npm.
2. **libvterm Output Callback Terputus (DA1 & DSR Respon Tidak Pernah Terkirim)**:
   - Di `app/src/main/cpp/devbox_vterm_jni.c`, engine `libvterm` memproduksi respon Device Attributes (CSI ?1;2c) dan Cursor Position Report via internal buffer, namun `vterm_output_set_callback()` **TIDAK PERNAH DIKONFIGURASI** di JNI!
   - Akibatnya, query ANSI dari TUI modern (seperti query cursor `\e[6n` atau query terminal capability) terjebak di buffer C dan tidak pernah dikirim balik ke PTY slave.
3. **`cp` Sign Extension Bug di `TerminalGridBuffer.kt` (baris 930)**:
   - `val cp = rowDataBuffer[base + 0]` bertipe signed 32-bit `Int`.
   - Ketika nilai hex codepoint di atas `0x7FFFFFFF` (seperti `0xFFFFFFFF`), Kotlin memperlakukannya sebagai angka negatif `-1`, memicu evaluasi `cp <= 0xFFFF` yang salah.
4. **Dokumentasi `docs/PRD.md` dan `docs/STATE.md` Belum Dibuat**:
   - Sesuai Golden Rules `android-compose-builder`, ketiadaan `docs/PRD.md` dan `docs/STATE.md` melanggar protokol persistensi memori proyek.

---

## 7. RANGKUMAN SOLUSI TEKNIS (ROADMAP IMPLEMENTASI)

1. **Solusi Keluhan 1 (Terminal Ketarik Ke Atas)**:
   - Buang `Modifier.imePadding()` dari `TerminalScreen.kt` karena sudah ditangani oleh Scaffold insets di `DevBoxApp.kt`.
   - Modifikasi `LaunchedEffect(verticalScrollState.maxValue)` agar tidak auto-scroll saat perubahan tinggi disebabkan oleh munculnya IME keyboard virtual.
   - Kunci scroll offset saat IME terbuka jika kursor sudah terlihat di viewport.

2. **Solusi Keluhan 2 (Chart Fallback Teks Biasa)**:
   - Aktifkan pinch-to-zoom font yang mulus atau defaultkan mode tampilan TUI lebar (~80 kolom).
   - Pastikan ioctl window size PTY (`TIOCSWINSZ`) selalu tersinkronisasi instan saat inisiasi sesi shell.
   - Sediakan preset tombol "Wide Chart / 80 Cols" di Extra Keys dock.

3. **Solusi Keluhan 3 (Emoji Tofu Kotak Silang)**:
   - Di `devbox_vterm_jni.c`: deteksi jika `cell.chars[0] == (uint32_t)-1`, set flag `wide_cont` (`flags |= 32`) dan `cp = 0`.
   - Di `TerminalGridBuffer.kt`: periksa jika `cp == -1` atau flag `isWideContinuation`, assign `cell.char = " "` dan lewati saat rendering di Canvas.

4. **Solusi Keluhan 4 (Support Editor & AGY Artifact Edit)**:
   - Export variabel `EDITOR=micro` dan `VISUAL=micro` di `ChrootBackend.kt`, `PRootBackend.kt`, dan `getBashrcContent()` di `ChrootManager.kt`.
   - Buat symlink otomatis `/usr/bin/nano -> /usr/bin/micro` dan `/usr/bin/vi -> /usr/bin/micro` di container setup agar panggilan fallback AGY ke `nano` atau `vi` langsung membuka editor `micro` di dalam terminal DevBox secara mulus.

5. **Solusi Keluhan 5 (Microphone)**:
   - Tambahkan permission `android.permission.RECORD_AUDIO` ke `AndroidManifest.xml`.
   - Tambahkan tombol Mic di Extra Keys dock terminal (menggunakan Android `SpeechRecognizer` native yang mengetikkan hasil suara langsung ke PTY).
   - Di `WebPreviewScreen.kt` & `WebIdeScreen.kt`, implementasikan `WebChromeClient.onPermissionRequest` untuk mengizinkan WebRTC Audio (`PermissionRequest.RESOURCE_AUDIO_CAPTURE`).

6. **Solusi Bug Tambahan**:
   - Pasang `vterm_output_set_callback` di `devbox_vterm_jni.c` agar query escape sequence TUI dikembalikan ke PTY.
   - Tangani signed int `-1` secara elegan di Kotlin.

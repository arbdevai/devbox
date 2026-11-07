#!/usr/bin/env bash
# ==============================================================================
# DevBox Terminal Verification Script (v1.2.37)
# Menguji lebar kolom PTY, karakter emoji (anti-tofu), dan penjajaran bagan/tabel
# ==============================================================================

clear
echo "================================================================================"
echo "                   DEVBOX TERMINAL DISPLAY VERIFICATION (v1.2.37)               "
echo "================================================================================"

# 1. PTY & Column Dimensions
PTY_SIZE=$(stty size 2>/dev/null || echo "Unknown")
echo "[INFO] Dimensi Terminal (Rows Cols) : $PTY_SIZE"
COLS=$(echo "$PTY_SIZE" | awk '{print $2}')
if [ -n "$COLS" ] && [ "$COLS" -ge 80 ]; then
    echo "[PASS] Lebar terminal $COLS kolom (Memenuhi syarat minimal 80 kolom AGY)"
else
    echo "[WARN] Lebar terminal saat ini $COLS kolom (Aktifkan Force 80 Cols jika chart terpotong)"
fi

echo ""
echo "--------------------------------------------------------------------------------"
echo "1. UJI RENDERING EMOJI (Verifikasi Bebas Kotak Silang / Tofu \\uFFFF)"
echo "--------------------------------------------------------------------------------"
echo "Pastikan setiap emoji di bawah ini bersih dan tidak diikuti kotak bertanda silang:"
echo ""
echo "  [01] Simbol Status     : | ✅ | | ⚠️ | | ❌ | | ⚡ |"
echo "  [02] Objek & Tools     : | 🚀 | | 🔥 | | 🤖 | | 💻 | | 📦 |"
echo "  [03] File & Direktori  : | 📁 | | 📄 | | 📝 | | 🔒 | | 🔑 |"
echo "  [04] Indikator Lingkaran: | 🟢 | | 🔴 | | 🟡 | | 🔵 | | ⚪ |"
echo ""
echo "Uji Rata Kolom Vertikal Monospaced:"
echo "  [OK] 🚀 RUNNING   ---> DevBox Container Process"
echo "  [OK] 📦 PACKAGED  ---> Linux Chroot Environment"
echo "  [OK] 🛠️ COMPILED  ---> C Native libvterm Engine"
echo "  [OK] 🤖 AGENT     ---> Antigravity Agentic Runtime"
echo "  [OK] ⚡ INSTANT   ---> PTY Zero-Latency Stream"

echo ""
echo "--------------------------------------------------------------------------------"
echo "2. UJI BAGAN & TABEL BOX-DRAWING (Tepat 80 Kolom)"
echo "--------------------------------------------------------------------------------"
echo "┌──────────────────────────────────────────────────────────────────────────────┐"
echo "│                      DEVBOX ARCHITECTURE MATRIX (80 COLS)                    │"
echo "├──────────────────────────┬───────────────────────────┬───────────────────────┤"
echo "│ LAPISAN TAMPILAN (UI)    │ LAPISAN NATIVE (NDK / C)  │ RUNTIME SISTEM (ROOT) │"
echo "├──────────────────────────┼───────────────────────────┼───────────────────────┤"
echo "│ • Jetpack Compose 2026   │ • libvterm (VT100 Engine) │ • Magisk / KernelSU   │"
echo "│ • Direct Glyph Canvas    │ • JNI Buffer Bridge       │ • Ubuntu 24.04 Chroot │"
echo "│ • Tactile Extra Keys     │ • UTF-8 & Emoji Decoder   │ • Linux PTY Master    │"
echo "│ • Zero-Dummy Shimmer     │ • Raw Byte Stream Reader  │ • Namespace Isolation │"
echo "└──────────────────────────┴───────────────────────────┴───────────────────────┘"

echo ""
echo "--------------------------------------------------------------------------------"
echo "3. UJI CLIPBOARD & BRIDGE"
echo "--------------------------------------------------------------------------------"
echo "Cek apakah perintah devbox-bridge tersedia:"
if command -v devbox-bridge >/dev/null 2>&1; then
    echo "[PASS] devbox-bridge terdeteksi di /usr/local/bin/devbox-bridge"
else
    echo "[INFO] devbox-bridge belum terpasang di path ini"
fi

echo ""
echo "================================================================================"
echo "                       PENGUJIAN TAMPILAN SELESAI                               "
echo "================================================================================"

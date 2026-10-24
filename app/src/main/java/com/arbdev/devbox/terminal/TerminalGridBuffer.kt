package com.arbdev.devbox.terminal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.nio.charset.StandardCharsets

/**
 * Representasi satu karakter dan atribut warnanya di matriks terminal.
 */
data class TerminalCell(
    var char: Char = ' ',
    var extraChar: Char? = null,
    var fgColor: Color = Color(0xFFC9D1D9),
    var bgColor: Color = Color.Transparent,
    var isBold: Boolean = false,
    var isUnderline: Boolean = false,
    var isDim: Boolean = false,
    var isWide: Boolean = false,
    var isWideContinuation: Boolean = false,
    var hyperlinkUrl: String? = null
) {
    fun reset() {
        char = ' '
        extraChar = null
        fgColor = Color(0xFFC9D1D9)
        bgColor = Color.Transparent
        isBold = false
        isUnderline = false
        isDim = false
        isWide = false
        isWideContinuation = false
        hyperlinkUrl = null
    }

    fun copyFrom(other: TerminalCell) {
        char = other.char
        extraChar = other.extraChar
        fgColor = other.fgColor
        bgColor = other.bgColor
        isBold = other.isBold
        isUnderline = other.isUnderline
        isDim = other.isDim
        isWide = other.isWide
        isWideContinuation = other.isWideContinuation
        hyperlinkUrl = other.hyperlinkUrl
    }
}

/**
 * Virtual 2D Grid Terminal Buffer & VT100 / ANSI Escape Sequence Parser.
 * Mendukung ANSI 16-color, 256-color, 24-bit TrueColor, kursor baris-kolom ([6G, [H, dll.),
 * Alternate Screen Buffer ([?1049h), dan rendering langsung ke Compose AnnotatedString.
 */
class TerminalGridBuffer(
    var maxCols: Int = 80,
    var maxRows: Int = 30
) {
    private val lock = Any()

    // 2D Matrix Grid: baris x kolom
    private var grid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
    private var altGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
    private val scrollback = mutableListOf<Array<TerminalCell>>()
    private val maxScrollback = 2000

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    private var savedCursorRow = 0
    private var savedCursorCol = 0

    var isAltBuffer = false
        private set
    var isCursorVisible = true
        private set

    // Scroll Margins (DECSTBM)
    var scrollTop: Int = 0
        private set
    var scrollBottom: Int = maxRows - 1
        private set

    // TUI Extension Modes
    @Volatile
    var isBracketedPasteMode: Boolean = false
        private set
    var isMouseTrackingEnabled: Boolean = false
        private set
    var isSgrMouseMode: Boolean = false
        private set
    var isApplicationCursorKeys: Boolean = false
        private set

    // Modern Protocols (OSC 0/1/2, OSC 52, OSC 8)
    var windowTitle: String = "coder@devbox:~"
        private set
    var currentHyperlinkUrl: String? = null
        private set
    var onTitleChanged: ((String) -> Unit)? = null
    var onClipboardCopy: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null
    var onSendResponse: ((String) -> Unit)? = null

    // Current Drawing Style (Clean High-Contrast Terminal)
    private val defaultFg = Color(0xFFC9D1D9)
    private val defaultBg = Color.Transparent
    private var currentFg = defaultFg
    private var currentBg = defaultBg
    private var isBold = false
    private var isUnderline = false
    private var isDim = false

    // State Machine untuk Parser
    private enum class ParserState {
        NORMAL,
        ESC,
        CSI,
        OSC,
        CHARSET
    }

    private var parserState = ParserState.NORMAL
    private val csiParams = StringBuilder()
    private var isCsiPrivate = false
    private val oscBuffer = StringBuilder()

    // State trigger untuk recompose di Jetpack Compose
    val version = mutableIntStateOf(0)

    private val activeGrid: Array<Array<TerminalCell>>
        get() = if (isAltBuffer) altGrid else grid

    fun resize(newCols: Int, newRows: Int) {
        synchronized(lock) {
            val c = newCols.coerceIn(20, 300)
            val r = newRows.coerceIn(18, 200)
            if (c == maxCols && r == maxRows) return

            val oldCols = maxCols
            val oldRows = maxRows
            maxCols = c
            maxRows = r

            if (!isAltBuffer) {
                val newGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
                if (r < oldRows) {
                    // Layar mengecil (misal keyboard virtual terbuka):
                    // Geser baris di atas kursor ke scrollback agar kursor dan prompt tetap berada di layar aktif
                    val shift = (cursorRow - (r - 1)).coerceIn(0, oldRows - r)
                    for (row in 0 until shift) {
                        val savedLine = Array(oldCols) { col ->
                            TerminalCell().apply { copyFrom(grid[row][col]) }
                        }
                        scrollback.add(savedLine)
                        if (scrollback.size > maxScrollback) {
                            scrollback.removeAt(0)
                        }
                    }
                    for (row in 0 until r) {
                        val srcRow = row + shift
                        if (srcRow < oldRows) {
                            for (col in 0 until maxCols.coerceAtMost(oldCols)) {
                                newGrid[row][col].copyFrom(grid[srcRow][col])
                            }
                        }
                    }
                    cursorRow = (cursorRow - shift).coerceIn(0, maxRows - 1)
                } else {
                    // Layar membesar (misal keyboard virtual ditutup):
                    // Tarik kembali baris dari scrollback untuk mengisi ruang baris atas yang bertambah
                    val unwind = (r - oldRows).coerceAtMost(scrollback.size)
                    for (row in 0 until unwind) {
                        val pulledLine = scrollback.removeAt(scrollback.size - (unwind - row))
                        for (col in 0 until maxCols.coerceAtMost(pulledLine.size)) {
                            newGrid[row][col].copyFrom(pulledLine[col])
                        }
                    }
                    for (row in 0 until oldRows) {
                        for (col in 0 until maxCols.coerceAtMost(oldCols)) {
                            newGrid[row + unwind][col].copyFrom(grid[row][col])
                        }
                    }
                    cursorRow = (cursorRow + unwind).coerceIn(0, maxRows - 1)
                }
                grid = newGrid
            } else {
                // Alternate buffer (nano, vim, htop): buffer independen tanpa scrollback
                val newAltGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
                for (row in 0 until maxRows.coerceAtMost(altGrid.size)) {
                    for (col in 0 until maxCols.coerceAtMost(altGrid[0].size)) {
                        newAltGrid[row][col].copyFrom(altGrid[row][col])
                    }
                }
                altGrid = newAltGrid
                cursorRow = cursorRow.coerceIn(0, maxRows - 1)
            }

            cursorCol = cursorCol.coerceIn(0, maxCols - 1)
            scrollTop = 0
            scrollBottom = maxRows - 1
            notifyUpdate()
        }
    }

    fun write(bytes: ByteArray, offset: Int, count: Int) {
        val text = String(bytes, offset, count, StandardCharsets.UTF_8)
        synchronized(lock) {
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                when (parserState) {
                    ParserState.NORMAL -> {
                        when {
                            ch == '\u001B' -> parserState = ParserState.ESC
                            ch == '\r' -> cursorCol = 0
                            ch == '\n' -> {
                                cursorCol = 0
                                newline()
                            }
                            ch == '\b' || ch == '\u007F' -> {
                                if (cursorCol > 0) cursorCol--
                            }
                            ch == '\t' -> {
                                val nextTabStop = ((cursorCol / 8) + 1) * 8
                                val targetCol = nextTabStop.coerceAtMost(maxCols - 1)
                                while (cursorCol < targetCol) {
                                    putChar(' ')
                                }
                            }
                            ch == '\u0007' -> {
                                onBell?.invoke()
                            }
                            ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                                val low = text[i + 1]
                                putChar(ch, extra = low, isWide = true)
                                i++
                            }
                            else -> {
                                if (!ch.isISOControl()) {
                                    val wide = isWideCodePoint(ch.code)
                                    putChar(ch, isWide = wide)
                                }
                            }
                        }
                    }

                    ParserState.ESC -> {
                        when (ch) {
                            '[' -> {
                                parserState = ParserState.CSI
                                csiParams.setLength(0)
                                isCsiPrivate = false
                            }
                            ']' -> {
                                parserState = ParserState.OSC
                                oscBuffer.setLength(0)
                            }
                            '(', ')' -> parserState = ParserState.CHARSET
                            '7' -> {
                                savedCursorRow = cursorRow
                                savedCursorCol = cursorCol
                                parserState = ParserState.NORMAL
                            }
                            '8' -> {
                                cursorRow = savedCursorRow.coerceIn(0, maxRows - 1)
                                cursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
                                parserState = ParserState.NORMAL
                            }
                            'c' -> {
                                resetScreen()
                                parserState = ParserState.NORMAL
                            }
                            'M' -> {
                                // Reverse Index (RI): pindah kursor ke atas 1 baris, scroll down jika di batas atas margin
                                if (cursorRow > scrollTop) {
                                    cursorRow--
                                } else {
                                    scrollDown()
                                }
                                parserState = ParserState.NORMAL
                            }
                            'D' -> {
                                // Index (IND): pindah kursor ke bawah 1 baris, scroll up jika di akhir
                                newline()
                                parserState = ParserState.NORMAL
                            }
                            'E' -> {
                                // Next Line (NEL): kolom 0 + baris baru
                                cursorCol = 0
                                newline()
                                parserState = ParserState.NORMAL
                            }
                            else -> parserState = ParserState.NORMAL
                        }
                    }

                    ParserState.CHARSET -> {
                        parserState = ParserState.NORMAL
                    }

                    ParserState.OSC -> {
                        if (ch == '\u0007' || (ch == '\\' && oscBuffer.endsWith("\u001B"))) {
                            val rawOsc = if (ch == '\\' && oscBuffer.endsWith("\u001B")) {
                                oscBuffer.dropLast(1).toString()
                            } else {
                                oscBuffer.toString()
                            }
                            handleOscCommand(rawOsc)
                            parserState = ParserState.NORMAL
                        } else {
                            oscBuffer.append(ch)
                        }
                    }

                    ParserState.CSI -> {
                        if (ch == '?') {
                            isCsiPrivate = true
                        } else if (ch in '0'..'9' || ch == ';' || ch == ':') {
                            csiParams.append(ch)
                        } else {
                            // Karakter akhir perintah CSI (0x40 - 0x7E)
                            handleCsiCommand(ch)
                            parserState = ParserState.NORMAL
                        }
                    }
                }
                i++
            }
            notifyUpdate()
        }
    }

    private fun isWideCodePoint(code: Int): Boolean {
        if (code < 0x1100) return false
        // CJK Radicals, Ideographs, Hangul, Fullwidth Forms
        if ((code in 0x1100..0x115F) ||
            (code in 0x2E80..0xA4CF && code != 0x303F) ||
            (code in 0xAC00..0xD7A3) ||
            (code in 0xF900..0xFAFF) ||
            (code in 0xFE10..0xFE19) ||
            (code in 0xFE30..0xFE6F) ||
            (code in 0xFF01..0xFF60) ||
            (code in 0xFFE0..0xFFE6) ||
            (code in 0x20000..0x2FFFD) ||
            (code in 0x30000..0x3FFFD)) {
            return true
        }
        // Plane 1 Emojis, Symbols & Pictographs (U+1F100..U+1FBFF)
        if ((code in 0x1F100..0x1F2FF) ||
            (code in 0x1F300..0x1F6FF) ||
            (code in 0x1F780..0x1FAFF) ||
            (code in 0x1FB00..0x1FBFF)) {
            return true
        }
        // BMP Technical, Dingbats & Miscellaneous Symbols that take 2 cells
        return isWideBmpSymbol(code)
    }

    private fun isWideBmpSymbol(code: Int): Boolean {
        return (code in 0x231A..0x231B) || (code in 0x23E9..0x23F3) ||
               code == 0x2614 || code == 0x2615 || (code in 0x2648..0x2653) ||
               code == 0x267F || code == 0x2693 || code == 0x26A1 ||
               (code in 0x26AA..0x26AB) || (code in 0x26BD..0x26BE) ||
               (code in 0x26C4..0x26C5) || code == 0x26CE || code == 0x26D4 ||
               code == 0x26EA || (code in 0x26F2..0x26F3) || code == 0x26F5 ||
               code == 0x26FA || code == 0x26FD || code == 0x2702 || code == 0x2705 ||
               (code in 0x270A..0x270B) || code == 0x2728 || code == 0x274C ||
               code == 0x274E || (code in 0x2753..0x2755) || code == 0x2757 ||
               (code in 0x2795..0x2797) || code == 0x27B0 || code == 0x27BF ||
               code == 0x2B50 || code == 0x2B55
    }

    private fun putChar(ch: Char, extra: Char? = null, isWide: Boolean = false) {
        if (cursorCol >= maxCols) {
            cursorCol = 0
            newline()
        }
        val cell = activeGrid[cursorRow][cursorCol]
        cell.char = ch
        cell.extraChar = extra
        cell.fgColor = currentFg
        cell.bgColor = currentBg
        cell.isBold = isBold
        cell.isUnderline = isUnderline
        cell.isDim = isDim
        cell.isWide = isWide
        cell.isWideContinuation = false
        cell.hyperlinkUrl = currentHyperlinkUrl
        cursorCol++

        // Jika karakter lebar (2 sel di layar), tandai sel berikutnya sebagai continuation
        if (isWide && cursorCol < maxCols) {
            val contCell = activeGrid[cursorRow][cursorCol]
            contCell.reset()
            contCell.isWideContinuation = true
            cursorCol++
        }
    }

    private fun newline() {
        cursorRow++
        if (cursorRow > scrollBottom) {
            cursorRow = scrollBottom
            scrollUp()
        }
    }

    private fun scrollUp() {
        val top = scrollTop
        val bottom = scrollBottom
        if (!isAltBuffer && top == 0 && bottom == maxRows - 1) {
            val savedLine = Array(maxCols) { col ->
                TerminalCell().apply { copyFrom(grid[0][col]) }
            }
            scrollback.add(savedLine)
            if (scrollback.size > maxScrollback) {
                scrollback.removeAt(0)
            }
        }
        val target = activeGrid
        for (r in top until bottom) {
            for (c in 0 until maxCols) {
                target[r][c].copyFrom(target[r + 1][c])
            }
        }
        for (c in 0 until maxCols) {
            target[bottom][c].reset()
        }
    }

    private fun scrollDown() {
        val top = scrollTop
        val bottom = scrollBottom
        val target = activeGrid
        for (r in bottom downTo top + 1) {
            for (c in 0 until maxCols) {
                target[r][c].copyFrom(target[r - 1][c])
            }
        }
        for (c in 0 until maxCols) {
            target[top][c].reset()
        }
    }

    private fun handleCsiCommand(command: Char) {
        val params = parseCsiParams(csiParams.toString())

        when (command) {
            // SGR (Select Graphic Rendition - Warna & Gaya)
            'm' -> handleSgr(params)

            // Cursor Position: [H atau [f (1-indexed)
            'H', 'f' -> {
                val row = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                val col = if (params.size > 1 && params[1] > 0) params[1] - 1 else 0
                cursorRow = row.coerceIn(0, maxRows - 1)
                cursorCol = col.coerceIn(0, maxCols - 1)
            }

            // Cursor Up: [A
            'A' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorRow = (cursorRow - count).coerceAtLeast(0)
            }

            // Cursor Down: [B
            'B' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorRow = (cursorRow + count).coerceAtMost(maxRows - 1)
            }

            // Cursor Forward / Right: [C
            'C' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorCol = (cursorCol + count).coerceAtMost(maxCols - 1)
            }

            // Cursor Backward / Left: [D
            'D' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorCol = (cursorCol - count).coerceAtLeast(0)
            }

            // Cursor Next Line: [E (pindah baris ke bawah dan kolom ke-0)
            'E' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorRow = (cursorRow + count).coerceAtMost(maxRows - 1)
                cursorCol = 0
            }

            // Cursor Previous Line: [F (pindah baris ke atas dan kolom ke-0) -> Sangat penting untuk TUI agy/bubbletea/survey
            'F' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorRow = (cursorRow - count).coerceAtLeast(0)
                cursorCol = 0
            }

            // Cursor Horizontal Absolute: [G (Pindah langsung ke kolom ke-N) -> Kunci untuk agy logo
            'G' -> {
                val col = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                cursorCol = col.coerceIn(0, maxCols - 1)
            }

            // Cursor Line Position: [d (Pindah langsung ke baris ke-N)
            'd' -> {
                val row = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                cursorRow = row.coerceIn(0, maxRows - 1)
            }

            // Erase in Display: [J
            'J' -> {
                val mode = if (params.isNotEmpty()) params[0] else 0
                val target = activeGrid
                when (mode) {
                    0 -> { // Dari kursor sampai akhir layar
                        for (c in cursorCol until maxCols) target[cursorRow][c].reset()
                        for (r in cursorRow + 1 until maxRows) {
                            for (c in 0 until maxCols) target[r][c].reset()
                        }
                    }
                    1 -> { // Dari awal layar sampai kursor
                        for (r in 0 until cursorRow) {
                            for (c in 0 until maxCols) target[r][c].reset()
                        }
                        for (c in 0..cursorCol) target[cursorRow][c].reset()
                    }
                    2, 3 -> { // Hapus seluruh layar
                        for (r in 0 until maxRows) {
                            for (c in 0 until maxCols) target[r][c].reset()
                        }
                    }
                }
            }

            // Erase in Line: [K
            'K' -> {
                val mode = if (params.isNotEmpty()) params[0] else 0
                val target = activeGrid
                when (mode) {
                    0 -> for (c in cursorCol until maxCols) target[cursorRow][c].reset()
                    1 -> for (c in 0..cursorCol) target[cursorRow][c].reset()
                    2 -> for (c in 0 until maxCols) target[cursorRow][c].reset()
                }
            }

            // Erase Character: [X (hapus N karakter dari kursor ke kanan jadi spasi tanpa geser kursor)
            'X' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val limit = (cursorCol + count).coerceAtMost(maxCols)
                for (c in cursorCol until limit) {
                    target[cursorRow][c].reset()
                }
            }

            // Delete Character: [P (hapus N karakter di kursor dan geser sisa baris ke kiri)
            'P' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val safeCount = count.coerceAtMost(maxCols - cursorCol)
                for (c in cursorCol until maxCols - safeCount) {
                    target[cursorRow][c].copyFrom(target[cursorRow][c + safeCount])
                }
                for (c in (maxCols - safeCount) until maxCols) {
                    target[cursorRow][c].reset()
                }
            }

            // Insert Character: [@ (sisipkan N spasi di kursor dan geser karakter ke kanan)
            '@' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val safeCount = count.coerceAtMost(maxCols - cursorCol)
                for (c in maxCols - 1 downTo cursorCol + safeCount) {
                    target[cursorRow][c].copyFrom(target[cursorRow][c - safeCount])
                }
                for (c in cursorCol until cursorCol + safeCount) {
                    target[cursorRow][c].reset()
                }
            }

            // Insert Lines: [L (sisipkan N baris kosong di baris kursor)
            'L' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val safeCount = count.coerceAtMost(maxRows - cursorRow)
                for (r in maxRows - 1 downTo cursorRow + safeCount) {
                    for (c in 0 until maxCols) target[r][c].copyFrom(target[r - safeCount][c])
                }
                for (r in cursorRow until cursorRow + safeCount) {
                    for (c in 0 until maxCols) target[r][c].reset()
                }
            }

            // Delete Lines: [M (hapus N baris di baris kursor dan geser baris bawah ke atas)
            'M' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val safeCount = count.coerceAtMost(maxRows - cursorRow)
                for (r in cursorRow until maxRows - safeCount) {
                    for (c in 0 until maxCols) target[r][c].copyFrom(target[r + safeCount][c])
                }
                for (r in (maxRows - safeCount) until maxRows) {
                    for (c in 0 until maxCols) target[r][c].reset()
                }
            }

            // Scroll Up: [S (geser layar ke atas N baris)
            'S' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                repeat(count) { scrollUp() }
            }

            // Scroll Down: [T (geser layar ke bawah N baris)
            'T' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                repeat(count) { scrollDown() }
            }

            // Mode Private (?h / ?l)
            'h' -> {
                if (isCsiPrivate) {
                    for (p in params) {
                        when (p) {
                            1 -> isApplicationCursorKeys = true // DECCKM Mode
                            1049 -> { // Masuk ke Alternate Screen Buffer (nano, agy, htop)
                                isAltBuffer = true
                                for (r in 0 until maxRows) {
                                    for (c in 0 until maxCols) altGrid[r][c].reset()
                                }
                                cursorRow = 0
                                cursorCol = 0
                                scrollTop = 0
                                scrollBottom = maxRows - 1
                            }
                            25 -> isCursorVisible = true
                            1000, 1002 -> isMouseTrackingEnabled = true
                            1006 -> isSgrMouseMode = true
                            2004 -> isBracketedPasteMode = true
                        }
                    }
                }
            }

            'l' -> {
                if (isCsiPrivate) {
                    for (p in params) {
                        when (p) {
                            1 -> isApplicationCursorKeys = false // Reset ke normal cursor keys
                            1049 -> { // Keluar dari Alternate Screen Buffer
                                isAltBuffer = false
                                scrollTop = 0
                                scrollBottom = maxRows - 1
                            }
                            25 -> isCursorVisible = false
                            1000, 1002 -> isMouseTrackingEnabled = false
                            1006 -> isSgrMouseMode = false
                            2004 -> isBracketedPasteMode = false
                        }
                    }
                }
            }

            // Scroll Region Margins: [top;bottom r (DECSTBM untuk tmux, vim split, htop)
            'r' -> {
                val top = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                val bottom = if (params.size > 1 && params[1] > 0) params[1] - 1 else maxRows - 1
                scrollTop = top.coerceIn(0, maxRows - 1)
                scrollBottom = bottom.coerceIn(scrollTop, maxRows - 1)
                cursorRow = 0
                cursorCol = 0
            }

            's' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }

            'u' -> {
                cursorRow = savedCursorRow.coerceIn(0, maxRows - 1)
                cursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
            }

            // Device Status Report (DSR) & Cursor Position Report (CPR): [6n / [5n
            // Vital untuk program CLI Go/Rust/Python (gh, bubbletea, survey, fzf) yang mendeteksi ukuran layar
            'n' -> {
                val mode = if (params.isNotEmpty()) params[0] else 0
                if (mode == 6) {
                    val r = (cursorRow + 1).coerceIn(1, maxRows)
                    val c = (cursorCol + 1).coerceIn(1, maxCols)
                    onSendResponse?.invoke("\u001B[$r;${c}R")
                } else if (mode == 5) {
                    onSendResponse?.invoke("\u001B[0n")
                }
            }

            // Device Attributes (DA): [c
            'c' -> {
                onSendResponse?.invoke("\u001B[?1;2c")
            }
        }
    }

    private fun handleSgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetSgr()
            return
        }

        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> resetSgr()
                1 -> isBold = true
                2 -> isDim = true
                4 -> isUnderline = true
                22 -> { isBold = false; isDim = false }
                24 -> isUnderline = false
                in 30..37 -> currentFg = ansi16Color(p - 30)
                39 -> currentFg = defaultFg
                in 40..47 -> currentBg = ansi16Color(p - 40)
                49 -> currentBg = defaultBg
                in 90..97 -> currentFg = ansi16Color(p - 90 + 8)
                in 100..107 -> currentBg = ansi16Color(p - 100 + 8)
                38 -> {
                    // Extended Foreground: 38;5;N (256) atau 38;2;R;G;B (TrueColor)
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentFg = ansi256Color(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentFg = Color(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
                48 -> {
                    // Extended Background: 48;5;N (256) atau 48;2;R;G;B (TrueColor)
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentBg = ansi256Color(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentBg = Color(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun resetSgr() {
        currentFg = defaultFg
        currentBg = defaultBg
        isBold = false
        isUnderline = false
        isDim = false
    }

    private fun resetScreen() {
        for (r in 0 until maxRows) {
            for (c in 0 until maxCols) {
                grid[r][c].reset()
                altGrid[r][c].reset()
            }
        }
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = maxRows - 1
        currentHyperlinkUrl = null
        isBracketedPasteMode = false
        resetSgr()
    }

    private fun handleOscCommand(osc: String) {
        if (osc.isEmpty()) return
        val semicolonIdx = osc.indexOf(';')
        if (semicolonIdx == -1) return
        val code = osc.substring(0, semicolonIdx).toIntOrNull() ?: return
        val payload = osc.substring(semicolonIdx + 1)

        when (code) {
            0, 1, 2 -> {
                // Window Title / Tab Title (OSC 0, 1, 2)
                windowTitle = payload
                onTitleChanged?.invoke(payload)
            }
            52 -> {
                // Clipboard Protocol (OSC 52)
                val dataIdx = payload.indexOf(';')
                val base64Data = if (dataIdx != -1) payload.substring(dataIdx + 1) else payload
                if (base64Data.isNotEmpty() && base64Data != "?") {
                    try {
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val decoded = String(bytes, StandardCharsets.UTF_8)
                        onClipboardCopy?.invoke(decoded)
                    } catch (_: Exception) {}
                }
            }
            8 -> {
                // Hyperlinks Protocol (OSC 8)
                val urlIdx = payload.indexOf(';')
                if (urlIdx != -1) {
                    val url = payload.substring(urlIdx + 1)
                    currentHyperlinkUrl = if (url.isNotEmpty()) url else null
                } else {
                    currentHyperlinkUrl = null
                }
            }
        }
    }

    private fun parseCsiParams(str: String): List<Int> {
        if (str.isEmpty()) return emptyList()
        return str.split(';', ':').mapNotNull { it.toIntOrNull() }
    }

    private fun ansi16Color(index: Int): Color {
        return when (index.coerceIn(0, 15)) {
            0 -> Color(0xFF1E1E1E) // Black
            1 -> Color(0xFFE06C75) // Red
            2 -> Color(0xFF98C379) // Green
            3 -> Color(0xFFE5C07B) // Yellow
            4 -> Color(0xFF61AFEF) // Blue
            5 -> Color(0xFFC678DD) // Magenta
            6 -> Color(0xFF56B6C2) // Cyan
            7 -> Color(0xFFABB2BF) // Light Gray (Default)
            8 -> Color(0xFF5C6370) // Dark Gray
            9 -> Color(0xFFBE5046) // Bright Red
            10 -> Color(0xFFA3D487) // Bright Green
            11 -> Color(0xFFF0D18C) // Bright Yellow
            12 -> Color(0xFF7EC0EE) // Bright Blue
            13 -> Color(0xFFD896FF) // Bright Magenta
            14 -> Color(0xFF6FE2EF) // Bright Cyan
            15 -> Color(0xFFFFFFFF) // White
            else -> defaultFg
        }
    }

    private fun ansi256Color(index: Int): Color {
        return when {
            index in 0..15 -> ansi16Color(index)
            index in 16..231 -> {
                val n = index - 16
                val r = (n / 36) * 51
                val g = ((n % 36) / 6) * 51
                val b = (n % 6) * 51
                Color(r, g, b)
            }
            index in 232..255 -> {
                val gray = (index - 232) * 10 + 8
                Color(gray, gray, gray)
            }
            else -> defaultFg
        }
    }

    // Frame throttling untuk streaming teks cepat (misal respons AI / compiler)
    private var lastNotifyTime = 0L
    private var hasPendingUpdate = false
    private val notifyHandler by lazy { Handler(Looper.getMainLooper()) }
    private val notifyRunnable = Runnable {
        synchronized(lock) {
            if (hasPendingUpdate) {
                hasPendingUpdate = false
                lastNotifyTime = System.currentTimeMillis()
                version.intValue++
                try {
                    Snapshot.sendApplyNotifications()
                } catch (_: Exception) {}
            }
        }
    }

    private fun notifyUpdate() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastNotifyTime
        if (elapsed >= 16L) { // Maksimum ~60 FPS (anti-freeze UI saat AI streaming cepat)
            lastNotifyTime = now
            hasPendingUpdate = false
            notifyHandler.removeCallbacks(notifyRunnable)
            notifyHandler.post {
                version.intValue++
                try {
                    Snapshot.sendApplyNotifications()
                } catch (_: Exception) {}
            }
        } else {
            if (!hasPendingUpdate) {
                hasPendingUpdate = true
                notifyHandler.postDelayed(notifyRunnable, (16L - elapsed).coerceAtLeast(1L))
            }
        }
    }

    /**
     * Mengubah isi matriks layar saat ini menjadi Compose AnnotatedString siap render.
     * Menggunakan batching SpanStyle untuk efisiensi tinggi pada CPU Helio G35 (anti-lag saat ngetik).
     */
    fun renderToAnnotatedString(): AnnotatedString {
        synchronized(lock) {
            val target = activeGrid
            return buildAnnotatedString {
                // 1. Render riwayat scrollback jika sedang di layar normal (bukan fullscreen alt buffer)
                if (!isAltBuffer && scrollback.isNotEmpty()) {
                    val maxScrollbackRender = 300
                    val startIdx = (scrollback.size - maxScrollbackRender).coerceAtLeast(0)
                    for (idx in startIdx until scrollback.size) {
                        renderLine(this, scrollback[idx], -1, -1)
                        append('\n')
                    }
                }

                // 2. Tentukan baris aktif terakhir yang perlu dirender di grid
                var lastActiveRow = if (isAltBuffer) maxRows - 1 else cursorRow
                if (!isAltBuffer) {
                    for (r in maxRows - 1 downTo cursorRow + 1) {
                        val hasContent = target[r].any { it.char != ' ' || it.bgColor != Color.Transparent }
                        if (hasContent) {
                            lastActiveRow = r
                            break
                        }
                    }
                }

                // 3. Render setiap baris aktif dengan batching gaya ANSI
                for (r in 0..lastActiveRow) {
                    renderLine(this, target[r], r, cursorCol)
                    if (r < lastActiveRow) {
                        append('\n')
                    }
                }
            }
        }
    }

    private fun renderLine(
        builder: AnnotatedString.Builder,
        line: Array<TerminalCell>,
        rowIdx: Int,
        cursorColIdx: Int
    ) {
        var lastNonSpace = maxCols - 1
        while (lastNonSpace >= 0 && line[lastNonSpace].char == ' ' && line[lastNonSpace].bgColor == Color.Transparent && !line[lastNonSpace].isWideContinuation) {
            lastNonSpace--
        }
        val endCol = (lastNonSpace + 1).coerceAtMost(maxCols)

        var col = 0
        while (col < endCol) {
            val cell = line[col]
            if (cell.isWideContinuation) {
                col++
                continue
            }

            val isCursorHere = (isCursorVisible && rowIdx == cursorRow && col == cursorColIdx)
            val fg = if (isCursorHere) Color.Black else cell.fgColor
            val bg = if (isCursorHere) Color(0xFF61AFEF) else cell.bgColor
            val bold = cell.isBold
            val underline = cell.isUnderline

            val sb = StringBuilder()
            while (col < endCol) {
                val curCell = line[col]
                if (curCell.isWideContinuation) {
                    col++
                    continue
                }
                val curCursor = (isCursorVisible && rowIdx == cursorRow && col == cursorColIdx)
                val curFg = if (curCursor) Color.Black else curCell.fgColor
                val curBg = if (curCursor) Color(0xFF61AFEF) else curCell.bgColor
                if (curFg != fg || curBg != bg || curCell.isBold != bold || curCell.isUnderline != underline) {
                    break
                }
                sb.append(curCell.char)
                if (curCell.extraChar != null) {
                    sb.append(curCell.extraChar)
                }
                col++
            }

            val isDefaultStyle = (fg == defaultFg && bg == Color.Transparent && !bold && !underline)
            if (isDefaultStyle) {
                builder.append(sb.toString())
            } else {
                builder.pushStyle(
                    SpanStyle(
                        color = fg,
                        background = bg,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
                    )
                )
                builder.append(sb.toString())
                builder.pop()
            }
        }

        // Tampilkan kursor jika kursor berada setelah karakter terakhir baris
        if (isCursorVisible && rowIdx == cursorRow && cursorColIdx >= endCol && cursorColIdx < maxCols) {
            builder.pushStyle(SpanStyle(background = Color(0xFF61AFEF), color = Color.Black))
            builder.append(' ')
            builder.pop()
        }
    }

    /**
     * Mengambil teks lengkap dari scrollback history + layar aktif untuk clipboard copy.
     */
    fun getTranscriptText(): String {
        synchronized(lock) {
            val sb = StringBuilder()
            fun appendLineText(line: Array<TerminalCell>) {
                var last = maxCols - 1
                while (last >= 0 && line[last].char == ' ') last--
                for (c in 0..last) {
                    val cell = line[c]
                    if (cell.isWideContinuation) continue
                    sb.append(cell.char)
                    if (cell.extraChar != null) sb.append(cell.extraChar)
                }
            }
            for (line in scrollback) {
                appendLineText(line)
                sb.append('\n')
            }
            val target = activeGrid
            for (r in 0 until maxRows) {
                appendLineText(target[r])
                if (r < maxRows - 1) sb.append('\n')
            }
            return sb.toString().trimEnd()
        }
    }


    fun getScrollbackSnapshot(): List<Array<TerminalCell>> {
        synchronized(lock) {
            return scrollback.toList()
        }
    }

    fun getLineCount(): Int {
        synchronized(lock) {
            val historyCount = if (isAltBuffer) 0 else scrollback.size
            val activeCount = getLastActiveRow() + 1
            return historyCount + activeCount
        }
    }

    fun getLineAt(absoluteIndex: Int): Array<TerminalCell>? {
        synchronized(lock) {
            val historyCount = if (isAltBuffer) 0 else scrollback.size
            if (absoluteIndex < historyCount) {
                return scrollback.getOrNull(absoluteIndex)
            }
            val activeRow = absoluteIndex - historyCount
            if (activeRow in 0 until maxRows && activeRow in activeGrid.indices) {
                return activeGrid[activeRow]
            }
            return null
        }
    }

    fun getLastActiveRow(): Int {
        synchronized(lock) {
            if (isAltBuffer) return maxRows - 1
            var last = cursorRow
            for (r in maxRows - 1 downTo cursorRow + 1) {
                if (activeGrid[r].any { it.char != ' ' || it.bgColor != Color.Transparent || it.isWideContinuation }) {
                    last = r
                    break
                }
            }
            return last
        }
    }

    fun getUrlAt(absoluteRow: Int, col: Int): String? {
        synchronized(lock) {
            val line = getLineAt(absoluteRow) ?: return null
            val cell = line.getOrNull(col)
            if (cell?.hyperlinkUrl != null) return cleanUrl(cell.hyperlinkUrl!!)

            // Helper untuk mengekstrak baris teks murni
            fun extractLine(row: Int): String {
                val l = getLineAt(row) ?: return ""
                val sb = StringBuilder()
                for (c in 0 until maxCols) {
                    if (!l[c].isWideContinuation) {
                        sb.append(l[c].char)
                    }
                }
                return sb.toString()
            }

            val currentLine = extractLine(absoluteRow)
            val prevLine = if (absoluteRow > 0) extractLine(absoluteRow - 1) else ""
            val nextLine = extractLine(absoluteRow + 1)

            val urlRegex = Regex("(?:https?|file)://[^\\s<>\"]+|localhost:\\d+[^\\s<>\"]*")

            // 1. Coba cari match di baris aktif dengan toleransi hitbox sentuhan jari (±6 karakter)
            val matchesOnCurrent = urlRegex.findAll(currentLine).toList()
            for (m in matchesOnCurrent) {
                val expandedRange = (m.range.first - 6)..(m.range.last + 6)
                if (col in expandedRange) {
                    return cleanUrl(m.value)
                }
            }

            // 2. Jika hanya ada 1 URL di baris tersebut, pilih langsung (ramah sentuhan di layar HP)
            if (matchesOnCurrent.size == 1) {
                return cleanUrl(matchesOnCurrent[0].value)
            } else if (matchesOnCurrent.size > 1) {
                val closest = matchesOnCurrent.minByOrNull { m ->
                    val mid = (m.range.first + m.range.last) / 2
                    kotlin.math.abs(mid - col)
                }
                if (closest != null) return cleanUrl(closest.value)
            }

            // 3. Toleransi baris tetangga (atas/bawah ±1 baris jika jari sedikit meleset)
            val prevMatches = urlRegex.findAll(prevLine).toList()
            for (m in prevMatches) {
                if (col in (m.range.first - 3)..(m.range.last + 3)) return cleanUrl(m.value)
            }
            val nextMatches = urlRegex.findAll(nextLine).toList()
            for (m in nextMatches) {
                if (col in (m.range.first - 3)..(m.range.last + 3)) return cleanUrl(m.value)
            }

            // 4. Coba cari di teks gabungan (URL panjang yang terpotong/wrapped antar baris)
            val candidateStrings = listOf(
                currentLine.trimEnd() + nextLine.trimStart(),
                prevLine.trimEnd() + currentLine.trimStart()
            )
            for (cs in candidateStrings) {
                val m = urlRegex.find(cs)
                if (m != null) {
                    return cleanUrl(m.value)
                }
            }

            return null
        }
    }

    /**
     * Mengekstrak seluruh URL yang ada di layar aktif dan scrollback.
     */
    fun extractAllUrls(): List<String> {
        synchronized(lock) {
            val urls = linkedSetOf<String>()
            val urlRegex = Regex("(?:https?|file)://[^\\s<>\"]+|localhost:\\d+[^\\s<>\"]*")
            val totalLines = getLineCount()
            for (r in 0 until totalLines) {
                val line = getLineAt(r) ?: continue
                for (c in 0 until maxCols) {
                    val h = line[c].hyperlinkUrl
                    if (h != null) urls.add(cleanUrl(h))
                }
                val sb = StringBuilder()
                for (c in 0 until maxCols) {
                    if (!line[c].isWideContinuation) sb.append(line[c].char)
                }
                urlRegex.findAll(sb.toString()).forEach { m ->
                    urls.add(cleanUrl(m.value))
                }
            }
            return urls.toList()
        }
    }

    private fun cleanUrl(raw: String): String {
        var res = raw.trim()
        res = res.trimStart('(', '[', '{', '<', '"', '\'', '`')
        res = res.trimEnd(')', ']', '}', '>', '.', ',', ';', '!', '"', '\'', '`', ':')
        return res
    }
}

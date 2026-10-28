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
    var isInverse: Boolean = false,
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
        isInverse = false
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
        isInverse = other.isInverse
        isWide = other.isWide
        isWideContinuation = other.isWideContinuation
        hyperlinkUrl = other.hyperlinkUrl
    }
}

/**
 * Zero-Allocation Ring Buffer untuk riwayat scrollback terminal.
 * Mendaur ulang baris terlama saat kapasitas penuh tanpa alokasi objek baru pada heap GC Android.
 */
class TerminalScrollbackBuffer(val capacity: Int = 2000) : Iterable<Array<TerminalCell>> {
    private val buffer = arrayOfNulls<Array<TerminalCell>>(capacity)
    private var head = 0
    var size = 0
        private set

    fun push(source: Array<TerminalCell>, cols: Int) {
        if (size < capacity) {
            val targetLine = Array(cols) { col ->
                TerminalCell().apply { if (col < source.size) copyFrom(source[col]) }
            }
            val insertIdx = (head + size) % capacity
            buffer[insertIdx] = targetLine
            size++
        } else {
            // RECYCLE: Ambil baris terlama langsung dari head dan salin isi baru ke dalamnya (Nol alokasi objek!)
            val targetLine = buffer[head]
            if (targetLine != null && targetLine.size == cols) {
                for (c in 0 until cols) {
                    if (c < source.size) {
                        targetLine[c].copyFrom(source[c])
                    } else {
                        targetLine[c].reset()
                    }
                }
            } else {
                buffer[head] = Array(cols) { col ->
                    TerminalCell().apply { if (col < source.size) copyFrom(source[col]) }
                }
            }
            head = (head + 1) % capacity
        }
    }

    fun takeLast(count: Int): List<Array<TerminalCell>> {
        val safeCount = count.coerceIn(0, size)
        val result = ArrayList<Array<TerminalCell>>(safeCount)
        val startIdx = size - safeCount
        for (i in 0 until safeCount) {
            get(startIdx + i)?.let { result.add(it) }
            val bufIdx = (head + startIdx + i) % capacity
            buffer[bufIdx] = null
        }
        size -= safeCount
        return result
    }

    operator fun get(index: Int): Array<TerminalCell>? {
        if (index < 0 || index >= size) return null
        return buffer[(head + index) % capacity]
    }

    fun getOrNull(index: Int): Array<TerminalCell>? = get(index)

    fun isNotEmpty(): Boolean = size > 0
    fun isEmpty(): Boolean = size == 0

    fun toList(): List<Array<TerminalCell>> {
        val list = ArrayList<Array<TerminalCell>>(size)
        for (i in 0 until size) {
            get(i)?.let { list.add(it) }
        }
        return list
    }

    override fun iterator(): Iterator<Array<TerminalCell>> = object : Iterator<Array<TerminalCell>> {
        private var curr = 0
        override fun hasNext(): Boolean = curr < size
        override fun next(): Array<TerminalCell> {
            val line = get(curr) ?: throw NoSuchElementException()
            curr++
            return line
        }
    }

    fun clear() {
        head = 0
        size = 0
        buffer.fill(null)
    }

    inline fun forEach(action: (Array<TerminalCell>) -> Unit) {
        for (i in 0 until size) {
            val line = get(i)
            if (line != null) action(line)
        }
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
    private val scrollback = TerminalScrollbackBuffer(2000)

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
    var isAutoWrap: Boolean = true
        private set
    var isInsertMode: Boolean = false
        private set
    @Volatile
    var isSynchronizedUpdate: Boolean = false
        private set
    private var wrapPending: Boolean = false
    private var utf8Remainder = ByteArray(0)

    // Modern Protocols (OSC 0/1/2, OSC 52, OSC 8, OSC 777 DevBox Bridge)
    var windowTitle: String = "coder@devbox:~"
        private set
    var currentHyperlinkUrl: String? = null
        private set
    var onTitleChanged: ((String) -> Unit)? = null
    var onClipboardCopy: ((String) -> Unit)? = null
    var onClipboardRequest: (() -> String?)? = null
    var onBell: (() -> Unit)? = null
    var onSendResponse: ((String) -> Unit)? = null
    var onCwdChanged: ((String) -> Unit)? = null
    var onAgentChanged: ((String?) -> Unit)? = null
    var onOpenUrl: ((String) -> Unit)? = null
    var onNotify: ((String, String) -> Unit)? = null

    // Terminal Theme & Dynamic Color System
    var currentTheme: TerminalTheme = TerminalTheme.DevBoxEmerald
        private set
    private var defaultFg = currentTheme.foreground
    private var defaultBg = currentTheme.background
    private var currentFg = defaultFg
    private var currentBg = defaultBg
    private var isBold = false
    private var isUnderline = false
    private var isDim = false
    private var isInverse = false

    fun setTheme(theme: TerminalTheme) {
        synchronized(lock) {
            currentTheme = theme
            defaultFg = theme.foreground
            defaultBg = theme.background
            currentFg = theme.foreground
            currentBg = theme.background
            notifyUpdate(forceImmediate = true)
        }
    }

    // State Machine untuk Parser
    private enum class ParserState {
        NORMAL,
        ESC,
        CSI,
        OSC,
        DCS_OR_APC,
        CHARSET
    }

    private var parserState = ParserState.NORMAL
    private val csiParams = StringBuilder()
    private var isCsiPrivate = false
    private var csiPrefix: Char = '\u0000'
    private var csiIntermediate: Char = '\u0000'
    private val oscBuffer = StringBuilder()

    // State trigger untuk recompose di Jetpack Compose
    val version = mutableIntStateOf(0)
    val lineCountState = mutableIntStateOf(1)

    private val activeGrid: Array<Array<TerminalCell>>
        get() = if (isAltBuffer) altGrid else grid

    fun resetForNewProcess() {
        synchronized(lock) {
            isAltBuffer = false
            parserState = ParserState.NORMAL
            csiParams.setLength(0)
            oscBuffer.setLength(0)
            utf8Remainder = ByteArray(0)
            isCursorVisible = true
            isBracketedPasteMode = false
            isMouseTrackingEnabled = false
            isSgrMouseMode = false
            isApplicationCursorKeys = false
            isAutoWrap = true
            isInsertMode = false
            isSynchronizedUpdate = false
            wrapPending = false
            scrollTop = 0
            scrollBottom = (maxRows - 1).coerceAtLeast(0)
            cursorRow = cursorRow.coerceIn(0, (maxRows - 1).coerceAtLeast(0))
            cursorCol = cursorCol.coerceIn(0, (maxCols - 1).coerceAtLeast(0))
            resetSgr()
            notifyUpdate()
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        synchronized(lock) {
            val c = newCols.coerceIn(20, 300)
            val r = newRows.coerceIn(18, 200)
            if (c == maxCols && r == maxRows &&
                grid.size == r && (grid.isEmpty() || grid[0].size == c) &&
                altGrid.size == r && (altGrid.isEmpty() || altGrid[0].size == c)
            ) return

            val oldGridRows = grid.size
            val oldGridCols = if (grid.isNotEmpty()) grid[0].size else maxCols
            val oldAltRows = altGrid.size
            val oldAltCols = if (altGrid.isNotEmpty()) altGrid[0].size else maxCols

            maxCols = c
            maxRows = r

            // 1. Selalu resize main grid (agar saat keluar dari altScreen ukuran grid selalu sinkron)
            val newGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
            val refCursorRow = if (!isAltBuffer) cursorRow.coerceIn(0, (oldGridRows - 1).coerceAtLeast(0)) else savedCursorRow.coerceIn(0, (oldGridRows - 1).coerceAtLeast(0))
            val newMainCursorRow: Int
            if (r < oldGridRows) {
                // Layar mengecil (misal keyboard virtual terbuka):
                // Geser baris di atas kursor ke scrollback agar kursor dan prompt tetap berada di layar aktif
                val shift = (refCursorRow - (r - 1)).coerceIn(0, oldGridRows - r)
                for (row in 0 until shift) {
                    scrollback.push(grid[row], oldGridCols)
                }
                for (row in 0 until r) {
                    val srcRow = row + shift
                    if (srcRow < oldGridRows) {
                        for (col in 0 until maxCols.coerceAtMost(oldGridCols)) {
                            newGrid[row][col].copyFrom(grid[srcRow][col])
                        }
                    }
                }
                newMainCursorRow = (refCursorRow - shift).coerceIn(0, maxRows - 1)
            } else {
                // Layar membesar (misal keyboard virtual ditutup):
                // Tarik kembali baris dari scrollback untuk mengisi ruang baris atas yang bertambah
                val unwind = if (!isAltBuffer) (r - oldGridRows).coerceAtMost(scrollback.size) else 0
                val pulledLines = scrollback.takeLast(unwind)
                for (row in 0 until pulledLines.size) {
                    val pulledLine = pulledLines[row]
                    for (col in 0 until maxCols.coerceAtMost(pulledLine.size)) {
                        newGrid[row][col].copyFrom(pulledLine[col])
                    }
                }
                for (row in 0 until oldGridRows.coerceAtMost(maxRows - unwind)) {
                    for (col in 0 until maxCols.coerceAtMost(oldGridCols)) {
                        newGrid[row + unwind][col].copyFrom(grid[row][col])
                    }
                }
                newMainCursorRow = (refCursorRow + unwind).coerceIn(0, maxRows - 1)
            }
            grid = newGrid

            // 2. Selalu resize alternate grid (agar saat masuk \x1b[?1049h di agy/vim/htop tidak pernah ArrayIndexOutOfBoundsException!)
            val newAltGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
            for (row in 0 until maxRows.coerceAtMost(oldAltRows)) {
                for (col in 0 until maxCols.coerceAtMost(oldAltCols)) {
                    newAltGrid[row][col].copyFrom(altGrid[row][col])
                }
            }
            altGrid = newAltGrid

            if (!isAltBuffer) {
                cursorRow = newMainCursorRow
            } else {
                savedCursorRow = newMainCursorRow
                cursorRow = cursorRow.coerceIn(0, maxRows - 1)
            }

            cursorCol = cursorCol.coerceIn(0, maxCols - 1)
            savedCursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
            wrapPending = false
            scrollTop = 0
            scrollBottom = maxRows - 1
            notifyUpdate()
        }
    }

    private fun decodeUtf8Chunk(bytes: ByteArray, offset: Int, count: Int): String {
        if (count <= 0) return ""
        val combined = if (utf8Remainder.isEmpty()) {
            if (offset == 0 && count == bytes.size) bytes else bytes.copyOfRange(offset, offset + count)
        } else {
            val merged = ByteArray(utf8Remainder.size + count)
            System.arraycopy(utf8Remainder, 0, merged, 0, utf8Remainder.size)
            System.arraycopy(bytes, offset, merged, utf8Remainder.size, count)
            utf8Remainder = ByteArray(0)
            merged
        }

        val len = combined.size
        var splitIdx = len
        val minIdx = (len - 3).coerceAtLeast(0)
        for (idx in len - 1 downTo minIdx) {
            val b = combined[idx].toInt() and 0xFF
            if ((b and 0x80) == 0) {
                // ASCII byte — semua byte sampai di sini utuh
                break
            }
            if ((b and 0xC0) == 0xC0) {
                // UTF-8 leading byte
                val expectedLen = when {
                    (b and 0xE0) == 0xC0 -> 2
                    (b and 0xF0) == 0xE0 -> 3
                    (b and 0xF8) == 0xF0 -> 4
                    else -> 1
                }
                val available = len - idx
                if (available < expectedLen) {
                    splitIdx = idx
                }
                break
            }
        }

        if (splitIdx < len) {
            utf8Remainder = combined.copyOfRange(splitIdx, len)
        }
        if (splitIdx <= 0) return ""
        return String(combined, 0, splitIdx, StandardCharsets.UTF_8)
    }

    fun write(bytes: ByteArray, offset: Int, count: Int) {
        synchronized(lock) {
            val text = decodeUtf8Chunk(bytes, offset, count)
            if (text.isEmpty()) return
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                when (parserState) {
                    ParserState.NORMAL -> {
                        when {
                            ch == '\u001B' -> parserState = ParserState.ESC
                            ch == '\r' -> {
                                wrapPending = false
                                cursorCol = 0
                            }
                            ch == '\n' || ch == '\u000B' || ch == '\u000C' -> {
                                // VT100 / xterm standar: LF (\n) hanya menggeser baris ke bawah TANPA mereset cursorCol ke 0!
                                // Pada shell cooked-mode (bash/ls), kernel PTY (onlcr) otomatis mengirim \r\n.
                                // Pada TUI raw-mode (agy / Bubbletea / vim), bare \n dipakai sebagai vertikal kursor murni
                                // diikuti gerakan horizontal relatif (\x1b[<N>D / \x1b[<N>C).
                                wrapPending = false
                                newline()
                            }
                            ch == '\b' || ch == '\u007F' -> {
                                wrapPending = false
                                if (cursorCol > 0) cursorCol--
                            }
                            ch == '\t' -> {
                                // VT100 CHT: majukan posisi kursor ke kelipatan 8 berikutnya tanpa menimpa isi sel yang sudah ada
                                wrapPending = false
                                val nextTabStop = ((cursorCol / 8) + 1) * 8
                                cursorCol = nextTabStop.coerceAtMost(maxCols - 1)
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
                                csiPrefix = '\u0000'
                                csiIntermediate = '\u0000'
                            }
                            ']' -> {
                                parserState = ParserState.OSC
                                oscBuffer.setLength(0)
                            }
                            'P', '_', '^', 'X' -> {
                                // DCS, APC, PM, SOS string sequence (diakhiri ST: ESC \ atau BEL)
                                parserState = ParserState.DCS_OR_APC
                                oscBuffer.setLength(0)
                            }
                            '(', ')', '*', '+' -> parserState = ParserState.CHARSET
                            '7' -> {
                                savedCursorRow = cursorRow
                                savedCursorCol = cursorCol
                                parserState = ParserState.NORMAL
                            }
                            '8' -> {
                                wrapPending = false
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
                                wrapPending = false
                                if (cursorRow > scrollTop) {
                                    cursorRow--
                                } else {
                                    scrollDown()
                                }
                                parserState = ParserState.NORMAL
                            }
                            'D' -> {
                                // Index (IND): pindah kursor ke bawah 1 baris, scroll up jika di akhir
                                wrapPending = false
                                newline()
                                parserState = ParserState.NORMAL
                            }
                            'E' -> {
                                // Next Line (NEL): kolom 0 + baris baru
                                wrapPending = false
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

                    ParserState.DCS_OR_APC -> {
                        if (ch == '\u0007' || (ch == '\\' && oscBuffer.endsWith("\u001B"))) {
                            parserState = ParserState.NORMAL
                        } else {
                            oscBuffer.append(ch)
                        }
                    }

                    ParserState.OSC -> {
                        if (ch == '\u0007' || (ch == '\\' && oscBuffer.endsWith("\u001B"))) {
                            val rawOsc = if (ch == '\\' && oscBuffer.endsWith("\u001B")) {
                                oscBuffer.dropLast(1).toString()
                            } else {
                                oscBuffer.toString()
                            }
                            parserState = ParserState.NORMAL
                            try {
                                handleOscCommand(rawOsc)
                            } catch (_: Exception) {}
                        } else {
                            oscBuffer.append(ch)
                        }
                    }

                    ParserState.CSI -> {
                        when {
                            ch == '\u001B' -> {
                                parserState = ParserState.ESC
                            }
                            ch in charArrayOf('?', '>', '<', '=') && csiParams.isEmpty() && csiPrefix == '\u0000' -> {
                                csiPrefix = ch
                                if (ch == '?') isCsiPrivate = true
                            }
                            ch in '0'..'9' || ch == ';' || ch == ':' -> {
                                csiParams.append(ch)
                            }
                            ch.code in 0x20..0x2F -> {
                                // Intermediate byte (misal ' ' pada \x1b[0 q atau '$' pada \x1b[?2026$p)
                                csiIntermediate = ch
                            }
                            ch.code in 0x40..0x7E -> {
                                // Karakter akhir perintah CSI (0x40 - 0x7E)
                                parserState = ParserState.NORMAL
                                try {
                                    handleCsiCommand(ch)
                                } catch (_: Exception) {}
                            }
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
        val target = activeGrid
        if (target.isEmpty() || target[0].isEmpty()) return
        val rowsLimit = target.size
        val colsLimit = target[0].size

        cursorRow = cursorRow.coerceIn(0, rowsLimit - 1)
        val charWidth = if (isWide) 2 else 1

        // 1. VT100 DECAWM Delayed Auto-Wrap:
        // Jika kursor berada di status wrapPending dari karakter sebelumnya,
        // baru wrap ke kolom 0 baris baru saat karakter cetak berikutnya tiba.
        if (wrapPending) {
            wrapPending = false
            if (isAutoWrap) {
                cursorCol = 0
                newline()
                cursorRow = cursorRow.coerceIn(0, rowsLimit - 1)
            }
        }

        // Jika karakter lebar (2 sel di layar) tidak muat di kolom paling kanan, wrap ke baris baru
        if (isWide && cursorCol >= colsLimit - 1 && isAutoWrap) {
            target[cursorRow][cursorCol.coerceIn(0, colsLimit - 1)].reset()
            cursorCol = 0
            newline()
            cursorRow = cursorRow.coerceIn(0, rowsLimit - 1)
        }

        val safeRow = cursorRow.coerceIn(0, rowsLimit - 1)
        val safeCol = cursorCol.coerceIn(0, colsLimit - 1)

        // ECMA-48 IRM (Insert Mode): jika aktif, geser isi sel di sebelah kanan ke kanan
        if (isInsertMode && safeCol < colsLimit - charWidth) {
            for (c in colsLimit - 1 downTo safeCol + charWidth) {
                target[safeRow][c].copyFrom(target[safeRow][c - charWidth])
            }
            target[safeRow][safeCol].reset()
        }

        val cell = target[safeRow][safeCol]
        cell.char = ch
        cell.extraChar = extra
        cell.fgColor = currentFg
        cell.bgColor = currentBg
        cell.isBold = isBold
        cell.isUnderline = isUnderline
        cell.isDim = isDim
        cell.isInverse = isInverse
        cell.isWide = isWide
        cell.isWideContinuation = false
        cell.hyperlinkUrl = currentHyperlinkUrl

        // Jika karakter lebar (2 sel di layar), tandai sel berikutnya sebagai continuation
        if (isWide && safeCol + 1 < colsLimit) {
            val contCell = target[safeRow][safeCol + 1]
            contCell.reset()
            contCell.isWideContinuation = true
        }

        // Majukan kursor; jika mencapai batas kanan margin, pasang flag wrapPending (delayed wrap)
        if (safeCol + charWidth >= colsLimit) {
            cursorCol = colsLimit - 1
            if (isAutoWrap) {
                wrapPending = true
            }
        } else {
            cursorCol = safeCol + charWidth
            wrapPending = false
        }
    }

    private fun newline() {
        val bottom = scrollBottom.coerceIn(0, maxRows - 1)
        if (cursorRow == bottom) {
            scrollUp()
        } else if (cursorRow < maxRows - 1) {
            cursorRow++
        }
    }

    private fun scrollUp() {
        val target = activeGrid
        if (target.isEmpty()) return
        val rowsLimit = target.size
        val colsLimit = target[0].size
        val top = scrollTop.coerceIn(0, rowsLimit - 1)
        val bottom = scrollBottom.coerceIn(top, rowsLimit - 1)
        if (!isAltBuffer && top == 0 && bottom == rowsLimit - 1) {
            scrollback.push(grid[0], colsLimit)
        }
        for (r in top until bottom) {
            for (c in 0 until colsLimit) {
                target[r][c].copyFrom(target[r + 1][c])
            }
        }
        for (c in 0 until colsLimit) {
            target[bottom][c].reset()
        }
    }

    private fun scrollDown() {
        val target = activeGrid
        if (target.isEmpty()) return
        val rowsLimit = target.size
        val colsLimit = target[0].size
        val top = scrollTop.coerceIn(0, rowsLimit - 1)
        val bottom = scrollBottom.coerceIn(top, rowsLimit - 1)
        for (r in bottom downTo top + 1) {
            for (c in 0 until colsLimit) {
                target[r][c].copyFrom(target[r - 1][c])
            }
        }
        for (c in 0 until colsLimit) {
            target[top][c].reset()
        }
    }



    private fun handleCsiCommand(command: Char) {
        // Tangani Synchronized Output mode query / status (\x1b[?2026$p)
        if (csiIntermediate == '$' && command == 'p' && isCsiPrivate && csiParams.toString() == "2026") {
            // Mode 2026 didukung: kirim DECRPM respon (\x1b[?2026;1$y jika aktif, \x1b[?2026;2$y jika nonaktif)
            val state = if (isSynchronizedUpdate) 1 else 2
            onSendResponse?.invoke("\u001B[?2026;$state\$y")
            return
        }

        // Abaikan sequence CSI dengan intermediate byte lain (misal \x1b[0 q)
        // atau prefix ekstensi modern ('>', '<', '=' seperti \x1b[>4;2m, \x1b[>1u, \x1b[<1u)
        // agar tidak disalahartikan sebagai perintah standar ('m', 'u', 'p', 'q', dll.)
        if (csiIntermediate != '\u0000' || csiPrefix in charArrayOf('>', '<', '=')) {
            if (csiPrefix == '>' && csiIntermediate == '\u0000' && command == 'c') {
                onSendResponse?.invoke("\u001B[>0;10;1c")
            }
            return
        }

        val params = parseCsiParams(csiParams.toString())

        // Batalkan pending wrap VT100 saat ada instruksi kursor/edit
        if (command in charArrayOf('H', 'f', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'd', 'J', 'K', 'X', '@', 'P', 'L', 'M')) {
            wrapPending = false
        }

        when (command) {
            // SGR (Select Graphic Rendition - Warna & Gaya)
            'm' -> if (!isCsiPrivate) handleSgr(params)

            // Cursor Position: [H atau [f (1-indexed)
            'H', 'f' -> {
                val row = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                val col = if (params.size > 1 && params[1] > 0) params[1] - 1 else 0
                cursorRow = row.coerceIn(0, maxRows - 1)
                cursorCol = col.coerceIn(0, maxCols - 1)
            }

            // Cursor Up: [A (Clamping zero-mutation agar TUI prompt tidak terduplikasi)
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

            // Cursor Previous Line: [F (pindah baris ke atas dan kolom ke-0 - zero mutation)
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
                val safeCol = cursorCol.coerceIn(0, maxCols - 1)
                when (mode) {
                    0 -> { // Dari kursor sampai akhir layar
                        for (c in safeCol until maxCols) target[cursorRow][c].reset()
                        for (r in cursorRow + 1 until maxRows) {
                            for (c in 0 until maxCols) target[r][c].reset()
                        }
                    }
                    1 -> { // Dari awal layar sampai kursor
                        for (r in 0 until cursorRow) {
                            for (c in 0 until maxCols) target[r][c].reset()
                        }
                        for (c in 0..safeCol) target[cursorRow][c].reset()
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
                val safeCol = cursorCol.coerceIn(0, maxCols - 1)
                when (mode) {
                    0 -> for (c in safeCol until maxCols) target[cursorRow][c].reset()
                    1 -> for (c in 0..safeCol) target[cursorRow][c].reset()
                    2 -> for (c in 0 until maxCols) target[cursorRow][c].reset()
                }
            }

            // Erase Character: [X (hapus N karakter dari kursor ke kanan jadi spasi tanpa geser kursor)
            'X' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val safeCol = cursorCol.coerceIn(0, maxCols - 1)
                val limit = (safeCol + count).coerceAtMost(maxCols)
                for (c in safeCol until limit) {
                    target[cursorRow][c].reset()
                }
            }

            // Delete Character: [P (hapus N karakter di kursor dan geser sisa baris ke kiri)
            'P' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val target = activeGrid
                val safeCol = cursorCol.coerceIn(0, maxCols - 1)
                val safeCount = count.coerceAtMost(maxCols - safeCol)
                for (c in safeCol until maxCols - safeCount) {
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
                val safeCol = cursorCol.coerceIn(0, maxCols - 1)
                val safeCount = count.coerceAtMost(maxCols - safeCol)
                for (c in maxCols - 1 downTo safeCol + safeCount) {
                    target[cursorRow][c].copyFrom(target[cursorRow][c - safeCount])
                }
                for (c in safeCol until safeCol + safeCount) {
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
                            7 -> isAutoWrap = true // DECAWM Auto-Wrap Mode
                            47, 1047, 1049 -> { // Masuk ke Alternate Screen Buffer (nano, agy, htop)
                                if (p == 1049) {
                                    savedCursorRow = cursorRow
                                    savedCursorCol = cursorCol
                                }
                                isAltBuffer = true
                                if (altGrid.size != maxRows || (altGrid.isNotEmpty() && altGrid[0].size != maxCols)) {
                                    altGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
                                } else {
                                    for (r in 0 until altGrid.size) {
                                        val rowArr = altGrid[r]
                                        for (c in 0 until rowArr.size) rowArr[c].reset()
                                    }
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
                            2026 -> {
                                // DECSET 2026: Begin Synchronized Output (TUI atomic render)
                                isSynchronizedUpdate = true
                            }
                        }
                    }
                } else {
                    for (p in params) {
                        when (p) {
                            4 -> isInsertMode = true // ECMA-48 IRM (Insert Mode)
                        }
                    }
                }
            }

            'l' -> {
                if (isCsiPrivate) {
                    for (p in params) {
                        when (p) {
                            1 -> isApplicationCursorKeys = false // Reset ke normal cursor keys
                            7 -> isAutoWrap = false // Nonaktifkan Auto-Wrap (penting saat TUI menggambar border tabel)
                            47, 1047, 1049 -> { // Keluar dari Alternate Screen Buffer
                                isAltBuffer = false
                                if (grid.size != maxRows || (grid.isNotEmpty() && grid[0].size != maxCols)) {
                                    val newGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
                                    for (r in 0 until maxRows.coerceAtMost(grid.size)) {
                                        for (c in 0 until maxCols.coerceAtMost(grid[r].size)) {
                                            newGrid[r][c].copyFrom(grid[r][c])
                                        }
                                    }
                                    grid = newGrid
                                }
                                scrollTop = 0
                                scrollBottom = maxRows - 1
                                if (p == 1049) {
                                    cursorRow = savedCursorRow.coerceIn(0, maxRows - 1)
                                    cursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
                                }
                            }
                            25 -> isCursorVisible = false
                            1000, 1002 -> isMouseTrackingEnabled = false
                            1006 -> isSgrMouseMode = false
                            2004 -> isBracketedPasteMode = false
                            2026 -> {
                                // DECRST 2026: End Synchronized Output (Flush atomic frame seketika ke UI)
                                isSynchronizedUpdate = false
                                notifyUpdate(forceImmediate = true)
                            }
                        }
                    }
                } else {
                    for (p in params) {
                        when (p) {
                            4 -> isInsertMode = false // ECMA-48 IRM (Replace Mode)
                        }
                    }
                }
            }

            // Scroll Region Margins: [top;bottom r (DECSTBM untuk tmux, vim split, htop)
            'r' -> {
                if (!isCsiPrivate) {
                    val top = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                    val bottom = if (params.size > 1 && params[1] > 0) params[1] - 1 else maxRows - 1
                    scrollTop = top.coerceIn(0, maxRows - 1)
                    scrollBottom = bottom.coerceIn(scrollTop, maxRows - 1)
                    cursorRow = 0
                    cursorCol = 0
                }
            }

            's' -> {
                if (!isCsiPrivate) {
                    savedCursorRow = cursorRow
                    savedCursorCol = cursorCol
                }
            }

            'u' -> {
                // Jangan jalankan SCORC jika ada prefix '?' (misal \x1b[?u dari Kitty keyboard query di agy)
                if (!isCsiPrivate) {
                    cursorRow = savedCursorRow.coerceIn(0, maxRows - 1)
                    cursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
                }
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
                7 -> isInverse = true
                22 -> { isBold = false; isDim = false }
                24 -> isUnderline = false
                27 -> isInverse = false
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
        isInverse = false
    }

    private fun resetScreen() {
        for (r in 0 until grid.size) {
            for (c in 0 until grid[r].size) {
                grid[r][c].reset()
            }
        }
        for (r in 0 until altGrid.size) {
            for (c in 0 until altGrid[r].size) {
                altGrid[r][c].reset()
            }
        }
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = maxRows - 1
        currentHyperlinkUrl = null
        isBracketedPasteMode = false
        isAutoWrap = true
        isInsertMode = false
        wrapPending = false
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
            10 -> {
                // Query Foreground Color (OSC 10;?) -> TUI async theme detection
                if (payload == "?") {
                    val r = (currentTheme.foreground.red * 255).toInt().coerceIn(0, 255)
                    val g = (currentTheme.foreground.green * 255).toInt().coerceIn(0, 255)
                    val b = (currentTheme.foreground.blue * 255).toInt().coerceIn(0, 255)
                    val rHex = String.format("%02x%02x", r, r)
                    val gHex = String.format("%02x%02x", g, g)
                    val bHex = String.format("%02x%02x", b, b)
                    onSendResponse?.invoke("\u001B]10;rgb:$rHex/$gHex/$bHex\u0007")
                }
            }
            11 -> {
                // Query Background Color (OSC 11;?) -> TUI async theme detection
                if (payload == "?") {
                    val bg = if (currentTheme.background == Color.Transparent) Color(0xFF0D1117) else currentTheme.background
                    val r = (bg.red * 255).toInt().coerceIn(0, 255)
                    val g = (bg.green * 255).toInt().coerceIn(0, 255)
                    val b = (bg.blue * 255).toInt().coerceIn(0, 255)
                    val rHex = String.format("%02x%02x", r, r)
                    val gHex = String.format("%02x%02x", g, g)
                    val bHex = String.format("%02x%02x", b, b)
                    onSendResponse?.invoke("\u001B]11;rgb:$rHex/$gHex/$bHex\u0007")
                }
            }
            52 -> {
                // Clipboard Protocol (OSC 52)
                val dataIdx = payload.indexOf(';')
                val base64Data = if (dataIdx != -1) payload.substring(dataIdx + 1) else payload
                if (base64Data == "?" || payload.endsWith(";?") || payload == "?") {
                    // Query Read Clipboard (Micro editor, Neovim, tmux Ctrl+V / Paste)
                    try {
                        val clipText = onClipboardRequest?.invoke() ?: ""
                        val b64 = android.util.Base64.encodeToString(
                            clipText.toByteArray(StandardCharsets.UTF_8),
                            android.util.Base64.NO_WRAP
                        )
                        onSendResponse?.invoke("\u001B]52;c;$b64\u0007")
                    } catch (_: Exception) {}
                } else if (base64Data.isNotEmpty()) {
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
            777 -> {
                // DevBox Bridge OSC Protocol (Zero-SELinux, Zero-Polling, Zero-su)
                // Format: 777;cwd;/path | 777;agent;agy | 777;open;https://... | 777;notify;Title;Message
                val subIdx = payload.indexOf(';')
                if (subIdx != -1) {
                    val subCmd = payload.substring(0, subIdx).trim()
                    val subArg = payload.substring(subIdx + 1)
                    when (subCmd) {
                        "cwd" -> {
                            val cleanPath = subArg.trim()
                            if (cleanPath.isNotEmpty()) {
                                onCwdChanged?.invoke(cleanPath)
                            }
                        }
                        "agent" -> {
                            val agent = subArg.trim().takeIf { it.isNotEmpty() }
                            onAgentChanged?.invoke(agent)
                        }
                        "open" -> {
                            val url = subArg.trim()
                            if (url.isNotEmpty()) {
                                onOpenUrl?.invoke(url)
                            }
                        }
                        "notify" -> {
                            val msgIdx = subArg.indexOf(';')
                            val title = if (msgIdx != -1) subArg.substring(0, msgIdx).trim() else "DevBox Agent"
                            val msg = if (msgIdx != -1) subArg.substring(msgIdx + 1).trim() else subArg.trim()
                            if (msg.isNotEmpty()) {
                                onNotify?.invoke(title.ifEmpty { "DevBox Agent" }, msg)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseCsiParams(str: String): List<Int> {
        if (str.isEmpty()) return emptyList()
        return str.split(';', ':').mapNotNull { it.toIntOrNull() }
    }

    private fun ansi16Color(index: Int): Color {
        val safeIndex = index.coerceIn(0, 15)
        return currentTheme.ansiColors.getOrElse(safeIndex) { defaultFg }
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
                val curLines = if (isAltBuffer) maxRows else (scrollback.size + getLastActiveRow() + 1).coerceAtLeast(1)
                if (lineCountState.intValue != curLines) {
                    lineCountState.intValue = curLines
                }
                try {
                    Snapshot.sendApplyNotifications()
                } catch (_: Exception) {}
            }
        }
    }

    private fun notifyUpdate(forceImmediate: Boolean = false) {
        if (isSynchronizedUpdate && !forceImmediate) {
            // Sedang di dalam batch Synchronized Output (DECSET 2026), jangan picu recompose parsial
            hasPendingUpdate = true
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastNotifyTime
        if (forceImmediate || elapsed >= 16L) { // Maksimum ~60 FPS (anti-freeze UI saat AI streaming cepat)
            lastNotifyTime = now
            hasPendingUpdate = false
            notifyHandler.removeCallbacks(notifyRunnable)
            notifyHandler.post {
                version.intValue++
                val curLines = if (isAltBuffer) maxRows else (scrollback.size + getLastActiveRow() + 1).coerceAtLeast(1)
                if (lineCountState.intValue != curLines) {
                    lineCountState.intValue = curLines
                }
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
        while (lastNonSpace >= 0 && line[lastNonSpace].char == ' ' && line[lastNonSpace].bgColor == Color.Transparent && !line[lastNonSpace].isInverse && !line[lastNonSpace].isWideContinuation) {
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
            val rawFg = if (cell.isDim) cell.fgColor.copy(alpha = 0.6f) else cell.fgColor
            val invBg = if (cell.isInverse) {
                if (cell.fgColor == Color.Transparent || cell.fgColor == Color(0xFFC9D1D9)) Color(0xFFC9D1D9)
                else cell.fgColor
            } else {
                cell.bgColor
            }
            val invFg = if (cell.isInverse) {
                if (cell.bgColor == Color.Transparent) Color(0xFF0D1117)
                else cell.bgColor
            } else {
                rawFg
            }
            val fg = if (isCursorHere) Color(0xFF062100) else invFg
            val bg = if (isCursorHere) Color(0xFF7DFF4C) else invBg
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
                val curRawFg = if (curCell.isDim) curCell.fgColor.copy(alpha = 0.6f) else curCell.fgColor
                val curInvBg = if (curCell.isInverse) {
                    if (curCell.fgColor == Color.Transparent || curCell.fgColor == Color(0xFFC9D1D9)) Color(0xFFC9D1D9)
                    else curCell.fgColor
                } else {
                    curCell.bgColor
                }
                val curInvFg = if (curCell.isInverse) {
                    if (curCell.bgColor == Color.Transparent) Color(0xFF0D1117)
                    else curCell.bgColor
                } else {
                    curRawFg
                }
                val curFg = if (curCursor) Color(0xFF062100) else curInvFg
                val curBg = if (curCursor) Color(0xFF7DFF4C) else curInvBg
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
            builder.pushStyle(SpanStyle(background = Color(0xFF7DFF4C), color = Color(0xFF062100)))
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

    /**
     * Membaca jumlah baris scrollback tanpa alokasi memori (zero object allocation).
     * Mencegah garbage collection churn pada 60-120 FPS rendering.
     */
    fun getScrollbackSize(): Int {
        synchronized(lock) {
            return if (isAltBuffer) 0 else scrollback.size
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
                if (activeGrid[r].any { it.char != ' ' || it.bgColor != Color.Transparent || it.isInverse || it.isWideContinuation }) {
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

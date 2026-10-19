package com.arbdev.devbox.terminal

import androidx.compose.runtime.mutableIntStateOf
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
    var fgColor: Color = Color(0xFFDCDCDC),
    var bgColor: Color = Color.Transparent,
    var isBold: Boolean = false,
    var isUnderline: Boolean = false,
    var isDim: Boolean = false
) {
    fun reset() {
        char = ' '
        fgColor = Color(0xFFDCDCDC)
        bgColor = Color.Transparent
        isBold = false
        isUnderline = false
        isDim = false
    }

    fun copyFrom(other: TerminalCell) {
        char = other.char
        fgColor = other.fgColor
        bgColor = other.bgColor
        isBold = other.isBold
        isUnderline = other.isUnderline
        isDim = other.isDim
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

    private var isAltBuffer = false
    private var isCursorVisible = true

    // Current Drawing Style
    private val defaultFg = Color(0xFFDCDCDC)
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
            val c = newCols.coerceIn(40, 160)
            val r = newRows.coerceIn(15, 60)
            if (c == maxCols && r == maxRows) return

            maxCols = c
            maxRows = r

            val newGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
            for (row in 0 until maxRows.coerceAtMost(grid.size)) {
                for (col in 0 until maxCols.coerceAtMost(grid[0].size)) {
                    newGrid[row][col].copyFrom(grid[row][col])
                }
            }
            grid = newGrid

            val newAltGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
            for (row in 0 until maxRows.coerceAtMost(altGrid.size)) {
                for (col in 0 until maxCols.coerceAtMost(altGrid[0].size)) {
                    newAltGrid[row][col].copyFrom(altGrid[row][col])
                }
            }
            altGrid = newAltGrid

            cursorRow = cursorRow.coerceIn(0, maxRows - 1)
            cursorCol = cursorCol.coerceIn(0, maxCols - 1)
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
                        when (ch) {
                            '\u001B' -> parserState = ParserState.ESC
                            '\r' -> cursorCol = 0
                            '\n' -> newline()
                            '\b', '\u007F' -> {
                                if (cursorCol > 0) cursorCol--
                            }
                            '\t' -> {
                                val nextTabStop = ((cursorCol / 8) + 1) * 8
                                cursorCol = nextTabStop.coerceAtMost(maxCols - 1)
                            }
                            '\u0007' -> { /* Bell - diabaikan */ }
                            else -> {
                                if (!ch.isISOControl()) {
                                    putChar(ch)
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
                            else -> parserState = ParserState.NORMAL
                        }
                    }

                    ParserState.CHARSET -> {
                        parserState = ParserState.NORMAL
                    }

                    ParserState.OSC -> {
                        if (ch == '\u0007' || (ch == '\\' && oscBuffer.endsWith("\u001B"))) {
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

    private fun putChar(ch: Char) {
        if (cursorCol >= maxCols) {
            cursorCol = 0
            newline()
        }
        val cell = activeGrid[cursorRow][cursorCol]
        cell.char = ch
        cell.fgColor = currentFg
        cell.bgColor = currentBg
        cell.isBold = isBold
        cell.isUnderline = isUnderline
        cell.isDim = isDim
        cursorCol++
    }

    private fun newline() {
        cursorRow++
        if (cursorRow >= maxRows) {
            cursorRow = maxRows - 1
            scrollUp()
        }
    }

    private fun scrollUp() {
        if (!isAltBuffer) {
            val savedLine = Array(maxCols) { col ->
                TerminalCell().apply { copyFrom(grid[0][col]) }
            }
            scrollback.add(savedLine)
            if (scrollback.size > maxScrollback) {
                scrollback.removeAt(0)
            }
        }
        val target = activeGrid
        for (r in 0 until maxRows - 1) {
            for (c in 0 until maxCols) {
                target[r][c].copyFrom(target[r + 1][c])
            }
        }
        for (c in 0 until maxCols) {
            target[maxRows - 1][c].reset()
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

            // Mode Private (?h / ?l)
            'h' -> {
                if (isCsiPrivate) {
                    when (params.firstOrNull()) {
                        1049 -> { // Masuk ke Alternate Screen Buffer (nano, agy, htop)
                            isAltBuffer = true
                            for (r in 0 until maxRows) {
                                for (c in 0 until maxCols) altGrid[r][c].reset()
                            }
                            cursorRow = 0
                            cursorCol = 0
                        }
                        25 -> isCursorVisible = true
                    }
                }
            }

            'l' -> {
                if (isCsiPrivate) {
                    when (params.firstOrNull()) {
                        1049 -> { // Keluar dari Alternate Screen Buffer
                            isAltBuffer = false
                        }
                        25 -> isCursorVisible = false
                    }
                }
            }

            's' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }

            'u' -> {
                cursorRow = savedCursorRow.coerceIn(0, maxRows - 1)
                cursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
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
        resetSgr()
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
            7 -> Color(0xFFABB2BF) // Light Gray
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

    private fun notifyUpdate() {
        version.intValue++
    }

    /**
     * Mengubah isi matriks layar saat ini menjadi Compose AnnotatedString siap render.
     */
    fun renderToAnnotatedString(): AnnotatedString {
        synchronized(lock) {
            val target = activeGrid
            return buildAnnotatedString {
                for (r in 0 until maxRows) {
                    val line = target[r]
                    // Cari panjang baris tanpa trailing spaces yang berlebihan
                    var lastNonSpace = maxCols - 1
                    while (lastNonSpace >= 0 && line[lastNonSpace].char == ' ' && line[lastNonSpace].bgColor == Color.Transparent) {
                        lastNonSpace--
                    }

                    val endCol = (lastNonSpace + 1).coerceAtMost(maxCols)

                    for (c in 0 until endCol) {
                        val cell = line[c]
                        val isCursorHere = (isCursorVisible && r == cursorRow && c == cursorCol)

                        val effectiveFg = if (isCursorHere) Color.Black else cell.fgColor
                        val effectiveBg = if (isCursorHere) Color(0xFF61AFEF) else cell.bgColor

                        pushStyle(
                            SpanStyle(
                                color = effectiveFg,
                                background = effectiveBg,
                                fontWeight = if (cell.isBold) FontWeight.Bold else FontWeight.Normal,
                                textDecoration = if (cell.isUnderline) TextDecoration.Underline else TextDecoration.None
                            )
                        )
                        append(cell.char)
                        pop()
                    }

                    // Tampilkan kursor jika kursor berada di akhir baris kosong
                    if (isCursorVisible && r == cursorRow && cursorCol >= endCol && cursorCol < maxCols) {
                        pushStyle(SpanStyle(background = Color(0xFF61AFEF), color = Color.Black))
                        append(' ')
                        pop()
                    }

                    if (r < maxRows - 1) {
                        append('\n')
                    }
                }
            }
        }
    }

    /**
     * Mengambil teks lengkap dari scrollback history + layar aktif untuk clipboard copy.
     */
    fun getTranscriptText(): String {
        synchronized(lock) {
            val sb = StringBuilder()
            for (line in scrollback) {
                var last = maxCols - 1
                while (last >= 0 && line[last].char == ' ') last--
                for (c in 0..last) sb.append(line[c].char)
                sb.append('\n')
            }
            val target = activeGrid
            for (r in 0 until maxRows) {
                val line = target[r]
                var last = maxCols - 1
                while (last >= 0 && line[last].char == ' ') last--
                for (c in 0..last) sb.append(line[c].char)
                if (r < maxRows - 1) sb.append('\n')
            }
            return sb.toString().trimEnd()
        }
    }
}

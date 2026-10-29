package com.arbdev.devbox.terminal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
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
 * Terminal Buffer bertenaga mesin C native libvterm (Neovim/Emacs engine) via Android NDK.
 * Mengeliminasi 100% bug loncat kursor, kerning drift, dan desync TUI UltraViolet Charmbracelet.
 */
class TerminalGridBuffer(
    var maxCols: Int = 80,
    var maxRows: Int = 30
) {
    private val lock = Any()

    private var vterm = NativeVTerm(rows = maxRows, cols = maxCols)

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    var isCursorVisible: Boolean = true
        private set
    var isAltBuffer: Boolean = false
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

    // Protocols & Callbacks (OSC 0/1/2, OSC 52, OSC 8, OSC 777 DevBox Bridge)
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

    // Jetpack Compose Recomposition Triggers
    val version = mutableIntStateOf(0)
    val lineCountState = mutableIntStateOf(1)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var updatePending = false

    // JNI I/O Buffers (Pre-allocated, zero-allocation during rendering)
    private var rowDataBuffer = IntArray(maxCols * 4)
    private val cursorPos = IntArray(3)
    private val modeArr = IntArray(4)
    private val surrogateChars = CharArray(2)

    // Cached grid lines for active screen to avoid heap churn
    private var activeGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
    private val scrollbackCache = Array(128) { Array(maxCols) { TerminalCell() } }

    // Lightweight OSC Scanner Buffer
    private val oscBuffer = StringBuilder()
    private var inOsc = false

    fun setTheme(theme: TerminalTheme) {
        synchronized(lock) {
            currentTheme = theme
            notifyUpdate()
        }
    }

    fun resetForNewProcess() {
        synchronized(lock) {
            vterm.destroy()
            vterm = NativeVTerm(rows = maxRows, cols = maxCols)
            cursorRow = 0
            cursorCol = 0
            isCursorVisible = true
            isAltBuffer = false
            isBracketedPasteMode = false
            isMouseTrackingEnabled = false
            isSgrMouseMode = false
            isApplicationCursorKeys = false
            inOsc = false
            oscBuffer.setLength(0)
            notifyUpdate()
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        synchronized(lock) {
            val c = newCols.coerceIn(20, 300)
            val r = newRows.coerceIn(18, 200)
            if (c == maxCols && r == maxRows) return

            maxCols = c
            maxRows = r

            vterm.resize(maxRows, maxCols)
            rowDataBuffer = IntArray(maxCols * 4)
            activeGrid = Array(maxRows) { Array(maxCols) { TerminalCell() } }
            for (i in scrollbackCache.indices) {
                scrollbackCache[i] = Array(maxCols) { TerminalCell() }
            }

            syncNativeState()
            notifyUpdate()
        }
    }

    fun write(bytes: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return

        synchronized(lock) {
            // 1. Scan stream for OSC Sequences (OSC 0/2, OSC 7, OSC 52, OSC 777)
            scanOscSequences(bytes, offset, count)

            // 2. Feed raw bytes into native libvterm C engine
            vterm.feed(bytes, offset, count)

            // 3. Sync cursor, modes, and title from native engine
            syncNativeState()

            // 4. Trigger UI redraw
            notifyUpdate()
        }
    }

    private fun syncNativeState() {
        vterm.getCursor(cursorPos)
        cursorRow = cursorPos[0]
        cursorCol = cursorPos[1]
        isCursorVisible = (cursorPos[2] != 0)

        vterm.getModes(modeArr)
        isBracketedPasteMode = (modeArr[0] != 0)
        isMouseTrackingEnabled = (modeArr[1] != 0)
        isSgrMouseMode = (modeArr[2] != 0)
        isApplicationCursorKeys = (modeArr[3] != 0)

        isAltBuffer = vterm.isAltScreen()

        val vTitle = vterm.getTitle()
        if (!vTitle.isNullOrBlank() && vTitle != windowTitle) {
            windowTitle = vTitle
            onTitleChanged?.invoke(vTitle)
        }
    }

    private fun scanOscSequences(bytes: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            if (!inOsc) {
                if (b == 0x1B && i + 1 < end && bytes[i + 1] == ']'.code.toByte()) {
                    inOsc = true
                    oscBuffer.setLength(0)
                    i += 2
                    continue
                }
            } else {
                if (b == 0x07) { // BEL terminates OSC
                    inOsc = false
                    try {
                        handleOscCommand(oscBuffer.toString())
                    } catch (_: Exception) {}
                    oscBuffer.setLength(0)
                } else if (b == 0x1B && i + 1 < end && bytes[i + 1] == '\\'.code.toByte()) { // ST (ESC \) terminates OSC
                    inOsc = false
                    try {
                        handleOscCommand(oscBuffer.toString())
                    } catch (_: Exception) {}
                    oscBuffer.setLength(0)
                    i += 2
                    continue
                } else {
                    if (oscBuffer.length < 4096) {
                        oscBuffer.append(b.toChar())
                    }
                }
            }
            i++
        }
    }

    private fun handleOscCommand(osc: String) {
        if (osc.isEmpty()) return
        val semicolonIdx = osc.indexOf(';')
        if (semicolonIdx == -1) return
        val code = osc.substring(0, semicolonIdx).toIntOrNull() ?: return
        val payload = osc.substring(semicolonIdx + 1)

        when (code) {
            0, 1, 2 -> {
                windowTitle = payload
                onTitleChanged?.invoke(payload)
            }
            10 -> {
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
                val dataIdx = payload.indexOf(';')
                val base64Data = if (dataIdx != -1) payload.substring(dataIdx + 1) else payload
                if (base64Data == "?" || payload.endsWith(";?") || payload == "?") {
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
                val urlIdx = payload.indexOf(';')
                if (urlIdx != -1) {
                    val url = payload.substring(urlIdx + 1)
                    currentHyperlinkUrl = if (url.isNotEmpty()) url else null
                } else {
                    currentHyperlinkUrl = null
                }
            }
            777 -> {
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
                            if (msgIdx != -1) {
                                val title = subArg.substring(0, msgIdx).trim()
                                val msg = subArg.substring(msgIdx + 1).trim()
                                onNotify?.invoke(title, msg)
                            } else {
                                onNotify?.invoke("DevBox", subArg.trim())
                            }
                        }
                    }
                }
            }
            7 -> {
                if (payload.startsWith("file://")) {
                    val cleanPath = payload.substringAfter("file://").substringAfter("/")
                    if (cleanPath.isNotEmpty()) {
                        onCwdChanged?.invoke("/$cleanPath")
                    }
                }
            }
        }
    }

    private fun notifyUpdate() {
        if (!updatePending) {
            updatePending = true
            mainHandler.post {
                synchronized(lock) {
                    updatePending = false
                    version.intValue++
                    val count = getLineCount()
                    if (lineCountState.intValue != count) {
                        lineCountState.intValue = count
                    }
                }
            }
        }
    }

    fun getScrollbackSize(): Int {
        synchronized(lock) {
            return vterm.getScrollbackSize()
        }
    }

    fun getLineCount(): Int {
        synchronized(lock) {
            return getScrollbackSize() + maxRows
        }
    }

    fun getLineAt(absoluteIndex: Int): Array<TerminalCell>? {
        synchronized(lock) {
            val historyCount = getScrollbackSize()
            val totalLines = historyCount + maxRows
            if (absoluteIndex !in 0 until totalLines) return null

            val cols = maxCols
            if (rowDataBuffer.size < cols * 4) {
                rowDataBuffer = IntArray(cols * 4)
            }
            val returnedCols = vterm.getRowData(absoluteIndex, rowDataBuffer)
            val effectiveCols = minOf(cols, if (returnedCols > 0) returnedCols else cols)

            val line: Array<TerminalCell> = if (absoluteIndex >= historyCount) {
                val activeRow = (absoluteIndex - historyCount).coerceIn(0, maxRows - 1)
                activeGrid[activeRow]
            } else {
                val cacheIdx = absoluteIndex % scrollbackCache.size
                scrollbackCache[cacheIdx]
            }

            for (c in 0 until effectiveCols) {
                val base = c * 4
                val cp = rowDataBuffer[base + 0]
                val fg = rowDataBuffer[base + 1]
                val bg = rowDataBuffer[base + 2]
                val flags = rowDataBuffer[base + 3]

                val cell = line[c]
                if (cp == 0) {
                    cell.char = ' '
                    cell.extraChar = null
                } else if (cp <= 0xFFFF) {
                    cell.char = cp.toChar()
                    cell.extraChar = null
                } else {
                    Character.toChars(cp, surrogateChars, 0)
                    cell.char = surrogateChars[0]
                    cell.extraChar = surrogateChars[1]
                }

                cell.fgColor = if (fg == 0) currentTheme.foreground else Color(fg)
                cell.bgColor = if (bg == 0) Color.Transparent else Color(bg)
                cell.isBold = (flags and 1) != 0
                cell.isUnderline = (flags and 2) != 0
                cell.isDim = false
                cell.isInverse = (flags and 8) != 0
                cell.isWide = (flags and 16) != 0
                cell.isWideContinuation = (flags and 32) != 0
                cell.hyperlinkUrl = null
            }
            for (c in effectiveCols until line.size) {
                line[c].reset()
            }
            return line
        }
    }

    fun getTranscriptText(): String {
        synchronized(lock) {
            val sb = StringBuilder()
            val totalLines = getLineCount()
            for (r in 0 until totalLines) {
                val line = getLineAt(r) ?: continue
                var last = maxCols - 1
                while (last >= 0 && line[last].char == ' ') last--
                for (c in 0..last) {
                    val cell = line[c]
                    if (cell.isWideContinuation) continue
                    sb.append(cell.char)
                    if (cell.extraChar != null) sb.append(cell.extraChar)
                }
                if (r < totalLines - 1) sb.append('\n')
            }
            return sb.toString().trimEnd()
        }
    }

    fun getUrlAt(absoluteRow: Int, col: Int): String? {
        synchronized(lock) {
            val line = getLineAt(absoluteRow) ?: return null
            val cell = line.getOrNull(col)
            if (cell?.hyperlinkUrl != null) return cleanUrl(cell.hyperlinkUrl!!)

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

            val matchesOnCurrent = urlRegex.findAll(currentLine).toList()
            for (m in matchesOnCurrent) {
                val expandedRange = (m.range.first - 6)..(m.range.last + 6)
                if (col in expandedRange) {
                    return cleanUrl(m.value)
                }
            }

            if (matchesOnCurrent.size == 1) {
                return cleanUrl(matchesOnCurrent[0].value)
            } else if (matchesOnCurrent.size > 1) {
                val closest = matchesOnCurrent.minByOrNull { m ->
                    val mid = (m.range.first + m.range.last) / 2
                    kotlin.math.abs(mid - col)
                }
                if (closest != null) return cleanUrl(closest.value)
            }

            val prevMatches = urlRegex.findAll(prevLine).toList()
            for (m in prevMatches) {
                if (col in (m.range.first - 3)..(m.range.last + 3)) return cleanUrl(m.value)
            }
            val nextMatches = urlRegex.findAll(nextLine).toList()
            for (m in nextMatches) {
                if (col in (m.range.first - 3)..(m.range.last + 3)) return cleanUrl(m.value)
            }

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

    fun destroy() {
        synchronized(lock) {
            vterm.destroy()
        }
    }
}

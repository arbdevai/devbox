package com.arbdev.devbox.terminal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 * Representasi gambar grafis (Kitty Graphics Protocol) yang ditempatkan di dalam buffer terminal.
 */
data class TerminalImage(
    val id: Int,
    val bitmap: android.graphics.Bitmap,
    val startLine: Int,
    val startCol: Int,
    val cellWidth: Int,
    val cellHeight: Int
)

/**
 * Terminal Buffer bertenaga mesin C native libvterm (Neovim/Emacs engine) via Android NDK.
 * Dilengkapi Kitty Graphics Protocol, Synchronized Update (DEC 2026), dan zero-GC rendering.
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

    // DEC Mode 1049 Alternate Screen Buffer State (Reaktif untuk beralih layar Agent CLI vs Bash secara bersih)
    val isAltBufferState = mutableStateOf(false)
    val isAltBuffer: Boolean
        get() = isAltBufferState.value

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
    var isSynchronizedUpdate: Boolean = false
        private set
    private var syncStartTime: Long = 0L

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

    // Lightweight Stream Scanner Buffers
    private val oscBuffer = StringBuilder()
    private var inOsc = false

    // Kitty Graphics Protocol Accumulator
    private var inApc = false
    private val apcBuffer = StringBuilder()
    private val kittyChunkBuffer = StringBuilder()
    private var kittyChunkAction: Char = 't'
    private var kittyChunkFormat: Int = 100
    private var kittyChunkWidth: Int = 0
    private var kittyChunkHeight: Int = 0
    private var kittyChunkCols: Int = 0
    private var kittyChunkRows: Int = 0
    private var kittyChunkId: Int = 0
    private var kittyChunkQuiet: Int = 0

    // List of Active Renderable Images
    private val imagesLock = Any()
    private val images = mutableListOf<TerminalImage>()

    fun getVisibleImages(firstLine: Int, lastLine: Int): List<TerminalImage> {
        synchronized(imagesLock) {
            if (images.isEmpty()) return emptyList()
            return images.filter { img ->
                val endLine = img.startLine + img.cellHeight
                endLine >= firstLine && img.startLine <= lastLine
            }
        }
    }

    fun clearImages() {
        synchronized(imagesLock) {
            for (img in images) {
                try {
                    if (!img.bitmap.isRecycled) img.bitmap.recycle()
                } catch (_: Exception) {}
            }
            images.clear()
        }
    }

    private fun addImage(image: TerminalImage) {
        synchronized(imagesLock) {
            if (images.size >= 40) {
                val oldest = images.removeAt(0)
                try {
                    if (!oldest.bitmap.isRecycled) oldest.bitmap.recycle()
                } catch (_: Exception) {}
            }
            images.add(image)
        }
    }

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
            isAltBufferState.value = false
            isBracketedPasteMode = false
            isMouseTrackingEnabled = false
            isSgrMouseMode = false
            isApplicationCursorKeys = false
            isSynchronizedUpdate = false
            syncStartTime = 0L
            inOsc = false
            inApc = false
            oscBuffer.setLength(0)
            apcBuffer.setLength(0)
            kittyChunkBuffer.setLength(0)
            clearImages()
            notifyUpdate()
        }
    }

    fun clearScrollback() {
        synchronized(lock) {
            vterm.clearScrollback()
            for (line in scrollbackCache) {
                for (cell in line) {
                    cell.reset()
                }
            }
            clearImages()
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
            // 1. Scan stream for Escape Protocols (OSC, Kitty Graphics APC, DEC 2026)
            scanEscapeSequences(bytes, offset, count)

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

        val alt = vterm.isAltScreen()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (isAltBufferState.value != alt) {
                isAltBufferState.value = alt
            }
        }

        val vTitle = vterm.getTitle()
        if (!vTitle.isNullOrBlank() && vTitle != windowTitle) {
            windowTitle = vTitle
            onTitleChanged?.invoke(vTitle)
        }
    }

    private fun scanEscapeSequences(bytes: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            if (!inOsc && !inApc) {
                if (b == 0x1B && i + 1 < end) {
                    val next = bytes[i + 1]
                    if (next == ']'.code.toByte()) {
                        inOsc = true
                        oscBuffer.setLength(0)
                        i += 2
                        continue
                    } else if (next == '_'.code.toByte()) {
                        inApc = true
                        apcBuffer.setLength(0)
                        i += 2
                        continue
                    } else if (i + 7 < end && next == '['.code.toByte() && bytes[i + 2] == '?'.code.toByte()
                        && bytes[i + 3] == '2'.code.toByte() && bytes[i + 4] == '0'.code.toByte()
                        && bytes[i + 5] == '2'.code.toByte() && bytes[i + 6] == '6'.code.toByte()
                    ) {
                        // DEC Mode 2026: Synchronized Update (Bubble Tea / agy atomic frame rendering)
                        val modeChar = bytes[i + 7].toInt().toChar()
                        if (modeChar == 'h') {
                            isSynchronizedUpdate = true
                            syncStartTime = System.currentTimeMillis()
                        } else if (modeChar == 'l') {
                            isSynchronizedUpdate = false
                            notifyUpdate()
                        }
                        i += 8
                        continue
                    }
                }
            } else if (inOsc) {
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
            } else if (inApc) {
                if (b == 0x07) { // BEL terminates APC
                    inApc = false
                    try {
                        handleApcCommand(apcBuffer.toString())
                    } catch (_: Exception) {}
                    apcBuffer.setLength(0)
                } else if (b == 0x1B && i + 1 < end && bytes[i + 1] == '\\'.code.toByte()) { // ST terminates APC
                    inApc = false
                    try {
                        handleApcCommand(apcBuffer.toString())
                    } catch (_: Exception) {}
                    apcBuffer.setLength(0)
                    i += 2
                    continue
                } else {
                    if (apcBuffer.length < 1000000) { // Limit chunk to 1MB per packet
                        apcBuffer.append(b.toChar())
                    }
                }
            }
            i++
        }
    }

    private fun handleApcCommand(apc: String) {
        if (!apc.startsWith("G")) return
        val content = apc.substring(1)
        val semiIdx = content.indexOf(';')
        val controlStr = if (semiIdx != -1) content.substring(0, semiIdx) else content
        val payloadStr = if (semiIdx != -1) content.substring(semiIdx + 1).trim() else ""

        var action = 't'
        var format = 100
        var moreChunks = 0
        var imgId = 0
        var widthPx = 0
        var heightPx = 0
        var cellCols = 0
        var cellRows = 0
        var quiet = 0

        if (controlStr.isNotEmpty()) {
            val tokens = controlStr.split(',')
            for (tok in tokens) {
                val eq = tok.indexOf('=')
                if (eq != -1 && eq > 0) {
                    val k = tok[0]
                    val v = tok.substring(eq + 1)
                    when (k) {
                        'a' -> action = v.firstOrNull() ?: 't'
                        'f' -> format = v.toIntOrNull() ?: 100
                        'm' -> moreChunks = v.toIntOrNull() ?: 0
                        'i' -> imgId = v.toIntOrNull() ?: 0
                        's' -> widthPx = v.toIntOrNull() ?: 0
                        'v' -> heightPx = v.toIntOrNull() ?: 0
                        'c' -> cellCols = v.toIntOrNull() ?: 0
                        'r' -> cellRows = v.toIntOrNull() ?: 0
                        'q' -> quiet = v.toIntOrNull() ?: 0
                    }
                }
            }
        }

        // Action: Query Support ('q')
        if (action == 'q' || action == 'Q') {
            onSendResponse?.invoke("\u001B_Gi=$imgId;OK\u001B\\")
            return
        }

        // Action: Delete Images ('d')
        if (action == 'd' || action == 'D') {
            synchronized(imagesLock) {
                if (imgId == 0) {
                    clearImages()
                } else {
                    val it = images.iterator()
                    while (it.hasNext()) {
                        val img = it.next()
                        if (img.id == imgId) {
                            try { if (!img.bitmap.isRecycled) img.bitmap.recycle() } catch (_: Exception) {}
                            it.remove()
                        }
                    }
                }
            }
            notifyUpdate()
            return
        }

        // Action: Transmit & Display ('t' / 'T')
        if (action == 't' || action == 'T') {
            if (moreChunks == 1) {
                if (kittyChunkBuffer.isEmpty()) {
                    kittyChunkAction = action
                    kittyChunkFormat = format
                    kittyChunkWidth = widthPx
                    kittyChunkHeight = heightPx
                    kittyChunkCols = cellCols
                    kittyChunkRows = cellRows
                    kittyChunkId = imgId
                    kittyChunkQuiet = quiet
                }
                kittyChunkBuffer.append(payloadStr)
                return
            }

            val fullPayload = if (kittyChunkBuffer.isNotEmpty()) {
                val res = kittyChunkBuffer.append(payloadStr).toString()
                format = kittyChunkFormat
                widthPx = if (widthPx > 0) widthPx else kittyChunkWidth
                heightPx = if (heightPx > 0) heightPx else kittyChunkHeight
                cellCols = if (cellCols > 0) cellCols else kittyChunkCols
                cellRows = if (cellRows > 0) cellRows else kittyChunkRows
                imgId = if (imgId > 0) imgId else kittyChunkId
                quiet = if (quiet > 0) quiet else kittyChunkQuiet
                kittyChunkBuffer.setLength(0)
                res
            } else {
                payloadStr
            }

            if (fullPayload.isEmpty()) return

            try {
                val cleanB64 = fullPayload.replace("\r", "").replace("\n", "").trim()
                val rawBytes = android.util.Base64.decode(cleanB64, android.util.Base64.DEFAULT)
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    val bitmap = decodeBitmap(rawBytes, format, widthPx, heightPx)
                    if (bitmap != null) {
                        val finalCols = if (cellCols > 0) cellCols else ((bitmap.width + 19) / 20).coerceIn(1, maxCols)
                        val finalRows = if (cellRows > 0) cellRows else ((bitmap.height + 39) / 40).coerceIn(1, maxRows)
                        val startL = getScrollbackSize() + cursorRow
                        val startC = cursorCol

                        addImage(TerminalImage(imgId, bitmap, startL, startC, finalCols, finalRows))
                        if (quiet < 2 && imgId > 0) {
                            onSendResponse?.invoke("\u001B_Gi=$imgId;OK\u001B\\")
                        }
                        notifyUpdate()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun decodeBitmap(rawBytes: ByteArray, format: Int, w: Int, h: Int): android.graphics.Bitmap? {
        return try {
            if (format == 100 || isPngOrJpeg(rawBytes)) {
                android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            } else if (format == 32 && w > 0 && h > 0 && rawBytes.size >= w * h * 4) {
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val buffer = java.nio.ByteBuffer.wrap(rawBytes)
                bmp.copyPixelsFromBuffer(buffer)
                bmp
            } else if (format == 24 && w > 0 && h > 0 && rawBytes.size >= w * h * 3) {
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val pixels = IntArray(w * h)
                for (i in pixels.indices) {
                    val r = rawBytes[i * 3 + 0].toInt() and 0xFF
                    val g = rawBytes[i * 3 + 1].toInt() and 0xFF
                    val b = rawBytes[i * 3 + 2].toInt() and 0xFF
                    pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                bmp
            } else {
                android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isPngOrJpeg(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        return false
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
        if (isSynchronizedUpdate && (System.currentTimeMillis() - syncStartTime < 60)) {
            // Tahan rendering saat batch frame DEC Mode 2026 sedang berlangsung (anti-tearing / anti-flicker)
            return
        }
        if (!updatePending) {
            updatePending = true
            mainHandler.post {
                synchronized(lock) {
                    updatePending = false
                    version.intValue++
                    val alt = vterm.isAltScreen()
                    if (isAltBufferState.value != alt) {
                        isAltBufferState.value = alt
                    }
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
            val totalLines = getLineCount()
            if (absoluteRow !in 0 until totalLines) return null

            val targetLine = getLineAt(absoluteRow) ?: return null
            val targetCell = targetLine.getOrNull(col)
            if (targetCell?.hyperlinkUrl != null) return cleanUrl(targetCell.hyperlinkUrl!!)

            val urlRegex = Regex("(?:https?|file)://[^\\s<>\"]+|localhost:\\d+[^\\s<>\"]*")

            // Kumpulan baris kandidat di sekitar baris yang diklik (hingga 15 baris ke atas dan ke bawah)
            val windowStart = (absoluteRow - 15).coerceAtLeast(0)
            val windowEnd = (absoluteRow + 15).coerceAtMost(totalLines - 1)

            val stitchedSb = StringBuilder()
            var stitchedTargetOffset = -1

            for (r in windowStart..windowEnd) {
                val l = getLineAt(r) ?: continue
                val lineStr = StringBuilder()
                var lastNonSpace = -1
                for (c in 0 until maxCols) {
                    if (!l[c].isWideContinuation) {
                        lineStr.append(l[c].char)
                        if (l[c].char != ' ') lastNonSpace = lineStr.length - 1
                    }
                }

                val trimmedEnd = if (lastNonSpace >= 0) lineStr.substring(0, lastNonSpace + 1) else ""
                val leadingSpaces = trimmedEnd.takeWhile { it == ' ' }.length
                val contentWithoutIndent = trimmedEnd.trimStart()

                // Cek apakah baris r adalah kelanjutan pembungkusan URL dari baris sebelumnya:
                val isContinuation = if (stitchedSb.isNotEmpty() && contentWithoutIndent.isNotEmpty()) {
                    val prevLastChar = stitchedSb.last()
                    val nextFirstChar = contentWithoutIndent.first()
                    val prevIsUrlChar = prevLastChar in 'a'..'z' || prevLastChar in 'A'..'Z' || prevLastChar in '0'..'9' || prevLastChar in "-._~:/?#[]@!$&'()*+,;%="
                    val nextIsUrlChar = nextFirstChar in 'a'..'z' || nextFirstChar in 'A'..'Z' || nextFirstChar in '0'..'9' || nextFirstChar in "-._~:/?#[]@!$&'()*+,;%="
                    prevIsUrlChar && nextIsUrlChar && (leadingSpaces in 0..4) && !contentWithoutIndent.contains(" ")
                } else false

                if (r == absoluteRow) {
                    stitchedTargetOffset = stitchedSb.length + (col - leadingSpaces).coerceAtLeast(0)
                }

                if (isContinuation) {
                    stitchedSb.append(contentWithoutIndent)
                } else {
                    if (stitchedSb.isNotEmpty()) {
                        stitchedSb.append(" ")
                    }
                    stitchedSb.append(contentWithoutIndent)
                }
            }

            val stitchedText = stitchedSb.toString()
            val matches = urlRegex.findAll(stitchedText).toList()

            if (stitchedTargetOffset >= 0 && matches.isNotEmpty()) {
                val encompassing = matches.firstOrNull { m ->
                    stitchedTargetOffset in (m.range.first - 15)..(m.range.last + 15)
                }
                if (encompassing != null) {
                    return cleanUrl(encompassing.value)
                }
                val closest = matches.minByOrNull { m ->
                    val mid = (m.range.first + m.range.last) / 2
                    kotlin.math.abs(mid - stitchedTargetOffset)
                }
                if (closest != null) {
                    return cleanUrl(closest.value)
                }
            }

            if (matches.isNotEmpty()) {
                return cleanUrl(matches.first().value)
            }

            return null
        }
    }

    fun extractAllUrls(): List<String> {
        synchronized(lock) {
            val urls = linkedSetOf<String>()
            val urlRegex = Regex("(?:https?|file)://[^\\s<>\"]+|localhost:\\d+[^\\s<>\"]*")
            val totalLines = getLineCount()

            val textSb = StringBuilder()
            for (r in 0 until totalLines) {
                val line = getLineAt(r) ?: continue
                for (c in 0 until maxCols) {
                    val h = line[c].hyperlinkUrl
                    if (h != null) urls.add(cleanUrl(h))
                }
                var last = maxCols - 1
                while (last >= 0 && line[last].char == ' ') last--
                val lineContent = StringBuilder()
                for (c in 0..last) {
                    if (!line[c].isWideContinuation) lineContent.append(line[c].char)
                }
                val trimmedEnd = lineContent.toString()
                val leadingSpaces = trimmedEnd.takeWhile { it == ' ' }.length
                val contentWithoutIndent = trimmedEnd.trimStart()

                val isContinuation = if (textSb.isNotEmpty() && contentWithoutIndent.isNotEmpty()) {
                    val prevLastChar = textSb.last()
                    val nextFirstChar = contentWithoutIndent.first()
                    val prevIsUrlChar = prevLastChar in 'a'..'z' || prevLastChar in 'A'..'Z' || prevLastChar in '0'..'9' || prevLastChar in "-._~:/?#[]@!$&'()*+,;%="
                    val nextIsUrlChar = nextFirstChar in 'a'..'z' || nextFirstChar in 'A'..'Z' || nextFirstChar in '0'..'9' || nextFirstChar in "-._~:/?#[]@!$&'()*+,;%="
                    prevIsUrlChar && nextIsUrlChar && (leadingSpaces in 0..4) && !contentWithoutIndent.contains(" ")
                } else false

                if (isContinuation) {
                    textSb.append(contentWithoutIndent)
                } else {
                    if (textSb.isNotEmpty()) textSb.append(' ')
                    textSb.append(contentWithoutIndent)
                }
            }
            urlRegex.findAll(textSb.toString()).forEach { m ->
                urls.add(cleanUrl(m.value))
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
            clearImages()
            vterm.destroy()
        }
    }
}

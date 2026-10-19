package com.arbdev.devbox.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.regex.Pattern

data class TerminalLine(
    val text: String,
    val type: LineType = LineType.OUTPUT
)

enum class LineType {
    INPUT,
    OUTPUT,
    ERROR,
    SYSTEM
}

object AnsiParser {
    // ECMA-48 standard CSI, OSC, and 2-byte escape sequences
    private val MASTER_ANSI_PATTERN = Pattern.compile(
        "\u001b\\[([0-9:;<=>?]*)[ !\"#\\\$%&'()*+,-./]*([@A-Z\\[\\\\\\]^_`a-z{|}~])|\u001b\\].*?(?:\u0007|\u001b\\\\)|\u001b[@-Z\\\\-_]"
    )

    private val STANDARD_FG_COLORS = mapOf(
        30 to Color.parseColor("#484F58"), // Black
        31 to Color.parseColor("#FF7B72"), // Red
        32 to Color.parseColor("#3FB950"), // Green
        33 to Color.parseColor("#D29922"), // Yellow
        34 to Color.parseColor("#58A6FF"), // Blue
        35 to Color.parseColor("#BC8CFF"), // Magenta
        36 to Color.parseColor("#39C5CF"), // Cyan
        37 to Color.parseColor("#C9D1D9"), // White
        90 to Color.parseColor("#6E7681"), // Bright Black (Gray)
        91 to Color.parseColor("#FFA198"), // Bright Red
        92 to Color.parseColor("#56D364"), // Bright Green
        93 to Color.parseColor("#E3B341"), // Bright Yellow
        94 to Color.parseColor("#79C0FF"), // Bright Blue
        95 to Color.parseColor("#D2A8FF"), // Bright Magenta
        96 to Color.parseColor("#56D4DD"), // Bright Cyan
        97 to Color.parseColor("#F0F6FC")  // Bright White
    )

    private val STANDARD_BG_COLORS = mapOf(
        40 to Color.parseColor("#161B22"), // Black BG
        41 to Color.parseColor("#8E1519"), // Red BG
        42 to Color.parseColor("#1B4721"), // Green BG
        43 to Color.parseColor("#533F03"), // Yellow BG
        44 to Color.parseColor("#0C2D6B"), // Blue BG
        45 to Color.parseColor("#4C2889"), // Magenta BG
        46 to Color.parseColor("#13454C"), // Cyan BG
        47 to Color.parseColor("#484F58"), // White BG
        100 to Color.parseColor("#21262D"),
        101 to Color.parseColor("#A82A2E"),
        102 to Color.parseColor("#238636"),
        103 to Color.parseColor("#8E6B04"),
        104 to Color.parseColor("#1F6FEB"),
        105 to Color.parseColor("#8957E5"),
        106 to Color.parseColor("#39C5CF"),
        107 to Color.parseColor("#8B949E")
    )

    private val DEFAULT_OUTPUT_COLOR = Color.parseColor("#C9D1D9")
    private val DEFAULT_INPUT_COLOR = Color.parseColor("#7EE787")
    private val DEFAULT_ERROR_COLOR = Color.parseColor("#FFA198")
    private val DEFAULT_SYSTEM_COLOR = Color.parseColor("#E3B341")

    /**
     * Converts standard 256-color index (xterm256) into Android Color Int.
     * Supports 0-15 standard/bright, 16-231 6x6x6 color cube, and 232-255 grayscale.
     */
    fun xterm256Color(n: Int): Int {
        if (n in 0..15) {
            return when (n) {
                0 -> Color.rgb(0, 0, 0)
                1 -> Color.rgb(205, 0, 0)
                2 -> Color.rgb(0, 205, 0)
                3 -> Color.rgb(205, 205, 0)
                4 -> Color.rgb(0, 0, 238)
                5 -> Color.rgb(205, 0, 205)
                6 -> Color.rgb(0, 205, 205)
                7 -> Color.rgb(229, 229, 229)
                8 -> Color.rgb(127, 127, 127)
                9 -> Color.rgb(255, 0, 0)
                10 -> Color.rgb(0, 255, 0)
                11 -> Color.rgb(255, 255, 0)
                12 -> Color.rgb(92, 92, 255)
                13 -> Color.rgb(255, 0, 255)
                14 -> Color.rgb(0, 255, 255)
                else -> Color.rgb(255, 255, 255)
            }
        }
        if (n in 16..231) {
            val idx = n - 16
            val r = idx / 36
            val g = (idx % 36) / 6
            val b = idx % 6
            val rv = if (r == 0) 0 else 55 + r * 40
            val gv = if (g == 0) 0 else 55 + g * 40
            val bv = if (b == 0) 0 else 55 + b * 40
            return Color.rgb(rv, gv, bv)
        }
        if (n in 232..255) {
            val gray = 8 + (n - 232) * 10
            return Color.rgb(gray, gray, gray)
        }
        return DEFAULT_OUTPUT_COLOR
    }

    /**
     * Strips all ANSI escape sequences, OSC queries, and terminal control characters
     * to obtain pure readable plain text.
     */
    fun stripAnsi(text: String): String {
        return MASTER_ANSI_PATTERN.matcher(text).replaceAll("")
    }

    fun parseLine(line: TerminalLine, ssb: SpannableStringBuilder) {
        val rawText = line.text

        val baseColor = when (line.type) {
            LineType.INPUT -> DEFAULT_INPUT_COLOR
            LineType.OUTPUT -> DEFAULT_OUTPUT_COLOR
            LineType.ERROR -> DEFAULT_ERROR_COLOR
            LineType.SYSTEM -> DEFAULT_SYSTEM_COLOR
        }

        if (!rawText.contains("\u001b")) {
            val start = ssb.length
            ssb.append(rawText).append("\n")
            val end = ssb.length
            ssb.setSpan(ForegroundColorSpan(baseColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }

        val matcher = MASTER_ANSI_PATTERN.matcher(rawText)
        var lastEnd = 0
        var currentFgColor: Int? = null
        var currentBgColor: Int? = null
        var isBold = false

        while (matcher.find()) {
            val matchStart = matcher.start()
            val matchEnd = matcher.end()

            if (matchStart > lastEnd) {
                val segment = rawText.substring(lastEnd, matchStart)
                val start = ssb.length
                ssb.append(segment)
                val end = ssb.length

                val fg = currentFgColor ?: baseColor
                ssb.setSpan(ForegroundColorSpan(fg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                currentBgColor?.let { bg ->
                    ssb.setSpan(BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (isBold) {
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Group 1: parameters, Group 2: CSI command character
            val params = matcher.group(1)
            val cmd = matcher.group(2)

            if (cmd == "m") {
                val paramsStr = params ?: ""
                if (paramsStr.isEmpty() || paramsStr == "0") {
                    currentFgColor = null
                    currentBgColor = null
                    isBold = false
                } else {
                    val tokens = paramsStr.split(";").mapNotNull { it.toIntOrNull() }
                    var i = 0
                    while (i < tokens.size) {
                        val code = tokens[i]
                        when {
                            code == 0 -> {
                                currentFgColor = null
                                currentBgColor = null
                                isBold = false
                                i++
                            }
                            code == 1 -> {
                                isBold = true
                                i++
                            }
                            code == 39 -> {
                                currentFgColor = null
                                i++
                            }
                            code == 49 -> {
                                currentBgColor = null
                                i++
                            }
                            code in 30..37 || code in 90..97 -> {
                                currentFgColor = STANDARD_FG_COLORS[code]
                                i++
                            }
                            code in 40..47 || code in 100..107 -> {
                                currentBgColor = STANDARD_BG_COLORS[code]
                                i++
                            }
                            code == 38 -> {
                                if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                                    currentFgColor = xterm256Color(tokens[i + 2])
                                    i += 3
                                } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                                    val r = tokens[i + 2].coerceIn(0, 255)
                                    val g = tokens[i + 3].coerceIn(0, 255)
                                    val b = tokens[i + 4].coerceIn(0, 255)
                                    currentFgColor = Color.rgb(r, g, b)
                                    i += 5
                                } else {
                                    i++
                                }
                            }
                            code == 48 -> {
                                if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                                    currentBgColor = xterm256Color(tokens[i + 2])
                                    i += 3
                                } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                                    val r = tokens[i + 2].coerceIn(0, 255)
                                    val g = tokens[i + 3].coerceIn(0, 255)
                                    val b = tokens[i + 4].coerceIn(0, 255)
                                    currentBgColor = Color.rgb(r, g, b)
                                    i += 5
                                } else {
                                    i++
                                }
                            }
                            else -> i++
                        }
                    }
                }
            }

            lastEnd = matchEnd
        }

        if (lastEnd < rawText.length) {
            val segment = rawText.substring(lastEnd)
            val start = ssb.length
            ssb.append(segment)
            val end = ssb.length

            val fg = currentFgColor ?: baseColor
            ssb.setSpan(ForegroundColorSpan(fg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            currentBgColor?.let { bg ->
                ssb.setSpan(BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (isBold) {
                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        ssb.append("\n")
    }

    fun buildSpannable(lines: List<TerminalLine>): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        for (line in lines) {
            parseLine(line, ssb)
        }
        return ssb
    }
}

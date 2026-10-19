package com.arbdev.devbox.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
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
    // Regex parsing ANSI escape codes (colors, bold, and stripping terminal control sequences)
    private val ANSI_PATTERN = Pattern.compile("\u001b\\[([0-9;]*)([a-zA-Z])")

    private val COLOR_MAP = mapOf(
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

    private val DEFAULT_OUTPUT_COLOR = Color.parseColor("#C9D1D9")
    private val DEFAULT_INPUT_COLOR = Color.parseColor("#7EE787")
    private val DEFAULT_ERROR_COLOR = Color.parseColor("#FFA198")
    private val DEFAULT_SYSTEM_COLOR = Color.parseColor("#E3B341")

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

        val matcher = ANSI_PATTERN.matcher(rawText)
        var lastEnd = 0
        var currentColor: Int? = null
        var isBold = false

        while (matcher.find()) {
            val matchStart = matcher.start()
            val matchEnd = matcher.end()

            if (matchStart > lastEnd) {
                val segment = rawText.substring(lastEnd, matchStart)
                val start = ssb.length
                ssb.append(segment)
                val end = ssb.length

                val spanColor = currentColor ?: baseColor
                ssb.setSpan(ForegroundColorSpan(spanColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isBold) {
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            val params = matcher.group(1) ?: ""
            val cmd = matcher.group(2) ?: ""

            if (cmd == "m") {
                if (params.isEmpty() || params == "0") {
                    currentColor = null
                    isBold = false
                } else {
                    val codes = params.split(";").mapNotNull { it.toIntOrNull() }
                    for (code in codes) {
                        when {
                            code == 0 -> {
                                currentColor = null
                                isBold = false
                            }
                            code == 1 -> isBold = true
                            COLOR_MAP.containsKey(code) -> currentColor = COLOR_MAP[code]
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

            val spanColor = currentColor ?: baseColor
            ssb.setSpan(ForegroundColorSpan(spanColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

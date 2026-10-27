package com.arbdev.devbox.terminal

import androidx.compose.ui.graphics.Color

/**
 * Model dan Preset Palet Warna Tema Terminal DevBox.
 * Mendukung tema gelap modern terpopuler dan mode hemat daya baterai AMOLED (OLED Pure Black).
 */
data class TerminalTheme(
    val id: String,
    val name: String,
    val description: String,
    val foreground: Color,
    val background: Color,
    val cursorColor: Color,
    val ansiColors: List<Color>
) {
    companion object {
        val DevBoxEmerald = TerminalTheme(
            id = "devbox_emerald",
            name = "DevBox Matrix Emerald (Default)",
            description = "Tema bawaan dengan aksen hijau emerald high-contrast",
            foreground = Color(0xFFC9D1D9),
            background = Color.Transparent,
            cursorColor = Color(0xFF7DFF4C),
            ansiColors = listOf(
                Color(0xFF141414), // 0 Black
                Color(0xFFFF6B6B), // 1 Red
                Color(0xFF7DFF4C), // 2 Green (#7DFF4C)
                Color(0xFFFFD166), // 3 Yellow
                Color(0xFFA8FF82), // 4 Accent Lime
                Color(0xFFC678DD), // 5 Magenta
                Color(0xFF73F59F), // 6 Mint/Emerald
                Color(0xFFE3E5E0), // 7 Light Gray
                Color(0xFF5C635A), // 8 Dark Gray
                Color(0xFFFF8E8E), // 9 Bright Red
                Color(0xFF8FFF60), // 10 Bright Green
                Color(0xFFFFE082), // 11 Bright Yellow
                Color(0xFFB8FF96), // 12 Bright Lime
                Color(0xFFD896FF), // 13 Bright Magenta
                Color(0xFF8BFFB5), // 14 Bright Mint
                Color(0xFFFFFFFF)  // 15 Bright White
            )
        )

        val CatppuccinMocha = TerminalTheme(
            id = "catppuccin_mocha",
            name = "Catppuccin Mocha",
            description = "Palet warna developer modern #1 terfavorit",
            foreground = Color(0xFFCDD6F4),
            background = Color(0xFF1E1E2E),
            cursorColor = Color(0xFFF5E0DC),
            ansiColors = listOf(
                Color(0xFF45475A), // 0 Black
                Color(0xFFF38BA8), // 1 Red
                Color(0xFFA6E3A1), // 2 Green
                Color(0xFFF9E2AF), // 3 Yellow
                Color(0xFF89B4FA), // 4 Blue
                Color(0xFFF5C2E7), // 5 Pink/Magenta
                Color(0xFF94E2D5), // 6 Teal/Cyan
                Color(0xFFBAC2DE), // 7 Subtext1
                Color(0xFF585B70), // 8 Surface2
                Color(0xFFF38BA8), // 9 Bright Red
                Color(0xFFA6E3A1), // 10 Bright Green
                Color(0xFFF9E2AF), // 11 Bright Yellow
                Color(0xFF89B4FA), // 12 Bright Blue
                Color(0xFFF5C2E7), // 13 Bright Magenta
                Color(0xFF94E2D5), // 14 Bright Teal
                Color(0xFFA6ADC8)  // 15 Subtext0
            )
        )

        val TokyoNight = TerminalTheme(
            id = "tokyo_night",
            name = "Tokyo Night",
            description = "Tema ikonik NeoVim dengan warna malam Tokyo",
            foreground = Color(0xFFA9B1D6),
            background = Color(0xFF1A1B26),
            cursorColor = Color(0xFFC0CAF5),
            ansiColors = listOf(
                Color(0xFF15161E), // 0 Black
                Color(0xFFF7768E), // 1 Red
                Color(0xFF9ECE6A), // 2 Green
                Color(0xFFE0AF68), // 3 Yellow
                Color(0xFF7AA2F7), // 4 Blue
                Color(0xFFBB9AF7), // 5 Magenta
                Color(0xFF7DCFFF), // 6 Cyan
                Color(0xFFA9B1D6), // 7 White
                Color(0xFF414868), // 8 Bright Black
                Color(0xFFF7768E), // 9 Bright Red
                Color(0xFF9ECE6A), // 10 Bright Green
                Color(0xFFE0AF68), // 11 Bright Yellow
                Color(0xFF7AA2F7), // 12 Bright Blue
                Color(0xFFBB9AF7), // 13 Bright Magenta
                Color(0xFF7DCFFF), // 14 Bright Cyan
                Color(0xFFC0CAF5)  // 15 Bright White
            )
        )

        val Dracula = TerminalTheme(
            id = "dracula",
            name = "Dracula Dark",
            description = "Tema kontras tinggi legendaris dengan aksen ungu pink",
            foreground = Color(0xFFF8F8F2),
            background = Color(0xFF282A36),
            cursorColor = Color(0xFFFF79C6),
            ansiColors = listOf(
                Color(0xFF21222C), // 0 Black
                Color(0xFFFF5555), // 1 Red
                Color(0xFF50FA7B), // 2 Green
                Color(0xFFF1FA8C), // 3 Yellow
                Color(0xFFBD93F9), // 4 Purple
                Color(0xFFFF79C6), // 5 Magenta
                Color(0xFF8BE9FD), // 6 Cyan
                Color(0xFFBFBFBF), // 7 White
                Color(0xFF4D4D4D), // 8 Bright Black
                Color(0xFFFF6E6E), // 9 Bright Red
                Color(0xFF69FF94), // 10 Bright Green
                Color(0xFFFFFFA5), // 11 Bright Yellow
                Color(0xFFD6ACFF), // 12 Bright Purple
                Color(0xFFFF92DF), // 13 Bright Magenta
                Color(0xFFA4FFFF), // 14 Bright Cyan
                Color(0xFFFFFFFF)  // 15 Bright White
            )
        )

        val GruvboxDark = TerminalTheme(
            id = "gruvbox_dark",
            name = "Gruvbox Dark",
            description = "Skema warna retro hangat yang ramah di mata",
            foreground = Color(0xFFEBDBB2),
            background = Color(0xFF282828),
            cursorColor = Color(0xFFFE8019),
            ansiColors = listOf(
                Color(0xFF282828), // 0 Black
                Color(0xFFCC241D), // 1 Red
                Color(0xFF98971A), // 2 Green
                Color(0xFFD79921), // 3 Yellow
                Color(0xFF458588), // 4 Blue
                Color(0xFFB16286), // 5 Magenta
                Color(0xFF689D6A), // 6 Cyan
                Color(0xFFA89984), // 7 White
                Color(0xFF928374), // 8 Bright Black
                Color(0xFFFB4934), // 9 Bright Red
                Color(0xFFB8BB26), // 10 Bright Green
                Color(0xFFFABD2F), // 11 Bright Yellow
                Color(0xFF83A598), // 12 Bright Blue
                Color(0xFFD3869B), // 13 Bright Magenta
                Color(0xFF8EC07C), // 14 Bright Cyan
                Color(0xFFEBDBB2)  // 15 Bright White
            )
        )

        val Nord = TerminalTheme(
            id = "nord",
            name = "Nord Arctic",
            description = "Palet warna es Arktik yang sejuk dan bersih",
            foreground = Color(0xFFECEFF4),
            background = Color(0xFF2E3440),
            cursorColor = Color(0xFF88C0D0),
            ansiColors = listOf(
                Color(0xFF3B4252), // 0 Black
                Color(0xFFBF616A), // 1 Red
                Color(0xFFA3BE8C), // 2 Green
                Color(0xFFEBCB8B), // 3 Yellow
                Color(0xFF81A1C1), // 4 Blue
                Color(0xFFB48EAD), // 5 Magenta
                Color(0xFF88C0D0), // 6 Cyan
                Color(0xFFE5E9F0), // 7 White
                Color(0xFF4C566A), // 8 Bright Black
                Color(0xFFBF616A), // 9 Bright Red
                Color(0xFFA3BE8C), // 10 Bright Green
                Color(0xFFEBCB8B), // 11 Bright Yellow
                Color(0xFF81A1C1), // 12 Bright Blue
                Color(0xFFB48EAD), // 13 Bright Magenta
                Color(0xFF8FBCBB), // 14 Bright Cyan
                Color(0xFFECEFF4)  // 15 Bright White
            )
        )

        val OledPureBlack = TerminalTheme(
            id = "oled_pure_black",
            name = "OLED Pure Black (Battery Saver)",
            description = "Hitam absolut #000000 untuk penghematan daya baterai AMOLED",
            foreground = Color(0xFFE6EDF3),
            background = Color(0xFF000000), // True 100% AMOLED black
            cursorColor = Color(0xFF7DFF4C),
            ansiColors = listOf(
                Color(0xFF000000), // 0 Black
                Color(0xFFFF7B72), // 1 Red
                Color(0xFF7EE787), // 2 Green
                Color(0xFFFFA657), // 3 Yellow
                Color(0xFF79C0FF), // 4 Blue
                Color(0xFFD2A8FF), // 5 Magenta
                Color(0xFF56D4DD), // 6 Cyan
                Color(0xFFF0F6FC), // 7 White
                Color(0xFF484F58), // 8 Bright Black
                Color(0xFFFFA198), // 9 Bright Red
                Color(0xFF56D364), // 10 Bright Green
                Color(0xFFE3B341), // 11 Bright Yellow
                Color(0xFF58A6FF), // 12 Bright Blue
                Color(0xFFBC8CFF), // 13 Bright Magenta
                Color(0xFF39C5CF), // 14 Bright Cyan
                Color(0xFFFFFFFF)  // 15 Bright White
            )
        )

        val ALL_THEMES = listOf(
            DevBoxEmerald,
            CatppuccinMocha,
            TokyoNight,
            Dracula,
            GruvboxDark,
            Nord,
            OledPureBlack
        )

        fun getThemeById(id: String): TerminalTheme {
            return ALL_THEMES.find { it.id == id } ?: DevBoxEmerald
        }
    }
}

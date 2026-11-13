package dev.nothingisland

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

/** Aktuelles Aussehen der Island. Wird aus den Einstellungen gesetzt, bevor die UI gezeichnet wird. */
object IslandTheme {
    @Volatile var accent: Color = Color(0xFFD71921)
    @Volatile var bg: Color = Color.Black
    @Volatile var fg: Color = Color.White
    @Volatile var font: FontFamily = FontFamily.Monospace

    val textGray: Color get() = lerp(bg, fg, 0.6f)
    val dotGray: Color get() = lerp(bg, fg, 0.22f)
    val hairline: Color get() = lerp(bg, fg, 0.20f)
    val buttonBg: Color get() = lerp(bg, fg, 0.10f)

    /** Name, Akzentfarbe, Hintergrund für die Auswahl in den Einstellungen. */
    val presets: List<Triple<String, String, Color>> = listOf(
        Triple("nothing", "NOTHING", Color(0xFFD71921)),
        Triple("white", "WEISS", Color(0xFFF2F2F2)),
        Triple("midnight", "MIDNIGHT", Color(0xFF4D8DFF)),
        Triple("mint", "MINT", Color(0xFF2EE6A6)),
        Triple("amber", "AMBER", Color(0xFFFFB020)),
        Triple("violet", "VIOLETT", Color(0xFFA66BFF)),
    )

    fun apply(s: IslandSettings) {
        val red = Color(0xFFD71921)
        when (s.theme) {
            "white" -> set(red, Color(0xFFF4F4F4), Color(0xFF111111))
            "midnight" -> set(Color(0xFF4D8DFF), Color(0xFF05070F), Color.White)
            "mint" -> set(Color(0xFF2EE6A6), Color.Black, Color.White)
            "amber" -> set(Color(0xFFFFB020), Color.Black, Color.White)
            "violet" -> set(Color(0xFFA66BFF), Color.Black, Color.White)
            "custom" -> {
                val accent = Color.hsv(s.themeHue.coerceIn(0f, 359f), 0.85f, 0.95f)
                when (s.themeBg) {
                    1 -> set(accent, Color(0xFF15161A), Color.White)
                    2 -> set(accent, Color(0xFFF4F4F4), Color(0xFF111111))
                    else -> set(accent, Color.Black, Color.White)
                }
            }
            else -> set(red, Color.Black, Color.White)
        }
        font = when (s.themeFont) {
            1 -> FontFamily.SansSerif
            2 -> FontFamily.Serif
            else -> FontFamily.Monospace
        }
    }

    private fun set(a: Color, b: Color, f: Color) {
        accent = a; bg = b; fg = f
    }

    val signature: String get() = "${accent.value}-${bg.value}-${fg.value}-${font.hashCode()}"
}

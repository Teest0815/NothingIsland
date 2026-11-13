package dev.nothingisland

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily

/** Aktuelles Aussehen der Island. Wird aus den Einstellungen gesetzt, bevor die UI gezeichnet wird. */
object IslandTheme {
    @Volatile var accent: Color = Color(0xFFD71921)
    @Volatile var bg: Color = Color.Black
    @Volatile var fg: Color = Color.White
    @Volatile var font: FontFamily = FontFamily.Monospace

    /** Schriftfarbe auf einer Fläche in Akzentfarbe: dunkel bei hellem Akzent, sonst weiß. */
    val onAccent: Color get() = if (accent.luminance() > 0.45f) Color(0xFF101010) else Color.White

    /** Akzentfarbe als Schrift auf dem Island-Hintergrund, bei zu wenig Kontrast Richtung Schriftfarbe gemischt. */
    val accentText: Color get() = accentOn(bg)

    fun accentOn(bg: Color): Color {
        var t = 0f
        var c = accent
        val other = if (bg.luminance() > 0.5f) Color.Black else Color.White
        while (contrast(c, bg) < 3.2f && t < 1f) {
            t += 0.1f
            c = lerp(accent, other, t)
        }
        return c
    }

    private fun contrast(a: Color, b: Color): Float {
        val l1 = a.luminance() + 0.05f
        val l2 = b.luminance() + 0.05f
        return if (l1 > l2) l1 / l2 else l2 / l1
    }

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
                val accent = Color.hsv(s.themeHue.coerceIn(0f, 359f), s.themeSat.coerceIn(0f, 1f), s.themeVal.coerceIn(0.15f, 1f))
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

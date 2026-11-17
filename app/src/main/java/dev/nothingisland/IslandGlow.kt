package dev.nothingisland

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.sign
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import android.media.session.PlaybackState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class GlowSpec(
    val color: Color,
    /** Ändert sich der Schlüssel, blitzt das Leuchten kurz stärker auf. */
    val key: Any,
    val flash: Boolean,
    val period: Int,
    val lo: Float,
    val hi: Float,
)

/** Feste Farbe für bekannte Apps, sonst stabile Farbe aus dem Namen. */
internal fun appGlowColor(app: String): Color {
    val n = app.lowercase()
    return when {
        "whatsapp" in n -> Color(0xFF25D366)
        "telegram" in n -> Color(0xFF2AABEE)
        "signal" in n -> Color(0xFF3A76F0)
        "instagram" in n -> Color(0xFFE1306C)
        "gmail" in n || "mail" in n -> Color(0xFFEA4335)
        "messenger" in n -> Color(0xFF0084FF)
        "discord" in n -> Color(0xFF5865F2)
        "outlook" in n -> Color(0xFF0078D4)
        "nachricht" in n || "message" in n || "sms" in n -> Color(0xFF34C759)
        "home assistant" in n -> Color(0xFF18BCF2)
        else -> Color.hsv((n.hashCode().mod(360)).toFloat(), 0.65f, 1f)
    }
}

/** Kräftigste Farbe des Covers, leicht aufgehellt. */
internal fun coverGlowColor(art: ImageBitmap): Color = runCatching {
    val src = art.asAndroidBitmap()
    val bm = android.graphics.Bitmap.createScaledBitmap(src.copy(android.graphics.Bitmap.Config.ARGB_8888, false), 12, 12, true)
    val hsv = FloatArray(3)
    var best = -1f
    var bh = 0f
    for (x in 0 until 12) for (y in 0 until 12) {
        android.graphics.Color.colorToHSV(bm.getPixel(x, y), hsv)
        val score = hsv[1] * hsv[2]
        if (score > best) { best = score; bh = hsv[0] }
    }
    Color.hsv(bh, 0.75f, 1f)
}.getOrDefault(NothingRed)

@Composable
internal fun GlowLayer(spec: GlowSpec, strength: Float, sizeDp: Int, corner: Dp, modifier: Modifier) {
    // Phase mit ca. 30 Bildern pro Sekunde statt Bildschirmrate: Der Schein ist weich, das spart Akku bei dauerhaftem Leuchten.
    val ph = remember { mutableFloatStateOf(0f) }
    val screenOn by IslandBus.screenOn.collectAsState()
    LaunchedEffect(spec.period, screenOn) {
        if (!screenOn) return@LaunchedEffect
        val t0 = withFrameMillis { it }
        while (true) {
            withFrameMillis { ph.floatValue = ((it - t0) % spec.period).toFloat() / spec.period }
            delay(30)
        }
    }
    val flash = remember(spec.key) { Animatable(if (spec.flash) 1f else 0f) }
    LaunchedEffect(spec.key) {
        if (spec.flash) {
            flash.snapTo(1f)
            flash.animateTo(0f, androidx.compose.animation.core.tween(1300))
        }
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(500)) }
    Box(
        modifier.drawBehind {
            val breathe = 0.5f + 0.5f * kotlin.math.sin(ph.floatValue * 2f * Math.PI.toFloat())
            val inten = (spec.lo + (spec.hi - spec.lo) * breathe) * (1f + 0.9f * flash.value) * appear.value * strength
            if (inten <= 0.01f) return@drawBehind
            val reach = sizeDp.dp.toPx()
            val layers = 10
            val c = corner.toPx()
            for (i in layers downTo 1) {
                val g = reach * i / layers
                // Stärker nach unten: Der Schein kommt von unter der Island.
                drawRoundRect(
                    color = spec.color.copy(alpha = (inten * 0.105f).coerceIn(0f, 1f)),
                    // Rundum, nach unten etwas weiter: auch über der kleinen Island sieht man den Schein.
                    topLeft = androidx.compose.ui.geometry.Offset(-g, -g * 0.9f),
                    size = androidx.compose.ui.geometry.Size(size.width + 2f * g, size.height + g * 1.9f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(c + g),
                )
            }
        },
    )
}

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

internal val WORKOUT_ORDER = listOf("bpm", "dist", "pace", "speed", "power", "cad", "kcal")

/** Gewählter Wert, sonst der erste vorhandene. */
internal fun workoutValue(e: IslandEvent, pref: String): String? =
    e.metrics[pref] ?: WORKOUT_ORDER.firstNotNullOfOrNull { e.metrics[it] }

/** Alle erkannten Werte in einer Zeile, z. B. "142 BPM · 5.2 KM". */
internal fun workoutLine(e: IslandEvent): String =
    WORKOUT_ORDER.mapNotNull { e.metrics[it] }.take(4).joinToString("  ·  ") { it.uppercase() }

internal enum class Turn { LEFT, RIGHT, STRAIGHT, UTURN, ROUNDABOUT, NONE }

internal val DIST_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s?(km|mi|ft|yd|m)\b""", RegexOption.IGNORE_CASE)

/** Nur die Entfernung, z. B. "25 m". Findet sich keine, wird der Titel gezeigt. */
internal fun navDistance(e: IslandEvent): String =
    DIST_REGEX.find(e.title)?.value
        ?: DIST_REGEX.find(e.text)?.value
        ?: e.title.ifBlank { e.app }

/** Liest die Abbiegerichtung aus Titel/Text (Deutsch und Englisch). Das zuerst genannte Wort gewinnt. */
internal fun navTurn(e: IslandEvent): Turn {
    val t = (e.title + " " + e.text).lowercase()
    fun first(vararg words: String): Int =
        words.map { t.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
    if ("wenden" in t || "u-turn" in t || "u turn" in t) return Turn.UTURN
    if ("kreisverkehr" in t || "roundabout" in t) return Turn.ROUNDABOUT
    val l = first("links", "left")
    val r = first("rechts", "right")
    if (l >= 0 && (r < 0 || l < r)) return Turn.LEFT
    if (r >= 0) return Turn.RIGHT
    if ("geradeaus" in t || "straight" in t || "weiter" in t || "continue" in t) return Turn.STRAIGHT
    return Turn.NONE
}

@Composable
internal fun TurnArrow(turn: Turn, size: Dp, color: Color = Fg) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Für "links" wird die Rechts-Form gespiegelt.
        val mx: (Float) -> Float = if (turn == Turn.LEFT) { x -> 1f - x } else { x -> x }
        val path = Path()
        when (turn) {
            Turn.STRAIGHT -> {
                path.moveTo(0.5f * w, 0.92f * w); path.lineTo(0.5f * w, 0.12f * w)
                path.moveTo(0.22f * w, 0.38f * w); path.lineTo(0.5f * w, 0.1f * w); path.lineTo(0.78f * w, 0.38f * w)
            }
            Turn.RIGHT, Turn.LEFT -> {
                path.moveTo(mx(0.28f) * w, 0.92f * w); path.lineTo(mx(0.28f) * w, 0.4f * w); path.lineTo(mx(0.82f) * w, 0.4f * w)
                path.moveTo(mx(0.62f) * w, 0.18f * w); path.lineTo(mx(0.84f) * w, 0.4f * w); path.lineTo(mx(0.62f) * w, 0.62f * w)
            }
            Turn.UTURN -> {
                path.moveTo(0.7f * w, 0.92f * w); path.lineTo(0.7f * w, 0.38f * w)
                path.cubicTo(0.7f * w, 0.08f * w, 0.3f * w, 0.08f * w, 0.3f * w, 0.38f * w)
                path.lineTo(0.3f * w, 0.78f * w)
                path.moveTo(0.12f * w, 0.6f * w); path.lineTo(0.3f * w, 0.8f * w); path.lineTo(0.48f * w, 0.6f * w)
            }
            Turn.ROUNDABOUT -> {
                drawCircle(color, radius = 0.26f * w, center = Offset(0.5f * w, 0.4f * w), style = style)
                path.moveTo(0.5f * w, 0.96f * w); path.lineTo(0.5f * w, 0.68f * w)
            }
            Turn.NONE -> Unit
        }
        drawPath(path, color, style = style)
    }
}

@Composable
internal fun Equalizer(playing: Boolean, sc: Float) {
    val lp by IslandBus.lowPower.collectAsState()
    if (lp) {
        // Sparmodus: stehende Balken statt Dauer-Animation.
        val hs = floatArrayOf(0.55f, 0.95f, 0.4f, 0.75f)
        Row(
            modifier = Modifier.height((12f * sc).dp),
            horizontalArrangement = Arrangement.spacedBy((2f * sc).dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hs.forEach { h ->
                Box(
                    Modifier
                        .width((2.5f * sc).dp)
                        .fillMaxHeight(if (playing) h else 0.2f)
                        .clip(RoundedCornerShape(50))
                        .background(NothingRed),
                )
            }
        }
        return
    }
    val t = rememberInfiniteTransition(label = "eq")
    val periods = intArrayOf(520, 380, 610, 450)
    Row(
        modifier = Modifier.height((12f * sc).dp),
        horizontalArrangement = Arrangement.spacedBy((2f * sc).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        periods.forEachIndexed { i, p ->
            val h by t.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(p, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width((2.5f * sc).dp)
                    .fillMaxHeight()
                    // Nur im Zeichnen skalieren: keine Neuberechnung des Layouts pro Bild.
                    .graphicsLayer {
                        scaleY = if (playing) h else 0.2f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(NothingRed),
            )
        }
    }
}

@Composable
internal fun LivePulse(sc: Float) {
    val lp by IslandBus.lowPower.collectAsState()
    if (lp) {
        Box(Modifier.size((5f * sc).dp).clip(CircleShape).background(NothingRed))
        return
    }
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(Modifier.size((5f * sc).dp).graphicsLayer { alpha = a }.clip(CircleShape).background(NothingRed))
}

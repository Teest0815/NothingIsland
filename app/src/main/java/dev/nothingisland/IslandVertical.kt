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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
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

// ---------------------------------------------------------------- Querformat: senkrechte Pille

/** Text als einzelne, aufrechte Buchstaben untereinander. Passt er nicht, wird er mit "…" gekürzt. */
@Composable
internal fun VText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() } * 0.84f
        val n = (maxHeight / lineH).toInt().coerceAtLeast(1)
        val cps = text.trim().codePoints().toArray().map { String(Character.toChars(it)) }
        val shown = if (cps.size > n) cps.take(maxOf(n - 1, 0)) + "\u2026" else cps
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            shown.forEach { ch ->
                if (ch.isBlank()) Spacer(Modifier.height(lineH * 0.5f))
                else Text(
                    ch, color = color, fontSize = size, fontWeight = weight,
                    fontFamily = if (mono) Mono else FontFamily.Default,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.height(lineH).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                )
            }
        }
    }
}

/** Ruhezustand senkrecht: drei Punkte untereinander, der rote auf Kamerahöhe. */
@Composable
internal fun IdleColumn(sc: Float) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy((6f * sc).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size((4f * sc).dp).clip(CircleShape).background(DotGray))
        Box(Modifier.size((6f * sc).dp).clip(CircleShape).background(NothingRed))
        Box(Modifier.size((4f * sc).dp).clip(CircleShape).background(DotGray))
    }
}

/** Oben Inhalt, in der Mitte Platz für die Kamera, unten Inhalt. */
@Composable
internal fun SplitCol(
    sc: Float,
    gap: Float,
    modifier: Modifier,
    bottomAlignStart: Boolean = false,
    top: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    bottom: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(vertical = (11f * sc).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy((4f * sc).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = top,
        )
        Spacer(Modifier.height(gap.dp))
        androidx.compose.foundation.layout.Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = if (bottomAlignStart) Arrangement.Top else Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

@Composable
internal fun CompactColumn(e: IslandEvent, sc: Float, gap: Float, eq: Boolean, workoutMetric: String, modifier: Modifier) {
    val nav = e.kind == Kind.NAVIGATION
    val workout = e.kind == Kind.WORKOUT
    val paused = (e.kind == Kind.MEDIA && !e.playing) || (e.kind == Kind.TIMER && e.chronoBase == null)
    val timeColor by androidx.compose.animation.animateColorAsState(if (paused) TextGray else Fg, tween(250), label = "timeColor")
    val clock = chronoText(e)
    SplitCol(sc, gap, modifier,
        top = {
            PillIcon(e, (14f * sc).dp, pulse = e.kind == Kind.CALL && e.chronoBase == null)
            val t = when {
                workout -> clock ?: e.metrics["time"] ?: e.title.ifBlank { e.app }
                nav -> navDistance(e)
                else -> e.title.ifBlank { e.app }
            }
            VText(t, (if (nav || workout) 12f else 11f).let { (it * sc).sp }, Fg, Modifier.weight(1f, fill = false), mono = nav || workout,
                weight = if (nav || workout) FontWeight.Medium else FontWeight.Normal)
        },
        bottom = {
            val turn = if (nav) navTurn(e) else Turn.NONE
            val wv = if (workout) workoutValue(e, workoutMetric) else null
            val fs = (12f * sc).sp
            when {
                workout -> if (wv != null) VText(wv.uppercase(), fs, if (workoutMetric == "bpm") AccentText else Fg, Modifier.weight(1f, fill = false), mono = true, weight = FontWeight.Medium) else LivePulse(sc)
                nav && turn != Turn.NONE -> TurnArrow(turn, (16f * sc).dp)
                clock != null || (e.kind == Kind.MEDIA && e.rightText != null) -> {
                    val t = clock ?: e.rightText ?: ""
                    VText(t, fs, timeColor, Modifier.weight(1f, fill = false), mono = true, weight = FontWeight.Medium)
                    if (eq && e.kind == Kind.MEDIA) Equalizer(e.playing || e.chronoBase != null, sc)
                }
                e.rightText != null -> VText(e.rightText, fs, timeColor, Modifier.weight(1f, fill = false), mono = true, weight = FontWeight.Medium)
                else -> LivePulse(sc)
            }
        },
    )
}

@Composable
internal fun MessageColumn(e: IslandEvent, sc: Float, gap: Float, modifier: Modifier) {
    val status = e.kind == Kind.STATUS
    SplitCol(sc, gap, modifier, bottomAlignStart = !status,
        top = {
            PillIcon(e, (14f * sc).dp)
            VText(e.title.ifBlank { e.app }, (11f * sc).sp, Fg, Modifier.weight(1f, fill = false), weight = FontWeight.Medium)
        },
        bottom = {
            VText(
                if (e.textShort != null) e.textShort else e.text,
                (if (status) 12f else 11f).let { (it * sc).sp },
                if (status) Fg else TextGray,
                Modifier.weight(1f, fill = false), mono = status,
                weight = if (status) FontWeight.Medium else FontWeight.Normal,
            )
        },
    )
}

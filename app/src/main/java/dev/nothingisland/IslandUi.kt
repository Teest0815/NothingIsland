package dev.nothingisland

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val NothingRed = Color(0xFFD71921)
private val DotGray = Color(0xFF3A3A3A)
private val TextGray = Color(0xFF9A9A9A)
private val Hairline = Color(0xFF2A2A2A)

private const val AUTO_DISMISS_MS = 5000L

@Composable
fun IslandHost() {
    val event by IslandBus.current.collectAsState()
    val idleVisible by IslandBus.idleVisible.collectAsState()
    val expanded = event != null

    // Letztes Event merken, damit der Inhalt beim Einklappen nicht sofort verschwindet.
    var shown by remember { mutableStateOf<IslandEvent?>(null) }
    LaunchedEffect(event?.id) {
        event?.let {
            shown = it
            delay(AUTO_DISMISS_MS)
            IslandBus.dismiss()
        }
    }

    if (!expanded && !idleVisible) return

    val motion = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val width by animateDpAsState(if (expanded) 348.dp else 92.dp, motion, label = "w")
    val height by animateDpAsState(if (expanded) 84.dp else 30.dp, motion, label = "h")
    val corner by animateDpAsState(if (expanded) 38.dp else 15.dp, motion, label = "c")
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Hairline, shape)
            .clickable(enabled = expanded) {
                runCatching { event?.action?.send() }
                IslandBus.dismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!expanded) IdleDots()

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220, delayMillis = 120)),
            exit = fadeOut(tween(100)),
        ) {
            shown?.let { ExpandedContent(it) }
        }
    }
}

@Composable
private fun IdleDots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(DotGray))
        Box(Modifier.size(6.dp).clip(CircleShape).background(NothingRed))
        Box(Modifier.size(4.dp).clip(CircleShape).background(DotGray))
    }
}

@Composable
private fun ExpandedContent(event: IslandEvent) {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            DotRing(Modifier.size(48.dp))
            event.icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.app.uppercase(),
                color = NothingRed,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.title.isNotBlank()) {
                Text(
                    text = event.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (event.text.isNotBlank()) {
                Text(
                    text = event.text,
                    color = TextGray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Punktmatrix-Ring im Glyph-Stil mit umlaufendem Lichtpunkt. */
@Composable
private fun DotRing(modifier: Modifier = Modifier, dots: Int = 24) {
    val transition = rememberInfiniteTransition(label = "ring")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "head",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f - 3.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until dots) {
            val p = i / dots.toFloat()
            val a = (p * 2f * PI).toFloat() - (PI / 2).toFloat()
            // Abstand hinter dem Lichtpunkt (0 = genau am Kopf)
            val behind = ((head - p) + 1f) % 1f
            val glow = (1f - behind * 2.2f).coerceIn(0f, 1f)
            val color = if (glow > 0.02f) {
                androidx.compose.ui.graphics.lerp(DotGray, NothingRed, glow)
            } else DotGray
            drawCircle(
                color = color,
                radius = (1.3f + glow * 0.9f).dp.toPx(),
                center = Offset(c.x + r * cos(a), c.y + r * sin(a)),
            )
        }
    }
}

package dev.nothingisland

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val NothingRed = Color(0xFFD71921)
private val DotGray = Color(0xFF3A3A3A)
private val TextGray = Color(0xFF9A9A9A)
private val Hairline = Color(0xFF2A2A2A)
private val ButtonBg = Color(0xFF1C1C1C)

private enum class Mode { IDLE, COMPACT, ALERT, EXPANDED }

private const val BASE_BIG_W = 348f
private const val BASE_ALERT_H = 84f
private const val BASE_EXPANDED_H = 84f
private const val BASE_EXPANDED_ACTIONS_H = 134f
private const val BASE_IDLE_W = 92f
private const val BASE_COMPACT_W = 132f
private const val BASE_SMALL_H = 30f

@Composable
fun IslandHost() {
    val s by IslandBus.settings.collectAsState()
    val alert by IslandBus.current.collectAsState()
    val ongoingAll by IslandBus.ongoing.collectAsState()
    val preview by IslandBus.preview.collectAsState()
    val ongoing = if (s.showOngoing) ongoingAll else emptyList()

    var manual by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var lastAlert by remember { mutableStateOf<IslandEvent?>(null) }

    // Neue Benachrichtigung: aufklappen und nach X Sekunden wieder einklappen.
    LaunchedEffect(alert?.id) {
        alert?.let {
            lastAlert = it
            delay(s.alertSeconds * 1000L)
            IslandBus.dismiss()
        }
    }
    LaunchedEffect(ongoing.isEmpty()) { if (ongoing.isEmpty()) manual = false }
    // Vom Nutzer aufgeklappte Pille klappt nach 10 s ohne Interaktion von selbst zu.
    LaunchedEffect(manual, touch) {
        if (manual) {
            delay(10_000)
            manual = false
        }
    }

    val sel = selected.coerceIn(0, (ongoing.size - 1).coerceAtLeast(0))
    val current = ongoing.getOrNull(sel)
    val mode = when {
        alert != null -> Mode.ALERT
        manual && current != null -> Mode.EXPANDED
        current != null -> Mode.COMPACT
        else -> Mode.IDLE
    }
    if (mode == Mode.IDLE && !s.idleVisible && !preview) return

    val sc = s.scale
    val screenW = LocalConfiguration.current.screenWidthDp
    val bigW = minOf(BASE_BIG_W * sc, screenW - 16f)
    val hasActions = current?.actions?.isNotEmpty() == true
    val expandedH = (if (hasActions) BASE_EXPANDED_ACTIONS_H else BASE_EXPANDED_H) * sc
    val idleW = BASE_IDLE_W * sc * s.widthFactor
    val compactW = BASE_COMPACT_W * sc * s.widthFactor
    val smallH = BASE_SMALL_H * sc

    val targetW = when (mode) {
        Mode.IDLE -> idleW
        Mode.COMPACT -> compactW
        else -> bigW
    }
    val targetH = when (mode) {
        Mode.IDLE, Mode.COMPACT -> smallH
        Mode.ALERT -> BASE_ALERT_H * sc
        Mode.EXPANDED -> expandedH
    }
    val targetCorner = when (mode) {
        Mode.IDLE, Mode.COMPACT -> smallH / 2f
        else -> 38f * sc
    }

    val motion = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val width by animateDpAsState(targetW.dp, motion, label = "w")
    val height by animateDpAsState(targetH.dp, motion, label = "h")
    val corner by animateDpAsState(targetCorner.dp, motion, label = "c")
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Hairline, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = mode != Mode.IDLE,
            ) {
                when (mode) {
                    Mode.ALERT -> {
                        runCatching { alert?.action?.send() }
                        IslandBus.dismiss()
                    }
                    Mode.COMPACT -> {
                        manual = true
                        touch++
                    }
                    Mode.EXPANDED -> manual = false
                    Mode.IDLE -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = mode, animationSpec = tween(180), label = "mode") { m ->
            when (m) {
                Mode.IDLE -> IdleDots(sc)
                Mode.COMPACT -> current?.let {
                    CompactContent(it, ongoing.size, sc, Modifier.requiredSize(compactW.dp, smallH.dp))
                }
                Mode.ALERT -> (alert ?: lastAlert)?.let {
                    AlertContent(it, sc, Modifier.requiredSize(bigW.dp, (BASE_ALERT_H * sc).dp))
                }
                Mode.EXPANDED -> current?.let {
                    OngoingExpanded(
                        e = it,
                        count = ongoing.size,
                        index = sel,
                        sc = sc,
                        onCycle = {
                            selected = (sel + 1) % ongoing.size
                            touch++
                        },
                        onAction = { touch++ },
                        modifier = Modifier.requiredSize(bigW.dp, expandedH.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleDots(sc: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy((6f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size((4f * sc).dp).clip(CircleShape).background(DotGray))
        Box(Modifier.size((6f * sc).dp).clip(CircleShape).background(NothingRed))
        Box(Modifier.size((4f * sc).dp).clip(CircleShape).background(DotGray))
    }
}

@Composable
private fun SmallIcon(icon: ImageBitmap?, size: Dp) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(size),
        )
    } else {
        Box(Modifier.size(size / 2).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun CompactContent(e: IslandEvent, count: Int, sc: Float, modifier: Modifier) {
    Row(
        modifier = modifier.padding(horizontal = (11f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((7f * sc).dp),
    ) {
        SmallIcon(e.icon, (14f * sc).dp)
        if (e.chronoBase != null) {
            Chrono(e, (12f * sc).sp, Modifier.weight(1f))
        } else {
            Text(
                text = e.title.ifBlank { e.app },
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = (11f * sc).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (count > 1) {
            Text(
                text = "$count",
                color = NothingRed,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (10f * sc).sp,
            )
        } else {
            Box(Modifier.size((5f * sc).dp).clip(CircleShape).background(NothingRed))
        }
    }
}

private fun formatClock(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

@Composable
private fun Chrono(
    e: IslandEvent,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(e.chronoBase) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val base = e.chronoBase ?: return
    val ms = if (e.countDown) base - now else now - base
    Text(
        text = formatClock(ms),
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = size,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Aufgeklappte Meldung bei neuer Benachrichtigung. */
@Composable
private fun AlertContent(event: IslandEvent, sc: Float, modifier: Modifier) {
    Row(
        modifier = modifier.padding(horizontal = (18f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size((48f * sc).dp)) {
            DotRing(Modifier.fillMaxSize())
            event.icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size((20f * sc).dp),
                )
            }
        }
        Spacer(Modifier.width((14f * sc).dp))
        Column(modifier = Modifier.weight(1f)) {
            AppLabel(event.app, sc)
            if (event.title.isNotBlank()) {
                Text(
                    text = event.title,
                    color = Color.White,
                    fontSize = (14f * sc).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (event.text.isNotBlank()) {
                Text(
                    text = event.text,
                    color = TextGray,
                    fontSize = (12f * sc).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AppLabel(app: String, sc: Float) {
    Text(
        text = app.uppercase(),
        color = NothingRed,
        fontFamily = FontFamily.Monospace,
        fontSize = (10f * sc).sp,
        letterSpacing = 1.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Aufgeklappte laufende Aktivität (Musik, Timer ...) mit Aktions-Buttons. */
@Composable
private fun OngoingExpanded(
    e: IslandEvent,
    count: Int,
    index: Int,
    sc: Float,
    onCycle: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (18f * sc).dp, vertical = (12f * sc).dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size((48f * sc).dp)) {
                    DotRing(Modifier.fillMaxSize())
                    e.icon?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size((20f * sc).dp),
                        )
                    }
                }
                Spacer(Modifier.width((14f * sc).dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppLabel(e.app, sc)
                    if (e.chronoBase != null) {
                        Chrono(e, (22f * sc).sp)
                        if (e.title.isNotBlank()) {
                            Text(
                                text = e.title,
                                color = TextGray,
                                fontSize = (11f * sc).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        if (e.title.isNotBlank()) {
                            Text(
                                text = e.title,
                                color = Color.White,
                                fontSize = (14f * sc).sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (e.text.isNotBlank()) {
                            Text(
                                text = e.text,
                                color = TextGray,
                                fontSize = (12f * sc).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (count > 1) {
                    Text(
                        text = "${index + 1}/$count",
                        color = NothingRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (11f * sc).sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onCycle)
                            .padding(horizontal = (8f * sc).dp, vertical = (6f * sc).dp),
                    )
                }
            }
            if (e.actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((8f * sc).dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    e.actions.forEach { ActionButton(it, sc, onAction) }
                }
            }
        }
        e.progress?.let { p ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = (28f * sc).dp)
                    .padding(bottom = (5f * sc).dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(DotGray),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(NothingRed),
                )
            }
        }
    }
}

private val dangerWords = listOf(
    "stop", "stopp", "beend", "cancel", "abbrech", "dismiss", "auflegen", "hang up", "decline", "ablehn",
)

@Composable
private fun ActionButton(a: IslandAction, sc: Float, onUsed: () -> Unit) {
    val danger = remember(a.title) {
        val t = a.title.lowercase()
        dangerWords.any { it in t }
    }
    Box(
        modifier = Modifier
            .height((38f * sc).dp)
            .defaultMinSize(minWidth = (44f * sc).dp)
            .clip(RoundedCornerShape(50))
            .background(if (danger) NothingRed else ButtonBg)
            .clickable {
                a.onClick?.invoke()
                a.intent?.let { runCatching { it.send() } }
                onUsed()
            }
            .padding(horizontal = (12f * sc).dp),
        contentAlignment = Alignment.Center,
    ) {
        if (a.icon != null) {
            Image(
                bitmap = a.icon,
                contentDescription = a.title,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size((20f * sc).dp),
            )
        } else {
            Text(
                text = a.title.uppercase(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = (11f * sc).sp,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
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
            val behind = ((head - p) + 1f) % 1f
            val glow = (1f - behind * 2.2f).coerceIn(0f, 1f)
            val color = if (glow > 0.02f) lerp(DotGray, NothingRed, glow) else DotGray
            drawCircle(
                color = color,
                radius = (1.3f + glow * 0.9f).dp.toPx(),
                center = Offset(c.x + r * cos(a), c.y + r * sin(a)),
            )
        }
    }
}

package dev.nothingisland

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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

private enum class Mode { IDLE, COMPACT, MESSAGE, ALERT, EXPANDED }

private const val BASE_BIG_W = 348f
private const val BASE_ALERT_H = 84f
private const val BASE_EXPANDED_H = 84f
private const val BASE_EXPANDED_ACTIONS_H = 134f
private const val BASE_IDLE_W = 92f
/** Halbe Breite der kleinen Pille ohne Kamera-Lücke (links und rechts je gleich viel). */
private const val BASE_COMPACT_SIDE = 82f
private const val BASE_MESSAGE_SIDE = 125f
private const val BASE_SMALL_H = 30f

@Composable
fun IslandHost() {
    val s by IslandBus.settings.collectAsState()
    val alert by IslandBus.current.collectAsState()
    val ongoingAll by IslandBus.ongoing.collectAsState()
    val primaryKey by IslandBus.primaryKey.collectAsState()
    val manual by IslandBus.expanded.collectAsState()
    val preview by IslandBus.preview.collectAsState()
    val ongoing = if (s.showOngoing) ongoingAll else emptyList()

    var touch by remember { mutableIntStateOf(0) }
    var lastAlert by remember { mutableStateOf<IslandEvent?>(null) }
    var lingering by remember { mutableStateOf<IslandEvent?>(null) }
    var lastCircle by remember { mutableStateOf<IslandEvent?>(null) }

    // Neue Meldung: aufklappen, nach X Sekunden (oder Tippen außerhalb) in die kleine Pille schrumpfen.
    LaunchedEffect(alert?.id) {
        val a = alert ?: return@LaunchedEffect
        lastAlert = a
        try {
            delay(s.alertSeconds * 1000L)
        } finally {
            lingering = a // bleibt noch eine Weile als Text in der kleinen Pille stehen
        }
        IslandBus.dismiss()
    }
    LaunchedEffect(lingering?.id) {
        if (lingering != null) {
            delay(8_000)
            lingering = null
        }
    }
    LaunchedEffect(ongoing.isEmpty()) { if (ongoing.isEmpty()) IslandBus.setExpanded(false) }
    // Vom Nutzer aufgeklappte Pille klappt nach 10 s ohne Interaktion von selbst zu.
    LaunchedEffect(manual, touch) {
        if (manual) {
            delay(10_000)
            IslandBus.setExpanded(false)
        }
    }

    val current = ongoing.firstOrNull { it.key == primaryKey } ?: ongoing.lastOrNull()
    val mode = when {
        alert != null -> Mode.ALERT
        manual && current != null -> Mode.EXPANDED
        lingering != null -> Mode.MESSAGE
        current != null -> Mode.COMPACT
        else -> Mode.IDLE
    }
    if (mode == Mode.IDLE && !s.idleVisible && !preview) return

    // Zweite Aktivität als Kreis neben der Pille (wie bei Apple).
    val circleItem = when (mode) {
        Mode.COMPACT -> ongoing.lastOrNull { it.key != current?.key }
        Mode.MESSAGE -> current
        else -> null
    }
    SideEffect { if (circleItem != null) lastCircle = circleItem }

    val sc = s.scale
    val gap = s.cameraGap.toFloat()
    val screenW = LocalConfiguration.current.screenWidthDp
    val bigW = minOf(BASE_BIG_W * sc, screenW - 16f)
    val hasActions = current?.actions?.isNotEmpty() == true
    val expandedH = (if (hasActions) BASE_EXPANDED_ACTIONS_H else BASE_EXPANDED_H) * sc
    val idleW = BASE_IDLE_W * sc * s.widthFactor
    val compactW = minOf(2f * BASE_COMPACT_SIDE * sc * s.widthFactor + gap, screenW - 16f)
    val messageW = minOf(2f * BASE_MESSAGE_SIDE * sc * s.widthFactor + gap, screenW - 16f)
    val smallH = BASE_SMALL_H * sc

    val targetW = when (mode) {
        Mode.IDLE -> idleW
        Mode.COMPACT -> compactW
        Mode.MESSAGE -> messageW
        else -> bigW
    }
    val targetH = when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH
        Mode.ALERT -> BASE_ALERT_H * sc
        Mode.EXPANDED -> expandedH
    }
    val targetCorner = when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH / 2f
        else -> 38f * sc
    }

    val motion = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val width by animateDpAsState(targetW.dp, motion, label = "w")
    val height by animateDpAsState(targetH.dp, motion, label = "h")
    val corner by animateDpAsState(targetCorner.dp, motion, label = "c")
    // Gleich großer freier Platz links und rechts, damit die Pille mittig (über der Kamera) bleibt.
    val slot by animateDpAsState(
        if (circleItem != null) (smallH + 6f * sc).dp else 0.dp,
        motion,
        label = "slot",
    )
    val shape = RoundedCornerShape(corner)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(slot))

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
                        Mode.MESSAGE -> {
                            runCatching { lingering?.action?.send() }
                            lingering = null
                        }
                        Mode.COMPACT -> {
                            IslandBus.setExpanded(true)
                            touch++
                        }
                        Mode.EXPANDED -> IslandBus.setExpanded(false)
                        Mode.IDLE -> Unit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = mode, animationSpec = tween(180), label = "mode") { m ->
                when (m) {
                    Mode.IDLE -> IdleDots(sc)
                    Mode.COMPACT -> current?.let {
                        CompactContent(it, sc, gap, Modifier.requiredSize(compactW.dp, smallH.dp))
                    }
                    Mode.MESSAGE -> lingering?.let {
                        MessageContent(it, sc, gap, Modifier.requiredSize(messageW.dp, smallH.dp))
                    }
                    Mode.ALERT -> (alert ?: lastAlert)?.let {
                        AlertContent(it, sc, Modifier.requiredSize(bigW.dp, (BASE_ALERT_H * sc).dp))
                    }
                    Mode.EXPANDED -> current?.let { cur ->
                        OngoingExpanded(
                            e = cur,
                            count = ongoing.size,
                            index = ongoing.indexOf(cur).coerceAtLeast(0),
                            sc = sc,
                            onCycle = {
                                val i = ongoing.indexOf(cur)
                                IslandBus.setPrimary(ongoing[(i + 1) % ongoing.size].key)
                                touch++
                            },
                            onAction = { touch++ },
                            modifier = Modifier.requiredSize(bigW.dp, expandedH.dp),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier.width(slot).height(height),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AnimatedVisibility(
                visible = circleItem != null,
                enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.4f),
                exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.4f),
            ) {
                (circleItem ?: lastCircle)?.let { c ->
                    CircleBubble(c, sc, smallH) {
                        lingering = null
                        IslandBus.setPrimary(c.key)
                        touch++
                    }
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

/** Kleine Pille: links Symbol + Text/Zeit direkt am Symbol, in der Mitte Platz für die Kamera. */
@Composable
private fun SplitRow(
    sc: Float,
    gap: Float,
    modifier: Modifier,
    rightAlignStart: Boolean = false,
    left: @Composable RowScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = (11f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((6f * sc).dp),
            content = left,
        )
        Spacer(Modifier.width(gap.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (rightAlignStart) Alignment.CenterStart else Alignment.CenterEnd,
            content = right,
        )
    }
}

@Composable
private fun CompactContent(e: IslandEvent, sc: Float, gap: Float, modifier: Modifier) {
    SplitRow(
        sc = sc,
        gap = gap,
        modifier = modifier,
        left = {
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
        },
        right = {
            Box(Modifier.size((5f * sc).dp).clip(CircleShape).background(NothingRed))
        },
    )
}

/** Nach dem Verkleinern einer Meldung: Absender links, Nachricht rechts der Kamera. */
@Composable
private fun MessageContent(e: IslandEvent, sc: Float, gap: Float, modifier: Modifier) {
    SplitRow(
        sc = sc,
        gap = gap,
        modifier = modifier,
        rightAlignStart = true,
        left = {
            SmallIcon(e.icon, (14f * sc).dp)
            Text(
                text = e.title.ifBlank { e.app },
                color = Color.White,
                fontSize = (11f * sc).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        },
        right = {
            Text(
                text = e.text,
                color = TextGray,
                fontSize = (11f * sc).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Zweite laufende Aktivität als Kreis neben der Pille. Antippen holt sie in die Pille. */
@Composable
private fun CircleBubble(e: IslandEvent, sc: Float, size: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .requiredSize(size.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .border(1.dp, Hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SmallIcon(e.icon, (14f * sc).dp)
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

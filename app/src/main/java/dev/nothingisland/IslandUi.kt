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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.sign
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

val NothingRed = Color(0xFFD71921)
private val DotGray = Color(0xFF3A3A3A)
private val TextGray = Color(0xFF9A9A9A)
private val Hairline = Color(0xFF2A2A2A)
private val ButtonBg = Color(0xFF1C1C1C)

private enum class Mode { IDLE, COMPACT, MESSAGE, ALERT, EXPANDED, QUICK }

/** Reserve um die Pille, damit Überschwingen der Feder nicht abgeschnitten wird. */
private const val WINDOW_MARGIN = 24f
private const val BASE_ALERT_H = 84f
private const val BASE_EXPANDED_H = 84f
private const val BASE_EXPANDED_ACTIONS_H = 134f
private const val BASE_EXPANDED_MEDIA_H = 156f
private const val BASE_IDLE_W = 92f
private const val BASE_QUICK_H = 122f
/** Halbe Breite der kleinen Pille ohne Kamera-Lücke (links und rechts je gleich viel). */
private const val BASE_COMPACT_SIDE = 82f
private const val BASE_MESSAGE_SIDE = 125f
private const val BASE_SMALL_H = 30f

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IslandHost() {
    val s by IslandBus.settings.collectAsState()
    val alert by IslandBus.current.collectAsState()
    val ongoingAll by IslandBus.ongoing.collectAsState()
    val primaryKey by IslandBus.primaryKey.collectAsState()
    val manual by IslandBus.expanded.collectAsState()
    val quick by IslandBus.quick.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { QuickActions.init(ctx) }
    LaunchedEffect(quick, touch) {
        if (quick) {
            delay(10_000)
            IslandBus.setQuick(false)
        }
    }
    val preview by IslandBus.preview.collectAsState()
    val ongoing = if (s.showOngoing) ongoingAll else emptyList()

    var touch by remember { mutableIntStateOf(0) }
    var lastAlert by remember { mutableStateOf<IslandEvent?>(null) }
    var lingering by remember { mutableStateOf<IslandEvent?>(null) }
    var lastCircle by remember { mutableStateOf<IslandEvent?>(null) }
    var lastMessage by remember { mutableStateOf<IslandEvent?>(null) }
    var lastCurrent by remember { mutableStateOf<IslandEvent?>(null) }
    var reported by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var snapNext by remember { mutableStateOf(false) }
    val appear = remember { Animatable(1f) }
    var suppressLinger by remember { mutableStateOf(false) }

    // Neue Meldung: aufklappen, nach X Sekunden (oder Tippen außerhalb) in die kleine Pille schrumpfen.
    LaunchedEffect(alert?.id) {
        val a = alert ?: return@LaunchedEffect
        lastAlert = a
        try {
            delay(s.alertSeconds * 1000L)
        } finally {
            // Bleibt noch eine Weile als Text in der kleinen Pille stehen, außer sie wurde weggewischt.
            if (suppressLinger) suppressLinger = false else lingering = a
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
        quick -> Mode.QUICK
        manual && current != null -> Mode.EXPANDED
        lingering != null -> Mode.MESSAGE
        current != null -> Mode.COMPACT
        else -> Mode.IDLE
    }
    if (mode == Mode.IDLE && !s.idleVisible && !preview) {
        SideEffect {
            reported = Triple(1f, 1f, 1f)
            IslandBus.setWindowSize(1f, 1f, 1f)
            IslandBus.setTouchRect(0f, 0f, 1f, 1f)
        }
        return
    }

    // Zweite Aktivität als Kreis neben der Pille (wie bei Apple).
    val circleItem = when (mode) {
        Mode.COMPACT -> ongoing.lastOrNull { it.key != current?.key }
        Mode.MESSAGE -> current
        else -> null
    }
    SideEffect {
        if (circleItem != null) lastCircle = circleItem
        if (lingering != null) lastMessage = lingering
        if (current != null) lastCurrent = current
    }
    val shownCurrent = current ?: lastCurrent

    val sc = s.scale
    val gap = s.cameraGap.toFloat()
    val screenW = LocalConfiguration.current.screenWidthDp
    val limit = minOf(s.maxWidth.toFloat(), screenW - 8f)
    val bsc = sc * s.expandedScale // Skalierung von Text und Symbolen der aufgeklappten Pille
    val fsc = sc * minOf(s.expandedScale, s.expandedFrameMax) // Rahmen wächst nur bis zum Limit
    // Aufgeklappt gilt nur die eigene Breite (begrenzt durch den Bildschirm), nicht die "Maximale Breite".
    val bigW = minOf(s.expandedWidth.toFloat(), screenW - 8f)
    val hasActions = current?.actions?.isNotEmpty() == true
    val isMedia = current?.kind == Kind.MEDIA && current.media != null
    val expandedH = (if (isMedia) BASE_EXPANDED_MEDIA_H else if (hasActions) BASE_EXPANDED_ACTIONS_H else BASE_EXPANDED_H) * fsc
    val idleW = BASE_IDLE_W * sc * s.widthFactor
    val compactW = minOf(2f * BASE_COMPACT_SIDE * sc * s.widthFactor + gap, limit)
    val messageW = minOf(2f * BASE_MESSAGE_SIDE * sc * s.widthFactor + gap, limit)
    val smallH = BASE_SMALL_H * sc

    val targetW = when (mode) {
        Mode.IDLE -> idleW
        Mode.COMPACT -> compactW
        Mode.MESSAGE -> messageW
        else -> bigW
    }
    val targetH = when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH
        Mode.ALERT -> BASE_ALERT_H * fsc
        Mode.EXPANDED -> expandedH
        Mode.QUICK -> BASE_QUICK_H * fsc
    }
    val targetCorner = when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH / 2f * s.smallRoundness
        else -> minOf(s.expandedCorner * fsc, targetH / 2f)
    }

    val motion = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val growing = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.QUICK
    val widthSpec = spring<Dp>(
        dampingRatio = if (growing) 0.66f else 0.88f,
        stiffness = if (growing) 380f else 520f,
    )
    val heightSpec = spring<Dp>(
        dampingRatio = if (growing) 0.74f else 0.92f,
        stiffness = if (growing) 460f else 620f,
    )
    val width by animateDpAsState(targetW.dp, if (snapNext) snap<Dp>() else widthSpec, label = "w")
    val height by animateDpAsState(targetH.dp, if (snapNext) snap<Dp>() else heightSpec, label = "h")
    val corner by animateDpAsState(targetCorner.dp, if (snapNext) snap<Dp>() else motion, label = "c")
    // Gleich großer freier Platz links und rechts, damit die Pille mittig (über der Kamera) bleibt.
    val slot by animateDpAsState(
        if (circleItem != null) (smallH + 6f * sc).dp else 0.dp,
        motion,
        label = "slot",
    )
    val circleScale by animateFloatAsState(
        if (circleItem != null) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "circle",
    )
    // Leichtes Einfedern beim Antippen.
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed && mode != Mode.IDLE) 0.95f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press",
    )
    // Große Pille kann auf anderer Höhe sitzen als die kleine: Abstand im Layout, weich animiert.
    val topPad by animateDpAsState(
        ((if (growing) s.expandedTop else s.offsetY) - s.windowTop).dp,
        if (snapNext) snap<Dp>() else spring<Dp>(dampingRatio = 0.8f, stiffness = 380f),
        label = "top",
    )
    // Wischen nach links oder rechts schließt Meldung, aufgeklappte Pille und Nachricht.
    val swipeX = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val swipeable = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.MESSAGE || mode == Mode.QUICK
    LaunchedEffect(mode) { swipeX.snapTo(0f) }
    val onSwipeAway: () -> Unit = {
        when (mode) {
            Mode.ALERT -> {
                suppressLinger = true
                IslandBus.dismiss()
            }
            Mode.EXPANDED -> IslandBus.setExpanded(false)
            Mode.QUICK -> IslandBus.setQuick(false)
            Mode.MESSAGE -> lingering = null
            else -> Unit
        }
    }

    // Fenster: Die linke Kante hängt an einer festen Breite ("span") um die Bildschirmmitte, nur die
    // rechte Kante und die Höhe ändern sich. So gibt es beim Auf- und Zuklappen keinen seitlichen Versatz.
    // Wachsen passiert sofort, Schrumpfen erst nach der Animation.
    val slotMax = smallH + 6f * sc
    // Das sichtbare Fenster hat immer dieselbe Breite (kein seitlicher Versatz beim Auf- und Zuklappen)
    // und nimmt selbst keine Touches an. Ein unsichtbares, passgenaues Touch-Fenster reicht sie durch.
    val rowBig = maxOf(bigW, compactW, messageW) + 2f * slotMax
    val span = minOf(rowBig + 2f * WINDOW_MARGIN, screenW.toFloat())
    val slotTarget = if (circleItem != null) slotMax else 0f
    val topTarget = ((if (growing) s.expandedTop else s.offsetY) - s.windowTop).toFloat()
    val winH = topTarget + targetH + WINDOW_MARGIN
    LaunchedEffect(winH, span) {
        val gh = maxOf(winH, reported.second)
        if (span != reported.first || gh != reported.second) {
            reported = Triple(span, gh, span)
            IslandBus.setWindowSize(span, gh, span)
        }
        if (gh != winH) {
            delay(520)
            reported = Triple(span, winH, span)
            IslandBus.setWindowSize(span, winH, span)
        }
    }
    val rowW = targetW + 2f * slotTarget
    SideEffect {
        IslandBus.setTouchRect(span / 2f - rowW / 2f - 4f, topTarget - 4f, rowW + 8f, targetH + 8f)
    }
    val shape = RoundedCornerShape(corner)

    Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart, unbounded = true)) {
    Box(Modifier.width(span.dp), contentAlignment = Alignment.TopCenter) {
    Row(modifier = Modifier.padding(top = maxOf(topPad, 0.dp)), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(slot))

        Box(
            modifier = Modifier
                .graphicsLayer {
                    val pop = 0.9f + 0.1f * appear.value
                    scaleX = pressScale * pop
                    scaleY = pressScale * pop
                    translationX = swipeX.value
                    alpha = (1f - abs(swipeX.value) / (size.width * 0.9f)).coerceIn(0f, 1f) * appear.value
                }
                .size(width, height)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, Hairline, shape)
                .pointerInput(mode) {
                    if (!swipeable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val x = swipeX.value
                            swipeScope.launch {
                                if (abs(x) > 80.dp.toPx()) {
                                    swipeX.animateTo(sign(x) * size.width * 0.8f, tween(160))
                                    // Die kleine Pille erscheint direkt an ihrem Platz und wächst nicht aus der Mitte.
                                    snapNext = true
                                    appear.snapTo(0f)
                                    onSwipeAway()
                                    swipeX.snapTo(0f)
                                    appear.animateTo(1f, tween(260))
                                    snapNext = false
                                } else {
                                    swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                                }
                            }
                        },
                        onDragCancel = {
                            swipeScope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeScope.launch { swipeX.snapTo(swipeX.value + dragAmount) }
                        },
                    )
                }
                .combinedClickable(
                    interactionSource = press,
                    indication = null,
                    onLongClick = {
                        if (mode == Mode.COMPACT || mode == Mode.MESSAGE || mode == Mode.IDLE) IslandBus.setQuick(true)
                    },
                ) {
                    when (mode) {
                        Mode.ALERT -> {
                            runCatching { alert?.action?.send() }
                            suppressLinger = true // App öffnet sich: Meldung verschwindet direkt, nicht in die kleine Pille
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
                        Mode.IDLE -> IslandBus.setQuick(true)
                        Mode.QUICK -> IslandBus.setQuick(false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = mode to (if (mode == Mode.COMPACT) current?.key else null),
                transitionSpec = {
                    val swap = initialState.first == Mode.COMPACT && targetState.first == Mode.COMPACT
                    if (swap) {
                        // Tausch mit dem Kreis: neuer Inhalt kommt vom Kreis her, alter wandert dorthin.
                        return@AnimatedContent ContentTransform(
                            targetContentEnter = fadeIn(tween(260, delayMillis = 60)) +
                                slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 2 } +
                                scaleIn(tween(340, easing = FastOutSlowInEasing), initialScale = 0.7f),
                            initialContentExit = fadeOut(tween(180)) +
                                slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 2 } +
                                scaleOut(tween(300), targetScale = 0.6f),
                            targetContentZIndex = 0f,
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    }
                    val toBig = targetState.first == Mode.ALERT || targetState.first == Mode.EXPANDED || targetState.first == Mode.QUICK
                    val delay = if (toBig) 120 else 70
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(240, delayMillis = delay)) +
                            scaleIn(
                                tween(320, delayMillis = delay, easing = FastOutSlowInEasing),
                                initialScale = 0.9f,
                            ),
                        initialContentExit = fadeOut(tween(110)) +
                            scaleOut(tween(160), targetScale = 0.95f),
                        targetContentZIndex = 0f,
                        sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                    )
                },
                contentAlignment = Alignment.Center,
                label = "mode",
            ) { st ->
                when (st.first) {
                    Mode.IDLE -> IdleDots(sc)
                    Mode.QUICK -> QuickPanel(bsc, Modifier.requiredSize(bigW.dp, (BASE_QUICK_H * fsc).dp)) { touch++ }
                    Mode.COMPACT -> shownCurrent?.let {
                        CompactContent(it, sc, gap, s.iconTextGap.toFloat(), Modifier.requiredSize(compactW.dp, smallH.dp))
                    }
                    Mode.MESSAGE -> (lingering ?: lastMessage)?.let {
                        MessageContent(it, sc, gap, s.iconTextGap.toFloat(), Modifier.requiredSize(messageW.dp, smallH.dp))
                    }
                    Mode.ALERT -> (alert ?: lastAlert)?.let {
                        AlertContent(it, bsc, Modifier.requiredSize(bigW.dp, (BASE_ALERT_H * fsc).dp))
                    }
                    Mode.EXPANDED -> shownCurrent?.let { cur ->
                        if (cur.kind == Kind.MEDIA && cur.media != null) MediaExpanded(
                            e = cur,
                            count = ongoing.size,
                            index = ongoing.indexOf(cur).coerceAtLeast(0),
                            sc = bsc,
                            onCycle = {
                                val i = ongoing.indexOf(cur)
                                IslandBus.setPrimary(ongoing[(i + 1) % ongoing.size].key)
                                touch++
                            },
                            onAction = { touch++ },
                            showSeek = s.mediaSeek,
                            modifier = Modifier.requiredSize(bigW.dp, expandedH.dp),
                        ) else OngoingExpanded(
                            e = cur,
                            count = ongoing.size,
                            index = ongoing.indexOf(cur).coerceAtLeast(0),
                            sc = bsc,
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
            if (circleScale > 0.01f) {
                (circleItem ?: lastCircle)?.let { c ->
                    Box(
                        Modifier.graphicsLayer {
                            alpha = circleScale.coerceIn(0f, 1f)
                            scaleX = circleScale.coerceAtLeast(0f)
                            scaleY = circleScale.coerceAtLeast(0f)
                        },
                    ) {
                        AnimatedContent(
                            targetState = c,
                            contentKey = { it.key },
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(tween(240, delayMillis = 60)) +
                                        slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it * 2 } +
                                        scaleIn(tween(340), initialScale = 0.5f),
                                    initialContentExit = fadeOut(tween(160)) +
                                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it * 2 } +
                                        scaleOut(tween(300), targetScale = 0.5f),
                                    targetContentZIndex = 0f,
                                    sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                                )
                            },
                            label = "circleSwap",
                        ) { cc ->
                            CircleBubble(cc, sc, smallH) {
                                lingering = null
                                IslandBus.setPrimary(cc.key)
                                touch++
                            }
                        }
                    }
                }
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
    iconGap: Float,
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
            horizontalArrangement = Arrangement.spacedBy((iconGap * sc).dp),
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

/** Links Name (bei Navigation nur die Entfernung), rechts Zeit (bei Navigation der Abbiege-Pfeil). */
@Composable
private fun CompactContent(e: IslandEvent, sc: Float, gap: Float, iconGap: Float, modifier: Modifier) {
    val nav = e.kind == Kind.NAVIGATION
    SplitRow(
        sc = sc,
        gap = gap,
        iconGap = iconGap,
        modifier = modifier,
        left = {
            SmallIcon(e.icon, (14f * sc).dp)
            Text(
                text = if (nav) navDistance(e) else e.title.ifBlank { e.app },
                color = Color.White,
                fontFamily = if (nav) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (nav) FontWeight.Medium else FontWeight.Normal,
                fontSize = (if (nav) 12f else 11f).let { (it * sc).sp },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        },
        right = {
            val turn = if (nav) navTurn(e) else Turn.NONE
            when {
                nav && turn != Turn.NONE -> TurnArrow(turn, (16f * sc).dp)
                e.chronoBase != null -> Chrono(e, (12f * sc).sp)
                e.rightText != null -> Text(
                    text = e.rightText,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = (12f * sc).sp,
                    maxLines = 1,
                )
                else -> LivePulse(sc)
            }
        },
    )
}

// ---------------------------------------------------------------- Navigation

private enum class Turn { LEFT, RIGHT, STRAIGHT, UTURN, ROUNDABOUT, NONE }

private val DIST_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s?(km|mi|ft|yd|m)\b""", RegexOption.IGNORE_CASE)

/** Nur die Entfernung, z. B. "25 m". Findet sich keine, wird der Titel gezeigt. */
private fun navDistance(e: IslandEvent): String =
    DIST_REGEX.find(e.title)?.value
        ?: DIST_REGEX.find(e.text)?.value
        ?: e.title.ifBlank { e.app }

/** Liest die Abbiegerichtung aus Titel/Text (Deutsch und Englisch). Das zuerst genannte Wort gewinnt. */
private fun navTurn(e: IslandEvent): Turn {
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
private fun TurnArrow(turn: Turn, size: Dp, color: Color = Color.White) {
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
private fun LivePulse(sc: Float) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(Modifier.size((5f * sc).dp).clip(CircleShape).background(NothingRed.copy(alpha = a)))
}

/** Nach dem Verkleinern einer Meldung: Absender links, Nachricht rechts der Kamera. */
@Composable
private fun MessageContent(e: IslandEvent, sc: Float, gap: Float, iconGap: Float, modifier: Modifier) {
    SplitRow(
        sc = sc,
        gap = gap,
        iconGap = iconGap,
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

private fun mediaTime(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    return if (t >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    else String.format(java.util.Locale.ROOT, "%d:%02d", t / 60, t % 60)
}

private enum class TransportIcon { PLAY, PAUSE, PREV, NEXT }

@Composable
private fun TransportButton(kind: TransportIcon, sc: Float, big: Boolean, onClick: () -> Unit) {
    val d = (if (big) 52f else 40f) * sc
    Box(
        modifier = Modifier
            .size(d.dp)
            .clip(CircleShape)
            .background(if (big) Color.White else ButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (big) Color.Black else Color.White
        Canvas(Modifier.size((d * 0.42f).dp)) {
            val w = size.width
            val h = size.height
            when (kind) {
                TransportIcon.PLAY -> drawPath(
                    Path().apply { moveTo(w * 0.15f, 0f); lineTo(w, h / 2f); lineTo(w * 0.15f, h); close() }, tint,
                )
                TransportIcon.PAUSE -> {
                    drawRect(tint, Offset(w * 0.1f, 0f), androidx.compose.ui.geometry.Size(w * 0.30f, h))
                    drawRect(tint, Offset(w * 0.6f, 0f), androidx.compose.ui.geometry.Size(w * 0.30f, h))
                }
                TransportIcon.NEXT -> {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.78f, h / 2f); lineTo(0f, h); close() }, tint)
                    drawRect(tint, Offset(w * 0.82f, 0f), androidx.compose.ui.geometry.Size(w * 0.18f, h))
                }
                TransportIcon.PREV -> {
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.22f, h / 2f); lineTo(w, h); close() }, tint)
                    drawRect(tint, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w * 0.18f, h))
                }
            }
        }
    }
}

@Composable
private fun SeekButton(label: String, sc: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size((38f * sc).dp)
            .clip(CircleShape)
            .background(ButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = (11f * sc).sp)
    }
}

@Composable
private fun QuickChip(label: String, active: Boolean, sc: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height((38f * sc).dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) NothingRed else ButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = (10f * sc).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Schnellzugriff: Taschenlampe, Klingelmodus, Nicht stören; WLAN, Bluetooth, Hotspot, Flugmodus öffnen die Systemeinstellung. */
@Composable
private fun QuickPanel(sc: Float, modifier: Modifier, onUsed: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val torch by QuickActions.torch.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val ringer = QuickActions.ringerMode(ctx)
    val ringerLabel = when (ringer) {
        android.media.AudioManager.RINGER_MODE_SILENT -> "STUMM"
        android.media.AudioManager.RINGER_MODE_VIBRATE -> "VIBRATION"
        else -> "TON AN"
    }
    fun close() = IslandBus.setQuick(false)
    val m = Modifier.weight(1f)
    Column(
        modifier = modifier.padding(horizontal = (16f * sc).dp, vertical = (14f * sc).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8f * sc).dp)) {
            QuickChip("LAMPE", torch, sc, m) { QuickActions.toggleTorch(ctx); onUsed() }
            QuickChip(ringerLabel, ringer != android.media.AudioManager.RINGER_MODE_NORMAL, sc, m) {
                QuickActions.cycleRinger(ctx); onUsed()
            }
            QuickChip("NICHT STÖREN", QuickActions.dndOn(ctx), sc, m) {
                if (QuickActions.toggleDnd(ctx)) close() else onUsed()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy((8f * sc).dp)) {
            QuickChip("WLAN", QuickActions.globalOn(ctx, "wifi_on"), sc, m) {
                QuickActions.open(ctx, android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY, android.provider.Settings.ACTION_WIFI_SETTINGS)
                close()
            }
            QuickChip("BLUETOOTH", QuickActions.globalOn(ctx, "bluetooth_on"), sc, m) {
                QuickActions.open(ctx, android.provider.Settings.ACTION_BLUETOOTH_SETTINGS); close()
            }
            QuickChip("HOTSPOT", false, sc, m) {
                QuickActions.open(ctx, "android.settings.TETHER_SETTINGS", android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                close()
            }
            QuickChip("FLUGMODUS", QuickActions.globalOn(ctx, "airplane_mode_on"), sc, m) {
                QuickActions.open(ctx, android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS); close()
            }
        }
    }
}

/** Aufgeklappte Musik: Cover, Titel, Fortschritt mit Zeiten (zum Spulen antippen) und Steuerung. */
@Composable
private fun MediaExpanded(
    e: IslandEvent,
    count: Int,
    index: Int,
    sc: Float,
    onCycle: () -> Unit,
    onAction: () -> Unit,
    showSeek: Boolean,
    modifier: Modifier,
) {
    val mc = e.media ?: return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var base by remember(e.positionMs, e.positionStamp) { mutableStateOf(e.positionMs to e.positionStamp) }
    LaunchedEffect(e.playing) {
        while (e.playing) {
            delay(250)
            now = SystemClock.elapsedRealtime()
        }
    }
    val dur = e.durationMs
    val raw = if (base.first < 0) 0L else base.first + (if (e.playing) (now - base.second) else 0L)
    val pos = if (dur > 0) raw.coerceIn(0L, dur) else raw.coerceAtLeast(0L)
    val frac = if (dur > 0) pos.toFloat() / dur else 0f

    Column(
        modifier = modifier.padding(horizontal = (18f * sc).dp, vertical = (12f * sc).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size((50f * sc).dp).clip(RoundedCornerShape((12f * sc).dp)).background(ButtonBg),
            ) {
                if (e.art != null) {
                    Image(
                        bitmap = e.art,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DotRing(Modifier.fillMaxSize())
                    e.icon?.let {
                        Image(
                            bitmap = it, contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size((20f * sc).dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width((12f * sc).dp))
            Column(modifier = Modifier.weight(1f)) {
                AppLabel(e.app, sc)
                Text(
                    text = e.title, color = Color.White, fontSize = (14f * sc).sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (e.text.isNotBlank()) {
                    Text(
                        text = e.text, color = TextGray, fontSize = (12f * sc).sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mediaTime(pos), color = TextGray, fontFamily = FontFamily.Monospace, fontSize = (10f * sc).sp)
            Spacer(Modifier.width((8f * sc).dp))
            Box(
                Modifier
                    .weight(1f)
                    .height((22f * sc).dp)
                    .pointerInput(mc, dur) {
                        if (dur <= 0) return@pointerInput
                        detectTapGestures { o ->
                            val target = (o.x / size.width).coerceIn(0f, 1f) * dur
                            runCatching { mc.transportControls.seekTo(target.toLong()) }
                            base = target.toLong() to SystemClock.elapsedRealtime()
                            now = SystemClock.elapsedRealtime()
                            onAction()
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height((3f * sc).dp).clip(RoundedCornerShape(50)).background(DotGray))
                Box(
                    Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height((3f * sc).dp)
                        .clip(RoundedCornerShape(50)).background(NothingRed),
                )
            }
            Spacer(Modifier.width((8f * sc).dp))
            Text(
                if (dur > 0) mediaTime(dur) else "--:--",
                color = TextGray, fontFamily = FontFamily.Monospace, fontSize = (10f * sc).sp,
            )
        }

        // Spulen-Tasten (Podcast & Co.): aus den Aktionen der Benachrichtigung erkannt ("15 Sekunden ...").
        val secRe = remember { Regex("(\\d{1,3})\\s*(sek|sec|s\\b)", RegexOption.IGNORE_CASE) }
        val backWords = remember { listOf("zurück", "zurueck", "back", "rewind", "rück", "replay", "rev") }
        val seekActs = if (showSeek) e.actions.filter { secRe.containsMatchIn(it.title) } else emptyList()
        val backAct = seekActs.firstOrNull { a -> backWords.any { it in a.title.lowercase() } }
        val fwdAct = seekActs.firstOrNull { it !== backAct }
        val fallback = showSeek && seekActs.isEmpty() && e.canSkipSeconds
        // Ohne erkennbare Sekundenzahl (z. B. ArchiveTune) kein erfundener Wert, nur Pfeile.
        val backLabel = backAct?.let { secRe.find(it.title)?.groupValues?.get(1) }?.let { "-$it" } ?: "«"
        val fwdLabel = fwdAct?.let { secRe.find(it.title)?.groupValues?.get(1) }?.let { "+$it" } ?: "»"
        val showBack = backAct != null || fallback
        val showFwd = fwdAct != null || fallback
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((14f * sc).dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) SeekButton(backLabel, sc) {
                if (backAct != null) backAct.intent?.let { runCatching { it.send() } }
                else runCatching { mc.transportControls.rewind() }
                onAction()
            }
            TransportButton(TransportIcon.PREV, sc, false) {
                runCatching { mc.transportControls.skipToPrevious() }; onAction()
            }
            TransportButton(if (e.playing) TransportIcon.PAUSE else TransportIcon.PLAY, sc, true) {
                runCatching { if (e.playing) mc.transportControls.pause() else mc.transportControls.play() }
                onAction()
            }
            TransportButton(TransportIcon.NEXT, sc, false) {
                runCatching { mc.transportControls.skipToNext() }; onAction()
            }
            if (showFwd) SeekButton(fwdLabel, sc) {
                if (fwdAct != null) fwdAct.intent?.let { runCatching { it.send() } }
                else runCatching { mc.transportControls.fastForward() }
                onAction()
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

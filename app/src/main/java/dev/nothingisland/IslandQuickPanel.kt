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

@Composable
internal fun RowScope.QuickTile(icon: ImageBitmap, label: String, activeState: Boolean, sc: Float, tint: Boolean = true, anim: Float = 1f, momentary: Boolean = false, onClick: () -> Unit) {
    // Gedrückt: Kachel wird weiß, beim Loslassen wieder normal.
    val src = remember { MutableInteractionSource() }
    val pressedNow by src.collectIsPressedAsState()
    val active = activeState && !pressedNow
    val click = onClick
    // Zustandswechsel: Farbe blendet weich über, das Symbol federt kurz.
    val bgColor by androidx.compose.animation.animateColorAsState(
        if (pressedNow) Color.White else if (active) NothingRed else ButtonBg,
        tween(if (pressedNow) 60 else 220), label = "tilebg",
    )
    val iconPop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(activeState) {
        if (first) { first = false; return@LaunchedEffect }
        iconPop.snapTo(0.55f)
        iconPop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .bounceClick(click, src)
            .graphicsLayer {
                alpha = anim.coerceIn(0f, 1f)
                val k = 0.62f + 0.38f * anim
                scaleX = k
                scaleY = k
                translationY = (1f - anim) * 14.dp.toPx()
            }
            .clip(RoundedCornerShape((14f * sc).dp))
            .drawBehind { drawRect(bgColor) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            bitmap = icon,
            contentDescription = label,
            colorFilter = if (tint) ColorFilter.tint(if (pressedNow) Color.Black else if (active) OnAccent else Fg) else null,
            modifier = Modifier.size(((if (tint) 20f else 26f) * sc).dp).graphicsLayer {
                val k = iconPop.value
                scaleX = k; scaleY = k
            },
        )
        Spacer(Modifier.height((5f * sc).dp))
        Text(
            label, color = if (pressedNow) Color.Black else if (active) OnAccent else TextGray, fontFamily = Mono,
            fontSize = (8f * sc).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ordnet die Kacheln in Seiten zu je zwei Reihen. Regler ("hal:") brauchen eine ganze Reihe für sich. */
internal fun buildQuickPages(tiles: List<QuickTile>): List<List<List<QuickTile>>> {
    val rows = ArrayList<List<QuickTile>>()
    var cur = ArrayList<QuickTile>()
    for (t in tiles) {
        if (t.key.startsWith("hal:")) {
            if (cur.isNotEmpty()) {
                rows += cur
                cur = ArrayList()
            }
            rows += listOf(t)
        } else {
            cur += t
            if (cur.size == 4) {
                rows += cur
                cur = ArrayList()
            }
        }
    }
    if (cur.isNotEmpty()) rows += cur
    return rows.chunked(2).ifEmpty { listOf(emptyList()) }
}

/** Schnellzugriff: Seiten mit Kacheln (zwei Reihen je Seite), seitlich wischbar, Smart-Home-Regler als breite Reihe. */
@Composable
internal fun QuickPanel(pages: List<List<List<QuickTile>>>, sc: Float, modifier: Modifier, onUsed: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val torch by QuickActions.torch.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    @Suppress("UNUSED_EXPRESSION") torch
    // Smart Home: Zustände holen, solange das Panel offen ist.
    val hasHa = remember(pages) { pages.any { p -> p.any { r -> r.any { it.key.startsWith("ha") } } } }
    LaunchedEffect(hasHa) {
        if (!hasHa || !HomeAssistant.configured) return@LaunchedEffect
        while (true) {
            HomeAssistant.refresh()
            delay(6000)
        }
    }
    val haStates by HomeAssistant.states.collectAsState()
    @Suppress("UNUSED_EXPRESSION") haStates

    val rowsPerPage = pages.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    val last = (pages.size - 1).coerceAtLeast(0)
    var pos by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    var settle by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
        // Am Rand gibt es Widerstand wie bei einem Gummiband.
        val resist = if (pos < 0f || pos > last) 0.35f else 1f
        pos = (pos - delta / widthPx * resist).coerceIn(-0.25f, last + 0.25f)
        onUsed()
    }
    Column(modifier = modifier.padding(horizontal = (14f * sc).dp, vertical = (14f * sc).dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .then(
                    if (pages.size > 1) Modifier.draggable(
                        state = drag,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        onDragStarted = { settle?.cancel() },
                        onDragStopped = { velocity ->
                            // Kurzes Wischen reicht: Schwung zählt mit.
                            val target = (pos - velocity / widthPx * 0.22f).roundToInt().coerceIn(0, last)
                            settle = scope.launch {
                                androidx.compose.animation.core.animate(
                                    pos, target.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
                                ) { v, _ -> pos = v }
                            }
                        },
                    ) else Modifier,
                ),
        ) {
            pages.forEachIndexed { pi, rows ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Seite gleitet seitlich, die daneben blendet aus und schrumpft leicht: das gibt Tiefe.
                            val d = pi - pos
                            val a = abs(d).coerceAtMost(1f)
                            translationX = d * widthPx
                            alpha = 1f - a
                            val k = 1f - 0.1f * a
                            scaleX = k
                            scaleY = k
                        },
                    verticalArrangement = Arrangement.spacedBy((10f * sc).dp),
                ) {
                    rows.forEachIndexed { r, row ->
                        if (row.size == 1 && row[0].key.startsWith("hal:")) {
                            HaSliderRow(row[0], sc, Modifier.fillMaxWidth().weight(1f), onUsed)
                        } else {
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy((10f * sc).dp)) {
                                row.forEachIndexed { c, t ->
                                    val appBmp = if (t.key.startsWith("app:")) QuickActions.appIcon(t.key.removePrefix("app:")) else null
                                    var shown by remember { mutableStateOf(pi != 0) }
                                    LaunchedEffect(Unit) {
                                        if (pi == 0) {
                                            delay(10L + (r * 4 + c) * 28L)
                                            shown = true
                                        }
                                    }
                                    val appear by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "tile")
                                    QuickTile(appBmp ?: QuickActions.icon(t.icon), t.label(ctx), t.active(ctx), sc, appBmp == null, appear, t.key.substringAfter(':').substringBefore('.') in setOf("scene", "script", "button") && t.key.startsWith("ha:")) {
                                        if (t.click(ctx)) IslandBus.setQuick(false) else onUsed()
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    repeat(rowsPerPage - rows.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (pages.size > 1) {
            val dotOn = Fg
            val dotOff = DotGray
            Canvas(Modifier.fillMaxWidth().height((14f * sc).dp)) {
                val dens = density
                val h = 4f * sc * dens
                val gap = 5f * sc * dens
                val widths = pages.indices.map { i -> (4f + 9f * (1f - abs(i - pos)).coerceIn(0f, 1f)) * sc * dens }
                var x = (size.width - widths.sum() - gap * (widths.size - 1)) / 2f
                widths.forEachIndexed { i, w ->
                    val prox = (1f - abs(i - pos)).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.lerp(dotOff, dotOn, prox),
                        topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h - 1f * dens),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                    )
                    x += w + gap
                }
            }
        }
    }
}

/** Smart-Home-Regler: Helligkeit einer Lampe per Ziehen, Symbol links schaltet um. */
@Composable
internal fun HaSliderRow(t: QuickTile, sc: Float, modifier: Modifier, onUsed: () -> Unit) {
    val id = t.key.substringAfter(':')
    val states by HomeAssistant.states.collectAsState()
    val on = states[id]?.isOn == true
    val remote = HomeAssistant.brightnessPct(id) ?: if (on) 100 else 0
    var local by remember { mutableStateOf<Float?>(null) }
    val target = local ?: remote.toFloat()
    // Beim Ziehen direkt, sonst weich mit leichtem Nachfedern (Tippen, Loslassen, Antwort von Home Assistant).
    val shown by animateFloatAsState(
        target,
        if (local != null) snap() else spring(dampingRatio = 0.9f, stiffness = 300f),
        label = "ha",
    )
    var w by remember { mutableIntStateOf(1) }
    var lastSend by remember { mutableLongStateOf(0L) }
    val shape = RoundedCornerShape((14f * sc).dp)
    val send = { v: Float, force: Boolean ->
        val now = SystemClock.uptimeMillis()
        if (force || now - lastSend > 220) {
            lastSend = now
            HomeAssistant.setBrightness(id, v.roundToInt())
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(ButtonBg)
            .onSizeChanged { w = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val v = (off.x / w * 100f).coerceIn(0f, 100f)
                    send(v, true)
                    onUsed()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        local = (off.x / w * 100f).coerceIn(0f, 100f)
                        onUsed()
                    },
                    onDragEnd = {
                        local?.let { send(it, true) }
                        local = null
                    },
                    onDragCancel = { local = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val v = (change.position.x / w * 100f).coerceIn(0f, 100f)
                        local = v
                        send(v, false)
                        onUsed()
                    },
                )
            },
    ) {
        val fillFrac = (shown / 100f).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fillFrac)
                .background(NothingRed),
        )
        // Zwei Schichten: normale Schrift, darüber die Schrift auf Akzent, nur im gefüllten Bereich sichtbar.
        HaSliderContent(t, id, sc, shown, Fg, true, onUsed, Modifier.fillMaxSize())
        HaSliderContent(
            t, id, sc, shown, OnAccent, false, onUsed,
            Modifier.fillMaxSize().drawWithContent {
                clipRect(right = size.width * fillFrac) { this@drawWithContent.drawContent() }
            },
        )
    }
}

@Composable
internal fun HaSliderContent(t: QuickTile, id: String, sc: Float, shown: Float, textColor: Color, interactive: Boolean, onUsed: () -> Unit, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = (14f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size((34f * sc).dp)
                .then(
                    if (interactive) Modifier.bounceClick({
                        HomeAssistant.toggle(id)
                        onUsed()
                    }) else Modifier,
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = QuickActions.icon(t.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(textColor),
                modifier = Modifier.size((20f * sc).dp),
            )
        }
        Spacer(Modifier.width((8f * sc).dp))
        Text(
            HomeAssistant.nameOf(id).uppercase(),
            color = textColor, fontFamily = Mono, fontSize = (10f * sc).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            if (shown <= 0f) "AUS" else "${shown.roundToInt()} %",
            color = textColor, fontFamily = Mono, fontSize = (10f * sc).sp, fontWeight = FontWeight.Bold,
        )
    }
}

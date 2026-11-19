package dev.nothingisland

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

internal fun mediaTime(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    return if (t >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    else String.format(java.util.Locale.ROOT, "%d:%02d", t / 60, t % 60)
}

internal enum class TransportIcon { PLAY, PAUSE, PREV, NEXT }

@Composable
internal fun TransportButton(kind: TransportIcon, sc: Float, big: Boolean, onClick: () -> Unit) {
    val d = (if (big) 52f else 40f) * sc
    val nudge = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // Play/Pause: Symbol dreht und poppt beim Wechsel. Vor/Zurück: Symbol schnippt in Richtung.
    var firstKind by remember { mutableStateOf(true) }
    LaunchedEffect(kind) {
        if (firstKind) { firstKind = false; return@LaunchedEffect }
        if (kind == TransportIcon.PLAY || kind == TransportIcon.PAUSE) {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f))
        }
    }
    Box(
        modifier = Modifier
            .size(d.dp)
            .bounceClick({
                if (kind == TransportIcon.NEXT || kind == TransportIcon.PREV) scope.launch {
                    nudge.snapTo(if (kind == TransportIcon.NEXT) 1f else -1f)
                    nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 380f))
                }
                onClick()
            })
            .clip(CircleShape)
            .background(if (big) Fg else ButtonBg),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (big) Bg else Fg
        Canvas(
            Modifier.size((d * 0.42f).dp).graphicsLayer {
                translationX = nudge.value * d * 0.22f * density
                if (kind == TransportIcon.PLAY || kind == TransportIcon.PAUSE) {
                    val p = pop.value
                    scaleX = 0.8f + 0.2f * p; scaleY = 0.8f + 0.2f * p
                }
            },
        ) {
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
internal fun SeekButton(label: String, sc: Float, onClick: () -> Unit) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dir = if (label.startsWith("-") || label.startsWith("«")) -1f else 1f
    Box(
        modifier = Modifier
            .size((38f * sc).dp)
            .bounceClick({
                scope.launch {
                    nudge.snapTo(dir)
                    nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 380f))
                }
                onClick()
            })
            .clip(CircleShape)
            .background(ButtonBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = Fg, fontFamily = Mono, fontSize = (11f * sc).sp,
            modifier = Modifier.graphicsLayer { translationX = nudge.value * 7.dp.toPx() },
        )
    }
}

/** Aufgeklappte Musik: Cover, Titel, Fortschritt mit Zeiten (zum Spulen antippen) und Steuerung. */
@Composable
internal fun MediaExpanded(
    e: IslandEvent,
    count: Int,
    index: Int,
    sc: Float,
    onCycle: () -> Unit,
    onAction: () -> Unit,
    showSeek: Boolean,
    eq: Boolean,
    modifier: Modifier,
    tall: Boolean = false,
) {
    val mc = e.media ?: return
    // Titelwechsel: Cover blendet über, Text gleitet seitlich aus und ein (nur bei neuem Titel).
    var track by remember { mutableStateOf(Triple(e.title, e.text, e.art)) }
    if (track.first != e.title || (track.third == null && e.art != null)) track = Triple(e.title, e.text, e.art)
    val seekPulse = remember { Animatable(0f) }
    val seekScope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var base by remember(e.positionMs, e.positionStamp) { mutableStateOf(e.positionMs to e.positionStamp) }
    val screenOnM by IslandBus.screenOn.collectAsState()
    val lowM by IslandBus.lowPower.collectAsState()
    LaunchedEffect(e.playing, screenOnM) {
        now = SystemClock.elapsedRealtime()
        while (e.playing && screenOnM) {
            delay(if (lowM) 1000L else 500L)
            now = SystemClock.elapsedRealtime()
        }
    }
    val dur = e.durationMs
    val raw = if (base.first < 0) 0L else base.first + (if (e.playing) (now - base.second) else 0L)
    val pos = if (dur > 0) raw.coerceIn(0L, dur) else raw.coerceAtLeast(0L)
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = dragFrac ?: if (dur > 0) pos.toFloat() / dur else 0f

    Column(
        modifier = modifier.padding(horizontal = (18f * sc).dp, vertical = (12f * sc).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        FlexRow(tall) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size((50f * sc).dp).clip(RoundedCornerShape((12f * sc).dp)).background(ButtonBg),
            ) {
                AnimatedContent(
                    targetState = track.third,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(320)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 1.15f),
                            initialContentExit = fadeOut(tween(320)),
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    },
                    label = "art",
                ) { art ->
                    if (art != null) {
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DotRing(Modifier.fillMaxSize())
                        e.icon?.let {
                            Image(
                                bitmap = it, contentDescription = null,
                                colorFilter = ColorFilter.tint(Fg),
                                modifier = Modifier.size((20f * sc).dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width((12f * sc).dp))
            Box(modifier = Modifier.grow().clipToBounds()) {
                AnimatedContent(
                    targetState = track.first to track.second,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260, delayMillis = 60)),
                            initialContentExit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(160)),
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    },
                    label = "track",
                ) { (title, text) ->
                    Column(Modifier.fillMaxWidth()) {
                        AppLabel(e.app, sc)
                        Text(
                            text = title, color = Fg, fontSize = (14f * sc).sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (text.isNotBlank()) {
                            Text(
                                text = text, color = TextGray, fontSize = (12f * sc).sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (eq) Equalizer(e.playing, sc)
            if (count > 1) {
                Text(
                    text = "${index + 1}/$count",
                    color = AccentText,
                    fontFamily = Mono,
                    fontSize = (11f * sc).sp,
                    modifier = Modifier
                        .bounceClick(onCycle)
                        .clip(RoundedCornerShape(50))
                        .padding(horizontal = (8f * sc).dp, vertical = (6f * sc).dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mediaTime(pos), color = TextGray, fontFamily = Mono, fontSize = (10f * sc).sp)
            Spacer(Modifier.width((8f * sc).dp))
            Box(
                Modifier
                    .weight(1f)
                    .height((22f * sc).dp)
                    .pointerInput(mc, dur) {
                        if (dur <= 0) return@pointerInput
                        // Ziehen am Strich spult, ohne die große Bubble zu schließen.
                        detectHorizontalDragGestures(
                            onDragStart = { o ->
                                dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                                onAction()
                            },
                            onDragEnd = {
                                dragFrac?.let { f ->
                                    val target = f * dur
                                    runCatching { mc.transportControls.seekTo(target.toLong()) }
                                    base = target.toLong() to SystemClock.elapsedRealtime()
                                    now = SystemClock.elapsedRealtime()
                                }
                                dragFrac = null
                            },
                            onDragCancel = { dragFrac = null },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                                onAction()
                            },
                        )
                    }
                    .pointerInput(mc, dur) {
                        if (dur <= 0) return@pointerInput
                        detectTapGestures { o ->
                            val target = (o.x / size.width).coerceIn(0f, 1f) * dur
                            runCatching { mc.transportControls.seekTo(target.toLong()) }
                            base = target.toLong() to SystemClock.elapsedRealtime()
                            now = SystemClock.elapsedRealtime()
                            seekScope.launch {
                                seekPulse.snapTo(1f)
                                seekPulse.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
                            }
                            onAction()
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                val barH = ((3f + 4f * seekPulse.value) * sc).dp
                Box(Modifier.fillMaxWidth().height(barH).clip(RoundedCornerShape(50)).background(DotGray))
                Box(
                    Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(barH)
                        .clip(RoundedCornerShape(50)).background(NothingRed),
                )
                // Kleiner Strich zum Ziehen.
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)), contentAlignment = Alignment.CenterEnd) {
                    val thumbH by animateDpAsState(((if (dragFrac != null) 18f else 12f) * sc).dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "thumb")
                    Box(Modifier.width((3f * sc).dp).height(thumbH).clip(RoundedCornerShape(50)).background(Fg))
                }
            }
            Spacer(Modifier.width((8f * sc).dp))
            Text(
                if (dur > 0) mediaTime(dur) else "--:--",
                color = TextGray, fontFamily = Mono, fontSize = (10f * sc).sp,
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
        WrapRow(tall, (if (tall) 8f else 14f).times(sc).dp) {
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

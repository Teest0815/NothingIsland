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

/**
 * Querformat, Einstellung "Meldungen oben in der Mitte": Die Meldung ist ein eigenes Element oben in der Mitte.
 * Sie rutscht von oben ein und nach der Anzeigezeit schnell wieder nach oben weg. Die Pille am Rand bleibt unberührt.
 * Weitere Meldungen stapeln sich dahinter: Eine neue fällt von oben auf den Stapel, die bisherige rutscht eine Stufe zurück.
 */
@Composable
internal fun TopAlertBubble(
    s: IslandSettings,
    alert: IslandEvent?,
    hidden: Boolean,
    onBusy: (Boolean) -> Unit,
    more: Int,
    behind: List<IslandEvent>,
    onOpen: (IslandEvent) -> Unit,
    onMarkRead: (IslandEvent) -> Unit,
    onCopy: (IslandEvent) -> Unit,
    onReply: (IslandEvent) -> Unit,
    onMute: (IslandEvent) -> Unit,
    onAway: () -> Unit,
) {
    var shown by remember { mutableStateOf<IslandEvent?>(null) }
    if (alert != null) shown = alert
    val visible = alert != null && !hidden
    val p = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val shift = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val front = remember { Animatable(0f) } // 1 = Karte lag eben noch hinten und rückt nach vorn
    val ghostP = remember { Animatable(0f) }
    var ghost by remember { mutableStateOf<IslandEvent?>(null) }
    var ghostX by remember { mutableStateOf(0f) }
    var ghostDir by remember { mutableStateOf(0f) }
    val ghostScope = rememberCoroutineScope()
    var lastId by remember { mutableStateOf<Long?>(null) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val hold = remember { object { var prevIds = emptyList<Long>(); var curIds = emptyList<Long>(); var prevFront: IslandEvent? = null; var curFront: IslandEvent? = null; var skipGhost = false } }
    val idsNow = (listOfNotNull(alert) + behind).map { it.id }
    SideEffect {
        if (hold.curIds != idsNow) { hold.prevIds = hold.curIds; hold.curIds = idsNow }
        if (hold.curFront?.id != alert?.id && alert != null) { hold.prevFront = hold.curFront; hold.curFront = alert }
    }
    LaunchedEffect(visible, alert?.id) {
        if (visible) {
            val changed = lastId != null && lastId != alert?.id && p.value > 0.5f
            val promoted = changed && alert != null && hold.prevIds.contains(alert.id)
            val replace = changed && !promoted
            lastId = alert?.id
            if (promoted) {
                // Vordere Karte weg (gelesen, weggewischt, geöffnet): die nächste rückt von hinten nach vorn, der Rest folgt.
                val old = hold.prevFront
                if (old != null && !hold.skipGhost) {
                    ghostX = 0f; ghostDir = 0f
                    ghost = old
                    // Eigener Scope: wird die Animation vom nächsten Wechsel abgebrochen, bliebe sonst ein halbtransparenter Geist stehen.
                    ghostScope.launch { ghostP.snapTo(0f); ghostP.animateTo(1f, tween(170, easing = FastOutSlowInEasing)); ghost = null }
                }
                hold.skipGhost = false
                launch { front.snapTo(1f); front.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
                launch { shift.snapTo(1f); shift.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
            } else if (replace) {
                // Neue Meldung fällt von oben auf den Stapel, die bisherige rutscht dahinter.
                launch { drop.snapTo(with(dens) { -40.dp.toPx() }); drop.animateTo(0f, spring(dampingRatio = 0.68f, stiffness = 400f)) }
                launch { shift.snapTo(-1f); shift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f))
            } else {
                // Erscheint erst kleiner und federt dann auf die volle Größe.
                launch { pop.snapTo(0.8f); pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 450f)) }
                p.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 560f))
            }
        } else {
            lastId = null
            p.animateTo(0f, tween(130, easing = FastOutSlowInEasing))
        }
    }
    val ev = shown
    val dm = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    val screenW = maxOf(dm.widthPixels, dm.heightPixels) / dm.density
    val sc = s.scale * s.expandedScale
    val fsc = s.scale * minOf(s.expandedScale, s.expandedFrameMax)
    val w = minOf(s.expandedWidth.toFloat(), screenW - 8f)
    val h = BASE_ALERT_H * fsc
    val left = (screenW - w) / 2f
    val top = 8f
    val show = ev != null && (p.value > 0.002f || ghost != null)
    SideEffect {
        if (visible && ev != null) IslandBus.setTouchRect2(left - 4f, top - 4f, w + 8f, h + 8f + behind.size * 8f)
        else IslandBus.setTouchRect2(0f, 0f, 1f, 1f)
    }
    if (!show || ev == null) return
    val swipe = remember { Animatable(0f) }
    val pull = remember { Animatable(0f) }
    // Die Wisch-Verschiebung gilt nur für die Karte, die gewischt wurde (die nachrückende startet sofort an ihrem Platz).
    val swipeOwner = remember { longArrayOf(0L) }
    val evNow by androidx.compose.runtime.rememberUpdatedState(ev)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape((minOf(s.expandedCorner * fsc, 42f * fsc)).dp)
    val ctxForHaptic = androidx.compose.ui.platform.LocalContext.current
    val canRead = s.swipeDownRead && ev.markRead != null
    val canMute = s.muteOnSwipeUp && ev.kind == Kind.OTHER && ev.pkg.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        // Karten dahinter (hinterste zuerst, damit die vordere oben liegt).
        for (i in behind.indices.reversed()) {
            val idx = (i + 1) + shift.value
            Box(
                Modifier
                    .offset(left.dp, (top + idx * 8f).dp)
                    .size(w.dp, h.dp)
                    .graphicsLayer {
                        val k = p.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = -(1f - k) * (h + 24f) * density * 0.5f
                        val sk = (0.86f + 0.14f * k.coerceAtMost(1.04f)) * (1f - 0.05f * idx)
                        scaleX = sk; scaleY = sk
                        alpha = (k * 2.2f).coerceIn(0f, 1f) * (1f - 0.28f * idx).coerceIn(0.2f, 1f)
                    }
                    .clip(shape)
                    .background(Bg)
                    .border(1.dp, Hairline, shape),
            )
        }
        Box(
            Modifier
                .offset(left.dp, top.dp)
                .size(w.dp, h.dp)
                .graphicsLayer {
                    val k = p.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationY = -(1f - k) * (h + 24f) * density * 0.5f + pull.value + drop.value
                    // swipe.value immer lesen, damit die Ebene bei jedem Zug neu gezeichnet wird (auch vor der Zuordnung).
                    val sv = swipe.value
                    val sw = if (swipeOwner[0] == ev.id) sv else 0f
                    translationX = sw
                    val landing = 1f - 0.07f * (abs(drop.value) / (40f * density)).coerceIn(0f, 1f)
                    val fi = front.value
                    translationY += fi * 8f * density
                    val sk = pop.value * (0.9f + 0.1f * k.coerceAtMost(1f)) * landing * (1f - 0.05f * fi)
                    scaleX = sk; scaleY = sk
                    alpha = (k * 2.2f).coerceIn(0f, 1f) * (1f - abs(sw) / (w * density * 0.9f)).coerceIn(0f, 1f) * (1f - 0.28f * fi)
                }
                .clip(shape)
                .background(Bg)
                .border(1.dp, Hairline, shape)
                .pointerInput(Unit) {
                    var vel = 0f
                    var last = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { vel = 0f; last = 0L; swipeOwner[0] = evNow.id; onBusy(true); scope.launch { swipe.snapTo(0f) } },
                        onDragEnd = {
                            val x = swipe.value
                            val away = 70.dp.toPx()
                            val flung = abs(vel) > 650.dp.toPx() && abs(x) > 8.dp.toPx() && sign(vel) == sign(x)
                            scope.launch {
                                if (abs(x) > away || flung) {
                                    // Die Karte fliegt in Wischrichtung weiter und blendet dabei langsam aus; die nächste rückt sofort nach.
                                    ghostX = swipe.value
                                    ghostDir = sign(x)
                                    ghost = evNow
                                    hold.skipGhost = true
                                    // Original sofort unsichtbar halten, der Geist übernimmt an derselben Stelle.
                                    swipe.snapTo(sign(x) * size.width * 3f)
                                    scope.launch { ghostP.snapTo(0f); ghostP.animateTo(1f, tween(210, easing = androidx.compose.animation.core.LinearEasing)); ghost = null }
                                    onAway()
                                    onBusy(false)
                                } else { swipe.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)); onBusy(false) }
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)); onBusy(false) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val dt = change.uptimeMillis - last
                            if (last != 0L && dt > 0) vel = vel * 0.5f + (amount / dt * 1000f) * 0.5f
                            last = change.uptimeMillis
                            scope.launch { swipe.snapTo(swipe.value + amount) }
                        },
                    )
                }
                .pointerInput(canRead, canMute) {
                    if (!canRead && !canMute) return@pointerInput
                    var raw = 0f
                    var armed = false
                    val maxPull = 30.dp.toPx()
                    val trigger = 18.dp.toPx()
                    fun band(r: Float) = maxPull * kotlin.math.tanh(r / (maxPull * 1.7f))
                    detectVerticalDragGestures(
                        onDragStart = { onBusy(true) },
                        onDragEnd = {
                            val y = pull.value
                            raw = 0f
                            armed = false
                            scope.launch {
                                if (y < -trigger && canMute) onMute(evNow)
                                else if (y > trigger && canRead) onMarkRead(evNow)
                                pull.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                onBusy(false)
                            }
                        },
                        onDragCancel = { raw = 0f; armed = false; scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)); onBusy(false) } },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            raw = (raw + amount).coerceIn(if (canMute) -1000f else 0f, if (canRead) 1000f else 0f)
                            val y = if (raw < 0f) -band(-raw) else band(raw)
                            val over = abs(y) > trigger
                            if (over && !armed && s.haptics) QuickActions.haptic(ctxForHaptic, 1)
                            armed = over
                            scope.launch { pull.snapTo(y) }
                        },
                    )
                }
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onOpen(ev) },
        ) {
            AlertContent(ev, sc, Modifier.requiredSize(w.dp, h.dp).graphicsLayer { alpha = (1f - front.value * 1.4f).coerceIn(0f, 1f) }, more, onMarkRead = { onMarkRead(ev) }, onCopyCode = { onCopy(ev) }) { onReply(ev) }
        }
        // Eben entfernte vordere Karte: schrumpft kurz nach oben und blendet aus.
        ghost?.let { g ->
            Box(
                Modifier
                    .offset(left.dp, top.dp)
                    .size(w.dp, h.dp)
                    .graphicsLayer {
                        val k = ghostP.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = if (ghostDir == 0f) -k * 18f * density else 0f
                        translationX = ghostX + ghostDir * (1f - (1f - k) * (1f - k)) * 0.45f * size.width
                        scaleX = 1f - 0.1f * k; scaleY = 1f - 0.1f * k
                        alpha = if (ghostDir == 0f) 1f - k else (1f - 1.8f * k).coerceAtLeast(0f)
                    }
                    .clip(shape)
                    .background(Bg)
                    .border(1.dp, Hairline, shape),
            ) { AlertContent(g, sc, Modifier.requiredSize(w.dp, h.dp), 0, instant = true, onMarkRead = {}, onCopyCode = {}) {} }
        }
    }
}

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
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

val NothingRed: Color get() = IslandTheme.accent
val AccentText: Color get() = IslandTheme.accentText
val OnAccent: Color get() = IslandTheme.onAccent
private val DotGray: Color get() = IslandTheme.dotGray
private val TextGray: Color get() = IslandTheme.textGray
private val Hairline: Color get() = IslandTheme.hairline
private val ButtonBg: Color get() = IslandTheme.buttonBg
private val Fg: Color get() = IslandTheme.fg
private val Bg: Color get() = IslandTheme.bg
private val Mono: FontFamily get() = IslandTheme.font

private enum class Mode { IDLE, COMPACT, MESSAGE, ALERT, EXPANDED, QUICK }

/** Reserve um die Pille, damit Überschwingen der Feder nicht abgeschnitten wird. */
private const val WINDOW_MARGIN = 24f
private const val BASE_ALERT_H = 84f
private const val BASE_EXPANDED_H = 84f
private const val BASE_EXPANDED_ACTIONS_H = 134f
private const val BASE_EXPANDED_MEDIA_H = 156f
private const val BASE_IDLE_W = 92f
/** Halbe Breite der kleinen Pille ohne Kamera-Lücke (links und rechts je gleich viel). */
private const val BASE_COMPACT_SIDE = 82f
private const val BASE_MESSAGE_SIDE = 125f
private const val BASE_SMALL_H = 30f

/** Setzt das Theme und baut die Island bei Theme-Wechsel neu auf. */
@Composable
fun IslandHost() {
    val st by IslandBus.settings.collectAsState()
    IslandTheme.apply(st)
    androidx.compose.runtime.key(IslandTheme.signature) { IslandHostBody() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun IslandHostBody() {
    val s by IslandBus.settings.collectAsState()
    val alertRaw by IslandBus.current.collectAsState()
    val alert = alertRaw?.takeUnless { it.quiet }
    val ongoingAll by IslandBus.ongoing.collectAsState()
    val primaryKey by IslandBus.primaryKey.collectAsState()
    val manual by IslandBus.expanded.collectAsState()
    val quick by IslandBus.quick.collectAsState()
    val replying by IslandBus.replyShown.collectAsState()
    val pendingList by IslandBus.pending.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Zwischenablage: kopierten Text kurz zeigen.
    DisposableEffect(ctx, s.clipboard) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val l = android.content.ClipboardManager.OnPrimaryClipChangedListener { QuickActions.onClipboard(ctx) }
        if (s.clipboard) runCatching { cm?.addPrimaryClipChangedListener(l) }
        onDispose { runCatching { cm?.removePrimaryClipChangedListener(l) } }
    }
    LaunchedEffect(Unit) { QuickActions.init(ctx) }
    LaunchedEffect(s.suppressPopups) { QuickActions.applyHeadsUp(ctx, s.suppressPopups) }
    val torchOn by QuickActions.torch.collectAsState()
    LaunchedEffect(torchOn, s.statusPills, s.statusOff) { QuickActions.syncStatus(ctx, s) }
    // Wecker-Countdown und Zustände ohne eigenes Signal regelmäßig prüfen.
    val lowPowerNow by IslandBus.lowPower.collectAsState()
    LaunchedEffect(s.powerSaver) { QuickActions.updateLowPower(ctx, s) }
    val screenOnNow by IslandBus.screenOn.collectAsState()
    LaunchedEffect(lowPowerNow, screenOnNow) {
        // Bei ausgeschaltetem Bildschirm gibt es nichts anzuzeigen: keine Schleife. Beim Einschalten sofort abgleichen.
        if (!screenOnNow) return@LaunchedEffect
        while (true) {
            delay(if (lowPowerNow) 180_000L else 60_000L)
            QuickActions.syncStatus(ctx, IslandBus.settings.value)
        }
    }
    LaunchedEffect(screenOnNow) {
        if (screenOnNow) QuickActions.syncStatus(ctx, IslandBus.settings.value)
    }
    DisposableEffect(ctx) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                when (i?.action) {
                    "android.net.wifi.WIFI_AP_STATE_CHANGED" -> {
                        val on = i.getIntExtra("wifi_state", 0) % 10 == 3
                        QuickActions.hotspotOn = on
                    }
                    android.content.Intent.ACTION_BATTERY_CHANGED ->
                        QuickActions.onBattery(ctx, i, IslandBus.settings.value)
                    android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED,
                    android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" ->
                        QuickActions.onBluetooth(ctx, i, IslandBus.settings.value)
                }
                QuickActions.updateLowPower(ctx, IslandBus.settings.value)
                QuickActions.syncStatus(ctx, IslandBus.settings.value)
            }
        }
        val f = android.content.IntentFilter().apply {
            addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            addAction(android.content.Intent.ACTION_BATTERY_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction("android.location.MODE_CHANGED")
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        val ok = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                ctx, r, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        onDispose { if (ok) runCatching { ctx.unregisterReceiver(r) } }
    }
    val preview by IslandBus.preview.collectAsState()
    val ongoing = if (s.showOngoing) ongoingAll else emptyList()

    var touch by remember { mutableIntStateOf(0) }
    var pullY by remember { mutableFloatStateOf(0f) }
    var pullRaw by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(quick, touch) {
        if (quick) {
            delay(10_000)
            IslandBus.setQuick(false)
        }
    }
    var lastAlert by remember { mutableStateOf<IslandEvent?>(null) }
    var lingering by remember { mutableStateOf<IslandEvent?>(null) }
    var lastCircle by remember { mutableStateOf<IslandEvent?>(null) }
    var lastMessage by remember { mutableStateOf<IslandEvent?>(null) }
    var lastCurrent by remember { mutableStateOf<IslandEvent?>(null) }
    var reported by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var snapNext by remember { mutableStateOf(false) }
    val appear = remember { Animatable(1f) }
    // Kartenstapel: neue Meldung fällt von oben auf den Stapel, gelesene Karte schrumpft weg.
    val dropY = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val stackShift = remember { Animatable(0f) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    var suppressLinger by remember { mutableStateOf(false) }

    // Leise Meldungen (App-Regel) gehen direkt in die kleine Pille.
    LaunchedEffect(alertRaw?.id) {
        val a = alertRaw ?: return@LaunchedEffect
        if (a.quiet) {
            lingering = a
            IslandBus.dismiss()
        }
    }
    // Neue Meldung: aufklappen, nach X Sekunden (oder Tippen außerhalb) in die kleine Pille schrumpfen.
    LaunchedEffect(alert?.id) {
        val a = alert ?: return@LaunchedEffect
        val prev = lastAlert
        lastAlert = a
        if (prev != null && prev.id != a.id && pendingList.any { it.id == prev.id }) {
            // Neue Meldung landet oben auf dem Stapel, die bisherige rutscht dahinter.
            dropY.snapTo(with(dens) { -30.dp.toPx() })
            appear.snapTo(0.6f)
            launch { dropY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
            launch { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 380f)) }
            // Alle Karten dahinter rutschen eine Stufe zurück und werden dabei weich kleiner.
            stackShift.snapTo(-1f)
            launch { stackShift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
        }
        try {
            // Mit gestapelten Karten bleibt mehr Zeit zum Durchblättern; jedes Wischen startet die Zeit neu.
            delay(s.alertSeconds * 1000L + 3000L * pendingList.size)
            while (pullY > 0f) delay(200) // nicht wegklappen, solange gezogen wird
        } finally {
            // Bleibt noch eine Weile als Text in der kleinen Pille stehen, außer sie wurde weggewischt.
            if (suppressLinger) suppressLinger = false else lingering = a
        }
        if (pendingList.isNotEmpty()) IslandBus.dismissAll() else IslandBus.dismiss()
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
        quick && s.quickPanel -> Mode.QUICK
        manual && current != null -> Mode.EXPANDED
        lingering != null -> Mode.MESSAGE
        current != null -> Mode.COMPACT
        else -> Mode.IDLE
    }
    // Beim Antworten zeichnet das eigene Eingabefenster; die Pille tritt zurück.
    val fullscreenNow by IslandBus.fullscreen.collectAsState()
    val landscapeNow = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val hideNow = ((s.hideFullscreen && fullscreenNow) || (s.hideLandscape && landscapeNow)) &&
        alert?.kind != Kind.CALL && !preview
    if ((mode == Mode.IDLE && !s.idleVisible && !preview) || replying || hideNow) {
        // Das Fenster behält seine Größe (nur die Touch-Fläche schrumpft): Ein Größenwechsel beim Zurückkehren
        // ließ die kleine Pille kurz nach links oder rechts springen.
        SideEffect { IslandBus.setTouchRect(0f, 0f, 1f, 1f) }
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

    // Haptik: kurzes Vibrieren, wenn die Pille aufklappt.
    var prevMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val big = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.QUICK
        if (s.haptics && big && prevMode != mode) QuickActions.haptic(ctx, s.hapticLevel)
        prevMode = mode
    }
    val sc = s.scale
    val gap = s.cameraGap.toFloat()
    val screenW = LocalConfiguration.current.screenWidthDp
    val limit = minOf(s.maxWidth.toFloat(), screenW - 8f)
    val bsc = sc * s.expandedScale // Skalierung von Text und Symbolen der aufgeklappten Pille
    val fsc = sc * minOf(s.expandedScale, s.expandedFrameMax) // Rahmen wächst nur bis zum Limit
    // Aufgeklappt gilt nur die eigene Breite (begrenzt durch den Bildschirm), nicht die "Maximale Breite".
    val bigW = minOf(s.expandedWidth.toFloat(), screenW - 8f)
    val hasActions = current?.actions?.isNotEmpty() == true || (current?.kind == Kind.CALL && current.chronoBase != null)
    val quickKeys = remember(s.quickTiles) {
        s.quickTiles.split(',').mapNotNull { QuickActions.tile(it) }.take(24)
    }
    val quickPages = remember(quickKeys) { buildQuickPages(quickKeys) }
    val quickRows = (quickPages.maxOfOrNull { it.size } ?: 1).coerceIn(1, 2)
    val quickBaseH = quickRows * 54f + (quickRows - 1) * 10f + 28f + (if (quickPages.size > 1) 14f else 0f)
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
        Mode.QUICK -> quickBaseH * fsc
    }
    val targetCorner = when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH / 2f * s.smallRoundness
        else -> minOf(s.expandedCorner * fsc, targetH / 2f)
    }

    val motion = spring<Dp>(dampingRatio = 0.9f, stiffness = 500f)
    val growing = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.QUICK
    // Je größer die Pille, desto langsamer und ruhiger federt sie (sonst wirkt das Nachschwingen zu hektisch).
    val bigK = maxOf(fsc, bsc).coerceAtLeast(1f)
    val slow = (1f / bigK).coerceIn(0.5f, 1f)
    val calm = (bigK - 1f).coerceIn(0f, 0.6f) * 0.15f
    val widthSpec = spring<Dp>(
        dampingRatio = (if (growing) 0.6f else 0.88f) + calm * 0.3f,
        stiffness = (if (growing) 400f else 600f) * slow,
    )
    val heightSpec = spring<Dp>(
        dampingRatio = (if (growing) 0.64f else 0.92f) + calm * 0.3f,
        stiffness = (if (growing) 520f else 680f) * slow,
    )
    // Squash-Eintritt: Pille zieht sich kurz zusammen, federt dann etwas breiter und setzt sich.
    // Gilt, wenn die große Bubble zur kleinen Pille wird.
    var squashHold by remember { mutableStateOf(false) }
    var squashBounce by remember { mutableStateOf(false) }
    var lastMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val was = lastMode
        lastMode = mode
        val small = mode == Mode.IDLE || mode == Mode.COMPACT || mode == Mode.MESSAGE
        val big = was == Mode.ALERT || was == Mode.EXPANDED || was == Mode.QUICK
        if (s.squashEntry && small && big) {
            squashBounce = false
            squashHold = true
            delay(140)
            squashHold = false
            squashBounce = true
            delay(800)
            squashBounce = false
        } else {
            squashHold = false
            squashBounce = false
        }
    }
    val holding = squashHold && !snapNext
    val widthTarget = if (holding) targetW * 0.82f else targetW
    val width by animateDpAsState(
        widthTarget.dp,
        when {
            snapNext -> snap<Dp>()
            holding -> tween(110, easing = FastOutSlowInEasing)
            squashBounce -> spring(dampingRatio = 0.48f, stiffness = 430f)
            else -> widthSpec
        },
        label = "w",
    )
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
    // Neue Inhalte in der kleinen Pille (Taschenlampe, Flugmodus ...) poppen mit Nachfedern auf.
    val bump = remember { Animatable(1f) }
    val bumpKey = if (mode == Mode.COMPACT) current?.key else if (mode == Mode.MESSAGE) lingering?.id?.toString() else null
    var prevBumpMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode, bumpKey) {
        val was = prevBumpMode
        prevBumpMode = mode
        if (mode == Mode.COMPACT || mode == Mode.MESSAGE) {
            bump.snapTo(0.84f)
            bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
        } else if (mode == Mode.IDLE && was != Mode.IDLE) {
            // Pille zeigt nichts mehr an: kurzes, leichtes Nachfedern.
            bump.snapTo(0.93f)
            bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 450f))
        } else bump.snapTo(1f)
    }
    val swipeScope = rememberCoroutineScope()
    // Musik: Wischen dehnt die Pille in die Richtung und wechselt den Song.
    var stretchPx by remember { mutableFloatStateOf(0f) }
    var stretchRaw by remember { mutableFloatStateOf(0f) }
    // Meldung nach unten ziehen = als gelesen markieren (nur wenn die App die Aktion anbietet).
    val alertNow by rememberUpdatedState(alert)
    val canPullRead = mode == Mode.ALERT && s.swipeDownRead && (alert ?: lastAlert)?.markRead != null
    // Wird die Meldung mitten im Ziehen beendet, bleibt sonst der Versatz stehen.
    LaunchedEffect(canPullRead) { if (!canPullRead) pullY = 0f }
    val canSkip = mode == Mode.COMPACT && s.swipeSkip && current?.kind == Kind.MEDIA && current.media != null
    val curForSkip by rememberUpdatedState(current)
    val stackBehind = if (mode == Mode.ALERT && s.multiNotif) minOf(pendingList.size, 3) else 0
    val stackOffDp = 9f - 7f * s.stackCompact
    val stackInsetDp = 12f - 9f * s.stackCompact
    // Fortschritt 0..1: je weiter die oberste Karte weggezogen wird, desto mehr rückt die nächste nach vorn.
    val stackP: () -> Float = {
        val sw = abs(swipeX.value) / with(dens) { 110.dp.toPx() }
        val pl = pullY / with(dens) { 18.dp.toPx() }
        maxOf(sw, pl, leave.value).coerceIn(0f, 1f)
    }
    val swipeable = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.MESSAGE || (mode == Mode.QUICK && quickPages.size <= 1)
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
    // Beim Herunterziehen einer Meldung braucht das Fenster Platz nach unten, sonst wird sie abgeschnitten.
    val winH = topTarget + targetH + WINDOW_MARGIN + (if (canPullRead) 40f else 0f) + (if (mode == Mode.ALERT && s.multiNotif && pendingList.isNotEmpty()) stackOffDp * stackBehind + 6f else 0f)
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

        Box {
        // Karten dahinter: eigene Ebenen (bewegen sich nicht mit der obersten). Tiefe d: 0 = vorn, 1 = erste dahinter ...
        // Beim Wegziehen rückt jede Karte nach vorn, bei neuer Meldung rutschen alle eine Stufe zurück (smooth).
        for (i in stackBehind downTo 1) {
            val ev = pendingList.getOrNull(i - 1) ?: continue
            androidx.compose.runtime.key(i) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val d = (i - stackP() + stackShift.value).coerceAtLeast(0f)
                            val inset = stackInsetDp.dp.toPx()
                            val step = (2f * inset / size.width.coerceAtLeast(1f)).coerceAtMost(0.3f)
                            val k = (1f - step * d).coerceIn(0.4f, 1f)
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = stackOffDp.dp.toPx() * d
                            alpha = ((1f - 0.3f * (d - 1f).coerceAtLeast(0f)) * (3.4f - d).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                        }
                        .clip(shape)
                        .drawBehind {
                            val d = (i - stackP() + stackShift.value).coerceIn(0f, 1f)
                            drawRect(Bg.copy(alpha = 1f - (1f - s.stackAlpha) * d))
                        }
                        .border(1.dp, Hairline, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == 1) {
                        Box(Modifier.graphicsLayer { val d = (1f - stackP() + stackShift.value).coerceIn(0f, 1f); alpha = (1f - d) * (1f - d) }) {
                            AlertContent(ev, bsc, Modifier.requiredSize(bigW.dp, (BASE_ALERT_H * fsc).dp)) { }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val pop = 0.9f + 0.1f * appear.value
                    scaleX = pressScale * pop * bump.value.coerceAtMost(1.05f)
                    scaleY = pressScale * pop * bump.value.coerceAtMost(1.05f)
                    translationX = swipeX.value + stretchPx / 2f
                    translationY = pullY + dropY.value
                    // Gestapelte Karten kippen beim Wischen leicht.
                    if (stackBehind > 0) {
                        rotationZ = (swipeX.value / size.width.coerceAtLeast(1f)) * 9f
                        val lv = leave.value
                        scaleX *= 1f - 0.12f * lv
                        scaleY *= 1f - 0.12f * lv
                    }
                    // Zieht sich beim Ziehen leicht in die Länge.
                    if (pullY > 0f) scaleY *= 1f + (pullY / size.height.coerceAtLeast(1f)) * 0.35f
                    alpha = (1f - abs(swipeX.value) / (size.width * 0.9f)).coerceIn(0f, 1f) * appear.value *
                        (if (stackBehind > 0) 1f - leave.value else 1f)
                }
                .layout { m, _ ->
                    // Beim schnellen Auf- und Zuklappen darf die Feder nicht weit über das Ziel schießen.
                    // Beim Aufklappen wächst die Breite höchstens so schnell wie die Höhe (kein plötzliches Breitwerden beim Spammen).
                    val hp = if (growing && targetH > smallH) ((height.value - smallH) / (targetH - smallH)).coerceIn(0f, 1f) else 1f
                    val wCap = if (growing) minOf(targetW * 1.07f, idleW + (targetW - idleW) * (0.3f + 0.7f * hp)) else Float.MAX_VALUE
                    val w = (if (growing) minOf(width.roundToPx(), wCap.dp.roundToPx()) else width.roundToPx()) + abs(stretchPx).roundToInt()
                    val h = if (growing) minOf(height.roundToPx(), (targetH * 1.08f).dp.roundToPx()) else height.roundToPx()
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(w, h) { pl.place(0, 0) }
                }
                .clip(shape)
                .background(Bg)
                .border(1.dp, Hairline, shape)
                .pointerInput(canPullRead) {
                    if (!canPullRead) return@pointerInput
                    val maxPull = 30.dp.toPx()
                    val trigger = 18.dp.toPx()
                    // Gummiband: zieht sich ein Stück mit und gibt immer mehr Widerstand.
                    fun band(raw: Float) = maxPull * kotlin.math.tanh(raw / (maxPull * 1.6f))
                    var armed = false
                    val release: () -> Unit = {
                        val y = pullY
                        pullRaw = 0f
                        swipeScope.launch {
                            if (y > trigger) {
                                val a = alertNow
                                a?.markRead?.let { runCatching { it.send() } }
                                if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                                armed = false
                                // Kein Hochgleiten: Versatz sofort weg, die Meldung schrumpft an Ort und Stelle in die kleine Pille.
                                pullY = 0f
                                suppressLinger = true
                                if (pendingList.isNotEmpty()) {
                                    // Gelesene Karte schrumpft und blendet aus, die nächste rückt nach vorn.
                                    leave.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
                                    IslandBus.dismiss()
                                    leave.snapTo(0f)
                                } else IslandBus.dismiss()
                            } else {
                                androidx.compose.animation.core.animate(y, 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f)) { v, _ -> pullY = v }
                            }
                        }
                    }
                    detectVerticalDragGestures(
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            pullRaw = (pullRaw + dragAmount).coerceAtLeast(0f)
                            pullY = band(pullRaw)
                            val over = pullY > trigger
                            if (over && !armed && s.haptics) QuickActions.haptic(ctx, 1)
                            armed = over
                        },
                    )
                }
                .pointerInput(mode, canSkip, stackBehind) {
                    if (canSkip) {
                        val maxS = 48.dp.toPx()
                        val trigger = 24.dp.toPx()
                        var armed = false
                        // Gummiband: je weiter gezogen, desto mehr Widerstand, ohne harten Anschlag.
                        fun band(raw: Float) = maxS * kotlin.math.tanh(raw / (maxS * 1.7f))
                        val release: () -> Unit = {
                            val from = stretchPx
                            val doSkip = abs(from) > trigger
                            if (doSkip) {
                                val tc = curForSkip?.media?.transportControls
                                runCatching { if (from > 0) tc?.skipToNext() else tc?.skipToPrevious() }
                            }
                            stretchRaw = 0f
                            armed = false
                            swipeScope.launch {
                                androidx.compose.animation.core.animate(
                                    from, 0f,
                                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
                                ) { v, _ -> stretchPx = v }
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragEnd = { release() },
                            onDragCancel = { release() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                stretchRaw += dragAmount
                                stretchPx = band(stretchRaw)
                                val over = abs(stretchPx) > trigger
                                if (over && !armed && s.haptics) QuickActions.haptic(ctx, 1)
                                armed = over
                            },
                        )
                        return@pointerInput
                    }
                    if (!swipeable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val x = swipeX.value
                            swipeScope.launch {
                                if (abs(x) > 80.dp.toPx() && stackBehind > 0) {
                                    // Oberste Karte fliegt zur Seite und ist weg, die nächste rückt nach vorn.
                                    swipeX.animateTo(sign(x) * size.width * 0.7f, tween(150))
                                    suppressLinger = true
                                    // Die nächste Karte ist durch das Wegziehen schon groß und deckend: nahtlos übernehmen.
                                    IslandBus.dismiss()
                                    swipeX.snapTo(0f)
                                    if (s.haptics) QuickActions.haptic(ctx, 1)
                                } else if (abs(x) > 80.dp.toPx()) {
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
                        if (s.quickPanel && (mode == Mode.COMPACT || mode == Mode.MESSAGE || mode == Mode.IDLE)) IslandBus.setQuick(true)
                    },
                ) {
                    when (mode) {
                        Mode.ALERT -> {
                            runCatching { alert?.action?.send() }
                            suppressLinger = true // App öffnet sich: Meldung verschwindet direkt, nicht in die kleine Pille
                            IslandBus.dismiss()
                        }
                        Mode.MESSAGE -> {
                            val l = lingering
                            if (l?.tap != null) l.tap.invoke() else runCatching { l?.action?.send() }
                            lingering = null
                        }
                        Mode.COMPACT -> {
                            IslandBus.setExpanded(true)
                            touch++
                        }
                        Mode.EXPANDED -> IslandBus.setExpanded(false)
                        Mode.IDLE -> if (s.quickPanel) IslandBus.setQuick(true)
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
                    if (targetState.first == Mode.QUICK) {
                        // Schnellzugriff: Rahmen wächst zuerst, die Kacheln blenden danach einzeln ein.
                        return@AnimatedContent ContentTransform(
                            targetContentEnter = fadeIn(tween(140, delayMillis = 20)) +
                                scaleIn(spring(dampingRatio = 0.8f, stiffness = 420f), initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0f)),
                            initialContentExit = fadeOut(tween(80)),
                            targetContentZIndex = 0f,
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    }
                    if (initialState.first == Mode.QUICK) {
                        return@AnimatedContent ContentTransform(
                            targetContentEnter = fadeIn(tween(150, delayMillis = 100)) +
                                scaleIn(tween(240, delayMillis = 100, easing = FastOutSlowInEasing), initialScale = 0.9f),
                            initialContentExit = fadeOut(tween(70)) + scaleOut(tween(120), targetScale = 0.92f),
                            targetContentZIndex = 0f,
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    }
                    val toBig = targetState.first == Mode.ALERT || targetState.first == Mode.EXPANDED || targetState.first == Mode.QUICK
                    val delay = if (toBig) 90 else 60
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(200, delayMillis = delay)) +
                            scaleIn(
                                spring(dampingRatio = 0.78f, stiffness = 380f),
                                initialScale = if (toBig) 0.88f else 0.94f,
                                transformOrigin = TransformOrigin(0.5f, if (toBig) 0f else 0.5f),
                            ),
                        initialContentExit = fadeOut(tween(90)) +
                            scaleOut(tween(140), targetScale = 0.94f),
                        targetContentZIndex = 0f,
                        sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                    )
                },
                contentAlignment = Alignment.Center,
                label = "mode",
            ) { st ->
                when (st.first) {
                    Mode.IDLE -> IdleDots(sc)
                    Mode.QUICK -> QuickPanel(quickPages, bsc, Modifier.requiredSize(bigW.dp, (quickBaseH * fsc).dp)) { touch++ }
                    Mode.COMPACT -> shownCurrent?.let {
                        CompactContent(it, sc, gap, s.iconTextGap.toFloat(), s.equalizer, s.workoutMetric, Modifier.requiredSize(compactW.dp, smallH.dp))
                    }
                    Mode.MESSAGE -> (lingering ?: lastMessage)?.let {
                        MessageContent(it, sc, gap, s.iconTextGap.toFloat(), Modifier.requiredSize(messageW.dp, smallH.dp))
                    }
                    Mode.ALERT -> (alert ?: lastAlert)?.let {
                        AlertContent(it, bsc, Modifier.requiredSize(bigW.dp, (BASE_ALERT_H * fsc).dp), if (s.multiNotif) pendingList.size else 0, onMarkRead = {
                            // Als gelesen markieren: Aktion der App auslösen, Meldung verschwindet (nächste kommt nach).
                            runCatching { it.markRead?.send() }
                            if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                            suppressLinger = true
                            if (pendingList.isNotEmpty()) swipeScope.launch {
                                leave.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
                                IslandBus.dismiss()
                                leave.snapTo(0f)
                            } else IslandBus.dismiss()
                        }, onCopyCode = {
                            it.code?.let { c ->
                                if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                                suppressLinger = true
                                IslandBus.dismiss()
                                QuickActions.copyCode(ctx, c)
                            }
                        }) {
                            // Antworten: Eingabe direkt in der Island, die Meldung verschwindet.
                            IslandBus.startReply(it)
                            suppressLinger = true
                        }
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
                            eq = s.equalizer,
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
private fun SmallIcon(icon: ImageBitmap?, size: Dp, pulse: Boolean = false) {
    if (icon != null) {
        // Klingelnder Anruf: Symbol pulsiert wie ein Herzschlag.
        val beat = if (pulse) {
            val tr = rememberInfiniteTransition(label = "ring")
            tr.animateFloat(
                1f, 1.28f,
                infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "beat",
            ).value
        } else 1f
        // Symbol poppt beim Erscheinen auf und dreht sich leicht ein.
        val pop = remember(icon) { Animatable(0f) }
        LaunchedEffect(icon) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        Image(
            bitmap = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Fg),
            modifier = Modifier.size(size).graphicsLayer {
                val p = pop.value * beat
                scaleX = p; scaleY = p
                rotationZ = (1f - p) * -40f
                alpha = p.coerceIn(0f, 1f)
            },
        )
    } else {
        Box(Modifier.size(size / 2).clip(CircleShape).background(Fg))
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
private fun CompactContent(e: IslandEvent, sc: Float, gap: Float, iconGap: Float, eq: Boolean, workoutMetric: String, modifier: Modifier) {
    val nav = e.kind == Kind.NAVIGATION
    val workout = e.kind == Kind.WORKOUT
    SplitRow(
        sc = sc,
        gap = gap,
        iconGap = iconGap,
        modifier = modifier,
        left = {
            SmallIcon(e.icon, (14f * sc).dp, pulse = e.kind == Kind.CALL && e.chronoBase == null)
            if (workout) {
                // Links die Zeit (läuft live mit, wenn die App einen Zähler liefert).
                Box(Modifier.weight(1f)) {
                    if (e.chronoBase != null) Chrono(e, (12f * sc).sp)
                    else Text(
                        text = e.metrics["time"] ?: e.title.ifBlank { e.app },
                        color = Fg, fontFamily = Mono, fontWeight = FontWeight.Medium,
                        fontSize = (12f * sc).sp, maxLines = 1,
                    )
                }
            } else Text(
                text = if (nav) navDistance(e) else e.title.ifBlank { e.app },
                color = Fg,
                fontFamily = if (nav) Mono else FontFamily.Default,
                fontWeight = if (nav) FontWeight.Medium else FontWeight.Normal,
                fontSize = (if (nav) 12f else 11f).let { (it * sc).sp },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        },
        right = {
            val turn = if (nav) navTurn(e) else Turn.NONE
            val wv = if (workout) workoutValue(e, workoutMetric) else null
            when {
                workout -> if (wv != null) Text(
                    text = wv.uppercase(),
                    color = if (workoutMetric == "bpm") AccentText else Fg,
                    fontFamily = Mono, fontWeight = FontWeight.Medium,
                    fontSize = (12f * sc).sp, maxLines = 1,
                ) else LivePulse(sc)
                nav && turn != Turn.NONE -> TurnArrow(turn, (16f * sc).dp)
                e.chronoBase != null || (e.kind == Kind.MEDIA && e.rightText != null) -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((6f * sc).dp),
                ) {
                    if (eq && e.kind == Kind.MEDIA) Equalizer(e.playing || e.chronoBase != null, sc)
                    if (e.chronoBase != null) Chrono(e, (12f * sc).sp)
                    else Text(
                        text = e.rightText ?: "",
                        color = Fg,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        fontSize = (12f * sc).sp,
                        maxLines = 1,
                    )
                }
                e.rightText != null -> Text(
                    text = e.rightText,
                    color = Fg,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = (12f * sc).sp,
                    maxLines = 1,
                )
                else -> LivePulse(sc)
            }
        },
    )
}

// ---------------------------------------------------------------- Training

private val WORKOUT_ORDER = listOf("bpm", "dist", "pace", "speed", "power", "cad", "kcal")

/** Gewählter Wert, sonst der erste vorhandene. */
private fun workoutValue(e: IslandEvent, pref: String): String? =
    e.metrics[pref] ?: WORKOUT_ORDER.firstNotNullOfOrNull { e.metrics[it] }

/** Alle erkannten Werte in einer Zeile, z. B. "142 BPM · 5.2 KM". */
private fun workoutLine(e: IslandEvent): String =
    WORKOUT_ORDER.mapNotNull { e.metrics[it] }.take(4).joinToString("  ·  ") { it.uppercase() }

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
private fun TurnArrow(turn: Turn, size: Dp, color: Color = Fg) {
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
private fun Equalizer(playing: Boolean, sc: Float) {
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
private fun LivePulse(sc: Float) {
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

/** Nach dem Verkleinern einer Meldung: Absender links, Nachricht rechts der Kamera. */
@Composable
private fun MessageContent(e: IslandEvent, sc: Float, gap: Float, iconGap: Float, modifier: Modifier) {
    SplitRow(
        sc = sc,
        gap = gap,
        iconGap = iconGap,
        modifier = modifier,
        rightAlignStart = e.kind != Kind.STATUS,
        left = {
            SmallIcon(e.icon, (14f * sc).dp)
            Text(
                text = e.title.ifBlank { e.app },
                color = Fg,
                fontSize = (11f * sc).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        },
        right = {
            var short by remember(e.id) { mutableStateOf(false) }
            Text(
                text = if (short && e.textShort != null) e.textShort else e.text,
                color = if (e.kind == Kind.STATUS) Fg else TextGray,
                fontFamily = if (e.kind == Kind.STATUS) Mono else FontFamily.Default,
                fontWeight = if (e.kind == Kind.STATUS) FontWeight.Medium else FontWeight.Normal,
                fontSize = (if (e.kind == Kind.STATUS) 12f else 11f).let { (it * sc).sp },
                maxLines = 1,
                softWrap = e.textShort == null,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (e.textShort != null && it.hasVisualOverflow) short = true },
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
            .background(Bg)
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
    color: Color = Fg,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val screenOn by IslandBus.screenOn.collectAsState()
    LaunchedEffect(e.chronoBase, screenOn) {
        if (!screenOn) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            // Sekunden-Anzeige: genau zum nächsten Sekundenwechsel statt alle 500 ms.
            delay(1000L - (now % 1000L) + 5L)
        }
    }
    val base = e.chronoBase ?: return
    val ms = if (e.countDown) base - now else now - base
    val rollSetting = IslandBus.settings.collectAsState().value.rollDigits
    RollingText(formatClock(ms), size, color, rollSetting, modifier)
}

/** Ersetzt die Prozentzahl am Anfang des Titels ("62 % · LÄDT") durch den animierten Wert. */
private fun chargeTitle(title: String, pct: Int): String {
    val i = title.indexOf('%')
    return if (i < 0) title else "$pct" + " " + title.substring(i)
}

/** Ziffern rollen einzeln nach oben, wenn sie sich ändern (Zählwerk). */
@Composable
private fun RollingText(text: String, size: TextUnit, color: Color, roll: Boolean, modifier: Modifier = Modifier) {
    if (!roll) {
        Text(text, color = color, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = size, maxLines = 1, modifier = modifier)
        return
    }
    Row(modifier.clipToBounds()) {
        text.forEach { ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(160)),
                        initialContentExit = slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(120)),
                        sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                    )
                },
                label = "digit",
            ) { c ->
                Text(c.toString(), color = color, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = size, maxLines = 1)
            }
        }
    }
}

/** Klick mit federndem Eindrücken (Scale-Bounce) statt starrem Ripple. */
@Composable
internal fun Modifier.bounceClick(onClick: () -> Unit, src: MutableInteractionSource = remember { MutableInteractionSource() }): Modifier {
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (down) 0.86f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 650f),
        label = "bounce",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Aufgeklappte Meldung bei neuer Benachrichtigung. */
@Composable
internal fun AlertContent(event: IslandEvent, sc: Float, modifier: Modifier, more: Int = 0, onMarkRead: () -> Unit = {}, onCopyCode: () -> Unit = {}, onReply: () -> Unit) {
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
                    colorFilter = ColorFilter.tint(Fg),
                    modifier = Modifier.size((20f * sc).dp),
                )
            }
        }
        Spacer(Modifier.width((14f * sc).dp))
        val reveal = remember(event.id) { Animatable(0f) }
        LaunchedEffect(event.id) { reveal.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
        Column(modifier = Modifier.weight(1f).graphicsLayer {
            alpha = reveal.value.coerceIn(0f, 1f)
            translationX = (1f - reveal.value) * 16.dp.toPx()
        }) {
            AppLabel(if (event.count > 1) "${event.app} · ${event.count}" else event.app, sc)
            if (event.title.isNotBlank()) {
                Text(
                    text = event.title,
                    color = Fg,
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
        if (more > 0) {
            Spacer(Modifier.width((8f * sc).dp))
            Box(
                modifier = Modifier.clip(CircleShape).background(ButtonBg).padding(horizontal = (8f * sc).dp, vertical = (4f * sc).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$more", color = Fg, fontFamily = Mono, fontSize = (11f * sc).sp, fontWeight = FontWeight.Medium)
            }
        }
        if (event.code != null) {
            Spacer(Modifier.width((10f * sc).dp))
            Box(
                modifier = Modifier
                    .height((36f * sc).dp)
                    .clip(CircleShape)
                    .background(NothingRed)
                    .bounceClick(onCopyCode)
                    .padding(horizontal = (12f * sc).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(event.code, color = OnAccent, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = (13f * sc).sp, maxLines = 1)
            }
        }
        if (event.markRead != null) {
            Spacer(Modifier.width((10f * sc).dp))
            Box(
                modifier = Modifier
                    .size((36f * sc).dp)
                    .clip(CircleShape)
                    .background(ButtonBg)
                    .bounceClick(onMarkRead),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = QuickActions.icon("check"),
                    contentDescription = "Als gelesen markieren",
                    colorFilter = ColorFilter.tint(Fg),
                    modifier = Modifier.size((16f * sc).dp),
                )
            }
        }
        if (event.reply != null) {
            Spacer(Modifier.width((10f * sc).dp))
            Box(
                modifier = Modifier
                    .size((36f * sc).dp)
                    .clip(CircleShape)
                    .background(ButtonBg)
                    .bounceClick(onReply),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = QuickActions.icon("reply"),
                    contentDescription = "Antworten",
                    colorFilter = ColorFilter.tint(Fg),
                    modifier = Modifier.size((16f * sc).dp),
                )
            }
        }
    }
}

@Composable
private fun AppLabel(app: String, sc: Float) {
    Text(
        text = app.uppercase(),
        color = AccentText,
        fontFamily = Mono,
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
    // Ladeanzeige: Balken füllt sich von 0, Prozent zählen hoch, Glow pulsiert beim Laden.
    val chargeOn = e.key == "charge-detail" && IslandBus.settings.collectAsState().value.chargeAnim
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val target = e.progress ?: 0f
    val shownProg by animateFloatAsState(
        if (!chargeOn || go) target else 0f,
        tween(if (chargeOn) 1100 else 0, easing = FastOutSlowInEasing), label = "chg",
    )
    val shownPct = (shownProg * 100f).roundToInt()
    val glowOn = chargeOn && e.title.contains("LÄDT")
    val glow = if (glowOn) rememberInfiniteTransition(label = "glow").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g",
    ).value else 1f
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
                            colorFilter = ColorFilter.tint(Fg),
                            modifier = Modifier.size((20f * sc).dp),
                        )
                    }
                }
                Spacer(Modifier.width((14f * sc).dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppLabel(e.app, sc)
                    if (e.kind == Kind.WORKOUT) {
                        if (e.chronoBase != null) Chrono(e, (22f * sc).sp)
                        else Text(
                            text = e.metrics["time"] ?: e.title,
                            color = Fg, fontFamily = Mono, fontWeight = FontWeight.Medium,
                            fontSize = (22f * sc).sp, maxLines = 1,
                        )
                        val line = workoutLine(e)
                        if (line.isNotBlank()) {
                            Text(
                                text = line, color = TextGray, fontFamily = Mono, fontSize = (11f * sc).sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (e.chronoBase != null) {
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
                                text = if (chargeOn) chargeTitle(e.title, shownPct) else e.title,
                                color = Fg,
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
                        color = AccentText,
                        fontFamily = Mono,
                        fontSize = (11f * sc).sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onCycle)
                            .padding(horizontal = (8f * sc).dp, vertical = (6f * sc).dp),
                    )
                }
            }
            val callActive = e.kind == Kind.CALL && e.chronoBase != null
            if (e.actions.isNotEmpty() || callActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((8f * sc).dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    e.actions.forEach { ActionButton(it, sc, onAction) }
                    if (callActive) CallControls(e, sc, onAction)
                }
            }
        }
        e.progress?.let { _ ->
            val p = shownProg
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
                        .graphicsLayer { alpha = glow }
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
private fun SeekButton(label: String, sc: Float, onClick: () -> Unit) {
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

@Composable
private fun RowScope.QuickTile(icon: ImageBitmap, label: String, activeState: Boolean, sc: Float, tint: Boolean = true, anim: Float = 1f, momentary: Boolean = false, onClick: () -> Unit) {
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
private fun buildQuickPages(tiles: List<QuickTile>): List<List<List<QuickTile>>> {
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
private fun QuickPanel(pages: List<List<List<QuickTile>>>, sc: Float, modifier: Modifier, onUsed: () -> Unit) {
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
private fun HaSliderRow(t: QuickTile, sc: Float, modifier: Modifier, onUsed: () -> Unit) {
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
private fun HaSliderContent(t: QuickTile, id: String, sc: Float, shown: Float, textColor: Color, interactive: Boolean, onUsed: () -> Unit, modifier: Modifier) {
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
    eq: Boolean,
    modifier: Modifier,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Box(modifier = Modifier.weight(1f).clipToBounds()) {
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
private fun CallToggle(label: String, on: Boolean, sc: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height((38f * sc).dp)
            .defaultMinSize(minWidth = (44f * sc).dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) NothingRed else ButtonBg)
            .clickable(onClick = onClick)
            .padding(horizontal = (12f * sc).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (on) OnAccent else Fg,
            fontFamily = Mono,
            fontSize = (11f * sc).sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

/** Stumm und Lautsprecher während eines Gesprächs (nur wenn die Anruf-App sie nicht selbst anbietet). */
@Composable
private fun CallControls(e: IslandEvent, sc: Float, onUsed: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val am = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var muted by remember { mutableStateOf(runCatching { am.isMicrophoneMute }.getOrDefault(false)) }
    var speaker by remember { mutableStateOf(runCatching { @Suppress("DEPRECATION") am.isSpeakerphoneOn }.getOrDefault(false)) }
    val titles = e.actions.joinToString(" ") { it.title.lowercase() }
    if (listOf("stumm", "mute").none { it in titles }) {
        CallToggle("STUMM", muted, sc) {
            runCatching { am.isMicrophoneMute = !muted }
            muted = runCatching { am.isMicrophoneMute }.getOrDefault(!muted)
            onUsed()
        }
    }
    if (listOf("lautsprecher", "speaker").none { it in titles }) {
        CallToggle("LAUTSPRECHER", speaker, sc) {
            runCatching { @Suppress("DEPRECATION") am.isSpeakerphoneOn = !speaker }
            speaker = runCatching { @Suppress("DEPRECATION") am.isSpeakerphoneOn }.getOrDefault(!speaker)
            onUsed()
        }
    }
}

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
                colorFilter = ColorFilter.tint(if (danger) OnAccent else Fg),
                modifier = Modifier.size((20f * sc).dp),
            )
        } else {
            Text(
                text = a.title.uppercase(),
                color = if (danger) OnAccent else Fg,
                fontFamily = Mono,
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

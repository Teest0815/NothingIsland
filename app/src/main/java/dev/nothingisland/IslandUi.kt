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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
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


val NothingRed: Color get() = IslandTheme.accent
val AccentText: Color get() = IslandTheme.accentText
val OnAccent: Color get() = IslandTheme.onAccent
internal val DotGray: Color get() = IslandTheme.dotGray
internal val TextGray: Color get() = IslandTheme.textGray
internal val Hairline: Color get() = IslandTheme.hairline
internal val ButtonBg: Color get() = IslandTheme.buttonBg
internal val Fg: Color get() = IslandTheme.fg
internal val Bg: Color get() = IslandTheme.bg
internal val Mono: FontFamily get() = IslandTheme.font

internal enum class Mode { IDLE, COMPACT, MESSAGE, ALERT, EXPANDED, QUICK, CALC, NOTE }

/** Reserve um die Pille, damit Überschwingen der Feder nicht abgeschnitten wird. */
internal const val WINDOW_MARGIN = 24f
internal const val BASE_ALERT_H = 84f
internal const val BASE_EXPANDED_H = 84f
internal const val BASE_EXPANDED_ACTIONS_H = 134f
internal const val BASE_EXPANDED_MEDIA_H = 156f
internal const val BASE_IDLE_W = 92f
/** Halbe Breite der kleinen Pille ohne Kamera-Lücke (links und rechts je gleich viel). */
internal const val BASE_COMPACT_SIDE = 82f
internal const val BASE_MESSAGE_SIDE = 125f
internal const val BASE_SMALL_H = 30f

/** Setzt das Theme und baut die Island bei Theme-Wechsel neu auf. */
@Composable
fun IslandHost() {
    val st by IslandBus.settings.collectAsState()
    IslandTheme.apply(st)
    androidx.compose.runtime.key(IslandTheme.signature) { IslandHostBody() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun IslandHostBody() {
    val s by IslandBus.settings.collectAsState()
    val alertRaw by IslandBus.current.collectAsState()
    val alert = alertRaw?.takeUnless { it.quiet }
    val ongoingAll by IslandBus.ongoing.collectAsState()
    val primaryKey by IslandBus.primaryKey.collectAsState()
    val manual by IslandBus.expanded.collectAsState()
    val quick by IslandBus.quick.collectAsState()
    val calc by IslandBus.calc.collectAsState()
    val note by IslandBus.note.collectAsState()
    val replyingRaw by IslandBus.replyShown.collectAsState()
    val replyEv by IslandBus.replyTarget.collectAsState()
    val replying = replyingRaw && replyEv?.noteSink == null
    // Bei der Notiz wird die Island nur unsichtbar und berührungslos (nicht abgebaut): Beim Schließen kein Neuaufbau, kein Ruckeln.
    val noteReplyHidden = replyingRaw && replyEv?.noteSink != null

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
    // Gummiband-Dehnung der kleinen Pille beim Nach-unten-Ziehen (0..1, beim Loslassen schnellt sie federnd zurück).
    var rubber by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(quick, touch) {
        if (quick) {
            delay(10_000)
            IslandBus.setQuick(false)
        }
    }
    LaunchedEffect(note, touch) {
        if (note) {
            delay(45_000)
            IslandBus.setNote(false)
        }
    }
    LaunchedEffect(calc, touch) {
        if (calc) {
            delay(45_000)
            IslandBus.setCalc(false)
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
    // Frischer Aufbau (z. B. nach dem Aufwecken): Die Pille kommt von ihrer Mitte aus herein, wie beim Auftauchen einer Status-Pille.
    val wakeTick by IslandBus.wakeTick.collectAsState()
    // Einblenden (monoton, kein Flackern) und Größe (federt) getrennt: Die Feder darf überschwingen, die Deckkraft nicht.
    // Startwerte schon "klein und unsichtbar": Das erste Bild eines frischen Aufbaus darf nicht in voller Größe aufblitzen.
    val wakeFade = remember { Animatable(0f) }
    val wakeScale = remember { Animatable(0.55f) }
    LaunchedEffect(wakeTick) {
        wakeFade.snapTo(0f)
        wakeScale.snapTo(0.55f)
        launch { wakeFade.animateTo(1f, tween(150)) }
        wakeScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
    }
    // Kartenstapel: neue Meldung fällt von oben auf den Stapel, gelesene Karte schrumpft weg.
    val dropY = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val stackShift = remember { Animatable(0f) }
    var stackHold by remember { mutableStateOf(false) }
    val swipeX = remember { Animatable(0f) }
    // Eigener Scope für Stapel-Animationen: sie laufen immer bis zum Ende, auch wenn der Effekt neu startet (Spam).
    val arriveScope = rememberCoroutineScope()
    // Karte hinter der obersten, deren Zeit abläuft: schrumpft und blendet aus, bevor sie entfernt wird.
    val backLeave = remember { Animatable(0f) }
    var backLeaveId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingList.map { it.id }) {
        // Jede Karte läuft nach ihrer eigenen Zeit ab: die älteste (hinterste) verschwindet zuerst.
        pendingList.forEach { ev ->
            launch {
                val remaining = (ev.postedAt + s.alertSeconds * 1000L - System.currentTimeMillis()).coerceAtLeast(400L)
                delay(remaining)
                // Solange gezogen wird, bleibt die Karte; danach noch kurz Zeit zum Lesen.
                if (abs(swipeX.value) > 0.5f || pullY > 0f) {
                    while (abs(swipeX.value) > 0.5f || pullY > 0f) delay(150)
                    delay(900)
                }
                try {
                    backLeaveId = ev.id
                    backLeave.snapTo(0f)
                    backLeave.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
                    IslandBus.removePending(ev.id)
                } finally {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        backLeaveId = null
                        backLeave.snapTo(0f)
                    }
                }
            }
        }
    }
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
        val a = alert
        if (a == null) {
            // Nichts mehr zu zeigen: Stapel-Versatz sicher zurücksetzen (sonst bleibt die Island verschoben).
            arriveScope.launch {
                dropY.snapTo(0f)
                stackShift.snapTo(0f)
                leave.snapTo(0f)
            }
            return@LaunchedEffect
        }
        val prev = lastAlert
        lastAlert = a
        if (prev != null && prev.id != a.id && pendingList.any { it.id == prev.id }) {
            // Neue Meldung landet oben auf dem Stapel, die bisherige rutscht dahinter.
            val fromY = with(dens) { -52.dp.toPx() }
            arriveScope.launch {
                dropY.snapTo(fromY)
                dropY.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = 300f))
            }
            arriveScope.launch {
                appear.snapTo(0.2f)
                appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f))
            }
            // Alle Karten dahinter rutschen eine Stufe zurück und werden dabei weich kleiner.
            arriveScope.launch {
                stackShift.snapTo(-1f)
                stackShift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 260f))
            }
        }
        try {
            // Jede Karte hat ihre eigene Zeit: nach Ablauf verschwindet nur die oberste, die nächste ist dann dran.
            delay((a.postedAt + s.alertSeconds * 1000L - System.currentTimeMillis()).coerceAtLeast(1500L))
            // nicht wegklappen, solange gezogen wird (Wischen oder Herunterziehen); danach noch kurz Zeit zum Lesen
            if (abs(swipeX.value) > 0.5f || pullY > 0f) {
                while (abs(swipeX.value) > 0.5f || pullY > 0f) delay(150)
                delay(900)
            }
        } finally {
            // Bleibt noch eine Weile als Text in der kleinen Pille stehen, außer sie wurde weggewischt.
            if (suppressLinger) suppressLinger = false else if (pendingList.isEmpty()) lingering = a
        }
        if (pendingList.isNotEmpty()) {
            // Nur diese Karte geht weg (schrumpft nach oben), die nächste rückt nach vorn.
            try {
                leave.animateTo(1f, androidx.compose.animation.core.keyframes<Float> { durationMillis = 190; 0f at 0; 1f at 190 using FastOutSlowInEasing })
                IslandBus.dismiss()
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { leave.snapTo(0f) }
            }
        } else IslandBus.dismiss()
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
        calc -> Mode.CALC
        note -> Mode.NOTE
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
        val big = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.QUICK || mode == Mode.CALC || mode == Mode.NOTE
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
        Mode.CALC -> 292f * fsc
        Mode.NOTE -> 250f * fsc
    }
    val targetCorner = when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH / 2f * s.smallRoundness
        else -> minOf(s.expandedCorner * fsc, targetH / 2f)
    }

    val motion = spring<Dp>(dampingRatio = 0.9f, stiffness = 500f)
    val growing = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.QUICK || mode == Mode.CALC || mode == Mode.NOTE
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
        val big = was == Mode.ALERT || was == Mode.EXPANDED || was == Mode.QUICK || was == Mode.CALC || was == Mode.NOTE
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
        maxOf(sw, pl, leave.value, if (stackHold) 1f else 0f).coerceIn(0f, 1f)
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
            Mode.CALC -> IslandBus.setCalc(false)
            Mode.NOTE -> IslandBus.setNote(false)
            Mode.MESSAGE -> lingering = null
            else -> Unit
        }
    }

    // Leuchten: Quelle wählen (Meldung > Anruf > Kamera/Mikrofon > Laden > Musik).
    val charging by IslandBus.charging.collectAsState()
    val callEv = ongoing.firstOrNull { it.kind == Kind.CALL }
    val mediaEv = ongoing.firstOrNull { it.kind == Kind.MEDIA && it.playing }
    val msgEv = if (mode == Mode.MESSAGE) lingering else null
    // Nur bei der kleinen Island (Ruhe, laufende Aktivität, Meldungs-Pille), nie bei großen Ansichten.
    val glowSpec: GlowSpec? = if (!s.glow || lowPowerNow || (mode != Mode.IDLE && mode != Mode.COMPACT && mode != Mode.MESSAGE)) null else when {
        msgEv != null && msgEv.app == "AKKU" && msgEv.kind == Kind.OTHER ->
            if (s.glowBattery) GlowSpec(Color(0xFFFF3B30), msgEv.id, true, 1300, 0.35f, 1f) else null
        msgEv != null && msgEv.kind == Kind.OTHER ->
            if (s.glowNotif) GlowSpec(appGlowColor(msgEv.app), msgEv.id, true, 2600, 0.35f, 0.75f) else null
        callEv != null && s.glowCall ->
            if (callEv.chronoBase == null) GlowSpec(Color(0xFF34C759), "ring", true, 750, 0.3f, 1f)
            else GlowSpec(Color(0xFF34C759), "call", false, 3200, 0.3f, 0.55f)
        ongoing.any { it.key == "camera" || it.key == "mic" } && s.glowPrivacy ->
            GlowSpec(if (ongoing.any { it.key == "camera" }) Color(0xFFFF9F0A) else Color(0xFF30D158), "privacy", true, 2200, 0.6f, 0.85f)
        charging && s.glowCharge -> GlowSpec(Color(0xFF30D158), "charge", true, 3600, 0.2f, 0.5f)
        mediaEv != null && s.glowMedia ->
            GlowSpec(remember(mediaEv.art) { mediaEv.art?.let { coverGlowColor(it) } ?: NothingRed }, "media", false, 3400, 0.25f, 0.6f)
        else -> null
    }

    // Fenster: Die linke Kante hängt an einer festen Breite ("span") um die Bildschirmmitte, nur die
    // rechte Kante und die Höhe ändern sich. So gibt es beim Auf- und Zuklappen keinen seitlichen Versatz.
    // Wachsen passiert sofort, Schrumpfen erst nach der Animation.
    val slotMax = smallH + 6f * sc
    // Das sichtbare Fenster hat immer dieselbe Breite (kein seitlicher Versatz beim Auf- und Zuklappen)
    // und nimmt selbst keine Touches an. Ein unsichtbares, passgenaues Touch-Fenster reicht sie durch.
    val rowBig = maxOf(bigW, compactW, messageW) + 2f * slotMax
    val dir = s.expandDir
    val span = if (dir != 0) screenW.toFloat() else minOf(rowBig + 2f * WINDOW_MARGIN, screenW.toFloat())
    val sw = screenW.toFloat()
    val anchor = s.anchorDp(sw)
    // Linke Fensterkante in dp: bei einseitigem Aufgehen hängt die Pille an der festen Kante (Kamera-Seite).
    val winLeft = when (dir) {
        1 -> (anchor - span).coerceIn(0f, maxOf(0f, sw - span))
        2 -> anchor.coerceIn(0f, maxOf(0f, sw - span))
        else -> sw / 2f + s.offsetX - span / 2f
    }
    val slotTarget = if (circleItem != null) slotMax else 0f
    val topTarget = ((if (growing) s.expandedTop else s.offsetY) - s.windowTop).toFloat()
    // Beim Herunterziehen einer Meldung braucht das Fenster Platz nach unten, sonst wird sie abgeschnitten.
    val winH = topTarget + targetH + WINDOW_MARGIN + (if (canPullRead) 40f else 0f) + (if (mode == Mode.ALERT && s.multiNotif && pendingList.isNotEmpty()) stackOffDp * stackBehind + 6f else 0f)
    LaunchedEffect(winH, span, winLeft) {
        val gh = maxOf(winH, reported.second)
        if (span != reported.first || gh != reported.second || winLeft != reported.third) {
            reported = Triple(span, gh, winLeft)
            IslandBus.setWindowSize(span, gh, winLeft)
        }
        if (gh != winH) {
            delay(520)
            reported = Triple(span, winH, winLeft)
            IslandBus.setWindowSize(span, winH, winLeft)
        }
    }
    val rowW = targetW + 2f * slotTarget
    // Einseitiges Aufgehen: Reihe an der festen Kante, bei Platzmangel in den Bildschirm geschoben.
    // Große Blasen bleiben mittig, nur die kleine Pille hängt an der Kamera-Kante.
    val bigMode = mode == Mode.ALERT || mode == Mode.EXPANDED || mode == Mode.QUICK || mode == Mode.CALC || mode == Mode.NOTE
    val centerX = sw / 2f + s.offsetX
    val rowLeftScreen = when {
        bigMode -> (centerX - rowW / 2f).coerceIn(4f, maxOf(4f, sw - 4f - rowW))
        dir == 1 -> maxOf(4f, anchor - rowW)
        dir == 2 -> minOf(anchor, sw - 4f - rowW)
        else -> 0f
    }
    val bigMix by animateFloatAsState(if (bigMode) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 380f), label = "bigMix")
    SideEffect {
        if (noteReplyHidden) IslandBus.setTouchRect(0f, 0f, 1f, 1f)
        else IslandBus.setTouchRect(
            (if (dir == 0) span / 2f - rowW / 2f else rowLeftScreen - winLeft) - 4f,
            topTarget - 4f, rowW + 8f, targetH + 8f,
        )
    }
    val shape = RoundedCornerShape(corner)

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (noteReplyHidden) 0f else 1f }
    .wrapContentSize(Alignment.TopStart, unbounded = true)) {
    Box(
        (if (dir == 1) Modifier.width(span.dp).padding(end = (winLeft + span - anchor).coerceAtLeast(0f).dp)
        else if (dir == 2) Modifier.width(span.dp).padding(start = (anchor - winLeft).coerceAtLeast(0f).dp)
        else Modifier.width(span.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
    Row(modifier = (if (dir == 1) Modifier.fillMaxWidth().wrapContentWidth(Alignment.End, unbounded = true)
        else if (dir == 2) Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start, unbounded = true) else Modifier)
        .padding(top = maxOf(topPad, 0.dp)), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(slot))

        Box(
            if (dir == 0) Modifier else Modifier.graphicsLayer {
                // Passt die wachsende Pille nicht neben die Kante, wird sie in den Bildschirm geschoben.
                val a = anchor.dp.toPx()
                val m = 4.dp.toPx()
                val w = size.width
                val base = if (dir == 1) a - w else a
                val mid = (centerX.dp.toPx() - w / 2f).coerceIn(m, maxOf(m, sw.dp.toPx() - m - w))
                val small = if (dir == 1) maxOf(base, m) else minOf(base, sw.dp.toPx() - m - w)
                translationX = small + (mid - small) * bigMix - base
            },
        ) {
        // Leuchten: weicher Schein um die Pille (Meldung, Musik, Anruf, Laden, Kamera/Mikrofon, Akku).
        if (glowSpec != null) GlowLayer(glowSpec, s.glowStrength, s.glowSize, corner, Modifier.matchParentSize())
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
                            val bl = if (ev.id == backLeaveId) backLeave.value else 0f
                            val k = (1f - step * d).coerceIn(0.4f, 1f) * (1f - 0.1f * bl)
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = stackOffDp.dp.toPx() * d + bl * 6.dp.toPx()
                            alpha = ((1f - 0.3f * (d - 1f).coerceAtLeast(0f)) * (3.4f - d).coerceIn(0f, 1f)).coerceIn(0f, 1f) * (1f - bl)
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
                    val pop = 0.6f + 0.4f * appear.value
                    scaleX = pressScale * pop * bump.value.coerceAtMost(1.05f) * wakeScale.value
                    scaleY = pressScale * pop * bump.value.coerceAtMost(1.05f) * wakeScale.value
                    translationX = swipeX.value + stretchPx / 2f
                    translationY = pullY + dropY.value
                    if (rubber != 0f) {
                        // Zieht sich etwas in die Höhe und wird schmaler.
                        scaleY *= 1f + 0.16f * rubber
                        scaleX *= 1f - 0.12f * rubber
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    // Gestapelte Karten kippen beim Wischen leicht, fallen mit leichter Schräglage ein.
                    if (stackBehind > 0) {
                        rotationZ = (swipeX.value / size.width.coerceAtLeast(1f)) * 9f +
                            (dropY.value / 52.dp.toPx()) * 5f
                        // Gelesen: kurz anwachsen, dann nach oben zur Pille zusammenschrumpfen.
                        val lv = leave.value
                        scaleX *= 1f - 0.45f * lv
                        scaleY *= 1f - 0.28f * lv
                        translationY -= lv * 28.dp.toPx()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    // Zieht sich beim Ziehen leicht in die Länge.
                    if (pullY > 0f) scaleY *= 1f + (pullY / size.height.coerceAtLeast(1f)) * 0.35f
                    alpha = (1f - abs(swipeX.value) / (size.width * 0.9f)).coerceIn(0f, 1f) * appear.value.coerceIn(0f, 1f) * wakeFade.value *
                        (if (stackBehind > 0) 1f - leave.value.coerceAtLeast(0f).let { it * it } else 1f)
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
                                suppressLinger = true
                                if (pendingList.isNotEmpty()) {
                                    // Gelesene Karte schrumpft aus der gezogenen Position heraus (kein Zurückspringen),
                                    // die nächste bleibt vorn.
                                    stackHold = true
                                    launch { androidx.compose.animation.core.animate(y, 0f, animationSpec = tween(190, easing = FastOutSlowInEasing)) { v, _ -> pullY = v } }
                                    leave.animateTo(1f, androidx.compose.animation.core.keyframes<Float> { durationMillis = 190; 0f at 0; 1f at 190 using FastOutSlowInEasing })
                                    IslandBus.dismiss()
                                    pullY = 0f
                                    leave.snapTo(0f)
                                    stackHold = false
                                } else {
                                    // Kein Hochgleiten: Versatz sofort weg, die Meldung schrumpft an Ort und Stelle in die kleine Pille.
                                    pullY = 0f
                                    IslandBus.dismiss()
                                }
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
                                    launch { delay(60); snapNext = false }
                                    // Kleine Pille poppt federnd wieder auf.
                                    appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 360f))
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
                .pointerInput(mode, s.swipeUpAction, s.swipeDownPause) {
                    if ((s.swipeUpAction.isEmpty() && !s.swipeDownPause) || (mode != Mode.IDLE && mode != Mode.COMPACT)) return@pointerInput
                    val trigger = 22.dp.toPx()
                    val maxPull = 44.dp.toPx()
                    var total = 0f
                    var armed = false
                    val snapBack: () -> Unit = {
                        val from = rubber
                        swipeScope.launch {
                            androidx.compose.animation.core.animate(
                                from, 0f,
                                animationSpec = spring(dampingRatio = 0.3f, stiffness = 700f),
                            ) { v, _ -> rubber = v }
                            rubber = 0f
                        }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f; armed = false },
                        onDragEnd = {
                            val fire = total > trigger
                            total = 0f
                            snapBack()
                            if (fire) {
                                if (s.haptics) QuickActions.haptic(ctx, 1)
                                val mc = curForSkip?.takeIf { it.kind == Kind.MEDIA }?.media
                                if (s.swipeDownPause && mode == Mode.COMPACT && mc != null) {
                                    runCatching {
                                        if (mc.playbackState?.state == PlaybackState.STATE_PLAYING) mc.transportControls.pause()
                                        else mc.transportControls.play()
                                    }
                                } else {
                                    val k = s.swipeUpAction
                                    if (k == "panel") IslandBus.setQuick(true)
                                    else if (k == "calc") IslandBus.setCalc(true)
                                    else if (k == "note") IslandBus.setNote(true)
                                    else runCatching { QuickActions.tile(k)?.click?.invoke(ctx) }
                                }
                            }
                        },
                        onDragCancel = { total = 0f; snapBack() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            total = (total + dragAmount).coerceAtLeast(0f)
                            rubber = kotlin.math.tanh(total / (maxPull * 1.4f))
                            val over = total > trigger
                            if (over && !armed && s.haptics) QuickActions.haptic(ctx, 1)
                            armed = over
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
                        Mode.CALC, Mode.NOTE -> Unit
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
                    if (targetState.first == Mode.QUICK || targetState.first == Mode.CALC || targetState.first == Mode.NOTE) {
                        // Schnellzugriff: Rahmen wächst zuerst, die Kacheln blenden danach einzeln ein.
                        return@AnimatedContent ContentTransform(
                            targetContentEnter = fadeIn(tween(140, delayMillis = 20)) +
                                scaleIn(spring(dampingRatio = 0.8f, stiffness = 420f), initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0f)),
                            initialContentExit = fadeOut(tween(80)),
                            targetContentZIndex = 0f,
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    }
                    if (initialState.first == Mode.QUICK || initialState.first == Mode.CALC || initialState.first == Mode.NOTE) {
                        return@AnimatedContent ContentTransform(
                            targetContentEnter = fadeIn(tween(150, delayMillis = 100)) +
                                scaleIn(tween(240, delayMillis = 100, easing = FastOutSlowInEasing), initialScale = 0.9f),
                            initialContentExit = fadeOut(tween(70)) + scaleOut(tween(120), targetScale = 0.92f),
                            targetContentZIndex = 0f,
                            sizeTransform = SizeTransform(clip = false) { _, _ -> snap<IntSize>() },
                        )
                    }
                    val toBig = targetState.first == Mode.ALERT || targetState.first == Mode.EXPANDED || targetState.first == Mode.QUICK || targetState.first == Mode.CALC || targetState.first == Mode.NOTE
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
                    Mode.IDLE -> IdleDots(sc, camSide = s.expandDir, offsetX = if (s.expandDir == 0) 0f else (if (s.expandDir == 1) 1f else -1f) * (idleW / 2f - maxOf(gap / 2f, 15f * sc)))
                    Mode.NOTE -> NotePanel(s.notes, bsc, Modifier.requiredSize(bigW.dp, (250f * fsc).dp), onUsed = { touch++ })
                    Mode.CALC -> CalcPanel(bsc, Modifier.requiredSize(bigW.dp, (292f * fsc).dp)) { touch++ }
                    Mode.QUICK -> QuickPanel(quickPages, bsc, Modifier.requiredSize(bigW.dp, (quickBaseH * fsc).dp)) { touch++ }
                    Mode.COMPACT -> shownCurrent?.let {
                        CompactContent(it, sc, gap, s.iconTextGap.toFloat(), s.equalizer, s.workoutMetric, Modifier.requiredSize(compactW.dp, smallH.dp), s.expandDir)
                    }
                    Mode.MESSAGE -> (lingering ?: lastMessage)?.let {
                        MessageContent(it, sc, gap, s.iconTextGap.toFloat(), Modifier.requiredSize(messageW.dp, smallH.dp), s.expandDir)
                    }
                    Mode.ALERT -> (alert ?: lastAlert)?.let {
                        AlertContent(it, bsc, Modifier.requiredSize(bigW.dp, (BASE_ALERT_H * fsc).dp), if (s.multiNotif) pendingList.size else 0, onMarkRead = {
                            // Als gelesen markieren: Aktion der App auslösen, Meldung verschwindet (nächste kommt nach).
                            runCatching { it.markRead?.send() }
                            if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                            suppressLinger = true
                            if (pendingList.isNotEmpty()) swipeScope.launch {
                                leave.animateTo(1f, androidx.compose.animation.core.keyframes<Float> { durationMillis = 200; 0f at 0; -0.06f at 40 using FastOutSlowInEasing; 1f at 200 using FastOutSlowInEasing })
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
internal fun IdleDots(sc: Float, offsetX: Float = 0f, camSide: Int = 0) {
    Row(
        modifier = Modifier.offset(x = offsetX.dp),
        horizontalArrangement = Arrangement.spacedBy((6f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kamera rechts (1): rechter grauer Punkt weg, Kamera links (2): linker grauer Punkt weg.
        if (camSide != 2) Box(Modifier.size((4f * sc).dp).clip(CircleShape).background(DotGray))
        Box(Modifier.size((6f * sc).dp).clip(CircleShape).background(NothingRed))
        if (camSide != 1) Box(Modifier.size((4f * sc).dp).clip(CircleShape).background(DotGray))
    }
}

@Composable
internal fun SmallIcon(icon: ImageBitmap?, size: Dp, pulse: Boolean = false, popKey: Any? = icon) {
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
        val pop = remember(popKey) { Animatable(0f) }
        LaunchedEffect(popKey) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
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

/** Symbol der kleinen Pille je nach Einstellung: App-Symbol, Musiknote, Cover oder Profilbild. */
@Composable
internal fun PillIcon(e: IslandEvent, size: Dp, pulse: Boolean = false) {
    val s by IslandBus.settings.collectAsState()
    val pic: ImageBitmap? = when {
        e.kind == Kind.MEDIA && s.pillIconMedia == 2 -> e.art
        e.kind == Kind.OTHER && s.pillIconNotif == 1 -> e.avatar
        else -> null
    }
    val note = e.kind == Kind.MEDIA && s.pillIconMedia == 1
    when {
        pic != null -> {
            val pop = remember(e.key) { Animatable(0f) }
            LaunchedEffect(e.key) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
            Image(
                bitmap = pic,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size * 1.15f).graphicsLayer {
                    scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f)
                }.clip(CircleShape),
            )
        }
        note -> SmallIcon(QuickActions.icon("music"), size, pulse, popKey = e.key)
        else -> SmallIcon(e.icon, size, pulse, popKey = e.key)
    }
}

/** Symbol im Punktring der großen Pille, gleiche Einstellung wie in der kleinen Pille. */
@Composable
internal fun RingIcon(e: IslandEvent, sc: Float) {
    val s by IslandBus.settings.collectAsState()
    val pic: ImageBitmap? = when {
        e.kind == Kind.MEDIA && s.pillIconMedia == 2 -> e.art
        e.kind == Kind.OTHER && s.pillIconNotif == 1 -> e.avatar
        else -> null
    }
    if (pic != null) {
        Image(
            bitmap = pic,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size((34f * sc).dp).clip(CircleShape),
        )
        return
    }
    val bmp = if (e.kind == Kind.MEDIA && s.pillIconMedia == 1) QuickActions.icon("music") else e.icon
    bmp?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Fg),
            modifier = Modifier.size((20f * sc).dp),
        )
    }
}

/** Kleine Pille: links Symbol + Text/Zeit direkt am Symbol, in der Mitte Platz für die Kamera. */
@Composable
internal fun SplitRow(
    sc: Float,
    gap: Float,
    iconGap: Float,
    modifier: Modifier,
    rightAlignStart: Boolean = false,
    camSide: Int = 0,
    left: @Composable RowScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = (11f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kamera an der festen Kante (camSide 1 = rechts, 2 = links): Lücke dort, Inhalt daneben.
        if (camSide == 2) Spacer(Modifier.width(gap.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((iconGap * sc).dp),
            content = left,
        )
        if (camSide == 0) Spacer(Modifier.width(gap.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (rightAlignStart) Alignment.CenterStart else Alignment.CenterEnd,
            content = right,
        )
        if (camSide == 1) Spacer(Modifier.width(gap.dp))
    }
}

/** Links Name (bei Navigation nur die Entfernung), rechts Zeit (bei Navigation der Abbiege-Pfeil). */
@Composable
internal fun CompactContent(e: IslandEvent, sc: Float, gap: Float, iconGap: Float, eq: Boolean, workoutMetric: String, modifier: Modifier, camSide: Int = 0) {
    val nav = e.kind == Kind.NAVIGATION
    val workout = e.kind == Kind.WORKOUT
    SplitRow(
        sc = sc,
        gap = gap,
        camSide = camSide,
        iconGap = iconGap,
        modifier = modifier,
        left = {
            PillIcon(e, (14f * sc).dp, pulse = e.kind == Kind.CALL && e.chronoBase == null)
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

// ---------------------------------------------------------------- Navigation

/** Nach dem Verkleinern einer Meldung: Absender links, Nachricht rechts der Kamera. */
@Composable
internal fun MessageContent(e: IslandEvent, sc: Float, gap: Float, iconGap: Float, modifier: Modifier, camSide: Int = 0) {
    SplitRow(
        sc = sc,
        gap = gap,
        camSide = camSide,
        iconGap = iconGap,
        modifier = modifier,
        rightAlignStart = e.kind != Kind.STATUS,
        left = {
            PillIcon(e, (14f * sc).dp)
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
internal fun CircleBubble(e: IslandEvent, sc: Float, size: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .requiredSize(size.dp)
            .clip(CircleShape)
            .background(Bg)
            .border(1.dp, Hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PillIcon(e, (14f * sc).dp)
    }
}

internal fun formatClock(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

@Composable
internal fun Chrono(
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
internal fun chargeTitle(title: String, pct: Int): String {
    val i = title.indexOf('%')
    return if (i < 0) title else "$pct" + " " + title.substring(i)
}

/** Ziffern rollen einzeln nach oben, wenn sie sich ändern (Zählwerk). */
@Composable
internal fun RollingText(text: String, size: TextUnit, color: Color, roll: Boolean, modifier: Modifier = Modifier) {
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

// ------------------------------------------------------------------ Taschenrechner

// ------------------------------------------------------------------ Notiz-Pille

// ------------------------------------------------------------------ Leuchten

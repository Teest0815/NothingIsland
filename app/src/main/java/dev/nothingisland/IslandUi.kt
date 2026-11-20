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
internal const val BASE_ALERT_TALL_H = 210f
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
    // Nachrücken: die neue vordere Karte kommt aus ihrer Stapelposition (kleiner, tiefer) federnd nach vorn.
    val frontPromo = remember { Animatable(0f) }
    // Welche Karte gerade weggeht: der Weggeh-Wert gilt nur für sie, nicht für die nachrückende.
    val leaveId = remember { longArrayOf(0L) }
    // Die Karte kam aus einem Stapel (auch die letzte): sie geht mit derselben Animation weg.
    val fromStack = remember { booleanArrayOf(false) }
    var stackHold by remember { mutableStateOf(false) }
    val swipeX = remember { Animatable(0f) }
    // Eigener Scope für Stapel-Animationen: sie laufen immer bis zum Ende, auch wenn der Effekt neu startet (Spam).
    val arriveScope = rememberCoroutineScope()
    // Karte hinter der obersten, deren Zeit abläuft: schrumpft und blendet aus, bevor sie entfernt wird.
    val backLeave = remember { Animatable(0f) }
    var backLeaveId by remember { mutableStateOf<Long?>(null) }
    // Karten hinter der obersten haben keinen eigenen Countdown: ihre Anzeigezeit läuft erst ab, wenn sie vorne liegen.
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
                frontPromo.snapTo(0f)
                fromStack[0] = false
            }
            return@LaunchedEffect
        }
        val prev = lastAlert
        lastAlert = a
        // Querformat mit Meldung oben: die Stapel-Animation läuft dort in der eigenen Meldung, die Pille am Rand bleibt ruhig.
        val topOn = IslandBus.rotation.value.let { it == 1 || it == 3 } && s.landTopAlert
        if (!topOn && prev != null && prev.id != a.id && pendingList.any { it.id == prev.id }) {
            // Neue Meldung landet oben auf dem Stapel, die bisherige rutscht dahinter.
            val fromY = with(dens) { -30.dp.toPx() }
            arriveScope.launch {
                dropY.snapTo(fromY)
                dropY.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 320f))
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
        if (!topOn && s.multiNotif && prev != null && prev.id != a.id && a.id < prev.id && pendingList.none { it.id == prev.id }) {
            // Die vordere Karte ist weg, die nächste rückt aus dem Stapel nach vorn (federnd, mit Überschwingen).
            fromStack[0] = true
            arriveScope.launch {
                frontPromo.snapTo(1f)
                frontPromo.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
            }
        }
        try {
            // Jede Karte hat ihre eigene Zeit: nach Ablauf verschwindet nur die oberste, die nächste ist dann dran.
            delay((s.alertSeconds * 1000L).coerceAtLeast(1500L))
            // nicht wegklappen, solange gezogen wird (Wischen oder Herunterziehen); danach noch kurz Zeit zum Lesen
            if (abs(swipeX.value) > 0.5f || pullY > 0f) {
                while (abs(swipeX.value) > 0.5f || pullY > 0f) delay(150)
                delay(900)
            }
        } finally {
            // Bleibt noch eine Weile als Text in der kleinen Pille stehen, außer sie wurde weggewischt.
            if (suppressLinger) suppressLinger = false else if (pendingList.isEmpty()) lingering = a
        }
        if ((pendingList.isNotEmpty() || fromStack[0]) && !topOn) {
            // Nur diese Karte geht weg (schrumpft nach oben), die nächste rückt nach vorn.
            try {
                leaveId[0] = a.id
                leave.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                frontPromo.snapTo(1f)
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
    // Querformat + "Meldungen oben in der Mitte": die Meldung ist ein eigenes Element, die Pille am Rand bleibt, wie sie ist.
    val rotE by IslandBus.rotation.collectAsState()
    val topAlertOn = (rotE == 1 || rotE == 3) && s.landTopAlert
    val mode = when {
        alert != null && !topAlertOn -> Mode.ALERT
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
    val topScope = rememberCoroutineScope()
    TopAlertBubble(
        s = s,
        alert = if (topAlertOn) alert else null,
        hidden = replying,
        more = 0,
        behind = if (s.multiNotif) pendingList.take(2) else emptyList(),
        onMute = { a ->
            if (!a.pkg.startsWith("test.")) IslandBus.mute(a.pkg, s.muteMinutes)
            if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
            suppressLinger = true
            IslandBus.dismiss()
            IslandBus.post(
                IslandEvent(
                    key = "mute-" + a.pkg, kind = Kind.STATUS, app = a.app, title = a.app.uppercase(),
                    text = "STUMM · ${s.muteMinutes} MIN", textShort = "${s.muteMinutes} MIN", quiet = true,
                ),
            )
        },
        onOpen = { a ->
            runCatching { a.action?.send() }
            suppressLinger = true
            IslandBus.dismiss()
        },
        onMarkRead = { a ->
            a.markRead?.let { pi -> topScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { pi.send() } } }
            if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
            suppressLinger = true
            IslandBus.dismiss()
        },
        onCopy = { a ->
            a.code?.let { c ->
                if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                suppressLinger = true
                IslandBus.dismiss()
                QuickActions.copyCode(ctx, c)
            }
        },
        onReply = { a ->
            IslandBus.startReply(a)
            suppressLinger = true
        },
        onAway = {
            suppressLinger = true
            if (s.haptics) QuickActions.haptic(ctx, 1)
            IslandBus.dismiss()
        },
    )
    if ((mode == Mode.IDLE && !s.idleVisible && !preview) || replying || hideNow) {
        // Das Fenster behält seine Größe (nur die Touch-Fläche schrumpft): Ein Größenwechsel beim Zurückkehren
        // ließ die kleine Pille kurz nach links oder rechts springen.
        SideEffect { IslandBus.setTouchRect(0f, 0f, 1f, 1f) }
        return
    }

    // Zweite Aktivität als Kreis neben der Pille (wie bei Apple).
    val landNow0 = IslandBus.rotation.collectAsState().value.let { it == 1 || it == 3 }
    val circleItem = if (landNow0) null else when (mode) {
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
    // Querformat (Kamera links oder rechts): Die Island bleibt aufrecht und lesbar, hängt aber mit ihrer Kante an der
    // Kamera-Seite und sitzt in der Höhe der Kamera. Das Fenster ist dann fest (ganzer Bildschirm), es wird nie umgebaut.
    val dmNow = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    val rot by IslandBus.rotation.collectAsState()
    val land = rot == 1 || rot == 3
    // Drehen: kurz ausblenden, Größen und Position sofort umschalten (ohne Federn), dann weich einblenden.
    var rotShown by remember { mutableIntStateOf(rot) }
    val rotFade = remember { Animatable(1f) }
    val rotSnap = rotShown != rot
    LaunchedEffect(rot) {
        if (rotShown != rot) {
            rotFade.snapTo(0f)
            delay(140)
            rotShown = rot
            rotFade.animateTo(1f, tween(260))
        }
    }
    val longDp = maxOf(dmNow.widthPixels, dmNow.heightPixels) / dmNow.density
    val shortDp = minOf(dmNow.widthPixels, dmNow.heightPixels) / dmNow.density
    val screenW = (if (land) longDp else shortDp).toInt()
    val capH: (Float) -> Float = { h -> if (land) landCapH(s, shortDp, h * s.landBigH) else h }
    val limit = minOf(s.maxWidth.toFloat(), screenW - 8f)
    val landK = if (land) s.landBigScale else 1f
    val bsc = sc * s.expandedScale * landK // Skalierung von Text und Symbolen der aufgeklappten Pille
    val fsc = sc * minOf(s.expandedScale, s.expandedFrameMax) * landK // Rahmen wächst nur bis zum Limit
    // Aufgeklappt gilt nur die eigene Breite (begrenzt durch den Bildschirm), nicht die "Maximale Breite".
    // Querformat: alle großen Ansichten sind senkrechte Rahmen (Inhalt waagerecht lesbar).
    val bigW = minOf((if (land) s.landBigW else s.expandedWidth).toFloat(), screenW - 8f)
    // Meldung im Querformat: senkrecht am Rand oder (Einstellung) oben in der Mitte wie im Hochformat.
    val topAlert = land && s.landTopAlert && mode == Mode.ALERT
    val landTall = land && !topAlert
    val alertW = if (topAlert) minOf(s.expandedWidth.toFloat(), screenW - 8f) else bigW
    val alertH = if (landTall) capH(BASE_ALERT_TALL_H * fsc) else BASE_ALERT_H * (fsc / landK)
    val hasActions = current?.actions?.isNotEmpty() == true || (current?.kind == Kind.CALL && current.chronoBase != null)
    val quickKeys = remember(s.quickTiles) {
        s.quickTiles.split(',').mapNotNull { QuickActions.tile(it) }.take(24)
    }
    val quickPages = remember(quickKeys, land) { if (land) buildQuickPages(quickKeys, 2, 4) else buildQuickPages(quickKeys) }
    val quickRows = (quickPages.maxOfOrNull { it.size } ?: 1).coerceIn(1, if (land) 4 else 2)
    val quickBaseH = quickRows * 54f + (quickRows - 1) * 10f + 28f + (if (quickPages.size > 1) 14f else 0f)
    val isMedia = current?.kind == Kind.MEDIA && current.media != null
    val expandedH = capH((if (land) (if (isMedia) 250f else if (hasActions) 200f else 150f) else (if (isMedia) BASE_EXPANDED_MEDIA_H else if (hasActions) BASE_EXPANDED_ACTIONS_H else BASE_EXPANDED_H)) * fsc)
    val quickH = capH(quickBaseH * fsc)
    val calcH = capH((if (land) 330f else 292f) * fsc)
    val noteH = capH((if (land) 320f else 250f) * fsc)
    val idleW = BASE_IDLE_W * sc * s.widthFactor
    val compactW = minOf(2f * BASE_COMPACT_SIDE * sc * s.widthFactor + gap, limit)
    val messageW = minOf(2f * BASE_MESSAGE_SIDE * sc * s.widthFactor + gap, limit)
    val smallH = BASE_SMALL_H * sc

    // Querformat: kleine Pille steht senkrecht am Rand (Dicke = Höhe, Länge = Breite).
    val vert = land && (mode == Mode.IDLE || mode == Mode.COMPACT || mode == Mode.MESSAGE)
    // Querformat: "nach unten" der kleinen Pille ist vom Kamera-Rand weg (nach innen).
    val outSgn = if (rot == 1) 1f else -1f
    val smallLen = when (mode) { Mode.IDLE -> idleW; Mode.COMPACT -> compactW; Mode.MESSAGE -> messageW; else -> 0f }
    val targetW = if (vert) smallH else when (mode) {
        Mode.IDLE -> idleW
        Mode.COMPACT -> compactW
        Mode.MESSAGE -> messageW
        Mode.ALERT -> alertW
        else -> bigW
    }
    val targetH = if (vert) minOf(smallLen, shortDp - 12f) else when (mode) {
        Mode.IDLE, Mode.COMPACT, Mode.MESSAGE -> smallH
        Mode.ALERT -> alertH
        Mode.EXPANDED -> expandedH
        Mode.QUICK -> quickH
        Mode.CALC -> calcH
        Mode.NOTE -> noteH
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
    // Nach oben weggezogen: kein Squash (die Bubble ist schon oben, erst-kleiner-dann-größer wäre unsinnig).
    var skipSquash by remember { mutableStateOf(false) }
    var lastMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val was = lastMode
        lastMode = mode
        val small = mode == Mode.IDLE || mode == Mode.COMPACT || mode == Mode.MESSAGE
        val big = was == Mode.ALERT || was == Mode.EXPANDED || was == Mode.QUICK || was == Mode.CALC || was == Mode.NOTE
        if (s.squashEntry && small && big && !skipSquash) {
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
    val snapAll = snapNext || rotSnap
    val holding = squashHold && !snapAll
    val widthTarget = if (holding) targetW * 0.82f else targetW
    val width by animateDpAsState(
        widthTarget.dp,
        when {
            snapAll -> snap<Dp>()
            holding -> tween(110, easing = FastOutSlowInEasing)
            squashBounce -> spring(dampingRatio = 0.48f, stiffness = 430f)
            else -> widthSpec
        },
        label = "w",
    )
    val height by animateDpAsState(targetH.dp, if (snapAll) snap<Dp>() else heightSpec, label = "h")
    val corner by animateDpAsState(targetCorner.dp, if (snapAll) snap<Dp>() else motion, label = "c")
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
    // Querformat: Höhe der Kamera am Rand (Horizontal-Wert verschiebt entlang der Kante), große Blasen mittig dazu.
    val centerYLand = shortDp / 2f + s.landShift
    val bigTopLand = (centerYLand - targetH / 2f).coerceIn(6f, maxOf(6f, shortDp - targetH - 6f))
    val topBaseTarget = if (topAlert) 8f else if (land) (if (growing) bigTopLand else (centerYLand - targetH / 2f).coerceIn(6f, maxOf(6f, shortDp - targetH - 6f)))
        else ((if (growing) s.expandedTop else s.offsetY) - s.windowTop).toFloat()
    val topPadAnim by animateDpAsState(
        topBaseTarget.dp,
        if (snapAll) snap<Dp>() else spring<Dp>(dampingRatio = 0.8f, stiffness = 380f),
        label = "top",
    )
    // Querformat: immer aus der aktuellen (animierten) Höhe mittig zur Kamera, damit beim Wachsen/Schließen nichts verrutscht.
    val topPad = if (land && !topAlert) (centerYLand - height.value / 2f).coerceIn(6f, maxOf(6f, shortDp - height.value - 6f)).dp else topPadAnim
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
    // Meldung nach oben wischen = App stummschalten (große Meldung und die kleine Meldungs-Pille).
    val msgNow by rememberUpdatedState(lingering)
    val canMute = s.muteOnSwipeUp && (mode == Mode.ALERT || mode == Mode.MESSAGE) &&
        (if (mode == Mode.ALERT) (alert ?: lastAlert) else lingering)?.let { it.kind == Kind.OTHER && it.pkg.isNotEmpty() } == true
    LaunchedEffect(canPullRead, canMute) { if (!canPullRead && !canMute) pullY = 0f }
    val canSkip = mode == Mode.COMPACT && s.swipeSkip && current?.kind == Kind.MEDIA && current.media != null
    val curForSkip by rememberUpdatedState(current)
    val stackBehind = if (mode == Mode.ALERT && s.multiNotif) minOf(pendingList.size, 3) else 0
    val stackOffDp = 9f - 7f * s.stackCompact
    val stackInsetDp = 12f - 9f * s.stackCompact
    // Fortschritt 0..1: je weiter die oberste Karte weggezogen wird, desto mehr rückt die nächste nach vorn.
    val stackP: () -> Float = {
        // Die Karten dahinter rücken erst nach dem Loslassen vor (nicht schon beim Ziehen).
        0f
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
    val dir = if (topAlert) 2 else if (land) (if (rot == 1) 2 else 1) else s.expandDir
    val span = if (dir != 0) screenW.toFloat() else minOf(rowBig + 2f * WINDOW_MARGIN, screenW.toFloat())
    val sw = screenW.toFloat()
    // Querformat: Abstand der kleinen Pille zum Rand = der Vertikal-Wert (wie oben am Rand im Hochformat), weich animiert.
    val edgeTarget = if (land) (if (vert) s.landEdge.toFloat() else 8f) else 0f
    val edge by animateFloatAsState(edgeTarget, if (rotSnap) snap() else spring(dampingRatio = 0.85f, stiffness = 380f), label = "edge")
    val anchor = if (topAlert) (sw - alertW) / 2f else if (land) (if (rot == 1) edge else sw - edge) else s.anchorDp(sw)
    // Linke Fensterkante in dp: bei einseitigem Aufgehen hängt die Pille an der festen Kante (Kamera-Seite).
    val winLeft = when (dir) {
        1 -> (anchor - span).coerceIn(0f, maxOf(0f, sw - span))
        2 -> anchor.coerceIn(0f, maxOf(0f, sw - span))
        else -> sw / 2f + s.offsetX - span / 2f
    }
    val slotTarget = if (circleItem != null) slotMax else 0f
    val topTarget = topBaseTarget
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
    // Einseitig: große Blasen sind in der Bildschirmmitte (der Horizontal-Wert gilt nur für die kleine Pille).
    val centerX = if (dir == 0) sw / 2f + s.offsetX else sw / 2f
    val rowLeftScreen = when {
        bigMode && !s.bigFromCamera && !land -> (centerX - rowW / 2f).coerceIn(4f, maxOf(4f, sw - 4f - rowW))
        dir == 1 -> (anchor - rowW).coerceIn(4f, maxOf(4f, sw - 4f - rowW))
        dir == 2 -> anchor.coerceIn(4f, maxOf(4f, sw - 4f - rowW))
        else -> 0f
    }
    val bigMix by animateFloatAsState(if (bigMode && !s.bigFromCamera && !land) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 380f), label = "bigMix")
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
        Modifier.width(span.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
    Row(modifier = (if (dir == 0) Modifier else Modifier.layout { m, c ->
            // Absolute Platzierung: feste Kante (Kamera) oder mittig, weich ineinander übergehend.
            val p = m.measure(Constraints())
            val d = density
            val mg = 4f * d
            val full = sw * d
            val lim = maxOf(mg, full - mg - p.width)
            val a = anchor * d
            val anchored = (if (dir == 1) a - p.width else a).coerceIn(mg, lim)
            val centered = (centerX * d - p.width / 2f).coerceIn(mg, lim)
            val left = anchored + (centered - anchored) * bigMix
            layout(maxOf(c.maxWidth, 0), p.height) { p.place((left - winLeft * d).roundToInt(), 0) }
        })
        .padding(top = maxOf(topPad, 0.dp)), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(slot))

        Box(
            Modifier,
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
                            val d = (i - stackP() + stackShift.value).coerceAtLeast(-0.35f)
                            val inset = stackInsetDp.dp.toPx()
                            val step = (2f * inset / size.width.coerceAtLeast(1f)).coerceAtMost(0.3f)
                            val bl = if (ev.id == backLeaveId) backLeave.value else 0f
                            // Nächste Karte macht beim Vorrücken einen kleinen Satz nach vorn (hebt kurz ab und setzt sich).
                            val hop = if (i == 1) kotlin.math.sin(Math.PI.toFloat() * stackP().coerceIn(0f, 1f)) else 0f
                            val k = (1f - step * d).coerceIn(0.4f, 1.1f) * (1f - 0.1f * bl) * (1f + 0.05f * hop)
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = stackOffDp.dp.toPx() * d + bl * 6.dp.toPx() - hop * 16.dp.toPx()
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
                    // Sobald die vordere Karte losgelassen wird und die nächste zu rücken beginnt, blendet deren Inhalt ein
                    // (Text rutscht ein, Sticker poppt); nach dem Tausch bleibt er unverändert stehen.
                    if (i == 1 && (leave.value > 0.01f || pullY > 0f || abs(swipeX.value) > 0f)) {
                        // Schon beim Herunterziehen/Wischen sichtbar, nicht erst nach dem Loslassen.
                        Box(Modifier.graphicsLayer { alpha = maxOf(leave.value * 2f, pullY / (14f * density), abs(swipeX.value) / (40f * density)).coerceIn(0f, 1f) }) {
                            AlertContent(ev, bsc / landK.coerceAtLeast(0.01f).let { if (topAlert) it else 1f }, Modifier.requiredSize(alertW.dp, alertH.dp), tall = landTall) { }
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
                    translationX = (if (vert) 0f else swipeX.value + stretchPx / 2f) + (if (vert) outSgn * pullY else 0f)
                    translationY = (if (vert) swipeX.value + stretchPx / 2f else pullY) + dropY.value
                    if (rubber != 0f) {
                        // Zieht sich etwas in die Höhe und wird schmaler.
                        if (vert) {
                            // Querformat: dieselbe Dehnung, nur vom Rand weg (nach innen) statt nach unten.
                            scaleX *= 1f + 0.16f * rubber
                            scaleY *= 1f - 0.12f * rubber
                            transformOrigin = TransformOrigin(if (rot == 1) 0f else 1f, 0.5f)
                        } else {
                            scaleY *= 1f + 0.16f * rubber
                            scaleX *= 1f - 0.12f * rubber
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                    }
                    // Gestapelte Karten kippen beim Wischen leicht, fallen mit leichter Schräglage ein.
                    if (stackBehind > 0 || fromStack[0]) {
                        rotationZ = (swipeX.value / size.width.coerceAtLeast(1f)) * 9f +
                            (dropY.value / 30.dp.toPx()) * 5f
                        // Gelesen: kurz anwachsen, dann nach oben zur Pille zusammenschrumpfen.
                        // Die Karten dahinter laufen mit der langsamen Feder; die vordere Karte ist dafür schneller weg.
                        val lv = if (alert?.id == leaveId[0]) leave.value.coerceIn(0f, 1f) else 0f
                        scaleX *= 1f - 0.45f * lv
                        scaleY *= 1f - 0.28f * lv
                        translationY -= lv * 28.dp.toPx()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    // Nachrückende Karte (auch die letzte im Stapel): kommt aus der Stapelposition nach vorn.
                    val fp = frontPromo.value
                    if (fp != 0f) {
                        val st = (2f * stackInsetDp.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(0.3f)
                        val k = 1f - st * fp
                        scaleX *= k; scaleY *= k
                        translationY += stackOffDp.dp.toPx() * fp
                    }
                    if (frontPromo.value != 0f) transformOrigin = TransformOrigin(0.5f, 0f)
                    // Zieht sich beim Ziehen leicht in die Länge.
                    if (pullY > 0f && vert) scaleX *= 1f + (pullY / size.width.coerceAtLeast(1f)) * 0.35f
                    else if (pullY > 0f && !land) scaleY *= 1f + (pullY / size.height.coerceAtLeast(1f)) * 0.35f
                    alpha = (1f - abs(swipeX.value) / ((if (vert) size.height else size.width) * 0.9f)).coerceIn(0f, 1f) * appear.value.coerceIn(0f, 1f).let { if (stackBehind > 0) it * it else it } * wakeFade.value * rotFade.value *
                        (if (stackBehind > 0 || fromStack[0]) (1f - (if (alert?.id == leaveId[0]) leave.value.coerceIn(0f, 1f) else 0f).let { it * it }) else 1f) * (1f - 0.28f * frontPromo.value.coerceIn(0f, 1f))
                }
                .layout { m, _ ->
                    // Beim schnellen Auf- und Zuklappen darf die Feder nicht weit über das Ziel schießen.
                    // Beim Aufklappen wächst die Breite höchstens so schnell wie die Höhe (kein plötzliches Breitwerden beim Spammen).
                    val hp = if (growing && !land && targetH > smallH) ((height.value - smallH) / (targetH - smallH)).coerceIn(0f, 1f) else 1f
                    val wCap = if (growing && land) targetW * 1.07f else if (growing) minOf(targetW * 1.07f, idleW + (targetW - idleW) * (0.3f + 0.7f * hp)) else Float.MAX_VALUE
                    val w = (if (growing) minOf(width.roundToPx(), wCap.dp.roundToPx()) else width.roundToPx()) + (if (vert) 0 else abs(stretchPx).roundToInt())
                    val h = (if (growing) minOf(height.roundToPx(), (targetH * 1.08f).dp.roundToPx()) else height.roundToPx()) + (if (vert) abs(stretchPx).roundToInt() else 0)
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(w, h) { pl.place(0, 0) }
                }
                .clip(shape)
                .background(Bg)
                .border(1.dp, Hairline, shape)
                .pointerInput(canPullRead, canMute, vert, rot) {
                    if (!canPullRead && !canMute) return@pointerInput
                    val maxPull = 30.dp.toPx()
                    val trigger = 18.dp.toPx()
                    // Gummiband: zieht sich ein Stück mit und gibt immer mehr Widerstand.
                    fun band(raw: Float) = maxPull * kotlin.math.tanh(raw / (maxPull * 1.6f))
                    var armed = false
                    val release: () -> Unit = {
                        val y = pullY
                        pullRaw = 0f
                        swipeScope.launch {
                            if (y < -trigger && canMute) {
                                // Nach oben eingerastet: App stummschalten (Test-Meldungen nur zum Ausprobieren, ohne echte Stummschaltung).
                                val ev = if (mode == Mode.ALERT) alertNow else msgNow
                                if (ev != null) {
                                    if (!ev.pkg.startsWith("test.")) IslandBus.mute(ev.pkg, s.muteMinutes)
                                    if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                                    armed = false
                                    skipSquash = true
                                    launch { delay(1500); skipSquash = false }
                                    launch { androidx.compose.animation.core.animate(y, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> pullY = v } }
                                    if (mode == Mode.ALERT) { suppressLinger = true; IslandBus.dismiss() } else lingering = null
                                    IslandBus.post(
                                        IslandEvent(
                                            key = "mute-" + ev.pkg, kind = Kind.STATUS, app = ev.app, title = ev.app.uppercase(),
                                            text = "STUMM · ${s.muteMinutes} MIN", textShort = "${s.muteMinutes} MIN", quiet = true,
                                        ),
                                    )
                                }
                            } else if (y > trigger && canPullRead) {
                                val a = alertNow
                                a?.markRead?.let { pi -> launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { pi.send() } } }
                                if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                                armed = false
                                suppressLinger = true
                                if (pendingList.isNotEmpty() || fromStack[0]) {
                                    // Gelesene Karte schrumpft aus der gezogenen Position heraus (kein Zurückspringen),
                                    // die nächste bleibt vorn.
                                    stackHold = true
                                    launch { androidx.compose.animation.core.animate(y, 0f, animationSpec = tween(190, easing = FastOutSlowInEasing)) { v, _ -> pullY = v } }
                                    leaveId[0] = (alert ?: lastAlert)?.id ?: 0L
                                    leave.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                                    frontPromo.snapTo(1f)
                                    pullY = 0f
                                    IslandBus.dismiss()
                                    leave.snapTo(0f)
                                    stackHold = false
                                } else {
                                    // Der Zug klingt weich aus, während die Meldung schon in die kleine Pille schrumpft
                                    // (kein Sprung der Dehnung, kein Aufblitzen).
                                    launch { androidx.compose.animation.core.animate(y, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> pullY = v } }
                                    IslandBus.dismiss()
                                }
                            } else {
                                androidx.compose.animation.core.animate(y, 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f)) { v, _ -> pullY = v }
                            }
                        }
                    }
                    val onPull: (androidx.compose.ui.input.pointer.PointerInputChange, Float) -> Unit = { change, dragAmount ->
                        change.consume()
                        pullRaw = (pullRaw + dragAmount).coerceIn(if (canMute) -1000f else 0f, if (canPullRead) 1000f else 0f)
                        // Gummiband in beide Richtungen: nach unten = gelesen, nach oben = stumm; rastet mit einem Tick ein.
                        pullY = if (pullRaw < 0f) -band(-pullRaw) else band(pullRaw)
                        val over = abs(pullY) > trigger
                        if (over && !armed && s.haptics) QuickActions.haptic(ctx, 1)
                        armed = over
                    }
                    if (vert) detectHorizontalDragGestures(
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                        onHorizontalDrag = { change, a -> onPull(change, a * outSgn) },
                    ) else detectVerticalDragGestures(
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                        onVerticalDrag = { change, a -> onPull(change, a) },
                    )
                }
                .pointerInput(mode, canSkip, stackBehind, vert) {
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
                        val onStretch: (androidx.compose.ui.input.pointer.PointerInputChange, Float) -> Unit = { change, dragAmount ->
                            change.consume()
                            stretchRaw += dragAmount
                            stretchPx = band(stretchRaw)
                            val over = abs(stretchPx) > trigger
                            if (over && !armed && s.haptics) QuickActions.haptic(ctx, 1)
                            armed = over
                        }
                        // Querformat: Titel überspringen entlang der Kante (unten = weiter).
                        if (vert) detectVerticalDragGestures(
                            onDragEnd = { release() },
                            onDragCancel = { release() },
                            onVerticalDrag = { change, a -> onStretch(change, a) },
                        ) else detectHorizontalDragGestures(
                            onDragEnd = { release() },
                            onDragCancel = { release() },
                            onHorizontalDrag = { change, a -> onStretch(change, a) },
                        )
                        return@pointerInput
                    }
                    if (!swipeable) return@pointerInput
                    // Wegwischen: kleine Pille reagiert schon auf kurzes Wischen und auf schnelles Schnippen.
                    var vel = 0f
                    var lastT = 0L
                    val away = if (mode == Mode.MESSAGE) 26.dp.toPx() else 80.dp.toPx()
                    val flingV = 650.dp.toPx() // px pro Sekunde
                    val ext = { if (vert) size.height.toFloat() else size.width.toFloat() }
                    val onSwipeDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Float) -> Unit = { change, dragAmount ->
                        change.consume()
                        val dt = change.uptimeMillis - lastT
                        if (lastT != 0L && dt > 0) vel = vel * 0.5f + (dragAmount / dt * 1000f) * 0.5f
                        lastT = change.uptimeMillis
                        swipeScope.launch { swipeX.snapTo(swipeX.value + dragAmount) }
                    }
                    val dragEnd: () -> Unit = {
                        val x = swipeX.value
                        val flung = abs(vel) > flingV && abs(x) > 8.dp.toPx() && sign(vel) == sign(x)
                        val x2 = if (flung && abs(x) <= away) sign(x) * (away + 1f) else x
                        swipeScope.launch {
                            if (abs(x2) > away && (stackBehind > 0 || fromStack[0])) {
                                // Karte fliegt weg, gleichzeitig rücken die Karten dahinter federnd nach vorn; erst danach wird getauscht.
                                leaveId[0] = (alert ?: lastAlert)?.id ?: 0L
                                val adv = launch { leave.animateTo(1f, tween(210, easing = FastOutSlowInEasing)) }
                                swipeX.animateTo(sign(x) * ext() * 0.7f, tween(150))
                                adv.join()
                                suppressLinger = true
                                frontPromo.snapTo(1f)
                                swipeX.snapTo(0f)
                                IslandBus.dismiss()
                                leave.snapTo(0f)
                                if (s.haptics) QuickActions.haptic(ctx, 1)
                            } else if (abs(x2) > away) {
                                swipeX.animateTo(sign(x2) * ext() * 0.8f, tween(if (flung) 110 else 160, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                                snapNext = true
                                appear.snapTo(0f)
                                onSwipeAway()
                                swipeX.snapTo(0f)
                                launch { delay(60); snapNext = false }
                                appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 360f))
                                snapNext = false
                            } else {
                                swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                            }
                        }
                    }
                    val dragCancel: () -> Unit = { swipeScope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) } }
                    // Querformat: kleine Pille wird entlang der Kante (nach oben/unten) weggewischt.
                    if (vert) detectVerticalDragGestures(
                        onDragStart = { vel = 0f; lastT = 0L },
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragCancel() },
                        onVerticalDrag = { change, a -> onSwipeDrag(change, a) },
                    ) else detectHorizontalDragGestures(
                        onDragStart = { vel = 0f; lastT = 0L },
                        onDragEnd = {
                            val x = swipeX.value
                            val flung = abs(vel) > flingV && abs(x) > 8.dp.toPx() && sign(vel) == sign(x)
                            val x2 = if (flung && abs(x) <= away) sign(x) * (away + 1f) else x
                            swipeScope.launch {
                                if (abs(x2) > away && stackBehind > 0) {
                                    // Oberste Karte fliegt zur Seite und ist weg, die nächste rückt nach vorn.
                                    swipeX.animateTo(sign(x) * size.width * 0.7f, tween(150))
                                    suppressLinger = true
                                    // Die nächste Karte ist durch das Wegziehen schon groß und deckend: nahtlos übernehmen.
                                    IslandBus.dismiss()
                                    swipeX.snapTo(0f)
                                    if (s.haptics) QuickActions.haptic(ctx, 1)
                                } else if (abs(x2) > away) {
                                    swipeX.animateTo(sign(x2) * size.width * 0.8f, tween(if (flung) 110 else 160, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
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
                            val dt = change.uptimeMillis - lastT
                            if (lastT != 0L && dt > 0) vel = vel * 0.5f + (dragAmount / dt * 1000f) * 0.5f
                            lastT = change.uptimeMillis
                            swipeScope.launch { swipeX.snapTo(swipeX.value + dragAmount) }
                        },
                    )
                }
                .pointerInput(mode, s.swipeUpAction, s.swipeDownPause, vert, rot) {
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
                    val endRub: () -> Unit = {
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
                    }
                    val onRub: (androidx.compose.ui.input.pointer.PointerInputChange, Float) -> Unit = { change, dragAmount ->
                        change.consume()
                        total = (total + dragAmount).coerceAtLeast(0f)
                        rubber = kotlin.math.tanh(total / (maxPull * 1.4f))
                        val over = total > trigger
                        if (over && !armed && s.haptics) QuickActions.haptic(ctx, 1)
                        armed = over
                    }
                    if (vert) detectHorizontalDragGestures(
                        onDragStart = { total = 0f; armed = false },
                        onDragEnd = { endRub() },
                        onDragCancel = { total = 0f; snapBack() },
                        onHorizontalDrag = { change, a -> onRub(change, a * outSgn) },
                    ) else detectVerticalDragGestures(
                        onDragStart = { total = 0f; armed = false },
                        onDragEnd = { endRub() },
                        onDragCancel = { total = 0f; snapBack() },
                        onVerticalDrag = { change, a -> onRub(change, a) },
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
                                transformOrigin = if (land) TransformOrigin(if (rot == 1) 0f else 1f, 0.5f) else TransformOrigin(0.5f, if (toBig) 0f else 0.5f),
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
                    Mode.IDLE -> if (land) IdleColumn(sc) else IdleDots(sc, camSide = s.expandDir, offsetX = if (s.expandDir == 0) 0f else (if (s.expandDir == 1) 1f else -1f) * (idleW / 2f - maxOf(gap / 2f, 15f * sc)))
                    Mode.NOTE -> NotePanel(s.notes, bsc, Modifier.requiredSize(bigW.dp, noteH.dp), onUsed = { touch++ })
                    Mode.CALC -> CalcPanel(bsc, Modifier.requiredSize(bigW.dp, calcH.dp)) { touch++ }
                    Mode.QUICK -> QuickPanel(quickPages, bsc, Modifier.requiredSize(bigW.dp, quickH.dp), { touch++ }, if (land) 2 else 4)
                    Mode.COMPACT -> shownCurrent?.let {
                        if (land) CompactColumn(it, sc, gap, s.equalizer, s.workoutMetric, Modifier.requiredSize(smallH.dp, targetH.dp))
                        else CompactContent(it, sc, gap, s.iconTextGap.toFloat(), s.equalizer, s.workoutMetric, Modifier.requiredSize(compactW.dp, smallH.dp), s.expandDir)
                    }
                    Mode.MESSAGE -> (lingering ?: lastMessage)?.let {
                        if (land) MessageColumn(it, sc, gap, Modifier.requiredSize(smallH.dp, targetH.dp))
                        else MessageContent(it, sc, gap, s.iconTextGap.toFloat(), Modifier.requiredSize(messageW.dp, smallH.dp), s.expandDir)
                    }
                    Mode.ALERT -> (alert ?: lastAlert)?.let {
                        Box { AlertContent(it, bsc / landK.coerceAtLeast(0.01f).let { if (topAlert) it else 1f }, Modifier.requiredSize(alertW.dp, alertH.dp), if (s.multiNotif) pendingList.size else 0, tall = landTall, onMarkRead = {
                            // Als gelesen markieren: Aktion der App auslösen, Meldung verschwindet (nächste kommt nach).
                            it.markRead?.let { pi -> swipeScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { pi.send() } } }
                            if (s.haptics) QuickActions.haptic(ctx, s.hapticLevel)
                            suppressLinger = true
                            if (pendingList.isNotEmpty() || fromStack[0]) swipeScope.launch {
                                leaveId[0] = it.id
                                leave.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                                frontPromo.snapTo(1f)
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
                        } }
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
                            tall = land,
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
                            tall = land,
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
    // Pausiert (Musik oder Timer): die Zeit wird leicht grau.
    val paused = (e.kind == Kind.MEDIA && !e.playing) || (e.kind == Kind.TIMER && e.chronoBase == null)
    val timeColor by androidx.compose.animation.animateColorAsState(if (paused) TextGray else Fg, tween(250), label = "timeColor")
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
                    if (e.chronoBase != null) Chrono(e, (12f * sc).sp, color = timeColor)
                    else Text(
                        text = e.rightText ?: "",
                        color = timeColor,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        fontSize = (12f * sc).sp,
                        maxLines = 1,
                    )
                }
                e.rightText != null -> Text(
                    text = e.rightText,
                    color = timeColor,
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
    val txt = chronoText(e) ?: return
    val rollSetting = IslandBus.settings.collectAsState().value.rollDigits
    RollingText(txt, size, color, rollSetting, modifier)
}

/** Laufende Zeit als Text (null, wenn kein Zähler). Tickt nur bei eingeschaltetem Bildschirm. */
@Composable
internal fun chronoText(e: IslandEvent): String? {
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
    val base = e.chronoBase ?: return null
    val ms = if (e.countDown) base - now else now - base
    return formatClock(ms)
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


/**
 * Querformat, Einstellung "Meldungen oben in der Mitte": Die Meldung ist ein eigenes Element oben in der Mitte.
 * Sie rutscht von oben ein und nach der Anzeigezeit schnell wieder nach oben weg. Die Pille am Rand bleibt unberührt.
 * Weitere Meldungen stapeln sich dahinter: Eine neue fällt von oben auf den Stapel, die bisherige rutscht eine Stufe zurück.
 */
@Composable
private fun TopAlertBubble(
    s: IslandSettings,
    alert: IslandEvent?,
    hidden: Boolean,
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
                    ghostX = 0f
                    ghost = old
                    launch { ghostP.snapTo(0f); ghostP.animateTo(1f, tween(170, easing = FastOutSlowInEasing)); ghost = null }
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
    val show = ev != null && p.value > 0.002f
    SideEffect {
        if (visible && ev != null) IslandBus.setTouchRect2(left - 4f, top - 4f, w + 8f, h + 8f + behind.size * 8f)
        else IslandBus.setTouchRect2(0f, 0f, 1f, 1f)
    }
    if (!show || ev == null) return
    val swipe = remember { Animatable(0f) }
    val pull = remember { Animatable(0f) }
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
                    translationX = swipe.value
                    val landing = 1f - 0.07f * (abs(drop.value) / (40f * density)).coerceIn(0f, 1f)
                    val fi = front.value
                    translationY += fi * 8f * density
                    val sk = pop.value * (0.9f + 0.1f * k.coerceAtMost(1f)) * landing * (1f - 0.05f * fi)
                    scaleX = sk; scaleY = sk
                    alpha = (k * 2.2f).coerceIn(0f, 1f) * (1f - abs(swipe.value) / (w * density * 0.9f)).coerceIn(0f, 1f) * (1f - 0.28f * fi)
                }
                .clip(shape)
                .background(Bg)
                .border(1.dp, Hairline, shape)
                .pointerInput(Unit) {
                    var vel = 0f
                    var last = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { vel = 0f; last = 0L },
                        onDragEnd = {
                            val x = swipe.value
                            val away = 70.dp.toPx()
                            val flung = abs(vel) > 650.dp.toPx() && abs(x) > 8.dp.toPx() && sign(vel) == sign(x)
                            scope.launch {
                                if (abs(x) > away || flung) {
                                    swipe.animateTo(sign(x) * size.width * 0.8f, tween(130, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                                    // Weggewischte Karte bleibt als Geist an ihrer Position und blendet dort aus (nicht zurückspringen).
                                    ghostX = swipe.value
                                    ghost = ev
                                    hold.skipGhost = true
                                    scope.launch { ghostP.snapTo(0f); ghostP.animateTo(1f, tween(140)); ghost = null }
                                    onAway()
                                    swipe.snapTo(0f)
                                } else swipe.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) } },
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
                        onDragEnd = {
                            val y = pull.value
                            raw = 0f
                            armed = false
                            scope.launch {
                                if (y < -trigger && canMute) onMute(ev)
                                else if (y > trigger && canRead) onMarkRead(ev)
                                pull.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                            }
                        },
                        onDragCancel = { raw = 0f; armed = false; scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)) } },
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
                        translationY = -k * 18f * density
                        translationX = ghostX
                        scaleX = 1f - 0.1f * k; scaleY = 1f - 0.1f * k
                        alpha = 1f - k
                    }
                    .clip(shape)
                    .background(Bg)
                    .border(1.dp, Hairline, shape),
            ) { AlertContent(g, sc, Modifier.requiredSize(w.dp, h.dp), 0, instant = true, onMarkRead = {}, onCopyCode = {}) {} }
        }
    }
}

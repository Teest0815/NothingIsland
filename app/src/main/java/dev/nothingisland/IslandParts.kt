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

// Kleine Bausteine der Pille: Symbole, Inhalte (kompakt/Meldung), Uhr, Texte.

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

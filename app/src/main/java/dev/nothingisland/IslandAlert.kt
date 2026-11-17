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

/** Aufgeklappte Meldung bei neuer Benachrichtigung. */
@Composable
internal fun AlertContent(event: IslandEvent, sc: Float, modifier: Modifier, more: Int = 0, onMarkRead: () -> Unit = {}, onCopyCode: () -> Unit = {}, onReply: () -> Unit) {
    Row(
        modifier = modifier.padding(horizontal = (18f * sc).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size((48f * sc).dp)) {
            DotRing(Modifier.fillMaxSize())
            val usePic = IslandBus.settings.collectAsState().value.pillIconNotif == 1 && event.avatar != null
            if (usePic) {
                Image(
                    bitmap = event.avatar!!,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size((34f * sc).dp).clip(CircleShape),
                )
            } else event.icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Fg),
                    modifier = Modifier.size((20f * sc).dp),
                )
            }
        }
        Spacer(Modifier.width((14f * sc).dp))
        // Nur beim ersten Erscheinen einblenden, nicht bei jedem Kartenwechsel im Stapel (sonst blitzt der Text auf).
        val reveal = remember { Animatable(0f) }
        LaunchedEffect(Unit) { reveal.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
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
        event.picture?.let { pic ->
            Spacer(Modifier.width((10f * sc).dp))
            // Bild/Sticker poppt federnd auf.
            val pop = remember(pic) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(pic) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 300f)) }
            Image(
                bitmap = pic,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .size((52f * sc).dp)
                    .graphicsLayer {
                        val k = pop.value
                        scaleX = k
                        scaleY = k
                        rotationZ = (1f - k) * -14f
                        alpha = (k * 2f).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape((10f * sc).dp)),
            )
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
internal fun AppLabel(app: String, sc: Float) {
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
internal fun OngoingExpanded(
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
                    RingIcon(e, sc)
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

internal val dangerWords = listOf(
    "stop", "stopp", "beend", "cancel", "abbrech", "dismiss", "auflegen", "hang up", "decline", "ablehn",
)

@Composable
internal fun CallToggle(label: String, on: Boolean, sc: Float, onClick: () -> Unit) {
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
internal fun CallControls(e: IslandEvent, sc: Float, onUsed: () -> Unit) {
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
internal fun ActionButton(a: IslandAction, sc: Float, onUsed: () -> Unit) {
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
internal fun DotRing(modifier: Modifier = Modifier, dots: Int = 24) {
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

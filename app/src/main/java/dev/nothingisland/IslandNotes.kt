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
internal fun NotePanel(notesJson: String, sc: Float, modifier: Modifier, onUsed: () -> Unit, bottom: (@Composable () -> Unit)? = null, middle: (@Composable () -> Unit)? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val notes = remember(notesJson) { IslandBus.noteList(notesJson) }
    val shown = remember(notesJson) {
        val cnt = HashMap<String, Int>()
        // Stabile Schlüssel (Text + Vorkommen), damit Einfügen und Löschen sauber animiert werden.
        notes.mapIndexed { i, tx -> val n = (cnt[tx] ?: 0) + 1; cnt[tx] = n; Triple("$tx#$n", i, tx) }.asReversed()
    }
    Column(modifier.padding(horizontal = (16f * sc).dp, vertical = (12f * sc).dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NOTIZEN", color = TextGray, fontFamily = Mono, fontWeight = FontWeight.Bold,
                fontSize = (10f * sc).sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f),
            )
            if (shown.size > 1) Text(
                "ALLE TEILEN ↗", color = NothingRed, fontFamily = Mono, fontWeight = FontWeight.Bold,
                fontSize = (10f * sc).sp, letterSpacing = 1.sp,
                modifier = Modifier.clickable { onUsed(); shareNote(ctx, notes.asReversed().joinToString("\n\n")) }.padding(4.dp),
            )
        }
        Spacer(Modifier.height((6f * sc).dp))
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var prevCount by remember { mutableIntStateOf(shown.size) }
        LaunchedEffect(shown.size) {
            // Neue Notiz: ganz nach oben scrollen, damit man sie sieht.
            if (shown.size > prevCount) runCatching { listState.animateScrollToItem(0) }
            prevCount = shown.size
        }
        if (middle != null) Box(Modifier.weight(1f).fillMaxWidth()) { middle() }
        else androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy((6f * sc).dp),
        ) {
            if (shown.isEmpty()) item(key = "empty") {
                Text("Noch keine Notiz.", color = TextGray, fontSize = (13f * sc).sp, modifier = Modifier.padding(top = (8f * sc).dp))
            }
            items(shown, key = { it.first }) { (_, i, text) ->
                Row(
                    Modifier
                        .animateItem(
                            fadeInSpec = spring(stiffness = 380f),
                            placementSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                            fadeOutSpec = tween(170),
                        )
                        .fillMaxWidth().clip(RoundedCornerShape((12f * sc).dp)).background(ButtonBg)
                        .padding(start = (12f * sc).dp, top = (4f * sc).dp, bottom = (4f * sc).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text, color = Fg, fontSize = (13f * sc).sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = (4f * sc).dp)
                            .pointerInput(text) { detectTapGestures { onUsed(); QuickActions.copyCode(ctx, text, raw = true) } },
                    )
                    Box(
                        Modifier.size((34f * sc).dp).clickable { onUsed(); shareNote(ctx, text) },
                        contentAlignment = Alignment.Center,
                    ) { Text("↗", color = NothingRed, fontSize = (14f * sc).sp) }
                    Box(
                        Modifier.size((34f * sc).dp).clickable { onUsed(); IslandBus.removeNote(ctx, i) },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = TextGray, fontSize = (12f * sc).sp) }
                }
            }
        }
        Spacer(Modifier.height((8f * sc).dp))
        if (bottom != null) { bottom(); return@Column }
        val src = remember { MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().height((40f * sc).dp)
                // Schon beim Aufsetzen des Fingers reagieren (Eingabefenster braucht einen Moment zum Aufbauen).
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onUsed()
                        IslandBus.startReply(
                            IslandEvent(
                                app = "NOTIZEN", title = "Neue Notiz", text = "Schreib etwas auf",
                                noteSink = { t -> IslandBus.addNote(ctx, t) },
                            ),
                        )
                    }
                }
                .bounceClick({}, src)
                .clip(RoundedCornerShape(50))
                .background(if (down) Color.White else NothingRed),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+  NEUE NOTIZ", color = if (down) Color.Black else OnAccent, fontFamily = Mono,
                fontWeight = FontWeight.Bold, fontSize = (11f * sc).sp, letterSpacing = 1.sp,
            )
        }
    }
}

/** Notiz über das Android-Teilen-Menü weitergeben (z. B. an Google Docs oder Keep). */
internal fun shareNote(ctx: android.content.Context, text: String) {
    runCatching {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, text)
        ctx.startActivity(
            android.content.Intent.createChooser(send, "Notiz teilen")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        IslandBus.collapse()
    }
}

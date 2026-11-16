package dev.nothingisland

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Antwort-Leiste direkt in der Island: Texteingabe an der Stelle der aufgeklappten Pille. */
@Composable
fun ReplyBar() {
    val st by IslandBus.settings.collectAsState()
    IslandTheme.apply(st)
    val event by IslandBus.replyTarget.collectAsState()
    val closing by IslandBus.replyClosing.collectAsState()
    var lastEvent by remember { mutableStateOf<IslandEvent?>(null) }
    if (event != null) lastEvent = event
    val e = event ?: lastEvent ?: return
    val closeK by androidx.compose.animation.core.animateFloatAsState(
        if (closing) 0f else 1f, tween(if (closing) (if (e.noteSink != null) 110 else 230) else 0), label = "replyClose",
    )
    val noteOpen by IslandBus.note.collectAsState()
    val closeFx = Modifier.graphicsLayer {
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        if (e.noteSink == null || !noteOpen) {
            // Geschlossen (Antwort gesendet/abgebrochen, Notiz zu): Bubble schrumpft in die kleine Pille.
            alpha = (closeK * 1.8f).coerceAtMost(1f)
            scaleX = 0.34f + 0.66f * closeK
            scaleY = 0.10f + 0.90f * closeK
        } else {
            alpha = closeK
            val k = 0.96f + 0.04f * closeK
            scaleX = k; scaleY = k
        }
    }
    val reply = e.reply
    val sink = e.noteSink
    if (reply == null && sink == null) return
    val ctx = LocalContext.current
    val sc = st.scale * st.expandedScale
    val fsc = st.scale * minOf(st.expandedScale, st.expandedFrameMax)
    var text by remember(e.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(e.id) {
        appear.animateTo(1f, tween(220))
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val scope = rememberCoroutineScope()
    // Knopf "+ NEUE NOTIZ" <-> "SPEICHERN": 0 = Knopf, 1 = Eingabe.
    val morph = remember(e.id) { Animatable(0f) }
    LaunchedEffect(e.id) {
        if (sink != null) morph.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 650f))
    }
    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        if (sink != null) {
            // Speichern: Notiz rutscht in die Liste (auch in der Island darunter), dann blendet das Fenster aus.
            sink(t)
            text = ""
            scope.launch {
                keyboard?.hide()
                // Speichern wird wieder zu "+ NEUE NOTIZ", die Notiz rutscht oben in die Liste.
                morph.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 520f))
                delay(60)
                IslandBus.endReply()
            }
            return
        }
        val rp = reply ?: return
        val data = Intent()
        val b = Bundle().apply { putCharSequence(rp.remoteInput.resultKey, t) }
        RemoteInput.addResultsToIntent(arrayOf(rp.remoteInput), data, b)
        runCatching { rp.action.send(ctx, 0, data) }
        keyboard?.hide()
        IslandBus.endReply()
    }
    val shape = RoundedCornerShape((minOf(st.expandedCorner * fsc, 42f * fsc)).dp)
    if (sink != null) {
        // Schreiben-Bubble sieht aus wie die Notiz-Bubble, nur mit Eingabefeld statt Knopf.
        Box(Modifier.fillMaxWidth().then(closeFx).padding(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height((250f * fsc).dp).clip(shape)
                    .background(IslandTheme.bg).border(1.dp, IslandTheme.hairline, shape),
            ) {
                NotePanel(st.notes, sc, Modifier.fillMaxSize(), onUsed = {}, bottom = {
                    // Der rote Knopf "+ NEUE NOTIZ" zieht sich zu "SPEICHERN" zusammen, das Feld wächst daneben auf.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height((40f * sc).dp)) {
                        val total = maxWidth
                        val btnW = (96f * sc).dp
                        val gap = (8f * sc).dp
                        val k = morph.value
                        val kc = k.coerceIn(0f, 1f)
                        val fieldW = ((total - btnW - gap) * k).coerceAtLeast(0.dp)
                        val btn = (total * (1f - k) + btnW * k).coerceAtLeast(btnW * 0.6f)
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            if (fieldW > 2.dp) {
                                Box(
                                    Modifier.width(fieldW).fillMaxHeight().clip(RoundedCornerShape(50))
                                        .background(IslandTheme.buttonBg)
                                        .padding(horizontal = (14f * sc).dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Box(Modifier.graphicsLayer { alpha = ((kc - 0.35f) / 0.65f).coerceIn(0f, 1f) }) {
                                        if (text.isEmpty()) Text("Notiz schreiben …", color = IslandTheme.textGray, fontSize = (13f * sc).sp, maxLines = 1)
                                        BasicTextField(
                                            value = text,
                                            onValueChange = { text = it },
                                            singleLine = true,
                                            textStyle = TextStyle(color = IslandTheme.fg, fontSize = (13f * sc).sp),
                                            cursorBrush = SolidColor(IslandTheme.accent),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { send() }),
                                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(gap * kc))
                            Box(
                                Modifier.width(btn).fillMaxHeight().clip(RoundedCornerShape(50))
                                    .background(IslandTheme.accent)
                                    .clickable { send() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+  NEUE NOTIZ", color = IslandTheme.onAccent, fontFamily = IslandTheme.font,
                                    fontWeight = FontWeight.Bold, fontSize = (11f * sc).sp, letterSpacing = 1.sp, maxLines = 1,
                                    modifier = Modifier.graphicsLayer { alpha = (1f - kc * 2f).coerceIn(0f, 1f) },
                                )
                                Text(
                                    "SPEICHERN", color = IslandTheme.onAccent, fontFamily = IslandTheme.font,
                                    fontWeight = FontWeight.Bold, fontSize = (10f * sc).sp, letterSpacing = 1.sp, maxLines = 1,
                                    modifier = Modifier.graphicsLayer { alpha = (kc * 2f - 1f).coerceIn(0f, 1f) },
                                )
                            }
                        }
                    }
                })
            }
        }
        return
    }
    var expanded by remember(e.id) { mutableStateOf(false) }
    val alertH = 84f * fsc
    val fullH = alertH + 62f * sc
    val h by animateDpAsState(
        targetValue = (if (expanded) fullH else alertH).dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 520f),
        label = "replyH",
    )
    LaunchedEffect(e.id) {
        delay(16)
        expanded = true
        delay(110)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val progress = ((h.value - alertH) / (fullH - alertH)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().then(closeFx).padding(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .clip(shape)
                .background(IslandTheme.bg)
                .border(1.dp, IslandTheme.hairline, shape),
        ) {
            // Oben dieselbe Meldung wie in der Island, darunter wächst das Eingabefeld heraus.
            AlertContent(e.copy(reply = null, markRead = null), sc, Modifier.fillMaxWidth().height(alertH.dp)) {}
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = progress }
                    .padding(start = (16f * sc).dp, end = (16f * sc).dp, bottom = (14f * sc).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((8f * sc).dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(IslandTheme.buttonBg)
                        .padding(horizontal = (14f * sc).dp, vertical = (10f * sc).dp),
                ) {
                    if (text.isEmpty()) {
                        Text(if (sink != null) "Notiz schreiben …" else "Antwort schreiben …", color = IslandTheme.textGray, fontSize = (13f * sc).sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = IslandTheme.fg, fontSize = (13f * sc).sp),
                        cursorBrush = SolidColor(IslandTheme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(IslandTheme.accent)
                        .clickable { send() }
                        .padding(horizontal = (14f * sc).dp, vertical = (10f * sc).dp),
                ) {
                    Text(
                        if (sink != null) "SPEICHERN" else "SENDEN",
                        color = IslandTheme.onAccent,
                        fontFamily = IslandTheme.font,
                        fontWeight = FontWeight.Bold,
                        fontSize = (10f * sc).sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

package dev.nothingisland

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val e = event ?: return
    val reply = e.reply ?: return
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
    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        val data = Intent()
        val b = Bundle().apply { putCharSequence(reply.remoteInput.resultKey, t) }
        RemoteInput.addResultsToIntent(arrayOf(reply.remoteInput), data, b)
        runCatching { reply.action.send(ctx, 0, data) }
        keyboard?.hide()
        IslandBus.endReply()
    }
    val shape = RoundedCornerShape((minOf(st.expandedCorner * fsc, 42f * fsc)).dp)
    var expanded by remember(e.id) { mutableStateOf(false) }
    LaunchedEffect(e.id) {
        delay(60)
        expanded = true
        delay(140)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Box(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IslandTheme.bg)
                .border(1.dp, IslandTheme.hairline, shape)
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f)),
        ) {
            // Oben dieselbe Meldung wie in der Island, darunter wächst das Eingabefeld heraus.
            AlertContent(e.copy(reply = null), sc, Modifier.fillMaxWidth().height((84f * fsc).dp)) {}
            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = (16f * sc).dp, end = (16f * sc).dp, bottom = (14f * sc).dp),
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
                            Text("Antwort schreiben …", color = IslandTheme.textGray, fontSize = (13f * sc).sp)
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
                            "SENDEN",
                            color = androidx.compose.ui.graphics.Color.White,
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
}

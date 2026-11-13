package dev.nothingisland

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.core.Animatable
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
    val shape = RoundedCornerShape((st.expandedCorner * fsc).dp.coerceAtMost(34.dp))
    Box(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = 0.92f + 0.08f * appear.value
                    scaleX = p; scaleY = p; alpha = appear.value
                }
                .clip(shape)
                .background(IslandTheme.bg)
                .border(1.dp, IslandTheme.hairline, shape)
                .padding(horizontal = (16f * sc).dp, vertical = (12f * sc).dp),
        ) {
            Text(
                text = "ANTWORT AN ${e.title.ifBlank { e.app }.uppercase()}",
                color = IslandTheme.accent,
                fontFamily = IslandTheme.font,
                fontSize = (10f * sc).sp,
                letterSpacing = 1.4.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height((8f * sc).dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((8f * sc).dp)) {
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

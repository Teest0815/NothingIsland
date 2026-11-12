package dev.nothingisland

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kleines Eingabefenster oben: Antwort auf eine Messenger-Benachrichtigung schreiben und abschicken. */
class ReplyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandBus.loadPrefs(this)
        IslandTheme.apply(IslandBus.settings.value)
        val event = IslandBus.eventById(intent.getLongExtra("id", -1L))
        val reply = event?.reply
        if (event == null || reply == null) {
            finish()
            return
        }
        setContent {
            var text by remember { mutableStateOf("") }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            fun send() {
                val t = text.trim()
                if (t.isEmpty()) return
                val data = Intent()
                val b = Bundle().apply { putCharSequence(reply.remoteInput.resultKey, t) }
                RemoteInput.addResultsToIntent(arrayOf(reply.remoteInput), data, b)
                runCatching { reply.action.send(this@ReplyActivity, 0, data) }
                finish()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 72.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(IslandTheme.bg)
                        .border(1.dp, IslandTheme.hairline, shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(18.dp),
                ) {
                    Text(
                        event.app.uppercase(),
                        color = IslandTheme.accent,
                        fontFamily = IslandTheme.font,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                    )
                    Text(
                        event.title,
                        color = IslandTheme.fg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (event.text.isNotBlank()) {
                        Text(event.text, color = IslandTheme.textGray, fontSize = 12.sp, maxLines = 2)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(IslandTheme.buttonBg)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            if (text.isEmpty()) {
                                Text("Antwort schreiben …", color = IslandTheme.textGray, fontSize = 14.sp)
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it },
                                textStyle = TextStyle(color = IslandTheme.fg, fontSize = 14.sp),
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
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Text(
                                "SENDEN", color = Color.White, fontFamily = IslandTheme.font,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

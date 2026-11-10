package dev.nothingisland

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {

    // Wird in onResume erhöht, damit der Permission-Status nach Rückkehr aus den Settings neu gelesen wird.
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandBus.loadPrefs(this)
        setContent { SettingsScreen(refreshKey = refresh) }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    @Composable
    private fun SettingsScreen(refreshKey: Int) {
        @Suppress("UNUSED_VARIABLE") val key = refreshKey
        val overlayOk = Settings.canDrawOverlays(this)
        val listenerOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val idle by IslandBus.idleVisible.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                "NOTHING\nISLAND",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = 3.sp,
                lineHeight = 38.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Dynamic Island im Nothing-Stil",
                color = Color(0xFF9A9A9A),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(40.dp))

            StepRow(
                index = "01",
                title = "Über anderen Apps anzeigen",
                done = overlayOk,
            ) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            Spacer(Modifier.height(12.dp))
            StepRow(
                index = "02",
                title = "Zugriff auf Benachrichtigungen",
                done = listenerOk,
            ) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Ruhe-Pill anzeigen",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = idle,
                    onCheckedChange = { IslandBus.setIdleVisible(this@MainActivity, it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NothingRed,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF1A1A1A),
                        uncheckedThumbColor = Color(0xFF777777),
                    ),
                )
            }

            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(NothingRed)
                    .clickable {
                        IslandBus.post(
                            IslandEvent(
                                app = "Nothing Island",
                                title = "Test-Benachrichtigung",
                                text = "So sieht deine Island aus.",
                            )
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "TEST ANZEIGEN",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 13.sp,
                )
            }
        }
    }

    @Composable
    private fun StepRow(index: String, title: String, done: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(index, color = NothingRed, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (done) Color.White else Color(0xFF3A3A3A))
            )
        }
    }
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import kotlin.math.roundToInt

private val CardBorder = Color(0xFF2A2A2A)
private val Muted = Color(0xFF9A9A9A)

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
        // Solange die Einstellungen offen sind, zeigt die Island sich immer (Live-Vorschau).
        IslandBus.setPreview(true)
        IslandListenerService.rebind(this)
        IslandListenerService.instance?.ensureOverlay()
        refresh++
    }

    override fun onPause() {
        IslandBus.setPreview(false)
        super.onPause()
    }

    @Composable
    private fun SettingsScreen(refreshKey: Int) {
        @Suppress("UNUSED_VARIABLE") val key = refreshKey
        val ctx = this@MainActivity
        val s by IslandBus.settings.collectAsState()
        val overlayOk = Settings.canDrawOverlays(ctx)
        val widthCap = (resources.configuration.screenWidthDp - 8).coerceAtLeast(220)
        val listenerOk = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(packageName)
        fun edit(t: (IslandSettings) -> IslandSettings) = IslandBus.updateSettings(ctx, t)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(72.dp)) // Platz, damit die Island oben nicht den Titel verdeckt
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
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )

            SectionLabel("SETUP")
            StepRow("01", "Über anderen Apps anzeigen", overlayOk) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            Spacer(Modifier.height(12.dp))
            StepRow("02", "Zugriff auf Benachrichtigungen", listenerOk) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            SectionLabel("POSITION & GRÖSSE")
            Card {
                SettingSlider(
                    label = "Horizontal",
                    valueText = "${s.offsetX} dp",
                    value = s.offsetX.toFloat(),
                    range = -150f..150f,
                ) { v -> edit { it.copy(offsetX = v.roundToInt()) } }
                SettingSlider(
                    label = "Abstand von oben",
                    valueText = "${s.offsetY} dp",
                    value = s.offsetY.toFloat(),
                    range = 0f..300f,
                ) { v -> edit { it.copy(offsetY = v.roundToInt()) } }
                SettingSlider(
                    label = "Größe",
                    valueText = "${(s.scale * 100).roundToInt()} %",
                    value = s.scale,
                    range = 0.7f..1.5f,
                ) { v -> edit { it.copy(scale = (v * 20f).roundToInt() / 20f) } }
                SettingSlider(
                    label = "Breite der kleinen Pille",
                    valueText = "${(s.widthFactor * 100).roundToInt()} %",
                    value = s.widthFactor,
                    range = 0.8f..1.8f,
                ) { v -> edit { it.copy(widthFactor = (v * 20f).roundToInt() / 20f) } }
                SettingSlider(
                    label = "Maximale Breite (kleine Pille)",
                    valueText = "${s.maxWidth} dp",
                    value = s.maxWidth.toFloat(),
                    range = 140f..480f,
                ) { v -> edit { it.copy(maxWidth = v.roundToInt()) } }
                SettingSlider(
                    label = "Kamera-Lücke (Mitte bleibt frei)",
                    valueText = "${s.cameraGap} dp",
                    value = s.cameraGap.toFloat(),
                    range = 0f..100f,
                ) { v -> edit { it.copy(cameraGap = v.roundToInt()) } }
                SettingSlider(
                    label = "Abrundung der kleinen Pille",
                    valueText = "${(s.smallRoundness * 100).roundToInt()} %",
                    value = s.smallRoundness,
                    range = 0.2f..1f,
                ) { v -> edit { it.copy(smallRoundness = (v * 20f).roundToInt() / 20f) } }
            }

            SectionLabel("AUFGEKLAPPTE PILLE")
            Card {
                SettingSlider(
                    label = "Größe",
                    valueText = "${(s.expandedScale * 100).roundToInt()} %",
                    value = s.expandedScale,
                    range = 0.7f..1.5f,
                ) { v ->
                    edit { it.copy(expandedScale = (v * 20f).roundToInt() / 20f) }
                    IslandBus.post(previewAlert())
                }
                SettingSlider(
                    label = "Breite",
                    valueText = "${minOf(s.expandedWidth, widthCap)} dp",
                    value = s.expandedWidth.toFloat(),
                    range = 200f..widthCap.toFloat(),
                ) { v ->
                    edit { it.copy(expandedWidth = v.roundToInt()) }
                    IslandBus.post(previewAlert())
                }
                SettingSlider(
                    label = "Abrundung der Ecken",
                    valueText = "${s.expandedCorner} dp",
                    value = s.expandedCorner.toFloat(),
                    range = 0f..60f,
                ) { v ->
                    edit { it.copy(expandedCorner = v.roundToInt()) }
                    IslandBus.post(previewAlert())
                }
                Text(
                    "Gilt für neue Meldungen und für die aufgeklappte Timer-/Musik-Pille. Breiter als $widthCap dp (Bildschirmbreite) geht nicht. Die „Maximale Breite“ oben gilt hier nicht, nur für die kleine Pille. Beim Verschieben siehst du eine Vorschau.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            SectionLabel("VERHALTEN")
            Card {
                ToggleRow("Ruhe-Pille anzeigen", s.idleVisible) { c -> edit { it.copy(idleVisible = c) } }
                ToggleRow("Musik, Timer & Co. dauerhaft anzeigen", s.showOngoing) { c ->
                    edit { it.copy(showOngoing = c) }
                }
                SettingSlider(
                    label = "Neue Meldung sichtbar für",
                    valueText = "${s.alertSeconds} s",
                    value = s.alertSeconds.toFloat(),
                    range = 2f..15f,
                ) { v -> edit { it.copy(alertSeconds = v.roundToInt()) } }
            }

            SectionLabel("TESTEN")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("MELDUNG", filled = true, modifier = Modifier.weight(1f)) {
                    IslandBus.post(
                        IslandEvent(
                            app = "Nothing Island",
                            title = "Test-Benachrichtigung",
                            text = "So sieht deine Island aus.",
                        )
                    )
                }
                PillButton("TIMER", filled = false, modifier = Modifier.weight(1f)) {
                    val left = 5 * 60_000L
                    IslandBus.upsertOngoing(demoTimer(System.currentTimeMillis() + left, left))
                }
                PillButton("MUSIK", filled = false, modifier = Modifier.weight(1f)) {
                    IslandBus.upsertOngoing(demoMusic())
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("NAVI", filled = false, modifier = Modifier.weight(1f)) {
                    IslandBus.upsertOngoing(demoNav())
                }
                PillButton("LEEREN", filled = false, modifier = Modifier.weight(1f)) {
                    IslandBus.removeOngoing("demo-timer")
                    IslandBus.removeOngoing("demo-music")
                    IslandBus.removeOngoing("demo-nav")
                }
                PillButton("ZURÜCKSETZEN", filled = false, modifier = Modifier.weight(1f)) {
                    edit { IslandSettings() }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Tipp: Tippe auf die kleine Pille, um sie aufzuklappen. Dort findest du die Buttons der App (Pause, Stopp ...).",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    @Composable
    private fun StepRow(index: String, title: String, done: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
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

// ------------------------------------------------------------------ UI-Bausteine

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(32.dp))
    Text(
        text,
        color = NothingRed,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = NothingRed, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = NothingRed,
                inactiveTrackColor = CardBorder,
            ),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NothingRed,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF1A1A1A),
                uncheckedThumbColor = Color(0xFF777777),
            ),
        )
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (filled) Modifier.background(NothingRed) else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ Demo-Daten für den Test

private fun clock(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%02d:%02d", t / 60, t % 60)
}

/** Testet Chronometer, Pause/Weiter und Stopp. Pausiert wird über [base] = null. */
private fun demoTimer(base: Long?, leftMs: Long): IslandEvent {
    val running = base != null
    return IslandEvent(
        key = "demo-timer",
        kind = Kind.TIMER,
        app = "Timer",
        title = "Nudeln",
        text = if (running) "" else "Pausiert · ${clock(leftMs)}",
        chronoBase = base,
        countDown = true,
        actions = listOf(
            IslandAction(if (running) "Pause" else "Weiter", onClick = {
                if (base != null) {
                    IslandBus.upsertOngoing(demoTimer(null, base - System.currentTimeMillis()))
                } else {
                    IslandBus.upsertOngoing(demoTimer(System.currentTimeMillis() + leftMs, leftMs))
                }
            }),
            IslandAction("Stopp", onClick = { IslandBus.removeOngoing("demo-timer") }),
        ),
    )
}

/** Nur zur Ansicht: die Buttons tun nichts. Echte Musik-Apps liefern ihre eigenen Aktionen. */
private fun demoMusic(): IslandEvent = IslandEvent(
    key = "demo-music",
    kind = Kind.MEDIA,
    app = "Musik",
    title = "Lofi Beats",
    text = "Nothing Radio",
    actions = listOf(IslandAction("Zurück"), IslandAction("Pause"), IslandAction("Weiter")),
)

/** Nur zur Ansicht: Navigation wie von einer Karten-App. */
private fun demoNav(): IslandEvent = IslandEvent(
    key = "demo-nav",
    kind = Kind.NAVIGATION,
    app = "Karten",
    title = "In 250 m",
    text = "Rechts abbiegen auf Hauptstraße",
    actions = listOf(IslandAction("Beenden", onClick = { IslandBus.removeOngoing("demo-nav") })),
)

private fun previewAlert(): IslandEvent = IslandEvent(
    app = "Nothing Island",
    title = "Vorschau",
    text = "So groß ist die aufgeklappte Pille.",
)

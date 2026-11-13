package dev.nothingisland

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

private val CardBorder = Color(0xFF2A2A2A)
private val Muted = Color(0xFF9A9A9A)

private val TABS = listOf("START", "LAYOUT", "FUNKTION", "DESIGN", "APPS")

private val STATUS_TYPES = listOf(
    "torch" to "Taschenlampe",
    "ringer" to "Stumm / Vibration",
    "dnd" to "Nicht stören",
    "hotspot" to "Hotspot",
    "airplane" to "Flugmodus",
    "bluetooth" to "Neues Bluetooth-Gerät kurz anzeigen",
    "charging" to "Laden kurz anzeigen (beim Anstecken)",
    "lowbattery" to "Akku schwach",
    "mic" to "Mikrofon wird verwendet",
    "camera" to "Kamera wird verwendet",
    "powersave" to "Energiesparmodus",
    "location" to "Standort an",
    "alarm" to "Wecker kurz vor dem Klingeln (10 Min.)",
)

class MainActivity : ComponentActivity() {

    // Wird in onResume erhöht, damit der Permission-Status nach Rückkehr aus den Settings neu gelesen wird.
    private var refresh by mutableIntStateOf(0)

    private val btPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandBus.loadPrefs(this)
        setContent { SettingsScreen(refreshKey = refresh) }
    }

    override fun onResume() {
        super.onResume()
        // Solange die Einstellungen offen sind, zeigt die Island sich immer (Live-Vorschau).
        IslandBus.setPreview(true)
        A11yGuard.ensureEnabled(this)
        KeepAliveService.start(this)
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
        val s by IslandBus.settings.collectAsState()
        IslandTheme.apply(s)
        // Tab und Scrollposition liegen außerhalb des key-Blocks, damit ein Theme-Wechsel sie nicht zurücksetzt.
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val scroll = rememberScrollState()
        androidx.compose.runtime.key(IslandTheme.signature) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(72.dp)) // Platz, damit die Island oben nicht den Titel verdeckt
                Text(
                    "NOTHING ISLAND",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TABS.forEachIndexed { i, name -> Chip(name, tab == i) { tab = i } }
                }
                Spacer(Modifier.height(4.dp))
                if (tab == 4) {
                    AppRulesTab(s)
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scroll)
                            .padding(horizontal = 24.dp),
                    ) {
                        when (tab) {
                            0 -> StartTab()
                            1 -> LayoutTab(s)
                            2 -> FunctionTab(s)
                            else -> DesignTab(s)
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    private fun edit(t: (IslandSettings) -> IslandSettings) = IslandBus.updateSettings(this, t)

    // ---------------------------------------------------------------- START

    @Composable
    private fun StartTab() {
        val ctx = this@MainActivity
        val overlayOk = Settings.canDrawOverlays(ctx)
        val a11yOk = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith("$packageName/") }
        val batteryOk = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        val listenerOk = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(packageName)
        val btOk = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
        crash?.let { text ->
            SectionLabel("LETZTER ABSTURZ")
            Card {
                Text(
                    text.take(1500),
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("KOPIEREN", filled = true, modifier = Modifier.weight(1f)) {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Absturz", text))
                }
                PillButton("LÖSCHEN", filled = false, modifier = Modifier.weight(1f)) {
                    CrashLog.clear(ctx)
                    crash = null
                }
            }
        }

        var dump by remember { mutableStateOf(getSharedPreferences("island_prefs", MODE_PRIVATE).getString("workoutDump", null)) }
        dump?.let { text ->
            SectionLabel("LETZTE TRAININGS-BENACHRICHTIGUNG")
            Card {
                Text(
                    text.take(1200),
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("KOPIEREN", filled = true, modifier = Modifier.weight(1f)) {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Training", text))
                }
                PillButton("LÖSCHEN", filled = false, modifier = Modifier.weight(1f)) {
                    getSharedPreferences("island_prefs", MODE_PRIVATE).edit().remove("workoutDump").apply()
                    dump = null
                }
            }
        }

        SectionLabel("SETUP")
        StepRow("01", "Über anderen Apps anzeigen", overlayOk) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        Spacer(Modifier.height(12.dp))
        StepRow("02", "Zugriff auf Benachrichtigungen", listenerOk) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Spacer(Modifier.height(12.dp))
        StepRow("03", "Über der Statusleiste anzeigen (Barrierefreiheit)", a11yOk) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        Spacer(Modifier.height(12.dp))
        StepRow("04", "Im Hintergrund aktiv lassen (Akku-Optimierung aus)", batteryOk) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            Spacer(Modifier.height(12.dp))
            StepRow("05", "Bluetooth-Namen lesen (Kopfhörer-Anzeige)", btOk) {
                btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "03 legt die Island über die Symbole oben und hält die App zuverlässig im Hintergrund am Leben. Die App liest dabei keine Bildschirminhalte. Falls Android die Option ausgraut: Einstellungen > Apps > Nothing Island > ⋮ > „Eingeschränkte Einstellungen zulassen“.",
            color = Muted,
            fontSize = 12.sp,
        )
        if (A11yGuard.isWanted(ctx) && !a11yOk) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Android hat die Barrierefreiheit für Nothing Island ausgeschaltet (das passiert, wenn die App beendet wird). Schalte sie bei 03 wieder ein. Damit die App das künftig selbst macht, einmalig am PC: adb shell pm grant dev.nothingisland android.permission.WRITE_SECURE_SETTINGS",
                color = NothingRed,
                fontSize = 12.sp,
            )
        }

        SectionLabel("TESTEN")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("MELDUNG", filled = true, modifier = Modifier.weight(1f)) {
                IslandBus.post(IslandEvent(app = "Nothing Island", title = "Test-Benachrichtigung", text = "So sieht deine Island aus."))
            }
            PillButton("TIMER", filled = false, modifier = Modifier.weight(1f)) {
                val left = 5 * 60_000L
                IslandBus.upsertOngoing(demoTimer(System.currentTimeMillis() + left, left))
            }
            PillButton("MUSIK", filled = false, modifier = Modifier.weight(1f)) { IslandBus.upsertOngoing(demoMusic()) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("NAVI", filled = false, modifier = Modifier.weight(1f)) { IslandBus.upsertOngoing(demoNav()) }
            PillButton("LEEREN", filled = false, modifier = Modifier.weight(1f)) {
                IslandBus.removeOngoing("demo-timer")
                IslandBus.removeOngoing("demo-music")
                IslandBus.removeOngoing("demo-nav")
                IslandBus.removeOngoing("demo-workout")
            }
            PillButton("PANEL", filled = false, modifier = Modifier.weight(1f)) { IslandBus.setQuick(true) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tipp: Tippe auf die kleine Pille, um sie aufzuklappen. Dort findest du die Buttons der App (Pause, Stopp ...). Tippen auf die Ruhe-Pille oder langes Drücken auf die kleine Pille öffnet den Schnellzugriff.",
            color = Muted,
            fontSize = 12.sp,
        )
    }

    // ---------------------------------------------------------------- LAYOUT

    @Composable
    private fun LayoutTab(s: IslandSettings) {
        val widthCap = (resources.configuration.screenWidthDp - 8).coerceAtLeast(220)
        SectionLabel("POSITION & GRÖSSE")
        Card {
            SettingSlider("Horizontal", "${s.offsetX} dp", s.offsetX.toFloat(), -150f..150f) { v -> edit { it.copy(offsetX = v.roundToInt()) } }
            SettingSlider("Abstand von oben", "${s.offsetY} dp", s.offsetY.toFloat(), 0f..300f) { v -> edit { it.copy(offsetY = v.roundToInt()) } }
            SettingSlider("Größe", "${(s.scale * 100).roundToInt()} %", s.scale, 0.7f..1.5f) { v -> edit { it.copy(scale = (v * 20f).roundToInt() / 20f) } }
            SettingSlider("Breite der kleinen Pille", "${(s.widthFactor * 100).roundToInt()} %", s.widthFactor, 0.8f..1.8f) { v -> edit { it.copy(widthFactor = (v * 20f).roundToInt() / 20f) } }
            SettingSlider("Maximale Breite (kleine Pille)", "${s.maxWidth} dp", s.maxWidth.toFloat(), 140f..480f) { v -> edit { it.copy(maxWidth = v.roundToInt()) } }
            SettingSlider("Kamera-Lücke (Mitte bleibt frei)", "${s.cameraGap} dp", s.cameraGap.toFloat(), 0f..100f) { v -> edit { it.copy(cameraGap = v.roundToInt()) } }
            SettingSlider("Abrundung der kleinen Pille", "${(s.smallRoundness * 100).roundToInt()} %", s.smallRoundness, 0.2f..1f) { v -> edit { it.copy(smallRoundness = (v * 20f).roundToInt() / 20f) } }
            SettingSlider("Abstand Symbol → Text (links)", "${s.iconTextGap} dp", s.iconTextGap.toFloat(), 0f..24f) { v -> edit { it.copy(iconTextGap = v.roundToInt()) } }
        }

        SectionLabel("AUFGEKLAPPTE PILLE")
        Card {
            SettingSlider("Größe", "${(s.expandedScale * 100).roundToInt()} %", s.expandedScale, 0.7f..2f) { v ->
                edit { it.copy(expandedScale = (v * 20f).roundToInt() / 20f) }
                IslandBus.post(previewAlert())
            }
            SettingSlider("Rahmen wächst bis", "${(s.expandedFrameMax * 100).roundToInt()} %", s.expandedFrameMax, 0.7f..2f) { v ->
                edit { it.copy(expandedFrameMax = (v * 20f).roundToInt() / 20f) }
                IslandBus.post(previewAlert())
            }
            SettingSlider("Breite", "${minOf(s.expandedWidth, widthCap)} dp", s.expandedWidth.toFloat(), 200f..widthCap.toFloat()) { v ->
                edit { it.copy(expandedWidth = v.roundToInt()) }
                IslandBus.post(previewAlert())
            }
            SettingSlider("Abrundung der Ecken", "${s.expandedCorner} dp", s.expandedCorner.toFloat(), 0f..60f) { v ->
                edit { it.copy(expandedCorner = v.roundToInt()) }
                IslandBus.post(previewAlert())
            }
            SettingSlider("Höhe auf dem Bildschirm (von oben)", "${s.expandedTop} dp", s.expandedTop.toFloat(), 0f..500f) { v ->
                edit { it.copy(expandedOffsetY = v.roundToInt()) }
                IslandBus.post(previewAlert())
            }
            PillButton("HÖHE WIE KLEINE PILLE", filled = false, modifier = Modifier.fillMaxWidth()) {
                edit { it.copy(expandedOffsetY = -1) }
                IslandBus.post(previewAlert())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Gilt für neue Meldungen, die aufgeklappte Aktivität und das Schnellzugriff-Panel. Ist „Größe“ höher als „Rahmen wächst bis“, wächst der Rahmen nicht weiter, nur Text und Symbole. Breiter als $widthCap dp (Bildschirmbreite) geht nicht. Die „Maximale Breite“ gilt hier nicht. Beim Verschieben siehst du eine Vorschau.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        PillButton("ALLES ZURÜCKSETZEN", filled = false, modifier = Modifier.fillMaxWidth()) { edit { IslandSettings() } }
    }

    // ---------------------------------------------------------------- FUNKTION

    @Composable
    private fun FunctionTab(s: IslandSettings) {
        val ctx = this@MainActivity
        SectionLabel("ALLGEMEIN")
        Card {
            ToggleRow("Ruhe-Pille anzeigen", s.idleVisible) { c -> edit { it.copy(idleVisible = c) } }
            ToggleRow("Musik, Timer & Co. dauerhaft anzeigen", s.showOngoing) { c -> edit { it.copy(showOngoing = c) } }
            ToggleRow("Auf dem Sperrbildschirm antippbar", s.lockTouch) { c -> edit { it.copy(lockTouch = c) } }
            ToggleRow("Im Hintergrund aktiv halten (stille Benachrichtigung)", s.keepAlive) { c ->
                edit { it.copy(keepAlive = c) }
                if (c) KeepAliveService.start(ctx) else KeepAliveService.stop(ctx)
            }
            SettingSlider("Neue Meldung sichtbar für", "${s.alertSeconds} s", s.alertSeconds.toFloat(), 2f..15f) { v -> edit { it.copy(alertSeconds = v.roundToInt()) } }
        }

        SectionLabel("MUSIK")
        Card {
            ToggleRow("Equalizer-Balken bei laufender Musik", s.equalizer) { c -> edit { it.copy(equalizer = c) } }
            ToggleRow("Podcast: Vor-/Zurückspulen-Tasten", s.mediaSeek) { c -> edit { it.copy(mediaSeek = c) } }
        }

        SectionLabel("TRAINING (STRAVA, POLAR, WAHOO, KOMOOT)")
        Card {
            Text(
                "Während einer Aufzeichnung zeigt die kleine Pille links die Zeit und rechts diesen Wert. Er wird aus der Benachrichtigung der App gelesen, die Anzeige hängt also davon ab, was die App dort mitteilt. Fehlt der Wert, zeigt die Pille den ersten vorhandenen.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "bpm" to "PULS", "dist" to "DISTANZ", "pace" to "TEMPO", "speed" to "GESCHW.",
                    "power" to "LEISTUNG", "cad" to "KADENZ", "kcal" to "KALORIEN",
                ).forEach { (k, n) -> Chip(n, s.workoutMetric == k) { edit { it.copy(workoutMetric = k) } } }
            }
            PillButton("TRAINING TESTEN", filled = false, modifier = Modifier.fillMaxWidth()) {
                IslandBus.upsertOngoing(demoWorkout())
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionLabel("HAPTIK")
        Card {
            ToggleRow("Kurz vibrieren beim Aufklappen", s.haptics) { c ->
                edit { it.copy(haptics = c) }
                if (c) QuickActions.haptic(ctx, s.hapticLevel)
            }
            SettingSlider(
                "Stärke",
                when (s.hapticLevel) { 1 -> "LEICHT"; 3 -> "STARK"; else -> "MITTEL" },
                s.hapticLevel.toFloat(),
                1f..3f,
            ) { v ->
                val lv = v.roundToInt().coerceIn(1, 3)
                if (lv != s.hapticLevel) {
                    edit { it.copy(hapticLevel = lv) }
                    QuickActions.haptic(ctx, lv)
                }
            }
        }

        SectionLabel("STATUS-ANZEIGEN")
        Card {
            ToggleRow("Status in der Pille anzeigen", s.statusPills) { c -> edit { it.copy(statusPills = c) } }
            Text(
                "Die Pille zeigt es, solange es aktiv ist. Wähle, was angezeigt wird:",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            STATUS_TYPES.forEach { (key, name) ->
                Box(Modifier.padding(start = 12.dp)) {
                    ToggleRow(name, key !in s.statusOff.split(',')) { c ->
                        edit {
                            val off = it.statusOff.split(',').filter { k -> k.isNotBlank() && k != key }.toMutableList()
                            if (!c) off.add(key)
                            it.copy(statusOff = off.joinToString(","))
                        }
                    }
                }
            }
        }

        SectionLabel("SCHNELLZUGRIFF")
        Card {
            ToggleRow("Schnellzugriff-Panel aktivieren", s.quickPanel) { c ->
                edit { it.copy(quickPanel = c) }
                if (!c) IslandBus.setQuick(false)
            }
            Text(
                "Öffnet sich durch Tippen auf die Ruhe-Pille oder langes Drücken auf die kleine Pille. Bis zu 12 Kacheln, vier pro Reihe.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TileEditor(s)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("PANEL ÖFFNEN", filled = true, modifier = Modifier.weight(1f)) { IslandBus.setQuick(true) }
                PillButton("STANDARD", filled = false, modifier = Modifier.weight(1f)) {
                    edit { it.copy(quickTiles = IslandSettings().quickTiles) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    @Composable
    private fun TileEditor(s: IslandSettings) {
        val sel = s.quickTiles.split(',').filter { QuickActions.tile(it) != null }
        fun set(list: List<String>) = edit { it.copy(quickTiles = list.joinToString(",")) }
        sel.forEachIndexed { i, key ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = NothingRed, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                Text(QuickActions.tile(key)?.name ?: key, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                MiniButton("▲", i > 0) { set(sel.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }
                MiniButton("▼", i < sel.size - 1) { set(sel.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) }) }
                MiniButton("✕", true) { set(sel.filter { it != key }) }
            }
        }
        val free = QuickActions.tiles.filter { it.key !in sel }
        if (free.isNotEmpty() && sel.size < 12) {
            Spacer(Modifier.height(6.dp))
            Text("Hinzufügen", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            free.forEach { t ->
                Text(
                    "+  ${t.name}",
                    color = NothingRed,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { set(sel + t.key) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    // ---------------------------------------------------------------- DESIGN

    @Composable
    private fun DesignTab(s: IslandSettings) {
        SectionLabel("FARBSCHEMA")
        val presets = IslandTheme.presets
        (presets.map { it.first to it } + ("custom" to Triple("custom", "EIGEN", Color.Gray))).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, p) ->
                    val selected = s.theme == id
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                edit { it.copy(theme = id) }
                                IslandBus.post(previewAlert())
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val swatch = if (id == "custom") {
                            Modifier.background(
                                Brush.sweepGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                                ),
                            )
                        } else Modifier.background(p.third)
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(swatch)
                                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else CardBorder, CircleShape),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.second,
                            color = if (selected) Color.White else Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (s.theme == "custom") {
            Card {
                SettingSlider("Akzentfarbe", "${s.themeHue.roundToInt()}°", s.themeHue, 0f..359f) { v ->
                    edit { it.copy(themeHue = v) }
                }
                Text("Hintergrund", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    listOf("SCHWARZ", "GRAPHIT", "WEISS").forEachIndexed { i, n ->
                        Chip(n, s.themeBg == i) { edit { it.copy(themeBg = i) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionLabel("SCHRIFT")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MONO", "SANS", "SERIF").forEachIndexed { i, n ->
                Chip(n, s.themeFont == i) { edit { it.copy(themeFont = i) } }
            }
        }

        Spacer(Modifier.height(20.dp))
        PillButton("VORSCHAU ZEIGEN", filled = true, modifier = Modifier.fillMaxWidth()) { IslandBus.post(previewAlert()) }
        Spacer(Modifier.height(10.dp))
        Text(
            "Das Farbschema gilt für die Island, das Schnellzugriff-Panel und das Antwort-Fenster. Die Vorschau siehst du oben in der Island.",
            color = Muted,
            fontSize = 12.sp,
        )
    }

    // ---------------------------------------------------------------- APPS

    @Composable
    private fun AppRulesTab(s: IslandSettings) {
        val ctx = this@MainActivity
        val apps = remember {
            val pm = packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
                .filter { it.second != packageName }
                .distinctBy { it.second }
        }
        var query by remember { mutableStateOf("") }
        val shown = remember(query, s.appRules, apps) {
            apps.filter { query.isBlank() || it.first.contains(query, ignoreCase = true) }
                .sortedWith(compareBy({ s.ruleFor(it.second).isEmpty() }, { it.first.lowercase() }))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Text(
                "Tippe auf die Regel einer App, um sie zu wechseln:\nSTANDARD · LEISE (nur kleine Pille, kein Aufklappen) · STUMM (nie anzeigen) · LIVE (laufende Meldung als Live-Aktivität, z. B. Lieferdienst)",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, CardBorder, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (query.isEmpty()) Text("App suchen …", color = Muted, fontSize = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(NothingRed),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.second }) { (label, pkg) ->
                    val rule = s.ruleFor(pkg)
                    val next = when (rule) { "" -> "QUIET"; "QUIET" -> "MUTE"; "MUTE" -> "LIVE"; else -> "" }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = Color.White, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Chip(
                            when (rule) { "QUIET" -> "LEISE"; "MUTE" -> "STUMM"; "LIVE" -> "LIVE"; else -> "STANDARD" },
                            rule.isNotEmpty(),
                        ) { edit { it.withRule(pkg, next) } }
                    }
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
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


@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(NothingRed) else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Muted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun MiniButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) Color.White else Color(0xFF444444),
        fontSize = 15.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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

/** Nur zur Ansicht: Training wie von Strava oder Polar. */
private fun demoWorkout(): IslandEvent = IslandEvent(
    key = "demo-workout",
    kind = Kind.WORKOUT,
    app = "Training",
    title = "Lauf",
    text = "",
    chronoBase = System.currentTimeMillis() - (12 * 60 + 34) * 1000L,
    metrics = linkedMapOf("bpm" to "142 bpm", "dist" to "3.2 km", "pace" to "5:30 /km", "kcal" to "210 kcal"),
    actions = listOf(IslandAction("Beenden", onClick = { IslandBus.removeOngoing("demo-workout") })),
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

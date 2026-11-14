package dev.nothingisland

import android.app.NotificationManager
import android.app.AlarmManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Eine Kachel im Schnellzugriff-Panel. [click] gibt true zurück, wenn das Panel danach schließen soll. */
class QuickTile(
    val key: String,
    val name: String,
    val label: (Context) -> String,
    val icon: String,
    val active: (Context) -> Boolean,
    val click: (Context) -> Boolean,
)

/** Schnellzugriff und Status-Anzeigen (Lampe, Ton, Nicht stören, Bluetooth, Laden, Mikrofon ...). */
object QuickActions {
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()

    private var cm: CameraManager? = null
    private var torchId: String? = null
    private var registered = false
    private var appCtx: Context? = null

    // ---- Zustände, die per Broadcast oder Callback hereinkommen
    @Volatile var hotspotOn = false
    /** Mobile Datenmenge beim Start des Hotspots (Summe aus Empfang und Senden), um den Verbrauch seitdem zu zeigen. */
    @Volatile private var hotspotBase: Long? = null

    private fun mobileBytes(): Long {
        val rx = android.net.TrafficStats.getMobileRxBytes()
        val tx = android.net.TrafficStats.getMobileTxBytes()
        return if (rx < 0 || tx < 0) -1L else rx + tx
    }
    @Volatile private var micActive = false
    private val camUnavailable = HashSet<String>()
    private val btDevices = LinkedHashMap<String, Pair<String, Int>>() // Adresse -> (Name, Akku %)
    private var lastPlugged: Boolean? = null
    private var lowStage = 0
    private var lastClip = ""
    private var lastClipAt = 0L

    fun init(ctx: Context) {
        if (registered) return
        registered = true
        appCtx = ctx.applicationContext
        runCatching {
            val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm = m
            torchId = m.cameraIdList.firstOrNull { id ->
                val c = m.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            m.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) _torch.value = enabled
                }
            }, null)
            m.registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
                override fun onCameraUnavailable(cameraId: String) { camUnavailable.add(cameraId); resync() }
                override fun onCameraAvailable(cameraId: String) { camUnavailable.remove(cameraId); resync() }
            }, null)
        }
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.registerAudioRecordingCallback(object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                    micActive = !configs.isNullOrEmpty()
                    resync()
                }
            }, null)
        }
        initBluetooth(ctx.applicationContext)
    }

    private fun resync() {
        val c = appCtx ?: return
        syncStatus(c, IslandBus.settings.value)
    }

    // ------------------------------------------------------------ Schalter

    fun toggleTorch(ctx: Context) {
        init(ctx)
        val id = torchId ?: return
        runCatching { cm?.setTorchMode(id, !_torch.value) }
    }

    fun ringerMode(ctx: Context): Int =
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode

    /** Ton an -> Vibration -> Stumm (nur mit "Nicht stören"-Zugriff) -> Ton an. */
    fun cycleRinger(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE ->
                if (nm.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        runCatching { am.ringerMode = next }
    }

    fun dndOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val f = nm.currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** Schaltet "Nicht stören" um, oder öffnet die Freigabe. Gibt true zurück, wenn ein Fenster geöffnet wurde. */
    fun toggleDnd(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            open(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return true
        }
        runCatching {
            nm.setInterruptionFilter(
                if (dndOn(ctx)) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            )
        }
        return false
    }

    fun globalOn(ctx: Context, key: String): Boolean =
        runCatching { Settings.Global.getInt(ctx.contentResolver, key, 0) == 1 }.getOrDefault(false)

    fun powerSave(ctx: Context): Boolean =
        runCatching { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode }.getOrDefault(false)

    /** Berechnet, ob der Sparmodus der Island aktiv ist, und meldet es dem Bus. */
    fun updateLowPower(ctx: Context, st: IslandSettings) {
        val on = when (st.powerSaver) {
            2 -> true
            1 -> powerSave(ctx) || (!lastPluggedNow && lastPct in 0..20)
            else -> false
        }
        IslandBus.setLowPower(on)
    }

    @Volatile private var lastPluggedNow = false
    @Volatile private var lastPct = -1

    fun locationOn(ctx: Context): Boolean =
        runCatching { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled }.getOrDefault(false)

    fun open(ctx: Context, action: String, fallback: String? = null) {
        val ok = runCatching {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok && fallback != null) {
            runCatching { ctx.startActivity(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun hasSecurePermission(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Schaltet die Pop-ups (Heads-up) des Systems aus oder wieder an. Der ursprüngliche Wert wird gemerkt
     * und beim Wiedereinschalten zurückgesetzt. Gibt false zurück, wenn die Freigabe fehlt.
     */
    fun applyHeadsUp(ctx: Context, suppress: Boolean): Boolean {
        if (!hasSecurePermission(ctx)) return false
        val prefs = ctx.getSharedPreferences("island_prefs", Context.MODE_PRIVATE)
        return runCatching {
            val key = "heads_up_notifications_enabled"
            if (suppress) {
                if (!prefs.contains("headsUpOrig")) {
                    prefs.edit().putInt("headsUpOrig", Settings.Global.getInt(ctx.contentResolver, key, 1)).apply()
                }
                Settings.Global.putInt(ctx.contentResolver, key, 0)
            } else if (prefs.contains("headsUpOrig")) {
                Settings.Global.putInt(ctx.contentResolver, key, prefs.getInt("headsUpOrig", 1))
                prefs.edit().remove("headsUpOrig").apply()
            }
            true
        }.getOrDefault(false)
    }

    fun haptic(ctx: Context, level: Int) {
        runCatching {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val effect = when (level) {
                1 -> VibrationEffect.EFFECT_TICK
                3 -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            v.vibrate(VibrationEffect.createPredefined(effect))
        }
    }

    // ------------------------------------------------------------ Kachel-Registry

    private fun settingsTile(key: String, name: String, label: String, icon: String, action: String, fallback: String? = null) =
        QuickTile(key, name, { label }, icon, { false }, { c -> open(c, action, fallback); true })

    val tiles: List<QuickTile> = listOf(
        QuickTile("torch", "Taschenlampe", { "LAMPE" }, "torch", { _torch.value }, { c -> toggleTorch(c); false }),
        QuickTile(
            "ringer", "Ton / Vibration / Stumm",
            { c ->
                when (ringerMode(c)) {
                    AudioManager.RINGER_MODE_SILENT -> "STUMM"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATION"
                    else -> "TON"
                }
            },
            "bell", { c -> ringerMode(c) != AudioManager.RINGER_MODE_NORMAL }, { c -> cycleRinger(c); false },
        ),
        QuickTile("dnd", "Nicht stören", { "STÖREN" }, "moon", { c -> dndOn(c) }, { c -> toggleDnd(c) }),
        QuickTile(
            "hotspot", "Hotspot", { "HOTSPOT" }, "hotspot", { hotspotOn },
            { c -> open(c, "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS); true },
        ),
        QuickTile(
            "airplane", "Flugmodus", { "FLUG" }, "airplane", { c -> globalOn(c, "airplane_mode_on") },
            { c -> open(c, Settings.ACTION_AIRPLANE_MODE_SETTINGS); true },
        ),
        QuickTile(
            "wifi", "WLAN", { "WLAN" }, "wifi", { c -> globalOn(c, "wifi_on") },
            { c -> open(c, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS); true },
        ),
        QuickTile(
            "bluetooth", "Bluetooth", { "BT" }, "bluetooth", { c -> globalOn(c, "bluetooth_on") },
            { c -> open(c, Settings.ACTION_BLUETOOTH_SETTINGS); true },
        ),
        QuickTile(
            "powersave", "Energiesparmodus", { "SPAREN" }, "battery", { c -> powerSave(c) },
            { c -> open(c, Settings.ACTION_BATTERY_SAVER_SETTINGS); true },
        ),
        QuickTile(
            "location", "Standort", { "STANDORT" }, "pin", { c -> locationOn(c) },
            { c -> open(c, Settings.ACTION_LOCATION_SOURCE_SETTINGS); true },
        ),
        settingsTile("volume", "Lautstärke", "LAUT", "speaker", Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS),
        settingsTile("display", "Display / Helligkeit", "DISPLAY", "sun", Settings.ACTION_DISPLAY_SETTINGS),
        QuickTile(
            "camera", "Kamera", { "KAMERA" }, "camera", { false },
            { c -> open(c, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA); true },
        ),
        settingsTile("alarm", "Wecker", "WECKER", "alarm", AlarmClock.ACTION_SHOW_ALARMS),
        settingsTile("nfc", "NFC", "NFC", "nfc", Settings.ACTION_NFC_SETTINGS),
        QuickTile(
            "app", "Nothing Island Einstellungen", { "ISLAND" }, "gear", { false },
            { c ->
                runCatching {
                    c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                true
            },
        ),
    )

    fun tile(key: String): QuickTile? =
        tiles.firstOrNull { it.key == key } ?: when {
            key.startsWith("app:") -> appTile(key)
            key.startsWith("ha:") || key.startsWith("hal:") -> haTile(key)
            else -> null
        }

    /** Home-Assistant-Gerät als Kachel ("ha:") oder Regler ("hal:"). Nicht zwischengespeichert, damit Name und Zustand aktuell sind. */
    private fun haTile(key: String): QuickTile {
        val id = key.substringAfter(':')
        val icon = when (id.substringBefore('.')) {
            "light" -> "bulb"
            "fan" -> "fan"
            "cover" -> "blinds"
            "scene", "script", "button" -> "scene"
            "lock" -> "lock"
            else -> "plug"
        }
        return QuickTile(
            key, HomeAssistant.nameOf(id), { HomeAssistant.nameOf(id).uppercase().take(10) }, icon,
            { HomeAssistant.isOn(id) },
            { HomeAssistant.toggle(id); false },
        )
    }

    // ---- Eigene Kacheln: beliebige App starten ("app:<Paketname>") ----
    @Volatile var appContext: Context? = null
    private val appTiles = HashMap<String, QuickTile>()
    private val appIcons = HashMap<String, ImageBitmap>()

    private fun appTile(key: String): QuickTile? {
        appTiles[key]?.let { return it }
        val ctx = appContext ?: return null
        val pkg = key.removePrefix("app:")
        val pm = ctx.packageManager
        if (pm.getLaunchIntentForPackage(pkg) == null) return null
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val t = QuickTile(
            key, label, { label.uppercase().take(10) }, "app", { false },
            { c ->
                runCatching { pm.getLaunchIntentForPackage(pkg)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                true
            },
        )
        appTiles[key] = t
        return t
    }

    /** Start-Symbol der App für eine eigene Kachel (farbig, nicht eingefärbt). */
    fun appIcon(pkg: String): ImageBitmap? {
        appIcons[pkg]?.let { return it }
        val ctx = appContext ?: return null
        return runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()?.also { appIcons[pkg] = it }
    }

    // ------------------------------------------------------------ Bluetooth

    private fun deviceName(d: BluetoothDevice?): String =
        runCatching { d?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth-Gerät"

    /** Links/Case/rechts einzeln (z. B. "11/34/43") und die kurze Fassung links/rechts ("11/43"). */
    private fun deviceBatteryText(d: BluetoothDevice?): Pair<String, String?>? {
        fun meta(key: Int): Int? = runCatching {
            val b = BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType).invoke(d, key) as? ByteArray
            b?.toString(Charsets.UTF_8)?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }
        }.getOrNull()
        val l = meta(10)
        val c = meta(12)
        val r = meta(11)
        val full = listOfNotNull(l, c, r)
        if (full.size >= 2) {
            val short = if (l != null && r != null && c != null) "$l/$r" else null
            return full.joinToString("/") to short
        }
        val single = deviceBattery(d)
        return if (single >= 0) "$single %" to null else null
    }

    private fun deviceBattery(d: BluetoothDevice?): Int =
        runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int }
            .getOrDefault(-1).let { if (it in 0..100) it else -1 }

    private fun initBluetooth(ctx: Context) {
        runCatching {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val adapter = bm.adapter ?: return
            listOf(android.bluetooth.BluetoothProfile.A2DP, android.bluetooth.BluetoothProfile.HEADSET).forEach { prof ->
                adapter.getProfileProxy(ctx, object : android.bluetooth.BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                        runCatching {
                            proxy.connectedDevices.forEach { d ->
                                btDevices[d.address] = deviceName(d) to deviceBattery(d)
                            }
                        }
                        runCatching { adapter.closeProfileProxy(profile, proxy) }
                        resync()
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, prof)
            }
        }
    }

    /** Verarbeitet Bluetooth-Verbindungen (ACL und Adapter-Zustand). */
    fun onBluetooth(ctx: Context, i: Intent, st: IslandSettings) {
        val dev = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                if (dev != null) {
                    val isNew = dev.address !in btDevices
                    btDevices[dev.address] = deviceName(dev) to deviceBattery(dev)
                    if (isNew && st.statusEnabled("bluetooth")) {
                        // Akkustände kommen oft erst kurz nach dem Verbinden: erst zeigen, dann bei Änderung aktualisieren.
                        val h = android.os.Handler(android.os.Looper.getMainLooper())
                        var shown: String? = null
                        fun show() {
                            val name = deviceName(dev)
                            btDevices[dev.address] = name to deviceBattery(dev)
                            val t = deviceBatteryText(dev)
                            if (shown != null && t?.first == shown) return
                            shown = t?.first
                            IslandBus.post(
                                IslandEvent(
                                    app = "BLUETOOTH", title = name.uppercase(),
                                    text = t?.first ?: "Verbunden", textShort = t?.second,
                                    icon = icon("bluetooth"), quiet = true, kind = Kind.STATUS,
                                ),
                            )
                        }
                        h.postDelayed({ show() }, 1500)
                        h.postDelayed({ show() }, 5000)
                    }
                }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (dev != null) btDevices.remove(dev.address)
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                val lv = i.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                if (dev != null && lv in 0..100) btDevices[dev.address] = deviceName(dev) to lv
            }
            android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED ->
                if (i.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, 0) != android.bluetooth.BluetoothAdapter.STATE_ON) btDevices.clear()
        }
    }

    // ------------------------------------------------------------ Akku: Laden und schwacher Akku

    fun onBattery(ctx: Context, i: Intent, st: IslandSettings) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) level * 100 / scale else -1
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val prev = lastPlugged
        lastPlugged = plugged
        lastPluggedNow = plugged
        lastPct = pct
        updateLowPower(ctx, st)
        if (pct < 0) return
        if (prev != null && !prev && plugged && st.statusEnabled("charging")) {
            IslandBus.post(
                IslandEvent(
                    app = "AKKU", title = "LÄDT", text = "$pct %", icon = icon("charge"), quiet = true, kind = Kind.STATUS,
                    tap = { showChargeDetails(ctx) },
                ),
            )
        }
        if (IslandBus.ongoing.value.any { it.key == "charge-detail" }) {
            if (plugged) buildChargeEvent(ctx, i)?.let { IslandBus.upsertOngoing(it) }
            else IslandBus.removeOngoing("charge-detail")
        }
        if (plugged || pct > 25) lowStage = 0
        if (!plugged && st.statusEnabled("lowbattery")) {
            val saver = runCatching {
                android.app.PendingIntent.getActivity(
                    ctx, 7, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }.getOrNull()
            if (pct <= 10 && lowStage < 2) {
                lowStage = 2
                IslandBus.post(
                    IslandEvent(
                        app = "AKKU", title = "AKKU KRITISCH · $pct %", text = "Jetzt laden oder Energiesparmodus an",
                        icon = icon("battery"), action = saver,
                    ),
                )
            } else if (pct <= 20 && lowStage < 1) {
                lowStage = 1
                IslandBus.post(
                    IslandEvent(
                        app = "AKKU", title = "AKKU SCHWACH · $pct %", text = "Bitte bald laden",
                        icon = icon("battery"), action = saver,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------ Ladedetails

    private fun buildChargeEvent(ctx: Context, i: Intent): IslandEvent? {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return null
        val pct = level * 100 / scale
        val plugType = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val remainMs = runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L)
        val tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).let { if (it == Int.MIN_VALUE) null else it / 10f }
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
        var cur = runCatching { bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L }.getOrDefault(0L)
        cur = kotlin.math.abs(cur)
        if (cur in 1..20000) cur *= 1000 // manche Geräte liefern mA statt µA
        val watts = cur / 1_000_000f * volt
        val charging = plugType != 0
        val head = if (charging) "$pct %  ·  LÄDT" else "$pct %"
        val remain = if (charging && remainMs > 0) {
            val m = (remainMs / 60_000L).toInt().coerceAtLeast(1)
            if (m >= 60) "voll in ${m / 60} Std. ${m % 60} Min." else "voll in $m Min."
        } else if (charging && pct >= 100) "voll" else null
        val kind = when {
            plugType and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "kabellos"
            plugType and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "Netzteil"
            plugType and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            else -> null
        }
        val parts = listOfNotNull(
            remain,
            if (charging && watts >= 0.5f) String.format(java.util.Locale.GERMANY, "%.1f W", watts) else null,
            tempC?.let { String.format(java.util.Locale.GERMANY, "%.0f °C", it) },
            kind,
        )
        val saver = runCatching {
            android.app.PendingIntent.getActivity(
                ctx, 8, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }.getOrNull()
        return IslandEvent(
            key = "charge-detail", app = "AKKU", title = head, text = parts.joinToString("  ·  "),
            icon = icon("charge"), progress = pct / 100f,
            actions = listOf(IslandAction("Energiesparmodus", intent = saver)),
        )
    }

    fun showChargeDetails(ctx: Context) {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val ev = buildChargeEvent(ctx, i) ?: return
        IslandBus.upsertOngoing(ev)
        IslandBus.setPrimary("charge-detail")
        IslandBus.setExpanded(true)
    }

    // ------------------------------------------------------------ Zwischenablage

    private fun clipDump(ctx: Context, text: String) {
        runCatching {
            ctx.getSharedPreferences("island_prefs", Context.MODE_PRIVATE).edit().putString("clipDump", text).apply()
        }
    }

    fun onClipboard(ctx: Context) {
        if (!IslandBus.settings.value.clipboard) return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        val clip = runCatching { cm.primaryClip }.getOrNull()
        val sensitive = clip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        var raw = ""
        if (clip != null && clip.itemCount > 0 && !sensitive) {
            raw = runCatching { clip.getItemAt(0).coerceToText(ctx).toString() }.getOrDefault("")
        }
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date())
        if (raw.isNotBlank() || sensitive) {
            clipDump(ctx, "$stamp  Meldung erhalten\ndirekt gelesen: ${raw.length} Zeichen, sensibel=$sensitive")
            showClip(raw)
            return
        }
        // Android 10+: Im Hintergrund liefert die Zwischenablage nichts. Kurz ein Fenster mit Fokus öffnen, dann darf die App lesen.
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish: (String?, String) -> Unit = { t, how ->
            if (done.compareAndSet(false, true)) {
                clipDump(ctx, "$stamp  Meldung erhalten\ndirekt gelesen: 0 Zeichen\n$how: ${t?.length ?: 0} Zeichen")
                showClip(t.orEmpty())
            }
        }
        readWithFocus(ctx, cm) { t -> finish(t, "mit Fokus-Fenster") }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish(null, "Zeitüberschreitung (kein Fokus)") }, 700)
    }

    private fun readWithFocus(ctx: Context, cm: android.content.ClipboardManager, onText: (String?) -> Unit) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var view: android.view.View? = null
        var fired = false
        val remove = { view?.let { v -> runCatching { wm.removeView(v) } }; view = null }
        val v = object : android.view.View(ctx) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus && !fired) {
                    fired = true
                    val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull()
                    handler.post { remove() }
                    onText(t)
                }
            }
        }
        val lp = android.view.WindowManager.LayoutParams(
            1, 1, android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
        view = v
        if (runCatching { wm.addView(v, lp) }.isFailure) {
            view = null
            return
        }
        handler.postDelayed({ remove() }, 900)
    }

    private fun showClip(raw: String) {
        var text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.startsWith("http://") || text.startsWith("https://")) {
            text = text.substringAfter("://").removePrefix("www.").substringBefore('/')
        }
        if (text.length > 22) text = text.take(21) + "…"
        val now = System.currentTimeMillis()
        if (text == lastClip && now - lastClipAt < 1500) return
        lastClip = text
        lastClipAt = now
        IslandBus.post(
            IslandEvent(
                app = "ZWISCHENABLAGE", title = "KOPIERT", text = text, icon = icon("copy"),
                quiet = true, kind = Kind.STATUS,
            ),
        )
    }

    // ------------------------------------------------------------ Kalender

    private var calCheckedAt = 0L
    private var calCache: Pair<Long, String>? = null

    /** Der Kalender wird höchstens alle 2 Minuten abgefragt (oder neu, wenn der Termin begonnen hat). */
    private fun nextEventCached(ctx: Context): Pair<Long, String>? {
        val now = System.currentTimeMillis()
        val c = calCache
        if (now - calCheckedAt < 120_000L && (c == null || c.first > now)) return c
        calCheckedAt = now
        calCache = nextEvent(ctx)
        return calCache
    }

    private fun nextEvent(ctx: Context): Pair<Long, String>? {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val now = System.currentTimeMillis()
            val b = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(b, now)
            android.content.ContentUris.appendId(b, now + 10 * 60_000L)
            val cols = arrayOf(
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.ALL_DAY,
            )
            ctx.contentResolver.query(b.build(), cols, null, null, "begin ASC")?.use { c ->
                var best: Pair<Long, String>? = null
                while (c.moveToNext()) {
                    val begin = c.getLong(0)
                    if (begin <= now || c.getInt(2) == 1) continue
                    best = begin to (c.getString(1)?.takeIf { it.isNotBlank() } ?: "Termin")
                    break
                }
                best
            }
        }.getOrNull()
    }

    // ------------------------------------------------------------ Status-Anzeigen in der Pille

    private val startTimes = HashMap<String, Long>()
    private val lastSig = HashMap<String, String>()

    private fun setStatus(
        enabled: Boolean, key: String, on: Boolean, title: String, text: String, icon: String,
        timer: Boolean, actionTitle: String?,
        right: String? = null, countDownTo: Long? = null, action: () -> Unit = {},
    ) {
        val busKey = "status:$key"
        if (!enabled || !on) {
            if (startTimes.remove(key) != null) {
                lastSig.remove(key)
                IslandBus.removeOngoing(busKey)
            }
            return
        }
        val start = startTimes.getOrPut(key) { System.currentTimeMillis() }
        val sig = "$title|$text|$right"
        if (lastSig[key] == sig) return
        lastSig[key] = sig
        IslandBus.upsertOngoing(
            IslandEvent(
                key = busKey,
                kind = Kind.STATUS,
                app = "SYSTEM",
                title = title,
                text = text,
                icon = icon(icon),
                chronoBase = countDownTo ?: if (timer) start else null,
                countDown = countDownTo != null,
                rightText = right,
                actions = if (actionTitle == null) emptyList() else listOf(IslandAction(actionTitle, onClick = action)),
            ),
        )
    }

    /** Gleicht die Status-Anzeigen mit dem Systemzustand ab. */
    fun syncStatus(ctx: Context, st: IslandSettings) {
        val app = ctx.applicationContext
        setStatus(st.statusEnabled("torch"), "torch", _torch.value, "TASCHENLAMPE", "AN", "torch", true, "AUS") { toggleTorch(app) }

        val ringer = ringerMode(app)
        val silent = ringer == AudioManager.RINGER_MODE_SILENT
        val vib = ringer == AudioManager.RINGER_MODE_VIBRATE
        setStatus(
            st.statusEnabled("ringer"), "ringer", silent || vib, if (silent) "STUMM" else "VIBRATION",
            if (silent) "Ton ist aus" else "Nur Vibration", "bell", false, "TON AN",
        ) { runCatching { (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = AudioManager.RINGER_MODE_NORMAL } }

        setStatus(st.statusEnabled("dnd"), "dnd", dndOn(app), "NICHT STÖREN", "Aktiv", "moon", false, "AUS") { toggleDnd(app) }

        if (!hotspotOn) hotspotBase = null
        else if (hotspotBase == null) hotspotBase = mobileBytes()
        val used = hotspotBase?.let { b -> mobileBytes().let { n -> if (b < 0 || n < 0) null else (n - b).coerceAtLeast(0) } }
        val hsText = if (used == null) "Aktiv" else {
            val mb = used / 1_000_000.0
            if (mb >= 1000) String.format(java.util.Locale.GERMANY, "%.1f GB Daten", mb / 1000)
            else String.format(java.util.Locale.GERMANY, "%.0f MB Daten", mb)
        }
        setStatus(st.statusEnabled("hotspot"), "hotspot", hotspotOn, "HOTSPOT", hsText, "hotspot", true, "EINSTELLUNGEN") {
            open(app, "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)
        }
        setStatus(st.statusEnabled("airplane"), "airplane", globalOn(app, "airplane_mode_on"), "FLUGMODUS", "Aktiv", "airplane", false, "EINSTELLUNGEN") {
            open(app, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        }

        setStatus(st.statusEnabled("mic"), "mic", micActive, "MIKROFON", "Wird verwendet", "mic", true, null)
        setStatus(
            st.statusEnabled("camera"), "camera", camUnavailable.isNotEmpty() && !_torch.value,
            "KAMERA", "Wird verwendet", "camera", true, null,
        )
        setStatus(
            st.statusEnabled("powersave"), "powersave", powerSave(app), "ENERGIESPARMODUS", "Aktiv", "battery", false,
            "EINSTELLUNGEN", action = { open(app, Settings.ACTION_BATTERY_SAVER_SETTINGS) },
        )
        setStatus(
            st.statusEnabled("location"), "location", locationOn(app), "STANDORT", "Aktiv", "pin", false,
            "EINSTELLUNGEN", action = { open(app, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        )

        // Wecker: in den letzten 10 Minuten vor dem Klingeln als Countdown.
        val info = runCatching { (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock }.getOrNull()
        val next = info?.triggerTime
        val creator = runCatching { info?.showIntent?.creatorPackage }.getOrNull()
        val creatorLabel = creator?.let {
            runCatching { app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
        }
        val clockLike = creator != null && listOf("clock", "alarm", "deskclock").any { creator.contains(it, true) }
        val soon = next != null && clockLike && next - System.currentTimeMillis() in 1..10 * 60_000L
        if (next != null) {
            runCatching {
                app.getSharedPreferences("island_prefs", Context.MODE_PRIVATE).edit().putString(
                    "alarmDump", "nextAlarm=$next\njetzt=${System.currentTimeMillis()}\nvon=$creator ($creatorLabel)\nuhrApp=$clockLike\nangezeigt=$soon",
                ).apply()
            }
        }
        setStatus(
            st.statusEnabled("alarm"), "alarm", soon, "WECKER",
            if (next != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next)) + (creatorLabel?.let { " · $it" } ?: "") else "", "alarm",
            false, "WECKER", countDownTo = next, action = { open(app, AlarmClock.ACTION_SHOW_ALARMS) },
        )

        // Nächster Termin: in den letzten 10 Minuten vorher als Countdown.
        val ev = if (st.statusEnabled("calendar")) nextEventCached(app) else null
        setStatus(
            st.statusEnabled("calendar"), "calendar", ev != null, "TERMIN",
            ev?.second?.let { if (it.length > 20) it.take(19) + "…" else it } ?: "", "calendar",
            false, "KALENDER", countDownTo = ev?.first,
            action = {
                runCatching {
                    app.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/${ev?.first ?: System.currentTimeMillis()}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }

    // ------------------------------------------------------------ Symbole (weiß, 96x96)

    private val iconCache = HashMap<String, ImageBitmap>()

    fun icon(type: String): ImageBitmap = iconCache.getOrPut(type) { drawIcon(type) }

    private fun drawIcon(type: String): ImageBitmap {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        fun poly(vararg p: Float): Path = Path().apply {
            moveTo(p[0], p[1])
            var i = 2
            while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
            close()
        }
        fun arcs(cx: Float, cy: Float, start: Float, sweep: Float, vararg r: Float) {
            r.forEach { c.drawArc(RectF(cx - it, cy - it, cx + it, cy + it), start, sweep, false, line) }
        }
        when (type) {
            "torch" -> {
                c.drawPath(poly(28f, 8f, 68f, 8f, 62f, 32f, 34f, 32f), fill)
                c.drawRoundRect(RectF(34f, 36f, 62f, 88f), 8f, 8f, fill)
                c.drawRoundRect(RectF(44f, 46f, 52f, 62f), 4f, 4f, clear)
            }
            "bell" -> {
                val p = Path().apply {
                    moveTo(22f, 68f); lineTo(22f, 46f)
                    cubicTo(22f, 28f, 34f, 16f, 48f, 16f); cubicTo(62f, 16f, 74f, 28f, 74f, 46f)
                    lineTo(74f, 68f); lineTo(84f, 78f); lineTo(12f, 78f); close()
                }
                c.drawPath(p, fill)
                c.drawCircle(48f, 87f, 7f, fill)
            }
            "moon" -> {
                c.drawCircle(46f, 48f, 34f, fill)
                c.drawCircle(64f, 36f, 28f, clear)
            }
            "hotspot" -> {
                c.drawCircle(48f, 48f, 8f, fill)
                arcs(48f, 48f, 135f, 270f, 22f, 38f)
            }
            "wifi" -> {
                c.drawCircle(48f, 76f, 7f, fill)
                arcs(48f, 76f, 225f, 90f, 24f, 44f, 64f)
            }
            "airplane" -> c.drawPath(
                poly(48f, 8f, 56f, 34f, 88f, 58f, 88f, 68f, 56f, 58f, 53f, 78f, 66f, 88f, 66f, 94f, 48f, 90f,
                    30f, 94f, 30f, 88f, 43f, 78f, 40f, 58f, 8f, 68f, 8f, 58f, 40f, 34f),
                fill,
            )
            "bluetooth" -> c.drawPath(
                Path().apply {
                    moveTo(28f, 32f); lineTo(68f, 64f); lineTo(48f, 82f); lineTo(48f, 14f)
                    lineTo(68f, 32f); lineTo(28f, 64f)
                },
                line,
            )
            "gear" -> {
                c.drawCircle(48f, 48f, 20f, line)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x = Math.cos(a).toFloat(); val y = Math.sin(a).toFloat()
                    c.drawLine(48f + x * 30f, 48f + y * 30f, 48f + x * 40f, 48f + y * 40f, line)
                }
            }
            "battery" -> {
                c.drawRoundRect(RectF(26f, 22f, 70f, 86f), 8f, 8f, line)
                c.drawRoundRect(RectF(40f, 10f, 56f, 20f), 3f, 3f, fill)
                c.drawRoundRect(RectF(34f, 52f, 62f, 78f), 3f, 3f, fill)
            }
            "charge" -> {
                c.drawRoundRect(RectF(26f, 22f, 70f, 86f), 8f, 8f, line)
                c.drawRoundRect(RectF(40f, 10f, 56f, 20f), 3f, 3f, fill)
                c.drawPath(poly(52f, 32f, 36f, 58f, 47f, 58f, 43f, 78f, 60f, 50f, 49f, 50f), fill)
            }
            "mic" -> {
                c.drawRoundRect(RectF(36f, 8f, 60f, 54f), 12f, 12f, fill)
                c.drawArc(RectF(24f, 28f, 72f, 70f), 0f, 180f, false, line)
                c.drawLine(48f, 70f, 48f, 86f, line)
                c.drawLine(34f, 88f, 62f, 88f, line)
            }
            "camera" -> {
                c.drawRoundRect(RectF(8f, 28f, 88f, 80f), 10f, 10f, fill)
                c.drawRoundRect(RectF(32f, 16f, 64f, 30f), 4f, 4f, fill)
                c.drawCircle(48f, 54f, 17f, clear)
                c.drawCircle(48f, 54f, 10f, fill)
            }
            "copy" -> {
                c.drawRoundRect(RectF(24f, 22f, 72f, 86f), 8f, 8f, line)
                c.drawRoundRect(RectF(34f, 12f, 62f, 28f), 5f, 5f, fill)
                c.drawLine(34f, 48f, 62f, 48f, line)
                c.drawLine(34f, 62f, 62f, 62f, line)
            }
            "calendar" -> {
                c.drawRoundRect(RectF(14f, 22f, 82f, 84f), 8f, 8f, line)
                c.drawLine(14f, 40f, 82f, 40f, line)
                c.drawLine(32f, 12f, 32f, 28f, line)
                c.drawLine(64f, 12f, 64f, 28f, line)
                c.drawCircle(34f, 58f, 5f, fill)
                c.drawCircle(48f, 58f, 5f, fill)
                c.drawCircle(62f, 58f, 5f, fill)
                c.drawCircle(34f, 72f, 5f, fill)
            }
            "bulb" -> {
                c.drawCircle(48f, 38f, 22f, line)
                c.drawLine(38f, 66f, 58f, 66f, line)
                c.drawLine(41f, 76f, 55f, 76f, line)
                c.drawLine(40f, 56f, 56f, 56f, line)
            }
            "plug" -> {
                c.drawRoundRect(RectF(28f, 40f, 68f, 70f), 10f, 10f, line)
                c.drawLine(39f, 20f, 39f, 40f, line)
                c.drawLine(57f, 20f, 57f, 40f, line)
                c.drawLine(48f, 70f, 48f, 86f, line)
            }
            "fan" -> {
                for (i in 0 until 3) {
                    c.save()
                    c.rotate(120f * i, 48f, 48f)
                    c.drawOval(RectF(40f, 12f, 56f, 44f), line)
                    c.restore()
                }
                c.drawCircle(48f, 48f, 5f, fill)
            }
            "blinds" -> {
                c.drawRoundRect(RectF(20f, 18f, 76f, 78f), 6f, 6f, line)
                c.drawLine(20f, 34f, 76f, 34f, line)
                c.drawLine(20f, 50f, 76f, 50f, line)
                c.drawLine(20f, 64f, 76f, 64f, line)
            }
            "scene" -> {
                val p = android.graphics.Path()
                p.moveTo(34f, 22f)
                p.lineTo(70f, 48f)
                p.lineTo(34f, 74f)
                p.close()
                c.drawPath(p, line)
            }
            "lock" -> {
                c.drawRoundRect(RectF(26f, 46f, 70f, 80f), 8f, 8f, line)
                c.drawArc(RectF(34f, 20f, 62f, 56f), 180f, 180f, false, line)
            }
            "pin" -> {
                c.drawCircle(48f, 36f, 26f, fill)
                c.drawPath(poly(26f, 48f, 70f, 48f, 48f, 90f), fill)
                c.drawCircle(48f, 36f, 10f, clear)
            }
            "speaker" -> {
                c.drawPath(poly(10f, 36f, 28f, 36f, 48f, 18f, 48f, 78f, 28f, 60f, 10f, 60f), fill)
                arcs(48f, 48f, -50f, 100f, 18f, 32f)
            }
            "sun" -> {
                c.drawCircle(48f, 48f, 15f, fill)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x = Math.cos(a).toFloat(); val y = Math.sin(a).toFloat()
                    c.drawLine(48f + x * 27f, 48f + y * 27f, 48f + x * 38f, 48f + y * 38f, line)
                }
            }
            "alarm" -> {
                c.drawCircle(48f, 52f, 28f, line)
                c.drawLine(48f, 52f, 48f, 36f, line)
                c.drawLine(48f, 52f, 60f, 58f, line)
                c.drawCircle(18f, 22f, 9f, fill)
                c.drawCircle(78f, 22f, 9f, fill)
            }
            "nfc" -> {
                c.drawCircle(26f, 48f, 7f, fill)
                arcs(26f, 48f, -55f, 110f, 22f, 40f, 58f)
            }
            "check" -> {
                c.drawPath(
                    Path().apply { moveTo(16f, 50f); lineTo(38f, 72f); lineTo(80f, 24f) },
                    line,
                )
            }
            "reply" -> {
                c.drawPath(poly(10f, 44f, 40f, 18f, 40f, 70f), fill)
                c.drawPath(
                    Path().apply { moveTo(40f, 44f); lineTo(58f, 44f); cubicTo(80f, 44f, 86f, 58f, 86f, 78f) },
                    line,
                )
            }
        }
        return bmp.asImageBitmap()
    }
}

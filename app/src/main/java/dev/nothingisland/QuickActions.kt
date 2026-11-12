package dev.nothingisland

import android.app.NotificationManager
import android.app.AlarmManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
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
    @Volatile private var micActive = false
    private val camUnavailable = HashSet<String>()
    private val btDevices = LinkedHashMap<String, Pair<String, Int>>() // Adresse -> (Name, Akku %)
    private var lastPlugged: Boolean? = null
    private var lowWarned = false

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

    fun tile(key: String): QuickTile? = tiles.firstOrNull { it.key == key }

    // ------------------------------------------------------------ Bluetooth

    private fun deviceName(d: BluetoothDevice?): String =
        runCatching { d?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth-Gerät"

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
    fun onBluetooth(ctx: Context, i: Intent) {
        val dev = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                if (dev != null) btDevices[dev.address] = deviceName(dev) to deviceBattery(dev)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (dev != null) btDevices.remove(dev.address)
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
        if (pct < 0) return
        if (prev != null && !prev && plugged && st.statusEnabled("charging")) {
            IslandBus.post(
                IslandEvent(
                    app = "AKKU", title = "LÄDT · $pct %", text = "Ladegerät verbunden", icon = icon("charge"),
                ),
            )
        }
        if (plugged || pct > 20) lowWarned = false
        if (!plugged && pct <= 15 && !lowWarned && st.statusEnabled("lowbattery")) {
            lowWarned = true
            IslandBus.post(
                IslandEvent(app = "AKKU", title = "AKKU SCHWACH · $pct %", text = "Bitte bald laden", icon = icon("battery")),
            )
        }
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

        setStatus(st.statusEnabled("hotspot"), "hotspot", hotspotOn, "HOTSPOT", "Aktiv", "hotspot", true, "EINSTELLUNGEN") {
            open(app, "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)
        }
        setStatus(st.statusEnabled("airplane"), "airplane", globalOn(app, "airplane_mode_on"), "FLUGMODUS", "Aktiv", "airplane", false, "EINSTELLUNGEN") {
            open(app, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        }

        val bt = btDevices.values.lastOrNull()
        setStatus(
            st.statusEnabled("bluetooth"), "bluetooth", bt != null, bt?.first?.uppercase() ?: "", "Verbunden", "bluetooth", false,
            "EINSTELLUNGEN",
            right = bt?.second?.takeIf { it >= 0 }?.let { "$it %" },
            action = { open(app, Settings.ACTION_BLUETOOTH_SETTINGS) },
        )

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
        val next = runCatching {
            (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime
        }.getOrNull()
        val soon = next != null && next - System.currentTimeMillis() in 1..10 * 60_000L
        setStatus(
            st.statusEnabled("alarm"), "alarm", soon, "WECKER",
            if (next != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next)) else "", "alarm",
            false, "WECKER", countDownTo = next, action = { open(app, AlarmClock.ACTION_SHOW_ALARMS) },
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

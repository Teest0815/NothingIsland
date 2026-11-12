package dev.nothingisland

import android.app.NotificationManager
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Schnellzugriff: Taschenlampe, Klingelmodus, Nicht stören sind echte Schalter, der Rest öffnet die Systemeinstellung. */
object QuickActions {
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()

    private var cm: CameraManager? = null
    private var torchId: String? = null
    private var registered = false

    fun init(ctx: Context) {
        if (registered) return
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
            registered = true
        }
    }

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
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** Schaltet "Nicht stören" um, oder öffnet die Freigabe, falls der Zugriff fehlt. Gibt true zurück, wenn ein Fenster geöffnet wurde. */
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

    fun open(ctx: Context, action: String, fallback: String? = null) {
        val ok = runCatching {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok && fallback != null) {
            runCatching { ctx.startActivity(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    // ------------------------------------------------------------ Status-Anzeigen in der Pille

    /** Wird vom Broadcast "WIFI_AP_STATE_CHANGED" gesetzt (Android bietet keine offizielle Abfrage). */
    @Volatile
    var hotspotOn = false

    private val startTimes = HashMap<String, Long>()
    private val lastTitle = HashMap<String, String>()

    private fun setStatus(
        ctx: Context, enabled: Boolean, key: String, on: Boolean, title: String, text: String,
        icon: String, timer: Boolean, actionTitle: String, action: () -> Unit,
    ) {
        val busKey = "status:$key"
        if (!enabled || !on) {
            if (startTimes.remove(key) != null) {
                lastTitle.remove(key)
                IslandBus.removeOngoing(busKey)
            }
            return
        }
        val start = startTimes.getOrPut(key) { System.currentTimeMillis() }
        if (lastTitle[key] == title) return
        lastTitle[key] = title
        IslandBus.upsertOngoing(
            IslandEvent(
                key = busKey,
                kind = Kind.STATUS,
                app = "SYSTEM",
                title = title,
                text = text,
                icon = icon(icon),
                chronoBase = if (timer) start else null,
                actions = listOf(IslandAction(actionTitle, onClick = action)),
            ),
        )
    }

    /** Gleicht die Status-Anzeigen mit dem Systemzustand ab. */
    fun syncStatus(ctx: Context, st: IslandSettings) {
        val app = ctx.applicationContext
        setStatus(app, st.statusEnabled("torch"), "torch", _torch.value, "TASCHENLAMPE", "AN", "torch", true, "AUS") { toggleTorch(app) }
        val ringer = ringerMode(app)
        val silent = ringer == AudioManager.RINGER_MODE_SILENT
        val vib = ringer == AudioManager.RINGER_MODE_VIBRATE
        setStatus(
            app, st.statusEnabled("ringer"), "ringer", silent || vib, if (silent) "STUMM" else "VIBRATION",
            if (silent) "Ton ist aus" else "Nur Vibration", "bell", false, "TON AN",
        ) { runCatching { (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = AudioManager.RINGER_MODE_NORMAL } }
        setStatus(app, st.statusEnabled("dnd"), "dnd", dndOn(app), "NICHT STÖREN", "Aktiv", "moon", false, "AUS") { toggleDnd(app) }
        setStatus(app, st.statusEnabled("hotspot"), "hotspot", hotspotOn, "HOTSPOT", "Aktiv", "hotspot", true, "EINSTELLUNGEN") {
            open(app, "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)
        }
        setStatus(app, st.statusEnabled("airplane"), "airplane", globalOn(app, "airplane_mode_on"), "FLUGMODUS", "Aktiv", "airplane", false, "EINSTELLUNGEN") {
            open(app, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        }
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
                val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
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
                val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
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
            "bluetooth" -> {
                val p = Path().apply {
                    moveTo(28f, 32f); lineTo(68f, 64f); lineTo(48f, 82f); lineTo(48f, 14f)
                    lineTo(68f, 32f); lineTo(28f, 64f)
                }
                c.drawPath(p, line)
            }
            "gear" -> {
                c.drawCircle(48f, 48f, 20f, line)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x = Math.cos(a).toFloat(); val y = Math.sin(a).toFloat()
                    c.drawLine(48f + x * 30f, 48f + y * 30f, 48f + x * 40f, 48f + y * 40f, line)
                }
            }
        }
        return bmp.asImageBitmap()
    }
}

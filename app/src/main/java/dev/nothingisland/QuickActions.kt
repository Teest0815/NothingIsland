package dev.nothingisland

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
}

package dev.nothingisland

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Vordergrund-Dienst mit stiller, minimaler Benachrichtigung. Er sorgt dafür, dass Android den
 * Prozess beim Wegwischen der App aus der Übersicht nicht beendet, und startet ihn neu (START_STICKY),
 * falls das Gerät ihn doch beendet.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        IslandBus.loadPrefs(this)
        // Sofort in den Vordergrund. Schlägt das fehl (z. B. Start aus dem Hintergrund verboten), beenden wir den Dienst still.
        val ok = runCatching { enterForeground() }.isSuccess
        if (!ok || !IslandBus.settings.value.keepAlive) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun enterForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Hintergrund-Betrieb", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "Hält die Nothing Island im Hintergrund aktiv."
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Nothing Island aktiv")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "keepalive"
        private const val ID = 4711

        /** Aus dem Hintergrund kann Android den Start verbieten, das ist in Ordnung. */
        fun start(context: Context) {
            IslandBus.loadPrefs(context)
            if (!IslandBus.settings.value.keepAlive) return
            // Bewusst startService statt startForegroundService: Das verlangt kein sofortiges startForeground
            // und führt nicht zum Absturz, falls Android den Vordergrund-Start verweigert.
            runCatching { context.startService(Intent(context, KeepAliveService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        }
    }
}

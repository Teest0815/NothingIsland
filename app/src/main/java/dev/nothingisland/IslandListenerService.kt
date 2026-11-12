package dev.nothingisland

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IslandListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val host = OverlayHost(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, scope)
    private var connected = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        IslandBus.loadPrefs(this)
        // Zeichnet der Barrierefreiheits-Dienst (über der Statusleiste), bleibt dieses Overlay aus.
        scope.launch {
            IslandBus.accessibilityOverlay.collect { active ->
                if (active) host.remove() else if (connected) showOverlay()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        KeepAliveService.start(this)
        showOverlay()
        // Bereits laufende Aktivitäten (Musik, Timer ...) sofort übernehmen.
        runCatching { activeNotifications?.forEach { handle(it, allowAlert = false) } }
    }

    override fun onListenerDisconnected() {
        connected = false
        removeOverlay()
        runCatching { requestRebind(ComponentName(this, IslandListenerService::class.java)) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        showOverlay() // falls die Berechtigung erst nach dem Verbinden erteilt wurde
        runCatching { handle(sbn, allowAlert = true) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandBus.removeOngoing(sbn.key)
    }

    // ---------------------------------------------------------------- Auswertung

    private fun handle(sbn: StatusBarNotification, allowAlert: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // App-Regeln: STUMM = ganz ignorieren, LEISE = ohne Aufklappen, LIVE = laufende Meldung als Live-Aktivität.
        val rule = IslandBus.settings.value.ruleFor(sbn.packageName)
        if (rule == "MUTE") {
            IslandBus.removeOngoing(sbn.key)
            return
        }

        var kind = classify(n, sbn.isOngoing, sbn.packageName)
        if (kind == null && rule == "LIVE" && sbn.isOngoing) kind = Kind.PROGRESS
        if (kind != null) {
            val event = buildEvent(sbn, kind)
            if (event.title.isNotBlank() || event.text.isNotBlank() || event.chronoBase != null) {
                val isNew = IslandBus.upsertOngoing(event)
                // Eingehender Anruf (klingelt noch): Pille sofort aufklappen, damit Annehmen/Ablehnen erreichbar ist.
                if (isNew && allowAlert && kind == Kind.CALL && event.chronoBase == null) IslandBus.setExpanded(true)
            }
            return
        }
        if (sbn.isOngoing || !allowAlert) return

        val event = buildEvent(sbn, Kind.OTHER).copy(quiet = rule == "QUIET")
        if (event.title.isBlank() && event.text.isBlank()) return
        IslandBus.post(event)
    }

    /** Liefert die Art einer laufenden Aktivität oder null, wenn es eine normale Meldung ist. */
    private fun classify(n: Notification, ongoing: Boolean, pkg: String): Kind? {
        val ex = n.extras
        val template = ex.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        if (template.endsWith("MediaStyle") || n.category == Notification.CATEGORY_TRANSPORT) {
            return Kind.MEDIA
        }
        // Anrufe und klingelnde Wecker zählen auch dann, wenn sie nicht als "laufend" markiert sind.
        if (n.category == Notification.CATEGORY_CALL) return Kind.CALL
        if (n.category == Notification.CATEGORY_ALARM && (ongoing || n.fullScreenIntent != null)) return Kind.TIMER
        if (!ongoing) return null
        return when {
            n.category == "navigation" || pkg in NAV_PACKAGES -> Kind.NAVIGATION
            pkg in LIVE_PACKAGES -> Kind.PROGRESS
            ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> Kind.TIMER
            n.category == Notification.CATEGORY_ALARM || n.category == "stopwatch" -> Kind.TIMER
            ex.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 ||
                ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) -> Kind.PROGRESS
            else -> null
        }
    }

    private fun buildEvent(sbn: StatusBarNotification, kind: Kind): IslandEvent {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val progress = if (max > 0 && !indeterminate) {
            ex.getInt(Notification.EXTRA_PROGRESS).toFloat() / max
        } else null

        // Direktantwort (Messenger): erste Aktion mit freiem Texteingabefeld.
        var reply: IslandReply? = null
        n.actions.orEmpty().firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }?.let { a ->
            val ri = a.remoteInputs?.firstOrNull { it.allowFreeFormInput }
            val pi = a.actionIntent
            if (ri != null && pi != null) reply = IslandReply(pi, ri)
        }

        val actions = n.actions.orEmpty()
            .filter { a -> a.remoteInputs.isNullOrEmpty() }
            .take(5)
            .mapNotNull { a ->
                val pi = a.actionIntent ?: return@mapNotNull null
                IslandAction(
                    title = a.title?.toString().orEmpty(),
                    icon = loadIcon(a.getIcon()),
                    intent = pi,
                )
            }

        var chronoBase: Long? = if (chrono) n.`when` else null
        var countDown = chrono && ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        var rightText: String? = null

        // Musik: Abspielposition aus der Media-Session lesen (läuft live weiter, pausiert = fester Wert).
        var controller: MediaController? = null
        var playing = false
        var positionMs = -1L
        var durationMs = 0L
        var art: ImageBitmap? = null
        var canSkip = false
        if (kind == Kind.MEDIA) {
            runCatching {
                @Suppress("DEPRECATION")
                val token = ex.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                    ?: return@runCatching
                val mc = MediaController(this, token)
                controller = mc
                mc.metadata?.let { md ->
                    durationMs = md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
                    val bmp = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                    art = bmp?.let { Bitmap.createScaledBitmap(it, 160, 160, true).asImageBitmap() }
                }
                if (art == null) art = runCatching {
                    n.getLargeIcon()?.loadDrawable(this)?.toBitmap(160, 160)?.asImageBitmap()
                }.getOrNull()
                val st = mc.playbackState ?: return@runCatching
                playing = st.state == PlaybackState.STATE_PLAYING
                canSkip = st.actions and (PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND) != 0L
                if (st.position < 0) return@runCatching
                if (playing) {
                    val since = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
                    val pos = st.position + (since * st.playbackSpeed).toLong()
                    positionMs = pos
                    chronoBase = System.currentTimeMillis() - pos
                    countDown = false
                } else {
                    positionMs = st.position
                    chronoBase = null
                    rightText = mediaClock(st.position)
                }
            }
        }

        return IslandEvent(
            key = sbn.key,
            kind = kind,
            app = appName,
            title = title,
            text = text,
            icon = loadIcon(n.smallIcon),
            action = n.contentIntent,
            actions = actions,
            chronoBase = chronoBase,
            countDown = countDown,
            progress = progress,
            rightText = rightText,
            media = controller,
            playing = playing,
            positionMs = positionMs,
            positionStamp = SystemClock.elapsedRealtime(),
            durationMs = durationMs,
            art = art,
            canSkipSeconds = canSkip,
            reply = reply,
        )
    }

    private fun mediaClock(ms: Long): String {
        val t = ms.coerceAtLeast(0) / 1000
        return if (t >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
        else String.format(java.util.Locale.ROOT, "%02d:%02d", t / 60, t % 60)
    }

    private fun loadIcon(icon: Icon?): ImageBitmap? = runCatching {
        icon?.loadDrawable(this)?.toBitmap(96, 96)?.asImageBitmap()
    }.getOrNull()

    // ---------------------------------------------------------------- Overlay

    /** Kann von der Settings-Activity aufgerufen werden, nachdem die Berechtigung erteilt wurde. */
    fun ensureOverlay() = showOverlay()

    private fun showOverlay() {
        if (IslandBus.accessibilityOverlay.value) return // der Barrierefreiheits-Dienst übernimmt
        if (!Settings.canDrawOverlays(this)) return
        host.show()
    }

    private fun removeOverlay() = host.remove()

    companion object {
        @Volatile
        var instance: IslandListenerService? = null

        /** Karten-Apps, deren laufende Benachrichtigung als Navigation angezeigt wird. */
        private val NAV_PACKAGES = setOf(
            "com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus",
            "app.organicmaps", "com.here.app.maps", "com.sygic.aura", "de.komoot.android",
        )

        /** Lieferdienste, Fahrdienste und Co.: laufende Meldung wird als Live-Aktivität gezeigt. */
        private val LIVE_PACKAGES = setOf(
            "com.ubercab", "com.ubercab.eats", "de.lieferando.android", "com.takeaway.android", "com.deliveroo.orderapp",
            "com.wolt.android", "ee.mtakso.client", "com.bolt.android", "de.flaschenpost.app", "com.gorillas.android",
            "com.dhl.paket", "de.dhl.paket", "com.dpd.android", "com.flixbus.app", "de.bahn.dbnav", "com.lyft.android",
            "com.grubhub.android", "com.application.zomato", "com.global.foodpanda.android",
        )

        @Volatile
        private var lastRebind = 0L

        /**
         * Nach App-Update, Neustart oder Beenden der App wird der Benachrichtigungszugriff auf manchen
         * Geräten nicht neu verbunden. Das Aus- und Einschalten der Komponente zwingt Android dazu
         * (entspricht dem manuellen Aus- und Einschalten der Berechtigung).
         */
        fun rebind(context: Context) {
            if (instance != null) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastRebind < 60_000) return
            lastRebind = now
            val cn = ComponentName(context, IslandListenerService::class.java)
            runCatching {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            runCatching { NotificationListenerService.requestRebind(cn) }
        }
    }
}

package dev.nothingisland

import android.app.Notification
import android.content.ComponentName
import android.content.Context
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

/** Lifecycle-Owner, damit Compose ausserhalb einer Activity (im Service-Overlay) laufen kann. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

class IslandListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var layoutJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlay: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        IslandBus.loadPrefs(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        showOverlay()
        // Bereits laufende Aktivitäten (Musik, Timer ...) sofort übernehmen.
        runCatching { activeNotifications?.forEach { handle(it, allowAlert = false) } }
    }

    override fun onListenerDisconnected() {
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
        handle(sbn, allowAlert = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandBus.removeOngoing(sbn.key)
    }

    // ---------------------------------------------------------------- Auswertung

    private fun handle(sbn: StatusBarNotification, allowAlert: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val kind = classify(n, sbn.isOngoing, sbn.packageName)
        if (kind != null) {
            val event = buildEvent(sbn, kind)
            if (event.title.isNotBlank() || event.text.isNotBlank() || event.chronoBase != null) {
                IslandBus.upsertOngoing(event)
            }
            return
        }
        if (sbn.isOngoing || !allowAlert) return

        val event = buildEvent(sbn, Kind.OTHER)
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
        if (!ongoing) return null
        return when {
            n.category == Notification.CATEGORY_CALL -> Kind.CALL
            n.category == "navigation" || pkg in NAV_PACKAGES -> Kind.NAVIGATION
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

        val actions = n.actions.orEmpty()
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
        if (kind == Kind.MEDIA) {
            runCatching {
                @Suppress("DEPRECATION")
                val token = ex.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                    ?: return@runCatching
                val st = MediaController(this, token).playbackState ?: return@runCatching
                if (st.position < 0) return@runCatching
                if (st.state == PlaybackState.STATE_PLAYING) {
                    val since = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
                    val pos = st.position + (since * st.playbackSpeed).toLong()
                    chronoBase = System.currentTimeMillis() - pos
                    countDown = false
                } else {
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

    private fun applyPosition(s: IslandSettings) {
        val p = params ?: return
        val d = resources.displayMetrics.density
        p.x = (s.offsetX * d).toInt()
        p.y = (s.offsetY * d).toInt()
        val v = overlay ?: return
        runCatching { windowManager?.updateViewLayout(v, p) }
    }

    private fun showOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lifecycleOwner = OverlayOwner().also { it.start() }

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            // Tippen außerhalb der Island verkleinert sie wieder.
            setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) IslandBus.collapse()
                false
            }
            setContent { IslandHost() }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Erlaubt Positionen bis ganz oben, auch im Bereich der Kamera-Aussparung.
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            val s = IslandBus.settings.value
            val d = resources.displayMetrics.density
            x = (s.offsetX * d).toInt()
            y = (s.offsetY * d).toInt()
        }

        runCatching { wm.addView(view, lp) }
            .onSuccess {
                windowManager = wm
                overlay = view
                params = lp
                owner = lifecycleOwner
                layoutJob = scope.launch { IslandBus.settings.collect { applyPosition(it) } }
            }
            .onFailure { lifecycleOwner.stop() }
    }

    private fun removeOverlay() {
        layoutJob?.cancel()
        layoutJob = null
        overlay?.let { v -> runCatching { windowManager?.removeView(v) } }
        owner?.stop()
        overlay = null
        params = null
        owner = null
        windowManager = null
    }

    companion object {
        @Volatile
        var instance: IslandListenerService? = null

        /** Karten-Apps, deren laufende Benachrichtigung als Navigation angezeigt wird. */
        private val NAV_PACKAGES = setOf(
            "com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus",
            "app.organicmaps", "com.here.app.maps", "com.sygic.aura", "de.komoot.android",
        )

        /** Nach App-Update oder Neustart: Benachrichtigungszugriff neu verbinden lassen. */
        fun rebind(context: Context) {
            if (instance != null) return
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, IslandListenerService::class.java)
                )
            }
        }
    }
}

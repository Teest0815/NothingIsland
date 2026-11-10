package dev.nothingisland

import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.view.Gravity
import android.view.WindowManager
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

/**
 * Lifecycle-Owner, damit Compose ausserhalb einer Activity (im Service-Overlay) laufen kann.
 */
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

    private var windowManager: WindowManager? = null
    private var overlay: ComposeView? = null
    private var owner: OverlayOwner? = null

    override fun onCreate() {
        super.onCreate()
        IslandBus.loadPrefs(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        showOverlay()
    }

    override fun onListenerDisconnected() {
        removeOverlay()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val icon = runCatching {
            n.smallIcon?.loadDrawable(this)?.toBitmap(96, 96)?.asImageBitmap()
        }.getOrNull()

        IslandBus.post(
            IslandEvent(
                app = appName,
                title = title,
                text = text,
                icon = icon,
                action = n.contentIntent,
            )
        )
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
            setContent { IslandHost() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (10 * resources.displayMetrics.density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // verhindert, dass das Overlay Touches auf fremde Apps "verschluckt"
                setFitInsetsTypes(0)
            }
        }

        runCatching { wm.addView(view, params) }
            .onSuccess {
                windowManager = wm
                overlay = view
                owner = lifecycleOwner
            }
            .onFailure { lifecycleOwner.stop() }
    }

    private fun removeOverlay() {
        overlay?.let { v -> runCatching { windowManager?.removeView(v) } }
        owner?.stop()
        overlay = null
        owner = null
        windowManager = null
    }
}

package dev.nothingisland

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Lifecycle-Owner, damit Compose außerhalb einer Activity (im Service-Overlay) laufen kann. */
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

/**
 * Hostet die Island als Fenster. [windowType] ist entweder TYPE_APPLICATION_OVERLAY
 * (Benachrichtigungs-Dienst) oder TYPE_ACCESSIBILITY_OVERLAY (Barrierefreiheits-Dienst,
 * liegt über der Statusleiste).
 */
class OverlayHost(
    private val context: Context,
    private val windowType: Int,
    private val scope: CoroutineScope,
) {
    private var windowManager: WindowManager? = null
    private var overlay: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null
    private var layoutJob: Job? = null

    val isShown: Boolean get() = overlay != null

    fun show(): Boolean {
        if (overlay != null) return true
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lifecycleOwner = OverlayOwner().also { it.start() }

        val view = ComposeView(context).apply {
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

        val d0 = context.resources.displayMetrics.density
        val (w0, h0) = IslandBus.windowSize.value
        val lp = WindowManager.LayoutParams(
            (w0 * d0).toInt().coerceAtLeast(1),
            (h0 * d0).toInt().coerceAtLeast(1),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                // Erlaubt Positionen bis ganz oben, auch im Bereich der Kamera-Aussparung.
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            val s = IslandBus.settings.value
            val d = context.resources.displayMetrics.density
            x = (s.offsetX * d).toInt()
            y = (s.windowTop * d).toInt()
        }

        val added = runCatching { wm.addView(view, lp) }.isSuccess
        if (!added) {
            lifecycleOwner.stop()
            return false
        }
        windowManager = wm
        overlay = view
        params = lp
        owner = lifecycleOwner
        layoutJob = scope.launch {
            combine(IslandBus.settings, IslandBus.windowSize) { st, size -> st to size }
                .collect { (st, size) -> applyLayout(st, size) }
        }
        return true
    }

    fun remove() {
        layoutJob?.cancel()
        layoutJob = null
        overlay?.let { v -> runCatching { windowManager?.removeView(v) } }
        owner?.stop()
        overlay = null
        params = null
        owner = null
        windowManager = null
    }

    private fun applyLayout(s: IslandSettings, size: Pair<Float, Float>) {
        val p = params ?: return
        val v = overlay ?: return
        val d = context.resources.displayMetrics.density
        p.x = (s.offsetX * d).toInt()
        p.y = (s.windowTop * d).toInt()
        p.width = (size.first * d).toInt().coerceAtLeast(1)
        p.height = (size.second * d).toInt().coerceAtLeast(1)
        runCatching { windowManager?.updateViewLayout(v, p) }
    }
}

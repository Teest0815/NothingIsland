package dev.nothingisland

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null

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
            setContent { IslandHost() }
        }

        val d0 = context.resources.displayMetrics.density
        val (w0, h0, sp0) = IslandBus.windowSize.value
        val lp = WindowManager.LayoutParams(
            (w0 * d0).toInt().coerceAtLeast(1),
            (h0 * d0).toInt().coerceAtLeast(1),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                // Erlaubt Positionen bis ganz oben, auch im Bereich der Kamera-Aussparung.
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Links verankert: Beim Größenwechsel bewegt sich nur die rechte Kante, nichts springt seitlich.
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            val s = IslandBus.settings.value
            val d = context.resources.displayMetrics.density
            x = leftPx(s.offsetX, sp0)
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

        // Unsichtbares Touch-Fenster genau über der Pille: reicht Touches an die Compose-UI weiter.
        val tv = View(context)
        tv.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                IslandBus.collapse()
                false
            } else {
                val a = params
                val b = touchParams
                if (a != null && b != null) {
                    val copy = MotionEvent.obtain(ev)
                    copy.offsetLocation((b.x - a.x).toFloat(), (b.y - a.y).toFloat())
                    overlay?.dispatchTouchEvent(copy)
                    copy.recycle()
                }
                true
            }
        }
        val tp = WindowManager.LayoutParams(
            1, 1, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        if (runCatching { wm.addView(tv, tp) }.isSuccess) {
            touchView = tv
            touchParams = tp
        }
        layoutJob = scope.launch {
            combine(IslandBus.settings, IslandBus.windowSize, IslandBus.touchRect) { st, size, r -> Triple(st, size, r) }
                .collect { (st, size, r) -> applyLayout(st, size, r) }
        }
        return true
    }

    fun remove() {
        layoutJob?.cancel()
        layoutJob = null
        touchView?.let { v -> runCatching { windowManager?.removeView(v) } }
        touchView = null
        touchParams = null
        overlay?.let { v -> runCatching { windowManager?.removeView(v) } }
        owner?.stop()
        overlay = null
        params = null
        owner = null
        windowManager = null
    }

    /** Linke Fensterkante so, dass die Mitte des "span"-Bereichs auf der Bildschirmmitte (plus Versatz) liegt. */
    private fun leftPx(offsetX: Int, spanDp: Float): Int {
        val dm = context.resources.displayMetrics
        return (dm.widthPixels / 2f + offsetX * dm.density - spanDp * dm.density / 2f).toInt()
    }

    private fun applyLayout(s: IslandSettings, size: Triple<Float, Float, Float>, r: FloatArray) {
        val p = params ?: return
        val v = overlay ?: return
        val d = context.resources.displayMetrics.density
        p.x = leftPx(s.offsetX, size.third)
        p.y = (s.windowTop * d).toInt()
        p.width = (size.first * d).toInt().coerceAtLeast(1)
        p.height = (size.second * d).toInt().coerceAtLeast(1)
        runCatching { windowManager?.updateViewLayout(v, p) }
        val tp = touchParams
        val tv = touchView
        if (tp != null && tv != null) {
            tp.x = p.x + (r[0] * d).toInt()
            tp.y = p.y + (r[1] * d).toInt()
            tp.width = (r[2] * d).toInt().coerceAtLeast(1)
            tp.height = (r[3] * d).toInt().coerceAtLeast(1)
            runCatching { windowManager?.updateViewLayout(tv, tp) }
        }
    }
}

package dev.nothingisland

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var replyView: ComposeView? = null
    private var replyJob: Job? = null
    private var lockReceiver: BroadcastReceiver? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var lastAx = Int.MIN_VALUE
    private var lastAy = Int.MIN_VALUE

    val isShown: Boolean get() = overlay != null

    // ---- Vollbild-Erkennung: 1-Pixel-Fenster ohne LAYOUT_IN_SCREEN. Es sitzt unter der Statusleiste (y > 0),
    // und rutscht auf y = 0, sobald die Statusleiste ausgeblendet ist. Wurde nie y > 0 gemessen, wird nie ausgeblendet.
    private var probe: View? = null
    private var probeMax = 0
    private var probeLast = false
    private val probeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var probeJob: Job? = null
    private var probeRunning = false
    private val probeTick = object : Runnable {
        override fun run() {
            sampleProbe()
            probeHandler.postDelayed(this, if (IslandBus.lowPower.value) 1500L else 700L)
        }
    }

    /** Die Messschleife läuft nur, wenn "Bei Vollbild ausblenden" an und der Bildschirm an ist. */
    private fun updateProbeLoop() {
        val want = probe != null && IslandBus.settings.value.hideFullscreen && IslandBus.screenOn.value
        if (want && !probeRunning) {
            probeRunning = true
            probeHandler.postDelayed(probeTick, 300L)
        } else if (!want && probeRunning) {
            probeRunning = false
            probeHandler.removeCallbacks(probeTick)
            if (!IslandBus.settings.value.hideFullscreen) {
                probeLast = false
                IslandBus.setFullscreen(false)
            }
        }
    }

    private fun sampleProbe() {
        if (!IslandBus.settings.value.hideFullscreen) {
            probeLast = false
            IslandBus.setFullscreen(false)
            return
        }
        val v = probe ?: return
        if (!v.isAttachedToWindow) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm?.isInteractive == false) return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val y = loc[1]
        if (y > probeMax) probeMax = y
        val fs = probeMax > 0 && y < probeMax / 2
        // Erst nach zwei gleichen Messungen umschalten (kein Flackern bei Übergängen).
        if (fs == probeLast) IslandBus.setFullscreen(fs)
        probeLast = fs
    }

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
                // Solange das Antwort-/Notiz-Fenster offen ist, entscheidet dieses selbst: Tipps in sein Fenster
                // und auf die Tastatur sind für das Touch-Fenster der Pille sonst "außerhalb" und würden alles schließen.
                if (IslandBus.replyTarget.value == null) IslandBus.collapse()
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
            updateLockState()
        }
        val pv = View(context)
        val pp = WindowManager.LayoutParams(
            1, 1, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        if (runCatching { wm.addView(pv, pp) }.isSuccess) {
            probe = pv
        }
        // Auf dem Sperrbildschirm ist die Island nicht antippbar.
        val rc = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    Intent.ACTION_SCREEN_OFF -> IslandBus.setScreenOn(false)
                    Intent.ACTION_SCREEN_ON -> IslandBus.setScreenOn(true)
                }
                updateProbeLoop()
                updateLockState()
            }
        }
        (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.let { IslandBus.setScreenOn(it.isInteractive) }
        runCatching {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(rc, f)
            lockReceiver = rc
        }
        replyJob = scope.launch {
            IslandBus.replyTarget.collect { ev -> if (ev != null) showReply() else closeReplyAnimated() }
        }
        probeJob = scope.launch {
            IslandBus.settings.map { it.hideFullscreen }.distinctUntilChanged().collect { updateProbeLoop() }
        }
        layoutJob = scope.launch {
            combine(IslandBus.settings, IslandBus.windowSize, IslandBus.touchRect) { st, size, r -> Triple(st, size, r) }
                .collect { (st, size, r) -> applyLayout(st, size, r) }
        }
        return true
    }

    fun remove() {
        probeJob?.cancel()
        probeJob = null
        probeRunning = false
        probeHandler.removeCallbacks(probeTick)
        probe?.let { v -> runCatching { windowManager?.removeView(v) } }
        probe = null
        IslandBus.setFullscreen(false)
        QuickActions.applyHeadsUp(context, false)
        replyJob?.cancel()
        replyJob = null
        removeReply()
        layoutJob?.cancel()
        layoutJob = null
        lockReceiver?.let { r -> runCatching { context.unregisterReceiver(r) } }
        lockReceiver = null
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

    /** Eigenes, fokussierbares Fenster für die Texteingabe (die Island selbst nimmt nie den Fokus). */
    private fun showReply() {
        val wm = windowManager ?: return
        val lo = owner ?: return
        if (replyView != null) return
        val dm = context.resources.displayMetrics
        val d = dm.density
        val s = IslandBus.settings.value
        val bigW = minOf(s.expandedWidth.toFloat(), dm.widthPixels / d - 8f)
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lo)
            setViewTreeSavedStateRegistryOwner(lo)
            setViewTreeViewModelStoreOwner(lo)
            setOnTouchListener { v, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE && IslandBus.replyTarget.value?.noteSink != null) {
                    // Notiz: Ab Android 12 liefert Android für "außerhalb" keine Koordinaten mehr, Tippen auf die
                    // Tastatur ist dann nicht von Tippen daneben zu unterscheiden. Darum zählt es nur bei versteckter Tastatur.
                    val imeVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        v.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                    if (!imeVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        IslandBus.setNote(false)
                        IslandBus.endReply()
                    }
                } else if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    // Tippen auf die Tastatur zählt nicht als "außerhalb": sonst schließt sich die Island beim Schreiben.
                    val h = dm.heightPixels
                    val imeH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        v.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0
                    } else 0
                    val keyboardTop = if (imeH > 0) h - imeH else (h * 0.55f).toInt()
                    if (ev.rawY < keyboardTop) IslandBus.collapse()
                }
                false
            }
            setContent { ReplyBar() }
        }
        // Feste Fenstergröße (Endhöhe): Das Aufklappen passiert nur im Inneren, das Fenster wird nie umgebaut.
        val sc = s.scale * s.expandedScale
        val fsc = s.scale * minOf(s.expandedScale, s.expandedFrameMax)
        val lp = WindowManager.LayoutParams(
            ((bigW + 24f) * d).toInt(),
            (((if (IslandBus.replyTarget.value?.noteSink != null) 250f * fsc else 84f * fsc + 62f * sc) + 24f) * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (s.offsetX * d).toInt()
            y = ((s.expandedTop - 12) * d).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        if (runCatching { wm.addView(view, lp) }.isSuccess) {
            replyView = view
            // Erst wenn das Fenster steht, tritt die Pille zurück: so gibt es keinen Aussetzer.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (replyView === view) IslandBus.setReplyShown(true)
            }, 120)
        }
    }

    /** Fenster erst nach der Ausblend-Animation entfernen. */
    private fun closeReplyAnimated() {
        if (replyView == null) return
        IslandBus.setReplyClosing(true)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (IslandBus.replyTarget.value == null) {
                removeReply()
                IslandBus.setReplyClosing(false)
            }
        }, 230)
    }

    private fun removeReply() {
        replyView?.let { v -> runCatching { windowManager?.removeView(v) } }
        replyView = null
        IslandBus.setReplyShown(false)
    }

    private fun updateLockState() {
        val tp = touchParams ?: return
        val tv = touchView ?: return
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = km?.isKeyguardLocked == true && !IslandBus.settings.value.lockTouch
        val has = tp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
        if (locked == has) return
        tp.flags = if (locked) tp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else tp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { windowManager?.updateViewLayout(tv, tp) }
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
        val nw = (size.first * d).toInt().coerceAtLeast(1)
        val nh = (size.second * d).toInt().coerceAtLeast(1)
        val changed = p.width != nw || p.height != nh || p.x != lastAx || p.y != lastAy
        p.width = nw
        p.height = nh
        if (changed) {
            lastAx = p.x
            lastAy = p.y
            runCatching { windowManager?.updateViewLayout(v, p) }
        }
        updateLockState()
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

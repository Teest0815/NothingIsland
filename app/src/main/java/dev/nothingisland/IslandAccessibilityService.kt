package dev.nothingisland

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Zeichnet die Island als Barrierefreiheits-Overlay. Dieses Fenster liegt über der Statusleiste
 * (normale Overlays liegen dahinter). Der Dienst liest keine Bildschirminhalte und wertet keine
 * Ereignisse aus, er dient nur als Fensterträger. Android hält ihn außerdem zuverlässig am Leben.
 */
class IslandAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var host: OverlayHost? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandBus.loadPrefs(this)
        A11yGuard.markWanted(this, true)
        val h = OverlayHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, scope)
        host = h
        if (h.show()) IslandBus.setAccessibilityOverlay(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // Wird der Dienst vom Nutzer ausgeschaltet, soll er nicht automatisch wieder eingeschaltet werden.
        A11yGuard.markWanted(this, false)
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun cleanup() {
        host?.remove()
        host = null
        IslandBus.setAccessibilityOverlay(false)
    }
}

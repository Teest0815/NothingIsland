package dev.nothingisland

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Läuft bei jedem Prozessstart (App öffnen, Neustart, Update) und holt die Island zurück. */
class IslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        IslandBus.loadPrefs(this)
        A11yGuard.ensureEnabled(this)
        // Etwas warten: Meist bindet Android den Benachrichtigungszugriff selbst. Wenn nicht, erzwingen wir es.
        Handler(Looper.getMainLooper()).postDelayed({ IslandListenerService.rebind(this) }, 3_000)
    }
}

/**
 * Android schaltet die Barrierefreiheit einer App aus, wenn sie beendet wird. Hat die App die
 * Berechtigung WRITE_SECURE_SETTINGS (einmalig per adb erteilt), schaltet sie den Dienst selbst wieder ein.
 */
object A11yGuard {
    private const val PREFS = "island_prefs"
    private const val KEY_WANTED = "a11yWanted"

    fun markWanted(context: Context, wanted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WANTED, wanted).apply()
    }

    fun isWanted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WANTED, false)

    fun isEnabled(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith("${context.packageName}/") }

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun ensureEnabled(context: Context) {
        if (!isWanted(context) || isEnabled(context) || !canWrite(context)) return
        runCatching {
            val cr = context.contentResolver
            val me = ComponentName(context, IslandAccessibilityService::class.java).flattenToString()
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            Settings.Secure.putString(
                cr,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                if (current.isBlank()) me else "$current:$me",
            )
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        }
    }
}

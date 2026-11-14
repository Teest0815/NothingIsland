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
        CrashLog.install(this)
        QuickActions.appContext = this
        HomeAssistant.load(this)
        // Nötig, um den einzelnen Akkustand von Kopfhörern (links/Case/rechts) lesen zu können.
        if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("") }
        IslandBus.loadPrefs(this)
        A11yGuard.ensureEnabled(this)
        KeepAliveService.start(this)
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

/** Speichert den letzten Absturz, damit er in der App angezeigt und kopiert werden kann. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(app.filesDir, FILE).writeText("${java.util.Date()}\nThread: ${thread.name}\n$sw")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        java.io.File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        runCatching { java.io.File(context.filesDir, FILE).delete() }
    }
}

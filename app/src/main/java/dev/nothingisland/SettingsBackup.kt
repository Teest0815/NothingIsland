package dev.nothingisland

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sichert alle Einstellungen (Layout, Schalter, Kacheln, App-Regeln ...) als JSON-Datei und stellt sie wieder her.
 * Nicht enthalten: Home-Assistant-Zugang (Tokens) und Diagnose-Karten.
 */
object SettingsBackup {
    private const val PREFS = "island_prefs"

    fun export(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = JSONObject()
        for ((k, v) in p.all) {
            if (k.endsWith("Dump")) continue
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                is Set<*> -> o.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            out.put(k, o)
        }
        return JSONObject().put("app", "nothingisland").put("format", 1).put("prefs", out).toString(2)
    }

    /** @return Anzahl wiederhergestellter Werte, oder -1 bei ungültiger Datei. */
    fun import(ctx: Context, json: String): Int {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return -1
        if (root.optString("app") != "nothingisland") return -1
        val prefs = root.optJSONObject("prefs") ?: return -1
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        var n = 0
        for (k in prefs.keys()) {
            val o = prefs.optJSONObject(k) ?: continue
            when (o.optString("t")) {
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                "s" -> e.putString(k, o.getString("v"))
                "ss" -> {
                    val a = o.getJSONArray("v")
                    e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
                else -> continue
            }
            n++
        }
        e.apply()
        IslandBus.loadPrefs(ctx)
        return n
    }
}

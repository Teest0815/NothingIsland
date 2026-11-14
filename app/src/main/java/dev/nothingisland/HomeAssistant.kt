package dev.nothingisland

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Zustand eines Home-Assistant-Geräts. [brightness] ist 0..255 (nur Lampen, nur wenn an). */
data class HaState(val id: String, val name: String, val state: String, val brightness: Int?) {
    val domain: String get() = id.substringBefore('.')
    val isOn: Boolean get() = state == "on" || state == "open" || state == "opening" || state == "unlocked"
}

/**
 * Schlanker Home-Assistant-Client über die REST-API (Adresse + Langzeit-Zugriffstoken).
 * Die Kacheln im Schnellzugriff heißen "ha:<entity_id>" (Kachel) und "hal:<entity_id>" (Helligkeits-Regler).
 */
object HomeAssistant {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var url: String = ""
        private set
    @Volatile var token: String = ""
        private set

    private val _states = MutableStateFlow<Map<String, HaState>>(emptyMap())
    val states: StateFlow<Map<String, HaState>> = _states.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val configured: Boolean get() = url.isNotBlank() && token.isNotBlank()

    /** Geräte-Arten, die als Kachel taugen. */
    val SUPPORTED = listOf("light", "switch", "input_boolean", "fan", "cover", "scene", "script", "automation", "button", "lock")

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("island_ha", Context.MODE_PRIVATE)
        url = p.getString("url", "").orEmpty()
        token = p.getString("token", "").orEmpty()
    }

    fun save(ctx: Context, newUrl: String, newToken: String) {
        url = newUrl.trim().trimEnd('/')
        token = newToken.trim()
        ctx.getSharedPreferences("island_ha", Context.MODE_PRIVATE).edit()
            .putString("url", url).putString("token", token).apply()
    }

    fun nameOf(id: String): String = _states.value[id]?.name ?: id.substringAfter('.').replace('_', ' ')

    fun isOn(id: String): Boolean = _states.value[id]?.isOn == true

    fun brightnessPct(id: String): Int? = _states.value[id]?.brightness?.let { (it * 100 / 255f).toInt().coerceIn(0, 100) }

    // ------------------------------------------------------------------ Netzwerk

    private fun request(method: String, path: String, body: String? = null): String {
        val c = URL(url + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 4000
            c.readTimeout = 6000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(if (code == 401) "Token ungültig (401)" else "Fehler $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    private fun parse(o: JSONObject): HaState {
        val id = o.getString("entity_id")
        val attrs = o.optJSONObject("attributes")
        val name = attrs?.optString("friendly_name")?.takeIf { it.isNotBlank() } ?: id.substringAfter('.').replace('_', ' ')
        val br = if (attrs != null && attrs.has("brightness") && !attrs.isNull("brightness")) attrs.optInt("brightness") else null
        return HaState(id, name, o.optString("state"), br)
    }

    /** Alle Zustände holen (für Kacheln und die Geräteliste in den Einstellungen). */
    fun refresh(onDone: ((String?) -> Unit)? = null) {
        if (!configured) {
            onDone?.invoke("Adresse und Token fehlen")
            return
        }
        scope.launch {
            val err = runCatching {
                val arr = JSONArray(request("GET", "/api/states"))
                val map = HashMap<String, HaState>(arr.length())
                for (i in 0 until arr.length()) {
                    val st = parse(arr.getJSONObject(i))
                    if (st.domain in SUPPORTED) map[st.id] = st
                }
                _states.value = map
            }.exceptionOrNull()
            _error.value = err?.message ?: err?.javaClass?.simpleName
            onDone?.invoke(_error.value)
        }
    }

    private fun refreshOne(id: String) {
        scope.launch {
            runCatching {
                val st = parse(JSONObject(request("GET", "/api/states/$id")))
                _states.update { it + (id to st) }
            }
        }
    }

    private fun call(domain: String, service: String, data: JSONObject, id: String) {
        scope.launch {
            val err = runCatching { request("POST", "/api/services/$domain/$service", data.toString()) }.exceptionOrNull()
            _error.value = err?.message
            delay(900) // dem Gerät kurz Zeit geben, dann echten Zustand holen
            refreshOne(id)
        }
    }

    // ------------------------------------------------------------------ Steuern

    /** Schaltet um (Lampe, Schalter, Ventilator, Rollo ...) bzw. löst aus (Szene, Skript, Knopf). */
    fun toggle(id: String) {
        if (!configured) return
        val domain = id.substringBefore('.')
        val data = JSONObject().put("entity_id", id)
        val cur = _states.value[id]
        when (domain) {
            "scene", "script" -> call(domain, "turn_on", data, id)
            "button" -> call("button", "press", data, id)
            "lock" -> call("lock", if (cur?.isOn == true) "lock" else "unlock", data, id)
            else -> {
                if (cur != null) { // sofort umschalten, der echte Zustand kommt gleich nach
                    val next = if (cur.isOn) (if (domain == "cover") "closed" else "off") else (if (domain == "cover") "open" else "on")
                    _states.update { it + (id to cur.copy(state = next, brightness = if (next == "on") cur.brightness else null)) }
                }
                call(domain, "toggle", data, id)
            }
        }
    }

    /** Helligkeit in Prozent; 0 schaltet aus. */
    fun setBrightness(id: String, pct: Int) {
        if (!configured) return
        val cur = _states.value[id]
        if (pct <= 0) {
            cur?.let { s -> _states.update { it + (id to s.copy(state = "off", brightness = null)) } }
            call("light", "turn_off", JSONObject().put("entity_id", id), id)
        } else {
            cur?.let { s -> _states.update { it + (id to s.copy(state = "on", brightness = pct * 255 / 100)) } }
            call("light", "turn_on", JSONObject().put("entity_id", id).put("brightness_pct", pct), id)
        }
    }
}

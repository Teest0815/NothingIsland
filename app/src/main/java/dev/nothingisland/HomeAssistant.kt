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

    // Anmeldung über Home Assistant (OAuth): kurzlebiger Zugriff + Refresh-Token. Alternativ ein Langzeit-Token von Hand.
    @Volatile private var refreshToken: String = ""
    @Volatile private var accessToken: String = ""
    @Volatile private var expiresAt: Long = 0L
    @Volatile private var clientId: String = ""

    val loggedIn: Boolean get() = refreshToken.isNotBlank()
    val configured: Boolean get() = url.isNotBlank() && (token.isNotBlank() || refreshToken.isNotBlank())

    /** Geräte-Arten, die als Kachel taugen. */
    val SUPPORTED = listOf("light", "switch", "input_boolean", "fan", "cover", "scene", "script", "automation", "button", "lock")

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("island_ha", Context.MODE_PRIVATE)
        url = p.getString("url", "").orEmpty()
        token = p.getString("token", "").orEmpty()
        refreshToken = p.getString("refresh", "").orEmpty()
        accessToken = p.getString("access", "").orEmpty()
        expiresAt = p.getLong("expires", 0L)
        clientId = p.getString("clientId", "").orEmpty()
    }

    private fun saveAuth(ctx: Context) {
        ctx.getSharedPreferences("island_ha", Context.MODE_PRIVATE).edit()
            .putString("refresh", refreshToken).putString("access", accessToken)
            .putLong("expires", expiresAt).putString("clientId", clientId).apply()
    }

    fun save(ctx: Context, newUrl: String, newToken: String) {
        var u = newUrl.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
        url = u
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
            c.setRequestProperty("Authorization", "Bearer ${bearer()}")
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

    /** Gültiges Zugriffstoken: bei Anmeldung per Home Assistant wird es bei Bedarf automatisch erneuert. */
    private fun bearer(): String {
        if (refreshToken.isBlank()) return token
        if (accessToken.isNotBlank() && System.currentTimeMillis() < expiresAt - 60_000L) return accessToken
        val res = form("/auth/token", "grant_type=refresh_token&refresh_token=${enc(refreshToken)}&client_id=${enc(clientId)}")
        accessToken = res.getString("access_token")
        expiresAt = System.currentTimeMillis() + res.optLong("expires_in", 1800L) * 1000L
        QuickActions.appContext?.let { saveAuth(it) }
        return accessToken
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun form(path: String, body: String): JSONObject {
        val c = URL(url + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 5000
            c.readTimeout = 8000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Anmeldung fehlgeschlagen ($code)")
            return JSONObject(text.ifBlank { "{}" })
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------ Anmelden mit Home Assistant

    /**
     * Öffnet die Home-Assistant-Anmeldeseite im Browser. Die Rückkehr kommt über eine kleine Adresse nur auf diesem Handy
     * (127.0.0.1), so wie bei Apps üblich. Danach tauscht die App den Code gegen Zugriffs- und Refresh-Token.
     */
    fun login(ctx: Context, onDone: (String?) -> Unit) {
        if (url.isBlank()) {
            onDone("Bitte zuerst die Adresse eintragen")
            return
        }
        val app = ctx.applicationContext
        scope.launch {
            val err = runCatching {
                val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
                server.use { srv ->
                    srv.soTimeout = 300_000
                    val port = srv.localPort
                    val cid = "http://127.0.0.1:$port/"
                    val redirect = "http://127.0.0.1:$port/callback"
                    val state = java.util.UUID.randomUUID().toString()
                    val authUrl = "$url/auth/authorize?client_id=${enc(cid)}&redirect_uri=${enc(redirect)}&state=$state"
                    app.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    var code: String? = null
                    while (code == null) {
                        srv.accept().use { sock ->
                            sock.soTimeout = 5000
                            val line = sock.getInputStream().bufferedReader().readLine().orEmpty()
                            val target = line.split(' ').getOrNull(1).orEmpty()
                            val q = android.net.Uri.parse("http://127.0.0.1$target")
                            val ok = q.path == "/callback" && q.getQueryParameter("state") == state && q.getQueryParameter("code") != null
                            val html = if (ok) "<html><meta charset=utf-8><body style='font-family:sans-serif;background:#000;color:#fff;text-align:center;padding-top:30vh'><h2>Angemeldet</h2><p>Du kannst zur Nothing Island zurückkehren.</p></body></html>" else "Nicht gefunden"
                            sock.getOutputStream().write(
                                ("HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$html").toByteArray(),
                            )
                            if (ok) code = q.getQueryParameter("code")
                        }
                    }
                    val res = form("/auth/token", "grant_type=authorization_code&code=${enc(code!!)}&client_id=${enc(cid)}")
                    clientId = cid
                    refreshToken = res.getString("refresh_token")
                    accessToken = res.getString("access_token")
                    expiresAt = System.currentTimeMillis() + res.optLong("expires_in", 1800L) * 1000L
                    token = "" // das Langzeit-Token wird nicht mehr gebraucht
                    saveAuth(app)
                    app.getSharedPreferences("island_ha", Context.MODE_PRIVATE).edit().putString("token", "").apply()
                }
            }.exceptionOrNull()
            onDone(err?.let { if (it is java.net.SocketTimeoutException) "Zeit abgelaufen, bitte erneut versuchen" else (it.message ?: it.javaClass.simpleName) })
        }
    }

    fun logout(ctx: Context) {
        val rt = refreshToken
        if (rt.isNotBlank()) scope.launch { runCatching { form("/auth/token", "action=revoke&token=${enc(rt)}") } }
        refreshToken = ""
        accessToken = ""
        expiresAt = 0L
        clientId = ""
        saveAuth(ctx)
        _states.value = emptyMap()
    }

    // ------------------------------------------------------------------ Server im WLAN finden

    /** Sucht Home Assistant per mDNS (_home-assistant._tcp) und meldet die Adresse. Bricht nach 10 s ab. */
    fun discover(ctx: Context, onResult: (String?) -> Unit) {
        val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as? android.net.nsd.NsdManager
        if (nsd == null) {
            onResult(null)
            return
        }
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        var listener: android.net.nsd.NsdManager.DiscoveryListener? = null
        val finish = { found: String? ->
            if (done.compareAndSet(false, true)) {
                listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
                onResult(found)
            }
        }
        listener = object : android.net.nsd.NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: android.net.nsd.NsdServiceInfo) {}
            override fun onServiceFound(info: android.net.nsd.NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : android.net.nsd.NsdManager.ResolveListener {
                    override fun onResolveFailed(i: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(i: android.net.nsd.NsdServiceInfo) {
                        val attrs = i.attributes
                        fun attr(k: String) = attrs?.get(k)?.let { String(it) }?.takeIf { it.isNotBlank() }
                        val found = attr("internal_url") ?: attr("base_url")
                            ?: "http://${i.host?.hostAddress}:${i.port}"
                        finish(found.trimEnd('/'))
                    }
                })
            }
        }
        runCatching { nsd.discoverServices("_home-assistant._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { finish(null) }
        scope.launch {
            delay(10_000)
            finish(null)
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

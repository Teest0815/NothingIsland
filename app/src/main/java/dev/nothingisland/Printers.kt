package dev.nothingisland

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Aktueller Druckzustand, unabhängig vom Hersteller. */
private class PrintState {
    var state = "IDLE" // IDLE, RUNNING, PAUSE, FINISH, FAILED
    var percent = 0
    var leftMin = -1
    var file = ""
    var nozzle = Double.NaN
    var bed = Double.NaN
    var layer = 0
    var total = 0
    var nozzleT = 0.0
    var bedT = 0.0
    var stage = -1
}

private val BAMBU_STAGES = mapOf(
    1 to "Bett wird vermessen", 2 to "Bett heizt auf", 3 to "Mechanik wird geprüft", 4 to "Filamentwechsel",
    5 to "Pause", 6 to "Filament leer", 7 to "Düse heizt auf", 8 to "Extruder wird kalibriert",
    9 to "Druckbett wird gescannt", 10 to "Erste Schicht wird geprüft", 11 to "Platte wird erkannt",
    12 to "Lidar wird kalibriert", 13 to "Referenzfahrt", 14 to "Düse wird gereinigt",
    15 to "Düsentemperatur wird geprüft", 16 to "Vom Nutzer pausiert", 18 to "Lidar wird kalibriert",
    19 to "Fluss wird kalibriert",
)

/** Zeigt laufende Drucke von Bambu Lab (LAN-Modus, MQTT) und Creality (WebSocket) in der Pille. */
object PrinterMonitor {
    private val _info = MutableStateFlow(mapOf<String, String>())
    /** Verbindungsstatus je Drucker ("bambu", "creality") für die Einstellungen. */
    val info: StateFlow<Map<String, String>> = _info

    private fun setInfo(k: String, v: String) { _info.value = _info.value + (k to v) }

    private var bambuCfg = ""
    private var crealityCfg = ""
    @Volatile private var bambuGen = 0
    @Volatile private var crealityGen = 0

    /** Startet oder stoppt die Verbindungen passend zu den Einstellungen. */
    @Synchronized
    fun sync(ctx: Context, s: IslandSettings) {
        val app = ctx.applicationContext
        val b = if (s.bambuIp.isNotBlank() && s.bambuSerial.isNotBlank() && s.bambuCode.isNotBlank())
            "${s.bambuIp.trim()}|${s.bambuSerial.trim()}|${s.bambuCode.trim()}" else ""
        if (b != bambuCfg) {
            bambuCfg = b
            val gen = ++bambuGen
            IslandBus.removeOngoing("printer-bambu")
            if (b.isEmpty()) setInfo("bambu", "") else Thread { bambuLoop(app, gen, s.bambuIp.trim(), s.bambuSerial.trim(), s.bambuCode.trim()) }.start()
        }
        val c = s.crealityIp.trim()
        if (c != crealityCfg) {
            crealityCfg = c
            val gen = ++crealityGen
            IslandBus.removeOngoing("printer-creality")
            if (c.isEmpty()) setInfo("creality", "") else Thread { crealityLoop(app, gen, c) }.start()
        }
    }

    // ------------------------------------------------------------ Anzeige

    private fun fmtLeft(min: Int) = if (min >= 60) "${min / 60}:${"%02d".format(min % 60)} h" else "$min min"

    private fun temp(v: Double) = if (v.isNaN()) null else "${v.toInt()}°"

    private val wasRunning = HashMap<String, Boolean>()

    /** Was der Drucker gerade tut (Name, ob er heizt). */
    private fun phaseOf(st: PrintState): Pair<String, Boolean> {
        if (st.state == "PAUSE") return (BAMBU_STAGES[st.stage] ?: "Pausiert") to false
        BAMBU_STAGES[st.stage]?.let { return it to (st.stage == 2 || st.stage == 7 || st.stage == 15) }
        if (st.bedT > 0 && !st.bed.isNaN() && st.bed < st.bedT - 3) return "Bett heizt auf" to true
        if (st.nozzleT > 0 && !st.nozzle.isNaN() && st.nozzle < st.nozzleT - 5) return "Düse heizt auf" to true
        if (st.layer == 0 && st.percent == 0) return "Wird vorbereitet" to false
        return "Druckt" to false
    }

    private fun publish(key: String, brand: String, st: PrintState) {
        val busKey = "printer-$key"
        val active = st.state == "RUNNING" || st.state == "PAUSE"
        val was = wasRunning[key] == true
        if (active) {
            wasRunning[key] = true
            val parts = ArrayList<String>()
            if (st.file.isNotBlank()) parts += st.file.removeSuffix(".3mf").removeSuffix(".gcode").take(28)
            if (st.leftMin >= 0) parts += "noch ${fmtLeft(st.leftMin)}"
            temp(st.nozzle)?.let { parts += "Düse $it" }
            temp(st.bed)?.let { parts += "Bett $it" }
            if (st.total > 0) parts += "Schicht ${st.layer}/${st.total}"
            val (phase, heating) = phaseOf(st)
            IslandBus.upsertOngoing(
                IslandEvent(
                    key = busKey, kind = Kind.PROGRESS, app = "DRUCK",
                    printer = PrinterView(
                        brand, phase, heating, st.state == "PAUSE", st.percent, st.leftMin,
                        st.nozzle, st.nozzleT, st.bed, st.bedT, st.layer, st.total, st.file,
                    ),
                    title = if (st.state == "PAUSE") "$brand · PAUSE" else brand,
                    text = parts.joinToString("  ·  "),
                    textShort = if (st.leftMin >= 0) "noch ${fmtLeft(st.leftMin)}" else null,
                    icon = QuickActions.icon("printer"),
                    progress = (st.percent / 100f).coerceIn(0f, 1f),
                    rightText = if (heating) "HEIZT" else "${st.percent}%",
                ),
            )
        } else {
            IslandBus.removeOngoing(busKey)
            if (was) {
                wasRunning[key] = false
                val ok = st.state == "FINISH"
                val failed = st.state == "FAILED"
                if (ok || failed) {
                    IslandBus.post(
                        IslandEvent(
                            key = busKey, app = "DRUCK", kind = Kind.OTHER,
                            title = if (ok) "DRUCK FERTIG" else "DRUCK FEHLGESCHLAGEN",
                            text = "$brand · " + st.file.removeSuffix(".3mf").removeSuffix(".gcode").take(30),
                            icon = QuickActions.icon("printer"),
                        ),
                    )
                }
            }
        }
    }

    private fun clear(key: String) {
        IslandBus.removeOngoing("printer-$key")
        wasRunning[key] = false
    }

    // ------------------------------------------------------------ Bambu Lab (MQTT, LAN-Modus)

    private fun bambuLoop(ctx: Context, gen: Int, ip: String, serial: String, code: String) {
        val st = PrintState()
        while (gen == bambuGen) {
            try {
                setInfo("bambu", "Verbinde …")
                bambuSession(gen, ip, serial, code, st)
            } catch (e: Exception) {
                setInfo("bambu", "Getrennt: " + (e.message ?: e.javaClass.simpleName).take(60))
            }
            if (gen == bambuGen) { clear("bambu"); Thread.sleep(8000) }
        }
    }

    private fun bambuSession(gen: Int, ip: String, serial: String, code: String, st: PrintState) {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), SecureRandom()) }
        val raw = Socket()
        raw.connect(InetSocketAddress(ip, 8883), 8000)
        val sock = ssl.socketFactory.createSocket(raw, ip, 8883, true) as javax.net.ssl.SSLSocket
        sock.soTimeout = 10000
        sock.use {
            val inp = DataInputStream(sock.getInputStream())
            val out = DataOutputStream(sock.getOutputStream())
            fun str(s: String): ByteArray { val b = s.toByteArray(); return byteArrayOf((b.size shr 8).toByte(), b.size.toByte()) + b }
            fun send(type: Int, body: ByteArray) {
                val h = java.io.ByteArrayOutputStream()
                h.write(type)
                var n = body.size
                do { var d = n % 128; n /= 128; if (n > 0) d = d or 128; h.write(d) } while (n > 0)
                h.write(body)
                out.write(h.toByteArray()); out.flush()
            }
            val cid = "ni" + (Math.random() * 1e6).toInt()
            send(0x10, str("MQTT") + byteArrayOf(4, 0xC2.toByte(), 0, 60) + str(cid) + str("bblp") + str(code))
            val ack = ByteArray(4); inp.readFully(ack)
            if (ack[0].toInt() != 0x20 || ack[3].toInt() != 0) {
                throw IllegalStateException(if (ack[3].toInt() == 4 || ack[3].toInt() == 5) "Zugangscode falsch" else "Verbindung abgelehnt")
            }
            send(0x82, byteArrayOf(0, 1) + str("device/$serial/report") + byteArrayOf(0))
            fun pushAll() = send(0x30, str("device/$serial/request") + """{"pushing":{"sequence_id":"1","command":"pushall"}}""".toByteArray())
            pushAll()
            setInfo("bambu", "Verbunden")
            var lastPing = System.currentTimeMillis()
            var lastPush = lastPing
            while (gen == bambuGen) {
                val now = System.currentTimeMillis()
                if (now - lastPing > 25000) { send(0xC0, ByteArray(0)); lastPing = now }
                if (now - lastPush > 60000) { pushAll(); lastPush = now }
                val first = try { inp.readUnsignedByte() } catch (e: SocketTimeoutException) { continue }
                var len = 0; var mul = 1
                while (true) { val d = inp.readUnsignedByte(); len += (d and 127) * mul; if (d < 128) break; mul *= 128 }
                val body = ByteArray(len); inp.readFully(body)
                if (first shr 4 != 3) continue
                val tl = ((body[0].toInt() and 0xff) shl 8) or (body[1].toInt() and 0xff)
                var off = 2 + tl
                if ((first shr 1) and 3 > 0) off += 2
                val json = runCatching { JSONObject(String(body, off, len - off)) }.getOrNull() ?: continue
                val p = json.optJSONObject("print") ?: continue
                if (p.has("gcode_state")) st.state = p.optString("gcode_state").let { if (it == "PREPARE" || it == "SLICING") "RUNNING" else it }
                if (p.has("mc_percent")) st.percent = p.optInt("mc_percent")
                if (p.has("mc_remaining_time")) st.leftMin = p.optInt("mc_remaining_time")
                if (p.has("nozzle_temper")) st.nozzle = p.optDouble("nozzle_temper")
                if (p.has("bed_temper")) st.bed = p.optDouble("bed_temper")
                if (p.has("nozzle_target_temper")) st.nozzleT = p.optDouble("nozzle_target_temper")
                if (p.has("bed_target_temper")) st.bedT = p.optDouble("bed_target_temper")
                if (p.has("stg_cur")) st.stage = p.optInt("stg_cur", -1)
                if (p.has("subtask_name")) st.file = p.optString("subtask_name")
                if (p.has("layer_num")) st.layer = p.optInt("layer_num")
                if (p.has("total_layer_num")) st.total = p.optInt("total_layer_num")
                publish("bambu", "BAMBU LAB", st)
            }
        }
    }

    // ------------------------------------------------------------ Creality (WebSocket, Port 9999)

    private val http by lazy {
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS).build()
    }

    private fun num(o: JSONObject, k: String): Double? = if (o.has(k)) o.optString(k).toDoubleOrNull() else null

    private fun crealityLoop(ctx: Context, gen: Int, ip: String) {
        val st = PrintState()
        while (gen == crealityGen) {
            val closed = java.util.concurrent.CountDownLatch(1)
            setInfo("creality", "Verbinde …")
            val ws = http.newWebSocket(
                Request.Builder().url("ws://$ip:9999/").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { setInfo("creality", "Verbunden, warte auf Daten …") }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (gen != crealityGen) { webSocket.cancel(); return }
                        if (text.trim() == "ok") { webSocket.send("ok"); return }
                        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
                        crealityApply(j, st)
                        setInfo("creality", "Verbunden · Status ${st.state} · ${st.percent}%")
                        publish("creality", "CREALITY", st)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.countDown() }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        setInfo("creality", "Getrennt: " + (t.message ?: t.javaClass.simpleName).take(60))
                        closed.countDown()
                    }
                },
            )
            while (gen == crealityGen && !closed.await(2, TimeUnit.SECONDS)) { /* warten */ }
            ws.cancel()
            if (gen == crealityGen) { clear("creality"); Thread.sleep(8000) }
        }
    }

    /** Die Felder stammen aus der K-Serie (K1/K2/Hi); Teilmeldungen werden zusammengeführt. */
    private fun crealityApply(j: JSONObject, st: PrintState) {
        num(j, "nozzleTemp")?.let { st.nozzle = it }
        num(j, "bedTemp0")?.let { st.bed = it }
        num(j, "targetNozzleTemp")?.let { st.nozzleT = it }
        num(j, "targetBedTemp0")?.let { st.bedT = it }
        num(j, "layer")?.let { st.layer = it.toInt() }
        num(j, "TotalLayer")?.let { st.total = it.toInt() }
        if (j.has("printFileName")) st.file = j.optString("printFileName").substringAfterLast('/')
        num(j, "printProgress")?.let { st.percent = it.toInt() }
        num(j, "printLeftTime")?.let { st.leftMin = (it / 60).toInt() }
        num(j, "state")?.let {
            st.state = when (it.toInt()) {
                1 -> "RUNNING"
                5 -> "PAUSE"
                2 -> "FINISH"
                3, 4 -> "FAILED"
                else -> "IDLE"
            }
        }
    }
}

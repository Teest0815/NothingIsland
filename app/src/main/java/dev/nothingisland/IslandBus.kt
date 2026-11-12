package dev.nothingisland

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Reihenfolge = Priorität in der kleinen Pille. */
enum class Kind { CALL, NAVIGATION, TIMER, MEDIA, PROGRESS, STATUS, OTHER }

data class IslandAction(
    val title: String,
    val icon: ImageBitmap? = null,
    val intent: PendingIntent? = null,
    val onClick: (() -> Unit)? = null,
)

/** Direktantwort einer Benachrichtigung (RemoteInput). */
class IslandReply(
    val action: PendingIntent,
    val remoteInput: android.app.RemoteInput,
)

data class IslandEvent(
    val id: Long = System.nanoTime(),
    val key: String = "",
    val kind: Kind = Kind.OTHER,
    val app: String,
    val title: String,
    val text: String,
    val icon: ImageBitmap? = null,
    val action: PendingIntent? = null,
    val actions: List<IslandAction> = emptyList(),
    /** Basiszeit (Notification.when) falls die Benachrichtigung ein Chronometer zeigt. */
    val chronoBase: Long? = null,
    val countDown: Boolean = false,
    /** 0..1 oder null */
    val progress: Float? = null,
    /** Feststehender Text rechts in der kleinen Pille, z. B. die Zeit bei pausierter Musik. */
    val rightText: String? = null,
    /** Leise Meldung: geht direkt in die kleine Pille, ohne aufzuklappen. */
    val quiet: Boolean = false,
    /** Antwort-Möglichkeit (Messenger). */
    val reply: IslandReply? = null,
    /** Musik: Steuerung, Zustand und Metadaten. */
    val media: android.media.session.MediaController? = null,
    val playing: Boolean = false,
    val positionMs: Long = -1,
    val positionStamp: Long = 0,
    val durationMs: Long = 0,
    val art: ImageBitmap? = null,
    /** Player unterstützt Vor-/Zurückspulen (FAST_FORWARD / REWIND). */
    val canSkipSeconds: Boolean = false,
)

data class IslandSettings(
    val offsetX: Int = 0,
    val offsetY: Int = 10,
    val scale: Float = 1f,
    val widthFactor: Float = 1f,
    val idleVisible: Boolean = true,
    val showOngoing: Boolean = true,
    val alertSeconds: Int = 5,
    /** Freier Platz in der Mitte der kleinen Pille (Kamera-Aussparung), in dp. */
    val cameraGap: Int = 36,
    /** Maximale Breite der Island in dp (gilt für alle Zustände). */
    val maxWidth: Int = 480,
    /** Größe der aufgeklappten Pille (Meldung und aufgeklappte Aktivität), zusätzlich zur Gesamtgröße. */
    val expandedScale: Float = 1f,
    /** Breite der aufgeklappten Pille in dp (wird auf die Bildschirmbreite begrenzt). */
    val expandedWidth: Int = 348,
    /** Eckenrundung der aufgeklappten Pille in dp. */
    val expandedCorner: Int = 38,
    /** Rundung der kleinen Pille: 1 = ganz rund, kleiner = eckiger. */
    val smallRoundness: Float = 1f,
    /** Abstand zwischen Symbol und Text links in der kleinen Pille, in dp. */
    val iconTextGap: Int = 6,
    /** Bis zu dieser Größe wächst der Rahmen der aufgeklappten Pille; darüber wachsen nur Text und Symbole. */
    val expandedFrameMax: Float = 1.2f,
    /** Höhe (Abstand von oben) der großen Pille in dp. -1 = wie die kleine Pille. */
    val expandedOffsetY: Int = -1,
    /** Stiller Vordergrund-Dienst, damit die App beim Wegwischen nicht beendet wird. */
    val keepAlive: Boolean = true,
    /** Island auf dem Sperrbildschirm antippbar. */
    val lockTouch: Boolean = false,
    /** Zeigt bei Podcasts & Co. Tasten zum Vor- und Zurückspulen (z. B. 15 Sek.). */
    val mediaSeek: Boolean = true,
    /** Schnellzugriff-Panel (Tippen auf Ruhe-Pille, langes Drücken auf die kleine Pille). */
    val quickPanel: Boolean = true,
    /** Status-Anzeigen in der Pille: Lampe, Stumm, Nicht stören, Hotspot, Flugmodus. */
    val statusPills: Boolean = true,
    /** Ausgeschaltete Status-Arten, kommagetrennt (torch, ringer, dnd, hotspot, airplane). */
    val statusOff: String = "location",
    /** Kurzes Vibrieren beim Aufklappen. */
    val haptics: Boolean = true,
    /** 1 = leicht, 2 = mittel, 3 = stark. */
    val hapticLevel: Int = 2,
    /** Equalizer-Balken bei laufender Musik. */
    val equalizer: Boolean = true,
    /** Reihenfolge der Schnellzugriff-Kacheln. */
    val quickTiles: String = "torch,ringer,dnd,hotspot,airplane,wifi,bluetooth,app",
    /** App-Regeln: "paket=REGEL,paket=REGEL" mit MUTE, QUIET oder LIVE. */
    val appRules: String = "",
    /** nothing, white, midnight, mint, amber, violet oder custom. */
    val theme: String = "nothing",
    val themeHue: Float = 0f,
    /** Nur bei custom: 0 schwarz, 1 graphit, 2 weiß. */
    val themeBg: Int = 0,
    /** 0 Monospace, 1 Sans, 2 Serif. */
    val themeFont: Int = 0,
) {
    fun ruleFor(pkg: String): String {
        if (appRules.isEmpty()) return ""
        for (part in appRules.split(',')) {
            val i = part.lastIndexOf('=')
            if (i > 0 && part.substring(0, i) == pkg) return part.substring(i + 1)
        }
        return ""
    }

    fun withRule(pkg: String, rule: String): IslandSettings {
        val list = appRules.split(',').filter { it.isNotBlank() && it.substringBeforeLast('=') != pkg }.toMutableList()
        if (rule.isNotEmpty()) list.add("$pkg=$rule")
        return copy(appRules = list.joinToString(","))
    }

    fun statusEnabled(key: String): Boolean = statusPills && key !in statusOff.split(',')

    /** Tatsächlicher Abstand von oben für die große Pille. */
    val expandedTop: Int get() = if (expandedOffsetY < 0) offsetY else expandedOffsetY

    /** Das Fenster sitzt am höheren der beiden Werte, der Rest wird per Abstand im Layout erzeugt. */
    val windowTop: Int get() = minOf(offsetY, expandedTop)
}

/** Einfacher In-Process-Bus zwischen Listener-Service, Overlay-UI und Settings-Activity. */
object IslandBus {
    private const val PREFS = "island_prefs"

    /**
     * Größe des Overlay-Fensters in dp. Das Fenster wird nur bei Zustandswechseln angepasst und nicht
     * bei jedem Animationsschritt, das hält die Animationen flüssig.
     */
    private val _windowSize = MutableStateFlow(Triple(1f, 1f, 1f))

    /** Breite, Höhe und "span" (feste Gesamtbreite um die Bildschirmmitte, an der die linke Kante hängt). */
    val windowSize: StateFlow<Triple<Float, Float, Float>> = _windowSize.asStateFlow()

    fun setWindowSize(width: Float, height: Float, span: Float) {
        _windowSize.value = Triple(width, height, span)
    }

    private val _touchRect = MutableStateFlow(floatArrayOf(0f, 0f, 1f, 1f))

    /** Berührbarer Bereich (links, oben, Breite, Höhe) in dp relativ zum Overlay-Fenster. */
    val touchRect: StateFlow<FloatArray> = _touchRect.asStateFlow()

    fun setTouchRect(left: Float, top: Float, width: Float, height: Float) {
        val c = _touchRect.value
        if (c[0] != left || c[1] != top || c[2] != width || c[3] != height) {
            _touchRect.value = floatArrayOf(left, top, width, height)
        }
    }

    private val _settings = MutableStateFlow(IslandSettings())
    val settings: StateFlow<IslandSettings> = _settings.asStateFlow()

    private val _current = MutableStateFlow<IslandEvent?>(null)
    val current: StateFlow<IslandEvent?> = _current.asStateFlow()

    private val _ongoing = MutableStateFlow<List<IslandEvent>>(emptyList())
    val ongoing: StateFlow<List<IslandEvent>> = _ongoing.asStateFlow()

    /** true solange die Einstellungen offen sind: Pille wird dann immer gezeigt. */
    private val _preview = MutableStateFlow(false)
    val preview: StateFlow<Boolean> = _preview.asStateFlow()

    /** true, solange der Barrierefreiheits-Dienst die Island über der Statusleiste zeichnet. */
    private val _accessibilityOverlay = MutableStateFlow(false)
    val accessibilityOverlay: StateFlow<Boolean> = _accessibilityOverlay.asStateFlow()

    fun setAccessibilityOverlay(value: Boolean) {
        _accessibilityOverlay.value = value
    }

    fun setPreview(value: Boolean) {
        _preview.value = value
    }

    /** Vom Nutzer aufgeklappte laufende Aktivität. */
    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    fun setExpanded(value: Boolean) {
        _expanded.value = value
    }

    /** Aktivität in der großen Pille. Alle anderen erscheinen als Kreis daneben. */
    private val _primaryKey = MutableStateFlow<String?>(null)
    val primaryKey: StateFlow<String?> = _primaryKey.asStateFlow()

    fun setPrimary(key: String) {
        _primaryKey.value = key
    }

    /** Schnellzugriff-Panel (Taschenlampe, Stumm ...). */
    private val _quick = MutableStateFlow(false)
    val quick: StateFlow<Boolean> = _quick.asStateFlow()

    fun setQuick(value: Boolean) {
        _quick.value = value
    }

    /** Tippen außerhalb der Island: aufgeklappte Pille und Meldung wieder verkleinern. */
    fun collapse() {
        _quick.value = false
        _expanded.value = false
        _current.value = null
    }

    // ---- Kurzzeit-Meldungen (neue Benachrichtigungen) ----
    /** Zuletzt gezeigte Ereignisse, damit die Antwort-Ansicht sie per ID findet. */
    private val recent = LinkedHashMap<Long, IslandEvent>()

    fun eventById(id: Long): IslandEvent? = synchronized(recent) { recent[id] }

    fun post(event: IslandEvent) {
        if (event.reply != null) synchronized(recent) {
            recent[event.id] = event
            while (recent.size > 20) recent.remove(recent.keys.first())
        }
        _current.value = event
    }

    fun dismiss() {
        _current.value = null
    }

    // ---- Dauerhafte Aktivitäten (Musik, Timer, Anruf, Downloads ...) ----
    fun upsertOngoing(event: IslandEvent): Boolean {
        val isNew = _ongoing.value.none { it.key == event.key }
        _ongoing.update { list ->
            val idx = list.indexOfFirst { it.key == event.key }
            if (idx >= 0) list.toMutableList().also { it[idx] = event } else list + event
        }
        // Neue Aktivität übernimmt die große Pille, die bisherige wandert in den Kreis.
        if (isNew) _primaryKey.value = event.key
        return isNew
    }

    fun removeOngoing(key: String) {
        _ongoing.update { list -> list.filterNot { it.key == key } }
        if (_primaryKey.value == key) _primaryKey.value = null
    }

    // ---- Einstellungen ----
    fun loadPrefs(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = IslandSettings()
        _settings.value = IslandSettings(
            offsetX = p.getInt("offsetX", d.offsetX),
            offsetY = p.getInt("offsetY", d.offsetY),
            scale = p.getFloat("scale", d.scale),
            widthFactor = p.getFloat("widthFactor", d.widthFactor),
            idleVisible = p.getBoolean("idle_pill", d.idleVisible),
            showOngoing = p.getBoolean("showOngoing", d.showOngoing),
            alertSeconds = p.getInt("alertSeconds", d.alertSeconds),
            cameraGap = p.getInt("cameraGap", d.cameraGap),
            maxWidth = p.getInt("maxWidth", d.maxWidth),
            expandedScale = p.getFloat("expandedScale", d.expandedScale),
            expandedWidth = p.getInt("expandedWidthDp", d.expandedWidth),
            expandedCorner = p.getInt("expandedCorner", d.expandedCorner),
            smallRoundness = p.getFloat("smallRoundness", d.smallRoundness),
            iconTextGap = p.getInt("iconTextGap", d.iconTextGap),
            expandedFrameMax = p.getFloat("expandedFrameMax", d.expandedFrameMax),
            expandedOffsetY = p.getInt("expandedOffsetY", d.expandedOffsetY),
            keepAlive = p.getBoolean("keepAlive", d.keepAlive),
            lockTouch = p.getBoolean("lockTouch", d.lockTouch),
            mediaSeek = p.getBoolean("mediaSeek", d.mediaSeek),
            quickPanel = p.getBoolean("quickPanel", d.quickPanel),
            statusPills = p.getBoolean("statusPills", d.statusPills),
            statusOff = p.getString("statusOff", d.statusOff) ?: "",
            haptics = p.getBoolean("haptics", d.haptics),
            hapticLevel = p.getInt("hapticLevel", d.hapticLevel),
            equalizer = p.getBoolean("equalizer", d.equalizer),
            quickTiles = p.getString("quickTiles", d.quickTiles) ?: d.quickTiles,
            appRules = p.getString("appRules", d.appRules) ?: "",
            theme = p.getString("theme", d.theme) ?: d.theme,
            themeHue = p.getFloat("themeHue", d.themeHue),
            themeBg = p.getInt("themeBg", d.themeBg),
            themeFont = p.getInt("themeFont", d.themeFont),
        )
    }

    fun updateSettings(context: Context, transform: (IslandSettings) -> IslandSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("offsetX", s.offsetX)
            .putInt("offsetY", s.offsetY)
            .putFloat("scale", s.scale)
            .putFloat("widthFactor", s.widthFactor)
            .putBoolean("idle_pill", s.idleVisible)
            .putBoolean("showOngoing", s.showOngoing)
            .putInt("alertSeconds", s.alertSeconds)
            .putInt("cameraGap", s.cameraGap)
            .putInt("maxWidth", s.maxWidth)
            .putFloat("expandedScale", s.expandedScale)
            .putInt("expandedWidthDp", s.expandedWidth)
            .putInt("expandedCorner", s.expandedCorner)
            .putFloat("smallRoundness", s.smallRoundness)
            .putInt("iconTextGap", s.iconTextGap)
            .putFloat("expandedFrameMax", s.expandedFrameMax)
            .putInt("expandedOffsetY", s.expandedOffsetY)
            .putBoolean("keepAlive", s.keepAlive)
            .putBoolean("lockTouch", s.lockTouch)
            .putBoolean("mediaSeek", s.mediaSeek)
            .putBoolean("quickPanel", s.quickPanel)
            .putBoolean("statusPills", s.statusPills)
            .putString("statusOff", s.statusOff)
            .putBoolean("haptics", s.haptics)
            .putInt("hapticLevel", s.hapticLevel)
            .putBoolean("equalizer", s.equalizer)
            .putString("quickTiles", s.quickTiles)
            .putString("appRules", s.appRules)
            .putString("theme", s.theme)
            .putFloat("themeHue", s.themeHue)
            .putInt("themeBg", s.themeBg)
            .putInt("themeFont", s.themeFont)
            .apply()
    }
}

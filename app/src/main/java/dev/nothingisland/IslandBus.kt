package dev.nothingisland

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Reihenfolge = Priorität in der kleinen Pille. */
enum class Kind { CALL, NAVIGATION, TIMER, MEDIA, PROGRESS, OTHER }

data class IslandAction(
    val title: String,
    val icon: ImageBitmap? = null,
    val intent: PendingIntent? = null,
    val onClick: (() -> Unit)? = null,
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
) {
    /** Tatsächlicher Abstand von oben für die große Pille. */
    val expandedTop: Int get() = if (expandedOffsetY < 0) offsetY else expandedOffsetY

    /** Das Fenster sitzt am höheren der beiden Werte, der Rest wird per Abstand im Layout erzeugt. */
    val windowTop: Int get() = minOf(offsetY, expandedTop)
}

/** Einfacher In-Process-Bus zwischen Listener-Service, Overlay-UI und Settings-Activity. */
object IslandBus {
    private const val PREFS = "island_prefs"

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

    /** Tippen außerhalb der Island: aufgeklappte Pille und Meldung wieder verkleinern. */
    fun collapse() {
        _expanded.value = false
        _current.value = null
    }

    // ---- Kurzzeit-Meldungen (neue Benachrichtigungen) ----
    fun post(event: IslandEvent) {
        _current.value = event
    }

    fun dismiss() {
        _current.value = null
    }

    // ---- Dauerhafte Aktivitäten (Musik, Timer, Anruf, Downloads ...) ----
    fun upsertOngoing(event: IslandEvent) {
        val isNew = _ongoing.value.none { it.key == event.key }
        _ongoing.update { list ->
            val idx = list.indexOfFirst { it.key == event.key }
            if (idx >= 0) list.toMutableList().also { it[idx] = event } else list + event
        }
        // Neue Aktivität übernimmt die große Pille, die bisherige wandert in den Kreis.
        if (isNew) _primaryKey.value = event.key
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
            .apply()
    }
}

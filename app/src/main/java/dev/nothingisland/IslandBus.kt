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
)

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
            .apply()
    }
}

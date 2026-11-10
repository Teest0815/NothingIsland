package dev.nothingisland

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Reihenfolge = Priorität in der kleinen Pille. */
enum class Kind { CALL, TIMER, MEDIA, PROGRESS, OTHER }

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

    // ---- Kurzzeit-Meldungen (neue Benachrichtigungen) ----
    fun post(event: IslandEvent) {
        _current.value = event
    }

    fun dismiss() {
        _current.value = null
    }

    // ---- Dauerhafte Aktivitäten (Musik, Timer, Anruf, Downloads ...) ----
    fun upsertOngoing(event: IslandEvent) {
        _ongoing.update { list ->
            val idx = list.indexOfFirst { it.key == event.key }
            val merged = if (idx >= 0) list.toMutableList().also { it[idx] = event } else list + event
            merged.sortedBy { it.kind.ordinal } // stabil: gleiche Art behält ihre Reihenfolge
        }
    }

    fun removeOngoing(key: String) {
        _ongoing.update { list -> list.filterNot { it.key == key } }
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
            .apply()
    }
}

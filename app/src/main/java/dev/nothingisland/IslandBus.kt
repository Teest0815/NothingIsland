package dev.nothingisland

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandEvent(
    val id: Long = System.nanoTime(),
    val app: String,
    val title: String,
    val text: String,
    val icon: ImageBitmap? = null,
    val action: PendingIntent? = null,
)

/** Einfacher In-Process-Bus zwischen Listener-Service, Overlay-UI und Settings-Activity. */
object IslandBus {
    private const val PREFS = "island_prefs"
    private const val KEY_IDLE = "idle_pill"

    private val _current = MutableStateFlow<IslandEvent?>(null)
    val current: StateFlow<IslandEvent?> = _current.asStateFlow()

    private val _idleVisible = MutableStateFlow(true)
    val idleVisible: StateFlow<Boolean> = _idleVisible.asStateFlow()

    fun post(event: IslandEvent) {
        _current.value = event
    }

    fun dismiss() {
        _current.value = null
    }

    fun loadPrefs(context: Context) {
        _idleVisible.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IDLE, true)
    }

    fun setIdleVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IDLE, visible).apply()
        _idleVisible.value = visible
    }
}

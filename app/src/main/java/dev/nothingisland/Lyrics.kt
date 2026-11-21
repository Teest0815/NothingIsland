package dev.nothingisland

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Liedtexte von LRCLIB (lrclib.net, frei, ohne Konto). Es werden nur Titel, Interpret, Album und Länge gesendet. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data object Error : LyricsState

    /** Zeilen mit Zeitmarke (ms) oder, falls nur ungetaktet vorhanden, [plain]. */
    data class Found(val lines: List<Pair<Long, String>>, val plain: List<String>) : LyricsState
}

object Lyrics {
    /** Taste in der aufgeklappten Musik-Bubble. Bleibt gemerkt, solange die App läuft. */
    var enabled by mutableStateOf(false)

    private val cache = object : LinkedHashMap<String, LyricsState>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsState>?) = size > 40
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "NothingIsland (https://github.com/Teest0815/NothingIsland)")
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    private fun parse(o: JSONObject): LyricsState.Found? {
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val lines = synced?.let { parseSynced(it) }.orEmpty()
        if (lines.isEmpty() && plain == null) return null
        return LyricsState.Found(lines, plain?.lines().orEmpty())
    }

    /** "[01:23.45] Text" -> (83450, "Text"); mehrere Zeitmarken je Zeile sind erlaubt. */
    internal fun parseSynced(src: String): List<Pair<Long, String>> {
        val tag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val out = ArrayList<Pair<Long, String>>()
        for (raw in src.lines()) {
            val ms = tag.findAll(raw).toList()
            if (ms.isEmpty()) continue
            val text = raw.substring(ms.last().range.last + 1).trim()
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3].let { if (it.isEmpty()) 0L else (it.padEnd(3, '0').take(3)).toLong() }
                out += (min * 60_000 + sec * 1000 + frac) to text
            }
        }
        return out.sortedBy { it.first }
    }

    suspend fun fetch(artist: String, title: String, album: String, durationSec: Int): LyricsState {
        val key = "$artist|$title|$album|$durationSec".lowercase()
        synchronized(cache) { cache[key] }?.let { return it }
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val dur = if (durationSec > 0) "&duration=$durationSec" else ""
                val alb = if (album.isNotBlank()) "&album_name=${enc(album)}" else ""
                get("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(title)}$alb$dur")
                    ?.let { parse(JSONObject(it)) }
                    ?: get("https://lrclib.net/api/search?artist_name=${enc(artist)}&track_name=${enc(title)}")
                        ?.let { arr ->
                            val a = JSONArray(arr)
                            // Bevorzugt Treffer mit Zeitmarken, sonst den ersten mit Text.
                            val all = (0 until a.length()).mapNotNull { parse(a.getJSONObject(it)) }
                            all.firstOrNull { it.lines.isNotEmpty() } ?: all.firstOrNull()
                        }
                    ?: LyricsState.None
            }.getOrElse { LyricsState.Error }
        }
        // Netzwerkfehler nicht merken, damit ein erneuter Versuch möglich ist.
        if (res !is LyricsState.Error) synchronized(cache) { cache[key] = res }
        return res
    }
}

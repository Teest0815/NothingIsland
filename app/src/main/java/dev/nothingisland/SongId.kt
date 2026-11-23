package dev.nothingisland

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** Songerkennung im Hintergrund: Mikrofon ~8 s aufnehmen, an AudD senden, Ergebnis in der Bubble zeigen. */
object SongId {
    private const val KEY = "song-id"
    private const val RATE = 16000
    private const val SECONDS = 8
    @Volatile private var running = false

    fun available(ctx: Context): Boolean =
        IslandBus.settings.value.auddToken.isNotBlank() &&
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Fortschritt: bleibt in der Bubble, bis das Ergebnis kommt. */
    private fun show(title: String, text: String) {
        val ev = IslandEvent(
            key = KEY, app = "SONG", title = title, text = text,
            icon = QuickActions.icon("music"), kind = Kind.STATUS,
        )
        IslandBus.upsertOngoing(ev)
        IslandBus.setPrimary(KEY)
        IslandBus.setExpanded(true)
    }

    /** Ergebnis: normale Meldung, die man wegwischen kann und die von selbst verschwindet. */
    private fun result(title: String, text: String, ctx: Context? = null, query: String? = null) {
        IslandBus.removeOngoing(KEY)
        IslandBus.post(
            IslandEvent(
                key = KEY, app = "SONG", title = title, text = text,
                icon = QuickActions.icon("music"), kind = Kind.OTHER,
                actions = if (ctx != null && query != null) listOf(
                    IslandAction("Öffnen", onClick = { openInMusicApp(ctx, query) }),
                    IslandAction("Kopieren", onClick = { copy(ctx, query) }),
                ) else emptyList(),
            ),
        )
    }

    private fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("song", text))
    }

    /** Erst Spotify, dann andere Musik-Apps (Suche nach "Titel Künstler"), zuletzt die Websuche. */
    private fun openInMusicApp(ctx: Context, query: String) {
        fun go(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        fun search(pkg: String) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
        if (go(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query))).setPackage("com.spotify.music"))) return
        val apps = listOf(
            "com.google.android.apps.youtube.music", "com.apple.android.music", "deezer.android.app",
            "com.amazon.mp3", "com.soundcloud.android", "com.aspiro.tidal", "com.miui.player",
        )
        for (p in apps) if (go(search(p))) return
        val pm = ctx.packageManager
        val any = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
        if (any.resolveActivity(pm) != null && go(any)) return
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
    }

    fun start(ctx: Context) {
        if (running) return
        running = true
        val token = IslandBus.settings.value.auddToken
        show("HÖRE ZU …", "Musik in der Nähe des Mikrofons abspielen")
        Thread {
            try {
                val wav = record()
                if (wav == null) {
                    result("FEHLER", "Mikrofon nicht verfügbar oder im Hintergrund blockiert")
                } else {
                    show("ERKENNE …", "Einen Moment")
                    val r = query(token, wav)
                    if (r == null) result("NICHT ERKANNT", "Es wurde kein Song gefunden")
                    else result(r.first.uppercase().take(40), r.second, ctx.applicationContext, "${r.first} ${r.second.substringBefore(" · ")}".trim())
                }
            } catch (e: Exception) {
                result("FEHLER", "Keine Verbindung oder ungültiger Token")
            } finally {
                running = false
            }
        }.start()
    }

    /** Liefert WAV-Bytes oder null, wenn nichts (oder nur Stille) aufgenommen wurde. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun record(): ByteArray? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
        val pcm = ByteArrayOutputStream()
        var peak = 0
        try {
            rec.startRecording()
            val buf = ByteArray(4096)
            val end = System.currentTimeMillis() + SECONDS * 1000L
            while (System.currentTimeMillis() < end) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                pcm.write(buf, 0, n)
                var i = 0
                while (i + 1 < n) {
                    val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toInt()
                    if (abs(v) > peak) peak = abs(v)
                    i += 2
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        if (pcm.size() < RATE * 2 * 2 || peak < 8) return null
        return wav(pcm.toByteArray())
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        fun i32(v: Int) { for (k in 0..3) o.write((v shr (8 * k)) and 0xff) }
        fun i16(v: Int) { o.write(v and 0xff); o.write((v shr 8) and 0xff) }
        o.write("RIFF".toByteArray()); i32(36 + pcm.size); o.write("WAVEfmt ".toByteArray())
        i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        o.write("data".toByteArray()); i32(pcm.size); o.write(pcm)
        return o.toByteArray()
    }

    /** Titel + "Künstler · Album" oder null, wenn nichts erkannt wurde. */
    private fun query(token: String, wav: ByteArray): Pair<String, String>? {
        val b = "----ni" + System.nanoTime()
        val c = URL("https://api.audd.io/").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "NothingIsland")
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
            c.outputStream.use { os ->
                fun part(name: String, v: String) =
                    os.write("--$b\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$v\r\n".toByteArray())
                part("api_token", token)
                part("return", "")
                os.write("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                os.write(wav)
                os.write("\r\n--$b--\r\n".toByteArray())
            }
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
            val j = JSONObject(body)
            if (j.optString("status") != "success") {
                if (j.has("error")) throw IllegalStateException("audd")
                return null
            }
            val r = j.optJSONObject("result") ?: return null
            val title = r.optString("title").ifEmpty { return null }
            val sub = listOf(r.optString("artist"), r.optString("album")).filter { it.isNotEmpty() }.joinToString(" · ")
            return title to sub
        } finally {
            c.disconnect()
        }
    }
}

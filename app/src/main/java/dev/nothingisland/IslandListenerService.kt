package dev.nothingisland

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IslandListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val host = OverlayHost(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, scope)
    private var connected = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        IslandBus.loadPrefs(this)
        // Zeichnet der Barrierefreiheits-Dienst (über der Statusleiste), bleibt dieses Overlay aus.
        scope.launch {
            IslandBus.accessibilityOverlay.collect { active ->
                if (active) host.remove() else if (connected) showOverlay()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        KeepAliveService.start(this)
        showOverlay()
        // Bereits laufende Aktivitäten (Musik, Timer ...) sofort übernehmen.
        runCatching { activeNotifications?.forEach { handle(it, allowAlert = false) } }
    }

    override fun onListenerDisconnected() {
        connected = false
        removeOverlay()
        runCatching { requestRebind(ComponentName(this, IslandListenerService::class.java)) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    // Laufende Meldungen (Fortschritt, Musik, Training) melden sich oft mehrmals pro Sekunde:
    // höchstens alle 700 ms auswerten, der letzte Stand kommt verzögert nach.
    private val lastHandled = HashMap<String, Long>()
    private val trailing = HashMap<String, Runnable>()
    private val throttleHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        showOverlay() // falls die Berechtigung erst nach dem Verbinden erteilt wurde
        if (sbn.isOngoing) {
            val now = SystemClock.uptimeMillis()
            val last = lastHandled[sbn.key] ?: 0L
            if (now - last < 700L) {
                trailing.remove(sbn.key)?.let { throttleHandler.removeCallbacks(it) }
                val r = Runnable {
                    trailing.remove(sbn.key)
                    lastHandled[sbn.key] = SystemClock.uptimeMillis()
                    runCatching { handle(sbn, allowAlert = true) }
                }
                trailing[sbn.key] = r
                throttleHandler.postDelayed(r, 700L - (now - last))
                return
            }
            lastHandled[sbn.key] = now
        }
        runCatching { handle(sbn, allowAlert = true) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        trailing.remove(sbn.key)?.let { throttleHandler.removeCallbacks(it) }
        lastHandled.remove(sbn.key)
        IslandBus.removeOngoing(sbn.key)
    }

    // ---------------------------------------------------------------- Auswertung

    private fun handle(sbn: StatusBarNotification, allowAlert: Boolean) {
        // Eigene Meldungen ignorieren, außer den Notiz-Erinnerungen.
        if (sbn.packageName == packageName && sbn.notification.channelId != NoteReminders.CH) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // App-Regeln: STUMM = ganz ignorieren, LEISE = ohne Aufklappen, LIVE = laufende Meldung als Live-Aktivität.
        val rule = IslandBus.settings.value.ruleFor(sbn.packageName)
        if (rule == "MUTE") {
            IslandBus.removeOngoing(sbn.key)
            return
        }

        // Ohrhörer-Akku ("Links 100% Lade-Case 60% Rechts 100%"): kurz in der Pille zeigen statt als Meldung.
        run {
            val ex = n.extras
            val all = listOf(
                ex.getCharSequence(Notification.EXTRA_TEXT), ex.getCharSequence(Notification.EXTRA_BIG_TEXT),
                ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
            ).joinToString(" ") { it?.toString().orEmpty() }
            val parsed = parseEarbuds(all) ?: return@run
            if (!IslandBus.settings.value.statusEnabled("bluetooth")) return@run
            val name = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                .replace(Regex("""\s+von\s+.+$""", RegexOption.IGNORE_CASE), "")
            if (lastEarbuds[sbn.key] == parsed.first) return
            lastEarbuds[sbn.key] = parsed.first
            if (allowAlert) {
                IslandBus.post(
                    buildEvent(sbn, Kind.OTHER).copy(
                        title = name.uppercase(), text = parsed.first, textShort = parsed.second,
                        quiet = true, kind = Kind.STATUS,
                    ),
                )
            }
            return
        }

        var kind = classify(n, sbn.isOngoing, sbn.packageName)
        if (kind == null && rule == "LIVE" && sbn.isOngoing) kind = Kind.PROGRESS
        if (kind != null) {
            val event = buildEvent(sbn, kind)
            if (event.title.isNotBlank() || event.text.isNotBlank() || event.chronoBase != null) {
                val isNew = IslandBus.upsertOngoing(event)
                // Eingehender Anruf (klingelt noch): Pille sofort aufklappen, damit Annehmen/Ablehnen erreichbar ist.
                if (isNew && allowAlert && kind == Kind.CALL && event.chronoBase == null) IslandBus.setExpanded(true)
            }
            return
        }
        if (sbn.isOngoing || !allowAlert) return
        if (IslandBus.isMuted(sbn.packageName)) return

        val event = buildEvent(sbn, Kind.OTHER).copy(quiet = rule == "QUIET")
        if (event.title.isBlank() && event.text.isBlank()) return
        IslandBus.post(event)
    }

    /** Liefert die Art einer laufenden Aktivität oder null, wenn es eine normale Meldung ist. */
    private fun classify(n: Notification, ongoing: Boolean, pkg: String): Kind? {
        val ex = n.extras
        val template = ex.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        if (template.endsWith("MediaStyle") || n.category == Notification.CATEGORY_TRANSPORT) {
            return Kind.MEDIA
        }
        // Anrufe und klingelnde Wecker zählen auch dann, wenn sie nicht als "laufend" markiert sind.
        if (n.category == Notification.CATEGORY_CALL) return Kind.CALL
        if (n.category == Notification.CATEGORY_ALARM && (ongoing || n.fullScreenIntent != null)) return Kind.TIMER
        if (!ongoing) return null
        if (pkg in FITNESS_PACKAGES || pkg.startsWith("com.wahoofitness")) {
            // Komoot: Abbiegehinweise sind Navigation, sonst Aufzeichnung.
            val t = (ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() + " " +
                ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()).lowercase()
            val turn = Regex("""\b\d+\s?m\b""").containsMatchIn(t) &&
                listOf("links", "rechts", "left", "right", "abbieg", "turn", "geradeaus", "straight").any { it in t }
            return if (pkg == "de.komoot.android" && turn) Kind.NAVIGATION else Kind.WORKOUT
        }
        return when {
            n.category == "navigation" || pkg in NAV_PACKAGES -> Kind.NAVIGATION
            pkg in LIVE_PACKAGES -> Kind.PROGRESS
            ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> Kind.TIMER
            n.category == Notification.CATEGORY_ALARM || n.category == "stopwatch" -> Kind.TIMER
            ex.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 ||
                ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) -> Kind.PROGRESS
            else -> null
        }
    }

    private val artCache = HashMap<String, Pair<Bitmap, ImageBitmap>>()

    private fun buildEvent(sbn: StatusBarNotification, kind: Kind): IslandEvent {
        val n = sbn.notification
        val ex = n.extras
        // Trainings-Apps (z. B. Wahoo) bauen eigene Ansichten: Texte und Zähler daraus lesen.
        val remote = if (kind == Kind.WORKOUT) remoteTexts(n) else RemoteInfo()
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            .ifBlank { remote.texts.firstOrNull().orEmpty() }
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val progress = if (max > 0 && !indeterminate) {
            ex.getInt(Notification.EXTRA_PROGRESS).toFloat() / max
        } else null

        // Direktantwort (Messenger): erste Aktion mit freiem Texteingabefeld.
        var reply: IslandReply? = null
        n.actions.orEmpty().firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }?.let { a ->
            val ri = a.remoteInputs?.firstOrNull { it.allowFreeFormInput }
            val pi = a.actionIntent
            if (ri != null && pi != null) reply = IslandReply(pi, ri)
        }

        // "Als gelesen markieren": echte Systemaktion, sonst anhand des Namens erkannt.
        val markRead = n.actions.orEmpty().firstOrNull { a ->
            a.remoteInputs.isNullOrEmpty() && a.actionIntent != null && (
                a.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ ||
                    Regex("""(als gelesen|gelesen markieren|mark as read|mark read)""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(a.title?.toString().orEmpty())
                )
        }?.actionIntent

        val code = if (IslandBus.settings.value.otpCodes && kind == Kind.OTHER) findCode("$title $text") else null

        val actions = n.actions.orEmpty()
            .filter { a -> a.remoteInputs.isNullOrEmpty() }
            .take(5)
            .mapNotNull { a ->
                val pi = a.actionIntent ?: return@mapNotNull null
                IslandAction(
                    title = a.title?.toString().orEmpty(),
                    icon = loadIcon(a.getIcon()),
                    intent = pi,
                )
            }

        var chronoBase: Long? = if (chrono) n.`when` else null
        var countDown = chrono && ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        if (chronoBase == null && remote.chronoBase != null) {
            chronoBase = remote.chronoBase
            countDown = remote.countDown
        }
        var rightText: String? = null

        // Musik: Abspielposition aus der Media-Session lesen (läuft live weiter, pausiert = fester Wert).
        var controller: MediaController? = null
        var playing = false
        var positionMs = -1L
        var durationMs = 0L
        var art: ImageBitmap? = null
        var canSkip = false
        if (kind == Kind.MEDIA) {
            runCatching {
                @Suppress("DEPRECATION")
                val token = ex.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                    ?: return@runCatching
                val mc = MediaController(this, token)
                controller = mc
                mc.metadata?.let { md ->
                    durationMs = md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
                    val bmp = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                    // Cover nur neu skalieren, wenn es ein anderes Bild ist (Pause/Weiter liefert dasselbe Bild).
                    art = bmp?.let { src ->
                        artCache[sbn.packageName]?.takeIf { it.first === src }?.second
                            ?: Bitmap.createScaledBitmap(src, 160, 160, true).asImageBitmap().also {
                                if (artCache.size > 6) artCache.clear()
                                artCache[sbn.packageName] = src to it
                            }
                    }
                }
                if (art == null) art = runCatching {
                    n.getLargeIcon()?.loadDrawable(this)?.toBitmap(160, 160)?.asImageBitmap()
                }.getOrNull()
                val st = mc.playbackState ?: return@runCatching
                playing = st.state == PlaybackState.STATE_PLAYING
                canSkip = st.actions and (PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND) != 0L
                if (st.position < 0) return@runCatching
                if (playing) {
                    val since = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
                    val pos = st.position + (since * st.playbackSpeed).toLong()
                    positionMs = pos
                    chronoBase = System.currentTimeMillis() - pos
                    countDown = false
                } else {
                    positionMs = st.position
                    chronoBase = null
                    rightText = mediaClock(st.position)
                }
            }
        }

        val metrics = if (kind == Kind.WORKOUT) {
            val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" • ").orEmpty()
            val all = listOf(
                title, text, ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
                ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty(),
                ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty(), lines,
            ).joinToString(" • ")
            val m = LinkedHashMap(parseMetrics(all))
            m.putAll(parseLabeled(remote.texts))
            if (m["time"] == null) remote.texts.firstOrNull { TIME_ONLY.matches(it) }?.let { m["time"] = it }
            // Diagnose für die Einstellungen: was wurde aus der Benachrichtigung gelesen?
            runCatching {
                getSharedPreferences("island_prefs", Context.MODE_PRIVATE).edit().putString(
                    "workoutDump",
                    "${sbn.packageName}\ntitle=$title\ntext=$text\nansicht=${remote.texts}\nzähler=${remote.chronoBase}\nwerte=$m",
                ).apply()
            }
            m
        } else emptyMap()

        return IslandEvent(
            key = sbn.key,
            pkg = sbn.packageName,
            kind = kind,
            app = appName,
            title = title,
            text = text,
            icon = loadIcon(n.smallIcon),
            avatar = if (kind == Kind.OTHER) loadAvatar(n, ex) else null,
            picture = if (kind == Kind.OTHER && IslandBus.settings.value.showPictures) loadPicture(n, ex) else null,
            action = n.contentIntent,
            actions = actions,
            chronoBase = chronoBase,
            countDown = countDown,
            progress = progress,
            rightText = rightText,
            media = controller,
            playing = playing,
            positionMs = positionMs,
            positionStamp = SystemClock.elapsedRealtime(),
            durationMs = durationMs,
            art = art,
            canSkipSeconds = canSkip,
            reply = reply,
            markRead = markRead,
            code = code,
            metrics = metrics,
        )
    }

    private val codeKey = Regex("""(code|c[oó]digo|otp|tan|pin|passwort|kennwort|verif|best[aä]tig|sicherheits|einmal|one[- ]time|g-)""", RegexOption.IGNORE_CASE)
    private val codeNum = Regex("""(?<![\d.,:/-])(\d{3}[ -]\d{3}|\d{4,8})(?![\d.,:/-])""")

    /** Bestätigungscode erkennen: Schlüsselwort plus 4-8 Ziffern. */
    private fun findCode(s: String): String? {
        if (!codeKey.containsMatchIn(s)) return null
        return codeNum.find(s)?.value
    }

    private val lastEarbuds = HashMap<String, String>()

    /** Liest links / Case / rechts aus Texten wie "Links 100% Lade-Case 60% Rechts 100%". */
    private fun parseEarbuds(t: String): Pair<String, String?>? {
        val o = RegexOption.IGNORE_CASE
        Regex("""(?:links|left)\D{0,3}(\d{1,3})\s?%.*?(?:case|etui)\D{0,3}(\d{1,3})\s?%.*?(?:rechts|right)\D{0,3}(\d{1,3})\s?%""", o)
            .find(t)?.let { return "${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}" to "${it.groupValues[1]}/${it.groupValues[3]}" }
        Regex("""(?:links|left)\D{0,3}(\d{1,3})\s?%.*?(?:rechts|right)\D{0,3}(\d{1,3})\s?%""", o)
            .find(t)?.let { return "${it.groupValues[1]}/${it.groupValues[2]}" to null }
        return null
    }

    private class RemoteInfo(
        val texts: List<String> = emptyList(),
        val chronoBase: Long? = null,
        val countDown: Boolean = false,
    )

    /** Baut die eigene Ansicht der Benachrichtigung nach und liest Texte und Zähler heraus. */
    @Suppress("DEPRECATION")
    private fun remoteTexts(n: Notification): RemoteInfo {
        var best = RemoteInfo()
        for (rv in listOfNotNull(n.bigContentView, n.contentView, n.headsUpContentView)) {
            runCatching {
                val root = rv.apply(this, android.widget.FrameLayout(this))
                val texts = ArrayList<String>()
                var base: Long? = null
                var down = false
                fun walk(v: android.view.View) {
                    if (v is android.widget.Chronometer) {
                        val el = SystemClock.elapsedRealtime()
                        val now = System.currentTimeMillis()
                        down = v.isCountDown
                        base = if (down) v.base - el + now else now - (el - v.base)
                        v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
                    } else if (v is android.widget.TextView) {
                        v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
                    }
                    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                walk(root)
                if (texts.size > best.texts.size || (best.chronoBase == null && base != null && texts.isNotEmpty())) {
                    best = RemoteInfo(texts, base, down)
                }
            }
        }
        return best
    }

    /** Wertepaare der eigenen Ansicht: Beschriftung, dann Wert (z. B. "BPM", "142"). */
    private fun parseLabeled(texts: List<String>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (i in 0 until texts.size - 1) {
            val label = texts[i].lowercase().trim()
            val raw = texts[i + 1].trim()
            if (!raw.any { it.isDigit() } || raw.equals("n/a", true)) continue
            val v = raw.replace(',', '.')
            when {
                "bpm" in label || "puls" in label || "herz" in label || "heart" in label -> m["bpm"] = "$v bpm"
                "min/mi" in label -> m["pace"] = "$v /mi"
                "min/" in label || "pace" in label || "tempo" in label -> m["pace"] = "$v /km"
                "schritte" in label || "spm" in label || "cadence" in label || "kadenz" in label || "rpm" in label -> m["cad"] = "$v spm"
                label == "w" || "watt" in label || "power" in label || "leistung" in label -> m["power"] = "$v W"
                "km/h" in label || "speed" in label || "geschw" in label -> m["speed"] = "$v km/h"
                "kcal" in label || "kalorien" in label -> m["kcal"] = "$v kcal"
                label == "km" || label == "mi" || "dist" in label || "strecke" in label -> m["dist"] = "$v $label".replace("dist", "km")
            }
        }
        return m
    }

    /** Liest Trainingswerte aus dem Text der Benachrichtigung (Deutsch und Englisch). */
    private fun parseMetrics(src: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        var t = src
        val o = RegexOption.IGNORE_CASE
        fun num(v: String) = v.replace(',', '.')
        Regex("""(\d{1,2}:\d{2})\s*(?:min)?\s*/\s*(km|mi)""", o).find(t)?.let {
            m["pace"] = "${it.groupValues[1]} /${it.groupValues[2].lowercase()}"; t = t.replace(it.value, " ")
        }
        Regex("""(\d+(?:[.,]\d+)?)\s*(km/h|kmh|mph)""", o).find(t)?.let {
            m["speed"] = "${num(it.groupValues[1])} ${it.groupValues[2].lowercase()}"; t = t.replace(it.value, " ")
        }
        Regex("""(\d{2,3})\s*(?:bpm|♥|❤)|(?:herzfrequenz|heart rate|puls|hr)\D{0,3}(\d{2,3})""", o).find(t)?.let {
            val v = it.groupValues[1].ifEmpty { it.groupValues[2] }
            m["bpm"] = "$v bpm"; t = t.replace(it.value, " ")
        }
        Regex("""(\d+(?:[.,]\d+)?)\s*(km|mi)\b""", o).find(t)?.let {
            m["dist"] = "${num(it.groupValues[1])} ${it.groupValues[2].lowercase()}"; t = t.replace(it.value, " ")
        }
        Regex("""(\d{1,4})\s*(?:w|watt)\b""", o).find(t)?.let { m["power"] = "${it.groupValues[1]} W"; t = t.replace(it.value, " ") }
        Regex("""(\d{2,3})\s*(rpm|spm)""", o).find(t)?.let { m["cad"] = "${it.groupValues[1]} ${it.groupValues[2].lowercase()}"; t = t.replace(it.value, " ") }
        Regex("""(\d{1,5})\s*(?:kcal|cal)\b""", o).find(t)?.let { m["kcal"] = "${it.groupValues[1]} kcal"; t = t.replace(it.value, " ") }
        Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""").find(t)?.let { m["time"] = it.groupValues[1] }
        return m
    }

    private fun mediaClock(ms: Long): String {
        val t = ms.coerceAtLeast(0) / 1000
        return if (t >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
        else String.format(java.util.Locale.ROOT, "%02d:%02d", t / 60, t % 60)
    }

    /** Profil- oder Gruppenbild: großes Symbol, sonst Bild des letzten Absenders (MessagingStyle). */
    private fun loadAvatar(n: Notification, ex: android.os.Bundle): ImageBitmap? = runCatching {
        fun bmp(i: Icon?) = i?.loadDrawable(this)?.toBitmap(128, 128)?.asImageBitmap()
        bmp(n.getLargeIcon())?.let { return@runCatching it }
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = msgs?.lastOrNull() as? android.os.Bundle
        @Suppress("DEPRECATION")
        val p = last?.getParcelable<android.app.Person>("sender_person")
        bmp(p?.icon)
    }.getOrNull()

    /** Bild oder Sticker: BigPicture der Meldung, sonst das Bild der letzten Chat-Nachricht (Messenger). */
    private fun loadPicture(n: Notification, ex: android.os.Bundle): ImageBitmap? = runCatching {
        fun scaled(b: Bitmap): ImageBitmap {
            val m = maxOf(b.width, b.height)
            return (if (m > 360) Bitmap.createScaledBitmap(b, b.width * 360 / m, b.height * 360 / m, true) else b).asImageBitmap()
        }
        @Suppress("DEPRECATION")
        (ex.get(Notification.EXTRA_PICTURE) as? Bitmap)?.let { return@runCatching scaled(it) }
        if (Build.VERSION.SDK_INT >= 31) {
            (ex.get("android.pictureIcon") as? Icon)?.loadDrawable(this)?.toBitmap(360, 360)?.let { return@runCatching scaled(it) }
        }
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = msgs?.lastOrNull() as? android.os.Bundle ?: return@runCatching null
        val type = last.getString("type") ?: return@runCatching null
        @Suppress("DEPRECATION")
        val uri = last.getParcelable<android.net.Uri>("uri") ?: return@runCatching null
        if (!type.startsWith("image/")) return@runCatching null
        val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
            val m = maxOf(info.size.width, info.size.height)
            if (m > 360) dec.setTargetSize(info.size.width * 360 / m, info.size.height * 360 / m)
            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        bmp.asImageBitmap()
    }.getOrNull()

    private fun loadIcon(icon: Icon?): ImageBitmap? = runCatching {
        icon?.loadDrawable(this)?.toBitmap(96, 96)?.asImageBitmap()
    }.getOrNull()

    // ---------------------------------------------------------------- Overlay

    /** Kann von der Settings-Activity aufgerufen werden, nachdem die Berechtigung erteilt wurde. */
    fun ensureOverlay() = showOverlay()

    private fun showOverlay() {
        if (IslandBus.accessibilityOverlay.value) return // der Barrierefreiheits-Dienst übernimmt
        if (!Settings.canDrawOverlays(this)) return
        host.show()
    }

    private fun removeOverlay() = host.remove()

    companion object {
        @Volatile
        var instance: IslandListenerService? = null

        /** Karten-Apps, deren laufende Benachrichtigung als Navigation angezeigt wird. */
        private val NAV_PACKAGES = setOf(
            "com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus",
            "app.organicmaps", "com.here.app.maps", "com.sygic.aura",
        )

        private val TIME_ONLY = Regex("""\d{1,2}:\d{2}(:\d{2})?""")

        /** Trainings-Apps: laufende Aufzeichnung wird als Training angezeigt (Zeit links, Wert rechts). */
        private val FITNESS_PACKAGES = setOf(
            "com.strava", "fi.polar.polarflow", "fi.polar.beat", "com.wahoofitness.fitness", "com.wahoofitness.bolt",
            "com.wahoofitness.elemnt", "de.komoot.android", "com.garmin.android.apps.connectmobile",
        )

        /** Lieferdienste, Fahrdienste und Co.: laufende Meldung wird als Live-Aktivität gezeigt. */
        private val LIVE_PACKAGES = setOf(
            "com.ubercab", "com.ubercab.eats", "de.lieferando.android", "com.takeaway.android", "com.deliveroo.orderapp",
            "com.wolt.android", "ee.mtakso.client", "com.bolt.android", "de.flaschenpost.app", "com.gorillas.android",
            "com.dhl.paket", "de.dhl.paket", "com.dpd.android", "com.flixbus.app", "de.bahn.dbnav", "com.lyft.android",
            "com.grubhub.android", "com.application.zomato", "com.global.foodpanda.android",
        )

        @Volatile
        private var lastRebind = 0L

        /**
         * Nach App-Update, Neustart oder Beenden der App wird der Benachrichtigungszugriff auf manchen
         * Geräten nicht neu verbunden. Das Aus- und Einschalten der Komponente zwingt Android dazu
         * (entspricht dem manuellen Aus- und Einschalten der Berechtigung).
         */
        fun rebind(context: Context) {
            if (instance != null) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastRebind < 60_000) return
            lastRebind = now
            val cn = ComponentName(context, IslandListenerService::class.java)
            runCatching {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            runCatching { NotificationListenerService.requestRebind(cn) }
        }
    }
}

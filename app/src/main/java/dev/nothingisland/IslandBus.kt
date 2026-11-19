package dev.nothingisland

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Reihenfolge = Priorität in der kleinen Pille. */
enum class Kind { CALL, NAVIGATION, TIMER, WORKOUT, MEDIA, PROGRESS, STATUS, OTHER }

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
    /** Paketname der absendenden App (zum Stummschalten). */
    val pkg: String = "",
    val kind: Kind = Kind.OTHER,
    val app: String,
    val title: String,
    val text: String,
    val icon: ImageBitmap? = null,
    /** Profil-/Gruppenbild des Absenders (Messenger), falls vorhanden. */
    val avatar: ImageBitmap? = null,
    /** Bild oder Sticker der Meldung (BigPicture oder Bild der letzten Chat-Nachricht). */
    val picture: ImageBitmap? = null,
    /** Notiz-Eingabe: statt einer Antwort wird der Text hier abgegeben. */
    val noteSink: ((String) -> Unit)? = null,
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
    /** Training: erkannte Werte (bpm, dist, pace, speed, power, cad, kcal, time). */
    val metrics: Map<String, String> = emptyMap(),
    /** Kürzere Fassung von [text], falls der Platz nicht reicht. */
    val textShort: String? = null,
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
    /** Eigene Aktion beim Antippen der kleinen Meldungs-Pille (statt [action]). */
    val tap: (() -> Unit)? = null,
    /** "Als gelesen markieren" der Meldung (Messenger bieten die Aktion an). */
    val markRead: PendingIntent? = null,
    /** Zusammengefasste Meldungen derselben App. */
    val count: Int = 1,
    /** Ungelesene Nachrichten dieser Meldung (Messenger bündeln mehrere in einer Meldung). */
    val msgCount: Int = 1,
    /** Erkannter Bestätigungscode (OTP). */
    val code: String? = null,
    /** Wann die Meldung eintraf (für den eigenen Ablauf jeder Karte im Stapel). */
    val postedAt: Long = System.currentTimeMillis(),
)

data class IslandSettings(
    val offsetX: Int = 0,
    /** 0 = symmetrisch um die Mitte, 1 = wächst nur nach links (Kamera rechts), 2 = nur nach rechts (Kamera links). */
    val expandDir: Int = 0,
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
    /** Kartenstapel: 0 = locker, 1 = ganz kompakt. */
    val stackCompact: Float = 0.5f,
    /** Deckkraft der Karten hinter der obersten (0.15 .. 1). */
    val stackAlpha: Float = 0.55f,
    /** 0 Fein, 1 Sanft, 2 Klick, 3 Doppelt, 4 Frei (Dauer und Stärke selbst wählen). */
    val hapticStyle: Int = 1,
    val hapticMs: Int = 25,
    val hapticAmp: Int = 110,
    /** Equalizer-Balken bei laufender Musik. */
    val equalizer: Boolean = true,
    /** System-Pop-ups (Heads-up) anderer Apps ausschalten, solange die Island läuft. Braucht WRITE_SECURE_SETTINGS. */
    val suppressPopups: Boolean = false,
    /** Island ausblenden bei Vollbild (Video, Spiel) bzw. im Querformat. Eingehende Anrufe bleiben sichtbar. */
    val hideFullscreen: Boolean = false,
    val hideLandscape: Boolean = false,
    /** Sparmodus: 0 aus, 1 automatisch (Systemsparmodus oder Akku unter 20 %), 2 immer. */
    val powerSaver: Int = 1,
    /** Wischen auf der Musik-Pille: Song vor/zurück (die Pille dehnt sich in die Richtung). */
    val swipeSkip: Boolean = true,
    /** Meldung nach unten ziehen = als gelesen markieren. */
    val swipeDownRead: Boolean = true,
    /** Mehrere Benachrichtigungen nacheinander zeigen statt die vorige zu überschreiben. */
    val multiNotif: Boolean = true,
    /** Ziffern von Timern und Zählern rollen beim Wechsel. */
    val rollDigits: Boolean = true,
    /** Große Bubble wird zur kleinen: erst kleiner, dann etwas breiter, dann normal. */
    val squashEntry: Boolean = true,
    /** Ladeanzeige: Balken füllt sich, Prozent zählen hoch. */
    val chargeAnim: Boolean = true,
    /** Mehrere Meldungen derselben App zusammenfassen. */
    val groupNotif: Boolean = false,
    /** Nachrichten derselben Person (gleiche App, gleicher Absender) zu einer Karte zusammenfassen. */
    val groupBySender: Boolean = false,
    /** Beim Öffnen nach einer neuen Version suchen. */
    val updateCheck: Boolean = true,
    /** Angepinnte Notizen (JSON-Liste der Texte), stehen ganz oben. */
    val notePins: String = "[]",
    /** Bestätigungscodes erkennen und Kopieren anbieten. */
    val otpCodes: Boolean = true,
    /** Symbol der kleinen Pille bei Musik: 0 App-Symbol, 1 Musiknote, 2 Cover. */
    val pillIconMedia: Int = 0,
    /** Wischen nach oben auf der kleinen Pille: Kachel-Schlüssel, "panel" oder leer (aus). */
    val swipeUpAction: String = "",
    /** Symbol der kleinen Pille bei Meldungen: 0 App-Symbol, 1 Profil-/Gruppenbild. */
    val pillIconNotif: Int = 0,
    /** Bei Musik: Nach-unten-Ziehen pausiert/startet die Wiedergabe. */
    val swipeDownPause: Boolean = false,
    /** Meldung nach oben wischen = App für [muteMinutes] stummschalten (nur in der Island, Android-Meldung bleibt). */
    val muteOnSwipeUp: Boolean = true,
    val muteMinutes: Int = 5,
    /** Bilder und Sticker aus Meldungen (z. B. WhatsApp) anzeigen. */
    val showPictures: Boolean = true,
    /** Bei einseitigem Aufgehen: auch große Blasen wachsen aus der Kamera-Kante statt mittig. */
    val bigFromCamera: Boolean = false,
    val landShift: Int = 0,
    val landEdge: Int = 4,
    val landBigW: Int = 170,
    val landBigScale: Float = 1f,
    /** Leuchten um die Island. */
    val glow: Boolean = true,
    val glowStrength: Float = 0.7f,
    val glowSize: Int = 16,
    val glowNotif: Boolean = true,
    val glowMedia: Boolean = true,
    val glowCall: Boolean = true,
    val glowCharge: Boolean = true,
    val glowPrivacy: Boolean = true,
    val glowBattery: Boolean = true,
    /** Notizen der Notiz-Pille als JSON-Liste (neueste zuletzt). */
    val notes: String = "[]",
    /** Kopierten Text kurz in der Pille zeigen. */
    val clipboard: Boolean = true,
    /** Training: Wert rechts in der Pille (bpm, dist, pace, speed, power, cad, kcal). */
    val workoutMetric: String = "bpm",
    /** Reihenfolge der Schnellzugriff-Kacheln. */
    val quickTiles: String = "torch,ringer,dnd,hotspot,airplane,wifi,bluetooth,app",
    /** App-Regeln: "paket=REGEL,paket=REGEL" mit MUTE, QUIET oder LIVE. */
    val appRules: String = "",
    /** nothing, white, midnight, mint, amber, violet oder custom. */
    val theme: String = "nothing",
    val themeHue: Float = 0f,
    /** Nur bei custom: Sättigung und Helligkeit der Akzentfarbe (0..1). */
    val themeSat: Float = 0.85f,
    val themeVal: Float = 0.95f,
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

    /** Bildschirmdrehung (Surface.ROTATION_*): 1 = Kamera links, 3 = Kamera rechts, sonst Hochformat. */
    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()
    fun setRotation(r: Int) { _rotation.value = r }

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

    /** Bildschirm an? Bei aus laufen keine Zeit-Schleifen. */
    private val _wakeTick = MutableStateFlow(0)
    /** Zählt hoch, wenn der Bildschirm angeht (löst das federnde Hereinkommen der Pille aus). */
    val wakeTick: StateFlow<Int> = _wakeTick.asStateFlow()

    /** Zeitpunkt des letzten Aufwachens (Leuchten blitzt direkt danach nicht auf). */
    @Volatile var lastWakeAt: Long = 0L

    fun bumpWake() {
        _wakeTick.value += 1
    }

    private val _charging = MutableStateFlow(false)
    /** Ladegerät steckt. */
    val charging: StateFlow<Boolean> = _charging.asStateFlow()

    fun setCharging(v: Boolean) {
        if (_charging.value != v) _charging.value = v
    }

    private val _screenOn = MutableStateFlow(true)
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    fun setScreenOn(v: Boolean) {
        if (_screenOn.value != v) _screenOn.value = v
    }

    /** true, wenn der Sparmodus der Island gerade aktiv ist (keine Dauer-Animationen, seltener prüfen). */
    private val _lowPower = MutableStateFlow(false)
    val lowPower: StateFlow<Boolean> = _lowPower.asStateFlow()

    fun setLowPower(v: Boolean) {
        if (_lowPower.value != v) _lowPower.value = v
    }

    /** true, wenn die Statusleiste gerade ausgeblendet ist (Vollbild-Video, Spiel ...). */
    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    fun setFullscreen(v: Boolean) {
        if (_fullscreen.value != v) _fullscreen.value = v
    }

    private val _settings = MutableStateFlow(IslandSettings())
    val settings: StateFlow<IslandSettings> = _settings.asStateFlow()

    /** Wartende Meldungen hinter der aktuellen (nur bei "mehrere Benachrichtigungen"). */
    private val _pending = MutableStateFlow<List<IslandEvent>>(emptyList())
    val pending: StateFlow<List<IslandEvent>> = _pending.asStateFlow()

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
        if (!value) removeOngoing("charge-detail")
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
        if (value) { _calc.value = false; _note.value = false }
    }

    /** Notiz-Pille. */
    private val _note = MutableStateFlow(false)
    val note: StateFlow<Boolean> = _note.asStateFlow()

    fun setNote(value: Boolean) {
        _note.value = value
        if (value) { _quick.value = false; _calc.value = false }
    }

    fun noteList(json: String): List<String> = runCatching {
        val a = org.json.JSONArray(json)
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun addNote(ctx: Context, text: String) {
        updateSettings(ctx) { s -> s.copy(notes = org.json.JSONArray((noteList(s.notes) + text).takeLast(30)).toString()) }
    }

    fun pinnedNotes(json: String): Set<String> = noteList(json).toSet()

    fun togglePin(ctx: Context, text: String) {
        updateSettings(ctx) { s ->
            val pins = noteList(s.notePins).toMutableList()
            if (!pins.remove(text)) pins.add(text)
            s.copy(notePins = org.json.JSONArray(pins).toString())
        }
    }

    fun removeNote(ctx: Context, index: Int) {
        noteList(settings.value.notes).getOrNull(index)?.let { NoteReminders.cancelForText(ctx, it) }
        updateSettings(ctx) { s ->
            val gone = noteList(s.notes).getOrNull(index)
            s.copy(
                notes = org.json.JSONArray(noteList(s.notes).filterIndexed { i, _ -> i != index }).toString(),
                notePins = org.json.JSONArray(noteList(s.notePins).filter { it != gone }).toString(),
            )
        }
    }

    /** Taschenrechner in der großen Pille. */
    private val _calc = MutableStateFlow(false)
    val calc: StateFlow<Boolean> = _calc.asStateFlow()

    fun setCalc(value: Boolean) {
        _calc.value = value
        if (value) { _quick.value = false; _note.value = false }
    }

    /** Meldung, auf die gerade direkt in der Island geantwortet wird. */
    private val _replyTarget = MutableStateFlow<IslandEvent?>(null)
    val replyTarget: StateFlow<IslandEvent?> = _replyTarget.asStateFlow()

    /** true, sobald das Antwort-Fenster sichtbar ist; erst dann tritt die Pille zurück (nahtloser Übergang). */
    private val _replyShown = MutableStateFlow(false)
    val replyShown: StateFlow<Boolean> = _replyShown.asStateFlow()

    fun setReplyShown(value: Boolean) {
        _replyShown.value = value
    }

    /** true, solange das Antwort-/Notiz-Fenster ausblendet (kurz vor dem Entfernen). */
    private val _replyClosing = MutableStateFlow(false)
    val replyClosing: StateFlow<Boolean> = _replyClosing.asStateFlow()

    fun setReplyClosing(value: Boolean) {
        _replyClosing.value = value
    }

    /** Alle großen Ansichten schließen (Schnellzugriff, Rechner, Notiz, Eingabe), z. B. beim Sperren. */
    fun closePanels() {
        _quick.value = false
        _calc.value = false
        _note.value = false
        _expanded.value = false
        if (_replyTarget.value != null) endReply()
    }

    /** Notiz schließen wie sonst: Eingabefenster blendet aus, die Notiz-Bubble darunter schrumpft normal zur Pille. */
    fun closeNoteAnimated() {
        if (_replyTarget.value != null) endReply()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ setNote(false) }, 110)
    }

    fun startReply(e: IslandEvent) {
        _replyClosing.value = false
        _replyTarget.value = e
    }

    fun endReply() {
        _replyTarget.value = null
        _replyShown.value = false
    }

    /** Tippen außerhalb der Island: aufgeklappte Pille und Meldung wieder verkleinern. */
    fun collapse() {
        _replyTarget.value = null
        _replyShown.value = false
        _quick.value = false
        _calc.value = false
        _note.value = false
        _expanded.value = false
        removeOngoing("charge-detail")
        _pending.value = emptyList()
        _current.value = null
    }

    // ---- Kurzzeit-Meldungen (neue Benachrichtigungen) ----
    /** Gehören zwei Meldungen zusammen? Gleiche App, bei "pro Person" auch gleicher Absender (Titel). */
    private fun sameGroup(a: IslandEvent, b: IslandEvent): Boolean {
        val s = _settings.value
        if (!(s.groupNotif || s.groupBySender)) return false
        if (a.kind != Kind.OTHER || b.kind != Kind.OTHER || a.code != null || b.code != null) return false
        return a.app == b.app && (!s.groupBySender || a.title == b.title)
    }

    /** Zähler beim Zusammenfassen: pro Person zählt jede Nachricht, sonst jede neue Meldung. */
    private fun mergedCount(old: IslandEvent, ev: IslandEvent): Int {
        val perPerson = _settings.value.groupBySender
        return if (ev.key == old.key) {
            if (perPerson) maxOf(old.count, ev.msgCount) else old.count
        } else {
            old.count + if (perPerson) ev.msgCount else 1
        }
    }

    fun post(event: IslandEvent) {
        val cur = _current.value
        if (_settings.value.multiNotif && cur != null && !cur.quiet && !event.quiet) {
            if (sameGroup(cur, event)) {
                // Zusammenfassen: neueste Meldung zeigen, Zähler hochzählen.
                _current.value = event.copy(count = mergedCount(cur, event))
            } else if (event.key.isNotEmpty() && cur.key == event.key && cur.title == event.title && cur.text == event.text) {
                _current.value = event // gleiche Meldung nur erneut gemeldet: ersetzen statt doppelt stapeln
            } else {
                // Neueste Meldung kommt nach oben, die bisherige oberste rutscht direkt dahinter.
                val list = _pending.value
                val same = list.firstOrNull { sameGroup(it, event) }
                val top = if (same != null) event.copy(count = mergedCount(same, event)) else event
                val rest = list.filterNot {
                    it === same || (event.key.isNotEmpty() && it.key == event.key && it.title == event.title && it.text == event.text)
                }
                _pending.value = (listOf(cur) + rest).take(5)
                _current.value = top
            }
            return
        }
        _current.value = event
    }

    /** Eine Karte hinter der obersten entfernen (ihre Zeit ist abgelaufen). */
    fun removePending(id: Long) {
        _pending.update { list -> list.filterNot { it.id == id } }
    }

    // ---- Stummschalten einzelner Apps (nur in der Island) ----
    private val mutedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Verbleibende Stummzeit in Millisekunden (0 = nicht stumm). */
    fun muteRemainingMs(pkg: String): Long = if (isMuted(pkg)) (mutedUntil[pkg] ?: 0L) - System.currentTimeMillis() else 0L

    fun isMuted(pkg: String): Boolean {
        val until = mutedUntil[pkg] ?: return false
        if (System.currentTimeMillis() < until) return true
        mutedUntil.remove(pkg)
        return false
    }

    /** App für [minutes] Minuten stummschalten; schon wartende Meldungen der App fallen weg. */
    fun mute(pkg: String, minutes: Int) {
        mutedUntil[pkg] = System.currentTimeMillis() + minutes * 60_000L
        _pending.update { l -> l.filterNot { it.pkg == pkg } }
    }

    fun clearMutes() = mutedUntil.clear()

    fun dismiss() {
        val next = _pending.value.firstOrNull()
        if (next != null) {
            _pending.update { it.drop(1) }
            _current.value = next
        } else {
            _current.value = null
        }
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
        // Einmalig: Wecker-Anzeige wieder standardmäßig an.
        if (!p.getBoolean("alarmOnV2", false)) {
            val off = p.getString("statusOff", d.statusOff).orEmpty().split(',').filter { it.isNotBlank() && it != "alarm" }
            p.edit().putString("statusOff", off.joinToString(",")).putBoolean("alarmOnV2", true).apply()
        }
        _settings.value = IslandSettings(
            offsetX = p.getInt("offsetX", d.offsetX),
            expandDir = p.getInt("expandDir", d.expandDir),
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
            stackCompact = p.getFloat("stackCompact", d.stackCompact),
            stackAlpha = p.getFloat("stackAlpha", d.stackAlpha),
            hapticStyle = p.getInt("hapticStyle", d.hapticStyle),
            hapticMs = p.getInt("hapticMs", d.hapticMs),
            hapticAmp = p.getInt("hapticAmp", d.hapticAmp),
            equalizer = p.getBoolean("equalizer", d.equalizer),
            suppressPopups = p.getBoolean("suppressPopups", d.suppressPopups),
            hideFullscreen = p.getBoolean("hideFullscreen", d.hideFullscreen),
            hideLandscape = p.getBoolean("hideLandscape", d.hideLandscape),
            powerSaver = p.getInt("powerSaver", d.powerSaver),
            swipeSkip = p.getBoolean("swipeSkip", d.swipeSkip),
            swipeDownRead = p.getBoolean("swipeDownRead", d.swipeDownRead),
            multiNotif = p.getBoolean("multiNotif", d.multiNotif),
            squashEntry = p.getBoolean("squashEntry", d.squashEntry),
            rollDigits = p.getBoolean("rollDigits", d.rollDigits),
            chargeAnim = p.getBoolean("chargeAnim", d.chargeAnim),
            groupNotif = p.getBoolean("groupNotif2", d.groupNotif),
            groupBySender = p.getBoolean("groupBySender", d.groupBySender),
            updateCheck = p.getBoolean("updateCheck", d.updateCheck),
            notePins = p.getString("notePins", d.notePins) ?: d.notePins,
            otpCodes = p.getBoolean("otpCodes", d.otpCodes),
            pillIconMedia = p.getInt("pillIconMedia", d.pillIconMedia),
            swipeUpAction = p.getString("swipeUpAction", d.swipeUpAction) ?: d.swipeUpAction,
            pillIconNotif = p.getInt("pillIconNotif", d.pillIconNotif),
            swipeDownPause = p.getBoolean("swipeDownPause", d.swipeDownPause),
            muteOnSwipeUp = p.getBoolean("muteOnSwipeUp", d.muteOnSwipeUp),
            muteMinutes = p.getInt("muteMinutes", d.muteMinutes),
            showPictures = p.getBoolean("showPictures", d.showPictures),
            bigFromCamera = p.getBoolean("bigFromCamera", d.bigFromCamera),
            landShift = p.getInt("landShift", d.landShift),
            landEdge = p.getInt("landEdge", d.landEdge),
            landBigW = p.getInt("landBigW2", d.landBigW),
            landBigScale = p.getFloat("landBigScale", d.landBigScale),
            glow = p.getBoolean("glow", d.glow),
            glowStrength = p.getFloat("glowStrength", d.glowStrength),
            glowSize = p.getInt("glowSize", d.glowSize),
            glowNotif = p.getBoolean("glowNotif", d.glowNotif),
            glowMedia = p.getBoolean("glowMedia", d.glowMedia),
            glowCall = p.getBoolean("glowCall", d.glowCall),
            glowCharge = p.getBoolean("glowCharge", d.glowCharge),
            glowPrivacy = p.getBoolean("glowPrivacy", d.glowPrivacy),
            glowBattery = p.getBoolean("glowBattery", d.glowBattery),
            notes = p.getString("notes", d.notes) ?: d.notes,
            clipboard = p.getBoolean("clipboard", d.clipboard),
            workoutMetric = p.getString("workoutMetric", d.workoutMetric) ?: d.workoutMetric,
            quickTiles = p.getString("quickTiles", d.quickTiles) ?: d.quickTiles,
            appRules = p.getString("appRules", d.appRules) ?: "",
            theme = p.getString("theme", d.theme) ?: d.theme,
            themeHue = p.getFloat("themeHue", d.themeHue),
            themeSat = p.getFloat("themeSat", d.themeSat),
            themeVal = p.getFloat("themeVal", d.themeVal),
            themeBg = p.getInt("themeBg", d.themeBg),
            themeFont = p.getInt("themeFont", d.themeFont),
        )
    }

    fun updateSettings(context: Context, transform: (IslandSettings) -> IslandSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("offsetX", s.offsetX)
            .putInt("expandDir", s.expandDir)
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
            .putFloat("stackCompact", s.stackCompact)
            .putFloat("stackAlpha", s.stackAlpha)
            .putInt("hapticStyle", s.hapticStyle)
            .putInt("hapticMs", s.hapticMs)
            .putInt("hapticAmp", s.hapticAmp)
            .putBoolean("equalizer", s.equalizer)
            .putBoolean("suppressPopups", s.suppressPopups)
            .putBoolean("hideFullscreen", s.hideFullscreen)
            .putBoolean("hideLandscape", s.hideLandscape)
            .putInt("powerSaver", s.powerSaver)
            .putBoolean("swipeSkip", s.swipeSkip)
            .putBoolean("swipeDownRead", s.swipeDownRead)
            .putBoolean("multiNotif", s.multiNotif)
            .putBoolean("squashEntry", s.squashEntry)
            .putBoolean("rollDigits", s.rollDigits)
            .putBoolean("chargeAnim", s.chargeAnim)
            .putBoolean("groupNotif2", s.groupNotif)
            .putBoolean("groupBySender", s.groupBySender)
            .putBoolean("updateCheck", s.updateCheck)
            .putString("notePins", s.notePins)
            .putBoolean("otpCodes", s.otpCodes)
            .putInt("pillIconMedia", s.pillIconMedia)
            .putString("swipeUpAction", s.swipeUpAction)
            .putInt("pillIconNotif", s.pillIconNotif)
            .putBoolean("swipeDownPause", s.swipeDownPause)
            .putBoolean("muteOnSwipeUp", s.muteOnSwipeUp)
            .putInt("muteMinutes", s.muteMinutes)
            .putBoolean("showPictures", s.showPictures)
            .putBoolean("bigFromCamera", s.bigFromCamera)
            .putInt("landShift", s.landShift)
            .putInt("landEdge", s.landEdge)
            .putInt("landBigW2", s.landBigW)
            .putFloat("landBigScale", s.landBigScale)
            .putBoolean("glow", s.glow)
            .putFloat("glowStrength", s.glowStrength)
            .putInt("glowSize", s.glowSize)
            .putBoolean("glowNotif", s.glowNotif)
            .putBoolean("glowMedia", s.glowMedia)
            .putBoolean("glowCall", s.glowCall)
            .putBoolean("glowCharge", s.glowCharge)
            .putBoolean("glowPrivacy", s.glowPrivacy)
            .putBoolean("glowBattery", s.glowBattery)
            .putString("notes", s.notes)
            .putBoolean("clipboard", s.clipboard)
            .putString("workoutMetric", s.workoutMetric)
            .putString("quickTiles", s.quickTiles)
            .putString("appRules", s.appRules)
            .putString("theme", s.theme)
            .putFloat("themeHue", s.themeHue)
            .putFloat("themeSat", s.themeSat)
            .putFloat("themeVal", s.themeVal)
            .putInt("themeBg", s.themeBg)
            .putInt("themeFont", s.themeFont)
            .apply()
    }
}


/** Feste Kante der kleinen Pille in dp (Bildschirmkoordinaten): rechte Kante bei Richtung 1, linke bei 2. */
internal fun IslandSettings.anchorDp(screenW: Float): Float {
    val idle = BASE_IDLE_W * scale * widthFactor
    val c = screenW / 2f + offsetX
    return if (expandDir == 1) c + idle / 2f else c - idle / 2f
}

/** Linke Kante einer Blase der Breite w (dp) bei einseitigem Aufgehen, im Bildschirm gehalten. */
internal fun IslandSettings.bubbleLeftDp(screenW: Float, w: Float): Float {
    val a = anchorDp(screenW)
    val l = if (expandDir == 1) a - w else a
    return l.coerceIn(4f, maxOf(4f, screenW - 4f - w))
}

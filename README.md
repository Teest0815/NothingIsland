# Nothing Island

Eine Dynamic Island für Android im Nothing-OS-Look: schwarz, Monospace-Schrift, roter Akzent, Punktmatrix-Ring.
Gebaut mit Kotlin und Jetpack Compose, getestet auf Xiaomi/HyperOS.

<p align="center">
  <img src="docs/demo.gif" alt="Nothing Island: Musik-Pille klappt auf und wieder zu" width="420">
</p>

## Installieren

1. Unter **Releases → latest** die `NothingIsland.apk` laden (jede Version gibt es zusätzlich als `NothingIsland-v1.N.apk`).
2. Installieren. Eine vorhandene Version wird einfach überschrieben, Einstellungen bleiben erhalten.
3. App öffnen und die Berechtigungen vergeben:
   - **Über anderen Apps anzeigen**
   - **Zugriff auf Benachrichtigungen**
   - **Bedienungshilfe** (damit die Island auch über der Statusleiste liegt)
4. Mit **TEST ANZEIGEN** im START-Tab ausprobieren.

Die Versionsnummer steht im START-Tab und im Dateinamen. Android 10 oder neuer wird benötigt.

## Was die Island kann

**Kleine Pille und Live-Aktivitäten**
- Ruhe-Pille, die um die Frontkamera sitzt, und kompakte Pille für laufende Dinge: Musik, Timer, Anruf, Navigation, Training, Downloads.
- Mehrere Aktivitäten: ein Kreis daneben zeigt die zweite, Antippen tauscht sie.
- Statuspillen für Taschenlampe, Flugmodus, Hotspot, Ladegerät, Bluetooth-Kopfhörer, Akku-Warnung (20 % und 10 %), nächsten Termin und Zwischenablage.

**Meldungen**
- Neue Benachrichtigungen klappen auf, mit App-Name, Titel und Text.
- Direkt antworten, ohne die App zu öffnen.
- **Als gelesen markieren:** per Haken oder indem du die Meldung nach unten ziehst (nur bei Meldungen, die das unterstützen).
- Mehrere Meldungen nacheinander mit Zähler „+N", Meldungen derselben App werden zusammengefasst.
- **Bestätigungscodes** werden erkannt, ein Tipp kopiert den Code.
- Regeln pro App.

**Aufgeklappte Ansichten**
- **Musik:** Cover, Titel, Spulleiste mit ziehbarem Strich, Play/Pause, Vor/Zurück, Spulen-Tasten für Podcasts. Wischen links und rechts auf der Pille wechselt den Song.
- **Anruf:** Details, Stumm und Lautsprecher.
- **Laden:** erweiterte Ansicht mit Leistung, Temperatur und Restzeit.
- **Training:** Zeit und Werte wie Puls, Distanz und Tempo.

**Schnellzugriff**
- Tippen auf die Ruhe-Pille öffnet ein Panel mit Kacheln, mit mehreren Seiten zum Wischen.
- Kacheln frei wählbar, auch beliebige Apps.
- **Smart Home:** Home-Assistant-Geräte als Kacheln, Lampen mit Helligkeitsregler. Anmeldung direkt über Home Assistant, der Server wird automatisch gefunden.

**Animationen**
- Federn mit leichtem Nachschwingen, ruhiger bei großer Pille.
- Neue Inhalte poppen in der kleinen Pille auf, Symbole federn.
- Optional: große Bubble schrumpft zur Pille erst kleiner, dann kurz breiter (Squash).
- Rollende Ziffern bei Timern, animierte Ladeanzeige, pulsierendes Symbol bei klingelndem Anruf.
- Tasten drücken sich federnd ein, Kacheln werden beim Drücken weiß.

**Anpassen** (Tabs START, LAYOUT, FUNKTION, DESIGN, APPS)
- Position, Größe, Breite und Rundung, Anzeigedauer, Haptik.
- Themen, Akzentfarbe und eigene Farben, gut lesbar auch bei hellen Farben.
- Für fast jede Funktion gibt es einen Schalter.

**Akku**
- Sparmodus mit Stufen im START-Tab, ausgeschaltet im Hintergrund, wenn der Bildschirm aus ist.
- Optional ausblenden bei Vollbild (Video, Spiele) und im Querformat.

## So funktioniert es

- `IslandListenerService` ist ein `NotificationListenerService` und liest die Benachrichtigungen.
- Ein Bedienungshilfe-Dienst zeigt die Island als Overlay über der Statusleiste.
- Drei Fenster: ein sichtbares ohne Touch, ein kleines unsichtbares für Berührungen (leitet sie weiter) und eines für die Antwort-Eingabe. Auf dem Sperrbildschirm gibt es keinen Touch.
- Die Oberfläche ist komplett in Compose (`IslandUi.kt`), der Zustand läuft über `IslandBus`.
- Einstellungen liegen in SharedPreferences, Home-Assistant-Zugang in privatem App-Speicher.

## Selbst bauen

Der Workflow `.github/workflows/build.yml` baut bei jedem Push und veröffentlicht die APK unter **Releases → latest**.
Die Versionsnummer ist `1.<Lauf-Nummer>`.

Lokal: Projekt in Android Studio öffnen oder `gradle assembleDebug` ausführen.

## Bekannte Grenzen

- Auf manchen Herstellersystemen beendet das System Hintergrunddienste. Dann Akku-Optimierung für die App ausschalten und sie im Autostart erlauben.
- Stumm und Lautsprecher bei Anrufen können von Android blockiert werden.
- Zwischenablage lesen und Vollbild-Erkennung hängen vom Gerät ab. Der START-Tab zeigt Diagnose-Karten.
- Die echte Nothing-Schrift ist lizenziert und nicht enthalten, als Ersatz dient eine Monospace-Schrift.
- Home-Assistant-Anmeldung läuft über eine lokale Rückleitung auf dem Handy und wurde nur auf einem Gerät getestet.

# Nothing Island

Dynamic Island für Android im Nothing-OS-Look (schwarz, Monospace-Typo, roter Akzent, Punktmatrix-Ring).

## So funktioniert es
- `IslandListenerService` ist ein `NotificationListenerService` und hostet zugleich das Overlay-Fenster
  (`TYPE_APPLICATION_OVERLAY`, oben mittig). Das Fenster ist nur so groß wie die Pill, Touches daneben gehen an die App darunter.
- Neue Benachrichtigungen (nicht ongoing, keine Gruppen-Summary) klappen die Pill mit Spring-Animation auf,
  nach 5 s klappt sie wieder ein. Tippen öffnet die Benachrichtigung.
- `IslandUi.kt` enthält die Compose-UI inkl. animiertem Punktmatrix-Ring.

## Einrichten
1. Ordner in Android Studio (Koala oder neuer) öffnen, Gradle-Sync abwarten (der Wrapper wird von Android Studio erzeugt).
2. Auf einem Gerät mit Android 10+ starten.
3. In der App: **01** "Über anderen Apps anzeigen" und **02** "Zugriff auf Benachrichtigungen" aktivieren.
4. "TEST ANZEIGEN" tippen oder dir selbst eine Nachricht schicken.

## Anpassen
- Größe / Position: `IslandUi.kt` (Werte `348.dp`, `84.dp`) und `y` in `IslandListenerService.showOverlay()`.
- Akzentfarbe: `NothingRed` in `IslandUi.kt`.
- Anzeigedauer: `AUTO_DISMISS_MS`.
- Echte Nothing-Schrift (Ndot/NType) ist lizenziert und deshalb nicht enthalten, als Fallback dient `FontFamily.Monospace`.
  Eigene Fonts kannst du unter `res/font/` ablegen.

## Bekannte Grenzen
- Auf Android 14+ können Hintergrund-Starts per `PendingIntent` durch das System eingeschränkt sein.
- Manche Hersteller-ROMs beenden den Listener im Hintergrund, dann Akku-Optimierung für die App ausschalten.
- Medien-Steuerung, Timer und Anrufe sind noch nicht eingebaut, wären aber über `MediaSessionManager` und weitere `IslandEvent`-Typen ergänzbar.

## APK bauen
- **Android Studio:** Build > Build Bundle(s) / APK(s) > Build APK(s). Die Datei liegt danach in `app/build/outputs/apk/debug/app-debug.apk`.
- **Kommandozeile:** `gradle assembleDebug` (oder `./gradlew assembleDebug`, sobald Android Studio den Wrapper erzeugt hat).
- **GitHub:** Projekt in ein Repo pushen. Der Workflow `.github/workflows/build.yml` baut die APK und legt sie unter Actions > Artifacts ab.

Zum Installieren auf dem Handy "Apps aus unbekannten Quellen installieren" für den Browser oder Dateimanager erlauben.

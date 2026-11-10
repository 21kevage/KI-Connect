# NewsRadar für Android

NewsRadar liest RSS und Atom von Tagesschau, Deutschlandfunk und heise online.
Ähnliche Überschriften aus verschiedenen Quellen werden zu einer Meldungsgruppe
zusammengefasst. In der Meldungsansicht zeigt **HTML auswerten** Titel und Beschreibung
der Originalseite oder einen einleitenden Absatz. Die Auswertung läuft auf dem Handy.

## APK direkt installieren

1. Lade die Debug-APK auf ein Android-Gerät ab Version 8.
2. Öffne die Datei in der Downloads- oder Dateien-App. Wenn Android nachfragt, erlaube
   **Aus dieser Quelle zulassen** für die App, mit der du die APK geöffnet hast, und
   bestätige anschließend **Installieren**.
3. Öffne NewsRadar. Für die Bühnenprobe tippe auf **Testbenachrichtigung auslösen**
   und erlaube Benachrichtigungen. Tippe auf die Benachrichtigung, um die fiktive
   Meldung mit dem Quellenvergleich zu sehen.

Die App benötigt Internet für die echten Feeds und Webseiten. Die fiktiven
Demoberichte sind in der APK enthalten.

## Neue Meldungen im Hintergrund

Mit **Feed-Hinweise einschalten** prüft Android die Feeds ungefähr alle 30 Minuten.
Der erste erfolgreiche Abruf einer Quelle dient als Ausgangspunkt. Spätere neue
Meldungen ergeben jeweils eine lokale Sammelbenachrichtigung pro Abruf. Android
kann die Prüfung abhängig von Netzwerk und Energiesparmodus verschieben.

## Bühnendemo am Laptop

1. Installiere Android Platform Tools am Laptop. Aktiviere USB-Debugging am Gerät,
   verbinde es per USB, bestätige den Laptop und prüfe die Verbindung mit
   `adb devices`. Bei mehreren Geräten kannst du mit der Umgebungsvariable
   `ANDROID_SERIAL` eines auswählen.
2. Starte im Ordner `newsradar` den Befehl `python demo/server.py` und öffne
   [das lokale Dashboard](http://127.0.0.1:8787).
3. Wechsle auf dem Handy zum Startbildschirm. Klicke am Laptop auf **Demo über USB
   starten**. Auf dem Handy erscheint die Android-Benachrichtigung. Beim Antippen
   öffnet sich der Vergleich mit drei fiktiven Quellen und zwei Startterminen.

Der USB-Empfänger steckt ausschließlich in der Debug-APK. Auf dem Handy kannst du
die Demo auch direkt auslösen.

## APK selbst bauen

Öffne den Ordner `newsradar` in Android Studio. Benötigt werden JDK 17 oder neuer,
Android SDK 35 und Build Tools 35.0.0.

```bash
./gradlew :app:assembleDebug
```

Unter Windows verwendest du `gradlew.bat :app:assembleDebug`. Die APK findest du
unter `app/build/outputs/apk/debug/app-debug.apk`.

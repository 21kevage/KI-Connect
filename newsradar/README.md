# NewsRadar für Android

NewsRadar lädt Meldungen von Tagesschau, Deutschlandfunk und heise online. Die App
gruppiert Überschriften mit ähnlichen Stichwörtern über Quellengrenzen hinweg und zeigt
die Meldungen mit ihren jeweiligen Originalquellen. Die Bühnendemo verwendet drei
gekennzeichnete fiktive Berichte zum selben Projekt und vergleicht deren Starttermine.

## APK bauen

Öffne diesen Ordner als Projekt in Android Studio. Der Build benötigt JDK 17 oder neuer,
Android SDK 35 und Gradle 8.9.

```bash
./gradlew :app:assembleDebug
```

Unter Windows verwendest du `gradlew.bat :app:assembleDebug`. Die APK findest du unter
`app/build/outputs/apk/debug/app-debug.apk`. Sie ist für Android 8 und neuer gedacht.

## Bühnendemo mit USB

1. Installiere und öffne die Debug-APK auf dem Gerät. Tippe auf
   **Testbenachrichtigung auslösen** und erlaube Benachrichtigungen.
2. Installiere Android Platform Tools am Laptop. Aktiviere USB-Debugging am Gerät,
   bestätige den verbundenen Laptop und prüfe die Verbindung mit `adb devices`.
   Bei mehreren Geräten kannst du die Seriennummer über `ANDROID_SERIAL` wählen.
3. Starte im Projektordner `python demo/server.py` und öffne
   [das lokale Dashboard](http://127.0.0.1:8787).
4. Wechsle auf dem Handy zum Startbildschirm. Klicke am Laptop auf **Demo über USB
   starten**. Die Android-Benachrichtigung öffnet beim Antippen den Quellenvergleich.

Du kannst den Quellenvergleich oder die lokale Testbenachrichtigung auch direkt in der
App auslösen. Die fiktiven Berichte liegen in `shared/demo.json` und sind in der APK
enthalten. Der USB-Empfänger steckt ausschließlich in der Debug-APK.

## Push über das Internet mit Firebase

1. Öffne die [Firebase Console](https://console.firebase.google.com/). Erstelle ein
   Projekt oder wähle dein Projekt aus. Klicke auf **App hinzufügen** und wähle
   **Android**. Registriere die Paket-ID `com.ki_connect.newsradar`.
2. Lade `google-services.json` herunter und lege die Datei unter
   `newsradar/app/google-services.json` ab. Baue die APK neu. Die Gradle-Einrichtung
   für Firebase ist bereits im Projekt enthalten.
3. Aktiviere für dasselbe Projekt die
   [Firebase Cloud Messaging API](https://console.cloud.google.com/apis/library/fcm.googleapis.com).
   Melde dich am Laptop mit einem Google-Konto an, das in diesem Projekt Nachrichten
   über FCM senden darf. Mit installiertem Google Cloud CLI geht das in PowerShell
   im Ordner `newsradar` so:

   ```powershell
   gcloud auth application-default login
   $env:NEWSRADAR_FIREBASE_PROJECT = "deine-firebase-projekt-id"
   python -m pip install -r demo/requirements.txt
   python demo/server.py
   ```

   Wenn gcloud auf ein fehlendes Quota-Projekt hinweist, führe
   `gcloud auth application-default set-quota-project deine-firebase-projekt-id` aus.
4. Installiere und öffne die konfigurierte APK auf einem Gerät mit Google
   Play-Diensten. Erlaube Benachrichtigungen. Kopiere unter **Push-Einrichtung**
   den Gerätetoken und füge ihn im lokalen Dashboard unter **Internet-Push** ein.
   **Demo über Firebase starten** sendet an dieses Gerät. Tippe auf die
   Benachrichtigung, um den Quellenvergleich zu öffnen.

Lege Zugangsdaten außerhalb des Repositorys ab. Das Dashboard hört nur auf dem Laptop
und speichert den eingegebenen Gerätetoken nicht. [Firebase-Dokumentation](https://firebase.google.com/docs/cloud-messaging)

## Umfang des Prototyps

Die Live-Feeds werden beim Start und per Schaltfläche geladen. Die automatische
Gruppierung nutzt Überschriften. Die Aussage zum widersprüchlichen Starttermin entsteht
aus den strukturierten Demodaten. Die Push-Wege lösen gezielt die Bühnenmeldung aus.

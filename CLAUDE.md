# URL Sharer — project notes

## Building
JDK 17 + Android SDK required. `./gradlew assembleDebug`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Design constraints
- Keep it tiny/fast: pure Java + platform widgets. No AndroidX, Compose, or WebView.
- No Google Play. Sideload-only.
- `compileSdk`/`targetSdk` 35, `minSdk` 26.

# URL Sharer

URL Sharer is a tiny Android app that listens for URLs and lets you hand them
off to any browser or app on your device — instead of always using the default
browser.

When you click a link, or share text/a URL from another app, URL Sharer opens
with the URL in a large, editable text area. Tweak it if you like, then tap
**Open URL With** to pick whichever browser or app should handle it.

## Highlights

- **Tiny & fast** — pure Java + platform widgets, *no* AndroidX, Compose, or
  WebView. The debug APK is ~13 KB.
- **No dependencies** — nothing to download at runtime, nothing to bloat the app.
- **Latest Android** — `compileSdk`/`targetSdk` 35 (Android 15), `minSdk` 26
  (Android 8.0). No legacy-device baggage.
- **No Google Play required** — sideload the APK directly.

## How it works

The single `MainActivity` registers three intent filters:

| Intent | Trigger |
| --- | --- |
| `MAIN` / `LAUNCHER` | Opening the app from the launcher |
| `VIEW` (`http`/`https`, `BROWSABLE`) | Clicking a web link in another app |
| `SEND` (`text/plain`) | Sharing text/a URL from another app |

Tapping **Open URL With** builds an `ACTION_VIEW` intent for the (possibly
edited) URL and shows the system chooser. The app excludes itself from that
chooser to avoid redirect loops.

## Building locally

Requires JDK 17 and the Android SDK. With `ANDROID_HOME` set:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Building in CI

`.github/workflows/build.yml` builds the debug APK on every push/PR to `main`
(and via manual dispatch), then uploads it as the `urlsharer-debug` artifact.

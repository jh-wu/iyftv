# Developing iyfTV

A native Android TV / Google TV client for [iyf.tv](https://www.iyf.tv/), written in Kotlin with
Jetpack Compose for TV, Media3 (ExoPlayer) and Room.

## Build

Requires Android Studio (or the Android SDK with platform 35) and JDK 17+.

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug and release builds are both signed with `app/debug.keystore`, so every build installs over
the previous one and keeps the watch history.

## Releases

Every push to `main` runs CI (`.github/workflows/build.yml`) and publishes the release APK as
GitHub release `build-N` (N is the CI run number). The commit message becomes the release notes,
which the app shows in its update prompt. The app reads releases without signing in, so the
repository must stay public.

## Layout

```
app/src/main/java/com/iyftv/app/
  data/VideoSource.kt          interface the UI talks to
  data/iyf/                    iyf.tv client: config, request signing, JSON parsing, WebView helpers
  data/history/                watch history (Room), search history, remembered player per title
  data/update/                 update check against GitHub releases
  ui/                          Compose for TV screens, the Media3 player and the website player
```

## How the site integration works

The app calls the same JSON API as the iyf.tv web client. Each call is signed like the web
client's `uriSignature`: `vv = md5(publicKey & lowercase(query) & privateKey)`, with the keys read
from the `pConfig` block the homepage inlines. Titles whose video servers refuse the app's player
play in the website's own player inside a WebView (`WebPlayerActivity`), and the app remembers
which player worked for each title.

## Tests

- `./gradlew testDebugUnitTest` runs the parser and signer tests (fixtures are copies of live responses).
- `./gradlew connectedDebugAndroidTest` runs the device tests on an emulator or TV. Tests that need
  the live site are skipped when iyf.tv blocks the machine (it shows GitHub's runners a Cloudflare
  check), via `LiveSite.assume()`.
- `IYF_LIVE=1 ./gradlew testDebugUnitTest --tests '*LiveSiteTest*'` runs the client against the real site.
- CI's `probe-site` job runs the live test and `tools/probe_site.py`, which dumps raw API responses.

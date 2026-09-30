# iyfTV

A native Android TV / Google TV client for [iyf.tv](https://www.iyf.tv/), written in Kotlin with
Jetpack Compose for TV, Media3 (ExoPlayer) and Room.

## Features (v1)

- **Browse**: home screen rows per category (电影, 电视剧, 综艺, 动漫, 纪录片), with a paged grid behind "更多".
- **Search**: keyword search with a paged result grid.
- **Detail**: poster, synopsis, episode picker.
- **Playback**: full-screen HLS playback with D-pad controls; auto-advances to the next episode.
- **Watch history**: position is saved every 10 seconds and when leaving the player. The home
  screen shows a "继续观看" row, the detail page offers "继续播放", and "观看记录" lists everything.

## Install on Google TV

Every push to `main` publishes a signed APK to the latest GitHub release:
<https://github.com/jh-wu/iyftv/releases/latest/download/iyftv.apk>

1. On the TV: Settings → System → About → click **Android TV OS build** 7 times to enable developer options.
2. Get the APK onto the TV, either with the **Send files to TV** app (install it on both your phone
   and the TV, then send `iyftv.apk`), or with `adb install iyftv.apk` over the network.
3. Allow the app you used under Settings → Apps → Security & restrictions → **Unknown sources**, then install.

Later builds install over the old one and keep your watch history, because all builds share
the same signing key (`app/debug.keystore`).

### Updates

After the first install the app updates itself. On start it checks the latest GitHub release;
when its `build-N` tag is newer than the installed build it asks **更新** or **以后再说**. The
**检查更新** button on the home screen checks on demand. Updating downloads the APK and opens the
system installer; the first time, Android asks you to allow iyfTV to install unknown apps.
This needs the repo to stay public, since the app reads releases without signing in.

## Build

Requires Android Studio (or the Android SDK with platform 35) and JDK 17+.

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```
app/src/main/java/com/iyftv/app/
  data/VideoSource.kt          interface the UI talks to
  data/iyf/                    iyf.tv client: config, request signing, JSON parsing, WebView fallback
  data/history/                Room database for watch history
  ui/                          Compose for TV screens and the Media3 player activity
```

## How the site integration works

The app calls the same JSON API as the iyf.tv web client (`m10.iyf.tv`). Each call is signed
like the web client's `uriSignature`: `vv = md5(publicKey & lowercase(query) & privateKey)`,
with the keys read from the `pConfig` block the homepage inlines. If the play API ever fails,
playback falls back to loading the site's watch page in an off-screen WebView and taking the
`.m3u8` URL its player requests.

- `./gradlew testDebugUnitTest` runs the parser and signer tests (fixtures are copies of live responses).
- `IYF_LIVE=1 ./gradlew testDebugUnitTest --tests '*LiveSiteTest*'` runs the client against the real site.
- CI's `probe-site` job runs both the live test and `tools/probe_site.py`, which dumps raw API responses.

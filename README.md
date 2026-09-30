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

## Site integration status

The iyf.tv client in `data/iyf/` was written without live access to the site, so the endpoint
paths, query parameters, category ids and the `vv`/`pub` signing scheme in `IyfConfig.kt` and
`IyfSigner.kt` still need checking against the browser's network tab. The JSON parsers find
titles, episodes and stream URLs by the fields they carry rather than fixed paths, and playback
falls back to loading the site's own watch page in an off-screen WebView and grabbing the
`.m3u8` URL its player requests, so playback can work even if the play API differs.

Unit tests for the signer and parsers: `./gradlew testDebugUnitTest`.

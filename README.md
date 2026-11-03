# iyfTV

An app for watching [iyf.tv](https://www.iyf.tv/) (爱壹帆) on Android TV and Google TV, made for
the TV remote.

## What it does

- **Browse** 电影, 电视剧, 综艺, 动漫, 纪录片 and 短剧, sorted by 按上传时间 or 按评分, and narrowed
  by 地区, 语言 and 年份. Press OK on a tab to refresh it.
- **Search** by name. Your last 20 searches are kept.
- **Continue watching**: the first tab (the clock icon) lists what you are part way through.
  Hold OK on a title there to remove it, or to reset its player (see below).
- **Title page**: poster, score, synopsis and episodes, with 继续播放 to pick up where you left
  off and 从头播放 to start again.
- **Playback**: the next episode starts by itself. Press OK or any arrow to show the controls:
  the progress bar (Left/Right skips 10 seconds), previous and next episode, and play/pause.
- **Updates**: the app tells you when a new version is out. You can also check from the
  settings (gear) icon, which shows the version you have.

### Why some titles show the website's player

Some video servers refuse the app's own player. For those titles the app plays the video in the
website's player instead (cutting its ad short) and remembers this for next time. If a title later
works in the app's player again, hold OK on it in 继续观看 and choose 重置播放器 so the app
tries its own player first.

## Install

You only need to do this once. After that the app updates itself.

1. On the TV, open the Google Play Store and install **Downloader** (by AFTVnews).
2. Open Downloader and enter this address:
   `https://github.com/jh-wu/iyftv/releases/latest/download/iyftv.apk`
3. When Android asks, allow Downloader to install apps. You can also turn this on under
   Settings → Apps → Security & restrictions → **Unknown sources** → Downloader.
4. Choose **Install**, then **Open**. You can delete the downloaded file afterwards.

Another way is the **Send files to TV** app: install it on your phone and the TV, send
`iyftv.apk` from the address above, then open it on the TV and allow that app to install apps
in the same way.

## Updating

When a new version is out, the app asks **更新** or **以后再说** when it starts. To check yourself,
open the settings (gear) icon on the home screen and choose **检查更新**. The first time you
update, Android asks you to allow iyfTV to install apps. Updates keep your watch history.

## Notes

- iyfTV is an unofficial app and is not made by iyf.tv. Everything you watch comes from the
  iyf.tv website, so what is available depends on the site and your region.
- The app keeps no account. Your watch history stays on the TV.

For building the app yourself, see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

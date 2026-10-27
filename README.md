# Storm Feed

**The companion app that brings Google Discover to Storm Launcher.**

Install it next to Storm and swipe right on your first home page to see your Discover feed, the same feed the
Google app shows. Storm finds it on its own: turn on Discover in Storm's settings and you're done.

[**Download the latest release**](https://github.com/nirmalkumar-rs7/storm-home-feed/releases/latest)

---

## Why a separate app?

The Google app serves its Discover feed only to launchers it trusts: apps built into the phone, and apps built for
debugging. Storm from the Play Store is neither, so the Google app turns it away.

Storm Feed is a small app built for debugging on purpose. Storm hands its feed requests to Storm Feed, Storm Feed
passes them to the Google app under its own name, and the feed comes back to Storm's home screen. Other launchers'
feed companions work the same way.

```
 Storm ──requests──▶ Storm Feed ──same requests, under its own name──▶ Google app
   ▲                                                                       │
   └────────────────────────── Discover feed, drawn on Storm ──────────────┘
```

## Requirements

- Android 13 or later
- Storm Launcher, installed and set as your home app
- The Google app (`com.google.android.googlequicksearchbox`), signed in and up to date

## Install

1. On your phone, open the [latest release](https://github.com/nirmalkumar-rs7/storm-home-feed/releases/latest) and
   download the `.apk` under **Assets**.
   Storm links here too: **Settings › Feed › Download Storm Feed**.
2. Open the downloaded file. If Android asks, allow your browser to install unknown apps, then tap **Install**.
3. In Storm, turn on **Settings › Feed › Google Discover**.
4. Go home and swipe right on the first page.

Open Storm Feed from your app list at any time to check that the Google app is installed and that Storm is connected.

### Updating

Install a newer APK over the old one. Storm reconnects by itself.

### Verifying the download

Each release lists the APK's SHA-256 checksum. To check yours:

```bash
shasum -a 256 storm-feed-*.apk
```

## What it can and can't do

- **No permissions.** Storm Feed asks for none: no internet, no storage, no contacts, no location. It can't reach the
  network itself; the feed's content comes from the Google app.
- **Nothing collected.** It keeps no data, has no analytics and no ads, and it writes nothing to storage.
- **Storm only.** It answers only Storm's own apps (`com.storm.launcher` and `com.storm.launcher.debug`) and refuses
  any other app that tries to use it.
- **Why "debuggable"?** That's how the Google app recognises it as trusted. Debugging needs a computer connected by
  USB with developer options turned on, so it doesn't open the app to anything else on the phone.

## Troubleshooting

| What you see | What to try |
| --- | --- |
| Swiping right does nothing | Turn on **Settings › Feed › Google Discover** in Storm, and check that Storm is your home app. |
| Storm says "Needs the Storm Feed app" | Storm Feed isn't installed. Install it from the latest release. |
| Storm Feed says the Google app isn't installed | Install or turn on the Google app from the Play Store. |
| The feed is blank or stops after a while | Open the Google app once, then go back home. Updating the Google app fixes most of these. |
| It stopped after reinstalling Storm Feed | Go home and swipe again: Storm reconnects within a couple of seconds. |

## Building from source

You need JDK 17 or later and the Android SDK (compile SDK 37).

```bash
git clone https://github.com/nirmalkumar-rs7/storm-home-feed.git
cd storm-home-feed
./gradlew :feed:assembleRelease
```

The APK is written to `feed/build/outputs/apk/release/`. Release builds are shrunk by R8, and they stay debuggable
because the Google app needs that.

An APK you build yourself is signed with your own key. Android won't install it over a downloaded release (or the
other way round) until you uninstall the old one.

## How it works

The whole app is two classes, built on the Android framework alone, with no libraries.

- **`FeedBridgeService`** is the bridge Storm connects to. It speaks the protocol the Google app uses for launcher
  feeds (`ILauncherOverlay`) and passes each of Storm's calls to the Google app as its own. Along the way it names
  Storm Feed as the window's owner, so the Google app moves the feed for it. It also remembers Storm's window and
  state, so it can hand them over again when the Google app's connection is new. The Google app's replies go straight
  to Storm.
- **`StatusActivity`** is the screen you see when you open Storm Feed. It shows whether Storm and the Google app are
  installed and whether Storm is connected.

## Feedback

Found a bug or have an idea? [Open an issue](https://github.com/nirmalkumar-rs7/storm-home-feed/issues). Please
include your phone's model, its Android version and the Google app's version.

---

Google, Google Discover and the Google app are trademarks of Google LLC. Storm Feed isn't made by Google or affiliated
with it.

# Storm Feed

Companion app that lets the release build of Storm Launcher show Google Discover. The Google app serves its feed
only to system or debuggable apps, so this small relay is always built debuggable and connects to the Google app in
Storm's place.

## Build

```bash
./gradlew :feed:assembleRelease
```

Install `feed/build/outputs/apk/release/*.apk`, then turn on Settings > Home screen > Google Discover in Storm.

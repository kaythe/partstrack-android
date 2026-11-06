# partstrack for Android

![Build](https://github.com/kaythe/partstrack-android/actions/workflows/build.yml/badge.svg)

An Android app to look up and change a [partstrack](https://github.com/kaythe/partstrack)
inventory from a phone, through the JSON API of `partstrack-web`: scan a
label, see what is in a drawer, take three parts out, add a photo of a new
part.

**Status:** in development; the app doesn't do anything useful yet. See
[PLAN.md](PLAN.md) for the design and the steps planned.

## Requirements

- Android 8.0 (API 26) or later.
- A `partstrack-web` server that the phone can reach (on the local network or
  through a VPN), from a partstrack release that has `/api/token` (the one
  after 1.3.0). Reading needs nothing else; changes need an API token,
  created on the server with `partstrack token add NAME`.

## Building

You need JDK 17 or later and the Android SDK (Android Studio brings both).
Open the folder in Android Studio, or build from the command line:

```sh
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device or running emulator
./gradlew build                # all variants, JVM tests and lint, as in CI
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Without Android Studio,
tell Gradle where the SDK is with `ANDROID_HOME` or a `local.properties` file
containing `sdk.dir=/path/to/sdk`.

### Testing against a server

Never test against your real inventory. Start a scratch server on the
development machine:

```sh
partstrack-web --db /tmp/scratch/parts.db --port 8080
```

and use `http://10.0.2.2:8080/` in the emulator (the emulator's name for the
host's `localhost`). A server in WSL 2 works the same way, because Windows
forwards `localhost` to it.

## License

Copyright (C) 2026 Kayla Theil. partstrack for Android is free software,
licensed under the GNU General Public License, version 3 or later; see
[LICENSE](LICENSE).

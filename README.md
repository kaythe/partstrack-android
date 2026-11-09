# partstrack for Android

![Build](https://github.com/kaythe/partstrack-android/actions/workflows/build.yml/badge.svg)

An Android app to look up and change a [partstrack](https://github.com/kaythe/partstrack)
inventory from a phone, through the JSON API of `partstrack-web`: scan a
label, see what is in a drawer, take three parts out, add a photo of a new
part.

See [CHANGELOG.md](CHANGELOG.md) for what it does and [PLAN.md](PLAN.md) for
the design. The APK is attached to each
[release](https://github.com/kaythe/partstrack-android/releases); to install
it, allow installing apps from the browser or file manager you open it with.

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

### Release builds

`./gradlew assembleRelease` builds the release APK, shrunk with R8, in
`app/build/outputs/apk/release/`. It is signed when the build finds the
release key, from a `keystore.properties` file in the project's folder:

```properties
storeFile=/path/outside/the/repository/partstrack-release.jks
storePassword=...
keyAlias=partstrack
keyPassword=...
```

or from the environment variables `PARTSTRACK_KEYSTORE` and
`PARTSTRACK_KEYSTORE_PASSWORD` (and `PARTSTRACK_KEY_ALIAS` and
`PARTSTRACK_KEY_PASSWORD` if they differ from `partstrack` and the keystore's
password). Without either, the APK is built unsigned (`app-release-unsigned.apk`),
as in CI. Neither the key nor `keystore.properties` ever goes into the
repository, and the key must be kept safe: updates of the app have to be
signed with the same key.

### Making a release

1. Set `versionName` in `app/build.gradle.kts` (the `versionCode` follows from
   it: X.Y.Z gives X * 10000 + Y * 100 + Z), move the changes in `CHANGELOG.md` under a `## [X.Y.Z] - date` heading,
   and update [docs/SBOM.md](docs/SBOM.md) if dependencies changed.
2. Tag the commit and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. The [Release workflow](.github/workflows/release.yml) checks that the tag
   matches `versionName` and that the changelog has the version, builds and
   tests the signed APK, checks that it is signed with the release key, and
   creates a draft GitHub release with `partstrack-X.Y.Z.apk`, its SHA-256,
   and the changelog section as notes.
4. Review the draft on GitHub and publish it.

A failed run can be started again from the Actions tab ("Run workflow", with
the tag); if the draft exists already, its files are replaced and its notes
kept.

The workflow needs two repository secrets (Settings → Secrets and variables →
Actions): the keystore, base64-encoded, and its password. With the GitHub CLI,
from the project's folder in Git Bash:

```sh
base64 -w0 ~/.android-keys/partstrack-release.jks | gh secret set PARTSTRACK_KEYSTORE_BASE64
sed -n 's/^storePassword=//p' keystore.properties | tr -d '\r\n' | gh secret set PARTSTRACK_KEYSTORE_PASSWORD
```

The workflow knows the release certificate's fingerprint and refuses an APK
signed with another key; change `CERT_SHA256` there if the key ever changes.

### Testing against a server

Never test against your real inventory. Start a scratch server on the
development machine:

```sh
partstrack-web --db /tmp/scratch/parts.db --port 8080
```

and use `http://10.0.2.2:8080/` in the emulator (the emulator's name for the
host's `localhost`). A server in WSL 2 works the same way, because Windows
forwards `localhost` to it.

The API client's tests answer with what a real `partstrack-web` answered,
stored in `app/src/test/resources/api/`. When the API changes, capture them
again with `python scripts/capture_fixtures.py [PARTSTRACK_CHECKOUT]` (default
`../partstrack`, which needs Flask and waitress); it runs its own scratch
server. `LiveServerTest` also sends every kind of request to a real server,
but only when `PARTSTRACK_TEST_SERVER` (e.g. `http://127.0.0.1:8765/`) and
`PARTSTRACK_TEST_TOKEN` are set; it adds and deletes parts and locations, so
use a scratch database for it too.

## License

Copyright (C) 2026 Kayla Theil. partstrack for Android is free software,
licensed under the GNU General Public License, version 3 or later; see
[LICENSE](LICENSE).

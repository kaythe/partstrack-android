# Software bill of materials

What the release build of partstrack for Android is made of, with the
licenses, and whether anything stands in the way of releasing it under
GPL-3.0-or-later.

- **Covers:** the release APK of version 1.0.0 (2026-11-09; the dependencies
  are the same as when this list was first made, at `6351821`): every component on the `releaseRuntimeClasspath`
  (what Gradle packs into the APK), plus files in the APK that don't come
  from a Maven component. Build tools and test libraries, which are not
  distributed, are listed separately at the end.
- **Made from:** the resolved dependency tree
  (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`),
  the license each component declares in its Maven POM (following parent
  POMs), and the contents of `app-release-unsigned.apk`. When a dependency
  changes, these steps have to be repeated; the version catalog alone
  doesn't show the transitive components.

## Summary

- **179 components** in the release classpath: 82 Android libraries (AAR), 22 Java libraries (JAR) and 75 metadata modules without code (Kotlin Multiplatform umbrella modules, whose code is in their `-android`/`-jvm` variants, and BOMs).
- **Licenses:** Apache-2.0 (178), BSD-3-Clause (1); and one data file under MPL-2.0 (the Public Suffix List, see below).
- **Nothing proprietary:** no Google Play Services, Firebase or ML Kit. QR codes are read with ZXing, which was chosen for that reason.
- **The app's own code** is GPL-3.0-or-later; all 80 source files carry the notice.

## Releasing under GPL-3.0

**Nothing prevents it.** Every component's license is compatible with
GPL-3.0, so the APK can be released under GPL-3.0-or-later. The notices
the licenses ask for are in the app, under Settings → "About and
open-source licenses" (see "Notices in the app").

| License | Components | Compatible with GPL-3.0? | What it asks for |
|---|---|---|---|
| Apache-2.0 | all but one (incl. AndroidX, Compose, Kotlin, OkHttp, Okio, Coil, ZXing) | Yes: Apache-2.0 code may be used in GPL-3.0 programs (not in GPL-2.0-only ones; the app's "or later" includes 3.0). | A copy of the Apache license for recipients (§4a), keeping notices (§4c), passing on NOTICE files (§4d). None of the components ships a NOTICE file. |
| BSD-3-Clause | `androidx.datastore:datastore-preferences-external-protobuf` (a copy of Protocol Buffers inside DataStore) | Yes. | Its copyright notice and license text in the documentation that comes with the binary. |
| MPL-2.0 | `assets/PublicSuffixDatabase.list`, Mozilla's Public Suffix List, which OkHttp ships (pre-processed, without its license header) | Yes: the list doesn't carry the "Incompatible With Secondary Licenses" notice, so MPL-2.0 §3.3 allows combining it with a GPL program. | Telling recipients of the app where the list's source is (MPL-2.0 §3.2): https://publicsuffix.org/list/ |

Other things checked:

- **Native code:** the APK contains `libandroidx.graphics.path.so` and
  `libdatastore_shared_counter.so` (for four CPU types). They come from the
  AndroidX components `graphics-path` and `datastore`, under Apache-2.0.
- **Icons and fonts:** the launcher icon is partstrack's logo (same author,
  GPL). `ui/common/Icons.kt` redraws the Material "Remove" icon (Material
  Icons are Apache-2.0); the QR viewfinder icon in `ui/scan/QrScanner.kt`
  is the app's own. The app bundles no fonts.
- **Android itself** (the SDK's `android.jar`) isn't part of the APK; the
  phone provides it.
- **Not distributed, so without effect on the APK:** JUnit 4 is under
  EPL-1.0, which is *not* compatible with the GPL; it is only used to run
  the tests, never shipped, so that doesn't matter. The Gradle wrapper in
  the repository is Apache-2.0, which is fine in a GPL project's sources.
- **GPL-3.0 itself:** with each APK, the corresponding source has to be
  offered (§6): a GitHub release for the tag, with GitHub's source
  archive, does that. GPL-3.0's "installation information" rule doesn't
  call for the signing key: anyone can build and install their own
  version (after uninstalling the official one).

### Notices in the app

1. **Third-party notices.** Settings → "About and open-source licenses"
   (`ui/about/AboutScreen.kt`) lists the components, grouped by project,
   with their licenses, and shows the texts from `res/raw/`: Apache-2.0,
   the Protocol Buffers BSD-3-Clause notice (with its copyright line,
   which DataStore's copy leaves out), MPL-2.0 with where to get the
   Public Suffix List, and the app's own GPL-3.0. When components change,
   the list there has to follow.
2. **For F-Droid** (not a license matter): the Android Gradle Plugin's
   encrypted block of dependency information is turned off
   (`dependenciesInfo` in `app/build.gradle.kts`).
3. **Repeat this check** when dependencies change. R8 removes unused code
   from the release build, but doesn't change which components are
   included or what their licenses ask for.

## Direct dependencies

| Component | Version | License | Used for |
|---|---|---|---|
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.21 | Apache-2.0 | Kotlin standard library |
| `androidx.core:core-ktx` | 1.19.1 | Apache-2.0 | Android platform helpers |
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | The activity hosting Compose |
| `androidx.compose.ui:ui` | 1.12.1 | Apache-2.0 | Jetpack Compose UI (versions from compose-bom 2026.09.00) |
| `androidx.compose.ui:ui-tooling-preview` | 1.12.1 | Apache-2.0 | Compose previews (annotations only) |
| `androidx.compose.material3:material3` | 1.4.0 | Apache-2.0 | Material 3 components |
| `androidx.compose.material:material-icons-core` | 1.7.8 | Apache-2.0 | Material icons |
| `androidx.navigation:navigation-compose` | 2.10.2 | Apache-2.0 | Navigation between screens |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 | ViewModels |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Apache-2.0 | Lifecycle-aware state |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Apache-2.0 | Settings storage |
| `io.coil-kt.coil3:coil-compose` | 3.6.3 | Apache-2.0 | Loading pictures |
| `io.coil-kt.coil3:coil-network-okhttp` | 3.6.3 | Apache-2.0 | Coil over the app's OkHttp client |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | Apache-2.0 | QR code scanner |
| `com.google.zxing:core` | 3.5.4 | Apache-2.0 | QR code decoding |
| `com.squareup.okhttp3:okhttp` | 5.5.1 | Apache-2.0 | HTTP client |
| `com.squareup.okhttp3:okhttp-coroutines` | 5.5.1 | Apache-2.0 | Cancellable calls from coroutines |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | Apache-2.0 | JSON for the API |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 | Apache-2.0 | Coroutines |

## All components in the release APK

179 components, sorted by group. "Metadata" modules contain no code.

| Group | Artifact | Version | License | Type |
|---|---|---|---|---|
| `androidx.activity` | `activity` | 1.13.0 | Apache-2.0 | AAR |
| `androidx.activity` | `activity-compose` | 1.13.0 | Apache-2.0 | AAR |
| `androidx.activity` | `activity-ktx` | 1.13.0 | Apache-2.0 | AAR |
| `androidx.annotation` | `annotation` | 1.10.0 | Apache-2.0 | metadata |
| `androidx.annotation` | `annotation-experimental` | 1.4.1 | Apache-2.0 | AAR |
| `androidx.annotation` | `annotation-jvm` | 1.10.0 | Apache-2.0 | JAR |
| `androidx.appcompat` | `appcompat-resources` | 1.7.1 | Apache-2.0 | metadata |
| `androidx.arch.core` | `core-common` | 2.2.0 | Apache-2.0 | JAR |
| `androidx.arch.core` | `core-runtime` | 2.2.0 | Apache-2.0 | AAR |
| `androidx.autofill` | `autofill` | 1.0.0 | Apache-2.0 | AAR |
| `androidx.collection` | `collection` | 1.6.0 | Apache-2.0 | metadata |
| `androidx.collection` | `collection-jvm` | 1.6.0 | Apache-2.0 | JAR |
| `androidx.collection` | `collection-ktx` | 1.6.0 | Apache-2.0 | JAR |
| `androidx.compose` | `compose-bom` | 2026.09.00 | Apache-2.0 | metadata |
| `androidx.compose.animation` | `animation` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.animation` | `animation-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.animation` | `animation-core` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.animation` | `animation-core-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.foundation` | `foundation` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.foundation` | `foundation-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.foundation` | `foundation-layout` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.foundation` | `foundation-layout-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.material` | `material-icons-core` | 1.7.8 | Apache-2.0 | metadata |
| `androidx.compose.material` | `material-icons-core-android` | 1.7.8 | Apache-2.0 | AAR |
| `androidx.compose.material` | `material-ripple` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.material` | `material-ripple-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.material3` | `material3` | 1.4.0 | Apache-2.0 | metadata |
| `androidx.compose.material3` | `material3-android` | 1.4.0 | Apache-2.0 | AAR |
| `androidx.compose.runtime` | `runtime` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.runtime` | `runtime-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.runtime` | `runtime-annotation` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.runtime` | `runtime-annotation-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.runtime` | `runtime-retain` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.runtime` | `runtime-retain-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.runtime` | `runtime-saveable` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.runtime` | `runtime-saveable-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui-geometry` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-geometry-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui-graphics` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-graphics-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui-text` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-text-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui-tooling-preview` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-tooling-preview-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui-unit` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-unit-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.compose.ui` | `ui-util` | 1.12.1 | Apache-2.0 | metadata |
| `androidx.compose.ui` | `ui-util-android` | 1.12.1 | Apache-2.0 | AAR |
| `androidx.concurrent` | `concurrent-futures` | 1.1.0 | Apache-2.0 | JAR |
| `androidx.core` | `core` | 1.19.1 | Apache-2.0 | AAR |
| `androidx.core` | `core-ktx` | 1.19.1 | Apache-2.0 | AAR |
| `androidx.core` | `core-viewtree` | 1.0.0 | Apache-2.0 | AAR |
| `androidx.customview` | `customview-poolingcontainer` | 1.0.0 | Apache-2.0 | AAR |
| `androidx.datastore` | `datastore` | 1.3.0 | Apache-2.0 | metadata |
| `androidx.datastore` | `datastore-android` | 1.3.0 | Apache-2.0 | AAR |
| `androidx.datastore` | `datastore-core` | 1.3.0 | Apache-2.0 | metadata |
| `androidx.datastore` | `datastore-core-android` | 1.3.0 | Apache-2.0 | AAR |
| `androidx.datastore` | `datastore-core-okio` | 1.3.0 | Apache-2.0 | metadata |
| `androidx.datastore` | `datastore-core-okio-jvm` | 1.3.0 | Apache-2.0 | JAR |
| `androidx.datastore` | `datastore-preferences` | 1.3.0 | Apache-2.0 | metadata |
| `androidx.datastore` | `datastore-preferences-android` | 1.3.0 | Apache-2.0 | AAR |
| `androidx.datastore` | `datastore-preferences-core` | 1.3.0 | Apache-2.0 | metadata |
| `androidx.datastore` | `datastore-preferences-core-android` | 1.3.0 | Apache-2.0 | AAR |
| `androidx.datastore` | `datastore-preferences-external-protobuf` | 1.3.0 | BSD-3-Clause | JAR |
| `androidx.datastore` | `datastore-preferences-proto` | 1.3.0 | Apache-2.0 | JAR |
| `androidx.emoji2` | `emoji2` | 1.4.0 | Apache-2.0 | AAR |
| `androidx.exifinterface` | `exifinterface` | 1.4.2 | Apache-2.0 | AAR |
| `androidx.graphics` | `graphics-path` | 1.0.1 | Apache-2.0 | AAR |
| `androidx.interpolator` | `interpolator` | 1.0.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-common` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-common-java8` | 2.11.0 | Apache-2.0 | JAR |
| `androidx.lifecycle` | `lifecycle-common-jvm` | 2.11.0 | Apache-2.0 | JAR |
| `androidx.lifecycle` | `lifecycle-livedata-core` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-process` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-runtime` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-runtime-android` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-runtime-compose` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-runtime-compose-android` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-runtime-ktx` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-runtime-ktx-android` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-viewmodel` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-viewmodel-android` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-viewmodel-compose-android` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-viewmodel-ktx` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.lifecycle` | `lifecycle-viewmodel-savedstate` | 2.11.0 | Apache-2.0 | metadata |
| `androidx.lifecycle` | `lifecycle-viewmodel-savedstate-android` | 2.11.0 | Apache-2.0 | AAR |
| `androidx.navigation` | `navigation-common` | 2.10.2 | Apache-2.0 | metadata |
| `androidx.navigation` | `navigation-common-android` | 2.10.2 | Apache-2.0 | AAR |
| `androidx.navigation` | `navigation-compose` | 2.10.2 | Apache-2.0 | metadata |
| `androidx.navigation` | `navigation-compose-android` | 2.10.2 | Apache-2.0 | AAR |
| `androidx.navigation` | `navigation-runtime` | 2.10.2 | Apache-2.0 | metadata |
| `androidx.navigation` | `navigation-runtime-android` | 2.10.2 | Apache-2.0 | AAR |
| `androidx.navigationevent` | `navigationevent` | 1.1.2 | Apache-2.0 | metadata |
| `androidx.navigationevent` | `navigationevent-android` | 1.1.2 | Apache-2.0 | AAR |
| `androidx.navigationevent` | `navigationevent-compose` | 1.1.2 | Apache-2.0 | metadata |
| `androidx.navigationevent` | `navigationevent-compose-android` | 1.1.2 | Apache-2.0 | AAR |
| `androidx.profileinstaller` | `profileinstaller` | 1.4.1 | Apache-2.0 | AAR |
| `androidx.savedstate` | `savedstate` | 1.5.0 | Apache-2.0 | metadata |
| `androidx.savedstate` | `savedstate-android` | 1.5.0 | Apache-2.0 | AAR |
| `androidx.savedstate` | `savedstate-compose` | 1.5.0 | Apache-2.0 | metadata |
| `androidx.savedstate` | `savedstate-compose-android` | 1.5.0 | Apache-2.0 | AAR |
| `androidx.savedstate` | `savedstate-ktx` | 1.5.0 | Apache-2.0 | AAR |
| `androidx.startup` | `startup-runtime` | 1.2.0 | Apache-2.0 | AAR |
| `androidx.tracing` | `tracing` | 2.0.1 | Apache-2.0 | metadata |
| `androidx.tracing` | `tracing-android` | 2.0.1 | Apache-2.0 | AAR |
| `androidx.vectordrawable` | `vectordrawable` | 1.1.0 | Apache-2.0 | AAR |
| `androidx.vectordrawable` | `vectordrawable-animated` | 1.1.0 | Apache-2.0 | AAR |
| `androidx.versionedparcelable` | `versionedparcelable` | 1.1.1 | Apache-2.0 | AAR |
| `androidx.window` | `window` | 1.5.0 | Apache-2.0 | AAR |
| `androidx.window` | `window-core` | 1.5.0 | Apache-2.0 | metadata |
| `androidx.window` | `window-core-android` | 1.5.0 | Apache-2.0 | AAR |
| `com.google.accompanist` | `accompanist-drawablepainter` | 0.37.3 | Apache-2.0 | AAR |
| `com.google.guava` | `listenablefuture` | 1.0 | Apache-2.0 | JAR |
| `com.google.zxing` | `core` | 3.5.4 | Apache-2.0 | JAR |
| `com.journeyapps` | `zxing-android-embedded` | 4.3.0 | Apache-2.0 | AAR |
| `com.squareup.okhttp3` | `okhttp` | 5.5.1 | Apache-2.0 | metadata |
| `com.squareup.okhttp3` | `okhttp-android` | 5.5.1 | Apache-2.0 | AAR |
| `com.squareup.okhttp3` | `okhttp-coroutines` | 5.5.1 | Apache-2.0 | JAR |
| `com.squareup.okio` | `okio` | 3.18.1 | Apache-2.0 | metadata |
| `com.squareup.okio` | `okio-jvm` | 3.18.1 | Apache-2.0 | JAR |
| `io.coil-kt.coil3` | `coil` | 3.6.3 | Apache-2.0 | metadata |
| `io.coil-kt.coil3` | `coil-android` | 3.6.3 | Apache-2.0 | AAR |
| `io.coil-kt.coil3` | `coil-compose` | 3.6.3 | Apache-2.0 | metadata |
| `io.coil-kt.coil3` | `coil-compose-android` | 3.6.3 | Apache-2.0 | AAR |
| `io.coil-kt.coil3` | `coil-compose-core` | 3.6.3 | Apache-2.0 | metadata |
| `io.coil-kt.coil3` | `coil-compose-core-android` | 3.6.3 | Apache-2.0 | AAR |
| `io.coil-kt.coil3` | `coil-core` | 3.6.3 | Apache-2.0 | metadata |
| `io.coil-kt.coil3` | `coil-core-android` | 3.6.3 | Apache-2.0 | AAR |
| `io.coil-kt.coil3` | `coil-network-core` | 3.6.3 | Apache-2.0 | metadata |
| `io.coil-kt.coil3` | `coil-network-core-android` | 3.6.3 | Apache-2.0 | AAR |
| `io.coil-kt.coil3` | `coil-network-okhttp` | 3.6.3 | Apache-2.0 | metadata |
| `io.coil-kt.coil3` | `coil-network-okhttp-android` | 3.6.3 | Apache-2.0 | AAR |
| `org.jetbrains` | `annotations` | 23.0.0 | Apache-2.0 | JAR |
| `org.jetbrains.androidx.lifecycle` | `lifecycle-common` | 2.9.6 | Apache-2.0 | metadata |
| `org.jetbrains.androidx.lifecycle` | `lifecycle-runtime` | 2.9.6 | Apache-2.0 | metadata |
| `org.jetbrains.androidx.lifecycle` | `lifecycle-runtime-compose` | 2.9.6 | Apache-2.0 | metadata |
| `org.jetbrains.androidx.savedstate` | `savedstate` | 1.3.6 | Apache-2.0 | metadata |
| `org.jetbrains.androidx.savedstate` | `savedstate-compose` | 1.3.6 | Apache-2.0 | metadata |
| `org.jetbrains.compose.animation` | `animation` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.animation` | `animation-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.animation` | `animation-core` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.animation` | `animation-core-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.foundation` | `foundation` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.foundation` | `foundation-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.foundation` | `foundation-layout` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.foundation` | `foundation-layout-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.runtime` | `runtime` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.runtime` | `runtime-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.runtime` | `runtime-saveable` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.runtime` | `runtime-saveable-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.ui` | `ui` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.ui` | `ui-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.ui` | `ui-geometry` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.ui` | `ui-geometry-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.ui` | `ui-graphics` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.ui` | `ui-graphics-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.ui` | `ui-text` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.ui` | `ui-text-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.ui` | `ui-unit` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.ui` | `ui-unit-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.compose.ui` | `ui-util` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.compose.ui` | `ui-util-android` | 1.12.0 | Apache-2.0 | AAR |
| `org.jetbrains.kotlin` | `kotlin-stdlib` | 2.4.21 | Apache-2.0 | JAR |
| `org.jetbrains.kotlin` | `kotlin-stdlib-common` | 2.4.21 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `atomicfu` | 0.28.0 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `atomicfu-jvm` | 0.28.0 | Apache-2.0 | JAR |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | JAR |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-bom` | 1.11.0 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-core` | 1.11.0 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-core-jvm` | 1.11.0 | Apache-2.0 | JAR |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-bom` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-core` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-core-jvm` | 1.12.0 | Apache-2.0 | JAR |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-json` | 1.12.0 | Apache-2.0 | metadata |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-json-jvm` | 1.12.0 | Apache-2.0 | JAR |
| `org.jspecify` | `jspecify` | 1.0.0 | Apache-2.0 | JAR |

## Files in the APK that aren't Maven components

| File | From | License |
|---|---|---|
| `assets/PublicSuffixDatabase.list` | Mozilla's Public Suffix List, via `com.squareup.okhttp3:okhttp-android` | MPL-2.0 |
| `lib/*/libandroidx.graphics.path.so` | `androidx.graphics:graphics-path` | Apache-2.0 |
| `lib/*/libdatastore_shared_counter.so` | `androidx.datastore:datastore-core-android` | Apache-2.0 |
| `assets/dexopt/baseline.prof(m)` | generated by the build from the libraries' baseline profiles | (build output) |
| `DebugProbesKt.bin` | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | Apache-2.0 |
| `res/`, `classes*.dex`, `AndroidManifest.xml`, `resources.arsc` | the app and the libraries above | as above |

## Not distributed: tests and build

Used to build or test the app, never part of the APK.

| Component | Version | License | Used for |
|---|---|---|---|
| Android Gradle Plugin (`com.android.tools.build:gradle`) | 9.5.1 | Apache-2.0 | Building |
| Kotlin compiler and Gradle plugins (`org.jetbrains.kotlin`) | 2.4.21 | Apache-2.0 | Building |
| Gradle (wrapper in the repository) | 9.8.2 | Apache-2.0 | Building |
| `androidx.compose.ui:ui-test` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-test-android` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-test-junit4` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-test-junit4-android` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-test-manifest` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-tooling` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-tooling-android` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-tooling-data` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.compose.ui:ui-tooling-data-android` | 1.12.1 | Apache-2.0 | Tests |
| `androidx.concurrent:concurrent-futures-ktx` | 1.2.0 | Apache-2.0 | Tests |
| `androidx.test:core` | 1.7.0 | Apache-2.0 | Tests |
| `androidx.test:monitor` | 1.8.0 | Apache-2.0 | Tests |
| `androidx.test:runner` | 1.7.0 | Apache-2.0 | Tests |
| `androidx.test.espresso:espresso-core` | 3.7.0 | Apache-2.0 | Tests |
| `androidx.test.espresso:espresso-idling-resource` | 3.7.0 | Apache-2.0 | Tests |
| `androidx.test.ext:junit` | 1.3.0 | Apache-2.0 | Tests |
| `androidx.test.services:storage` | 1.6.0 | Apache-2.0 | Tests |
| `com.google.code.findbugs:jsr305` | 3.0.2 | Apache-2.0 | Tests |
| `com.google.errorprone:error_prone_annotations` | 2.30.0 | Apache-2.0 | Tests |
| `com.squareup.okhttp3:mockwebserver3` | 5.5.1 | Apache-2.0 | Tests |
| `javax.inject:javax.inject` | 1 | Apache-2.0 | Tests |
| `junit:junit` | 4.13.2 | EPL-1.0 | Tests |
| `org.hamcrest:hamcrest-core` | 1.3 | BSD-3-Clause | Tests |
| `org.hamcrest:hamcrest-library` | 1.3 | BSD-3-Clause | Tests |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Apache-2.0 | Tests |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm` | 1.11.0 | Apache-2.0 | Tests |

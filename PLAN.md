# partstrack for Android: design and implementation plan

A small Android app to look up and change a [partstrack](https://github.com/kaythe/partstrack)
inventory from a phone, through the JSON API of `partstrack-web` (the first
release with `/api/token`, i.e. the one after 1.3.0). It is meant for the shop floor: scan a label, see what is in a drawer,
take three parts out, add a photo of a new part.

Status: plan, nothing implemented yet. Repository:
<https://github.com/kaythe/partstrack-android>. Development happens on
Windows with Android Studio and the Android SDK; partstrack itself (and a
scratch `partstrack-web` for testing) can run in WSL or on Windows.

## Goals and non-goals

**Goals (version 1)**

- Browse and search parts (including attribute filters like `resistance>=10k`),
  filter by tag, category, location and low stock.
- Show a part: fields, stock per location, tags, attributes, pictures and
  datasheets, recent history.
- Change stock quickly: take, add, set, move, with an optional note.
- Add, edit and delete parts; add, edit and delete locations.
- Scan the QR codes on partstrack labels to open a part or location.
- Take a photo (or pick a file) and attach it to a part; add a link.
- Work against one server on the local network or through the VPN.

**Non-goals (version 1)**

- Offline use or local caching beyond the screen being shown. Without the
  server the app shows an error and a retry button. (The read-only snapshot
  at `/api/snapshot` could back an offline mode later.)
- Several servers, accounts, or a login for the web pages (decided against:
  access is controlled by the VPN and API tokens).
- Bulk changes, CSV import/export, label printing. These stay in the web
  interface and the desktop app.
- Publishing on Google Play. The APK is installed directly (or via F-Droid
  later).

## The server side, as the app sees it

Everything the app needs exists in partstrack 1.3.0, except the token check,
which comes with the next release:

| Need | API |
|---|---|
| Check the server and its version | `GET /api` → `version`, endpoint list |
| Check a token | `GET /api/token` (with the token) → `name`, `read_only`; 401 if not valid |
| Parts list and search | `GET /api/parts?q=&tag=&location=&category=&low=` |
| One part | `GET /api/parts/<id>` |
| Locations (tree order, with `parent` path) | `GET /api/locations`, `GET /api/locations/<id>` |
| Tag and category suggestions | `GET /api/tags`, `GET /api/categories` |
| History | `GET /api/history?part=<id>&limit=` |
| Add / change / delete part | `POST /api/parts`, `PATCH /api/parts/<id>`, `DELETE /api/parts/<id>` |
| Stock | `POST /api/parts/<id>/stock` (`quantity` or `delta`, `note`), `POST /api/parts/<id>/move` |
| Locations | `POST /api/locations`, `PATCH`/`DELETE /api/locations/<id>` |
| Attachments | `POST /api/parts/<id>/attachments` (multipart `file`, or JSON `url`), `DELETE …/attachments/<id>`, `POST …/attachments/<id>/download` |
| Show a picture or datasheet | the attachment's `url` (`/attachments/<id>` on the server, or an external link), plain GET |

Rules the client must follow:

- Reads need no token; every POST, PATCH and DELETE sends
  `Authorization: Bearer <token>`. 401 means the token is missing, wrong or
  revoked; 409 means the server shows a read-only copy.
- Errors are `{"error": "…"}` with a 4xx/5xx status; the message is meant to be
  shown to the user as it is.
- Bodies are JSON objects; unknown fields are refused (so the app must not
  send extra fields, and must be updated if the API changes).
- Locations in bodies are full paths as the API returns them
  (`Cabinet/Drawer 3`); the server treats them as exact paths and creates
  missing ones where stock is put. In URLs, parts and locations are ids.
- Writes answer with the changed part, location or attachment, so the app
  updates its screen from the answer instead of loading again.

**QR codes on labels** (from `partstrack/labels.py`):

- Part: `<server>/parts/<id>` → open part `<id>`.
- Location: `<server>/?location=/<path>` (URL-encoded) → open the parts at
  that location; find its id by matching `path` in `GET /api/locations`.

The app only accepts codes whose server part matches the configured server
(scheme, host, port), and otherwise says the label belongs to another server.

**Server additions.**

1. `GET /api/token` (being added to partstrack; not released yet): answers
   with the token's id, name and creation time and with `read_only` if the
   token is valid, else 401, without changing anything. The setup screen uses
   it. A server without it answers 404 with "no such API endpoint"; the app
   then says the server is too old (partstrack 1.3.0 has the API but not this
   route).
2. Optional, later: `partstrack token add --qr` to show the new token (and the
   server address) as a QR code in the terminal, so the phone can scan it
   instead of typing a 46-character token. Needs `segno`, so only when it is
   installed (the CLI must stay dependency-free).

## Technology choices

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard for Android. |
| UI | Jetpack Compose, Material 3 | Least code for list/detail/form screens; dynamic colour and dark theme for free. |
| Structure | One activity, Navigation Compose, a ViewModel per screen with `StateFlow` | The usual, well-documented setup. |
| HTTP | OkHttp + kotlinx.serialization, a small hand-written `PartstrackApi` | About 20 endpoints; a hand-written client keeps error handling (the `{"error"}` bodies, 401, 409) in one place and avoids Retrofit's extra layer. |
| Images | Coil 3 (OkHttp network) | Thumbnails and full-size pictures from `/attachments/<id>`. |
| QR codes | zxing-android-embedded (Apache-2.0) | Works without Google Play Services and is GPL-compatible; ML Kit and the Play Services code scanner are proprietary. |
| Settings | Jetpack DataStore | Server address and display settings. |
| Token storage | AES-GCM with a key in the Android Keystore, ciphertext in DataStore | `androidx.security:security-crypto` is deprecated; this is a few dozen lines. |
| Dependency injection | By hand (an `AppContainer` in the `Application`) | Few objects; Hilt would be more setup than code saved. |
| Build | Gradle (Kotlin DSL), version catalog, Android Gradle Plugin current stable | |
| Android versions | minSdk 26 (Android 8.0), targetSdk current | See "Minimum Android version". |
| Tests | JUnit, OkHttp MockWebServer, kotlinx-coroutines-test, Compose UI tests | See "Testing". |
| License | GPL-3.0-or-later, same notice at the top of every source file as in partstrack | Same project family. |

**Minimum Android version: 8.0 (API 26).** What the app needs, and from
which version:

| Feature | Needs |
|---|---|
| Current Jetpack Compose and AndroidX | API 23 |
| Keystore AES-GCM key for the token | API 23 |
| zxing-android-embedded 4.x (QR scanner) | API 24 |
| `java.time` for history timestamps without core library desugaring | API 26 |
| Adaptive launcher icon without a fallback bitmap set | API 26 |
| Camera photo (`TakePicture`), file picker (Storage Access Framework) | API 19 or lower |

Nothing needs more than 26, and 26 saves the desugaring setup and the extra
icon files that 23 or 24 would need. Android 8.0 is from 2017, so every
phone still in use has it. Lowering it later would be possible but isn't
planned.

**Plain HTTP.** `partstrack-web` serves HTTP on the local network. Android
blocks cleartext traffic by default since Android 9, so the app allows it in
`network_security_config.xml`. The token then travels unencrypted, as the
partstrack README says, so it is only as private as the network (or VPN). The
settings screen points this out when the address starts with `http://`, and
`https://` (e.g. behind a reverse proxy) works without changes.

## Architecture

```
ui/            Compose screens and ViewModels (one package per screen)
  setup/       server address and token
  parts/       parts list, search and filters
  part/        part details, stock and move dialogs, attachments
  edit/        add/edit part form
  locations/   location tree and location dialogs
  scan/        QR scanner
data/
  api/         PartstrackApi (OkHttp), JSON models, ApiException
  settings/    SettingsStore (DataStore), TokenCipher (Keystore)
  labels/      LabelLink: parses QR code contents
AppContainer   builds the OkHttp client, API and stores once
```

- **One source of truth: the server.** ViewModels load what their screen
  needs, show it, and replace it with the object a write returns. There is no
  local database. Screens reload when they come back into view (another
  device may have changed something).
- **Errors.** `PartstrackApi` turns every non-2xx answer into
  `ApiException(status, message)`, using the server's `error` text. Network
  failures become a "can't reach the server" message with retry. 401 on a
  write offers to open the settings; 409 says the server is read-only.
- **Locations** are kept as full paths everywhere, exactly as the API returns
  them, and sent back unchanged. The location picker offers existing paths
  (a tree from `GET /api/locations`) and lets the user type a new path.
- **Concurrency.** Last write wins, as in the web interface. Stock changes use
  `delta` (take 3, add 10), which stays correct when two people change stock
  at once; "set to" is offered for counting.

## Screens

1. **Setup / settings.** Server address (e.g. `http://parts.lan:8080/`), token
   (paste, or scan a QR code once the server can show one), a "Check" button
   (`GET /api` for reachability, then `GET /api/token`; shows the token's
   name, or why it failed: unreachable, not partstrack, too old, token not
   valid, read-only copy), theme (system/light/dark). Shown on first start. Without
   a token, the app works read-only and hides editing actions.
2. **Parts.** Search field (sent as `q`, debounced; supports the server's
   attribute filters), filter chips for tag, category, location and "low",
   pull to refresh. Each row: name, description or package, total quantity,
   a "low" mark. Buttons: scan, add part.
3. **Part.** Fields, tags, attributes; stock per location, each with −/+
   buttons and a menu for "set", "move" and a note; pictures as thumbnails
   (tap for full size); datasheets and other files open in the matching app
   or the browser; the latest 10 history entries; edit, duplicate (later),
   delete (with confirmation).
4. **Add/edit part.** Name, description, manufacturer, MPN, category (with
   suggestions), package, notes, tags (chips with suggestions from
   `/api/tags`), attributes (key/value rows), minimum quantity; for a new
   part also an initial location and quantity. Sent as one request, so the
   server saves all of it or nothing; on an error the form keeps the input.
5. **Stock dialog.** Take/add amount or new quantity, location picker, note.
   **Move dialog.** From (one of the part's locations), to (picker), amount
   (default all), note.
6. **Locations.** The tree with part counts and totals; tap shows the parts
   there (the parts screen filtered by `/<path>`); add, rename, describe,
   move (choose a parent or top level) and delete (empty locations only; the
   server's message explains otherwise).
7. **Scan.** Camera view; a part code opens the part, a location code opens
   its parts, anything else shows what was read.
8. **Attachments** (on the part screen). "Take photo" (camera via
   `ActivityResultContracts.TakePicture` into the app's cache, then upload as
   `file`), "Choose file" (Storage Access Framework, any type), "Add link"
   (URL; the server downloads files behind links), delete. Photos are
   uploaded at the camera's full resolution, unchanged (typically 3–8 MB;
   the server accepts up to 100 MB per request), and removed from the cache
   after the upload. Uploads show progress and can be cancelled.

## Testing

- **API client (JVM):** MockWebServer with JSON answers captured from a real
  `partstrack-web` 1.3.0 (stored under `src/test/resources`), covering every
  endpoint, the `Authorization` header on writes only, error mapping (400,
  401, 404, 409, 413, 500, non-JSON answers, timeouts) and multipart uploads.
- **Pure logic (JVM):** QR code parsing (`LabelLink`), path handling, form
  validation, the token cipher's format (with a fake key).
- **ViewModels (JVM):** with a fake `PartstrackApi`: loading, error and retry
  states, updating from write answers, read-only mode without a token.
- **UI (instrumented, emulator):** a few Compose tests for the main flows:
  first setup, search → part → take stock, add part.
- **Against a real server (manual, and an optional instrumented test):** run
  `partstrack-web --db <scratch>/parts.db --port 8080` on the development
  machine and point the emulator at `http://10.0.2.2:8080/` (the emulator's
  name for the host's `localhost`). If it runs in WSL 2, Windows forwards
  `localhost:8080` to it by default, so the same address works. A phone on
  the network needs the Windows machine's address instead, and a WSL server
  then also needs a port forward (or mirrored networking). Never the real
  inventory.

## Implementation plan

One step at a time; each ends with passing tests, a short manual check on the
emulator, and a commit after review. Rough sizes in brackets.

0. **Tooling and skeleton** [S]
   - On Windows, in Android Studio: new "Empty Activity" (Compose) project
     with the Gradle wrapper, Kotlin DSL and a version catalog, one `app`
     module, application id and package `de.kaylatheil.partstrack`,
     minSdk 26.
   - `.gitattributes` with `* text=auto eol=lf` and `*.bat eol=crlf`, so the
     files stay LF like partstrack's, and `gradlew` marked executable
     (`git update-index --chmod=+x gradlew`), so the build also works on
     Linux and in CI.
   - Compose and Material 3 theme, license notices, `README.md`,
     `.gitignore`, `CHANGELOG.md`, remote `origin`
     <https://github.com/kaythe/partstrack-android.git>.
   - GitHub Actions: build and JVM tests on every push.
   - Done when: an empty app with a top bar builds, installs and starts.
1. **API client** [M]
   - `@Serializable` models for parts, locations, tags, history entries and
     attachments; request bodies with only the fields that are set.
   - `PartstrackApi` with every endpoint, `ApiException`, timeouts, a user
     agent `partstrack-android/<version>`.
   - Done when: MockWebServer tests cover every endpoint and error case.
2. **Setup and settings** [M]
   - DataStore settings, Keystore token cipher, setup screen with the check
     (`GET /api/token`; needs a partstrack release that has it, or a
     development server from its main branch), cleartext network config,
     read-only mode without a token.
3. **Parts list** [M]
   - Search with debounce, filters, pull to refresh, empty and error states.
4. **Part details (read-only)** [M]
   - Fields, stock, tags, attributes, thumbnails and full-size pictures with
     Coil, opening datasheets and links, history.
5. **Stock changes** [M]
   - −/+ buttons, stock and move dialogs, location picker (shared with later
     steps), notes.
6. **Add, edit, delete parts** [L]
   - The form with suggestions for tags and categories, attribute rows,
     initial stock; keeping input on errors; delete with confirmation.
7. **Locations** [M]
   - Tree, parts at a location, add/rename/move/delete dialogs.
8. **QR scanning** [S]
   - zxing-android-embedded scanner, `LabelLink` parsing, camera permission
     handling, navigation to part or location.
9. **Attachments** [M]
   - Photo, file and link upload with progress, delete, retrying a failed
     link download.
10. **Release** [S]
    - App icon (based on partstrack's `logo.svg`), release signing with a key
      kept outside the repo, R8 shrinking, version 1.0.0, a GitHub release
      with the APK.

Server-side: `GET /api/token` must be in partstrack (and released, for real
use) before step 2; `token add --qr` is optional, any time after step 8.

## Decisions

- Application id and package: `de.kaylatheil.partstrack`.
- Repository: <https://github.com/kaythe/partstrack-android>.
- Minimum Android version: 8.0 (API 26), see "Minimum Android version".
- Token check: a new `GET /api/token` route in partstrack, not a workaround.
- Photos are uploaded at full resolution; scaling down can be added later if
  the server's disk fills up.
- Implementation runs on Windows (Android Studio), not in WSL.

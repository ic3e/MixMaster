# MixMaster (Android)

Native Android app for ConWiC — solves the "guessing game" of epoxy/cementitious mixing
ratios and coverage rates, with product library, project/room/task management, calendar,
and full local-DB export/import. Built from the click-through design prototype
(`https://claude.ai/artifact/NG8FFjtmQeDPDexsAmWkuh`) into a real, buildable Android Studio
project.

## Stack

- **Kotlin + Jetpack Compose** (Material 3), Navigation-Compose
- **Room** for the local SQLite database (products, projects, floors, rooms, tasks, notes,
  photos, team) — fully exportable/importable as a single file, per the original brief
- **Preferences DataStore** for lightweight app state (signed-in role, theme, units, onboarding)
- **No backend** — everything lives on-device, matching "lives on the apk itself"
- Manual, hand-rolled dependency container (`di/AppContainer.kt`) instead of Hilt/Dagger, to
  keep the dependency surface small
- minSdk 26 / targetSdk 34 / Kotlin 2.0.21 / AGP 8.5.2 / Compose BOM 2024.09.00

## ⚠️ This project cannot be built or run in this session

Two independent things are missing from this sandboxed container:

1. **No Android SDK** (no `ANDROID_HOME`, no platform/build-tools).
2. **Google's Maven repository is network-blocked** (`dl.google.com` returns `403` through
   this environment's proxy — confirmed with `gradle help --offline` failing to resolve even
   the Android Gradle Plugin itself: `com.android.application:com.android.application.gradle.plugin:8.5.2`
   could not be found in the Google/MavenRepo/Gradle Central repositories).

Every `androidx.*` and Compose library lives on Google's Maven repo, so **no Gradle sync,
compile, or run is possible here** — not even with the SDK installed, since the dependencies
themselves can't download.

**All of the code below has been written by hand to be correct** (Room entity/DAO wiring,
Compose API usage, imports, experimental-API opt-ins, etc.) and checked against the real
AndroidX/Compose/Room APIs from documentation, but it has **not been compiled**. Open it in
Android Studio (which has full network access) to sync, build, and run it — that is the first
real verification step, and it's worth doing before relying on this as a finished app.

### Option A — GitHub Actions (get a downloadable APK without installing anything)

A workflow is already included at `.github/workflows/build-apk.yml`. It runs on GitHub's own
servers (which have full internet access to Google's Maven, unlike this sandbox), builds a
debug APK, and attaches it to the run as a downloadable artifact.

1. Push this repo to GitHub:
   ```bash
   cd MixMaster
   git remote add origin <your-repo-url>
   git push -u origin main
   ```
2. Go to the **Actions** tab on GitHub — the build starts automatically on push (or click
   **Run workflow** to trigger it manually via `workflow_dispatch`).
3. When the run finishes (a few minutes), open it and download the `mixmaster-debug-apk`
   artifact from the **Artifacts** section — that's your installable `.apk`.
4. To install: enable "Install unknown apps" for whatever you use to open the file on an
   Android device, then open it.

No Gradle wrapper is committed (this sandbox couldn't download the wrapper jar binary), so
the workflow provisions Gradle itself via `gradle/actions/setup-gradle`. Optional cleanup: run
`gradle wrapper` once from a machine with normal internet access and commit the result, so
`./gradlew` also works locally and in CI.

### Option B — Android Studio, locally

1. Open the `MixMaster/` folder in Android Studio (Ladybug 2024.2+ recommended).
2. Let Gradle sync (needs internet — it will pull AGP, Kotlin, Compose, Room, DataStore).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or just Run on a device/emulator
   with API 26+.
4. First launch seeds the real product catalog and one demo project automatically (see below).

## What's implemented

- **Sign-in** with Employer/Worker roles (persisted; switchable later from Settings)
- **Onboarding** tour
- **Home** — active project count, today's tasks (toggle done), week strip, recently-added
  products, quick-calculate shortcut
- **Calculator** — pick any product, enter area (+ coats/pours/thickness depending on the
  product's dosing mode), get an exact per-component breakdown in kg
- **Products** — brand/category filter chips, add/edit/delete (Employer only), each product
  stores an arbitrary number of ratio components (not just A/B — cement/water/additive/gravel
  etc. all work)
- **Projects** — list with status filter and real task-completion progress bars
- **Project detail**, 5 tabs:
  - **Overview** — client/address (tap → copy or open directions)/dates/progress/scope
  - **Tasks** — add (Employer)/toggle done
  - **Layout** — floors & rooms with per-room area and product assignment (auto-computes the
    exact mix for that room's area), photo gallery (system photo picker), notes feed
  - **Materials** — total kg rollup + per-room material log, derived from real assignments
  - **Calendar** — this project's tasks as an agenda
  - Overflow menu: **Generate report (PDF)** (see below), Edit project, Archive project
- **Calendar** (app-wide) — month grid with task-count dots, day agenda, aggregated across
  all projects
- **Settings** — theme/units, **export/import the whole database as one file** (Storage
  Access Framework; import restores and restarts the app), role switch, team list, app-lock
  and tips toggles (persisted; biometric enforcement itself is not wired up yet — see below)
- **PDF report generator** — real `android.graphics.pdf.PdfDocument`-based branded report
  (letterhead with the ConWiC logo, scope, rooms & materials, tasks), written to the app's
  files and opened via `FileProvider`

## Real product catalog (seeded on first run)

Sourced from public manufacturer datasheets during the design phase and carried over verbatim:

| Brand | Product | Dosing | Ratio |
|---|---|---|---|
| Ideal Work | Microtopping® Base Coat | 1.35 kg/m² per coat | 100 : 35 (powder:polymer) |
| Ideal Work | Microtopping® HP | 0.84 kg/m² per coat | 100 : 40 |
| Ideal Work | Microtopping® Finish Coat | 0.225 kg/m² per coat | 100 : 50 |
| Ideal Work | Nuvolato Architop® Coat 1 | 2.48 kg/m² per pour | 25 : 6 |
| Ideal Work | Nuvolato Architop® Coat 2 | 2.07 kg/m² per pour | 25 : 4 : 2 (+ water) |
| Ideal Work | IdealPU-WB Primer | ~50 g/m² per coat | exact A:B not published — flagged in-app |
| Ideal Work | Ideal Sealer | ~180 g/m² per coat | single component |
| Mapei | Ultraplan Eco | 1.6 kg/m² per mm | 100 : 25 (powder:water) |
| Mapei | Primer G | ~0.10–0.15 kg/m² | diluted 1:1–1:3 (midpoint shown, flagged as a range) |
| Sika | Sikafloor®-263 SL | 0.9–1.2 kg/m² per mm | 79 : 21 (A:B) |
| Ardex | ARDEX K 301 | 1.6 kg/m² per mm | 100 : 21 (powder:water) |

Two entries (IdealPU-WB Primer, Mapei Primer G) intentionally do **not** fabricate a precise
ratio the datasheets don't give — the app shows the real published range/note instead of
guessing, which is the whole point of the app.

## Known simplifications / good next steps

- **Fonts**: the design uses Archivo (display) + Manrope (body) from Google Fonts. No `.ttf`
  binaries could be fetched in this environment, so the app currently uses the system default
  font. Drop the real files under `app/src/main/res/font/` and wire them into `ui/theme/Type.kt`
  for exact brand match.
- **Biometric app-lock**: the Settings toggle persists a preference but doesn't yet gate app
  launch behind `BiometricPrompt` — straightforward to add in `MainActivity`.
- **Photos**: stored as the content URI returned by the system Photo Picker (Android's modern
  picker grants persistent read access to those URIs, so this is safe long-term) rather than
  copied into app storage — simplest correct option without adding an image-loading library.
- **Team management**: Settings shows the seeded team list; there's no invite/remove flow yet.
- **No automated tests yet** — the mixing-ratio math (`domain/MixCalculator.kt`) is the
  highest-value target for a first unit test pass since it's pure Kotlin with no Android
  dependencies.
- Once opened in Android Studio, run a full build and fix anything the compiler flags before
  shipping — this was written carefully but without a compiler in the loop.

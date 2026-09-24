# MixMaster

A real Android app for **ConWiC Oy**, an Estonian flooring contractor: it works out mix ratios,
batches, material needs and warehouse stock for floor coatings. It is field-tested daily on a
Samsung phone (Android 15, Estonian system language) and the tester is the client — every change
lands as an installed APK within minutes, so shipping something broken costs a site visit, not a
CI run.

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · single activity · hand-rolled DI
(`di/AppContainer.kt`). minSdk 26, targetSdk 34.

## Building

There is **no Android SDK in this sandbox** — never try to run Gradle. The build happens in CI:

- Push to `main`. GitHub Actions builds and commits `dist/MixMaster_1.0.<run_number>.apk` plus
  `dist/latest.json`; a failure commits `.ci-logs/last-failure.log` instead. Read that file first
  when a build fails — it names the line.
- `gh` is not installed. Poll with `git fetch -q origin main` in a background loop and look for
  the `ci: publish` commit.
- CI also commits the Room schema it generates (`app/schemas/`), so `<version>.json` appears after
  the build, not before.
- Deliver the APK to the user with `SendUserFile`. The in-app updater reads `latest.json` from
  `main` only.
- A round trip is about four minutes, and every one of them ships a version number. Docs-only
  changes are in `paths-ignore` so they do not cut a release.

## Before pushing

Everything is compiled blind, so check by hand what the compiler would have caught:

- **Strings.** Every `R.string` / `R.plurals` / `R.array` reference must exist in
  `values/strings.xml`, and every new key needs `values-et` and `values-fi` as well. `app_name` is
  deliberately untranslated; nothing else should be missing. (A script that shipped a commit
  referencing four undefined strings is why this is a rule.)
- **Imports.** No duplicates, no missing ones, braces balanced in every touched file.
- **Migrations.** Build the previous version from `app/schemas/<n>.json`, run the migration's SQL
  against it in SQLite, and check the table comes out matching the entity with no FK violations.
  After CI, diff the statements against the generated `<n+1>.json` — they should match exactly.
- **Arithmetic.** Simulate formatters, dose maths and batch plans in Python against real figures
  from the user's catalogue before trusting them.

## House style

- Comments say **why**, not what — usually the bug or the site reality that put the line there.
  Match the density of the file you are in.
- No hard-coded user-visible text. Formatting lives in `domain/Formatting.kt`: grams show to
  0.1 g (what the scale on the van reads), kilos to the gram.
- The user's words are the product's words: coats, batches, bags, the shed. Not "items", "records".

## Things that already bit us

- `MainActivity.attachBaseContext` replaces the base context with `createConfigurationContext` for
  the language setting. **That context belongs to no activity**: `PrintManager` refuses it (use
  `ActivityBaseContext.current()`), and anything resolving strings outside the activity — a
  `BroadcastReceiver`, for one — gets the device locale rather than the app's.
- A bare language tag (`et`) applied over a device locale with a country (`et_EE`) makes the config
  disagree with the system and relaunches the activity forever. `LanguageStore.localeFor` exists
  for that.
- `stringResource` works in inline lambdas but not inside `onClick` / `onSelect` — resolve above.
- Sheets and form lists need `.imePadding().navigationBarsPadding().verticalScroll(...)`, or the
  keyboard covers the field being typed into.
- `FLAG_ACTIVITY_CLEAR_TOP` on a standard-launchMode activity destroys and rebuilds it. MainActivity
  is `singleTop` and the alarm uses `SINGLE_TOP`.
- Destructive actions all go through `ui/components/ConfirmDialog.kt`, and no delete sits under the
  save button.

## The model, in one paragraph

**Products** are bought items (a bag of powder, a canister of polymer, water, a pigment) and carry
the pack they come in. **Solutions** are recipes made of products; a solution can hold several
**coats** (`parentId` / `coatName`), each with its own ratio and coverage, because a datasheet can
give one product two recipes. Projects hold floors, floors hold rooms, and a room holds an ordered
list of **room_layers** — the coats laid on it, each with the colour it is tinted with. **stock**
is what is on the shelf and **deliveries** are what has been ordered; bookings are worked out from
the rooms every time rather than stored, so they cannot drift.

## Delivering

- Commit messages explain the change in the app's own voice, and end with the `Co-Authored-By` and
  `Claude-Session` attribution lines.
- Push to `main`. Do not open pull requests unless asked.

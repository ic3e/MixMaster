# MixMaster

A real Android app for **ConWiC Oy**, an Estonian flooring contractor: it works out mix ratios,
batches, material needs and warehouse stock for floor coatings. It is field-tested daily on a
Samsung phone (Android 15, Estonian system language) and the tester is the client — every change
lands as an installed APK within minutes, so shipping something broken costs a site visit, not a
CI run.

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · single activity · hand-rolled DI
(`di/AppContainer.kt`). minSdk 26, targetSdk 34 (Pour Day: 36, for Google Play).

**Pour Day**, the break-time game, lives in its own module, `:game` (the page in
`game/src/main/assets/pourday`, plus the phone's voices, Nearby for co-workers and `GameScreen`,
which puts it in a WebView). Two apps carry it: MixMaster (Settings → Break time) and `:pourday`,
the game on its own for people outside the company, with nothing of MixMaster in it. Phones play
together when they carry the same game — the check is a hash of the `:game` module and
`pourday/src` (`GameScreen.stamp`), not either app's version. The same stamp goes into
`dist/pourday.json`, and the Pour Day app offers its players an update only when it has changed.
MixMaster carries the Pour Day APK in its assets (packed at build time by `embedPourDay`) for
"Send to a friend" under Break time.

`:pourday` comes in two builds. **direct** is the APK passed round as a file (and the one packed
into MixMaster): it looks on GitHub for a newer version and installs it (`src/direct`). **play** is
for Google Play, which takes off any app that updates itself outside Play: no updater, no
`REQUEST_INSTALL_PACKAGES`, no `INTERNET`, and a `targetSdk` that keeps up with Play's yearly
minimum. Anything that makes Pour Day download or install code goes in `src/direct` only. CI
builds the Play bundle in a step of its own that may fail without holding MixMaster back: signed
into `dist/play/` when the upload key is in the repository's secrets, its permissions listed in
`.ci-logs/play-permissions.txt`, and its log in `.ci-logs/play-failure.log` when it fails.
`docs/pourday-google-play.md` is the publishing guide, `docs/pourday-privacy.md` the policy the
store links to — both must stay true to the play build.

Pour Day has a **How to play** page (the `HOWTO` list in `game.js`, opened from the title and the
pause menu). It describes the whole game, section by section, and must stay true: any change to
how a day plays — a new job, tool, rule, button or mishap — updates `HOWTO` in the same commit.

MixMaster has a **guide** (`ui/guide`): the app played through like a video, chapter by chapter,
with its screens drawn from the app's own components and strings and the captions read out by the
phone's voice. A new phone sees it after sign-in; Settings → Help and the tour's cards open it
again. It has to stay true the same way: a change to a screen the guide shows — a button renamed
or moved, a step added — updates that scene (`GuideScript.kt`, `GuideScreens.kt`) and its caption
in all three languages in the same commit.

## Building

There is **no Android SDK in this sandbox** — never try to run Gradle. The build happens in CI:

- Push to `main`. GitHub Actions builds and commits `dist/MixMaster_1.0.<run_number>.apk`,
  `dist/PourDay_1.0.<run_number>.apk` and `dist/latest.json`; a failure commits
  `.ci-logs/last-failure.log` instead. Read that file first when a build fails — it names the line.
- `gh` is not installed. Poll with `git fetch -q origin main` in a background loop and look for
  the `ci: publish` commit.
- CI also commits the Room schema it generates (`app/schemas/`), so `<version>.json` appears after
  the build, not before.
- Every successful build also leaves the compiler's warnings in `.ci-logs/warnings.log` — an
  unused variable or parameter, a deprecated call. Read it after a build; it is the only
  compiler this code meets.
- Deliver the APK to the user with `SendUserFile`. The in-app updater reads `latest.json` from
  `main` only.
- A round trip is about four minutes, and every one of them ships a version number. Docs-only
  changes are in `paths-ignore` so they do not cut a release.

## Before pushing

Everything is compiled blind, so check by hand what the compiler would have caught. Most of it
is in one script — run it before every push:

```
python3 tools/preflight.py
```

It checks the seven things this app has actually shipped broken: a string that exists in one
language only, a `Modifier.x()` whose import was never added, an import nothing uses, a bracket
that never closes, the body left behind when a one-line function is deleted by hand, a
`@Composable` left stranded on a class when a declaration was inserted above the function it
belonged to, and a function declared twice in the game's script (JavaScript lets the second
silently replace the first — the float once ran the laser's bubble game that way). It exits
non-zero when it finds any. The rest still needs a person:

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
  0.1 g (what the scale on the van reads), kilos to 0.1 kg — three decimals on a total is noise.
- The user's words are the product's words: coats, batches, bags, the shed. Not "items", "records".

## Things that already bit us

- `MainActivity.attachBaseContext` replaces the base context with `createConfigurationContext` for
  the language setting. **That context belongs to no activity**: `PrintManager` refuses it (use
  `ActivityBaseContext.current()`), and anything resolving strings outside the activity — a
  `BroadcastReceiver`, for one — gets the device locale rather than the app's.
- `LanguageStore.wrap` overrides **only the locale** (`Configuration().apply { setLocale(..) }`).
  A copy of the whole configuration pinned the screen size too, and since MainActivity handles
  rotation itself, every dialog kept measuring against the portrait width after a turn.
- One typeface: Manrope (`AppFontFamily`). The app once paired it with Archivo for headings and
  the client found two faces on one card odd, so headings stand out by weight and size only.
  Ligatures are off (`PlainLetters`, on every style): Manrope's joined "fl" read as a rendering
  bug in "floors". A canvas `TextStyle` sets it too.
- All fifteen Material type styles are set in `ui/theme/Type.kt`. A style left out falls back to
  Roboto without a word — the mixing screen and the date/time pickers both did — so a new style
  used anywhere must exist there. Canvas `TextStyle`s name their `fontFamily` themselves.
- A filter list's first entry is `FilterAll` ("All", a stored value). Show it with `FilterField`,
  never a bare `DropdownField`, or it reads "All" in every language. Likewise never show an
  enum's `.name` — map it to a string.
- Pack kinds (`bag`, `canister`, …) are stored as English keys. Show them through
  `ui/components/PackNames.kt` (`packName`, `packsName`, `packCount`), never raw.
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
- The mixing alarm has one rule: the booking with the system clock is cancelled where the run
  really ends (closed, ended early, last batch acknowledged), and **left standing** everywhere
  else — including when the screen is disposed. Cancelling only the notification leaves it to go
  off during the next batch; cancelling it on dispose loses the one alert that still works with
  the app off screen.
- The app lock asks `MixRun.underWay()` before re-locking. Re-locking drops everything composed
  behind it, which mid-batch is the whole run.
- Anything animating every frame is read inside `drawBehind` / `Canvas` / `graphicsLayer`, not in
  composable scope: read while composing, one countdown recomposes the whole screen sixty times a
  second. Same reason the clock is passed into the ring as a lambda.
- No composable call behind `?.` — write the `if`, or lift the calls into a small
  `remember…()` that returns null early.
- No `vararg` of a value class (`Offset`, `Dp`, `Color`, `TextUnit`) — Kotlin refuses it. Spell
  the parameters out.

## The model, in one paragraph

**Products** are bought items (a bag of powder, a canister of polymer, water, a pigment) and carry
the pack they come in. **Solutions** are recipes made of products; a solution can hold several
**coats** (`parentId` / `coatName`), each with its own ratio and coverage, because a datasheet can
give one product two recipes. Projects hold floors, floors hold rooms, and a room holds an ordered
list of **room_layers** — the coats laid on it, each with the colour it is tinted with. **stock**
is what is on the shelf and **deliveries** are what has been ordered; bookings are worked out from
the rooms every time rather than stored, so they cannot drift.

**Sharing a company** (`data/company`): SQLite triggers write every change to the shared tables
into `sync_outbox`, whatever screen made it; `SyncEngine` sends it to the company's server and
asks for what changed since the last change number it saw. Row ids are made unique across phones
by `GlobalIds`, and a stock row's id is its product's. A new table that should be shared goes in
`SyncEngine.Tables` (parents before children) and in both servers' permission lists. The servers
(`server/`) speak one protocol; `node server/test/run.mjs` holds them to it. Photos and blueprints
are shared too, but their files travel apart from the rows (`FileSync`, in 1.5 MB parts): a row
names its file by `fileKey`, its `uri` is this phone's own and never sent, and a row whose file
hasn't come down yet has a blank `uri` — anything showing one must cope with that. Changes to
them wait in the outbox until the server's `hello` says `files: true`, since an older server
refuses a worker's photo and the phone would take that as a delete.

**Permissions on screen** follow the server's groups (`rememberAccess()`): catalogue = products
and recipes, projects = projects/floors/rooms/coats, warehouse = stock and deliveries, site =
tasks, notes, photos, recorded mixes and usage logs. Whoever may not change something does not
see the control that would: add/remove/save buttons are left out, and a form they may read is
wrapped in `CompositionLocalProvider(LocalReadOnly provides true)`, which every field honours.
No "not yours" toasts.

## Delivering

- Commit messages explain the change in the app's own voice, and end with the `Co-Authored-By` and
  `Claude-Session` attribution lines.
- Push to `main`. Do not open pull requests unless asked.

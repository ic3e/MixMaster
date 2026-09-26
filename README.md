# MixMaster (Android)

The floor-coating app for **ConWiC Oy**. It works out mix ratios and batches, what a job needs,
and what is on the shelf, and it shares one company's data between the employer's phone and the
crew's.

- **Products and recipes:** the bought items with their packs, and the recipes made of them,
  coat by coat, with the datasheet ranges.
- **Calculator and mixing:** batches that fit the mixer, a timer and an alarm per batch, and
  every mix recorded against the room it went into.
- **Projects:** floors, rooms and the coats laid on each, materials worked out from them, tasks,
  notes, photos and a PDF report.
- **Warehouse:** what is on the shelf, what the booked jobs need, orders and deliveries, and a
  stock count with reminders.
- **Company:** one set of data on every phone, kept on a server of the company's own (see below).
- English, Estonian and Finnish, an app lock, backup and restore, and an in-app updater.

Kotlin · Jetpack Compose (Material 3) · Navigation · Room · DataStore · Biometric. Nothing else:
no networking, image or DI library. minSdk 26, targetSdk 34.

## Getting the app

Every push to `main` is built by GitHub Actions, which commits the APK to
`dist/MixMaster_1.0.<build>.apk` along with `dist/latest.json`. The app's own updater reads
that file to offer the new version. A failed build commits `.ci-logs/last-failure.log`
instead, and every build leaves the compiler's warnings in `.ci-logs/warnings.log`.

There is no Gradle wrapper. The workflow provisions Gradle 8.7 itself. To build locally, open the
project in Android Studio.

## The company server

The shared data lives on the company's own hosting. There are two interchangeable servers:

- `server/website/mixmaster/`: one PHP file for the company website (SQLite, or MySQL)
- `server/google/Code.gs`: an Apps Script web app for a Google account, keeping a Google Sheet

`docs/company-server.md` is the step-by-step setup guide for either. `node server/test/run.mjs`
runs the same conversation against both.

## Where things are

| | |
|---|---|
| `app/src/main/java/.../data` | Room database, repositories, the company sync, backup, the PDF report |
| `app/src/main/java/.../domain` | The maths: doses, batches, packs, stock, dates, formatting |
| `app/src/main/java/.../ui` | The screens, one folder per part of the app |
| `app/schemas` | The database schema of every version, written by the build |
| `tools/preflight.py` | Checks run before every push (strings, imports, brackets) |

`CLAUDE.md` holds the working notes: how the code is checked without a compiler, the house style,
and the things that have already gone wrong once.

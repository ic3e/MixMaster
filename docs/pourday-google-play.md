# Pour Day on Google Play

The code is ready: the **play** build of Pour Day (`pourday/build.gradle.kts`) has no self-update,
no install permission and no internet, targets Android 16 (API 36) and comes out as a signed app
bundle (`.aab`). What is left is done once by whoever publishes it: an upload key, a Play Console
account, and the store listing. This page walks through all three.

The APK passed round as a file (the **direct** build, also packed into MixMaster) stays as it
was, updater and all. The two carry the same game and play together.

## 1. The upload key (once, on a computer)

Google signs the app that reaches phones with its own key. The **upload key** proves an upload
comes from you. It is never put in the repository.

1. On a computer with Java (it comes with Android Studio, or any JDK), run:

   ```
   keytool -genkeypair -v -keystore pourday-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   Give it a strong password, and the same one again if it asks for the key's password (CI uses
   one password for both). The name questions can be the company's (ConWiC Oy, Tallinn, EE).
2. Keep `pourday-upload.jks` and its password safe, outside the repository: a password manager
   and a second copy somewhere else. If it is ever lost, Google can reset an upload key (Play
   Console → Test and release → App integrity).
3. Turn the file into text for GitHub:
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("pourday-upload.jks")) | Set-Clipboard`
   - Mac: `base64 -i pourday-upload.jks | pbcopy`
   - Linux: `base64 -w0 pourday-upload.jks`
4. On GitHub: the repository → **Settings → Secrets and variables → Actions → New repository
   secret**, twice:
   - `PLAY_UPLOAD_KEYSTORE_BASE64`: the text from step 3
   - `PLAY_UPLOAD_PASSWORD`: the password
5. From the next build on, CI puts the signed bundle in the repository as
   `dist/play/PourDay_1.0.<build>.aab`. That is the file to upload. The same key signs
   MixMaster's Play bundle too (`dist/play/MixMaster_1.0.<build>.aab`, see
   `docs/mixmaster-google-play.md`). Without the secrets the bundle
   is still built (to show it builds), but not signed and not kept.

## 2. The Play Console account

- **An organisation account for ConWiC Oy** is the easier road: it needs the company's D-U-N-S
  number, and it can publish straight away.
- **A personal account** created after 13 November 2023 must first run a closed test: at least
  12 testers opted in for 14 days in a row, before it can publish to everyone.
- Registration is a one-time fee of 25 USD.
- Whoever publishes puts their name on the store page. The game shows ConWiC's wordmark on a van,
  so if it is not ConWiC publishing, get their written OK first.

## 3. Create the app

Play Console → **Create app**:

| Field | Answer |
| --- | --- |
| App name | Pour Day |
| Default language | English (United Kingdom) — the game is in English |
| App or game | Game |
| Free or paid | Free |

Then **Test and release → Production** (or Closed testing, for a personal account) → Create
release → upload `dist/play/PourDay_1.0.<build>.aab`. The first upload fixes the package name,
`com.conwic.pourday`, for good, and turns on Play App Signing with a key Google makes: accept it.

Everyone who has the Pour Day APK from a file must uninstall it before installing the Play one:
the two are signed with different keys, and Android won't put one over the other.

## 4. Store listing

**Short description** (80 characters at most):

> A day on a concrete site: prep, pipes, pour, trowel. Keep the dog off it.

**Full description**:

> Pour Day is one day on a concrete site, first person, from the alarm to the pay slip.
>
> Load the van, check the formwork, set up the laser, lay the pump line and pour the slab while
> the trucks keep coming — each one setting at its own pace. Then wash the tools before the
> concrete sets on them, wait, and trowel the slab before it's too hard to mark. Mind the
> passers-by, the dogs and the cats, and the pump driver who is on the clock.
>
> Play alone, or with co-workers on the same slab: phones close by play one day together over
> Bluetooth and Wi-Fi, no internet needed.
>
> No ads, no account, no tracking.

**Category**: Simulation. **Contact email**: required, and shown on the store page.

**Graphics** Play asks for: the app icon at 512 × 512 PNG, a feature graphic at 1024 × 500, and at
least two phone screenshots.

## 5. App content (Policy → App content)

| Section | Answer |
| --- | --- |
| Privacy policy | A public address for `docs/pourday-privacy.md`, with the publisher and contact email filled in: the company website, or the file on GitHub |
| Ads | No ads |
| App access | All functionality is available without special access |
| Target audience | 13 and older (not for children: swearing, alcohol, smoking) |
| Content rating | The IARC questionnaire; see below |
| Data safety | See below |

**Content rating.** Answer as the game is. It has strong language ("shit", "bastard"),
crude toilet humour, and references to drinking and smoking: hangover mornings where the worker
is "still a bit drunk" from last night's beer or the neighbour's vodka, and a smoke break. No
fighting, no blood beyond a bitten tongue, no gambling, no sexual content.
Players close by see each other's chosen name; there is no chat. Expect a rating around PEGI 12–16
or ESRB Teen.

**Data safety.** The app itself collects and shares nothing: no account, analytics, ads or
server, and no internet. Playing together sends the day and the typed name directly to phones
nearby, not to the developer or anyone else. Nearby Connections is part of Google Play services:
check Google's own data disclosure for it ("Google Play services — Data disclosure", on
developers.google.com) and include whatever it lists for Nearby. `.ci-logs/play-permissions.txt`,
written by every build, lists every permission the Play build asks for, libraries included.

## 6. Each new version

Every push to `main` makes a new `dist/play/PourDay_1.0.<build>.aab` (once the secrets are in
place). Upload it as a new release when there is something worth sending. Its version number is
always higher than the last, as Play requires.

Once a year Google raises the Android version apps must target (for new apps and updates: API 36
since 31 August 2026; the next step is expected around August 2027). Pour Day's `targetSdk` in
`pourday/build.gradle.kts` goes up with it, and the game is tried on a phone after.

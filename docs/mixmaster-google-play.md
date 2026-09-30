# MixMaster on Google Play

In case the company ever wants MixMaster on Google Play, the code is ready: the **play** build
(`app/build.gradle.kts`) comes out as a signed app bundle beside Pour Day's. The copy used every
day, passed round as a file (the **direct** build), is unchanged.

Much of the road is the same as Pour Day's, and `docs/pourday-google-play.md` walks through it:
the upload key and its two GitHub secrets (**one key signs both apps**, so nothing more is needed
once Pour Day's is in place), the Play Console account, and creating the app. This page is what
is different for MixMaster.

## 1. What the Play build leaves out, and why

| In the copy passed round as a file | In the Play build | Why |
| --- | --- | --- |
| Updates itself from GitHub (Settings → App updates) | No updater; Settings shows no such section | Play takes off apps that update themselves outside Play |
| Carries the Pour Day APK for "Send to a friend" | Sends the link to Pour Day on Google Play | Play allows no APK inside another app's files |
| Alarms to the second without asking (`USE_EXACT_ALARM`) | The worker allows "Alarms & reminders" once: the mixing screen says so and opens the page | Play keeps that permission for alarm clocks and calendars |
| Ideal Work's technical sheets (PDFs) in the demo | Links to Ideal Work's documentation page, as a fresh install has | The manufacturers' PDFs are not the company's to hand out on a public store |
| Targets Android 14 (API 34) | Targets Android 16 (API 36), as Play requires | Android 15 then draws the app under the bars; MainActivity keeps every screen between them |

The Play build has **not yet been tried on a phone**. Before its first upload, put it on a test
phone through the internal testing track (below) and go through the day: sign-in, a job, a mix
with the phone locked, the warehouse, the company screen, a form with the keyboard up.

## 2. Public, or only for the crew?

MixMaster is a work tool for one company. It does not have to be on the public store to come from
Google Play:

- **Internal testing track**: up to 100 people, invited by email. Releases reach them in minutes,
  without Google's full review, and it needs no closed test first. For a crew this is often all
  that is needed.
- **Managed Google Play (private app)**: for a company that manages its phones with Google
  Workspace or a device-management service. Only that company's phones see it.
- **Public (production)**: everyone can find it. A personal developer account first needs a closed
  test with 12 testers for 14 days (see the Pour Day guide).

## 3. Before a public listing

- **The name.** "Mixmaster" is also a long-standing brand of kitchen mixers (Sunbeam). Search the
  Play Store and a trademark register before using the name alone; "MixMaster by ConWiC" or
  similar is safer.
- **Graphics.** Icon 512 × 512, feature graphic 1024 × 500, at least two phone screenshots.
- **Category.** Business (or Productivity). **Target audience**: 18 and older, a work tool.

## 4. App content (Policy → App content)

| Section | Answer |
| --- | --- |
| Privacy policy | A public address for `docs/mixmaster-privacy.md`, with the publisher and contact email filled in |
| Ads | No ads |
| App access | Everything works without signing in. Sharing a company needs a server and an access code: give the reviewers a test company's access code, or say it is optional |
| Content rating | The IARC questionnaire. The app itself is a calculator; **Pour Day is inside it**, so answer as for Pour Day: strong language, crude humour, references to drinking and smoking |
| Target audience | 18 and older |
| Data safety | See below |
| Full-screen intent | Play asks apps that alert over the lock screen to say why: the mixing timer's alarm when a batch is done. If Google does not accept that, the Play build can drop `USE_FULL_SCREEN_INTENT`; the mixing screen then says the alert reaches only the notification shade, and opens the page where the phone can allow it |
| Exact alarms | `SCHEDULE_EXACT_ALARM` only, which the worker allows; nothing to declare |
| Account deletion | People join a company with a code from its owner, not an account with you. Say so, and point to the privacy policy's "Leaving, and deleting" |

**Data safety.** When a company shares its data, MixMaster sends it to the company's own server,
over https. Declare it as collected, for app functionality, encrypted in transit, not shared with
third parties, and deletable on request:

- Personal info: name (the names the owner gives people)
- Photos and videos: photos (added to jobs)
- Files and docs: blueprints
- App activity: other user-generated content (notes, tasks, jobs, stock)
- Pour Day's Nearby Connections: check Google's own data disclosure for Google Play services and
  include what it lists.

`.ci-logs/play-permissions.txt` lists every permission each Play build asks for, libraries
included, under `[app]` for MixMaster and `[pourday]` for Pour Day.

## 5. The bundle

Once the upload key is in the repository's secrets, every build puts
`dist/play/MixMaster_1.0.<build>.aab` beside Pour Day's. Upload the newest one. A phone with the
copy passed round as a file must uninstall it before installing the Play one: they are signed
with different keys. Uninstalling empties the app, so a worker in a company simply joins again
with a new code, and an owner exports a backup first or joins back with a new owner's code.

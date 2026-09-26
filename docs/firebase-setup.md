# MixMaster – Firebase setup guide

Kept in step with the shared doc of the same name. **If the app's sync code changes a collection
name, a role or a sign-in detail, this file, the rules below and the doc change with it.**

The employer sets this up once, at a computer, in about 30 minutes: a Firebase project owned by
ConWiC, Google sign-in, a database in the EU and its security rules, then the app is linked on the
employer's phone and the crew are invited from it. Everything up to step 7 is free; photos and files
(step 8) need a card on file and cost cents a month.

## Before you start

You need a Google account that belongs to the company, a computer with Chrome, and the employer's
phone with MixMaster on it.

- **A company Google account.** One ConWiC controls (an office Gmail), not someone's private account.
  Whoever owns it owns all the company's data in Firebase. Add a second owner later (*Keeping it safe*).
- **A computer.** The console works on a phone, but the steps are much easier on a laptop or desktop.
- **The employer's phone** with MixMaster installed, for steps 6 and 7.
- **A bank card, only for step 8** (photos and files). Steps 1–7 are free and ask for no card.

| What Firebase asks for | Value |
| --- | --- |
| Android package name | `com.conwic.mixmaster` |
| App nickname | `MixMaster` |
| SHA-1 certificate fingerprint | `F5:CD:92:FD:B4:E5:75:B1:4A:A0:36:06:13:2C:16:F1:05:ED:6C:9C` |
| SHA-256 certificate fingerprint | `76:A3:8D:E5:4E:D9:06:C0:65:3B:90:93:9D:3A:DD:59:FE:67:D7:E1:DA:42:F5:32:66:9E:39:10:C5:F7:21:1B` |
| Database location | `europe-north1` (Finland) |

The fingerprints are those of `keystore/debug.keystore`, which signs every build (CI included). They
are not secrets. A Google Play release is signed with Play's key, whose fingerprints must be added too.

## Steps 1–2: Create the project and register the app

**Step 1 — Create the project**

1. Go to <https://console.firebase.google.com> and sign in with the company Google account.
2. Click **Create a project** (on a new account: **Get started with a Firebase project**).
3. Name it `ConWiC MixMaster`. Accept the terms and click **Continue**.
4. If it offers **Gemini in Firebase**, it can be switched off; the app does not use it.
5. Switch **Google Analytics** off.
6. Click **Create project**, wait, then **Continue**.

**Step 2 — Register the Android app**

1. On **Project Overview**, click the **Android** icon (or **Add app** → Android).
2. **Android package name:** `com.conwic.mixmaster`
3. **App nickname:** `MixMaster`
4. **Debug signing certificate SHA-1:** the SHA-1 above.
5. **Register app**.
6. **Skip the `google-services.json` download for now** (click **Next**); it is downloaded in step 6,
   after sign-in is switched on, because the file changes when you do.
7. **Next** through the "Add Firebase SDK" screens, then **Continue to console**.
8. **Gear icon** → **Project settings** → **General** → **Your apps** → **MixMaster** →
   **Add fingerprint** → the SHA-256 above → **Save**.

## Step 3: Turn on Google sign-in

1. Left menu: **Build** (or **Security**) → **Authentication** → **Get started**.
2. **Sign-in method** tab → **Google** → **Enable**.
3. **Project support email:** the company email. **Save**.

Leave every other sign-in method off.

## Steps 4–5: Create the database and lock it down

**Step 4 — Create the database**

1. Left menu: **Build** (or **Databases & Storage**) → **Firestore Database**.
2. **Create database** (or **Add database**). If asked for an edition: **Standard edition**.
3. **Database ID:** leave `(default)`.
4. **Location:** `europe-north1 (Finland)`. **Cannot be changed later.**
5. **Start in production mode** → **Create**.

**Step 5 — Paste the security rules**

**Rules** tab → delete everything → paste → **Publish**.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }
    function companyDoc(cid) { return get(/databases/$(database)/documents/companies/$(cid)); }
    function isOwner(cid) { return signedIn() && companyDoc(cid).data.ownerUid == request.auth.uid; }
    function memberPath(cid) { return /databases/$(database)/documents/companies/$(cid)/members/$(request.auth.uid); }
    function isMember(cid) { return signedIn() && exists(memberPath(cid)); }
    function isEmployer(cid) { return isMember(cid) && get(memberPath(cid)).data.role == 'employer'; }
    function myEmail() { return request.auth.token.email.lower(); }
    function invitePath(cid) { return /databases/$(database)/documents/companies/$(cid)/invites/$(myEmail()); }
    function isInvited(cid) {
      return signedIn() && request.auth.token.email_verified == true && exists(invitePath(cid));
    }

    // What a worker may change. Everything else only the employer may change.
    function workerMayWrite(collection) {
      return collection in ['stock', 'stockMoves', 'mixes', 'usage', 'materialUse',
                            'notes', 'photos', 'tasks', 'deliveries'];
    }

    match /companies/{cid} {
      allow create: if signedIn() && request.resource.data.ownerUid == request.auth.uid;
      allow read: if isMember(cid) || isInvited(cid);
      allow update: if isOwner(cid);
      allow delete: if false;

      match /members/{uid} {
        allow read: if isMember(cid);
        allow create: if signedIn() && uid == request.auth.uid && (
          (isOwner(cid) && request.resource.data.role == 'employer') ||
          (isInvited(cid) && request.resource.data.role == get(invitePath(cid)).data.role));
        allow update: if isEmployer(cid) && uid != companyDoc(cid).data.ownerUid;
        allow delete: if (isEmployer(cid) && uid != companyDoc(cid).data.ownerUid)
                      || (signedIn() && uid == request.auth.uid);
      }

      match /invites/{email} {
        allow read: if isEmployer(cid) || (signedIn() && myEmail() == email);
        allow create, update, delete: if isEmployer(cid);
      }

      match /{collection}/{docId} {
        allow read: if isMember(cid) && !(collection in ['members', 'invites']);
        allow write: if isMember(cid) && !(collection in ['members', 'invites'])
                     && (isEmployer(cid) || workerMayWrite(collection));
      }
    }
  }
}
```

Data model the rules assume (the app's sync must match it):

- `companies/{cid}` — `ownerUid`, `name`. Created first, on its own (the member rule reads it with `get`,
  which does not see writes in the same batch).
- `companies/{cid}/members/{uid}` — `role`: `employer` | `worker`, `name`, `email`.
- `companies/{cid}/invites/{email}` — document id = the lower-cased email; `role`, `name`.
- Every other collection directly under the company holds data; workers may write only the
  collections in `workerMayWrite`.

## Steps 6–7: Connect the employer's phone and invite the crew

In MixMaster, in the version that adds company sharing.

**Step 6 — Connect the employer's phone**

1. Firebase: **gear icon** → **Project settings** → **General** → **Your apps** → **MixMaster** →
   download **google-services.json**.
2. Get it onto the employer's phone (email to yourself, or Google Drive) — it lands in **Downloads**.
3. MixMaster: **Settings** → **Company sharing** → **Connect company** → **Choose settings file**.
4. **Sign in with Google** with the employer's own account; it becomes the company's owner in the app.
5. **Create company** (`ConWiC Oy`). The phone uploads products, recipes, projects and stock — on
   Wi-Fi, app open until it says done.

**Step 7 — Invite the crew**

1. **Settings** → **Crew** → **Add member**: name, the email of their Google account, role.
2. **Send invite** by WhatsApp, SMS or email: a link and a join code (the code carries the project
   settings, so nobody else needs the file).
3. The worker installs MixMaster, opens the link (or **Settings** → **Company sharing** →
   **Join with code**) and signs in with Google **using the invited email**.
4. Their phone downloads the company's data; what was on it before is replaced (the app offers a
   backup first).

Removing someone from **Crew** ends their access straight away.

## Step 8 (optional): Photos, blueprints and datasheets

Since October 2024 Cloud Storage for Firebase needs the pay-as-you-go **Blaze** plan.

1. Left menu, plan name (**Spark**) → **Upgrade** (or **gear icon** → **Usage and billing** →
   **Details & settings** → **Modify plan**) → **Blaze** → add the company card.
2. Set a **budget** of 5 EUR (alerts at 50/90/100%). It warns; it does not stop spending.
3. **Build** (or **Databases & Storage**) → **Storage** → **Get started** → location `europe-north1`
   → **Start in production mode** → **Create**.
4. **Rules** tab → paste → **Publish**; if asked to let Storage read Cloud Firestore → **Grant**.

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /companies/{cid}/{allPaths=**} {
      allow read, write: if request.auth != null
        && firestore.exists(/databases/(default)/documents/companies/$(cid)/members/$(request.auth.uid))
        && (request.resource == null || request.resource.size < 20 * 1024 * 1024);
    }
  }
}
```

EU storage is billed from the first byte (the free allowance covers US locations only); a few GB of
photos is well under 1 EUR a month.

## Keeping it safe

- **Second owner:** **gear icon** → **Users and permissions** → **Add member** → role **Owner**.
- Everyone signs in with **their own** Google account; never share the company account's password.
- The settings file and invite codes are **not passwords** — the rules decide who gets in.
- Someone leaves or loses a phone: remove them in **Settings** → **Crew**; to be sure, also
  **Authentication** → **Users** → their email → **⋮** → **Disable account**.
- Keep saving the app's backup (**Settings** → **Backup**) monthly to the company Drive.

## If something changes

| What changed | What to do |
| --- | --- |
| A menu or button is not where this guide says | Search the console for: **Project settings → Your apps**, **Authentication → Sign-in method**, **Firestore Database → Rules**, **Storage → Rules**. |
| Sign-in fails with "not authorised" / error 10 (new signing key, e.g. Google Play) | Add the new SHA-1 and SHA-256 (**Your apps → MixMaster → Add fingerprint**; Play shows them under **App integrity**), download `google-services.json` again, connect again in the app. |
| Moving to a new Firebase project or Google account | Repeat steps 1–7; in the app **Company sharing → Disconnect**, then **Connect** with the new file; the crew need new invites. |
| The employer changes | Add the new person as **Owner** in Firebase and transfer ownership in the app before the old account is closed. |
| A daily limit was reached | Free plan: 50,000 reads, 20,000 writes, 20,000 deletes a day, 1 GiB stored. Phones keep working offline and catch up the next day. |

## Sources

- [Default bucket and billing requirements for Cloud Storage for Firebase after September 2024](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024)
- [Cloud Firestore locations](https://firebase.google.com/docs/firestore/locations)
- [Manage databases (Standard edition)](https://firebase.google.com/docs/firestore/manage-databases)

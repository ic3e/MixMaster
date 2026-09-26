# MixMaster: running the company

This guide has three parts:

1. **For the employer.** The everyday things: people, codes, permissions. No technical words.
2. **Setting up or moving the server.** Done once, and again only if the company ever moves its
   data. The app carries its own step-by-step guide for this (Settings → Company → *How to set
   up a server*). This part is the same, in more detail.
3. **Handing over.** What the developer passes to the company, so nothing depends on them.

---

# Part 1. For the employer

Everything is in the app: **Settings → Company**.

### Adding someone

1. **People → Add person.**
2. Type their name. Choose **Worker**, or **Owner** for someone who runs the company with you.
3. For a worker, switch on what they may **change**:
   - Products and recipes
   - Projects, floors and rooms
   - Stock counts, deliveries and orders
   - Mixes, notes and tasks

   Everybody can **see** everything. The switches only decide what they can change.
4. **Save and make access code** → **Share**, and send it by WhatsApp, SMS or email.

They install MixMaster, tap **Got an access code?** on the Home screen, paste the code and tap
**Join**.

### Everyday changes

| You want to… | Do this |
|---|---|
| Let someone change more, or less | People → tap their name → change the switches → Save. Their phone gets it within a minute. |
| Help someone with a new or lost phone | People → their name → **New access code**. The old phone is cut off and its company data erased the next time it connects. The new code works on the new phone. |
| Remove someone who has left | People → their name → **Remove from company**. Their phone erases the company's data the next time it connects. |
| See whether a phone is keeping up | People shows when each phone was last in touch. |

A worker's phone that hasn't reached the server for **14 days** locks the company's data until it
does. This is in case a phone is lost or kept away on purpose.

### Moving to another server

If the company's data ever has to live somewhere else (a new website, another Google account):

1. Set up the new server (Part 2). It must be **empty**.
2. Settings → Company → **Move to another server** → enter the new address → **Check** →
   **Move the company here**.
3. Keep the app open until it says it's done.

Everybody's phone follows by itself the next time it connects. **Nobody needs a new code.**

If the old server is already gone (the website closed, or the account lost), the move still works
from your phone's own copy. Everybody else opens Settings → Company → **Company moved to a new
server?** and enters the new address, which you send them. Someone added very recently may need
a new code.

---

# Part 2. Setting up the server

**The app's own guide:** Settings → Company → *How to set up a server*. It sends the server file to
your email, so you always have it, even without this document.

Choose one:

| | **A. Google account** (easiest) | **B. Company website** |
|---|---|---|
| Needs | Any Google account, a plain Gmail is enough | Web hosting that runs PHP (almost all do) |
| Data kept in | A Google Sheet in that account's Drive | A database file on the website |
| Costs | Nothing | Nothing extra |
| Doesn't work on | — | Wix, Squarespace, Webflow, Shopify, free WordPress.com |

## A. Google account

1. In the app, open *How to set up a server* → **Google account** → **Send the server file to
   myself**, and send it to your email.
2. On a computer, sign in to the company's Google account, open **script.google.com** and click
   **New project**.
3. Open the file from your email. Select everything (Ctrl+A) and copy (Ctrl+C). In the project,
   delete what is already there, paste (Ctrl+V) and save.
4. At the top, next to **Run**, pick **`prepare`** from the list and press **Run**.
5. Google asks for permission: **Review permissions** → choose the account. A page says
   *"Google hasn't verified this app"*. This is normal for something you made yourself. Click
   **Advanced** → **Go to … (unsafe)** → **Allow**.
6. **Deploy** → **New deployment** → the gear next to "Select type" → **Web app**.
   Before clicking **Deploy**, check both settings. This is the step that is easiest to miss:
   - Execute as: **Me**
   - Who has access: **Anyone** (plain "Anyone", not "Anyone with a Google account")

   If it was missed, fix it without getting a new address: Deploy → Manage deployments →
   pencil → change it → Deploy.
7. Copy the **Web app URL** (it ends in `/exec`) and send it to your phone.
8. In the app: Settings → Company → *I'm the employer* (or *Move to another server*) → paste it.

The data is kept in a Google Sheet called *MixMaster data*. You can look at it, but don't edit it.
If the script is ever changed, publish it with **Deploy → Manage deployments → pencil → New
version**, so its address stays the same.

## B. Company website

1. In the app, open *How to set up a server* → **Company website** → **Send the website files to
   myself**.
2. On a computer, save the zip from the email and unzip it. Inside is a folder called `mixmaster`.
3. Log in to the website's hosting control panel (where the website and domain are paid for) and
   open the **File Manager**.
4. Open the website's main folder (usually `public_html`, `htdocs` or `www`) and upload the whole
   `mixmaster` folder into it.
5. Open **https://your-website/mixmaster/api.php** in a browser. It should say *"The server is
   running and waiting to be set up."*
6. In the app, enter `your-website/mixmaster` as the server address.

| If the browser shows… | Do this |
|---|---|
| "…cannot open its database" | Ask the host to switch on SQLite for PHP, or use MySQL (`config.sample.php` explains). |
| The code itself, or a download | The hosting doesn't run PHP. Use the Google account instead. |
| A security warning | Switch on the free certificate (Let's Encrypt) in the control panel. |

Backups: the data is the file `mixmaster/data/mixmaster-….sqlite`. Download it now and then.

## Starting the company

When the app sets up the company, it asks how to start:

- **Start with what is on this phone:** the products, recipes, projects and stock go up. To
  start from an older backup, restore it first (Settings → Backup → Restore).
- **Start empty:** the phone is emptied first.

---

# Part 3. Handing over (developer → company)

When the app is handed over, nothing may depend on the developer. Check each of these:

1. **The server is in the company's own account.** That means their Google account, or their
   website hosting. If it was set up in the developer's account, move it (Part 1, *Moving to
   another server*).
2. **The employer is an Owner**, joined on their own phone. The developer is then removed from
   People (or kept, if the company wants).
3. **Passwords stay with the company.** Google account, hosting, email. Never send them in a chat.
4. **The app's source code and signing key.** Transfer the GitHub repository (`ic3e/MixMaster`)
   to a GitHub account the company owns, or give them a full copy. The signing key is
   `keystore/debug.keystore` inside it. A new version of the app can only be installed over the
   old one if it is signed with the **same key**. Without it, every phone would have to uninstall
   and start again.
5. **App updates.** The app looks for new versions at
   `raw.githubusercontent.com/ic3e/MixMaster/main/dist/latest.json`. If the repository moves to
   another name, the next version must point at the new place (`data/update/AppUpdates.kt`).
   Until then, updates are installed by hand from the APK.
6. **The server files** are inside the app (the guide sends them) and in the repository under
   `server/`. The company doesn't need anything else to set up or move a server.

---

## When something is wrong

| The app says | What it means |
|---|---|
| Couldn't reach the server | No internet, or a typo in the address. |
| …not as a MixMaster server | Something answered, but not the server. On Google, check the deployment is set to *Anyone* and the address ends in `/exec`. |
| This server already has a company set up | It has been set up already. Ask its owner for a code. A move needs an empty server. |
| That code doesn't work | Used already, or replaced by a newer one. Make a new code. |
| Too many wrong codes | The server stops accepting codes for an hour after 30 wrong ones. |
| The company has moved to a new server | Enter the new address under *Company moved to a new server?*. |
| The new server doesn't know this phone | Someone added just before a move with the old server gone. Give them a new code. |
| Connect to the internet (whole screen) | A worker's phone hasn't reached the server for 14 days. It opens as soon as it does, or when the new address is entered. |

---

For developers: the protocol is one address, JSON in and out (`hello`, `setup`, `join`, `pull`,
`push`, `people`, `person_save`, `person_code`, `person_remove`, `leave`, and for moving:
`export`, `adopt`, `move_out`, `move_done`). Both servers are held to the same conversation by
`node server/test/run.mjs`.

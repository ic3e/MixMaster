# MixMaster company server: setup guide

One company's data (products, recipes, projects, stock) shared by the employer's phone and the
crew's phones, kept on a server the company owns. Nobody else is in between, and there is no
subscription.

The server is set up once, by the employer (or whoever looks after the app). It takes about
15 minutes. After that everything is done from the app.

---

## 1. Pick where the data lives

| | **A. Company website** | **B. Google account** |
|---|---|---|
| Needs | Web hosting that runs PHP (almost all do) | Any Google account, a plain Gmail is enough |
| Data kept in | A database file on the website | A Google Sheet in that account's Drive |
| Costs | Nothing extra: the hosting is already paid | Nothing |
| Doesn't work on | Wix, Squarespace, Webflow, Shopify, free WordPress.com | — |

Both work the same way in the app, and the company can move from one to the other later
(see section 6). If you can't tell what the website runs on, open
**hostingchecker.com** and **builtwith.com** and type in the website's address.

---

## 2A. Company website

**What you need:** the login to the website's hosting control panel (Zone, Veebimajutus,
Hostinger, cPanel, Plesk…) and the `mixmaster` folder from `server/website/`.

1. Log in to the hosting control panel and open the **File Manager** (or connect with FTP,
   for example with FileZilla).
2. Go into the website's own folder. It is usually called `public_html`, `htdocs` or `www`.
3. Upload the whole **`mixmaster`** folder there, so that the file ends up at
   `public_html/mixmaster/api.php`. Upload the `data` folder that is inside it too.
4. Test it: open **https://your-domain/mixmaster/api.php** in a browser. It should say:
   > **MixMaster** — The server is running and waiting to be set up.

   | If you see… | Do this |
   |---|---|
   | "…cannot open its database" | Ask the host to switch on SQLite for PHP (`pdo_sqlite`), or use MySQL (below). |
   | The PHP code itself, or a download | The hosting does not run PHP. Use option B. |
   | A security warning, or http only | Switch on the free SSL certificate (Let's Encrypt) in the control panel. |
   | "Not found" | The folder went somewhere else. Check the address matches where `api.php` is. |

5. On the **employer's phone**: MixMaster → Settings → **Company** →
   *I'm the employer — set up the company* → **Company website** → type `your-domain/mixmaster`
   → **Check server** → fill in the company name and your name → choose how to start → **Set up
   company**.

**Only if the host has no SQLite:** create a MySQL database in the control panel, copy
`config.sample.php` to `config.php` in the same folder, fill in the database name, user and
password, and upload it.

**Backups:** the data is the file `mixmaster/data/mixmaster-….sqlite`. Download it with the
File Manager now and then. The employer's phone can also make a backup (Settings → Backup).

---

## 2B. Google account

**What you need:** the company's Google account, a computer, and the file `server/google/Code.gs`.

1. Sign in to the company's Google account and go to **script.google.com**.
2. **New project**. Click "Untitled project" at the top and call it `MixMaster server`.
3. Delete everything in the editor, paste in the **whole** of `Code.gs`, and save (Ctrl+S).
4. In the bar above the code, pick **`prepare`** from the function list and press **Run**.
   Google asks for permission:
   **Review permissions** → pick the account → *"Google hasn't verified this app"* →
   **Advanced** → **Go to MixMaster server (unsafe)** → **Allow**.
   This warning comes up for any script you write yourself. It only lets the script keep its
   own sheet. When it finishes, the log says **Ready**, and a sheet called *MixMaster data*
   appears in Drive.
5. **Deploy** → **New deployment** → the gear icon → **Web app**.
   - Description: `MixMaster`
   - Execute as: **Me**
   - Who has access: **Anyone**

   → **Deploy** → copy the **Web app URL** (it ends in `/exec`).
   "Anyone" means anyone can reach the address, the same as a website. Only phones holding an
   access code get anything back.
6. On the **employer's phone**: Settings → **Company** → *I'm the employer…* → **Google account**
   → paste the URL → **Check server** → company name, your name → **Set up company**.

**Changing the script later:** Deploy → **Manage deployments** → the pencil → Version:
**New version** → Deploy. The address stays the same. (A *new deployment* would get a new
address, and the phones would stop reaching it.)

**The sheet:** you can open it to see the people and their codes, but don't edit it by hand. The
app is what keeps it.

---

## 3. Starting the company

When the company is set up, the app asks how to start:

- **Start with what is on this phone:** the products, recipes, projects and stock on the
  employer's phone go up to the server. To start from an older backup, restore it first
  (Settings → Backup → Restore), then set up.
- **Start empty:** the phone is emptied first and the company starts with nothing.

---

## 4. Adding people

Settings → Company → **People** → **Add person**:

1. Name, and **Worker** or **Owner**. Owners can change everything and manage people.
2. For a worker, choose what they may change:
   - Products and recipes
   - Projects, floors and rooms
   - Stock counts, deliveries and orders
   - Mixes, notes and tasks

   Everybody in the company can **see** everything. These switches only say what they can
   **change**. The server checks every change, whatever the phone shows.
3. **Save and make access code** → **Share** (WhatsApp, SMS, email).

The worker installs MixMaster, then goes to **Home → Got an access code?** (or Settings →
Company), pastes the code and taps **Join**. Their phone's own data makes way for the company's.

- A code works **once, on one phone**.
- **Changing permissions:** open the person and change the switches. Their phone gets the change
  the next time it is in touch with the server (within half a minute while the app is open).
- **Lost or new phone:** open the person → **New access code**. The old phone is cut off, and
  the company's data is erased from it when it next connects. The new code works on the new phone.
- **Someone leaves:** open the person → **Remove from company**. Their phone is erased the next
  time it connects. A phone that never connects again is locked after **14 days** without
  reaching the server.
- A worker can also leave by themselves (Company → Leave company). That erases the company's data
  from their phone.
- Workers can't make backups of the company's data.

---

## 5. Handing it over (developer → company)

Whoever sets the server up first doesn't have to stay in charge:

1. If possible, set the server up on the **company's own** website or Google account from the
   start, not on a personal one.
2. In People, add the employer as an **Owner** and give them the code. They join on their phone.
3. The employer can then remove the developer, or keep them in (as a worker or an owner).
4. Keep the hosting and Google passwords with the company. Don't send them in chat messages.

---

## 6. Moving the server

For example, from the developer's Google account to the company's website:

1. Put the new server up (2A or 2B).
2. On the employer's phone: Company → **Disconnect this phone**. The data stays on the phone.
3. Company → set up with the **new** address and choose **Start with what is on this phone**.
4. Give everybody a new code. Their old codes pointed at the old server.
5. Delete the old server (the old folder, or the old script and sheet).

---

## 7. What is shared

| Shared | Not shared (yet) |
|---|---|
| Products and their packs | Photos and blueprint files (they stay on the phone that took them) |
| Recipes and their coats | Language, theme, alarm sound |
| Projects, floors, rooms and the coats on them | The stock-count reminder settings |
| Tasks, notes and recorded mixes | |
| Stock, deliveries and orders | |
| The crew list | |

---

## 8. When something is wrong

| The app says | What it means |
|---|---|
| Couldn't reach the server | No internet, or a typo in the address. |
| …not as a MixMaster server | Something answered at that address, but it isn't `api.php` (or, on Google, the deployment isn't set to *Anyone*). |
| This server already has a company set up | It has been set up once already. Ask its owner for a code. |
| That code doesn't work | Used already, or replaced by a newer one. Make a new code. |
| Too many wrong codes | The server stops accepting codes for an hour after 30 wrong ones. |
| The server now holds a different company | The server was set up again from scratch. Nothing was deleted from the phone. Set up or join again. |
| Connect to the internet (whole screen) | A worker's phone hasn't reached the server for 14 days. It opens again as soon as it does. |

---

For developers: the protocol is one address, JSON in and out (`hello`, `setup`, `join`, `pull`,
`push`, `people`, `person_save`, `person_code`, `person_remove`, `leave`). Both servers are
checked against the same conversation by `node server/test/run.mjs`.

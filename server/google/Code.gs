/*
 * MixMaster company server: the Google version.
 *
 * Runs free under the company's own Google account (a plain Gmail account is enough) as an Apps
 * Script web app, and keeps everything in a Google Sheet in that account's Drive: one tab listing
 * the people and their access codes, one holding the data. Nothing to fill in here — paste this
 * whole file into a new Apps Script project, deploy it as a web app, and give the app the address.
 * The setup guide (docs/company-server.md) walks through it.
 *
 * The website version (server/website/mixmaster/api.php) speaks exactly the same language; the app
 * does not care which one it is talking to, and a company can move from one to the other.
 *
 * Please do not edit the sheet by hand. It is laid out to be readable, but the app is what keeps it.
 */

const MM_PROTOCOL = 1;
const MM_PAGE = 500;
const MM_MAX_PUSH = 1000;
const MM_CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const MM_JOIN_TRIES_PER_HOUR = 30;
// A sheet cell holds 50,000 characters; a row's data is spread over this many of them.
const MM_CHUNK = 45000;
const MM_CHUNKS = 8;
const MM_ROW_WIDTH = 3 + MM_CHUNKS;
const MM_PEOPLE_WIDTH = 6;

/**
 * Who may change what. A table the app adds later that is not on this list is the owner's to
 * change until the server is updated, and everybody can still read it.
 */
const MM_GROUPS = {
  products: 'catalogue',
  solutions: 'catalogue',
  solution_lines: 'catalogue',
  projects: 'projects',
  floors: 'projects',
  room_areas: 'projects',
  room_layers: 'projects',
  stock: 'warehouse',
  deliveries: 'warehouse',
  tasks: 'site',
  notes: 'site',
  material_uses: 'site',
  usage_logs: 'site',
};
const MM_PERMS = ['catalogue', 'projects', 'warehouse', 'site'];

function mmGroup(table) {
  return Object.prototype.hasOwnProperty.call(MM_GROUPS, table) ? MM_GROUPS[table] : 'owner';
}

/** A new worker: can count the shed and record site work, cannot change the catalogue or plans. */
function mmDefaultPerms() {
  return { catalogue: false, projects: false, warehouse: true, site: true };
}

class MmError extends Error {
  constructor(code) {
    super(code);
    this.code = code;
  }
}

function mmFail(code) {
  throw new MmError(code);
}

// ---- Storage ----------------------------------------------------------------------------------

function mmProps() {
  return PropertiesService.getScriptProperties();
}

function mmMetaGet(key, fallback) {
  const value = mmProps().getProperty(key);
  return value === null || value === undefined ? fallback : value;
}

function mmMetaSet(key, value) {
  mmProps().setProperty(key, String(value));
}

function mmCompany() {
  const id = mmMetaGet('company_id', null);
  if (id === null) return null;
  return { id: id, name: mmMetaGet('company_name', '') };
}

/** The company's sheet, made in the account's Drive the first time it is needed. */
function mmBook() {
  const id = mmMetaGet('sheet_id', null);
  if (id) {
    try {
      return SpreadsheetApp.openById(id);
    } catch (e) {
      // Deleted from Drive by hand: a new one is made below, and the company starts again.
    }
  }
  const book = SpreadsheetApp.create('MixMaster data');
  const people = book.getSheets()[0];
  people.setName('People');
  people.getRange(1, 1, 1, MM_PEOPLE_WIDTH).setValues([['Name', 'Role', 'Status', 'Access code', 'Last seen', 'Kept by the app']]);
  people.getRange('A:F').setNumberFormat('@');
  people.setFrozenRows(1);
  const rows = book.insertSheet('Data');
  const head = ['Row', 'Change', 'About'];
  for (let i = 1; i <= MM_CHUNKS; i++) head.push('Data ' + i);
  rows.getRange(1, 1, 1, MM_ROW_WIDTH).setValues([head]);
  rows.getRange(1, 1, rows.getMaxRows(), MM_ROW_WIDTH).setNumberFormat('@');
  rows.setFrozenRows(1);
  mmMetaSet('sheet_id', book.getId());
  return book;
}

function mmSheet(name) {
  return mmBook().getSheetByName(name);
}

/** Every write, and every read that has to agree with one, waits its turn. */
function mmLocked(work) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const result = work();
    SpreadsheetApp.flush();
    return result;
  } finally {
    lock.releaseLock();
  }
}

// ---- People -----------------------------------------------------------------------------------

function mmHex(bytes) {
  return bytes.map(function (b) { return ((b + 256) % 256).toString(16).padStart(2, '0'); }).join('');
}

function mmHash(text) {
  return mmHex(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8));
}

function mmRandomBytes() {
  return Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256,
    Utilities.getUuid() + Utilities.getUuid() + Date.now(),
    Utilities.Charset.UTF_8
  );
}

function mmNewToken() {
  return mmHex(mmRandomBytes()).slice(0, 48);
}

function mmNewCode() {
  const bytes = mmRandomBytes();
  let code = '';
  for (let i = 0; i < 8; i++) code += MM_CODE_ALPHABET[((bytes[i] + 256) % 256) % 32];
  return code;
}

/** What people type is forgiving: spaces, dashes and small letters are all fine. */
function mmCleanCode(code) {
  return String(code || '').replace(/[^0-9A-Za-z]/g, '').toUpperCase();
}

function mmCleanName(name) {
  const clean = String(name || '').replace(/\s+/g, ' ').trim();
  if (!clean) mmFail('bad_request');
  return clean.slice(0, 80);
}

function mmCleanPerms(perms) {
  const out = mmDefaultPerms();
  if (perms && typeof perms === 'object') {
    MM_PERMS.forEach(function (name) {
      if (Object.prototype.hasOwnProperty.call(perms, name)) out[name] = Boolean(perms[name]);
    });
  }
  return out;
}

function mmCleanDevice(device) {
  return String(device || '').replace(/[^0-9A-Za-z_-]/g, '').slice(0, 64);
}

/** A cell the sheet will not take for a formula. */
function mmShown(text) {
  return String(text === null || text === undefined ? '' : text).replace(/^[=+\-@]+/, '');
}

function mmPeopleAll() {
  const sheet = mmSheet('People');
  const last = sheet.getLastRow();
  if (last < 2) return [];
  return sheet.getRange(2, 1, last - 1, MM_PEOPLE_WIDTH).getValues()
    .map(function (row, index) {
      if (!row[5]) return null;
      const person = JSON.parse(String(row[5]));
      person._row = index + 2;
      return person;
    })
    .filter(function (person) { return person !== null; });
}

function mmPeopleRow(person) {
  const status = person.status === 'pending' ? 'Waiting for the code to be used'
    : person.status === 'active' ? 'Connected' : 'Left';
  const code = person.status === 'pending' && person.code ? person.code.slice(0, 4) + '-' + person.code.slice(4) : '';
  const seen = person.seen ? new Date(person.seen).toISOString().replace('T', ' ').slice(0, 16) : '';
  const kept = Object.assign({}, person);
  delete kept._row;
  return [mmShown(person.name), person.owner ? 'Owner' : 'Worker', status, code, seen, JSON.stringify(kept)];
}

function mmPersonWrite(person) {
  const sheet = mmSheet('People');
  if (!person._row) person._row = Math.max(sheet.getLastRow(), 1) + 1;
  sheet.getRange(person._row, 1, 1, MM_PEOPLE_WIDTH).setValues([mmPeopleRow(person)]);
}

function mmPersonDelete(person) {
  mmSheet('People').deleteRow(person._row);
}

function mmNextPersonId(people) {
  const next = Number(mmMetaGet('next_person', '0')) + 1;
  const top = people.reduce(function (max, p) { return Math.max(max, p.id); }, 0);
  const id = Math.max(next, top + 1);
  mmMetaSet('next_person', id);
  return id;
}

function mmPermsOf(person) {
  const perms = person.perms || mmDefaultPerms();
  const out = {};
  MM_PERMS.forEach(function (name) { out[name] = Boolean(person.owner) || Boolean(perms[name]); });
  return out;
}

function mmMe(person) {
  return { id: person.id, name: person.name, owner: Boolean(person.owner), perms: mmPermsOf(person) };
}

function mmPersonOut(person) {
  return {
    id: person.id,
    name: person.name,
    owner: Boolean(person.owner),
    perms: mmPermsOf(person),
    status: person.status,
    code: person.status === 'pending' ? person.code : null,
    joined: person.joined || null,
    seen: person.seen || null,
  };
}

function mmPeopleList() {
  return mmPeopleAll()
    .sort(function (a, b) {
      if (Boolean(a.owner) !== Boolean(b.owner)) return a.owner ? -1 : 1;
      const byName = a.name.toLowerCase().localeCompare(b.name.toLowerCase());
      return byName !== 0 ? byName : a.id - b.id;
    })
    .map(mmPersonOut);
}

/**
 * The phone asking, from its token. Anything that no longer matches is told "revoked", and the
 * app takes that as the order to empty itself — unless the server now holds a different company
 * altogether (set up again from scratch), which is a mistake to report, not a sacking.
 */
function mmAuth(req) {
  const company = mmCompany();
  if (company === null) mmFail('other_company');
  if (req.company && req.company !== company.id) mmFail('other_company');
  const token = String(req.token || '');
  if (!token) mmFail('revoked');
  const hash = mmHash(token);
  const person = mmPeopleAll().filter(function (p) { return p.token_hash === hash; })[0];
  if (!person || person.status !== 'active') mmFail('revoked');
  const now = Date.now();
  if (!person.seen || now - person.seen > 60000) {
    person.seen = now;
    mmPersonWrite(person);
  }
  return person;
}

function mmRequireOwner(person) {
  if (!person.owner) mmFail('not_allowed');
}

// ---- Rows ---------------------------------------------------------------------------------------

function mmRowFromCells(cells) {
  const about = JSON.parse(String(cells[2]));
  let data = '';
  for (let i = 3; i < MM_ROW_WIDTH; i++) data += String(cells[i] || '');
  return {
    t: about.t,
    id: about.id,
    d: about.x ? null : data,
    x: Boolean(about.x),
    dev: about.dev || null,
    seq: Number(cells[1]),
  };
}

function mmCellsFromRow(row) {
  const cells = [row.t + '/' + row.id, String(row.seq), JSON.stringify({
    t: row.t, id: row.id, x: row.x, dev: row.dev, p: row.p, at: row.at,
  })];
  const data = row.x ? '' : row.d;
  for (let i = 0; i < MM_CHUNKS; i++) cells.push(data.slice(i * MM_CHUNK, (i + 1) * MM_CHUNK));
  return cells;
}

function mmRowOut(row) {
  return { t: row.t, id: row.id, d: row.x ? null : row.d, x: row.x, dev: row.dev };
}

// ---- Actions ----------------------------------------------------------------------------------

function mmHello() {
  const company = mmCompany();
  return {
    ok: true,
    app: 'mixmaster',
    protocol: MM_PROTOCOL,
    kind: 'google',
    claimed: company !== null,
    company: company,
    moved_to: mmMetaGet('moved_to', null),
  };
}

/**
 * The company has moved to another server: nothing more is taken here, and every phone that asks
 * is told where it went. Reading stays open, so the owner moving it can take the last of it along.
 */
function mmRefuseIfMoved() {
  if (mmMetaGet('moved_to', null) !== null) mmFail('moved');
}

/** Half way through arriving from another server: the owner fills it first, everybody else waits. */
function mmRefuseWhileArriving(person) {
  if (mmMetaGet('moving', '0') === '1' && !person.owner) mmFail('busy');
}

function mmSetup(req) {
  const companyName = mmCleanName(req.company_name);
  const name = mmCleanName(req.name);
  const device = mmCleanDevice(req.device);
  return mmLocked(function () {
    if (mmCompany() !== null) mmFail('claimed');
    // A fresh company starts from an empty sheet: nothing a previous one left behind is carried in.
    ['People', 'Data'].forEach(function (name) {
      const sheet = mmSheet(name);
      if (sheet.getLastRow() > 1) sheet.deleteRows(2, sheet.getLastRow() - 1);
    });
    mmProps().deleteProperty('next_person');
    const companyId = mmHex(mmRandomBytes()).slice(0, 16);
    const token = mmNewToken();
    const now = Date.now();
    const all = {};
    MM_PERMS.forEach(function (p) { all[p] = true; });
    mmBook();
    const person = {
      id: mmNextPersonId([]),
      name: name,
      owner: true,
      perms: all,
      status: 'active',
      code: null,
      token_hash: mmHash(token),
      device: device,
      created: now,
      joined: now,
      seen: now,
    };
    mmPersonWrite(person);
    mmMetaSet('company_id', companyId);
    mmMetaSet('company_name', companyName);
    mmMetaSet('created', now);
    mmMetaSet('seq', 0);
    mmBook().rename('MixMaster data – ' + companyName);
    return { ok: true, token: token, company: mmCompany(), me: mmMe(person) };
  });
}

function mmJoin(req) {
  const code = mmCleanCode(req.code);
  const device = mmCleanDevice(req.device);
  return mmLocked(function () {
    if (mmCompany() === null) mmFail('not_claimed');
    mmRefuseIfMoved();
    // Guessing codes is made slow: a few dozen wrong ones an hour, then nothing until the next.
    const hour = Math.floor(Date.now() / 3600000);
    const tries = String(mmMetaGet('join_fails', '0:0')).split(':');
    const fails = Number(tries[0]) === hour ? Number(tries[1] || 0) : 0;
    if (fails >= MM_JOIN_TRIES_PER_HOUR) return { ok: false, error: 'too_many' };
    const person = code.length === 8
      ? mmPeopleAll().filter(function (p) { return p.status === 'pending' && p.code === code; })[0]
      : undefined;
    if (!person) {
      mmMetaSet('join_fails', hour + ':' + (fails + 1));
      return { ok: false, error: 'bad_code' };
    }
    const token = mmNewToken();
    const now = Date.now();
    person.status = 'active';
    person.code = null;
    person.token_hash = mmHash(token);
    person.device = device;
    person.joined = now;
    person.seen = now;
    mmPersonWrite(person);
    return { ok: true, token: token, company: mmCompany(), me: mmMe(person) };
  });
}

function mmPull(req) {
  return mmLocked(function () {
    const person = mmAuth(req);
    const since = Number(req.since || 0);
    const top = Number(mmMetaGet('seq', '0'));
    let rows = [];
    let more = false;
    let next = since;
    // Most calls are a phone asking whether anything is new, and most of the time nothing is.
    if (top > since) {
      const sheet = mmSheet('Data');
      const last = sheet.getLastRow();
      if (last >= 2) {
        rows = sheet.getRange(2, 1, last - 1, MM_ROW_WIDTH).getValues()
          .filter(function (cells) { return cells[0] !== '' && Number(cells[1]) > since; })
          .map(mmRowFromCells)
          .sort(function (a, b) { return a.seq - b.seq; });
      }
      more = rows.length > MM_PAGE;
      if (more) rows = rows.slice(0, MM_PAGE);
      rows.forEach(function (row) { next = Math.max(next, row.seq); });
    }
    return {
      ok: true,
      company: mmCompany(),
      me: mmMe(person),
      rows: rows.map(mmRowOut),
      next: next,
      more: more,
      moved_to: mmMetaGet('moved_to', null),
    };
  });
}

function mmMayWrite(person, table) {
  if (person.owner) return true;
  const group = mmGroup(table);
  if (group === 'owner') return false;
  return Boolean(mmPermsOf(person)[group]);
}

function mmPush(req) {
  const rows = Array.isArray(req.rows) ? req.rows : [];
  if (rows.length > MM_MAX_PUSH) mmFail('bad_request');
  const device = mmCleanDevice(req.device);
  return mmLocked(function () {
    const person = mmAuth(req);
    mmRefuseIfMoved();
    mmRefuseWhileArriving(person);
    const sheet = mmSheet('Data');
    const last = sheet.getLastRow();
    const count = Math.max(last - 1, 0);
    // Read once, changed in memory, written back in as few calls as a sheet allows.
    const block = count > 0 ? sheet.getRange(2, 1, count, MM_ROW_WIDTH).getValues() : [];
    const where = {};
    block.forEach(function (cells, index) { if (cells[0] !== '') where[String(cells[0])] = index; });
    const refused = [];
    const changed = {};
    let seq = Number(mmMetaGet('seq', '0'));
    const now = Date.now();
    rows.forEach(function (row) {
      if (!row || typeof row !== 'object') return;
      const table = String(row.t || '');
      const id = String(row.id === undefined || row.id === null ? '' : row.id);
      if (!/^[a-z][a-z0-9_]{0,39}$/.test(table) || !/^-?[0-9]{1,20}$/.test(id)) return;
      const deleted = Boolean(row.x);
      const data = deleted ? null : (typeof row.d === 'string' ? row.d : null);
      if (!deleted && (data === null || data.length > MM_CHUNK * MM_CHUNKS)) return;
      const key = table + '/' + id;
      const at = where[key];
      if (!mmMayWrite(person, table)) {
        // Not theirs to change: the company's own copy goes back, and the phone puts it back.
        refused.push(at !== undefined
          ? mmRowOut(mmRowFromCells(block[at]))
          : { t: table, id: id, d: null, x: true, dev: null });
        return;
      }
      seq += 1;
      const cells = mmCellsFromRow({ t: table, id: id, d: data, x: deleted, dev: device, p: person.id, at: now, seq: seq });
      if (at === undefined) {
        where[key] = block.length;
        block.push(cells);
      } else {
        block[at] = cells;
        if (at < count) changed[at] = true;
      }
    });
    const added = block.slice(count);
    const changedRows = Object.keys(changed).map(Number);
    if (changedRows.length > 20) {
      sheet.getRange(2, 1, count, MM_ROW_WIDTH).setValues(block.slice(0, count));
    } else {
      changedRows.forEach(function (index) {
        sheet.getRange(index + 2, 1, 1, MM_ROW_WIDTH).setValues([block[index]]);
      });
    }
    if (added.length > 0) {
      const at = count + 2;
      const need = at + added.length - 1;
      if (need > sheet.getMaxRows()) sheet.insertRowsAfter(sheet.getMaxRows(), need - sheet.getMaxRows());
      const range = sheet.getRange(at, 1, added.length, MM_ROW_WIDTH);
      range.setNumberFormat('@');
      range.setValues(added);
    }
    mmMetaSet('seq', seq);
    return { ok: true, company: mmCompany(), me: mmMe(person), refused: refused };
  });
}

function mmPeople(req) {
  return mmLocked(function () {
    const person = mmAuth(req);
    mmRequireOwner(person);
    return { ok: true, me: mmMe(person), people: mmPeopleList() };
  });
}

function mmPersonSave(req) {
  const input = req.person && typeof req.person === 'object' ? req.person : {};
  const name = mmCleanName(input.name);
  const owner = Boolean(input.owner);
  const perms = mmCleanPerms(input.perms);
  const id = Number(input.id || 0);
  return mmLocked(function () {
    const me = mmAuth(req);
    mmRequireOwner(me);
    const people = mmPeopleAll();
    let person;
    if (id > 0) {
      person = people.filter(function (p) { return p.id === id; })[0];
      if (!person) mmFail('not_found');
      person.name = name;
      // Nobody takes their own keys away by accident: another owner has to do it.
      person.owner = person.id === me.id ? true : owner;
      person.perms = perms;
    } else {
      person = {
        id: mmNextPersonId(people),
        name: name,
        owner: owner,
        perms: perms,
        status: 'pending',
        code: mmNewCode(),
        token_hash: null,
        device: null,
        created: Date.now(),
        joined: null,
        seen: null,
      };
    }
    mmPersonWrite(person);
    return { ok: true, person: mmPersonOut(person), people: mmPeopleList() };
  });
}

/** A fresh code for someone: whatever phone they had is cut off, and the new code gets them back in. */
function mmPersonCode(req) {
  const id = Number(req.id || 0);
  return mmLocked(function () {
    const me = mmAuth(req);
    mmRequireOwner(me);
    if (id === me.id) mmFail('not_allowed');
    const person = mmPeopleAll().filter(function (p) { return p.id === id; })[0];
    if (!person) mmFail('not_found');
    person.status = 'pending';
    person.code = mmNewCode();
    person.token_hash = null;
    person.device = null;
    mmPersonWrite(person);
    return { ok: true, person: mmPersonOut(person), people: mmPeopleList() };
  });
}

function mmPersonRemove(req) {
  const id = Number(req.id || 0);
  return mmLocked(function () {
    const me = mmAuth(req);
    mmRequireOwner(me);
    if (id === me.id) mmFail('not_allowed');
    const person = mmPeopleAll().filter(function (p) { return p.id === id; })[0];
    if (person) mmPersonDelete(person);
    return { ok: true, people: mmPeopleList() };
  });
}

/** A phone giving its place back. The last owner cannot: the company would have nobody to run it. */
function mmLeave(req) {
  return mmLocked(function () {
    const me = mmAuth(req);
    if (me.owner) {
      const owners = mmPeopleAll().filter(function (p) { return p.owner && p.status === 'active'; }).length;
      if (owners <= 1) mmFail('last_owner');
    }
    me.status = 'left';
    me.code = null;
    me.token_hash = null;
    me.device = null;
    mmPersonWrite(me);
    return { ok: true };
  });
}

// ---- Moving the company to another server ------------------------------------------------------

/** Everything about the people a new server needs to let the same phones in with the same keys. */
function mmExport(req) {
  return mmLocked(function () {
    const me = mmAuth(req);
    mmRequireOwner(me);
    const people = mmPeopleAll()
      .sort(function (a, b) { return a.id - b.id; })
      .map(function (p) {
        return {
          id: p.id,
          name: p.name,
          owner: Boolean(p.owner),
          perms: mmPermsOf(p),
          status: p.status,
          code: p.code || null,
          token_hash: p.token_hash || null,
          device: p.device || null,
          created: p.created || null,
          joined: p.joined || null,
          seen: p.seen || null,
        };
      });
    return { ok: true, company: mmCompany(), people: people };
  });
}

/**
 * An empty server taking in a company from another one: the same company, the same people, the
 * same keys — so every phone carries on without a new code. The phone doing it has to be one of
 * the owners on the list it brings. The data itself follows as ordinary changes.
 */
function mmAdopt(req) {
  const input = req.company && typeof req.company === 'object' ? req.company : {};
  const companyId = String(input.id || '');
  const companyName = mmCleanName(input.name);
  if (!/^[0-9a-f]{8,64}$/.test(companyId)) mmFail('bad_request');
  const mine = req.token ? mmHash(String(req.token)) : '';
  let ownerFound = false;
  const list = Array.isArray(req.people) ? req.people : [];
  const people = list.map(function (p) {
    if (!p || typeof p !== 'object' || !(Number(p.id) > 0)) mmFail('bad_request');
    const status = String(p.status || '');
    if (['pending', 'active', 'left'].indexOf(status) < 0) mmFail('bad_request');
    const hash = /^[0-9a-f]{64}$/.test(String(p.token_hash || '')) ? String(p.token_hash) : null;
    const code = /^[0-9A-Z]{8}$/.test(String(p.code || '')) ? String(p.code) : null;
    const owner = Boolean(p.owner);
    if (owner && hash !== null && hash === mine && status === 'active') ownerFound = true;
    return {
      id: Number(p.id),
      name: mmCleanName(p.name),
      owner: owner,
      perms: mmCleanPerms(p.perms),
      status: status,
      code: status === 'pending' ? code : null,
      token_hash: status === 'active' ? hash : null,
      device: mmCleanDevice(p.device),
      created: Number(p.created || Date.now()),
      joined: p.joined ? Number(p.joined) : null,
      seen: p.seen ? Number(p.seen) : null,
    };
  });
  if (!ownerFound) mmFail('not_allowed');
  return mmLocked(function () {
    if (mmCompany() !== null) mmFail('claimed');
    ['People', 'Data'].forEach(function (name) {
      const sheet = mmSheet(name);
      if (sheet.getLastRow() > 1) sheet.deleteRows(2, sheet.getLastRow() - 1);
    });
    people.forEach(function (person) { mmPersonWrite(person); });
    const top = people.reduce(function (max, p) { return Math.max(max, p.id); }, 0);
    mmMetaSet('next_person', top);
    mmMetaSet('company_id', companyId);
    mmMetaSet('company_name', companyName);
    mmMetaSet('created', Date.now());
    mmMetaSet('seq', 0);
    mmMetaSet('moving', '1');
    mmProps().deleteProperty('moved_to');
    mmBook().rename('MixMaster data – ' + companyName);
    return { ok: true, company: mmCompany() };
  });
}

/** The owner's word that everything has arrived: the rest of the crew can write again. */
function mmMoveDone(req) {
  return mmLocked(function () {
    const me = mmAuth(req);
    mmRequireOwner(me);
    mmMetaSet('moving', '0');
    return { ok: true };
  });
}

/**
 * Closes this server for writing and points every phone at the new one. Given an empty address it
 * opens again — for a move that could not be finished.
 */
function mmMoveOut(req) {
  return mmLocked(function () {
    const me = mmAuth(req);
    mmRequireOwner(me);
    const to = String(req.to || '').trim();
    if (!to) {
      mmProps().deleteProperty('moved_to');
      return { ok: true };
    }
    if (!/^https:\/\/\S+$/.test(to) || to.length > 500) mmFail('bad_request');
    mmMetaSet('moved_to', to);
    return { ok: true };
  });
}

function mmHandle(req) {
  switch (String(req.a || '')) {
    case 'hello': return mmHello(req);
    case 'setup': return mmSetup(req);
    case 'join': return mmJoin(req);
    case 'pull': return mmPull(req);
    case 'push': return mmPush(req);
    case 'people': return mmPeople(req);
    case 'person_save': return mmPersonSave(req);
    case 'person_code': return mmPersonCode(req);
    case 'person_remove': return mmPersonRemove(req);
    case 'leave': return mmLeave(req);
    case 'export': return mmExport(req);
    case 'adopt': return mmAdopt(req);
    case 'move_out': return mmMoveOut(req);
    case 'move_done': return mmMoveDone(req);
  }
  return mmFail('bad_request');
}

// ---- The door ---------------------------------------------------------------------------------

function mmJson(out) {
  return ContentService.createTextOutput(JSON.stringify(out)).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  let req;
  try {
    req = JSON.parse(e && e.postData ? e.postData.contents : '');
  } catch (err) {
    return mmJson({ ok: false, error: 'bad_request' });
  }
  if (!req || typeof req !== 'object') return mmJson({ ok: false, error: 'bad_request' });
  try {
    return mmJson(mmHandle(req));
  } catch (err) {
    if (err instanceof MmError) {
      const out = { ok: false, error: err.code };
      if (err.code === 'moved') out.to = mmMetaGet('moved_to', null);
      return mmJson(out);
    }
    console.error(err && err.stack ? err.stack : err);
    return mmJson({ ok: false, error: 'server' });
  }
}

function doGet() {
  const company = mmCompany();
  const state = company === null
    ? 'The server is running and waiting to be set up. Open MixMaster on the employer\'s phone: Settings → Company → Set up.'
    : 'The server is running and set up for ' + mmShown(company.name).replace(/[<>&"]/g, '') + '.';
  return HtmlService.createHtmlOutput(
    '<div style="font-family:sans-serif;max-width:32em;margin:3em auto;padding:0 1em">' +
    '<h1>MixMaster</h1><p>' + state + '</p></div>'
  ).setTitle('MixMaster server');
}

/**
 * Run this once from the editor (select it, press Run) before deploying: it asks Google for the
 * permission to keep the company's sheet, and makes the sheet, so the first phone is not the one
 * left waiting for either.
 */
function prepare() {
  mmBook();
  console.log('Ready. The data will be kept in: ' + mmBook().getUrl());
}

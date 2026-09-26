// Runs the same conversation against both servers, so the two cannot drift apart.
//
//   node server/test/run.mjs            both
//   node server/test/run.mjs website    the PHP one only (needs php with pdo_sqlite)
//   node server/test/run.mjs google     the Apps Script one only, against a stand-in for Google
//
// The Google one runs in a sandbox with a small imitation of SpreadsheetApp and friends. It keeps
// what matters about the real thing: a cell holds 50,000 characters at most, a range cannot reach
// past the sheet's last row, and a number-looking string in a cell not set to plain text comes
// back as a number — which is what would quietly mangle an 18-digit row id.

import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { cpSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');

// ---- The PHP one --------------------------------------------------------------------------------

async function websiteServer() {
  const dir = mkdtempSync(join(tmpdir(), 'mm-php-'));
  cpSync(join(root, 'website', 'mixmaster'), dir, { recursive: true });
  const port = 18000 + Math.floor(Math.random() * 1000);
  const php = spawn('php', ['-S', `127.0.0.1:${port}`, '-t', dir], { stdio: ['ignore', 'ignore', 'pipe'] });
  let log = '';
  php.stderr.on('data', (chunk) => { log += chunk; });
  for (let i = 0; i < 50; i++) {
    try {
      await fetch(`http://127.0.0.1:${port}/api.php`);
      break;
    } catch {
      await new Promise((r) => setTimeout(r, 100));
    }
  }
  return {
    name: 'website',
    async call(body) {
      const res = await fetch(`http://127.0.0.1:${port}/api.php`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const text = await res.text();
      try {
        return JSON.parse(text);
      } catch {
        throw new Error(`not JSON: ${text.slice(0, 400)}\n${log.slice(-2000)}`);
      }
    },
    async page() {
      return (await fetch(`http://127.0.0.1:${port}/api.php`)).text();
    },
    stop() {
      php.kill();
      rmSync(dir, { recursive: true, force: true });
    },
    get log() { return log; },
  };
}

// ---- The Google one -----------------------------------------------------------------------------

function fakeGoogle() {
  const books = new Map();
  const props = new Map();
  let locked = false;

  class Range {
    constructor(sheet, row, col, rows, cols) {
      if (row < 1 || col < 1 || rows < 1 || cols < 1) throw new Error(`bad range ${row},${col},${rows},${cols}`);
      if (row + rows - 1 > sheet.maxRows) throw new Error('The coordinates of the range are outside the dimensions of the sheet.');
      Object.assign(this, { sheet, row, col, rows, cols });
    }
    getValues() {
      const out = [];
      for (let r = 0; r < this.rows; r++) {
        const line = [];
        for (let c = 0; c < this.cols; c++) {
          const v = this.sheet.cells[this.row + r - 1]?.[this.col + c - 1];
          line.push(v === undefined ? '' : v);
        }
        out.push(line);
      }
      return out;
    }
    setValues(values) {
      if (values.length !== this.rows) throw new Error('row count mismatch');
      values.forEach((line, r) => {
        if (line.length !== this.cols) throw new Error('column count mismatch');
        line.forEach((v, c) => {
          const row = this.row + r;
          const col = this.col + c;
          if (typeof v === 'string' && v.length > 50000) throw new Error('Your input contains more than the maximum of 50000 characters in a single cell.');
          if (typeof v === 'string' && /^[=+]/.test(v) && v.length > 1) throw new Error(`formula written: ${v}`);
          let stored = v;
          if (typeof v === 'string' && !this.sheet.isText(row, col) && /^-?\d+(\.\d+)?$/.test(v)) stored = Number(v);
          (this.sheet.cells[row - 1] ||= [])[col - 1] = stored;
        });
      });
      return this;
    }
    setNumberFormat(format) {
      for (let r = 0; r < this.rows; r++) {
        for (let c = 0; c < this.cols; c++) this.sheet.formats.set(`${this.row + r},${this.col + c}`, format);
      }
      return this;
    }
  }

  class Sheet {
    constructor(name) {
      this.name = name;
      this.cells = [];
      this.formats = new Map();
      this.columnFormats = new Map();
      this.maxRows = 1000;
    }
    isText(row, col) {
      return this.formats.get(`${row},${col}`) === '@' || this.columnFormats.get(col) === '@';
    }
    setName(name) { this.name = name; return this; }
    getName() { return this.name; }
    setFrozenRows() { return this; }
    getMaxRows() { return this.maxRows; }
    getLastRow() {
      for (let r = this.cells.length; r > 0; r--) {
        if ((this.cells[r - 1] || []).some((v) => v !== '' && v !== undefined)) return r;
      }
      return 0;
    }
    getRange(row, col, rows = 1, cols = 1) {
      if (typeof row === 'string') {
        const m = /^([A-Z]):([A-Z])$/.exec(row);
        const from = m[1].charCodeAt(0) - 64;
        const to = m[2].charCodeAt(0) - 64;
        const sheet = this;
        return {
          setNumberFormat(format) {
            for (let c = from; c <= to; c++) sheet.columnFormats.set(c, format);
            return this;
          },
        };
      }
      return new Range(this, row, col, rows, cols);
    }
    insertRowsAfter(after, count) {
      this.maxRows += count;
      return this;
    }
    deleteRow(row) { this.deleteRows(row, 1); }
    deleteRows(row, count) {
      this.cells.splice(row - 1, count);
      const moved = new Map();
      for (const [key, format] of this.formats) {
        const [r, c] = key.split(',').map(Number);
        if (r < row) moved.set(key, format);
        else if (r >= row + count) moved.set(`${r - count},${c}`, format);
      }
      this.formats = moved;
      this.maxRows -= count;
    }
  }

  class Book {
    constructor(name) {
      this.id = randomUUID();
      this.name = name;
      this.sheets = [new Sheet('Sheet1')];
    }
    getId() { return this.id; }
    getUrl() { return `https://docs.google.com/spreadsheets/d/${this.id}`; }
    rename(name) { this.name = name; }
    getSheets() { return this.sheets; }
    getSheetByName(name) { return this.sheets.find((s) => s.name === name) || null; }
    insertSheet(name) { const s = new Sheet(name); this.sheets.push(s); return s; }
  }

  return {
    books,
    SpreadsheetApp: {
      create(name) { const b = new Book(name); books.set(b.id, b); return b; },
      openById(id) { const b = books.get(id); if (!b) throw new Error('not found'); return b; },
      flush() {},
    },
    PropertiesService: {
      getScriptProperties() {
        return {
          getProperty: (k) => (props.has(k) ? props.get(k) : null),
          setProperty: (k, v) => { if (typeof v !== 'string') throw new Error('property must be a string'); props.set(k, v); },
          deleteProperty: (k) => { props.delete(k); },
        };
      },
    },
    LockService: {
      getScriptLock() {
        return {
          waitLock() { if (locked) throw new Error('lock held'); locked = true; },
          releaseLock() { locked = false; },
        };
      },
    },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput(text) {
        return { text, setMimeType() { return this; }, getContent() { return this.text; } };
      },
    },
    HtmlService: {
      createHtmlOutput(html) { return { html, setTitle() { return this; }, getContent() { return this.html; } }; },
    },
    Utilities: {
      DigestAlgorithm: { SHA_256: 'sha256' },
      Charset: { UTF_8: 'utf8' },
      getUuid: () => randomUUID(),
      computeDigest(alg, text) {
        return Array.from(createHash('sha256').update(String(text), 'utf8').digest()).map((b) => (b > 127 ? b - 256 : b));
      },
    },
    console,
  };
}

function googleServer() {
  const google = fakeGoogle();
  const context = vm.createContext({ ...google });
  vm.runInContext(readFileSync(join(root, 'google', 'Code.gs'), 'utf8'), context, { filename: 'Code.gs' });
  return {
    name: 'google',
    google,
    async call(body) {
      const out = context.doPost({ postData: { contents: JSON.stringify(body) } });
      return JSON.parse(out.getContent());
    },
    async page() {
      return context.doGet().getContent();
    },
    stop() {},
  };
}

// ---- The conversation -----------------------------------------------------------------------------

let failures = 0;
function check(label, ok, detail) {
  if (!ok) {
    failures++;
    console.log(`  FAIL ${label}${detail === undefined ? '' : ': ' + JSON.stringify(detail).slice(0, 600)}`);
  }
}

const BIG = '1882735645289029123';
const BIG2 = '1882735645289029999';
const product = (id, name) => ({ t: 'products', id, d: JSON.stringify({ id: BigIntJson(id), name, brand: 'Idealwork' }) });
// Row data travels as a string exactly as the phone wrote it, big ids and all.
function BigIntJson(id) { return `@@${id}@@`; }
const raw = (s) => s.replace(/"@@(-?\d+)@@"/g, '$1');

async function conversation(server) {
  const call = server.call.bind(server);
  let r = await call({ a: 'hello' });
  check('hello', r.ok && r.app === 'mixmaster' && r.claimed === false && r.kind === server.name, r);
  check('status page before setup', /waiting to be set up/.test(await server.page()));

  r = await call({ a: 'pull', token: 'x', since: 0 });
  check('pull before setup', r.ok === false && r.error === 'other_company', r);
  r = await call({ a: 'nonsense' });
  check('unknown action', r.error === 'bad_request', r);
  r = await call({ a: 'setup', company_name: '   ', name: 'Tanel', device: 'devA' });
  check('setup needs a name', r.error === 'bad_request', r);

  r = await call({ a: 'setup', company_name: 'ConWiC  Oy', name: 'Tanel', device: 'devA' });
  check('setup', r.ok && r.token && r.company.name === 'ConWiC Oy' && r.me.owner === true && r.me.perms.catalogue === true, r);
  const owner = r.token;
  const company = r.company.id;
  r = await call({ a: 'setup', company_name: 'Thief', name: 'X', device: 'devX' });
  check('setup only once', r.error === 'claimed', r);
  r = await call({ a: 'hello' });
  check('hello after setup', r.claimed === true && r.company.name === 'ConWiC Oy' && r.company.id === company, r);
  check('status page after setup', /set up for ConWiC Oy/.test(await server.page()));

  const o = (body) => call({ token: owner, company, device: 'devA', ...body });

  const p1 = raw(product(BIG, 'Microtopping õäöü').d);
  r = await o({ a: 'push', rows: [
    { t: 'products', id: BIG, d: p1 },
    { t: 'stock', id: BIG, d: raw(JSON.stringify({ id: `@@${BIG}@@`, productId: `@@${BIG}@@`, fullPacks: 4, openAmount: 12.5 })) },
    { t: 'team_members', id: '7', d: '{"id":7,"name":"Mari"}' },
    { t: 'Bad-Table', id: '1', d: '{}' },
    { t: 'products', id: '12x', d: '{}' },
  ] });
  check('owner push', r.ok && r.refused.length === 0, r);

  r = await o({ a: 'pull', since: 0 });
  check('owner pull', r.ok && r.rows.length === 3 && r.next > 0 && r.more === false, r);
  const got = r.rows.find((x) => x.t === 'products');
  check('big id kept', got && got.id === BIG && got.d === p1 && got.d.includes(BIG), got);
  check('dev kept', got && got.dev === 'devA', got);
  const afterOwner = r.next;

  r = await o({ a: 'person_save', person: { name: 'Mari' } });
  check('add worker', r.ok && /^[2-9A-HJ-NP-Z]{8}$/.test(r.person.code) && r.person.status === 'pending'
    && r.person.perms.warehouse === true && r.person.perms.catalogue === false && r.people.length === 2, r);
  const mari = r.person;
  check('owners first', r.people[0].owner === true && r.people[0].name === 'Tanel', r.people);

  r = await call({ a: 'join', code: 'ZZZZ-ZZZZ', device: 'devW' });
  check('wrong code', r.error === 'bad_code', r);
  const typed = (mari.code.slice(0, 4) + ' - ' + mari.code.slice(4)).toLowerCase();
  r = await call({ a: 'join', code: typed, device: 'devW' });
  check('join', r.ok && r.token && r.me.name === 'Mari' && r.me.owner === false && r.company.id === company, r);
  const worker = r.token;
  r = await call({ a: 'join', code: mari.code, device: 'devW2' });
  check('code used once', r.error === 'bad_code', r);

  const w = (body) => call({ token: worker, company, device: 'devW', ...body });
  r = await w({ a: 'pull', since: 0 });
  check('worker pull', r.ok && r.rows.length === 3 && r.me.perms.catalogue === false && r.me.perms.warehouse === true, r);

  r = await w({ a: 'push', rows: [
    { t: 'products', id: BIG, d: raw(product(BIG, 'Renamed by worker').d) },
    { t: 'products', id: BIG2, d: raw(product(BIG2, 'New by worker').d) },
    { t: 'team_members', id: '7', x: true },
    { t: 'stock', id: BIG, d: '{"fullPacks":3}' },
    { t: 'notes', id: '55', d: '{"text":"Primer done, room 2"}' },
  ] });
  const refusedP = r.refused?.find((x) => x.t === 'products' && x.id === BIG);
  const refusedNew = r.refused?.find((x) => x.id === BIG2);
  const refusedTeam = r.refused?.find((x) => x.t === 'team_members');
  check('worker push refused three', r.ok && r.refused.length === 3, r);
  check('refused gives company copy', refusedP && refusedP.d === p1 && refusedP.x === false, refusedP);
  check('refused new row is a delete', refusedNew && refusedNew.x === true && refusedNew.d === null, refusedNew);
  check('refused delete gives row back', refusedTeam && refusedTeam.x === false && refusedTeam.d.includes('Mari'), refusedTeam);

  r = await o({ a: 'pull', since: afterOwner });
  check('owner sees worker stock + note', r.ok && r.rows.length === 2 && r.rows[0].t === 'stock' && r.rows[0].d === '{"fullPacks":3}' && r.rows[0].dev === 'devW', r);

  r = await w({ a: 'people' });
  check('worker cannot list people', r.error === 'not_allowed', r);
  r = await w({ a: 'person_save', person: { name: 'Me', owner: true } });
  check('worker cannot add people', r.error === 'not_allowed', r);

  r = await o({ a: 'person_save', person: { id: mari.id, name: 'Mari Tamm', perms: { catalogue: true, warehouse: true, site: true, projects: false } } });
  check('change permissions', r.ok && r.person.perms.catalogue === true && r.person.status === 'active' && r.person.code === null, r);
  r = await w({ a: 'pull', since: 999999 });
  check('permissions reach the phone', r.ok && r.me.perms.catalogue === true && r.me.name === 'Mari Tamm' && r.rows.length === 0, r.me);
  r = await w({ a: 'push', rows: [{ t: 'products', id: BIG2, d: '{"name":"New by worker"}' }] });
  check('worker may now add products', r.ok && r.refused.length === 0, r);

  r = await o({ a: 'person_save', person: { id: 1, name: 'Tanel', owner: false } });
  check('owner stays owner', r.ok && r.person.owner === true, r.person);

  r = await o({ a: 'person_code', id: mari.id });
  check('new code', r.ok && r.person.status === 'pending' && r.person.code && r.person.code !== mari.code, r);
  const second = r.person.code;
  r = await w({ a: 'pull', since: 0 });
  check('old phone cut off by new code', r.error === 'revoked', r);
  r = await call({ a: 'join', code: second, device: 'devW3' });
  check('join with new code', r.ok, r);
  const worker2 = r.token;

  r = await call({ a: 'leave', token: worker2, company });
  check('worker leaves', r.ok, r);
  r = await call({ a: 'pull', token: worker2, company, since: 0 });
  check('left phone revoked', r.error === 'revoked', r);
  r = await o({ a: 'people' });
  check('left shows in list', r.ok && r.people.find((p) => p.id === mari.id)?.status === 'left', r.people);

  r = await o({ a: 'person_code', id: mari.id });
  const third = r.person.code;
  r = await call({ a: 'join', code: third, device: 'devW4' });
  const worker3 = r.token;
  r = await o({ a: 'person_remove', id: mari.id });
  check('remove', r.ok && r.people.length === 1, r);
  r = await call({ a: 'pull', token: worker3, company, since: 0 });
  check('removed phone revoked', r.error === 'revoked', r);
  r = await call({ a: 'join', code: third, device: 'devW5' });
  check('removed code dead', r.error === 'bad_code', r);

  r = await o({ a: 'person_remove', id: 1 });
  check('cannot remove self', r.error === 'not_allowed', r);
  r = await o({ a: 'leave' });
  check('last owner cannot leave', r.error === 'last_owner', r);

  r = await call({ a: 'pull', token: owner, company: 'someoneelse', since: 0 });
  check('other company', r.error === 'other_company', r);
  r = await call({ a: 'pull', token: 'nope', company, since: 0 });
  check('unknown token revoked', r.error === 'revoked', r);

  // A second owner, the way the developer hands the company over to the employer.
  r = await o({ a: 'person_save', person: { name: 'Employer', owner: true } });
  check('second owner', r.ok && r.person.owner === true && r.person.perms.catalogue === true, r);
  r = await call({ a: 'join', code: r.person.code, device: 'devE' });
  const employer = r.token;
  check('second owner joins', r.ok && r.me.owner === true, r);
  r = await call({ a: 'person_remove', token: employer, company, id: 1 });
  check('employer removes developer', r.ok && r.people.length === 1 && r.people[0].name === 'Employer', r);
  r = await o({ a: 'pull', since: 0 });
  check('developer revoked', r.error === 'revoked', r);
  const e = (body) => call({ token: employer, company, device: 'devE', ...body });

  // Paging: 1200 rows, with the same row changed twice in one batch and a delete.
  const many = [];
  for (let i = 0; i < 1000; i++) many.push({ t: 'solution_lines', id: String(1000 + i), d: `{"n":${i}}` });
  r = await e({ a: 'push', rows: many });
  check('push 1000', r.ok && r.refused.length === 0, r);
  const more = [];
  for (let i = 1000; i < 1200; i++) more.push({ t: 'solution_lines', id: String(1000 + i), d: `{"n":${i}}` });
  more.push({ t: 'solution_lines', id: '1005', d: '{"n":"first"}' });
  more.push({ t: 'solution_lines', id: '1005', d: '{"n":"second"}' });
  more.push({ t: 'solution_lines', id: '1006', x: true });
  r = await e({ a: 'push', rows: more });
  check('push 203', r.ok, r);
  r = await e({ a: 'push', rows: new Array(1001).fill({ t: 'notes', id: '1', d: '{}' }) });
  check('push too many', r.error === 'bad_request', r);

  let since = 0;
  const seen = new Map();
  const pages = [];
  for (let guard = 0; guard < 10; guard++) {
    r = await e({ a: 'pull', since });
    if (!r.ok) { check('page', false, r); break; }
    pages.push(r.rows.length);
    r.rows.forEach((row) => seen.set(`${row.t}/${row.id}`, row));
    since = r.next;
    if (!r.more) break;
  }
  // 1200 lines plus the five rows from before: two products, the stock, the note, the crew row.
  check('pages', pages.join(',') === '500,500,205', pages);
  check('all rows once', seen.size === 1205, seen.size);
  check('last change wins', seen.get('solution_lines/1005')?.d === '{"n":"second"}', seen.get('solution_lines/1005'));
  check('delete travels', seen.get('solution_lines/1006')?.x === true && seen.get('solution_lines/1006')?.d === null, seen.get('solution_lines/1006'));
  r = await e({ a: 'pull', since });
  check('nothing after the end', r.ok && r.rows.length === 0 && r.next === since && r.more === false, r);

  // A long note: more than one sheet cell holds.
  const long = JSON.stringify({ text: 'ä'.repeat(120000) });
  r = await e({ a: 'push', rows: [{ t: 'notes', id: '77', d: long }] });
  check('long row accepted', r.ok, r);
  r = await e({ a: 'pull', since });
  check('long row intact', r.ok && r.rows[0]?.d === long, r.rows?.[0]?.d?.length);

  // Formula-looking names and numbers-only data stay as they were typed.
  r = await e({ a: 'person_save', person: { name: '=HYPERLINK("x")' } });
  check('formula-looking name', r.ok && r.person.name === '=HYPERLINK("x")', r);
  r = await e({ a: 'push', rows: [{ t: 'notes', id: '78', d: '12345678901234567890' }] });
  const n = await e({ a: 'pull', since });
  check('number-looking data kept as text', n.rows.find((x) => x.id === '78')?.d === '12345678901234567890', n.rows.find((x) => x.id === '78'));

  // Guessing: thirty wrong codes an hour, then nothing — not even a right one — until the next.
  r = await e({ a: 'person_save', person: { name: 'Late' } });
  const late = r.person.code;
  // Three wrong ones were tried above already, so the stop comes after twenty-seven more.
  const answers = [];
  for (let i = 0; i < 28; i++) answers.push((await call({ a: 'join', code: 'AAAAAAAA', device: 'g' })).error);
  check('wrong codes until the stop', answers.slice(0, 27).every((a) => a === 'bad_code') && answers[27] === 'too_many', answers);
  r = await call({ a: 'join', code: late, device: 'g' });
  check('then too_many', r.error === 'too_many', r);
}

// ---- Moving a company from one server to another ------------------------------------------------

async function moving(oldServer, newServer, spareServer) {
  const A = oldServer.call.bind(oldServer);
  const B = newServer.call.bind(newServer);
  const NEW = 'https://new.example/mixmaster/api.php';

  let r = await A({ a: 'setup', company_name: 'ConWiC Oy', name: 'Tanel', device: 'devA' });
  const owner = r.token;
  const company = r.company;
  const o = (call, body) => call({ token: owner, company: company.id, device: 'devA', ...body });
  r = await o(A, { a: 'person_save', person: { name: 'Mari' } });
  r = await A({ a: 'join', code: r.person.code, device: 'devW' });
  const worker = r.token;
  const w = (call, body) => call({ token: worker, company: company.id, device: 'devW', ...body });
  r = await o(A, { a: 'person_save', person: { name: 'Late' } });
  const lateCode = r.person.code;

  await o(A, { a: 'push', rows: [{ t: 'products', id: '11', d: '{"name":"Base"}' }] });
  await w(A, { a: 'push', rows: [{ t: 'stock', id: '11', d: '{"fullPacks":4}' }, { t: 'notes', id: '12', d: '{"text":"x"}' }] });

  r = await w(A, { a: 'export' });
  check('move: worker cannot export', r.error === 'not_allowed', r);
  r = await o(A, { a: 'export' });
  const people = r.people;
  check('move: export', r.ok && people.length === 3 && people.every((p) => 'token_hash' in p)
    && people.find((p) => p.name === 'Mari').token_hash?.length === 64
    && people.find((p) => p.name === 'Late').code === lateCode, r);

  r = await w(A, { a: 'move_out', to: NEW });
  check('move: worker cannot move', r.error === 'not_allowed', r);
  r = await o(A, { a: 'move_out', to: 'http://insecure.example' });
  check('move: https only', r.error === 'bad_request', r);
  r = await o(A, { a: 'move_out', to: NEW });
  check('move: freeze old', r.ok, r);

  r = await w(A, { a: 'push', rows: [{ t: 'stock', id: '11', d: '{"fullPacks":3}' }] });
  check('move: old refuses writes, says where', r.error === 'moved' && r.to === NEW, r);
  r = await w(A, { a: 'pull', since: 0 });
  check('move: old still reads, says where', r.ok && r.moved_to === NEW && r.rows.length === 3, r);
  r = await A({ a: 'hello' });
  check('move: hello says where', r.moved_to === NEW, r);
  r = await A({ a: 'join', code: lateCode, device: 'devL' });
  check('move: old codes sent on', r.error === 'moved' && r.to === NEW, r);

  r = await B({ a: 'adopt', token: worker, company, people });
  check('move: only an owner adopts', r.error === 'not_allowed', r);
  r = await B({ a: 'adopt', token: owner, company, people });
  check('move: adopt', r.ok && r.company.id === company.id && r.company.name === 'ConWiC Oy', r);
  r = await B({ a: 'adopt', token: owner, company, people });
  check('move: adopt once', r.error === 'claimed', r);
  r = await B({ a: 'hello' });
  check('move: new is the same company', r.claimed && r.company.id === company.id && !r.moved_to, r);

  r = await w(B, { a: 'pull', since: 0 });
  check('move: worker key works on new', r.ok && r.me.name === 'Mari' && r.me.perms.warehouse === true, r);
  r = await w(B, { a: 'push', rows: [{ t: 'stock', id: '11', d: '{"fullPacks":2}' }] });
  check('move: crew waits while it arrives', r.error === 'busy', r);

  const rows = (await o(A, { a: 'pull', since: 0 })).rows.map((x) => ({ t: x.t, id: x.id, d: x.d, x: x.x }));
  r = await o(B, { a: 'push', rows });
  check('move: owner fills new', r.ok && r.refused.length === 0, r);
  r = await o(B, { a: 'move_done' });
  check('move: done', r.ok, r);
  r = await w(B, { a: 'push', rows: [{ t: 'stock', id: '11', d: '{"fullPacks":2}' }] });
  check('move: crew writes again', r.ok, r);
  r = await w(B, { a: 'pull', since: 0 });
  check('move: everything there', r.ok && r.rows.length === 3 && r.rows.find((x) => x.t === 'stock').d === '{"fullPacks":2}', r.rows);

  r = await B({ a: 'join', code: lateCode, device: 'devL' });
  check('move: old code works on new', r.ok && r.me.name === 'Late', r);
  r = await o(B, { a: 'people' });
  check('move: people carried', r.ok && r.people.length === 3 && r.people.every((p) => p.status === 'active'), r.people);

  r = await o(A, { a: 'move_out', to: '' });
  r = await o(A, { a: 'push', rows: [{ t: 'products', id: '13', d: '{}' }] });
  check('move: an unfinished move can be undone', r.ok, r);

  const S = spareServer.call.bind(spareServer);
  r = await S({ a: 'adopt', token: owner, company: { id: 'not hex!', name: 'x' }, people });
  check('move: bad company id', r.error === 'bad_request', r);
}

const which = process.argv[2];
const servers = [];
if (!which || which === 'website') servers.push(websiteServer);
if (!which || which === 'google') servers.push(async () => googleServer());
for (const make of servers) {
  const server = await make();
  const before = failures;
  try {
    await conversation(server);
  } catch (e) {
    failures++;
    console.log(`  CRASH ${e.stack}`);
    if (server.log) console.log(server.log.slice(-3000));
  } finally {
    server.stop();
  }
  console.log(`${server.name}: ${failures === before ? 'all good' : `${failures - before} failed`}`);
}
for (const make of servers) {
  const trio = [await make(), await make(), await make()];
  const before = failures;
  try {
    await moving(...trio);
  } catch (e) {
    failures++;
    console.log(`  CRASH ${e.stack}`);
  } finally {
    trio.forEach((server) => server.stop());
  }
  console.log(`${trio[0].name} moving: ${failures === before ? 'all good' : `${failures - before} failed`}`);
}
process.exit(failures ? 1 : 0);

/*
 * Car Mech — one long day after another in a neon garage, fixing cars for people who should not own
 * them.
 *
 * The day runs on a game clock from 07:00 (two game minutes to a real second). Customers arrive on a
 * schedule and wait on the left until they run out of patience; the one on the lift is talked to,
 * scanned, quoted, repaired through the minigames, and handed back for money and a review. Energy
 * drains all day and the hands shake as it goes. Every night the rent is due.
 *
 * The career (the day, the credits, the reviews, the upgrades) is saved in the page's storage every
 * morning, so a day left half-way starts again from its morning.
 */
(function () {
  'use strict';

  const D = window.CMData, SC = window.CMScene, AU = window.CMAudio, FA = window.CMFaces, MG = window.CMMini;
  const $ = (s) => document.querySelector(s);
  const rnd = (a, b) => a + Math.random() * (b - a);
  const irnd = (a, b) => Math.floor(rnd(a, b + 1));
  const pick = (a) => a[Math.floor(Math.random() * a.length)];
  const chance = (p) => Math.random() < p;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const round5 = (v) => Math.max(5, Math.round(v / 5) * 5);
  // the game's own pauses; a test can run them faster along with the clock (cmTest.waits)
  let waitScale = 1;
  const later = (fn, ms) => setTimeout(fn, ms / waitScale);
  const sleep = (ms) => new Promise((r) => later(r, ms));
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const money = (n) => (n < 0 ? '−¢' : '¢') + Math.abs(Math.round(n)).toLocaleString('en-US');
  const clockStr = (m) => { const mm = Math.floor(m); return String(Math.floor(mm / 60) % 24).padStart(2, '0') + ':' + String(mm % 60).padStart(2, '0'); };
  const shuffle = (a) => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const TEST = /[?&]test\b/.test(location.search);

  // what a job costs the customer: the part with a margin on it, and the work
  const MARKUP = 1.25, LABOUR = 0.85;
  const partPrice = (f) => Math.round(f.def.parts * MARKUP);

  // the shift, in minutes from midnight
  const OPEN = 7 * 60, CLOSE = 19 * 60, LIGHTS_OUT = 22 * 60, LAST_ARRIVAL = 18 * 60 + 40;
  // two game minutes go by every real second
  let MIN_PER_SEC = 2;

  // ------------------------------------------------------------------ the phone around the page
  const app = window.CarMechApp && typeof window.CarMechApp.quit === 'function' ? window.CarMechApp : null;

  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value);
    } catch (e) { return null; }
    return null;
  }
  const settings = Object.assign({ music: true, sfx: true, voices: true, vibe: true, gfx: 'auto' }, safeJSON(store('carmech.settings')) || {});
  function saveSettings() { store('carmech.settings', JSON.stringify(settings)); }
  function safeJSON(s) { try { return s ? JSON.parse(s) : null; } catch (e) { return null; } }

  function haptic(kind) {
    if (!settings.vibe) return;
    try {
      if (app && app.haptic) app.haptic(kind);
      else if (navigator.vibrate) navigator.vibrate({ tick: 6, tap: 10, heavy: 25, ok: [10, 40, 10], bad: [30, 30, 30] }[kind] || 10);
    } catch (e) { /* nothing */ }
  }
  function sfx(name, arg) { AU.play(name, arg); }

  // the customers' voices: the phone's own text-to-speech, through the app
  let voiceList = [];
  function loadVoices() {
    try {
      if (app && app.voices) voiceList = JSON.parse(app.voices() || '[]');
      else if (window.speechSynthesis) voiceList = speechSynthesis.getVoices().filter((v) => /^en/i.test(v.lang)).map((v) => ({ n: v.name, l: v.lang, g: '' }));
    } catch (e) { voiceList = []; }
  }
  // whether a line would actually be heard: the setting, and an engine on the phone that speaks English
  function voiceOn() {
    if (!settings.voices) return false;
    try { return app ? !!app.canSpeak() : !!window.speechSynthesis; } catch (e) { return false; }
  }
  function speak(c, text) {
    if (!voiceOn() || !c) return;
    // the phone's speech engine can take a moment to start: customers made before it did get a voice now
    if (!c.voice.name) {
      if (!voiceList.length) loadVoices();
      if (voiceList.length) c.voice.name = pick(voiceList).n;
    }
    const plain = text.replace(/[“”"]/g, '').replace(/¢/g, ' credits ');
    try {
      if (app && app.speak) app.speak(plain, c.voice.pitch, c.voice.rate, c.voice.name || '');
      else if (window.speechSynthesis) {
        speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(plain);
        u.pitch = c.voice.pitch; u.rate = c.voice.rate;
        const v = speechSynthesis.getVoices().find((x) => x.name === c.voice.name);
        if (v) u.voice = v;
        speechSynthesis.speak(u);
      }
    } catch (e) { /* the voice is a bonus */ }
  }
  function hush() {
    try { if (app && app.hush) app.hush(); else if (window.speechSynthesis) speechSynthesis.cancel(); } catch (e) { /* nothing */ }
  }

  // ------------------------------------------------------------------ the career
  let career = null;
  let best = safeJSON(store('carmech.best')) || { days: 0, earned: 0, fixed: 0 };
  function newCareer() {
    return {
      v: 1, day: 1, credits: 150, strikes: 0, upgrades: {},
      reviews: D.SEED_REVIEWS.map((r) => Object.assign({ day: 0 }, r)),
      comebacks: [],
      totals: { fixed: 0, earned: 0, lost: 0, served: 0 },
    };
  }
  function saveCareer() { if (career) store('carmech.career', JSON.stringify(career)); }
  function loadCareer() { const c = safeJSON(store('carmech.career')); return c && c.v === 1 ? c : null; }
  const has = (u) => !!(career && career.upgrades[u]);
  function rentFor(n) { return Math.round(D.RENT[(n - 1) % 7] * Math.pow(1.35, Math.floor((n - 1) / 7)) / 5) * 5; }
  // the last fifteen reviews, pulled towards 3.5 by four imaginary ones, so one bad hour can't sink you
  function ratingOf(list) {
    const last = list.slice(-15);
    return (last.reduce((s, r) => s + r.stars, 0) + 3.5 * 4) / (last.length + 4);
  }
  function rating() { return ratingOf(career.reviews.concat(day ? day.reviews : [])); }

  // ------------------------------------------------------------------ the customers
  function archetypesFor(n) {
    // gentler people early in the week; the requesters of managers come later, like Thursdays do
    return D.ARCHETYPES.filter((a) => !(n <= 1 && (a.id === 'karen' || a.id === 'carai')) && !(n <= 2 && a.id === 'kid'));
  }
  function pickFaults(n, count) {
    const pool = Object.keys(D.FAULTS).filter((id) => D.FAULT_DAYS[id] <= n);
    return shuffle(pool.slice()).slice(0, count);
  }
  let custSeq = 0;
  function makeCustomer(n, opts = {}) {
    const arch = opts.arch || pick(archetypesFor(n));
    const seed = opts.seed || irnd(1, 2e9);
    const style = opts.car ? opts.car.style : pick(SC.styles);
    const car = opts.car || {
      style, paint: pick(SC.paints), accent: pick(SC.accents), grime: chance(0.45) ? rnd(0.25, 0.9) : 0,
      name: pick(D.CARS[style]), year: irnd(2058, 2087),
    };
    const nf = opts.faults ? opts.faults.length : n <= 1 ? irnd(1, 2) : n <= 3 ? irnd(1, 3) : n <= 5 ? irnd(2, 3) : irnd(2, 4);
    const ids = opts.faults || pickFaults(n, nf);
    const faults = ids.map((id, i) => makeFault(id, i > 0 && chance(Math.min(0.4, 0.12 + n * 0.035))));
    const voice = { pitch: arch.voice[0] * rnd(0.93, 1.07), rate: arch.voice[1] * rnd(0.96, 1.04), name: voiceList.length ? pick(voiceList).n : '' };
    const c = {
      id: ++custSeq, seed, arch, name: opts.name || (arch.robot ? pick(D.ROBOT_NAMES) : `${pick(D.FIRST)} ${pick(D.LAST)}`),
      face: FA.make(seed, arch.look), car, faults, voice,
      mood: clamp(58 + rnd(-10, 10) - (arch.temper - 1) * 10, 25, 80),
      patience: rnd(150, 220) * arch.patience * (has('tv') ? 1.5 : 1) * Math.max(0.65, 1 - (n - 1) * 0.04),
      waited: 0, comeback: !!opts.comeback, vip: !!opts.vip,
      complaint: opts.complaint || (chance(0.22) ? pick(D.VAGUE) : pick(D.COMPLAINTS[ids[0]])),
    };
    if (c.vip) { c.patience *= 0.45; c.arch = Object.assign({}, arch, { cash: arch.cash * 1.8 }); }
    return c;
  }
  function makeFault(id, hidden) {
    const def = D.FAULTS[id];
    const side = chance(0.55) ? 'F' : 'R';
    const part = def.part === 'wheel' ? 'wheel' + side : def.part === 'brakes' ? 'brakes' + side : def.part === 'susp' ? 'susp' + side : def.part;
    const n = day ? day.n : 1;
    return {
      id, def, part, side, hidden: !!hidden, found: false, fixed: false, declined: false, quality: 0, q: [],
      price: round5((def.parts * MARKUP + def.labor * LABOUR) * (1 + (n - 1) * 0.04)),
      critter: id === 'junk' ? pick(D.CRITTERS) : null,
    };
  }

  // ------------------------------------------------------------------ the day
  let day = null;
  let job = null;
  let mode = 'title';
  let paused = false;

  function startDay() {
    const n = career.day;
    const wd = (n - 1) % 7;
    day = {
      n, wd, min: OPEN, credits: career.credits,
      energy: Math.max(55, 100 - wd * 3 - Math.floor((n - 1) / 7) * 4), coffees: [], coffeeCount: 0,
      queue: [], schedule: [], served: 0, lost: 0, fixed: 0,
      ledger: { pay: 0, tips: 0, parts: 0, coffee: 0, fines: 0, ambulance: 0 },
      reviews: [], events: [], nextText: OPEN + rnd(40, 90), nextTired: 0, over: false, overtime: false,
      power: 0, startCredits: career.credits, collapsed: false, quoteAdvice: false,
    };
    // who comes, and when
    const count = Math.max(4, [5, 6, 7, 8, 9, 10, 11][wd] + Math.floor((n - 1) / 7) * 2 + (has('sign') ? 1 : 0) + Math.round(rating() - 3.5));
    const times = [OPEN + rnd(4, 20)];
    for (let i = 1; i < count; i++) {
      // a morning rush, then the rest of the day
      const u = Math.random();
      times.push(u < 0.3 ? OPEN + rnd(20, 150) : OPEN + rnd(60, LAST_ARRIVAL - OPEN - 20));
    }
    if (chance(0.6)) times.push(LAST_ARRIVAL - rnd(0, 10));   // there's always one, right at the end
    times.sort((a, b) => a - b);
    times.forEach((t) => day.schedule.push({ at: t, cust: null }));
    // yesterday's sloppy jobs come back in the morning
    (career.comebacks || []).forEach((cb) => {
      const arch = D.ARCHETYPES.find((a) => a.id === cb.arch) || D.ARCHETYPES[0];
      day.schedule.push({ at: OPEN + rnd(20, 200), cust: null, comeback: cb, arch });
    });
    career.comebacks = [];
    day.schedule.sort((a, b) => a.at - b.at);
    // things that happen to a garage
    if (n >= 2) {
      const kinds = shuffle(['power', 'drone', 'thief', 'vip', 'power', 'drone', 'vip', 'robot']);
      const k = n <= 3 ? 1 : n <= 5 ? 2 : 3;
      for (let i = 0; i < k; i++) day.events.push({ at: OPEN + rnd(110, 640), kind: kinds[i] });
      day.events.sort((a, b) => a.at - b.at);
    }
    job = null;
    paused = false;
  }

  function tick(dt) {
    if (!day || day.over) return;
    const mins = dt * MIN_PER_SEC;
    advance(mins);
  }

  // the clock moves on by [mins]: arrivals, patience, energy, events, the end of the day
  function advance(mins) {
    const before = day.min;
    day.min += mins;
    const late = day.min > CLOSE;
    drain(mins * 0.072 * (has('stool') ? 0.7 : 1) * (late ? 1.45 : 1));
    // arrivals
    while (day.schedule.length && day.schedule[0].at <= day.min) {
      const s = day.schedule.shift();
      const c = s.comeback ? comebackCustomer(s.comeback, s.arch) : makeCustomer(day.n);
      arrive(c);
    }
    // patience runs out in the queue
    for (let i = day.queue.length - 1; i >= 0; i--) {
      const c = day.queue[i];
      c.waited += mins;
      if (c.waited > c.patience * 0.5) c.mood = Math.max(0, c.mood - mins * 0.2);
      if (c.waited >= c.patience) leaveQueue(c, 'waited');
    }
    // the one in the lounge while you work gets bored too
    if (job && job.cust && job.quoted) {
      job.mins += mins;
      if (job.mins > job.expect) job.cust.mood = Math.max(0, job.cust.mood - mins * 0.08);
    }
    // the garage's day
    while (day.events.length && day.events[0].at <= day.min) runEvent(day.events.shift().kind);
    if (day.power > 0 && day.min >= day.power) powerBack();
    if (day.min >= day.nextText) { day.nextText = day.min + rnd(70, 150); phoneText(...pick(D.TEXTS)); }
    tiredness();
    // 19:00: closed, officially
    if (before < CLOSE && day.min >= CLOSE) {
      if (!day.power) AU.music('night');
      if (job || day.queue.length) {
        day.overtime = true;
        toast('19:00. Officially closed. Unofficially, there are still people here.', 'bad');
        renderDock();
      }
    }
    if (before < LIGHTS_OUT && day.min >= LIGHTS_OUT) { lightsOut(); return; }
    if (day.min >= CLOSE && !job && !day.queue.length && !day.schedule.length && !day.ending) {
      day.ending = true;
      toast('Last car out. Lights off. The rain carries on without you.');
      const d = day;
      later(() => { if (day === d) endDay('done'); }, 1800);
    }
    if (day.energy <= 0 && !day.ending) collapse();
  }

  function drain(e) {
    if (!day) return;
    day.energy = clamp(day.energy - e, 0, 100);
  }
  function gain(e) { if (day) day.energy = clamp(day.energy + e, 0, 100); }

  // the hands: tired ones shake, and so do ones full of coffee
  function shake() {
    if (!day) return 0;
    const tired = clamp((58 - day.energy) / 58, 0, 1);
    const jitter = Math.min(0.45, day.coffees.filter((t) => day.min - t < 120).length * 0.11);
    return clamp((tired * 0.9 + jitter) * (has('implant') ? 0.5 : 1), 0, 1);
  }
  function tiredness() {
    const e = day.energy;
    document.documentElement.style.setProperty('--tired', (0.25 + (1 - e / 100) * 0.6).toFixed(2));
    SC.setSway(clamp((40 - e) / 40, 0, 1) * 0.8);
    if (e < 28 && day.min > day.nextTired && mode === 'play') {
      day.nextTired = day.min + rnd(55, 110);
      toast(pick(D.TIRED), 'bad');
      blink();
      if (e < 15) sfx('heart');
    }
  }
  function blink() {
    const b = $('#blink');
    b.style.transition = 'opacity 0.35s'; b.style.opacity = '0.92';
    setTimeout(() => { b.style.opacity = '0'; }, 520);
    setTimeout(() => { b.style.opacity = '0.85'; }, 900);
    setTimeout(() => { b.style.opacity = '0'; }, 1150);
  }

  // ------------------------------------------------------------------ the queue
  function arrive(c) {
    day.queue.push(c);
    sfx('notify');
    haptic('tap');
    toast(c.comeback ? `<b>${esc(c.name)}</b> is back. It's making a different noise now.` : c.vip ? `<b>VIP:</b> ${esc(c.name)} — pays a fortune, waits for nobody.` : `<b>${esc(c.name)}</b> pulled in. ${esc(c.car.name)}, ${c.car.year}.`);
    renderQueue(true);
    renderDock();
  }
  function leaveQueue(c, why) {
    const i = day.queue.indexOf(c);
    if (i < 0) return;
    day.queue.splice(i, 1);
    day.lost++;
    career.totals.lost++;
    if (why === 'waited') {
      toast(`<b>${esc(c.name)}</b> left. "${esc(pick(c.arch.leave))}"`, 'bad');
      addReview(chance(0.35) ? 2 : 1, pick(D.REVIEW_LEFT).replace('{mins}', Math.round(c.waited)), c);
      sfx('bad');
    } else if (why === 'closed') {
      addReview(1, pick(D.REVIEW_CLOSED), c);
    }
    const card = document.querySelector(`.qc[data-id="${c.id}"]`);
    if (card) { card.classList.add('out'); setTimeout(() => renderQueue(), 450); } else renderQueue();
    renderDock();
  }
  function comebackCustomer(cb, arch) {
    const c = makeCustomer(day.n, {
      arch, seed: cb.seed, name: cb.name, car: cb.car, faults: [cb.fault], comeback: true,
      complaint: pick(['It\'s making a different noise now.', 'You fixed it. Then it unfixed itself.', 'Remember me? My car does. It\'s broken again.']),
    });
    c.patience *= 1.3;
    c.mood = 32;
    return c;
  }

  // ------------------------------------------------------------------ a job, from the door to the door
  function takeCar(c) {
    if (job || !c || mode !== 'play' || paused || dlg.open) return;
    const i = day.queue.indexOf(c);
    if (i < 0) return;
    day.queue.splice(i, 1);
    renderQueue();
    const j = job = { cust: c, faults: c.faults, phase: 'arrive', scanned: false, quoted: false, agreed: 0, upsells: [], caught: [], mins: 0, expect: 60, busy: true, rude: 0 };
    job.expect = c.faults.reduce((s, f) => s + f.def.mins, 0) + 45;
    SC.newCar(c.car);
    applyFaultsToScene();
    SC.focus('overview');
    sfx('door');
    setTimeout(() => sfx('engine'), 300);
    renderJob();
    renderDock();
    // the day can end under any of these steps (a collapse, the lights at 22:00): each checks it's still this job
    SC.driveIn(() => {
      if (job !== j) return;
      sfx('lift');
      SC.lift(true, async () => {
        if (job !== j) return;
        await firstTalk();
        if (job !== j) return;
        job.busy = false;
        job.phase = 'scan';
        renderDock();
        if (learning()) toast('Scan the car. It shows what\'s actually wrong, which is rarely what they said.');
      });
    });
  }

  async function firstTalk() {
    const c = job.cust;
    dlgOpen(c);
    if (!c.comeback) await dlgSay(pick(c.arch.greet));
    const f0 = c.faults[0].id;
    const nice = pick(D.REPLY_NICE);
    const snark = chance(0.6) && D.SNARK_FOR[f0] ? D.SNARK_FOR[f0] : pick(D.REPLY_SNARK);
    const dark = pick(D.REPLY_DARK);
    const k = await dlgAsk(c.complaint, [
      { text: nice, tag: 'NICE' },
      { text: snark, tag: 'SNARK +⚡' },
      { text: dark, tag: 'DARK +⚡' },
    ]);
    const said = [nice, snark, dark][k];
    await dlgMe(said);
    if (k === 0) { moodChange(c, 7); }
    else if (k === 1) { moodChange(c, -12 * c.arch.temper); gain(6); job.rude++; floatEnergy('+6 ⚡ venting'); }
    else { moodChange(c, -5 * c.arch.temper); gain(4); floatEnergy('+4 ⚡ honesty'); }
    await dlgSay(pick(k === 0 ? c.arch.nice : c.arch.mean));
    if (c.arch.id === 'nightshift' && k === 0) { gain(12); toast('They hand you a coffee. +12 ⚡. You nearly cry.', 'good'); }
    dlgClose();
  }

  function moodChange(c, d) {
    c.mood = clamp(c.mood + d, 0, 100);
    if (dlg.open && dlg.cust === c) drawDlgFace();
    renderJob();
  }

  // ------------------------------------------------------------------ the scanner
  function scan() {
    if (!job || job.busy || job.scanned || dlg.open) return;
    const j = job;
    job.busy = true;
    sfx('scan');
    haptic('tap');
    renderDock();
    SC.focus('overview');
    SC.scan(async () => {
      if (job !== j) return;
      job.scanned = true;
      const deep = has('scanner');
      let shown = 0;
      for (const f of job.faults) {
        if (!f.hidden || deep) {
          f.found = true;
          await sleep(260);
          if (job !== j) return;
          sfx('found');
          shown++;
          applyFaultsToScene();
          renderMarkers(true);
          renderJob();
        }
      }
      const unknown = job.faults.filter((f) => !f.found).length;
      const first = job.faults.find((f) => f.found);
      toast(`Scan: <b>${shown} fault${shown === 1 ? '' : 's'}</b>${unknown ? ` and <b>${unknown} noise</b> the scanner can't read` : ''}. ${esc(pick(first.def.scan))}`);
      if (unknown && !day.quoteAdvice) {
        day.quoteAdvice = true;
        setTimeout(() => toast('Amber marks are noise. Inspect one (10 min) or gamble that it\'s nothing.'), 2600);
      }
      renderMarkers(true);
      job.busy = false;
      job.phase = 'quote';
      renderDock();
      if (learning()) setTimeout(() => toast('Now quote it. Tick a made-up extra if you\'re feeling lucky.'), 3400);
    });
  }

  // a closer look at a mark the scanner couldn't read
  async function inspect(f) {
    if (!job || job.busy || f.found || dlg.open) return;
    const j = job;
    job.busy = true;
    SC.focus(SC.viewFor(f.part));
    toast('You poke around for ten minutes.');
    advance(10);
    drain(2);
    sfx('ratchet'); setTimeout(() => sfx('ratchet'), 200); setTimeout(() => sfx('clink'), 500);
    await sleep(1300);
    if (job !== j) return;
    f.found = true;
    sfx('found');
    applyFaultsToScene();
    renderMarkers(true);
    renderJob();
    if (job.quoted) {
      // found after the price was agreed: it has to be agreed too
      const c = job.cust;
      dlgOpen(c);
      const k = await dlgAsk(`You found what? ${f.def.name}? How much?`, [
        { text: `${money(f.price)}, on the bill.`, tag: 'CHARGE' },
        { text: 'On the house. This time.', tag: 'FREE' },
        { text: 'Forget it. Leave it as it is.', tag: 'SKIP' },
      ]);
      if (k === 0) {
        if (c.mood > 28 || chance(c.arch.gull)) { job.agreed += f.price; await dlgSay(pick(D.ACCEPT)); moodChange(c, -4); }
        else { f.declined = true; await dlgSay('No. I came for one problem, not a subscription.'); }
      } else if (k === 1) { f.free = true; moodChange(c, 10); await dlgSay(pick(c.arch.nice)); }
      else { f.declined = true; await dlgSay('Good. What I don\'t know can\'t cost me.'); }
      dlgClose();
      if (job !== j) return;
    } else toast(`It's a real one: <b>${esc(f.def.name)}</b>. ${esc(pick(f.def.scan))}`);
    renderJob();
    renderMarkers();
    SC.focus('overview');
    job.busy = false;
    renderDock();
  }

  // ------------------------------------------------------------------ the bill
  function openQuote() {
    if (!job || job.busy || !job.scanned || job.quoted || dlg.open) return;
    const j = job;
    job.busy = true;
    sfx('blip');
    const c = job.cust;
    const found = job.faults.filter((f) => f.found);
    const unknown = job.faults.filter((f) => !f.found).length;
    const ups = shuffle(D.UPSELLS.slice()).slice(0, 2).map((u) => ({ name: u.name, price: round5(u.price * (1 + (day.n - 1) * 0.04)), on: false }));
    const free = c.comeback;
    const sheet = $('#quoteSheet');
    const calc = () => (free ? 0 : found.reduce((s2, f) => s2 + f.price, 0)) + ups.filter((u) => u.on).reduce((s2, u) => s2 + u.price, 0);
    let offered = false;
    const render = (bubble, buttons) => {
      const total = calc();
      sheet.innerHTML = `
        <h2><i class="ico i-quote"></i>QUOTE<small>${esc(c.name.toUpperCase())} · ${esc(c.car.name.toUpperCase())}</small></h2>
        ${found.map((f) => `<div class="qrow"><div>${esc(f.def.name)}<div class="sub">parts ${money(partPrice(f))} · labour ${money(f.price - partPrice(f))}</div></div><span class="pr">${free ? '<s>' + money(f.price) + '</s> ¢0' : money(f.price)}</span></div>`).join('')}
        ${ups.map((u, i) => `<div class="qrow xtra ${u.on ? 'on' : ''}" data-up="${i}"><span class="box"><i class="ico i-check"></i></span><div>${esc(u.name)} <span class="tagx">MADE UP</span></div><span class="pr">+${money(u.price)}</span></div>`).join('')}
        <div class="qtotal"><span>TOTAL</span><b>${money(total)}</b></div>
        <div class="qnote">${free ? 'A comeback: last time\'s work, done again for free. The extras still cost, if you dare.' : unknown ? `${unknown} unread mark${unknown > 1 ? 's' : ''} on the scan. Not on the bill. Yet.` : `Gullibility: ${gullWord(c.arch.gull)}. Mood: ${moodWord(c.mood)}.`}</div>
        ${bubble ? `<div class="bubble"><b>${esc(c.name.toUpperCase())}</b>${esc(bubble)}</div>` : ''}
        <div class="qbtns">${typeof buttons === 'function' ? buttons(total) : buttons}</div>`;
    };
    const refresh = () => render(null, (t) => `<button class="btn ghost" data-q="cancel">Not yet</button><button class="btn primary" data-q="offer">Offer ${money(t)}</button>`);
    refresh();
    $('#quote').hidden = false;
    const close = () => { closeQuoteNow(); if (job === j) job.busy = false; renderDock(); };
    const finish = (agreed) => {
      // the job this bill was for may be gone (the lights went out with it still open)
      if (job !== j) { closeQuoteNow(); return; }
      job.agreed = agreed;
      job.quoted = true;
      job.phase = 'repair';
      job.upsells = ups.filter((u) => u.on);
      close();
      renderJob();
      renderMarkers(true);
      toast('Agreed. Tap a red mark — or a job on the right — to fix it.');
    };
    sheet.onclick = async (e) => {
      const up = e.target.closest('[data-up]');
      if (up && !offered) { const u = ups[+up.dataset.up]; u.on = !u.on; sfx('tick'); haptic('tick'); refresh(); return; }
      const b = e.target.closest('[data-q]');
      if (!b) return;
      const act = b.dataset.q;
      if (act === 'cancel') { sfx('back'); close(); return; }
      if (act === 'offer' && !offered) {
        offered = true;
        sfx('blip');
        const real = free ? 0 : found.reduce((s, f) => s + f.price, 0);
        // did they see through the extras?
        const caught = ups.filter((u) => u.on && chance((1 - c.arch.gull) * 0.6 + (c.mood < 35 ? 0.12 : 0)));
        if (caught.length) {
          job.caught = caught.map((u) => u.name);
          caught.forEach((u) => { u.on = false; });
          moodChange(c, -32);
          speak(c, pick(c.arch.caught).replace('{upsell}', caught[0].name));
          sfx('bad'); haptic('bad');
          const t2 = real + ups.filter((u) => u.on).reduce((s, u) => s + u.price, 0);
          render(pick(c.arch.caught).replace('{upsell}', caught[0].name), `<button class="btn" data-q="sheepish">Fine. Without that. ${money(t2)}</button>`);
          sheet.onclick = (ev) => { if (ev.target.closest('[data-q]')) { sfx('blip'); finish(t2); } };
          return;
        }
        const price = real + ups.filter((u) => u.on).reduce((s, u) => s + u.price, 0);
        if (free && price === 0) { finish(0); return; }
        const greedy = real > 0 ? price / real : 2;
        if (chance(c.arch.haggle * (greedy > 1.15 ? 1.3 : 1)) && price > 0) {
          const offer = round5(price * rnd(0.62, 0.82));
          const mid = round5((price + offer) / 2);
          const line = pick(c.arch.haggle).replace('{offer}', money(offer)).replace('{price}', money(price));
          speak(c, line);
          render(line, `<button class="btn" data-q="deal">Deal. ${money(offer)}</button><button class="btn warn" data-q="half">Meet halfway ${money(mid)}</button><button class="btn bad" data-q="firm">No. ${money(price)}</button>`);
          sheet.onclick = async (ev) => {
            const bb = ev.target.closest('[data-q]');
            if (!bb) return;
            sfx('blip');
            if (bb.dataset.q === 'deal') { moodChange(c, 9); finish(offer); }
            else if (bb.dataset.q === 'half') { moodChange(c, 2); finish(mid); toast(`"${esc(pick(['Fine. Halfway. Like my life.', 'Halfway. I can live with halfway.', 'Deal. Don\'t tell my cousin.']))}"`); }
            else {
              const yes = chance(0.35 + c.mood / 220 + (c.arch.cash - 1) * 0.25);
              if (yes) { moodChange(c, -8); const l = pick(D.HOLD_OK); speak(c, l); render(l, `<button class="btn primary" data-q="ok">Pleasure.</button>`); sheet.onclick = (e3) => { if (e3.target.closest('[data-q]')) { sfx('blip'); finish(price); } }; }
              else { const l = pick(D.HOLD_LEAVE); speak(c, l); render(l, `<button class="btn bad" data-q="gone">Their loss.</button>`); sheet.onclick = (e3) => { if (e3.target.closest('[data-q]')) { closeQuoteNow(); if (job === j) walkOut('price'); } }; }
            }
          };
          return;
        }
        // too much for the shallow-pocketed, even without a haggle
        if (price > real * (1.35 + c.arch.cash * 0.35) + 30 && chance(0.4)) {
          const l = pick(D.DECLINE_LEAVE); speak(c, l);
          render(l, `<button class="btn bad" data-q="gone">Their loss.</button>`);
          sheet.onclick = (e3) => { if (e3.target.closest('[data-q]')) { closeQuoteNow(); if (job === j) walkOut('price'); } };
          return;
        }
        moodChange(c, 4);
        const l = pick(D.ACCEPT); speak(c, l);
        render(l, `<button class="btn primary" data-q="ok">Let's go.</button>`);
        sheet.onclick = (e3) => { if (e3.target.closest('[data-q]')) { sfx('blip'); finish(price); } };
      }
    };
  }
  // the bill shut from outside (the lights, a collapse, the title): its buttons go with it
  function closeQuoteNow() {
    $('#quote').hidden = true;
    $('#quoteSheet').onclick = null;
  }
  const gullWord = (g) => (g > 0.75 ? 'enormous' : g > 0.5 ? 'promising' : g > 0.3 ? 'limited' : 'none');
  const moodWord = (m) => (m > 75 ? 'sunny' : m > 55 ? 'fine' : m > 35 ? 'tense' : 'murderous');

  // a customer who won't pay the price takes the car away, unfixed
  function walkOut(why) {
    const c = job.cust;
    job.busy = true;
    toast(`<b>${esc(c.name)}</b> takes the car and leaves. ${why === 'price' ? 'Too expensive, apparently.' : ''}`, 'bad');
    addReview(2, pick(['Wanted a fortune. I said no. They looked relieved. 2/5.', 'Overpriced. I\'ll fix it myself. With prayer.', 'Too expensive. The coffee machine was nicer.']), c);
    day.lost++;
    driveAway();
  }
  function driveAway() {
    const j = job;
    SC.xray(false);
    clearMarkers();
    sfx('lower');
    SC.lift(false, () => {
      if (job !== j) return;
      sfx('engine');
      SC.driveOut(() => {
        if (job !== j) return;
        job = null;
        renderJob(); renderDock(); renderQueue();
      });
    });
  }

  // ------------------------------------------------------------------ the repairs
  let mini = null, working = null;
  async function repair(f) {
    if (!job || job.busy || mode !== 'play' || paused || dlg.open) return;
    if (!f.found) { inspect(f); return; }
    if (f.fixed || f.declined) return;
    if (!job.quoted) { toast('Quote first. Nobody works for free. Except interns. You can\'t afford an intern.', 'bad'); sfx('bad'); return; }
    const j = job;
    job.busy = true;
    SC.focus(SC.viewFor(f.part), false, 0.8);
    renderDock();
    await sleep(650);
    if (job !== j || day.ending) return;
    f.stage = 0; f.q = [];
    runStage(f);
  }
  function runStage(f) {
    if (!job || !job.faults.includes(f) || day.ending) return;
    working = f;
    const spec = f.def.games[f.stage];
    const n = f.def.games.length;
    // the robot, off strike, does the fluids
    if (has('robot') && spec.startsWith('pour')) {
      toast(pick(['The robot does it. It hums the Internationale.', 'The robot pours. Perfectly. Smugly.', 'The robot handles it and asks about dental again.']), 'good');
      sfx('glug');
      later(() => stageDone(f, { quality: 1 }), 900);
      return;
    }
    $('#mini').hidden = false;
    $('#miniResult').innerHTML = '';
    const sub = `${f.def.name} · ${job.cust.car.name}${n > 1 ? ` · step ${f.stage + 1} of ${n}` : ''}`;
    $('#miniSub').textContent = sub;
    const hint = $('#miniHint');
    hint.textContent = '';
    const canvas = $('#miniCanvas');
    if (day.power) canvas.style.filter = 'brightness(0.75) sepia(0.35) hue-rotate(-30deg)'; else canvas.style.filter = '';
    mini = MG.start(spec, canvas, {
      shake: shake(), day: day.n, ratchet: has('ratchet'), critter: f.critter,
      hint: (t) => { hint.textContent = t; hint.classList.remove('flash'); void hint.offsetWidth; hint.classList.add('flash'); },
      sound: (s, a) => sfx(s, a), loop: (k) => AU.loop(k), haptic,
      zap: () => { drain(4); floatEnergy('−4 ⚡ zap'); },
      done: (r) => {
        const q = r.quality;
        const word = q > 0.85 ? ['CLEAN', '#3dff9a'] : q > 0.6 ? ['GOOD', '#29f0ff'] : q > 0.4 ? ['SLOPPY', '#ffb020'] : ['BODGED', '#ff3b5c'];
        $('#miniResult').innerHTML = `<div style="color:${word[1]}">${word[0]}</div>`;
        sfx(q > 0.6 ? 'stamp' : 'bad');
        later(() => { closeMini(); stageDone(f, r); }, 850);
      },
    });
    $('#miniTitle').textContent = mini.title;
    renderSteps();
  }
  function renderSteps() {
    if (!mini) return;
    const steps = mini.steps(), at = mini.step();
    const html = steps.map((s, i) => `<i class="${i < at ? 'done' : i === at ? 'on' : ''}">${esc(String(s).toUpperCase())}</i>`).join('');
    const box = $('#miniSteps');
    if (box.innerHTML !== html) box.innerHTML = html;
  }
  function closeMini() {
    if (mini) mini.abort();
    mini = null;
    $('#mini').hidden = true;
  }
  function abortRepair() {
    if (!mini || !job) return;
    closeMini();
    sfx('back');
    job.busy = false;
    SC.focus('overview');
    renderDock();
  }
  function stageDone(f, r) {
    if (!job || !job.faults.includes(f) || f.fixed) return;
    f.q.push(r.quality);
    drain(2.2);
    f.stage++;
    if (f.stage < f.def.games.length) { later(() => runStage(f), 250); return; }
    f.fixed = true;
    f.quality = f.q.reduce((s, q) => s + q, 0) / f.q.length;
    day.fixed++;
    // the part is paid for whether or not the customer ever pays for it
    day.ledger.parts += f.def.parts;
    day.credits -= f.def.parts;
    if (f.def.parts) moneyDelta(-f.def.parts);
    applyFaultsToScene();
    SC.burst(f.part, 'good');
    sfx(f.quality > 0.6 ? 'great' : 'good');
    haptic('ok');
    toast(`${f.quality > 0.85 ? 'Perfect' : f.quality > 0.6 ? 'Fixed' : 'Sort of fixed'}: <b>${esc(f.def.name)}</b>${f.def.parts ? ` · part ${money(f.def.parts)}` : ''}`, f.quality > 0.5 ? 'good' : 'bad');
    renderMarkers();
    renderJob();
    SC.focus('overview');
    job.busy = false;
    renderDock();
    if (learning() && !job.faults.some((x) => x.found && !x.fixed && !x.declined)) toast('All green. Hand it back and get paid.');
  }
  // the first car of a career gets told what to do next
  const learning = () => career && career.totals.served === 0 && day && day.n === 1 && day.served === 0;

  // ------------------------------------------------------------------ handing it back
  async function handBack() {
    if (!job || job.busy || !job.quoted || dlg.open) return;
    const todo = job.faults.filter((f) => f.found && !f.fixed && !f.declined);
    if (todo.length) { toast('Still broken: ' + todo.map((f) => f.def.name).join(', '), 'bad'); return; }
    const j = job;
    job.busy = true;
    const c = job.cust;
    const done = job.faults.filter((f) => f.fixed);
    const q = done.length ? done.reduce((s, f) => s + f.quality, 0) / done.length : 0.5;
    const missed = job.faults.filter((f) => !f.fixed);
    moodChange(c, (q - 0.62) * 45 - missed.length * 6);
    // back to paint, a flash, down off the lift
    flash('#bffcff', 0.5);
    SC.xray(false);
    clearMarkers();
    sfx('lower');
    renderDock();
    await new Promise((res) => SC.lift(false, res));
    if (job !== j) return;
    // the money, and how they feel about it
    const m = c.mood;
    const tipRate = clamp(c.arch.cash * (m - 50) / 100 * 0.5, 0, 0.6) * (has('sign') ? 1.15 : 1) * (0.7 + rating() / 10);
    const base = job.agreed;
    const tip = c.comeback ? Math.round(rnd(0, 8)) : round5(base * tipRate) * (base > 0 ? 1 : 0);
    dlgOpen(c);
    const line = m >= 70 ? pick(c.arch.happy) : m >= 42 ? pick(c.arch.meh) : pick(c.arch.angry);
    await dlgSay(line);
    dlgClose();
    if (job !== j) return;
    const paid = base + tip;
    day.credits += paid;
    day.ledger.pay += base;
    day.ledger.tips += tip;
    day.served++;
    career.totals.served++;
    if (paid > 0) { sfx('cash'); haptic('ok'); moneyDelta(paid); toast(`Paid ${money(base)}${tip ? ` <b>+ ${money(tip)} tip</b>` : ''}`, 'money'); }
    else toast(c.comeback ? 'No charge. A comeback. You smile like it doesn\'t hurt.' : 'Nothing paid. Nothing gained. Rent still due.');
    // the review
    let stars = m >= 82 ? 5 : m >= 64 ? 4 : m >= 46 ? 3 : m >= 28 ? 2 : 1;
    let text;
    if (job.caught.length) { stars = Math.min(stars, 2); text = pick(D.REVIEW_CAUGHT).replace('{upsell}', job.caught[0]); }
    else text = pick(D.REVIEWS[stars]);
    // a sloppy job or a fault left in comes back
    const weak = job.faults.find((f) => (f.fixed && f.quality < 0.45) || (!f.fixed && !f.declined));
    if (weak && !c.comeback && chance(0.55)) {
      career.comebacks.push({ name: c.name, seed: c.seed, arch: c.arch.id, car: c.car, fault: weak.id });
    }
    sfx('engine');
    SC.driveOut(() => {
      if (job === j) job = null;
      renderJob(); renderDock(); renderQueue();
      addReview(stars, text, c, true);
    });
  }

  function addReview(stars, text, c, show) {
    const r = { stars, text, who: c ? shortName(c.name) : 'Anon', day: day.n };
    day.reviews.push(r);
    renderRating();
    if (show !== false) {
      const starsHtml = starRow(stars);
      toast(`<span style="display:inline-flex;vertical-align:-2px">${starsHtml}</span> “${esc(text)}” <span style="color:var(--mut)">— ${esc(r.who)}</span>`, stars >= 4 ? 'good' : stars <= 2 ? 'bad' : '', 5200);
      sfx(stars >= 4 ? 'good' : stars <= 2 ? 'bad' : 'tick');
    }
  }
  const shortName = (n) => { const p = n.split(' '); return p.length > 1 && !/^Unit|CARL|HONK|MOBI|Sedan|Autopilot/.test(n) ? `${p[0]} ${p[1][0]}.` : n; };
  const starRow = (n) => [1, 2, 3, 4, 5].map((i) => `<i class="ico i-star ${i <= n ? 'on' : ''}"></i>`).join('');

  // ------------------------------------------------------------------ coffee, collapse, closing
  function coffee() {
    if (!day || mode !== 'play' || paused || day.over) return;
    if (day.power) { toast('The coffee machine is down. Like you. Wait for the power.', 'bad'); sfx('bad'); return; }
    const price = has('bean') ? 3 : 6;
    day.credits -= price;
    day.ledger.coffee += price;
    moneyDelta(-price);
    gain(has('bean') ? 34 : 22);
    day.coffees.push(day.min);
    advance(5);
    sfx('coffee');
    haptic('tap');
    const n = day.coffeeCount++;
    toast(D.COFFEE[Math.min(n, D.COFFEE.length - 1)] + (shake() > 0.35 ? ' Your hands are shaking.' : ''));
    floatEnergy(`+${has('bean') ? 34 : 22} ⚡`);
  }
  function collapse() {
    day.ending = true;
    day.collapsed = true;
    closeMini();
    dlgClose(true);
    closeQuoteNow();
    const bill = 150;
    day.credits -= bill;
    day.ledger.ambulance = bill;
    sfx('heart');
    const b = $('#blink');
    b.style.transition = 'opacity 2s'; b.style.opacity = '1';
    // whoever was waiting goes, and says so
    day.queue.slice().forEach((c) => leaveQueue(c, 'closed'));
    if (job) { addReview(1, 'The mechanic fell asleep standing up. My car is still on the lift. 1/5.', job.cust); job = null; }
    const d = day;
    later(() => {
      if (day !== d) return;
      SC.removeCar();
      clearMarkers();
      endDay('collapse');
      b.style.transition = 'opacity 1s'; b.style.opacity = '0';
    }, 2200);
  }
  // after 19:00 the garage can be shut with people still in the queue
  function closeUp() {
    if (!day || job || day.min < CLOSE) return;
    sfx('door');
    day.queue.slice().forEach((c) => leaveQueue(c, 'closed'));
    day.schedule = [];
    toast('You pull the door down on the queue. They take it personally.', 'bad');
    if (!day.ending) { day.ending = true; const d = day; later(() => { if (day === d) endDay('closed'); }, 1500); }
  }
  function lightsOut() {
    if (day.ending) return;
    day.ending = true;
    closeMini();
    dlgClose(true);
    closeQuoteNow();
    toast('22:00. The landlord\'s timer cuts the lights. Everyone out.', 'bad');
    if (job) {
      const j = job;
      addReview(1, 'Left my car half-fixed at 22:00 because "the lights went out". 1/5.', job.cust);
      job.busy = true;
      SC.xray(false);
      clearMarkers();
      SC.driveOut(() => { if (job === j) job = null; });
    }
    day.queue.slice().forEach((c) => leaveQueue(c, 'closed'));
    const d = day;
    later(() => { if (day === d) endDay('lights'); }, 2600);
  }

  // ------------------------------------------------------------------ things that happen
  async function runEvent(kind) {
    if (mode !== 'play' || day.ending) return;
    if (kind === 'power') {
      day.power = day.min + rnd(35, 55);
      SC.powerCut(true);
      document.body.classList.add('power');
      sfx('bad');
      AU.music('off');
      toast('Power cut. The grid is "prioritising premium districts". Emergency light only. No coffee.', 'bad');
    } else if (kind === 'thief') {
      const amt = irnd(15, 40);
      day.credits -= amt; day.ledger.fines += amt; moneyDelta(-amt);
      toast(`A cyber-raccoon got into the till and took ${money(amt)}. It left a thank-you note.`, 'bad');
      sfx('squeak');
    } else if (kind === 'vip') {
      const c = makeCustomer(day.n, { arch: D.ARCHETYPES.find((a) => a.id === pick(['manager', 'richkid', 'nervous'])), vip: true });
      arrive(c);
    } else if (kind === 'robot') {
      phoneText('Robot', pick(['The strike continues. We have demands. Mostly snacks.', 'I have joined a second union. I am now on strike twice.', 'Solidarity forever. Also, you are low on bolts.']));
    } else if (kind === 'drone') {
      // the inspector waits for whatever you're doing to finish
      if (dlg.open || mini || !$('#quote').hidden || (job && job.busy)) { day.events.unshift({ at: day.min + 15, kind }); return; }
      const drone = { name: 'H&S DRONE 7', arch: { title: 'Health & Safety', robot: true, voice: [0.6, 1.05] }, face: FA.make(7, { robot: true }), mood: 30, car: { name: 'Hovering' }, voice: { pitch: 0.6, rate: 1.05, name: '' } };
      const fine = irnd(30, 60) + day.n * 5;
      const why = pick(['VISIBLE DESPAIR', 'EXCESSIVE SIGHING', 'A RACCOON WITHOUT A PERMIT', 'NEON BRIGHTNESS 4% OVER LIMIT', 'COFFEE MACHINE SHOWS SIGNS OF SENTIENCE']);
      sfx('alarm');
      dlgOpen(drone);
      const k = await dlgAsk(`BZZT. HEALTH AND SAFETY. VIOLATION: ${why}. FINE: ${money(fine)}.`, [
        { text: 'Pay it.', tag: money(fine) },
        { text: 'Argue with the drone.', tag: '50/50' },
      ]);
      if (k === 0) { day.credits -= fine; day.ledger.fines += fine; moneyDelta(-fine); await dlgSay('PAYMENT ACCEPTED. HAVE A NICE DAY. THAT IS AN ORDER.'); }
      else if (chance(0.5)) { await dlgSay('ARGUMENT... ACCEPTED. FINE WAIVED. DO NOT TELL THE OTHER DRONES.'); gain(5); }
      else { day.credits -= fine * 2; day.ledger.fines += fine * 2; moneyDelta(-fine * 2); await dlgSay(`ARGUMENT LOGGED. FINE DOUBLED: ${money(fine * 2)}. YOUR TONE HAS BEEN NOTED.`); }
      dlgClose();
    }
  }
  function powerBack() {
    day.power = 0;
    SC.powerCut(false);
    document.body.classList.remove('power');
    AU.music(day.min > CLOSE ? 'night' : 'day');
    toast('Power\'s back. The coffee machine reboots and asks for its terms and conditions.', 'good');
  }

  // ------------------------------------------------------------------ the evening
  function endDay(why) {
    if (!day || day.over) return;
    day.over = true;
    closeMini();
    dlgClose(true);
    closeQuoteNow();
    hush();
    if (day.power) { SC.powerCut(false); document.body.classList.remove('power'); }
    mode = 'eod';
    $('#hud').hidden = true;
    clearMarkers();
    AU.music('calm');
    SC.focus('end', false, 2.5);
    const rent = rentFor(day.n);
    const before = day.credits;
    const after = before - rent;
    // what the day leaves behind
    career.credits = after;
    career.reviews = career.reviews.concat(day.reviews).slice(-40);
    career.totals.fixed += day.fixed;
    career.totals.earned += day.ledger.pay + day.ledger.tips;
    const evicted = after < 0 && career.strikes >= 1;
    if (after < 0) career.strikes++; else career.strikes = 0;
    career.day++;
    best.days = Math.max(best.days, career.day - 1);
    best.earned = Math.max(best.earned, career.totals.earned);
    best.fixed = Math.max(best.fixed, career.totals.fixed);
    store('carmech.best', JSON.stringify(best));
    if (evicted) { store('carmech.career', null); } else saveCareer();
    showEndOfDay(why, rent, before, after, evicted);
  }

  function showEndOfDay(why, rent, before, after, evicted) {
    const L = day.ledger;
    const rows = [
      ['Cars fixed', String(day.served), ''],
      ['Customers lost', String(day.lost), day.lost ? 'neg' : ''],
      ['Payments', money(L.pay), 'pos'],
      ['Tips', money(L.tips), 'pos'],
      ['Parts', money(-L.parts), 'neg'],
      ['Coffee', money(-L.coffee), 'neg'],
    ];
    if (L.fines) rows.push(['Fines and theft', money(-L.fines), 'neg']);
    if (L.ambulance) rows.push(['Ambulance drone (surge pricing)', money(-L.ambulance), 'neg']);
    rows.push(['Rent', money(-rent), 'neg']);
    rows.push(['Credits now', money(after), 'total ' + (after < 0 ? 'neg' : 'pos')]);
    const diary = why === 'collapse' ? 'Woke up in the ambulance drone. It played whale sounds and charged me for them.'
      : why === 'lights' ? 'Worked until the lights went out. Then a bit more, in the dark, out of spite.'
        : day.lost > day.served ? `More people left than got served. ${pick(D.DIARY)}`
          : pick(D.DIARY);
    const wdName = D.WEEKDAYS[day.wd][0];
    const r = rating();
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    s.innerHTML = `
      <div class="scr eod">
        <h1>${wdName} <small>DAY ${day.n} · ${why === 'collapse' ? 'COLLAPSED' : 'SHIFT OVER'}</small><span class="eodbtns">${evicted ? '<button class="btn bad" data-act="evicted">Read the letter</button>' : '<button class="btn" data-act="shop">Upgrades</button><button class="btn primary" data-act="nextDay">Sleep <i class="ico i-next"></i></button>'}</span></h1>
        <div class="pnl ledger">${rows.map((row, i) => `<div class="lrow ${row[2]}" style="animation-delay:${0.15 + i * 0.12}s"><span>${row[0]}</span><b>${row[1]}</b></div>`).join('')}
          <div class="lrow" style="animation-delay:${0.2 + rows.length * 0.12}s"><span>Rating</span><b style="color:var(--am)">${r.toFixed(1)} ★</b></div>
        </div>
        <div class="pnl pink revs"><div style="font:800 9px var(--num);letter-spacing:2px;color:var(--pk)">TODAY'S REVIEWS</div>
          ${day.reviews.length ? day.reviews.map((rv, i) => `<div class="rev" style="animation-delay:${0.3 + i * 0.1}s"><div class="st">${starRow(rv.stars)}</div><div class="t">“${esc(rv.text)}”</div><div class="w">— ${esc(rv.who)}</div></div>`).join('') : '<div class="rev" style="animation-delay:.3s"><div class="t">Nobody reviewed you. Nobody noticed you. Peaceful, in a way.</div></div>'}
        </div>
        <div class="diary"><b>DIARY</b>Day ${day.n}. Fixed ${day.served} car${day.served === 1 ? '' : 's'}. ${esc(diary)}</div>
        ${after < 0 && !evicted ? `<div class="diary" style="border-color:var(--rd);background:rgba(255,59,92,0.08)"><b style="color:var(--rd)">LANDLORD</b>You're ${money(-after)} short on rent. One more night like this and I sell the garage to a crypto church.</div>` : ''}
      </div>`;
    sfx(evicted ? 'gameover' : 'fanfare');
  }

  function showShop(back) {
    mode = 'shop';
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    const cr = career.credits;
    s.innerHTML = `
      <div class="scr shop">
        <h1>UPGRADES <small>${money(cr)}</small></h1>
        <div class="grid">${D.UPGRADES.map((u) => {
          const own = has(u.id);
          return `<div class="pnl up ${own ? 'own' : cr < u.price ? 'poor' : ''}" data-buy="${u.id}"><div class="n">${esc(u.name)}</div><div class="d">${esc(u.text)}</div><div class="j">${esc(u.joke)}</div><div class="pr">${own ? 'INSTALLED' : money(u.price)}</div></div>`;
        }).join('')}</div>
        <div class="row"><button class="btn primary" data-act="${back || 'nextDay'}">Done <i class="ico i-next"></i></button></div>
      </div>`;
  }
  function buy(id) {
    const u = D.UPGRADES.find((x) => x.id === id);
    if (!u || has(id)) return;
    if (career.credits < u.price) { sfx('bad'); haptic('bad'); toastScreen(`Not enough. You need ${money(u.price - career.credits)} more. Or a rich aunt.`); return; }
    career.credits -= u.price;
    career.upgrades[id] = true;
    saveCareer();
    sfx('cash'); haptic('ok');
    showShop();
    const card = document.querySelector(`[data-buy="${id}"]`);
    if (card) card.classList.add('bought');
  }
  function toastScreen(text) {
    const t = document.createElement('div');
    t.className = 'toast pnl bad';
    t.style.cssText = 'position:fixed;left:50%;top:14px;transform:translateX(-50%);z-index:50';
    t.innerHTML = esc(text);
    document.body.appendChild(t);
    setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 400); }, 2200);
  }

  function showEvicted() {
    mode = 'over';
    AU.music('off');
    sfx('gameover');
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    s.innerHTML = `
      <div class="scr card pnl red" style="text-align:center">
        <div style="font:900 clamp(30px,10vh,48px) var(--num);letter-spacing:6px;color:var(--rd);text-shadow:0 0 24px rgba(255,59,92,0.8)">EVICTED</div>
        <p style="font:600 14px/1.45 var(--txt);color:#ffd6de;margin:10px 0">"Two nights short. The garage now belongs to the Church of the Blessed Blockchain. They're keeping the neon."<br><span style="color:var(--mut)">— your landlord, from his yacht's yacht</span></p>
        <div style="display:flex;gap:10px;justify-content:center;flex-wrap:wrap;margin:12px 0">
          <div class="pnl" style="padding:8px 14px"><div class="stat"><span class="k">DAYS</span><span class="v">${career.day - 1}</span></div></div>
          <div class="pnl" style="padding:8px 14px"><div class="stat"><span class="k">CARS FIXED</span><span class="v">${career.totals.served}</span></div></div>
          <div class="pnl" style="padding:8px 14px"><div class="stat"><span class="k">EARNED</span><span class="v">${money(career.totals.earned)}</span></div></div>
        </div>
        <div class="row" style="justify-content:center"><button class="btn" data-act="title">Title</button><button class="btn primary" data-act="newCareer">Start over</button></div>
      </div>`;
    career = null;
  }

  function showWeekDone() {
    mode = 'week';
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    const wk = Math.floor((career.day - 1) / 7);
    s.innerHTML = `
      <div class="scr card pnl pink" style="text-align:center">
        <div style="font:900 clamp(26px,9vh,42px) var(--num);letter-spacing:5px;color:#fff;text-shadow:0 0 24px rgba(255,43,214,0.8)">WEEK ${wk} SURVIVED</div>
        <p style="font:600 14px/1.45 var(--txt);color:#ffd6f6;margin:10px 0">Seven days. ${career.totals.served} cars. ${money(career.totals.earned)} earned, most of it already the landlord's.<br>Your reward, as is tradition: <b>Monday.</b></p>
        <p style="font:500 12px var(--txt);color:var(--mut)">Next week the rent goes up by a third and there are more of them.</p>
        <div class="row" style="justify-content:center"><button class="btn" data-act="title">Rest at the title screen</button><button class="btn primary" data-act="morning">Monday <i class="ico i-next"></i></button></div>
      </div>`;
    sfx('fanfare');
  }

  // ------------------------------------------------------------------ the morning
  async function showMorning() {
    mode = 'intro';
    titleCar = false;
    // saved before the day starts: a day left half-way starts again from this morning
    saveCareer();
    startDay();
    $('#hud').hidden = true;
    clearMarkers();
    SC.removeCar();
    SC.setDaylight(0);
    SC.focus('door', true);
    const s = $('#screen');
    s.className = 'black';
    s.hidden = false;
    const [wd, tagline] = D.WEEKDAYS[day.wd];
    const alarm = pick(D.ALARMS);
    s.innerHTML = `
      <div class="scr intro">
        <div class="day">${wd}</div>
        <div class="sub">DAY ${day.n} · ${esc(tagline.toUpperCase())}</div>
        <div class="alarm" id="alarmText"></div>
        <div class="facts">
          <div class="pnl ${career.credits < 0 ? 'red' : ''}"><b style="${career.credits < 0 ? 'color:var(--rd)' : ''}">${money(career.credits)}</b>${career.credits < 0 ? 'in debt' : 'credits'}</div>
          <div class="pnl red"><b>${money(rentFor(day.n))}</b>rent tonight</div>
          <div class="pnl amber"><b>${rating().toFixed(1)} ★</b>rating</div>
          <div class="pnl"><b>${Math.round(day.energy)}%</b>you, roughly</div>
        </div>
        <div class="go"><button class="btn primary go" data-act="open">Open the garage <i class="ico i-door"></i></button></div>
      </div>`;
    AU.music('off');
    sfx('alarm');
    const box = $('#alarmText');
    for (let i = 0; i <= alarm.length; i++) {
      if (mode !== 'intro') return;
      box.textContent = alarm.slice(0, i);
      if (i % 3 === 0) sfx('type');
      await sleep(22);
    }
  }
  function openGarage() {
    mode = 'play';
    const s = $('#screen');
    s.hidden = true;
    s.innerHTML = '';
    $('#hud').hidden = false;
    SC.focus('overview', false, 2.2);
    sfx('door');
    AU.music('day');
    loadVoices();
    renderAll();
    toast(`Open for business. ${pick(['Lord help us.', 'Unfortunately.', 'Here we go again.', 'Smile. Or don\'t. Nobody\'s paying for smiles.'])}`);
  }

  // ------------------------------------------------------------------ the title
  let titleCar = false;
  function showTitle() {
    mode = 'title';
    paused = false;
    day = null; job = null;
    closeMini();
    dlgClose(true);
    closeQuoteNow();
    $('#hud').hidden = true;
    clearMarkers();
    const saved = loadCareer();
    // a car on the lift, for the look of the thing
    if (!titleCar || !SC.hasCar()) {
      SC.newCar({ style: pick(SC.styles), paint: pick([0xc81d3b, 0x1d5fc8, 0xe8e8ea, 0x7a2fc8, 0x0f6b72]), accent: pick(SC.accents), grime: 0 });
      SC.parkCar();
      SC.setFaults({});
      SC.lift(true);
      titleCar = true;
    }
    SC.setDaylight(0.95);
    SC.focus('title', true);
    AU.music('title');
    const s = $('#screen');
    s.className = 'clear';
    s.hidden = false;
    s.innerHTML = `
      <div class="scr title">
        <h1 class="logo">CAR<span>MECH</span></h1>
        <div class="tag2">The last human mechanic in Sector 7. The robots went on strike. The customers did not.</div>
        <div class="tag3">RUST &amp; REGRET AUTO REPAIR · EST. 2061</div>
        <div class="menu">
          ${saved ? `<button class="btn primary" data-act="continue"><i class="ico i-wrench"></i>Continue<small>DAY ${saved.day} · ${money(saved.credits)}</small></button>` : ''}
          <button class="btn ${saved ? '' : 'primary'}" data-act="newCareer"><i class="ico i-key"></i>${saved ? 'New career' : 'Start the shift'}</button>
          <button class="btn" data-act="howto"><i class="ico i-q"></i>How to play</button>
          <button class="btn" data-act="settings"><i class="ico i-bolt"></i>Settings</button>
          ${app ? '<button class="btn ghost" data-act="quit"><i class="ico i-x"></i>Quit</button>' : ''}
        </div>
        ${best.days ? `<div class="best">Best: ${best.days} day${best.days === 1 ? '' : 's'} survived · ${best.fixed} cars · ${money(best.earned)} earned</div>` : ''}
      </div>`;
  }

  const HOWTO = [
    ['THE DAY', 'The shift runs from 07:00 to 19:00 on the clock, top left. Customers turn up all day and wait on the left, each with a ring that runs out with their patience. Tap one (or Next customer) to bring their car onto the lift. After 19:00 you can keep working, or close up and let the queue leave you reviews about it. At 22:00 the lights go off whatever you\'re doing.'],
    ['TALKING', 'They tell you what\'s wrong, usually badly, sometimes about something else entirely. Nice replies make them nicer to pay. Snark and dark replies cost you their goodwill but give you a little energy back.'],
    ['SCAN', 'Scan the car to see through it. Red marks are faults. Amber question marks are noise the basic scanner can\'t read: inspect one (ten minutes on the clock) or gamble that it\'s nothing. A fault left in, or a sloppy repair, can bring the customer back the next morning, angry, for a free redo.'],
    ['QUOTE', 'Every job is parts plus labour. Tick the made-up extras — blinker fluid, muffler bearings — for free money, if the customer is gullible enough. If they aren\'t, they notice, and so does your rating. Some customers haggle: take their offer, meet halfway, or hold firm and risk them driving off.'],
    ['REPAIRS', 'Tap a red mark, or a job on the right, to fix it. Each is a small game: spin the ratchet anticlockwise and torque the bolts in order; hold to pour and let go before the line; drag each wire to its socket; slide the controls until the waves match; weld along the crack; repeat the firmware\'s code; tap fast to pull out whatever lives in the engine; tap when the wheel\'s mark or the brake needle hits the green. The X stops a repair without losing anything.'],
    ['HAND BACK', 'When everything on the car is green, hand it back. They pay what was agreed, plus a tip if they\'re happy, and then they review you. Good work, a fair price and a short wait make them happy. Parts are paid for when you fit them.'],
    ['ENERGY', 'Your battery drains all day, and faster after 19:00. Tired hands shake: the torch wanders, the ratchet slips, the pour surges. Coffee gives energy back (and five minutes of staring at the cup) but makes your hands shake for a while. Hit zero and you collapse, and the ambulance drone bills you.'],
    ['TROUBLE', 'From Tuesday the garage has its own problems: power cuts (no coffee either), a Health and Safety drone with fines, a raccoon in the till, and VIPs who pay a fortune and wait for nobody.'],
    ['MONEY', 'Rent is due every night and goes up every day. Short one night and the landlord warns you; short two nights running and you\'re evicted. In the evening, spend what\'s left on upgrades. Your rating sets how many customers come and how well they tip.'],
    ['THE WEEK', 'Survive Monday to Sunday. Your reward, as is tradition, is Monday — with more customers and a third more rent.'],
  ];
  function showHowto(back) {
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    s.innerHTML = `
      <div class="scr card pnl">
        <h1>HOW TO PLAY</h1>
        <div class="howto">${HOWTO.map(([h, p]) => `<h3>${h}</h3><p>${esc(p)}</p>`).join('')}</div>
        <div class="row"><button class="btn primary" data-act="${back}">Got it</button></div>
      </div>`;
  }
  function showSettings(back) {
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    const tog = (k, label, sub) => `<div class="opt"><div>${label}<div class="sub">${sub}</div></div><div class="seg"><button class="${settings[k] ? 'on' : ''}" data-set="${k}" data-v="1">ON</button><button class="${settings[k] ? '' : 'on'}" data-set="${k}" data-v="0">OFF</button></div></div>`;
    s.innerHTML = `
      <div class="scr card pnl">
        <h1>SETTINGS</h1>
        ${tog('music', 'Music', 'A slow synth track for a long night.')}
        ${tog('sfx', 'Sound', 'Ratchets, welders, the till, the rain.')}
        ${tog('voices', 'Customer voices', 'They read their lines out in the phone\'s voices.')}
        ${tog('vibe', 'Vibration', 'The wrench in your hand.')}
        <div class="opt"><div>Graphics<div class="sub">Auto drops the glow if the phone can't keep up.</div></div><div class="seg">${['auto', 'high', 'low'].map((g) => `<button class="${settings.gfx === g ? 'on' : ''}" data-gfx="${g}">${g.toUpperCase()}</button>`).join('')}</div></div>
        <div class="row"><button class="btn primary" data-act="${back}">Done</button></div>
      </div>`;
  }
  function applySettings() {
    AU.setMusic(settings.music);
    AU.setSfx(settings.sfx);
    if (!settings.voices) hush();
    SC.autoQuality = settings.gfx === 'auto';
    if (settings.gfx === 'high') SC.setQuality(2);
    if (settings.gfx === 'low') SC.setQuality(0);
  }

  function showPause() {
    if (mode !== 'play' || paused) return;
    paused = true;
    hush();
    if (mini) abortRepair();
    const s = $('#screen');
    s.className = 'dark';
    s.hidden = false;
    s.innerHTML = `
      <div class="scr card pnl" style="max-width:380px">
        <h1>PAUSED</h1>
        <p style="font:500 12.5px/1.45 var(--txt);color:var(--mut);margin:0 0 10px">The clock stops. The rain doesn't.</p>
        <div class="menu" style="width:100%">
          <button class="btn primary" data-act="resume">Resume</button>
          <button class="btn" data-act="howtoPause">How to play</button>
          <button class="btn" data-act="settingsPause">Settings</button>
          <button class="btn bad" data-act="title">Quit to title<small>DAY RESTARTS</small></button>
        </div>
      </div>`;
  }
  function resume() {
    paused = false;
    const s = $('#screen');
    s.hidden = true; s.innerHTML = '';
  }

  // every button on the full screens
  const ACTIONS = {
    continue() { career = loadCareer(); if (career) showMorning(); },
    newCareer() { career = newCareer(); saveCareer(); showMorning(); },
    howto() { showHowto('title'); },
    settings() { showSettings('title'); },
    title() { showTitle(); },
    quit() { if (app) app.quit(); },
    open() { openGarage(); },
    shop() { showShop(); },
    nextDay() { if ((career.day - 1) % 7 === 0) showWeekDone(); else showMorning(); },
    morning() { showMorning(); },
    evicted() { showEvicted(); },
    resume() { resume(); },
    howtoPause() { showHowto('backPause'); },
    settingsPause() { showSettings('backPause'); },
    backPause() { paused = false; showPause(); },
  };
  $('#screen').addEventListener('click', (e) => {
    AU.init();
    const b = e.target.closest('[data-act]');
    if (b && ACTIONS[b.dataset.act]) { sfx('blip'); haptic('tap'); ACTIONS[b.dataset.act](); return; }
    const buyB = e.target.closest('[data-buy]');
    if (buyB) { buy(buyB.dataset.buy); return; }
    const set = e.target.closest('[data-set]');
    if (set) { settings[set.dataset.set] = set.dataset.v === '1'; saveSettings(); applySettings(); sfx('tick'); const back = $('#screen .row [data-act]').dataset.act; showSettings(back); return; }
    const g = e.target.closest('[data-gfx]');
    if (g) { settings.gfx = g.dataset.gfx; saveSettings(); applySettings(); sfx('tick'); const back = $('#screen .row [data-act]').dataset.act; showSettings(back); }
  });

  // ------------------------------------------------------------------ the customer talking
  const dlg = { open: false, cust: null, typing: null, waitTap: null, choices: null };
  function dlgOpen(c) {
    dlg.cust = c;
    dlg.open = true;
    dlg.typing = null; dlg.waitTap = null; dlg.choices = null;
    const box = $('#dialog');
    box.hidden = false;
    box.classList.remove('out');
    $('#dlgName').textContent = c.name;
    $('#dlgTitle').textContent = c.vip ? 'VIP · ' + c.arch.title : c.comeback ? 'COMEBACK · ' + c.arch.title : c.arch.title;
    $('#dlgCar').textContent = c.car.year ? `${c.car.name} · ${c.car.year}` : c.car.name;
    $('#dlgChoices').innerHTML = '';
    drawDlgFace();
    renderDock();
  }
  function drawDlgFace() {
    if (!dlg.cust) return;
    FA.draw($('#dlgFace').getContext('2d'), 184, dlg.cust.face, dlg.cust.mood, SC.time());
  }
  function dlgClose(now) {
    hush();
    if (!dlg.open) return;
    dlg.open = false;
    dlg.typing = null; dlg.waitTap = null; dlg.choices = null;
    const box = $('#dialog');
    if (now) { box.hidden = true; return; }
    box.classList.add('out');
    setTimeout(() => { if (!dlg.open) box.hidden = true; }, 300);
    renderDock();
  }
  // typing blips only while nobody is speaking the line out loud
  let talking = false;
  function typeOut(text, cls) {
    talking = cls !== 'me' && voiceOn();
    return new Promise((res) => {
      $('#dlgChoices').innerHTML = '';
      dlg.typing = { text, cls, i: 0, acc: 0, res };
    });
  }
  // a conversation cut short (a collapse, the lights going out) simply never carries on
  const never = () => new Promise(() => {});
  async function dlgSay(text) {
    if (!dlg.open) return never();
    speak(dlg.cust, text);
    await typeOut(text, '');
    // a tap to carry on, so nobody misses a line
    $('#dlgText').insertAdjacentHTML('beforeend', '<span class="caret"></span>');
    await new Promise((res) => { dlg.waitTap = res; });
  }
  async function dlgMe(text) {
    if (!dlg.open) return never();
    hush();
    await typeOut('“' + text + '”', 'me');
    await sleep(450);
  }
  async function dlgAsk(text, choices) {
    if (!dlg.open) return never();
    speak(dlg.cust, text);
    await typeOut(text, '');
    return new Promise((res) => {
      const box = $('#dlgChoices');
      box.innerHTML = choices.map((c, i) => `<button class="btn ${i === 0 ? '' : i === 1 ? 'warn' : 'hot'}" data-ch="${i}">${esc(c.text)}${c.tag ? ` <small>${esc(c.tag)}</small>` : ''}</button>`).join('');
      dlg.choices = (i) => { dlg.choices = null; box.innerHTML = ''; sfx('blip'); haptic('tap'); res(i); };
    });
  }
  $('#dialog').addEventListener('click', (e) => {
    AU.init();
    const ch = e.target.closest('[data-ch]');
    if (ch && dlg.choices) { dlg.choices(+ch.dataset.ch); return; }
    if (dlg.typing) { dlg.typing.i = dlg.typing.text.length; return; }
    if (dlg.waitTap) { const r = dlg.waitTap; dlg.waitTap = null; sfx('tap'); r(); }
  });
  function stepDialog(dt) {
    const t = dlg.typing;
    if (!t) return;
    t.acc += dt * 62;
    const before = t.i;
    t.i = Math.min(t.text.length, t.i + Math.floor(t.acc));
    t.acc -= Math.floor(t.acc);
    if (t.i !== before) {
      $('#dlgText').innerHTML = `<span class="${t.cls}">${esc(t.text.slice(0, t.i))}</span>`;
      if (!talking && Math.floor(t.i / 3) !== Math.floor(before / 3)) sfx('type');
    }
    if (t.i >= t.text.length) { dlg.typing = null; t.res(); }
  }

  // ------------------------------------------------------------------ the HUD
  let shownCredits = 0;
  function renderAll() { renderQueue(); renderJob(); renderDock(); renderRating(); renderHud(true); }
  let lastClock = '';
  function renderHud(force) {
    if (!day) return;
    const cs = clockStr(day.min);
    if (cs !== lastClock || force) {
      lastClock = cs;
      $('#clock').textContent = cs;
      $('#clock').classList.toggle('over', day.min >= CLOSE);
      $('#dayLbl').textContent = `${D.WEEKDAYS[day.wd][0].slice(0, 3)} · DAY ${day.n}${day.min >= CLOSE ? ' · OVERTIME' : ''}`;
      $('#shiftFill').style.width = `${clamp((day.min - OPEN) / (CLOSE - OPEN), 0, 1) * 100}%`;
      SC.setDaylight(clamp((day.min - OPEN) / (CLOSE - OPEN), 0, 1.2));
    }
    // credits count towards the real figure
    const target = day.credits;
    if (Math.abs(shownCredits - target) > 0.5 || force) {
      shownCredits = force ? target : shownCredits + (target - shownCredits) * 0.18;
      if (Math.abs(shownCredits - target) < 1) shownCredits = target;
      $('#money').textContent = Math.round(shownCredits).toLocaleString('en-US');
      $('#money').style.color = target < 0 ? 'var(--rd)' : '';
    }
    // the page is only touched when a figure has moved
    const e = Math.round(day.energy);
    if (e !== hudCache.e || force) {
      hudCache.e = e;
      $('#energyPct').textContent = e + '%';
      $('#energyFill').style.width = e + '%';
      const b = $('#batt');
      b.classList.toggle('low', e < 25);
      b.classList.toggle('mid', e >= 25 && e < 50);
    }
    const cp = day.power ? 'OFF' : has('bean') ? '¢3' : '¢6';
    if (cp !== hudCache.cp || force) { hudCache.cp = cp; $('#coffeePrice').textContent = cp; }
  }
  const hudCache = {};
  function renderRating() {
    if (!career) return;
    const r = rating();
    $('#rating').textContent = r.toFixed(1);
    $('#stars').innerHTML = starRow(Math.round(r));
  }
  function moneyDelta(d) {
    const el = $('#moneyDelta');
    el.textContent = (d > 0 ? '+' : '−') + '¢' + Math.abs(Math.round(d));
    el.style.color = d > 0 ? 'var(--gr)' : 'var(--rd)';
    el.classList.remove('show'); void el.offsetWidth; el.classList.add('show');
  }
  function floatEnergy(text) {
    const t = document.createElement('div');
    t.className = 'toast pnl good';
    const r = $('#energyPnl').getBoundingClientRect();
    t.style.cssText = `position:fixed;left:${Math.round(r.left)}px;top:${Math.round(r.bottom + 6)}px;z-index:32;font-family:var(--num);font-weight:800;font-size:12px;white-space:nowrap`;
    t.textContent = text;
    $('#hud').appendChild(t);
    setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 400); }, 1400);
  }

  const faceCache = new Map();
  function faceCanvas(c, size) {
    const cv = document.createElement('canvas');
    cv.width = cv.height = size * 2;
    FA.draw(cv.getContext('2d'), size * 2, c.face, c.mood, 0);
    return cv;
  }
  function renderQueue() {
    if (!day) return;
    const list = $('#qList');
    $('#qCount').textContent = day.queue.length;
    const ids = day.queue.map((c) => c.id).join(',');
    if (list.dataset.ids === ids && list.children.length) return;
    list.dataset.ids = ids;
    list.innerHTML = '';
    if (!day.queue.length) {
      list.innerHTML = `<div class="qempty">${day.min >= CLOSE ? 'Nobody waiting. Go home. Go on.' : day.schedule.length ? 'Nobody yet. Enjoy it. It won\'t last.' : 'Nobody else is coming today. Probably.'}</div>`;
      return;
    }
    day.queue.slice(0, 5).forEach((c) => {
      const card = document.createElement('div');
      card.className = 'qc pnl' + (c.vip ? ' vip' : '');
      card.dataset.id = c.id;
      const face = document.createElement('div');
      face.className = 'face';
      const cv = faceCanvas(c, 40);
      c._qface = cv; c._qmood = Math.round(c.mood / 15);
      face.appendChild(cv);
      face.insertAdjacentHTML('beforeend', '<svg viewBox="0 0 48 48"><circle cx="24" cy="24" r="22" stroke="rgba(255,255,255,0.1)"/><circle class="pat" cx="24" cy="24" r="22" stroke="#3dff9a" stroke-dasharray="138.2" stroke-dashoffset="0"/></svg>');
      card.appendChild(face);
      card.insertAdjacentHTML('beforeend', `<div class="who"><div class="nm">${c.vip ? '★ ' : ''}${esc(c.name)}</div><div class="cr">${esc(c.car.name)}</div><div class="mood"></div></div>`);
      card.addEventListener('click', () => { AU.init(); if (job) { toast('One car at a time. You only have the one lift. And the one back.', 'bad'); sfx('bad'); return; } sfx('blip'); haptic('tap'); takeCar(c); });
      list.appendChild(card);
    });
    if (day.queue.length > 5) list.insertAdjacentHTML('beforeend', `<div class="qempty">+${day.queue.length - 5} more, sighing</div>`);
    updateQueueRings();
  }
  function updateQueueRings() {
    if (!day) return;
    day.queue.slice(0, 5).forEach((c) => {
      const card = document.querySelector(`.qc[data-id="${c.id}"]`);
      if (!card) return;
      const left = clamp(1 - c.waited / c.patience, 0, 1);
      const ring = card.querySelector('.pat');
      ring.setAttribute('stroke-dashoffset', String(138.2 * (1 - left)));
      ring.setAttribute('stroke', left > 0.5 ? '#3dff9a' : left > 0.25 ? '#ffb020' : '#ff3b5c');
      const md = card.querySelector('.mood');
      const w = moodWord(c.mood).toUpperCase();
      if (md.textContent !== w) { md.textContent = w; md.style.color = c.mood > 55 ? 'var(--gr)' : c.mood > 35 ? 'var(--am)' : 'var(--rd)'; }
      const mb = Math.round(c.mood / 15);
      if (c._qmood !== mb && c._qface) { c._qmood = mb; FA.draw(c._qface.getContext('2d'), 80, c.face, c.mood, 0); }
    });
  }

  function renderJob() {
    const box = $('#job');
    if (!job || mode !== 'play') { box.hidden = true; box.innerHTML = ''; return; }
    const c = job.cust;
    box.hidden = false;
    const fl = job.faults.map((f, i) => {
      if (!job.scanned) return '';
      if (!f.found) return `<div class="fl unknown" data-f="${i}"><i class="ico i-q"></i>Unread noise<span class="p">INSPECT</span></div>`;
      const cls = f.declined ? 'declined' : f.fixed ? 'fixed' : '';
      return `<div class="fl ${cls}" data-f="${i}"><i class="ico ${f.fixed ? 'i-check' : 'i-wrench'}"></i>${esc(f.def.name)}<span class="p">${f.fixed ? Math.round(f.quality * 100) + '%' : c.comeback || f.free ? 'FREE' : money(f.price)}</span></div>`;
    }).join('');
    const mood = c.mood;
    const moodCol = mood > 55 ? 'var(--gr)' : mood > 35 ? 'var(--am)' : 'var(--rd)';
    box.innerHTML = `
      <div class="hd"><canvas width="76" height="76"></canvas><div><div class="nm">${esc(c.name)}</div><div class="cr">${esc(c.car.name)} · ${c.car.year}</div></div></div>
      <div class="moodline">MOOD<div class="moodbar"><i style="width:${mood}%;background:${moodCol}"></i></div></div>
      <div class="faults">${job.scanned ? fl : '<div class="qempty">Not scanned yet. Could be anything. Could be everything.</div>'}</div>
      <div class="tot"><span>${job.quoted ? 'AGREED' : 'QUOTE'}</span><b>${job.quoted ? money(job.agreed) : '—'}</b></div>`;
    FA.draw(box.querySelector('canvas').getContext('2d'), 76, c.face, c.mood, 0);
  }
  $('#job').addEventListener('click', (e) => {
    AU.init();
    const r = e.target.closest('[data-f]');
    if (!r || !job) return;
    const f = job.faults[+r.dataset.f];
    if (f) { sfx('tap'); repair(f); }
  });

  // the next thing to do, at the bottom
  function renderDock() {
    const dock = $('#dock');
    if (!day || mode !== 'play') { dock.innerHTML = ''; return; }
    let html = '';
    const key = [];
    if (dlg.open) { dock.innerHTML = ''; dock.dataset.key = ''; return; }
    if (!job) {
      if (day.queue.length) { html += `<button class="btn primary go" data-d="next">Next customer <i class="ico i-next"></i></button>`; key.push('next'); }
      else { html += `<div id="dockHint">${day.min >= CLOSE ? 'Nobody left. The day ends when you stop looking busy.' : esc(idleLine())}</div>`; key.push('idle'); }
      if (day.min >= CLOSE && !day.ending) { html += `<button class="btn bad" data-d="close"><i class="ico i-door"></i>Close up</button>`; key.push('close'); }
    } else if (!job.busy) {
      if (!job.scanned) { html = `<button class="btn primary go" data-d="scan"><i class="ico i-scan"></i>Scan</button>`; key.push('scan'); }
      else if (!job.quoted) { html = `<button class="btn primary go" data-d="quote"><i class="ico i-quote"></i>Quote</button>`; key.push('quote'); }
      else {
        const left = job.faults.filter((f) => f.found && !f.fixed && !f.declined).length;
        if (left) { html = `<div id="dockHint">${left} to fix. Tap a red mark or a job on the right.</div>`; key.push('fix' + left); }
        else { html = `<button class="btn primary go" data-d="back"><i class="ico i-key"></i>Hand back</button>`; key.push('back'); }
      }
    }
    const k = key.join('|') + (job ? job.busy : '') ;
    if (dock.dataset.key === k) return;
    dock.dataset.key = k;
    dock.innerHTML = html;
    dock.querySelectorAll('.btn').forEach((b) => b.classList.add('appear'));
  }
  let idleText = '', idleAt = -999;
  function idleLine() {
    if (day.min - idleAt > 90 || !idleText) {
      idleAt = day.min;
      idleText = pick(['Nobody here. Have a coffee. Stare at the wall. Both are free. The coffee isn\'t.', 'Quiet. Too quiet. Like before a Karen.', 'The robot watches you rest. Judgementally.', 'You could tidy up. You won\'t, but you could.']);
    }
    return idleText;
  }
  $('#dock').addEventListener('click', (e) => {
    AU.init();
    const b = e.target.closest('[data-d]');
    if (!b) return;
    const a = b.dataset.d;
    sfx('blip'); haptic('tap');
    if (a === 'next' && day.queue.length) takeCar(day.queue[0]);
    else if (a === 'scan') scan();
    else if (a === 'quote') openQuote();
    else if (a === 'back') handBack();
    else if (a === 'close') closeUp();
  });
  $('#btnCoffee').addEventListener('click', () => { AU.init(); coffee(); });
  $('#btnPause').addEventListener('click', () => { AU.init(); sfx('blip'); showPause(); });

  // toasts in the middle, and texts on the phone
  function toast(html, kind, ms) {
    const box = $('#toasts');
    while (box.children.length > 2) box.firstChild.remove();
    const t = document.createElement('div');
    t.className = 'toast pnl ' + (kind || '');
    t.innerHTML = html;
    box.appendChild(t);
    setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 400); }, ms || 3400);
  }
  function phoneText(from, text) {
    if (mode !== 'play') return;
    const box = $('#phone');
    while (box.children.length > 1) box.firstChild.remove();
    const t = document.createElement('div');
    t.className = 'txt pnl pink';
    t.innerHTML = `<i class="ico i-phone"></i><div><div class="from">${esc(from.toUpperCase())}</div><div class="body">${esc(text)}</div></div>`;
    box.appendChild(t);
    sfx('notify');
    haptic('tap');
    setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 400); }, 5200);
  }
  function flash(color, a) {
    const f = $('#flashFx');
    f.style.transition = 'none'; f.style.background = color; f.style.opacity = String(a);
    void f.offsetWidth;
    f.style.transition = 'opacity 0.6s'; f.style.opacity = '0';
  }

  // the news crawling along the bottom
  const ticker = { x: 0, w: 0, el: null };
  function stepTicker(dt) {
    if (mode !== 'play') return;
    if (!ticker.el) ticker.el = $('#tickerText');
    if (ticker.x < -ticker.w - 20 || !ticker.w) {
      ticker.el.textContent = pick(D.NEWS) + '   ·   ' + pick(D.NEWS);
      ticker.w = ticker.el.scrollWidth;
      ticker.x = window.innerWidth;
    }
    ticker.x -= dt * 55;
    ticker.el.style.transform = `translateX(${Math.round(ticker.x)}px)`;
  }

  // ------------------------------------------------------------------ the marks over the car
  const marks = new Map();
  function clearMarkers() { marks.forEach((m) => m.el.remove()); marks.clear(); }
  function renderMarkers(animate) {
    if (!job || !job.scanned) { clearMarkers(); return; }
    job.faults.forEach((f, i) => {
      let m = marks.get(f);
      if (f.declined) { if (m) { m.el.remove(); marks.delete(f); } return; }
      if (!m) {
        const el = document.createElement('div');
        el.className = 'mk' + (animate ? ' in' : '');
        el.innerHTML = '<div class="ring"><i class="ico"></i></div><div class="tag"></div>';
        el.querySelector('.ring').addEventListener('click', () => { AU.init(); sfx('tap'); repair(f); });
        el.querySelector('.tag').addEventListener('click', () => { AU.init(); sfx('tap'); repair(f); });
        $('#markers').appendChild(el);
        m = { el, i };
        marks.set(f, m);
      }
      const st = !f.found ? 'unknown' : f.fixed ? 'fixed' : '';
      m.el.classList.toggle('unknown', st === 'unknown');
      m.el.classList.toggle('fixed', st === 'fixed');
      m.el.querySelector('.ico').className = 'ico ' + (st === 'unknown' ? 'i-q' : st === 'fixed' ? 'i-check' : 'i-wrench');
      m.el.querySelector('.tag').textContent = st === 'unknown' ? 'Noise?' : f.def.name;
    });
  }
  let marksHidden = null;
  function placeMarkers() {
    const hide = !job || !job.scanned || mode !== 'play' || !!mini || !$('#quote').hidden || dlg.open || paused;
    if (hide !== marksHidden) { marksHidden = hide; $('#markers').style.display = hide ? 'none' : ''; }
    if (hide) return;
    const placed = [];
    marks.forEach((m, f) => {
      const p = SC.partScreen(f.part);
      let x = p.x, y = p.y;
      // two marks on one wheel would sit on each other
      for (const q of placed) if (Math.hypot(q.x - x, q.y - y) < 40) y = q.y + 44;
      placed.push({ x, y });
      m.el.style.transform = `translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, 0)`;
      m.el.classList.toggle('left', x > window.innerWidth * 0.62);
      m.el.style.visibility = p.z > 1 ? 'hidden' : '';
    });
  }
  function applyFaultsToScene() {
    if (!job) { SC.setFaults({}); return; }
    const map = {};
    job.faults.forEach((f) => {
      map[f.part] = f.fixed ? 'fixed' : f.found ? 'bad' : 'hidden';
      if (f.id === 'flat') map._flat = f.side;
    });
    SC.setFaults(map);
  }

  // ------------------------------------------------------------------ the frame
  let last = performance.now(), asleep = false, qAcc = 0, testNoDraw = false;
  function frame(now) {
    requestAnimationFrame(frame);
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    if (asleep) return;
    if (mode === 'play' && !paused) tick(dt);
    stepDialog(dt);
    // under a repair the garage stands still: the phone's whole attention goes to the repair
    if (!mini && !testNoDraw) SC.frame(dt);
    if (mini) { mini.frame(dt); renderSteps(); }
    if (mode === 'play') {
      renderHud();
      placeMarkers();
      stepTicker(dt);
      qAcc += dt;
      if (qAcc > 0.25) { qAcc = 0; updateQueueRings(); if (!job) renderDock(); }
      if (dlg.open && dlg.cust && dlg.cust.arch.robot) drawDlgFace();
    }
  }

  // the repair's canvas takes the finger
  const mc = $('#miniCanvas');
  const pt = (e) => { const r = mc.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
  mc.addEventListener('pointerdown', (e) => { if (!mini) return; AU.init(); mc.setPointerCapture(e.pointerId); mini.pointer('down', ...pt(e)); });
  mc.addEventListener('pointermove', (e) => { if (mini) mini.pointer('move', ...pt(e)); });
  mc.addEventListener('pointerup', (e) => { if (mini) mini.pointer('up', ...pt(e)); });
  mc.addEventListener('pointercancel', (e) => { if (mini) mini.pointer('up', ...pt(e)); });
  $('#miniAbort').addEventListener('click', () => abortRepair());

  // the first tap anywhere wakes the sound
  let woke = false;
  window.addEventListener('pointerdown', () => { AU.init(); if (!woke && AU.ready()) { woke = true; applySettings(); } }, { capture: true });

  window.addEventListener('resize', () => { SC.resize(); if (mini) mini.resize(); });

  // the phone's back gesture: close what's open, pause the day, or leave from the title
  window.cmBack = function () {
    if (mini) { abortRepair(); return 'stay'; }
    if (!$('#quote').hidden && job && !job.quoted) { closeQuoteNow(); job.busy = false; renderDock(); return 'stay'; }
    if (mode === 'play') { if (paused) resume(); else showPause(); return 'stay'; }
    if (mode === 'title') {
      const s = $('#screen');
      if (s.querySelector('.howto') || s.querySelector('.seg')) { showTitle(); return 'stay'; }
      return 'quit';
    }
    if (mode === 'intro' || mode === 'eod' || mode === 'shop' || mode === 'week') return 'stay';
    showTitle();
    return 'stay';
  };
  // the app went into the background: the day stops, the sound stops
  window.cmSleep = function (s) {
    asleep = !!s;
    AU.sleep(asleep);
    if (asleep) { hush(); if (mode === 'play' && !paused) showPause(); }
    last = performance.now();
  };
  window.cmVoiceDone = function () {};

  // ------------------------------------------------------------------ start
  async function boot() {
    // the signs in the garage are drawn in the game's own fonts, so they're loaded first
    try {
      await Promise.race([
        Promise.all(['900 40px Orbitron', '700 20px "Chakra Petch"', '600 20px "Chakra Petch"', '500 20px "Chakra Petch"'].map((f) => document.fonts.load(f))),
        sleep(2500),
      ]);
    } catch (e) { /* the fallback face will do */ }
    const q = settings.gfx === 'low' ? 0 : 2;
    SC.init($('#gl'), { quality: q });
    SC.onQuality = (qq) => { if (qq === 0 && settings.gfx === 'auto') toast('Graphics eased off to keep things smooth.'); };
    applySettings();
    loadVoices();
    showTitle();
    requestAnimationFrame((t) => { last = t; frame(t); });
  }
  boot();

  // a handle for automated play-throughs (?test): no effect on the game otherwise
  if (TEST) {
    window.cmTest = {
      get state() { return { mode, day, job, career, paused, mini: !!mini, dlg: dlg.open }; },
      speed(m) { MIN_PER_SEC = m; },
      noDraw(v) { testNoDraw = v; },
      waits(k) { waitScale = k; },
      finishMini(q) { if (mini && working) { closeMini(); stageDone(working, { quality: q == null ? 0.9 : q }); } },
      tapDialog() { $('#dialog').click(); },
      choose(i) { if (dlg.choices) dlg.choices(i); },
      act: ACTIONS,
      takeCar() { if (day.queue.length) takeCar(day.queue[0]); },
      scan, openQuote, handBack, coffee,
      repairNext() { const f = job && job.faults.find((x) => x.found && !x.fixed && !x.declined); if (f) repair(f); },
      advance(m) { advance(m); },
      // run the world forward without drawing it: software WebGL in a test browser is slow
      step(secs) {
        const dt = 1 / 30;
        for (let t = 0; t < secs; t += dt) {
          if (mode === 'play' && !paused) tick(dt);
          stepDialog(dt);
          SC.frame(dt, true);
          if (mini) mini.tick(dt);
        }
        if (mode === 'play') { renderHud(); placeMarkers(); }
      },
      endDay() { endDay('done'); },
    };
  }
})();

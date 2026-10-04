/*
 * Car Mech — every sound, made on the spot with Web Audio: the ratchet, the welder, the till, the
 * rain on the roller door, and a slow synth track for a long night. Nothing is recorded, so the game
 * carries no sound files and works with no signal.
 *
 * Sound can only start after a tap, so CMAudio.init() is called from the first one.
 */
(function () {
  'use strict';
  const A = {};
  window.CMAudio = A;

  let ctx = null, master, sfxBus, musicBus, ambBus, verb, verbSend, delay;
  let noiseBuf = null, crackleBuf = null;
  let sfxOn = true, musicOn = true;
  let musicMode = 'off';

  A.init = function () {
    if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ctx = new AC();
    master = ctx.createGain(); master.gain.value = 0.9;
    // a gentle limiter, so a pile of sparks and a till at once don't clip
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -14; comp.ratio.value = 6; comp.attack.value = 0.004; comp.release.value = 0.2;
    master.connect(comp); comp.connect(ctx.destination);
    sfxBus = ctx.createGain(); sfxBus.gain.value = sfxOn ? 1 : 0; sfxBus.connect(master);
    musicBus = ctx.createGain(); musicBus.gain.value = musicOn ? 0.55 : 0; musicBus.connect(master);
    ambBus = ctx.createGain(); ambBus.gain.value = sfxOn ? 1 : 0; ambBus.connect(master);
    verb = ctx.createConvolver(); verb.buffer = impulse(2.6, 2.2);
    verbSend = ctx.createGain(); verbSend.gain.value = 0.5;
    verbSend.connect(verb); verb.connect(master);
    delay = ctx.createDelay(1); delay.delayTime.value = 60 / 84 * 0.75;
    const fb = ctx.createGain(); fb.gain.value = 0.32;
    const dl = ctx.createBiquadFilter(); dl.type = 'lowpass'; dl.frequency.value = 2400;
    delay.connect(dl); dl.connect(fb); fb.connect(delay); dl.connect(musicBus);
    noiseBuf = makeNoise(2, false);
    crackleBuf = makeNoise(2, true);
    startRain();
    if (musicMode !== 'off') startMusic();
  };
  A.ready = () => !!ctx;

  A.sleep = function (asleep) {
    if (!ctx) return;
    if (asleep) ctx.suspend(); else ctx.resume();
  };
  A.setSfx = function (on) {
    sfxOn = on;
    if (ctx) { sfxBus.gain.setTargetAtTime(on ? 1 : 0, ctx.currentTime, 0.05); ambBus.gain.setTargetAtTime(on ? 1 : 0, ctx.currentTime, 0.2); }
  };
  A.setMusic = function (on) {
    musicOn = on;
    if (ctx) musicBus.gain.setTargetAtTime(on ? 0.55 : 0, ctx.currentTime, 0.3);
  };

  function impulse(secs, decay) {
    const len = Math.floor(ctx.sampleRate * secs);
    const b = ctx.createBuffer(2, len, ctx.sampleRate);
    for (let ch = 0; ch < 2; ch++) {
      const d = b.getChannelData(ch);
      for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
    }
    return b;
  }
  function makeNoise(secs, crackle) {
    const len = Math.floor(ctx.sampleRate * secs);
    const b = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = b.getChannelData(0);
    let env = 0;
    for (let i = 0; i < len; i++) {
      if (crackle) {
        // a welder: bursts of hiss, popping in and out
        if (Math.random() < 0.0009) env = 0.6 + Math.random() * 0.4;
        env *= 0.9993;
        d[i] = (Math.random() * 2 - 1) * (0.15 + env) * (Math.random() < 0.002 ? 3 : 1);
      } else d[i] = Math.random() * 2 - 1;
    }
    return b;
  }

  // ------------------------------------------------------------------ building blocks
  function env(g, t, a, peak, d, sustain) {
    g.gain.cancelScheduledValues(t);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(Math.max(0.0002, peak), t + a);
    g.gain.exponentialRampToValueAtTime(Math.max(0.0001, sustain || 0.0001), t + a + d);
  }
  function tone(type, f0, f1, dur, vol, opts = {}) {
    if (!ctx) return;
    const t = ctx.currentTime + (opts.at || 0);
    const o = ctx.createOscillator(); o.type = type;
    o.frequency.setValueAtTime(f0, t);
    if (f1 && f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, t + dur);
    if (opts.detune) o.detune.value = opts.detune;
    const g = ctx.createGain();
    env(g, t, opts.attack || 0.004, vol, dur);
    let node = o;
    if (opts.lp) { const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = opts.lp; o.connect(f); node = f; }
    node.connect(g);
    g.connect(opts.bus || sfxBus);
    if (opts.verb) { const s = ctx.createGain(); s.gain.value = opts.verb; g.connect(s); s.connect(verbSend); }
    o.start(t); o.stop(t + dur + 0.05);
  }
  function noise(dur, vol, type, freq, q, opts = {}) {
    if (!ctx) return null;
    const t = ctx.currentTime + (opts.at || 0);
    const s = ctx.createBufferSource(); s.buffer = opts.buf || noiseBuf; s.loop = !!opts.loop;
    if (opts.rate) s.playbackRate.value = opts.rate;
    const f = ctx.createBiquadFilter(); f.type = type || 'bandpass'; f.frequency.value = freq || 1000; f.Q.value = q || 1;
    if (opts.f1) f.frequency.exponentialRampToValueAtTime(opts.f1, t + dur);
    const g = ctx.createGain();
    if (opts.loop) { g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + (opts.attack || 0.05)); }
    else env(g, t, opts.attack || 0.003, vol, dur);
    s.connect(f); f.connect(g); g.connect(opts.bus || sfxBus);
    if (opts.verb) { const v = ctx.createGain(); v.gain.value = opts.verb; g.connect(v); v.connect(verbSend); }
    s.start(t, Math.random() * 1.5);
    if (!opts.loop) s.stop(t + dur + 0.05);
    return { s, g, f };
  }

  // ------------------------------------------------------------------ the sounds
  const SFX = {
    tap: () => tone('sine', 1300, 900, 0.035, 0.08),
    blip: () => { tone('sine', 660, 990, 0.08, 0.1); tone('sine', 1320, 1320, 0.05, 0.04, { at: 0.03 }); },
    back: () => tone('sine', 700, 420, 0.09, 0.09),
    good: () => { tone('triangle', 1046, 1046, 0.12, 0.13, { verb: 0.3 }); tone('triangle', 1568, 1568, 0.22, 0.12, { at: 0.08, verb: 0.4 }); },
    great: () => { [1046, 1318, 1568, 2093].forEach((f, i) => tone('triangle', f, f, 0.25, 0.1, { at: i * 0.07, verb: 0.5 })); },
    bad: () => { tone('sawtooth', 140, 90, 0.28, 0.12, { lp: 900 }); tone('square', 70, 60, 0.28, 0.05, { lp: 400 }); },
    cash: () => {
      noise(0.06, 0.25, 'highpass', 3000, 0.7);
      [2093, 2637, 3136].forEach((f, i) => tone('sine', f, f, 0.6, 0.09, { at: 0.05 + i * 0.05, verb: 0.4 }));
      tone('triangle', 523, 523, 0.15, 0.08, { at: 0.02 });
    },
    notify: () => { tone('sine', 1318, 1318, 0.12, 0.08, { verb: 0.3 }); tone('sine', 1975, 1975, 0.2, 0.07, { at: 0.11, verb: 0.4 }); },
    ratchet: () => { noise(0.018, 0.35, 'bandpass', 3200 + Math.random() * 600, 4); tone('square', 1800, 1200, 0.01, 0.03); },
    impact: () => {
      for (let i = 0; i < 14; i++) noise(0.012, 0.3, 'bandpass', 2400, 3, { at: i * 0.022 });
      tone('sawtooth', 90, 110, 0.32, 0.08, { lp: 700 });
    },
    pop: () => { tone('sine', 320, 110, 0.12, 0.2); noise(0.02, 0.3, 'highpass', 4000, 1); },
    clink: () => { tone('sine', 2600 + Math.random() * 300, 2500, 0.18, 0.08, { verb: 0.3 }); tone('sine', 3900, 3800, 0.1, 0.05, { at: 0.01 }); tone('sine', 2200, 2150, 0.12, 0.05, { at: 0.14 }); },
    thunk: () => { tone('sine', 160, 60, 0.18, 0.3); noise(0.05, 0.15, 'lowpass', 600, 1); },
    zap: () => { tone('sawtooth', 1600, 70, 0.25, 0.14, { lp: 4000 }); noise(0.2, 0.2, 'highpass', 2500, 1); },
    spark: () => noise(0.06 + Math.random() * 0.05, 0.12, 'highpass', 5000, 1),
    lift: () => { tone('sine', 55, 95, 1.5, 0.22, { attack: 0.3 }); noise(1.5, 0.08, 'lowpass', 300, 1, { attack: 0.4, f1: 900 }); tone('sine', 220, 440, 1.4, 0.04, { attack: 0.5, verb: 0.4 }); },
    lower: () => { tone('sine', 95, 50, 1.4, 0.2, { attack: 0.2 }); noise(1.4, 0.07, 'lowpass', 900, 1, { attack: 0.2, f1: 250 }); },
    scan: () => {
      tone('sine', 300, 1800, 2.2, 0.07, { attack: 0.2, verb: 0.4 });
      tone('triangle', 600, 3600, 2.2, 0.03, { attack: 0.2 });
      noise(2.2, 0.05, 'bandpass', 2000, 6, { attack: 0.3, f1: 6000 });
    },
    found: () => { tone('square', 880, 880, 0.06, 0.05, { lp: 3000 }); tone('square', 660, 660, 0.09, 0.05, { at: 0.07, lp: 3000 }); },
    engine: () => {
      if (!ctx) return;
      const t = ctx.currentTime;
      const o = ctx.createOscillator(); o.type = 'sawtooth';
      o.frequency.setValueAtTime(38, t); o.frequency.linearRampToValueAtTime(62, t + 1.2); o.frequency.linearRampToValueAtTime(34, t + 3.0);
      const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 260;
      const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.25, t + 0.5); g.gain.exponentialRampToValueAtTime(0.0001, t + 3.2);
      o.connect(f); f.connect(g); g.connect(sfxBus); o.start(t); o.stop(t + 3.3);
      // the electric whine over it, because it's 2089
      tone('sine', 400, 900, 1.6, 0.03, { attack: 0.3 });
      tone('sine', 900, 300, 1.4, 0.025, { at: 1.6 });
      noise(3, 0.05, 'lowpass', 400, 1, { attack: 0.5 });
    },
    door: () => {
      for (let i = 0; i < 26; i++) noise(0.04, 0.05 + Math.random() * 0.05, 'bandpass', 700 + Math.random() * 400, 2, { at: i * 0.045 });
      tone('sine', 70, 60, 1.2, 0.1, { attack: 0.1 });
    },
    type: () => tone('square', 480 + Math.random() * 260, null, 0.018, 0.018, { lp: 2500 }),
    coffee: () => {
      noise(1.2, 0.12, 'bandpass', 900, 2, { attack: 0.1, f1: 1600 });
      for (let i = 0; i < 6; i++) tone('sine', 300 + Math.random() * 200, 200, 0.06, 0.05, { at: 0.2 + i * 0.15 });
      tone('sine', 880, 880, 0.12, 0.05, { at: 1.3 });
    },
    alarm: () => { for (let i = 0; i < 4; i++) { tone('square', 1760, 1760, 0.07, 0.05, { at: i * 0.18, lp: 4000 }); tone('square', 1760, 1760, 0.07, 0.05, { at: i * 0.18 + 0.09, lp: 4000 }); } },
    whoosh: () => noise(0.35, 0.09, 'bandpass', 500, 1.5, { attack: 0.12, f1: 3500 }),
    stamp: () => { tone('sine', 120, 50, 0.22, 0.35); noise(0.08, 0.15, 'lowpass', 1200, 1); },
    heart: () => { tone('sine', 62, 40, 0.14, 0.3); tone('sine', 58, 38, 0.14, 0.24, { at: 0.2 }); },
    tick: () => tone('square', 2400, 2400, 0.012, 0.04, { lp: 5000 }),
    hit: () => { tone('triangle', 784, 784, 0.1, 0.12); tone('sine', 1568, 1568, 0.15, 0.06, { at: 0.02, verb: 0.3 }); },
    miss: () => tone('sawtooth', 200, 120, 0.15, 0.08, { lp: 1200 }),
    plug: () => { tone('sine', 900, 1400, 0.06, 0.1); noise(0.03, 0.12, 'bandpass', 3000, 2); },
    glug: () => { for (let i = 0; i < 3; i++) tone('sine', 180 + Math.random() * 80, 90, 0.08, 0.1, { at: i * 0.09 }); },
    squeak: () => { tone('sine', 1400, 2400, 0.08, 0.06); tone('sine', 2200, 1600, 0.1, 0.05, { at: 0.08 }); },
    tug: () => { tone('triangle', 200 + Math.random() * 60, 140, 0.07, 0.12); noise(0.04, 0.08, 'lowpass', 800, 1); },
    fanfare: () => { [523, 659, 784, 1046].forEach((f, i) => tone('triangle', f, f, 0.4, 0.1, { at: i * 0.12, verb: 0.5 })); },
    gameover: () => { [392, 349, 311, 262].forEach((f, i) => tone('sawtooth', f, f * 0.98, 0.5, 0.07, { at: i * 0.3, lp: 1400, verb: 0.5 })); },
    note: (n) => { const f = 440 * Math.pow(2, (n - 9) / 12); tone('triangle', f, f, 0.28, 0.12, { verb: 0.35 }); tone('sine', f * 2, f * 2, 0.2, 0.04); },
    rain: () => {},
  };
  A.play = function (name, arg) {
    if (!ctx || !sfxOn) return;
    const f = SFX[name];
    if (f) try { f(arg); } catch (e) { /* a sound is never worth a crash */ }
  };

  // a sound that runs until it's stopped: pouring, draining, welding
  A.loop = function (kind) {
    if (!ctx || !sfxOn) return { stop() {}, set() {} };
    let n;
    if (kind === 'weld') n = noise(10, 0.22, 'highpass', 1800, 0.8, { loop: true, buf: crackleBuf, attack: 0.03 });
    else if (kind === 'pour') n = noise(10, 0.14, 'bandpass', 700, 1.4, { loop: true, attack: 0.08 });
    else if (kind === 'drain') n = noise(10, 0.14, 'bandpass', 380, 1.2, { loop: true, attack: 0.08 });
    else n = noise(10, 0.1, 'lowpass', 600, 1, { loop: true });
    let hum = null;
    if (kind === 'weld') {
      hum = ctx.createOscillator(); hum.type = 'sawtooth'; hum.frequency.value = 100;
      const hg = ctx.createGain(); hg.gain.value = 0.025;
      const hf = ctx.createBiquadFilter(); hf.type = 'lowpass'; hf.frequency.value = 600;
      hum.connect(hf); hf.connect(hg); hg.connect(sfxBus); hum.start();
      hum._g = hg;
    }
    return {
      stop() {
        const t = ctx.currentTime;
        n.g.gain.cancelScheduledValues(t); n.g.gain.setTargetAtTime(0.0001, t, 0.05);
        n.s.stop(t + 0.4);
        if (hum) { hum._g.gain.setTargetAtTime(0.0001, t, 0.03); hum.stop(t + 0.3); }
      },
      // how hard it's going, 0..1: a pour's flow, or whether the torch touches metal
      set(k) {
        const t = ctx.currentTime;
        n.g.gain.setTargetAtTime(Math.max(0.0001, k * (kind === 'weld' ? 0.25 : 0.16)), t, 0.04);
        if (kind !== 'weld') n.f.frequency.setTargetAtTime(400 + k * 700, t, 0.1);
      },
    };
  };

  // ------------------------------------------------------------------ the rain on the door
  let rain = null;
  function startRain() {
    if (rain) return;
    const s = ctx.createBufferSource(); s.buffer = noiseBuf; s.loop = true;
    const hp = ctx.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 500;
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 3200;
    const g = ctx.createGain(); g.gain.value = 0.045;
    s.connect(hp); hp.connect(lp); lp.connect(g); g.connect(ambBus);
    s.start();
    // the city: a low hum that never stops
    const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = 52;
    const og = ctx.createGain(); og.gain.value = 0.02;
    o.connect(og); og.connect(ambBus); o.start();
    rain = { g, og };
  }
  A.rain = function (level) { if (rain) rain.g.gain.setTargetAtTime(0.045 * level, ctx.currentTime, 1); };

  // ------------------------------------------------------------------ the music
  /*
   * A slow synthwave loop, written as it plays: a pad on the chords, a bass on the eighths, an arp
   * through the delay, a soft kick and a clap. "title" is the pad and arp; "day" all of it; "night"
   * the same in a darker key; "calm" the pad alone for the end of the day.
   */
  const PROG = {
    day: [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]],        // Am F C G
    night: [[50, 53, 57], [46, 50, 53], [53, 57, 60], [48, 52, 55]],      // Dm Bb F C
    title: [[57, 60, 64], [53, 57, 60], [50, 53, 57], [52, 56, 59]],      // Am F Dm E
  };
  const BPM = 84;
  let seq = null;
  A.music = function (mode) {
    musicMode = mode;
    if (!ctx) return;
    if (mode === 'off') { stopMusic(); return; }
    if (!seq) startMusic();
  };
  function startMusic() {
    if (seq) return;
    seq = { step: 0, next: ctx.currentTime + 0.1, timer: null };
    seq.timer = setInterval(schedule, 90);
  }
  function stopMusic() {
    if (!seq) return;
    clearInterval(seq.timer);
    seq = null;
  }
  const mtof = (n) => 440 * Math.pow(2, (n - 69) / 12);
  function schedule() {
    if (!ctx || !seq) return;
    const sixteenth = 60 / BPM / 4;
    while (seq.next < ctx.currentTime + 0.35) {
      const st = seq.step;
      const bar = Math.floor(st / 16) % 4;
      const prog = PROG[musicMode] || PROG.day;
      const chord = prog[bar];
      const t = seq.next;
      const full = musicMode === 'day' || musicMode === 'night';
      if (st % 16 === 0) chord.forEach((n, i) => pad(mtof(n), t, sixteenth * 16, i));
      if (musicMode !== 'calm') {
        if (st % 2 === 0 && full) bass(mtof(chord[0] - 24), t, sixteenth * 1.6, st % 8 === 0 ? 0.16 : 0.1);
        if (st % 2 === 0) { const n = chord[(st / 2) % 3] + 12 + ((st % 8 === 6) ? 12 : 0); arp(mtof(n), t, sixteenth * 1.4); }
        if (full && st % 8 === 0) kick(t);
        if (full && st % 8 === 4) clap(t);
        if (full && st % 2 === 1) hat(t);
      }
      seq.next += sixteenth;
      seq.step = (st + 1) % 64;
    }
  }
  function pad(f, t, dur, i) {
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.03, t + 1.2);
    g.gain.setValueAtTime(0.03, t + dur - 0.6);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.8);
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass';
    lp.frequency.setValueAtTime(700, t); lp.frequency.linearRampToValueAtTime(1500, t + dur / 2); lp.frequency.linearRampToValueAtTime(800, t + dur);
    lp.connect(g); g.connect(musicBus);
    const v = ctx.createGain(); v.gain.value = 0.6; g.connect(v); v.connect(verbSend);
    [-8, 7].forEach((det) => {
      const o = ctx.createOscillator(); o.type = 'sawtooth'; o.frequency.value = f; o.detune.value = det + i * 2;
      o.connect(lp); o.start(t); o.stop(t + dur + 1);
    });
  }
  function bass(f, t, dur, vol) {
    const o = ctx.createOscillator(); o.type = 'square'; o.frequency.value = f;
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.setValueAtTime(700, t); lp.frequency.exponentialRampToValueAtTime(180, t + dur);
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(lp); lp.connect(g); g.connect(musicBus); o.start(t); o.stop(t + dur + 0.05);
  }
  function arp(f, t, dur) {
    const o = ctx.createOscillator(); o.type = 'triangle'; o.frequency.value = f;
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.035, t + 0.005); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(musicBus); g.connect(delay); o.start(t); o.stop(t + dur + 0.05);
  }
  function kick(t) {
    const o = ctx.createOscillator(); o.type = 'sine';
    o.frequency.setValueAtTime(130, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.18);
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.22, t + 0.005); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
    o.connect(g); g.connect(musicBus); o.start(t); o.stop(t + 0.4);
  }
  function clap(t) {
    const s = ctx.createBufferSource(); s.buffer = noiseBuf;
    const f = ctx.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = 1500; f.Q.value = 0.9;
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.07, t + 0.004); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.22);
    s.connect(f); f.connect(g); g.connect(musicBus);
    const v = ctx.createGain(); v.gain.value = 0.8; g.connect(v); v.connect(verbSend);
    s.start(t, Math.random()); s.stop(t + 0.25);
  }
  function hat(t) {
    const s = ctx.createBufferSource(); s.buffer = noiseBuf;
    const f = ctx.createBiquadFilter(); f.type = 'highpass'; f.frequency.value = 7000;
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.018, t + 0.002); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.05);
    s.connect(f); f.connect(g); g.connect(musicBus); s.start(t, Math.random()); s.stop(t + 0.06);
  }
})();

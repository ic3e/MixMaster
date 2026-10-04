/*
 * Car Mech — the repairs, as small games for a thumb: spin the ratchet, pour to the line, plug the
 * wires in, tune the wave, weld the crack, repeat the code, pull out whatever lives in the engine.
 *
 * Each one draws into the canvas it's given and reports back how well it went (0..1). A tired
 * mechanic's hands shake: opts.shake (0..1) moves the torch, slips the ratchet, makes the pour surge.
 */
(function () {
  'use strict';
  const MG = {};
  window.CMMini = MG;

  const TAU = Math.PI * 2;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const rnd = (a, b) => a + Math.random() * (b - a);
  const CY = '#29f0ff', PK = '#ff2bd6', AM = '#ffb020', GR = '#3dff9a', RD = '#ff3b5c', INK = '#05080f';
  const NUMF = '"Orbitron", "Chakra Petch", sans-serif';
  const TXTF = '"Chakra Petch", "Orbitron", sans-serif';

  // a hand that shakes: smooth, so it drifts rather than buzzes
  function wobble(t, amt) {
    return {
      x: (Math.sin(t * 5.3) + Math.sin(t * 11.7 + 1.3) * 0.6 + Math.sin(t * 2.1 + 4) * 0.8) * amt,
      y: (Math.cos(t * 4.7 + 2) + Math.sin(t * 9.9 + 0.4) * 0.6 + Math.cos(t * 1.7) * 0.8) * amt,
    };
  }
  // a line that glows: a wide faint stroke under a narrow bright one
  function glow(ctx, color, width, draw, strength = 1) {
    ctx.save();
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    ctx.globalCompositeOperation = 'lighter';
    ctx.strokeStyle = color;
    ctx.globalAlpha = 0.12 * strength; ctx.lineWidth = width * 5; ctx.beginPath(); draw(); ctx.stroke();
    ctx.globalAlpha = 0.25 * strength; ctx.lineWidth = width * 2.4; ctx.beginPath(); draw(); ctx.stroke();
    ctx.globalAlpha = 1; ctx.lineWidth = width; ctx.beginPath(); draw(); ctx.stroke();
    ctx.restore();
  }
  function glowDot(ctx, x, y, r, color, a = 1) {
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, color); g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.globalAlpha = a;
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, r, 0, TAU); ctx.fill();
    ctx.restore();
  }
  function label(ctx, text, x, y, size, color, align = 'center', font = NUMF, weight = 700) {
    ctx.font = `${weight} ${size}px ${font}`;
    ctx.textAlign = align; ctx.textBaseline = 'middle';
    ctx.fillStyle = color;
    ctx.fillText(text, x, y);
  }
  function hexPath(ctx, x, y, r, rot = 0) {
    ctx.moveTo(x + Math.cos(rot) * r, y + Math.sin(rot) * r);
    for (let i = 1; i <= 6; i++) ctx.lineTo(x + Math.cos(rot + i * TAU / 6) * r, y + Math.sin(rot + i * TAU / 6) * r);
  }
  function gridBg(ctx, w, h, color) {
    ctx.save();
    ctx.strokeStyle = color || 'rgba(41,240,255,0.06)';
    ctx.lineWidth = 1;
    for (let x = (w % 24) / 2; x < w; x += 24) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke(); }
    for (let y = (h % 24) / 2; y < h; y += 24) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke(); }
    ctx.restore();
  }

  // little bits of light flying off things: sparks, drips, bolts
  function Particles() {
    const ps = [];
    return {
      add(x, y, n, o = {}) {
        for (let i = 0; i < n; i++) {
          const a = o.angle != null ? o.angle + rnd(-o.spread || -1, o.spread || 1) : rnd(0, TAU);
          const sp = rnd(o.min || 60, o.max || 260);
          ps.push({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, life: rnd(0.3, o.life || 0.8), max: 1, c: o.color || AM, g: o.gravity == null ? 600 : o.gravity, r: o.r || 2 });
        }
      },
      step(dt) {
        for (let i = ps.length - 1; i >= 0; i--) {
          const p = ps[i];
          p.life -= dt; if (p.life <= 0) { ps.splice(i, 1); continue; }
          p.vy += p.g * dt; p.x += p.vx * dt; p.y += p.vy * dt;
        }
      },
      draw(ctx) {
        ctx.save(); ctx.globalCompositeOperation = 'lighter';
        ps.forEach((p) => {
          ctx.globalAlpha = clamp(p.life * 2, 0, 1);
          ctx.strokeStyle = p.c; ctx.lineWidth = p.r; ctx.lineCap = 'round';
          ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(p.x - p.vx * 0.03, p.y - p.vy * 0.03); ctx.stroke();
        });
        ctx.restore();
      },
    };
  }

  // ======================================================================== BOLTS
  /*
   * Off with the bolts — spin the ratchet dial anticlockwise — then the new part goes in and the
   * bolts go back on in the right order: a wheel's nuts in a star, the way that keeps it true.
   */
  const BOLT_SETS = {
    lug: { n: 5, turns: 2, shape: 'circle', order: [0, 2, 4, 1, 3], title: 'WHEEL HUB', part: 'tyre' },
    caliper: { n: 2, turns: 2.5, shape: 'row', order: [0, 1], title: 'BRAKE CALIPER', part: 'pads' },
    plugs: { n: 4, turns: 1.6, shape: 'row', order: [0, 2, 1, 3], title: 'IGNITER BANK', part: 'igniters' },
    strut: { n: 3, turns: 2, shape: 'tri', order: [0, 1, 2], title: 'STABILISER MOUNT', part: 'stabiliser' },
  };
  function Bolts(variant, o) {
    const set = BOLT_SETS[variant] || BOLT_SETS.lug;
    const turns = Math.max(1, set.turns - (o.ratchet ? 0.9 : 0));
    const bolts = [];
    for (let i = 0; i < set.n; i++) bolts.push({ turn: 0, off: false, on: false, fly: null, rot: 0 });
    let phase = 'loosen', active = 0, swapT = 0, tightIdx = 0;
    let dial = { x: 0, y: 0, r: 0, ang: -Math.PI / 2, lastA: null, drag: false, clicks: 0 };
    let partBox = { x: 0, y: 0, w: 0, h: 0 };
    const ps = Particles();
    let t = 0, slips = 0, wrong = 0, slipCD = 2;
    const pos = [];
    this.title = set.title;
    this.debug = { pos, order: set.order, dial: () => dial, phase: () => phase, active: () => active };
    this.steps = ['Loosen', 'Swap', 'Torque'];
    this.step = 0;
    this.layout = (w, h) => {
      partBox = { x: 18, y: 14, w: w * 0.56 - 18, h: h - 28 };
      const cx = partBox.x + partBox.w / 2, cy = partBox.y + partBox.h / 2;
      const R = Math.min(partBox.w, partBox.h) * 0.3;
      pos.length = 0;
      for (let i = 0; i < set.n; i++) {
        if (set.shape === 'circle') pos.push({ x: cx + Math.cos(-Math.PI / 2 + i * TAU / set.n) * R, y: cy + Math.sin(-Math.PI / 2 + i * TAU / set.n) * R });
        else if (set.shape === 'tri') pos.push({ x: cx + Math.cos(-Math.PI / 2 + i * TAU / 3) * R, y: cy + 8 + Math.sin(-Math.PI / 2 + i * TAU / 3) * R });
        else pos.push({ x: cx + (i - (set.n - 1) / 2) * Math.min(R * 1.1, partBox.w / (set.n + 0.5)), y: cy });
      }
      dial.r = Math.min(w * 0.19, h * 0.36);
      dial.x = w - dial.r - 26; dial.y = h / 2 + 4;
      this.boltR = Math.max(14, Math.min(26, R * 0.32));
    };
    o.hint('Spin the ratchet anticlockwise to undo the glowing bolt.');
    this.down = (x, y) => {
      if (phase === 'loosen' && Math.hypot(x - dial.x, y - dial.y) < dial.r * 1.6) { dial.drag = true; dial.lastA = Math.atan2(y - dial.y, x - dial.x); }
      if (phase === 'tighten') {
        for (let i = 0; i < set.n; i++) {
          if (Math.hypot(x - pos[i].x, y - pos[i].y) < this.boltR * 1.8 && !bolts[i].on) {
            if (i === set.order[tightIdx]) {
              bolts[i].on = true; bolts[i].spin = 1; tightIdx++;
              o.sound('impact'); o.haptic('heavy');
              ps.add(pos[i].x, pos[i].y, 10, { color: CY, max: 160, gravity: 0 });
              if (tightIdx >= set.n) finish();
              else o.hint(`Good. Bolt ${tightIdx + 1} next — follow the numbers.`);
            } else {
              wrong++; o.sound('bad'); o.haptic('bad');
              o.hint(set.shape === 'circle' ? 'Wrong order. Wheel nuts go on in a star, or the wheel sits crooked.' : 'Wrong order. Follow the numbers.');
              o.jolt(0.4);
            }
            return;
          }
        }
      }
    };
    this.move = (x, y) => {
      if (!dial.drag || phase !== 'loosen') return;
      const a = Math.atan2(y - dial.y, x - dial.x);
      let d = a - dial.lastA;
      if (d > Math.PI) d -= TAU; if (d < -Math.PI) d += TAU;
      dial.lastA = a;
      // anticlockwise on screen undoes it; clockwise does it back up
      const b = bolts[active];
      const delta = -d / TAU;
      b.turn = Math.max(0, b.turn + delta);
      b.rot -= d;
      dial.ang += d;
      const clicks = Math.floor(dial.ang / (TAU / 12));
      if (clicks !== dial.clicks) { dial.clicks = clicks; o.sound('ratchet'); o.haptic('tick'); }
      if (b.turn >= turns) popBolt();
    };
    this.up = () => { dial.drag = false; };
    const popBolt = () => {
      const b = bolts[active];
      b.off = true;
      b.fly = { x: pos[active].x, y: pos[active].y, vx: rnd(-80, 80), vy: -260, r: 0 };
      o.sound('pop'); o.haptic('heavy');
      setTimeout(() => o.sound('clink'), 420);
      ps.add(pos[active].x, pos[active].y, 14, { color: AM });
      active++;
      if (active >= set.n) {
        phase = 'swap'; swapT = 0; this.step = 1; dial.drag = false;
        o.hint(`Old ${set.part} out, new ${set.part} in.`);
        o.sound('whoosh');
      } else o.hint(`${set.n - active} to go.`);
    };
    const finish = () => {
      phase = 'done';
      const q = clamp(1 - wrong * 0.15 - slips * 0.08 - Math.max(0, t - set.n * 4.5) * 0.02, 0.15, 1);
      setTimeout(() => o.done({ quality: q, slips, wrong }), 650);
    };
    this.update = (dt) => {
      t += dt;
      ps.step(dt);
      bolts.forEach((b) => {
        if (b.fly) { b.fly.vy += 900 * dt; b.fly.x += b.fly.vx * dt; b.fly.y += b.fly.vy * dt; b.fly.r += dt * 8; }
        if (b.spin) b.spin = Math.max(0, b.spin - dt * 2);
      });
      // a tired hand slips off the ratchet now and then
      if (phase === 'loosen' && dial.drag && o.shake > 0.25) {
        slipCD -= dt;
        if (slipCD <= 0) {
          slipCD = rnd(2.5, 5) / o.shake;
          if (Math.random() < o.shake) {
            slips++;
            const b = bolts[active];
            b.turn = Math.max(0, b.turn - 0.25);
            dial.drag = false;
            o.sound('thunk'); o.haptic('bad'); o.jolt(0.3);
            o.hint('Your hand slips off the ratchet. So does your will to live. Again.');
          }
        }
      }
      if (phase === 'swap') {
        swapT += dt;
        if (swapT > 1.3) {
          phase = 'tighten'; this.step = 2;
          o.hint(set.shape === 'circle' ? 'Tap the nuts in number order: a star pattern keeps the wheel true.' : 'Tap the bolts in number order to torque them down.');
        }
      }
    };
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h);
      const cx = partBox.x + partBox.w / 2, cy = partBox.y + partBox.h / 2;
      const R = Math.min(partBox.w, partBox.h) * 0.3;
      // the part: a hub, a caliper, a bank of igniters, a mount
      let off = 0;
      if (phase === 'swap') { const k = swapT / 1.3; off = k < 0.5 ? -k * 2 * partBox.w : (1 - k) * 2 * partBox.w; }
      ctx.save();
      ctx.translate(off, 0);
      const fresh = phase === 'tighten' || phase === 'done' || (phase === 'swap' && swapT > 0.65);
      const base = fresh ? CY : '#5a6878';
      if (set.shape === 'circle') {
        glow(ctx, base, 2, () => ctx.arc(cx, cy, R * 1.55, 0, TAU), 0.7);
        glow(ctx, base, 1.5, () => ctx.arc(cx, cy, R * 0.5, 0, TAU), 0.6);
        ctx.fillStyle = fresh ? 'rgba(41,240,255,0.06)' : 'rgba(120,100,80,0.12)';
        ctx.beginPath(); ctx.arc(cx, cy, R * 1.55, 0, TAU); ctx.fill();
      } else if (set.shape === 'row') {
        const ww = Math.min(partBox.w * 0.9, (set.n + 0.6) * R * 1.15), hh = R * 1.3;
        ctx.fillStyle = fresh ? 'rgba(41,240,255,0.06)' : 'rgba(120,100,80,0.12)';
        ctx.fillRect(cx - ww / 2, cy - hh / 2, ww, hh);
        glow(ctx, base, 2, () => ctx.rect(cx - ww / 2, cy - hh / 2, ww, hh), 0.7);
      } else {
        glow(ctx, base, 2, () => { for (let i = 0; i < 3; i++) { const a = -Math.PI / 2 + i * TAU / 3; ctx[i ? 'lineTo' : 'moveTo'](cx + Math.cos(a) * R * 1.5, cy + 8 + Math.sin(a) * R * 1.5); } ctx.closePath(); }, 0.7);
        glow(ctx, base, 1.5, () => ctx.arc(cx, cy + 8, R * 0.45, 0, TAU), 0.6);
      }
      if (!fresh) {
        // rust and grime on the old one
        ctx.fillStyle = 'rgba(160,80,30,0.25)';
        for (let i = 0; i < 9; i++) { ctx.beginPath(); ctx.arc(cx + Math.sin(i * 7.7) * R, cy + Math.cos(i * 3.1) * R * 0.8, 6 + (i % 3) * 4, 0, TAU); ctx.fill(); }
      }
      ctx.restore();
      // the bolts
      const br = this.boltR;
      bolts.forEach((b, i) => {
        const p = pos[i];
        if (b.fly) {
          if (b.fly.y < h + 40) {
            ctx.save(); ctx.translate(b.fly.x, b.fly.y); ctx.rotate(b.fly.r);
            ctx.fillStyle = '#8a96a3'; ctx.beginPath(); hexPath(ctx, 0, 0, br * 0.9); ctx.fill();
            ctx.restore();
          }
        }
        if (phase === 'loosen' && b.off) {
          ctx.strokeStyle = 'rgba(255,255,255,0.15)'; ctx.lineWidth = 1.5;
          ctx.beginPath(); ctx.arc(p.x, p.y, br * 0.45, 0, TAU); ctx.stroke();
          return;
        }
        if (phase === 'swap') return;
        if (phase === 'loosen' || b.on || phase === 'tighten' || phase === 'done') {
          const isActive = phase === 'loosen' && i === active;
          const rise = phase === 'loosen' ? b.turn / turns : 0;
          const col = phase === 'loosen' ? (isActive ? AM : '#8a96a3') : b.on ? GR : CY;
          if (phase !== 'loosen' && !b.on) {
            // an empty hole, waiting, with its number
            glow(ctx, CY, 1.5, () => ctx.arc(p.x, p.y, br * 0.7, 0, TAU), 0.5 + 0.5 * Math.sin(t * 5 + i));
            const n = set.order.indexOf(i) + 1;
            label(ctx, String(n), p.x, p.y + 1, br * 0.8, CY);
            return;
          }
          ctx.save();
          ctx.translate(p.x, p.y - rise * 6);
          ctx.rotate(phase === 'loosen' ? b.rot : (b.spin || 0) * 12);
          ctx.fillStyle = '#1a2028';
          ctx.beginPath(); hexPath(ctx, 0, 0, br * (1 + rise * 0.15)); ctx.fill();
          glow(ctx, col, isActive ? 2.5 : 1.6, () => hexPath(ctx, 0, 0, br * (1 + rise * 0.15)), isActive ? 1 : 0.6);
          ctx.strokeStyle = 'rgba(255,255,255,0.25)'; ctx.lineWidth = 1;
          ctx.beginPath(); ctx.arc(0, 0, br * 0.45, 0, TAU); ctx.stroke();
          ctx.restore();
          if (isActive) {
            // how far undone it is
            ctx.strokeStyle = AM; ctx.lineWidth = 3; ctx.lineCap = 'round';
            ctx.beginPath(); ctx.arc(p.x, p.y, br * 1.45, -Math.PI / 2, -Math.PI / 2 + TAU * (b.turn / turns)); ctx.stroke();
          }
        }
      });
      // the ratchet dial
      if (phase === 'loosen' || phase === 'swap') {
        const d = dial;
        ctx.globalAlpha = phase === 'swap' ? 0.3 : 1;
        ctx.fillStyle = 'rgba(41,240,255,0.04)';
        ctx.beginPath(); ctx.arc(d.x, d.y, d.r, 0, TAU); ctx.fill();
        glow(ctx, CY, 2, () => ctx.arc(d.x, d.y, d.r, 0, TAU), 0.6);
        for (let i = 0; i < 24; i++) {
          const a = d.ang + i * TAU / 24;
          ctx.strokeStyle = i % 2 ? 'rgba(41,240,255,0.25)' : 'rgba(41,240,255,0.6)';
          ctx.lineWidth = 2;
          ctx.beginPath(); ctx.moveTo(d.x + Math.cos(a) * d.r * 0.82, d.y + Math.sin(a) * d.r * 0.82); ctx.lineTo(d.x + Math.cos(a) * d.r * 0.94, d.y + Math.sin(a) * d.r * 0.94); ctx.stroke();
        }
        const kx = d.x + Math.cos(d.ang) * d.r * 0.62, ky = d.y + Math.sin(d.ang) * d.r * 0.62;
        ctx.strokeStyle = 'rgba(41,240,255,0.35)'; ctx.lineWidth = 6; ctx.lineCap = 'round';
        ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(kx, ky); ctx.stroke();
        glowDot(ctx, kx, ky, 26, d.drag ? AM : CY, 0.8);
        ctx.fillStyle = d.drag ? AM : CY; ctx.beginPath(); ctx.arc(kx, ky, 11, 0, TAU); ctx.fill();
        // the way to turn it
        ctx.strokeStyle = 'rgba(255,176,32,0.8)'; ctx.lineWidth = 2;
        const ar = d.r * 1.12, a0 = -0.3 + Math.sin(t * 2) * 0.1;
        ctx.beginPath(); ctx.arc(d.x, d.y, ar, a0, a0 - 1.1, true); ctx.stroke();
        const ae = a0 - 1.1;
        ctx.beginPath(); ctx.moveTo(d.x + Math.cos(ae) * ar, d.y + Math.sin(ae) * ar);
        ctx.lineTo(d.x + Math.cos(ae + 0.15) * (ar - 8), d.y + Math.sin(ae + 0.15) * (ar - 8));
        ctx.moveTo(d.x + Math.cos(ae) * ar, d.y + Math.sin(ae) * ar);
        ctx.lineTo(d.x + Math.cos(ae + 0.15) * (ar + 8), d.y + Math.sin(ae + 0.15) * (ar + 8));
        ctx.stroke();
        label(ctx, 'RATCHET', d.x, d.y + 2, 11, 'rgba(200,250,255,0.7)');
        ctx.globalAlpha = 1;
      }
      ps.draw(ctx);
    };
  }

  // ======================================================================== BALANCE / BRAKE BED-IN
  /*
   * A wheel on the balancer, a weight to clip on when the mark comes round to the top; or, for
   * brakes, a pressure needle to catch in the green while the new pads bed in. Three good taps.
   */
  function Balance(variant, o) {
    const brake = variant === 'brake';
    let ang = rnd(0, TAU), speed = brake ? 2.6 : 3.4, zone = brake ? 0.28 : 0.3;
    let hits = 0, misses = 0, t = 0, flash = 0, flashCol = GR;
    const need = 3;
    let cx = 0, cy = 0, R = 0;
    const ps = Particles();
    let finished = false;
    this.title = brake ? 'PAD BED-IN' : 'WHEEL BALANCER';
    this.debug = { inZone: () => inZone(), hits: () => hits };
    this.steps = ['1', '2', '3'];
    this.step = 0;
    o.hint(brake ? 'Tap when the needle is in the green. Three times.' : 'Tap when the white mark reaches the top to clip on a weight.');
    this.layout = (w, h) => { cx = w / 2; cy = h / 2 + (brake ? h * 0.12 : 0); R = Math.min(w, h) * (brake ? 0.42 : 0.36); };
    const markAngle = () => (brake ? Math.sin(ang) * 1.25 : ang);
    const target = () => (brake ? 0 : -Math.PI / 2);
    const inZone = () => {
      let d = markAngle() - target();
      d = Math.atan2(Math.sin(d), Math.cos(d));
      const wob = o.shake * 0.12 * Math.sin(t * 3.7);
      return Math.abs(d + wob) < zone / 2;
    };
    this.down = () => {
      if (finished) return;
      if (inZone()) {
        hits++; this.step = hits;
        flash = 1; flashCol = GR;
        o.sound('hit'); o.haptic('ok');
        const a = brake ? -Math.PI / 2 : -Math.PI / 2;
        ps.add(cx + Math.cos(a) * R, cy + Math.sin(a) * R, 18, { color: GR, gravity: 200 });
        speed *= 1.18; zone *= 0.86;
        if (hits >= need) {
          finished = true;
          o.hint(brake ? 'Pads bedded in. They will now stop the car instead of announcing it.' : 'Balanced. It will spin true, unlike the owner.');
          setTimeout(() => o.done({ quality: clamp(1 - misses * 0.14, 0.2, 1), misses }), 600);
        } else o.hint(`${need - hits} more.`);
      } else {
        misses++; flash = 1; flashCol = RD;
        o.sound('miss'); o.haptic('bad');
        o.hint(brake ? 'Too early or too late. The pads squeal at you.' : 'Missed. The weight goes somewhere. Probably your foot.');
      }
    };
    this.move = () => {};
    this.up = () => {};
    this.update = (dt) => { t += dt; ang += speed * dt; flash = Math.max(0, flash - dt * 2.5); ps.step(dt); };
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h);
      if (brake) {
        // a pressure gauge: an arc, a green band at the top, a needle swinging
        const a0 = -Math.PI / 2 - 1.4, a1 = -Math.PI / 2 + 1.4;
        glow(ctx, '#5a6878', 10, () => ctx.arc(cx, cy, R, a0, a1), 0.4);
        glow(ctx, GR, 10, () => ctx.arc(cx, cy, R, -Math.PI / 2 - zone / 2, -Math.PI / 2 + zone / 2), 0.9);
        for (let i = 0; i <= 14; i++) {
          const a = lerp(a0, a1, i / 14);
          ctx.strokeStyle = 'rgba(200,250,255,0.5)'; ctx.lineWidth = 2;
          ctx.beginPath(); ctx.moveTo(cx + Math.cos(a) * (R - 16), cy + Math.sin(a) * (R - 16)); ctx.lineTo(cx + Math.cos(a) * (R - 26), cy + Math.sin(a) * (R - 26)); ctx.stroke();
        }
        const na = -Math.PI / 2 + markAngle();
        glow(ctx, inZone() ? GR : AM, 3, () => { ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.cos(na) * (R - 6), cy + Math.sin(na) * (R - 6)); });
        ctx.fillStyle = '#1a2028'; ctx.beginPath(); ctx.arc(cx, cy, 14, 0, TAU); ctx.fill();
        glow(ctx, CY, 2, () => ctx.arc(cx, cy, 14, 0, TAU));
        label(ctx, 'BAR', cx, cy + 34, 12, 'rgba(200,250,255,0.6)');
      } else {
        // the wheel, spinning, with its weights clipped on
        ctx.save(); ctx.translate(cx, cy); ctx.rotate(ang);
        ctx.fillStyle = '#0d1016'; ctx.beginPath(); ctx.arc(0, 0, R, 0, TAU); ctx.fill();
        ctx.strokeStyle = '#1e232b'; ctx.lineWidth = R * 0.22; ctx.beginPath(); ctx.arc(0, 0, R * 0.86, 0, TAU); ctx.stroke();
        glow(ctx, CY, 2, () => ctx.arc(0, 0, R * 0.62, 0, TAU), 0.8);
        for (let i = 0; i < 5; i++) {
          const a = i * TAU / 5;
          ctx.strokeStyle = '#3a4452'; ctx.lineWidth = 8; ctx.lineCap = 'round';
          ctx.beginPath(); ctx.moveTo(Math.cos(a) * R * 0.15, Math.sin(a) * R * 0.15); ctx.lineTo(Math.cos(a) * R * 0.6, Math.sin(a) * R * 0.6); ctx.stroke();
        }
        // the mark
        ctx.fillStyle = '#ffffff';
        ctx.beginPath(); ctx.arc(R * 0.86, 0, 8, 0, TAU); ctx.fill();
        glowDot(ctx, R * 0.86, 0, 26, '#ffffff', 0.6);
        ctx.restore();
        // the target at the top
        const za = -Math.PI / 2;
        glow(ctx, inZone() ? GR : AM, 6, () => ctx.arc(cx, cy, R * 1.12, za - zone / 2, za + zone / 2), 0.9);
        ctx.fillStyle = inZone() ? GR : AM;
        ctx.beginPath(); ctx.moveTo(cx, cy - R * 1.12 - 6); ctx.lineTo(cx - 9, cy - R * 1.12 - 20); ctx.lineTo(cx + 9, cy - R * 1.12 - 20); ctx.fill();
      }
      if (flash > 0) { ctx.fillStyle = flashCol; ctx.globalAlpha = flash * 0.12; ctx.fillRect(0, 0, w, h); ctx.globalAlpha = 1; }
      ps.draw(ctx);
    };
  }

  // ======================================================================== POUR
  /*
   * Hold to pour, let go to stop — but it keeps coming for a moment after, so let go early. Drain
   * the old stuff first, fill to the green band. Over it is a spill; the robot, if it's working,
   * does all this for you.
   */
  const FLUIDS = {
    drain: { title: 'DRAIN OLD OIL', color: '#2a1a0c', glowC: AM, label: 'DRAIN' },
    oil: { title: 'FILL NEW OIL', color: '#d89a2a', glowC: AM, label: 'POUR' },
    coolant: { title: 'COOLANT FILL', color: '#3dff9a', glowC: GR, label: 'POUR' },
  };
  function Pour(variant, o) {
    const fl = FLUIDS[variant] || FLUIDS.oil;
    const drain = variant === 'drain';
    let level = drain ? 0.92 : rnd(0.06, 0.16);
    const bandMid = rnd(0.66, 0.8), bandH = 0.11 - Math.min(0.04, (o.day || 1) * 0.004);
    const lo = bandMid - bandH / 2, hi = bandMid + bandH / 2;
    let flow = 0, holding = false, t = 0, spilled = 0, done = false, surge = 0, settle = 0;
    let tank = { x: 0, y: 0, w: 0, h: 0 }, btn = { x: 0, y: 0, r: 0 };
    const bubbles = [];
    const ps = Particles();
    let snd = null;
    const bits = ['a coin', 'a tooth?', 'part of a sandwich', 'a small bolt', 'a wedding ring', 'glitter. So much glitter'];
    let bitShown = false;
    this.title = fl.title;
    this.debug = { btn: () => btn, level: () => level, lo, hi, flow: () => flow };
    this.steps = [drain ? 'Drain' : 'Fill'];
    this.step = 0;
    o.hint(drain ? 'Hold DRAIN until the old oil is out.' : 'Hold POUR. Let go just before the green band — it keeps flowing a moment.');
    this.layout = (w, h) => {
      tank = { x: w * 0.2, y: h * 0.1, w: w * 0.28, h: h * 0.8 };
      btn = { x: w * 0.76, y: h * 0.55, r: Math.min(w * 0.13, h * 0.26) };
    };
    this.down = (x, y) => {
      if (done) return;
      if (Math.hypot(x - btn.x, y - btn.y) < btn.r * 1.5) {
        holding = true; o.haptic('tap');
        if (!snd) snd = o.loop(drain ? 'drain' : 'pour');
      }
    };
    this.move = () => {};
    this.up = () => { holding = false; };
    this.stop = () => { if (snd) { snd.stop(); snd = null; } };
    this.update = (dt) => {
      t += dt;
      ps.step(dt);
      // flow builds while held and dies away after — the lag is the whole game
      const target = holding ? 1 : 0;
      flow += (target - flow) * Math.min(1, dt * (holding ? 3.2 : 4.5));
      if (o.shake > 0.3 && holding && Math.random() < dt * o.shake * 0.8) surge = rnd(0.3, 0.7);
      surge = Math.max(0, surge - dt * 1.5);
      const rate = (drain ? 0.42 : 0.24) * (flow + surge * flow);
      if (snd) snd.set(flow);
      if (drain) {
        level = Math.max(0, level - rate * dt);
        if (level < 0.5 && !bitShown) { bitShown = true; o.hint(`Something came out with the oil: ${bits[Math.floor(Math.random() * bits.length)]}.`); }
        if (level <= 0.02 && !done) { done = true; this.stop(); o.sound('glug'); setTimeout(() => o.done({ quality: 1 }), 400); }
      } else {
        level += rate * dt;
        if (level > hi + 0.005) {
          // over the top of the band: the rest goes on the floor
          if (level > hi + 0.06 || (!holding && flow < 0.05)) {
            spilled += level - hi; level = Math.min(level, hi + 0.06);
          }
        }
        if (!holding && flow < 0.03 && level > 0.15) {
          settle += dt;
          if (level >= lo && settle > 0.25 && !done) {
            done = true; this.stop();
            const over = Math.max(0, level - hi) + spilled;
            const q = clamp(1 - over * 6 - Math.abs(level - bandMid) * 1.5, 0.2, 1);
            if (over > 0.01) { o.hint('Overfilled. The floor gets some too. The floor is always thirsty.'); o.sound('bad'); }
            else { o.hint(Math.abs(level - bandMid) < bandH * 0.25 ? 'Dead on the line. Nobody will ever notice.' : 'In the band. Good enough for the city.'); o.sound('good'); }
            this.step = 1;
            setTimeout(() => o.done({ quality: q, spilled: over }), 700);
          }
        } else settle = 0;
        if (level > hi + 0.02 && Math.random() < dt * 20) ps.add(tank.x + tank.w, tank.y + tank.h * (1 - Math.min(1, level)), 2, { color: fl.color === '#3dff9a' ? GR : AM, angle: 0.6, spread: 0.6, min: 40, max: 120 });
      }
      if (Math.random() < dt * (3 + flow * 12)) bubbles.push({ x: rnd(0.1, 0.9), y: 1, s: rnd(2, 5), v: rnd(0.15, 0.35) });
      for (let i = bubbles.length - 1; i >= 0; i--) { bubbles[i].y -= bubbles[i].v * dt; if (bubbles[i].y < 1 - level) bubbles.splice(i, 1); }
    };
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h);
      const { x, y } = tank, tw = tank.w, th = tank.h;
      // the stream from the can (or out through the drain)
      if (flow > 0.02) {
        const sx = drain ? x + tw * 0.5 : x + tw * 0.5;
        const sy0 = drain ? y + th : y - 18, sy1 = drain ? h : y + th * (1 - level);
        ctx.save();
        ctx.globalCompositeOperation = 'lighter';
        ctx.fillStyle = drain ? 'rgba(90,60,30,0.8)' : fl.color;
        ctx.globalAlpha = 0.85;
        const sw = 3 + flow * 7 + Math.sin(t * 30) * 1;
        ctx.fillRect(sx - sw / 2, Math.min(sy0, sy1), sw, Math.abs(sy1 - sy0));
        ctx.restore();
      }
      // the tank and what's in it
      ctx.save();
      ctx.beginPath(); ctx.rect(x, y, tw, th); ctx.clip();
      const top = y + th * (1 - Math.min(1.05, level));
      const g = ctx.createLinearGradient(0, top, 0, y + th);
      g.addColorStop(0, fl.color); g.addColorStop(1, drain ? '#0a0604' : '#00000080');
      ctx.fillStyle = g;
      ctx.beginPath(); ctx.moveTo(x, y + th);
      for (let i = 0; i <= 24; i++) { const px = x + (i / 24) * tw; ctx.lineTo(px, top + Math.sin(i * 0.8 + t * 6) * (2 + flow * 4)); }
      ctx.lineTo(x + tw, y + th); ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.35)';
      bubbles.forEach((b) => { ctx.beginPath(); ctx.arc(x + b.x * tw, y + b.y * th, b.s, 0, TAU); ctx.fill(); });
      ctx.restore();
      glow(ctx, '#7a8a9a', 2, () => ctx.rect(x, y, tw, th), 0.5);
      // the band, and the ticks up the side
      if (!drain) {
        const by0 = y + th * (1 - hi), by1 = y + th * (1 - lo);
        ctx.fillStyle = 'rgba(61,255,154,0.12)'; ctx.fillRect(x - 10, by0, tw + 20, by1 - by0);
        glow(ctx, GR, 1.5, () => { ctx.moveTo(x - 14, by0); ctx.lineTo(x + tw + 14, by0); ctx.moveTo(x - 14, by1); ctx.lineTo(x + tw + 14, by1); }, 0.8);
        label(ctx, 'FULL', x + tw + 20, (by0 + by1) / 2, 11, GR, 'left');
      } else {
        label(ctx, 'EMPTY', x + tw + 14, y + th - 8, 11, AM, 'left');
      }
      for (let i = 0; i <= 10; i++) {
        const ty = y + th * (1 - i / 10);
        ctx.strokeStyle = 'rgba(200,250,255,0.35)'; ctx.lineWidth = 1;
        ctx.beginPath(); ctx.moveTo(x - 8, ty); ctx.lineTo(x - (i % 5 ? 3 : 0), ty); ctx.stroke();
      }
      label(ctx, `${Math.round(level * 100)}%`, x - 14, y + th * (1 - Math.min(1, level)), 13, '#e6feff', 'right');
      // the button
      const pressed = holding;
      ctx.fillStyle = pressed ? 'rgba(255,176,32,0.18)' : 'rgba(41,240,255,0.06)';
      ctx.beginPath(); ctx.arc(btn.x, btn.y, btn.r * (pressed ? 0.95 : 1), 0, TAU); ctx.fill();
      glow(ctx, pressed ? AM : CY, 3, () => ctx.arc(btn.x, btn.y, btn.r * (pressed ? 0.95 : 1), 0, TAU));
      glow(ctx, pressed ? AM : CY, 1.5, () => ctx.arc(btn.x, btn.y, btn.r * 0.78 + Math.sin(t * 4) * 2, 0, TAU), 0.5);
      label(ctx, 'HOLD', btn.x, btn.y - 10, 11, 'rgba(200,250,255,0.7)');
      label(ctx, fl.label, btn.x, btn.y + 9, 18, pressed ? AM : '#e6feff');
      ps.draw(ctx);
    };
  }

  // ======================================================================== WIRES
  /*
   * A harness with its plugs pulled: drag each lead to the socket of its colour and symbol. A wrong
   * one bites. When you're tired the colours aren't the help they were, so the symbols are there too.
   */
  const WIRE_COLS = [['#ff3b5c', '▲'], ['#29f0ff', '●'], ['#ffb020', '■'], ['#3dff9a', '◆'], ['#ff2bd6', '★'], ['#8a7dff', '✚']];
  function Wires(variant, o) {
    const n = variant === 'cell' ? 5 : 4;
    const cols = WIRE_COLS.slice().sort(() => Math.random() - 0.5).slice(0, n);
    const right = cols.map((c, i) => i).sort(() => Math.random() - 0.5);
    const done = new Array(n).fill(false);
    let L = [], Rr = [], drag = null, zaps = 0, t = 0, finished = false, flash = 0;
    const ps = Particles();
    this.title = variant === 'cell' ? 'FUSION CELL HARNESS' : 'HEADLIGHT DRIVER';
    this.debug = { L: () => L, R: () => Rr, right, n };
    this.steps = ['Connect'];
    this.step = 0;
    o.hint('Drag each lead on the left to the socket with the same colour and symbol.');
    this.layout = (w, h) => {
      L = []; Rr = [];
      const top = h * 0.14, gap = (h * 0.74) / (n - 1 || 1);
      for (let i = 0; i < n; i++) { L.push({ x: w * 0.16, y: top + i * gap }); Rr.push({ x: w * 0.84, y: top + i * gap }); }
    };
    this.down = (x, y) => {
      if (finished) return;
      for (let i = 0; i < n; i++) if (!done[i] && Math.hypot(x - L[i].x, y - L[i].y) < 34) { drag = { i, x, y }; o.haptic('tap'); o.sound('tap'); return; }
    };
    this.move = (x, y) => { if (drag) { drag.x = x; drag.y = y; } };
    this.up = (x, y) => {
      if (!drag) return;
      const i = drag.i;
      const w2 = wobble(t, o.shake * 14);
      const px = x + w2.x, py = y + w2.y;
      let hit = -1;
      for (let j = 0; j < n; j++) if (Math.hypot(px - Rr[j].x, py - Rr[j].y) < 40) hit = j;
      if (hit >= 0) {
        if (right[hit] === i) {
          done[i] = true; o.sound('plug'); o.haptic('ok');
          ps.add(Rr[hit].x, Rr[hit].y, 16, { color: cols[i][0], gravity: 0, max: 180 });
          if (done.every(Boolean)) {
            finished = true; this.step = 1;
            o.hint(variant === 'cell' ? 'Cell online. The car hums. You don\'t.' : 'Lights on. The car can see again. Lucky car.');
            o.sound('good');
            setTimeout(() => o.done({ quality: clamp(1 - zaps * 0.18 - Math.max(0, t - n * 3) * 0.015, 0.2, 1), zaps }), 600);
          }
        } else {
          zaps++; flash = 1;
          o.sound('zap'); o.haptic('bad'); o.jolt(0.6); o.zap && o.zap();
          ps.add(Rr[hit].x, Rr[hit].y, 24, { color: '#ffffff', max: 300 });
          o.hint(['Wrong socket. That one bites.', 'Zap. Your fillings hum for a while.', 'Wrong. Your hair is now slightly more interesting.'][zaps % 3]);
        }
      }
      drag = null;
    };
    this.update = (dt) => { t += dt; ps.step(dt); flash = Math.max(0, flash - dt * 3); };
    const lead = (ctx, x0, y0, x1, y1, c) => glow(ctx, c, 3.5, () => { ctx.moveTo(x0, y0); ctx.bezierCurveTo(x0 + (x1 - x0) * 0.5, y0, x0 + (x1 - x0) * 0.5, y1, x1, y1); }, 0.9);
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h);
      // the board between: a dark chip with traces
      ctx.fillStyle = 'rgba(10,16,28,0.7)';
      ctx.fillRect(w * 0.32, h * 0.08, w * 0.36, h * 0.84);
      ctx.strokeStyle = 'rgba(41,240,255,0.18)'; ctx.lineWidth = 1;
      for (let i = 0; i < 8; i++) { ctx.beginPath(); ctx.moveTo(w * 0.32, h * (0.15 + i * 0.1)); ctx.lineTo(w * 0.42, h * (0.15 + i * 0.1)); ctx.lineTo(w * 0.46, h * (0.2 + i * 0.1)); ctx.stroke(); }
      label(ctx, variant === 'cell' ? 'FUSION 9 · 400V' : 'LUMEN DRV', w / 2, h / 2, 13, 'rgba(41,240,255,0.35)');
      for (let i = 0; i < n; i++) {
        const c = cols[i];
        const j = right.indexOf(i);
        if (done[i]) lead(ctx, L[i].x, L[i].y, Rr[j].x, Rr[j].y, c[0]);
      }
      for (let i = 0; i < n; i++) {
        const c = cols[i];
        ctx.fillStyle = '#0d1016'; ctx.beginPath(); ctx.arc(L[i].x, L[i].y, 18, 0, TAU); ctx.fill();
        glow(ctx, c[0], 2.5, () => ctx.arc(L[i].x, L[i].y, 18, 0, TAU), done[i] ? 0.5 : 1);
        label(ctx, c[1], L[i].x, L[i].y + 1, 15, c[0], 'center', TXTF);
        ctx.strokeStyle = c[0]; ctx.lineWidth = 6; ctx.globalAlpha = 0.5;
        ctx.beginPath(); ctx.moveTo(0, L[i].y); ctx.lineTo(L[i].x - 18, L[i].y); ctx.stroke(); ctx.globalAlpha = 1;
      }
      for (let j = 0; j < n; j++) {
        const c = cols[right[j]];
        const lit = done[right[j]];
        ctx.fillStyle = '#0d1016'; ctx.fillRect(Rr[j].x - 18, Rr[j].y - 18, 36, 36);
        glow(ctx, c[0], 2.5, () => ctx.rect(Rr[j].x - 18, Rr[j].y - 18, 36, 36), lit ? 1 : 0.55);
        label(ctx, c[1], Rr[j].x, Rr[j].y + 1, 15, c[0], 'center', TXTF);
        if (lit) glowDot(ctx, Rr[j].x, Rr[j].y, 30, c[0], 0.5);
      }
      if (drag) {
        const c = cols[drag.i];
        const w2 = wobble(t, o.shake * 14);
        lead(ctx, L[drag.i].x, L[drag.i].y, drag.x + w2.x, drag.y + w2.y, c[0]);
        glowDot(ctx, drag.x + w2.x, drag.y + w2.y, 22, c[0], 0.9);
      }
      if (flash > 0) { ctx.fillStyle = '#ffffff'; ctx.globalAlpha = flash * 0.25; ctx.fillRect(0, 0, w, h); ctx.globalAlpha = 1; }
      ps.draw(ctx);
    };
  }

  // ======================================================================== WAVE
  /*
   * The AI core (or the stabiliser) hums at the wrong frequency. Slide its frequency and amplitude
   * until your wave lies on the target, and hold it there while it locks.
   */
  function Wave(variant, o) {
    const knobs = [
      { name: 'FREQ', v: rnd(0.05, 0.3), target: rnd(0.45, 0.9) },
      { name: 'AMP', v: rnd(0.75, 0.95), target: rnd(0.25, 0.6) },
    ];
    if ((o.day || 1) >= 4 && variant === 'core') knobs.push({ name: 'PHASE', v: rnd(0, 0.2), target: rnd(0.4, 0.9) });
    if (Math.random() < 0.5) knobs.forEach((k) => { if (Math.abs(k.v - k.target) < 0.25) k.v = clamp(k.target + (k.target > 0.5 ? -0.4 : 0.4), 0, 1); });
    let lock = 0, t = 0, finished = false, drag = -1, scope = {}, sliders = [];
    const ps = Particles();
    this.title = variant === 'core' ? 'AI CORE · MOOD TUNING' : 'STABILISER TUNING';
    this.debug = { knobs, sliders: () => sliders };
    this.steps = ['Tune', 'Lock'];
    this.step = 0;
    o.hint('Slide the controls until your wave (blue) lies on the target (pink). Hold it there.');
    this.layout = (w, h) => {
      const sw = Math.min(64, w * 0.08);
      scope = { x: 16, y: 16, w: w - 32 - knobs.length * (sw + 14), h: h - 32 };
      sliders = knobs.map((k, i) => ({ x: scope.x + scope.w + 18 + i * (sw + 14), y: 24, w: sw, h: h - 60 }));
    };
    const wave = (k, x, ph) => {
      const freq = 1 + k[0].v * 5, amp = 0.15 + k[1].v * 0.8, phase = k[2] ? k[2].v * TAU : 0;
      return Math.sin(x * freq * TAU + ph + phase) * amp;
    };
    const target = (x, ph) => {
      const freq = 1 + knobs[0].target * 5, amp = 0.15 + knobs[1].target * 0.8, phase = knobs[2] ? knobs[2].target * TAU : 0;
      return Math.sin(x * freq * TAU + ph + phase) * amp;
    };
    const error = () => knobs.reduce((s, k) => s + Math.abs(k.v - k.target), 0) / knobs.length;
    this.down = (x, y) => {
      if (finished) return;
      sliders.forEach((s, i) => { if (x > s.x - 14 && x < s.x + s.w + 14 && y > s.y - 20 && y < s.y + s.h + 20) { drag = i; this.move(x, y); o.haptic('tap'); } });
    };
    this.move = (x, y) => {
      if (drag < 0) return;
      const s = sliders[drag];
      const before = knobs[drag].v;
      knobs[drag].v = clamp(1 - (y - s.y) / s.h, 0, 1);
      if (Math.floor(before * 20) !== Math.floor(knobs[drag].v * 20)) { o.sound('tick'); o.haptic('tick'); }
    };
    this.up = () => { drag = -1; };
    this.update = (dt) => {
      t += dt; ps.step(dt);
      // tired fingers drift
      if (o.shake > 0.2) knobs.forEach((k, i) => { if (i === drag) k.v = clamp(k.v + Math.sin(t * 3 + i) * o.shake * 0.003, 0, 1); });
      const e = error();
      if (e < 0.045 && !finished) {
        lock += dt; this.step = 1;
        if (lock > 1.0) {
          finished = true;
          o.sound('great'); o.haptic('ok');
          o.hint(variant === 'core' ? 'Locked. The car AI says it feels "seen". It\'s still a car.' : 'Locked. The car floats like a sensible car again.');
          ps.add(scope.x + scope.w / 2, scope.y + scope.h / 2, 40, { color: GR, gravity: 0, max: 300 });
          setTimeout(() => o.done({ quality: clamp(1.15 - t / 25, 0.3, 1) }), 700);
        }
      } else if (!finished) { lock = Math.max(0, lock - dt * 2); this.step = 0; }
    };
    this.draw = (ctx, w, h) => {
      const { x, y } = scope, sw = scope.w, sh = scope.h;
      ctx.fillStyle = 'rgba(5,12,20,0.75)'; ctx.fillRect(x, y, sw, sh);
      ctx.strokeStyle = 'rgba(41,240,255,0.12)'; ctx.lineWidth = 1;
      for (let i = 1; i < 10; i++) { ctx.beginPath(); ctx.moveTo(x + sw * i / 10, y); ctx.lineTo(x + sw * i / 10, y + sh); ctx.stroke(); }
      for (let i = 1; i < 6; i++) { ctx.beginPath(); ctx.moveTo(x, y + sh * i / 6); ctx.lineTo(x + sw, y + sh * i / 6); ctx.stroke(); }
      glow(ctx, 'rgba(41,240,255,0.5)', 1, () => ctx.rect(x, y, sw, sh), 0.5);
      const ph = t * 2.2, mid = y + sh / 2, A = sh * 0.42;
      ctx.setLineDash([7, 6]);
      glow(ctx, PK, 2, () => { for (let i = 0; i <= 120; i++) { const u = i / 120; ctx[i ? 'lineTo' : 'moveTo'](x + u * sw, mid - target(u, ph) * A); } }, 0.8);
      ctx.setLineDash([]);
      const e = error();
      const col = e < 0.045 ? GR : CY;
      glow(ctx, col, 2.5, () => { for (let i = 0; i <= 120; i++) { const u = i / 120; ctx[i ? 'lineTo' : 'moveTo'](x + u * sw, mid - wave(knobs, u, ph) * A); } });
      const sync = Math.round(clamp(1 - e * 2.2, 0, 1) * 100);
      label(ctx, `SYNC ${sync}%`, x + 12, y + 16, 13, col, 'left');
      if (lock > 0) {
        ctx.fillStyle = GR; ctx.fillRect(x, y + sh - 4, sw * Math.min(1, lock / 1.0), 4);
        label(ctx, 'LOCKING…', x + sw - 12, y + 16, 12, GR, 'right');
      }
      sliders.forEach((s, i) => {
        const k = knobs[i];
        ctx.fillStyle = 'rgba(41,240,255,0.06)'; ctx.fillRect(s.x, s.y, s.w, s.h);
        glow(ctx, CY, 1.2, () => ctx.rect(s.x, s.y, s.w, s.h), 0.5);
        const ky = s.y + s.h * (1 - k.v);
        ctx.fillStyle = 'rgba(41,240,255,0.2)'; ctx.fillRect(s.x, ky, s.w, s.y + s.h - ky);
        glow(ctx, drag === i ? AM : CY, 3, () => { ctx.moveTo(s.x - 6, ky); ctx.lineTo(s.x + s.w + 6, ky); });
        label(ctx, k.name, s.x + s.w / 2, s.y + s.h + 18, 11, 'rgba(200,250,255,0.75)');
      });
      ps.draw(ctx);
    };
  }

  // ======================================================================== WELD
  /*
   * Run the torch along the crack. Stay on it and the bead closes it, glowing and then cooling; wander
   * off and you burn the paint. A tired hand weaves.
   */
  function Weld(variant, o) {
    const pipe = variant === 'pipe';
    let pts = [], filled = [], heat = [], burns = [], torch = null, t = 0, finished = false, snd = null, offT = 0;
    let area = {};
    const ps = Particles();
    this.title = pipe ? 'SCRUBBER PIPE · WELD' : 'BODY PANEL · WELD';
    this.debug = { pts: () => pts };
    this.steps = ['Weld'];
    this.step = 0;
    o.hint('Drag the torch along the glowing crack. Stay on the line.');
    this.layout = (w, h) => {
      area = { x: w * 0.06, y: h * 0.12, w: w * 0.88, h: h * 0.76 };
      if (pts.length) return;
      // the crack: a jagged line across the panel, or along the pipe
      const n = 70;
      const amp = pipe ? area.h * 0.12 : area.h * 0.3;
      let drift = 0;
      for (let i = 0; i < n; i++) {
        const u = i / (n - 1);
        drift += rnd(-1, 1) * amp * 0.08;
        drift *= 0.94;
        const yy = area.y + area.h / 2 + Math.sin(u * TAU * rnd(0.8, 1.2) + 1) * amp * 0.5 + drift;
        pts.push({ x: area.x + area.w * (0.06 + u * 0.88), y: clamp(yy, area.y + 20, area.y + area.h - 20) });
        filled.push(0); heat.push(0);
      }
    };
    this.down = (x, y) => { if (!finished) { torch = { x, y }; if (!snd) snd = o.loop('weld'); } };
    this.move = (x, y) => { if (torch) { torch.x = x; torch.y = y; } };
    this.up = () => { torch = null; if (snd) { snd.stop(); snd = null; } };
    this.stop = () => { if (snd) { snd.stop(); snd = null; } };
    this.update = (dt) => {
      t += dt; ps.step(dt);
      // the bead cools from white through orange to grey
      for (let i = 0; i < heat.length; i++) heat[i] = Math.max(0, heat[i] - dt * 0.6);
      if (!torch || finished) return;
      const w2 = wobble(t, 3 + o.shake * 22);
      const tx = torch.x + w2.x, ty = torch.y + w2.y - 26; // the tip sits above the finger, so you can see it
      let near = Infinity, any = false;
      pts.forEach((p, i) => {
        const d = Math.hypot(p.x - tx, p.y - ty);
        near = Math.min(near, d);
        if (d < 22) { filled[i] = Math.min(1, filled[i] + dt * 9); heat[i] = 1; any = true; }
      });
      if (snd) snd.set(near < 40 ? 1 : 0.3);
      if (near > 40) {
        offT += dt;
        if (Math.random() < dt * 12) burns.push({ x: tx, y: ty, r: rnd(4, 9) });
      }
      if (Math.random() < dt * 40) ps.add(tx, ty, 2, { color: any ? '#ffd27a' : '#ff8a5a', min: 80, max: 340, life: 0.5 });
      const prog = filled.reduce((s, f) => s + f, 0) / filled.length;
      this.progress = prog;
      if (prog > 0.96) {
        finished = true; this.step = 1; this.stop();
        o.sound('great'); o.haptic('ok');
        o.hint(pipe ? 'Sealed. It will now exhale like a dignified dragon.' : 'Welded. The wall it hit is still fine, for the record.');
        setTimeout(() => o.done({ quality: clamp(1 - burns.length * 0.012 - offT * 0.05, 0.25, 1), burns: burns.length }), 700);
      }
    };
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h);
      // the metal
      const { x, y } = area, aw = area.w, ah = area.h;
      const g = ctx.createLinearGradient(0, y, 0, y + ah);
      if (pipe) { g.addColorStop(0, '#2a323c'); g.addColorStop(0.35, '#8a96a3'); g.addColorStop(0.5, '#c8d0da'); g.addColorStop(0.7, '#5a6878'); g.addColorStop(1, '#1a2028'); }
      else { g.addColorStop(0, '#3a2a40'); g.addColorStop(1, '#1a1424'); }
      ctx.fillStyle = g;
      ctx.fillRect(x, y, aw, ah);
      glow(ctx, '#5a6878', 1.5, () => ctx.rect(x, y, aw, ah), 0.4);
      burns.forEach((b) => { ctx.fillStyle = 'rgba(0,0,0,0.45)'; ctx.beginPath(); ctx.arc(b.x, b.y, b.r, 0, TAU); ctx.fill(); });
      // the crack, the bead over it
      glow(ctx, 'rgba(255,59,92,0.9)', 1.5, () => pts.forEach((p, i) => ctx[i ? 'lineTo' : 'moveTo'](p.x, p.y)), 0.5 + 0.3 * Math.sin(t * 4));
      ctx.lineCap = 'round';
      for (let i = 1; i < pts.length; i++) {
        const f = Math.min(filled[i], filled[i - 1]);
        if (f <= 0) continue;
        const hot = Math.max(heat[i], heat[i - 1]);
        ctx.strokeStyle = `rgb(${Math.round(lerp(120, 255, hot))},${Math.round(lerp(120, 200, hot * hot))},${Math.round(lerp(130, 120, hot))})`;
        ctx.lineWidth = 7 * f;
        ctx.beginPath(); ctx.moveTo(pts[i - 1].x, pts[i - 1].y); ctx.lineTo(pts[i].x, pts[i].y); ctx.stroke();
        // ripples in the bead
        if (i % 2 === 0) { ctx.strokeStyle = 'rgba(0,0,0,0.25)'; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(pts[i].x, pts[i].y, 3 * f, 0, TAU); ctx.stroke(); }
      }
      if (torch) {
        const w2 = wobble(t, 3 + o.shake * 22);
        const tx = torch.x + w2.x, ty = torch.y + w2.y - 26;
        glowDot(ctx, tx, ty, 60, '#ffd27a', 0.55);
        glowDot(ctx, tx, ty, 14, '#ffffff', 1);
        ctx.strokeStyle = 'rgba(200,250,255,0.4)'; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.moveTo(torch.x, torch.y); ctx.lineTo(tx, ty + 6); ctx.stroke();
      }
      const prog = this.progress || 0;
      ctx.fillStyle = 'rgba(41,240,255,0.15)'; ctx.fillRect(x, y + ah + 8, aw, 4);
      ctx.fillStyle = CY; ctx.fillRect(x, y + ah + 8, aw * prog, 4);
      ps.draw(ctx);
    };
  }

  // ======================================================================== HACK
  /*
   * The firmware's ransomware guards its own deletion with a code: watch the cells light up, then
   * tap them back in the same order. Three rounds, longer each time.
   */
  const GLYPHS = ['Ω', 'Ψ', 'Δ', 'Σ', 'λ', 'Ξ'];
  const HCOL = [CY, PK, AM, GR, '#8a7dff', RD];
  function Hack(variant, o) {
    const nT = 6;
    const lengths = [3, 4, (o.day || 1) >= 6 ? 6 : 5];
    let round = 0, seq = [], showI = -1, showT = 0, input = [], lit = -1, litT = 0, mode = 'show', t = 0, errors = 0, finished = false, msgT = 0, replay = false;
    let tiles = [];
    const ps = Particles();
    this.title = 'FIRMWARE · ROOT ACCESS';
    this.debug = { seq: () => seq, mode: () => mode, tiles: () => tiles };
    this.steps = ['1', '2', '3'];
    this.step = 0;
    const newRound = () => {
      seq = [];
      for (let i = 0; i < lengths[round]; i++) seq.push(Math.floor(Math.random() * nT));
      showI = -1; showT = 0.6; mode = 'show'; input = [];
      o.hint(`Watch the code. ${lengths[round]} cells.`);
    };
    newRound();
    this.layout = (w, h) => {
      tiles = [];
      const r = Math.min(w / 8.5, h / 4.6);
      const cx = w / 2, cy = h / 2 + 6;
      for (let i = 0; i < nT; i++) {
        const col = i % 3, row = Math.floor(i / 3);
        tiles.push({ x: cx + (col - 1) * r * 2.05, y: cy + (row - 0.5) * r * 1.95, r });
      }
    };
    this.down = (x, y) => {
      if (mode !== 'input' || finished) return;
      for (let i = 0; i < nT; i++) {
        if (Math.hypot(x - tiles[i].x, y - tiles[i].y) < tiles[i].r * 0.95) {
          lit = i; litT = 0.25;
          o.sound('note', i * 2 + 3); o.haptic('tap');
          if (seq[input.length] === i) {
            input.push(i);
            if (input.length === seq.length) {
              round++; this.step = round;
              ps.add(tiles[i].x, tiles[i].y, 20, { color: GR, gravity: 0 });
              if (round >= lengths.length) {
                finished = true; mode = 'done';
                o.sound('great'); o.haptic('ok');
                o.hint('ROOT ACCESS. Ransomware deleted. It left a note: "rude".');
                setTimeout(() => o.done({ quality: clamp(1 - errors * 0.18, 0.2, 1), errors }), 900);
              } else { mode = 'wait'; msgT = 0.8; replay = false; o.hint('Accepted. Next layer.'); }
            }
          } else {
            errors++; mode = 'wait'; msgT = 1.1; replay = true;
            o.sound('bad'); o.haptic('bad'); o.jolt(0.5);
            o.hint(['ACCESS DENIED. The ransomware laughs in binary.', 'Wrong. The dashboard shows a skull emoji.', 'Denied. The car plays a sad trombone at you.'][errors % 3]);
          }
          return;
        }
      }
    };
    this.move = () => {};
    this.up = () => {};
    this.update = (dt) => {
      t += dt; ps.step(dt);
      litT = Math.max(0, litT - dt); if (litT <= 0) lit = -1;
      if (mode === 'wait') {
        msgT -= dt;
        if (msgT <= 0) {
          // a mistake shows the same code again; a code done brings the next, longer one
          if (replay) { mode = 'show'; showI = -1; showT = 0.4; input = []; o.hint('Watch it again.'); } else newRound();
        }
      }
      if (mode === 'show') {
        showT -= dt;
        if (showT <= 0) {
          showI++;
          if (showI >= seq.length) { mode = 'input'; lit = -1; o.hint('Your turn. Same order.'); }
          else { lit = seq[showI]; litT = 0.42; showT = 0.62; o.sound('note', seq[showI] * 2 + 3); }
        }
      }
    };
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h, 'rgba(255,43,214,0.05)');
      // falling code down the back
      ctx.font = `600 12px ${NUMF}`; ctx.fillStyle = 'rgba(61,255,154,0.12)';
      for (let i = 0; i < 18; i++) { const x = (i / 18) * w + 8; const y = ((t * (40 + i * 7) + i * 97) % (h + 40)) - 20; ctx.fillText(i % 2 ? '01101' : 'ROOT', x, y); }
      tiles.forEach((tl, i) => {
        const on = lit === i;
        ctx.fillStyle = on ? HCOL[i] : 'rgba(10,16,28,0.85)';
        ctx.globalAlpha = on ? 0.35 : 1;
        ctx.beginPath(); hexPath(ctx, tl.x, tl.y, tl.r * 0.95, Math.PI / 6); ctx.fill();
        ctx.globalAlpha = 1;
        glow(ctx, HCOL[i], on ? 3 : 1.5, () => hexPath(ctx, tl.x, tl.y, tl.r * 0.95, Math.PI / 6), on ? 1.2 : 0.45);
        if (on) glowDot(ctx, tl.x, tl.y, tl.r * 1.5, HCOL[i], 0.6);
        label(ctx, GLYPHS[i], tl.x, tl.y + 2, tl.r * 0.7, on ? '#ffffff' : HCOL[i], 'center', TXTF);
      });
      // how far into this round's code you are
      const n = seq.length;
      for (let i = 0; i < n; i++) {
        ctx.fillStyle = i < input.length ? GR : 'rgba(200,250,255,0.2)';
        ctx.fillRect(w / 2 - n * 9 + i * 18, h - 18, 12, 5);
      }
      label(ctx, mode === 'show' ? 'WATCH' : mode === 'input' ? 'REPEAT' : '', w / 2, 16, 12, mode === 'show' ? AM : CY);
      ps.draw(ctx);
    };
  }

  // ======================================================================== PULL
  /*
   * Something has moved into the engine. Tap fast to drag it out; it pulls back. When it comes,
   * it comes all at once.
   */
  const CRITTER_EMOJI = { 'a raccoon': '🦝', 'a pigeon': '🐦', 'a cyber-rat': '🐀', 'a whole kebab': '🥙', 'a delivery drone': '🛸', 'a smaller car': '🚗', 'a cat': '🐈', '400 parking tickets': '🧾' };
  function Pull(variant, o) {
    const critter = o.critter || { name: 'a raccoon', line: 'It leaves.' };
    let prog = 0, t = 0, finished = false, wig = 0, out = 0, taps = 0;
    let cx = 0, cy = 0, s = 0;
    const ps = Particles();
    this.title = 'FOREIGN OBJECT REMOVAL';
    this.debug = { prog: () => prog };
    this.steps = ['Pull'];
    this.step = 0;
    o.hint(`There's ${critter.name} in the engine. Tap fast to pull it out.`);
    this.layout = (w, h) => { cx = w / 2; cy = h * 0.6; s = Math.min(w, h) * 0.32; };
    this.down = () => {
      if (finished) return;
      taps++;
      prog = Math.min(1, prog + 0.075);
      wig = 1;
      o.sound('tug'); o.haptic('tick');
      ps.add(cx + rnd(-s * 0.4, s * 0.4), cy, 4, { color: '#8a96a3', min: 80, max: 220 });
      if (prog >= 1) {
        finished = true; this.step = 1;
        o.sound('squeak'); setTimeout(() => o.sound('good'), 250); o.haptic('ok');
        o.hint(`Out it comes: ${critter.name}. ${critter.line}`);
        setTimeout(() => o.done({ quality: clamp(1.2 - t / 12, 0.4, 1) }), 1400);
      }
    };
    this.move = () => {};
    this.up = () => {};
    this.update = (dt) => {
      t += dt; ps.step(dt);
      // it pulls back
      if (!finished) prog = Math.max(0, prog - dt * (0.16 + Math.sin(t * 2) * 0.05));
      wig = Math.max(0, wig - dt * 6);
      if (finished) out += dt;
    };
    this.draw = (ctx, w, h) => {
      gridBg(ctx, w, h);
      // the engine bay: a dark opening with pipes
      ctx.fillStyle = '#05080f';
      ctx.beginPath(); ctx.ellipse(cx, cy + s * 0.15, s * 1.25, s * 0.45, 0, 0, TAU); ctx.fill();
      glow(ctx, '#5a6878', 2, () => ctx.ellipse(cx, cy + s * 0.15, s * 1.25, s * 0.45, 0, 0, TAU), 0.6);
      // the thing, coming up out of it
      const rise = finished ? prog * s * 0.9 + out * out * 900 : prog * s * 0.9;
      const shakeX = Math.sin(t * 40) * wig * 8 + Math.sin(t * 7) * 3;
      const yy = cy + s * 0.2 - rise;
      ctx.save();
      ctx.beginPath(); ctx.rect(0, 0, w, cy + s * 0.15); ctx.clip();
      // eyes in the dark, in case the phone draws no picture for it
      ctx.fillStyle = '#fff27a';
      ctx.beginPath(); ctx.arc(cx - 12 + shakeX, yy - s * 0.05, 5, 0, TAU); ctx.arc(cx + 12 + shakeX, yy - s * 0.05, 5, 0, TAU); ctx.fill();
      ctx.font = `${Math.round(s * 0.95)}px sans-serif`;
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.save(); ctx.translate(cx + shakeX, yy); ctx.rotate(Math.sin(t * 9) * 0.15 * (1 - prog) + (finished ? out * 6 : 0));
      ctx.fillText(CRITTER_EMOJI[critter.name] || '👾', 0, 0);
      ctx.restore();
      ctx.restore();
      // the rope of effort: how close you are
      ctx.fillStyle = 'rgba(41,240,255,0.12)'; ctx.fillRect(w * 0.15, h * 0.1, w * 0.7, 10);
      const g = ctx.createLinearGradient(w * 0.15, 0, w * 0.85, 0);
      g.addColorStop(0, CY); g.addColorStop(1, GR);
      ctx.fillStyle = g; ctx.fillRect(w * 0.15, h * 0.1, w * 0.7 * prog, 10);
      label(ctx, finished ? 'OUT!' : 'TAP TAP TAP', w / 2, h * 0.1 + 26, 14, finished ? GR : AM);
      ps.draw(ctx);
    };
  }

  const GAMES = { bolts: Bolts, balance: Balance, pour: Pour, wires: Wires, wave: Wave, weld: Weld, hack: Hack, pull: Pull };

  /*
   * Run one repair step in [canvas]. [spec] is "kind:variant" as the jobs list them. opts: shake
   * (0..1), day, ratchet (the upgrade), critter (for pull), and the game's hooks: hint(text),
   * sound(name), loop(kind), haptic(kind), shake(amount), done(result).
   */
  MG.start = function (spec, canvas, opts) {
    const [kind, variant] = spec.split(':');
    const G = GAMES[kind];
    if (!G) throw new Error('no minigame ' + kind);
    const ctx = canvas.getContext('2d');
    let w = 1, h = 1, dpr = 1, alive = true;
    const o = Object.assign({ shake: 0, day: 1, hint() {}, sound() {}, loop() { return { stop() {}, set() {} }; }, haptic() {}, done() {} }, opts);
    const userDone = o.done;
    // a jolt of the picture for zaps and slips, apart from the hand's shake (o.shake, 0..1)
    let jolt = 0;
    const hooks = Object.assign({}, o, {
      jolt: (a) => { jolt = Math.max(jolt, a); },
      done: (r) => { if (!alive) return; alive = false; if (game.stop) game.stop(); userDone(r); },
    });
    const game = new G(variant, hooks);
    const runner = {
      title: game.title,
      steps: () => game.steps,
      step: () => game.step,
      resize() {
        const r = canvas.getBoundingClientRect();
        dpr = Math.min(window.devicePixelRatio || 1, 2);
        w = Math.max(10, r.width); h = Math.max(10, r.height);
        canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
        game.layout(w, h);
      },
      // the game moved on without being drawn (a test fast-forwarding)
      tick(dt) { game.update(dt); },
      // the last frame stays up, still moving, while the result shows
      frame(dt) {
        game.update(dt);
        jolt = Math.max(0, jolt - dt * 2.5);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, w, h);
        if (jolt > 0) ctx.translate((Math.random() - 0.5) * jolt * 14, (Math.random() - 0.5) * jolt * 14);
        game.draw(ctx, w, h);
      },
      pointer(type, x, y) {
        if (type === 'down') game.down(x, y);
        else if (type === 'move') game.move(x, y);
        else game.up(x, y);
      },
      abort() { alive = false; if (game.stop) game.stop(); },
      debug: game.debug,
    };
    runner.resize();
    return runner;
  };
})();

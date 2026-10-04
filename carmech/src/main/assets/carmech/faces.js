/*
 * Car Mech — the customers' faces, drawn on the spot: a head, hair, whatever the future has bolted
 * onto them, and an expression that follows how they feel about you right now. A face is a handful
 * of choices made once per customer (CMFaces.make) and drawn as often as their mood changes.
 */
(function () {
  'use strict';
  const F = {};
  window.CMFaces = F;

  const SKIN = ['#f6d7c3', '#eac0a2', '#d9a27e', '#b97f5b', '#8e5a3c', '#6a3f2a', '#4a2b1d', '#f0c8b0'];
  const HAIR = ['#1b1414', '#3b2a1e', '#6b4a2b', '#a8743a', '#d8b46a', '#c43f2b', '#e8e2d8', '#2b2b2b', '#ff2bd6', '#29f0ff', '#7a5cff'];
  const EYES = ['#3b6fb0', '#4a7a3a', '#6b4a2b', '#2b2b2b', '#5a8a9a'];
  const BG = ['#29f0ff', '#ff2bd6', '#ffb020', '#3dff9a', '#8a7dff', '#ff3b5c'];

  const pick = (a, r) => a[Math.floor(r() * a.length) % a.length];
  // a little seeded random, so a customer looks the same every time they're drawn
  function rng(seed) {
    let s = seed >>> 0 || 1;
    return () => { s ^= s << 13; s >>>= 0; s ^= s >> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
  }

  F.make = function (seed, look) {
    const r = rng(seed * 2654435761);
    look = look || {};
    const hairStyles = look.hair || ['short', 'side', 'long', 'bun', 'curly', 'messy', 'bob', 'bald', 'mohawk', 'swoop'];
    const f = {
      robot: !!look.robot,
      skin: pick(SKIN, r),
      hair: pick(hairStyles, r),
      hairColor: look.hairColor || (r() < 0.12 ? pick(HAIR.slice(8), r) : pick(HAIR.slice(0, 8), r)),
      eyes: pick(EYES, r),
      top: look.top || pick(['#2f6fb0', '#9b2c3c', '#2a4a5a', '#5b3a5a', '#3d4a2a', '#c43fa8'], r),
      bg: pick(BG, r),
      glasses: r() < (look.glasses || 0.12),
      sunglasses: r() < (look.sunglasses || 0.06),
      visor: r() < (look.visor || 0.08),
      cyberEye: r() < (look.cyberEye || 0.1),
      chromeJaw: r() < (look.chromeJaw || 0.04),
      beard: r() < (look.beard || 0.15),
      cap: r() < (look.cap || 0.06),
      hood: r() < (look.hood || 0.04),
      headset: r() < (look.headset || 0.03),
      tie: !!look.tie,
      bags: !!look.bags || r() < 0.15,
      old: !!look.old,
      young: !!look.young,
      earring: r() < 0.2,
      scar: r() < 0.08,
      face: 0.92 + r() * 0.16,      // how long the face is
      jaw: 0.85 + r() * 0.25,       // how wide the jaw is
    };
    if (f.sunglasses) { f.glasses = false; f.visor = false; }
    if (f.visor) f.glasses = false;
    if (f.hair === 'bald' && r() < 0.4) f.cap = true;
    return f;
  };

  // mood 0..100: furious .. delighted
  F.draw = function (ctx, size, f, mood, t) {
    const s = size / 128;
    ctx.save();
    ctx.clearRect(0, 0, size, size);
    ctx.scale(s, s);
    const m = mood == null ? 60 : mood;
    const happy = Math.max(0, (m - 55) / 45);   // 0..1
    const angry = Math.max(0, (40 - m) / 40);   // 0..1
    t = t || 0;

    // backdrop: a dark glass tile with the customer's colour in it
    const bg = ctx.createRadialGradient(64, 50, 6, 64, 64, 92);
    bg.addColorStop(0, hexA(f.bg, 0.45));
    bg.addColorStop(0.55, hexA(f.bg, 0.12));
    bg.addColorStop(1, 'rgba(5,8,15,0.95)');
    ctx.fillStyle = '#070b14';
    ctx.fillRect(0, 0, 128, 128);
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, 128, 128);
    ctx.strokeStyle = hexA(f.bg, 0.12);
    ctx.lineWidth = 1;
    for (let y = 4; y < 128; y += 6) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(128, y); ctx.stroke(); }

    if (f.robot) { drawRobot(ctx, f, m, happy, angry, t); ctx.restore(); return; }

    const cx = 64, cy = 58;
    const hw = 25 * (f.young ? 0.95 : 1), hh = 30 * f.face * (f.young ? 0.92 : 1);
    // shoulders and clothes
    ctx.fillStyle = f.top;
    ctx.beginPath();
    ctx.moveTo(10, 128); ctx.quadraticCurveTo(14, 98, 40, 92); ctx.lineTo(88, 92); ctx.quadraticCurveTo(114, 98, 118, 128); ctx.closePath();
    ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,0.08)';
    ctx.beginPath(); ctx.moveTo(10, 128); ctx.quadraticCurveTo(14, 98, 40, 92); ctx.lineTo(46, 96); ctx.quadraticCurveTo(24, 104, 22, 128); ctx.fill();
    if (f.hood) {
      ctx.fillStyle = shade(f.top, -0.25);
      ctx.beginPath(); ctx.ellipse(cx, cy + 4, hw + 13, hh + 14, 0, Math.PI, 0); ctx.lineTo(cx + hw + 13, cy + 38); ctx.lineTo(cx - hw - 13, cy + 38); ctx.fill();
    }
    // long hair hangs behind the head
    if (f.hair === 'long' || f.hair === 'bob') {
      ctx.fillStyle = f.hairColor;
      ctx.beginPath();
      ctx.ellipse(cx, cy - 2, hw + 6, hh + 2, 0, Math.PI, 0);
      const drop = f.hair === 'long' ? 44 : 22;
      ctx.lineTo(cx + hw + 7, cy + drop); ctx.quadraticCurveTo(cx, cy + drop + 6, cx - hw - 7, cy + drop);
      ctx.fill();
    }
    // neck
    ctx.fillStyle = shade(f.skin, -0.18);
    ctx.fillRect(cx - 10, cy + hh - 8, 20, 22);
    // collar / tie
    if (f.tie) {
      ctx.fillStyle = '#e8e8ee';
      ctx.beginPath(); ctx.moveTo(cx - 14, 92); ctx.lineTo(cx, 104); ctx.lineTo(cx + 14, 92); ctx.lineTo(cx + 9, 90); ctx.lineTo(cx, 97); ctx.lineTo(cx - 9, 90); ctx.fill();
      ctx.fillStyle = '#c43f2b';
      ctx.beginPath(); ctx.moveTo(cx - 4, 99); ctx.lineTo(cx + 4, 99); ctx.lineTo(cx + 6, 124); ctx.lineTo(cx, 128); ctx.lineTo(cx - 6, 124); ctx.fill();
    }
    // ears
    ctx.fillStyle = shade(f.skin, -0.08);
    ctx.beginPath(); ctx.ellipse(cx - hw - 1, cy + 2, 5, 8, 0, 0, 7); ctx.fill();
    ctx.beginPath(); ctx.ellipse(cx + hw + 1, cy + 2, 5, 8, 0, 0, 7); ctx.fill();
    if (f.earring) { ctx.fillStyle = '#ffd25a'; ctx.beginPath(); ctx.arc(cx - hw - 2, cy + 10, 2, 0, 7); ctx.fill(); }
    // the head, lit from the left with the garage's cyan on the right edge
    const g = ctx.createLinearGradient(cx - hw, cy, cx + hw, cy);
    g.addColorStop(0, shade(f.skin, 0.08)); g.addColorStop(0.6, f.skin); g.addColorStop(1, shade(f.skin, -0.2));
    ctx.fillStyle = g;
    headPath(ctx, cx, cy, hw, hh, f.jaw);
    ctx.fill();
    // anger shows as a flush
    if (angry > 0.3) {
      ctx.fillStyle = `rgba(255,40,40,${(angry - 0.3) * 0.3})`;
      headPath(ctx, cx, cy, hw, hh, f.jaw); ctx.fill();
    }
    ctx.save();
    headPath(ctx, cx, cy, hw, hh, f.jaw); ctx.clip();
    ctx.strokeStyle = 'rgba(41,240,255,0.55)'; ctx.lineWidth = 3;
    ctx.beginPath(); ctx.ellipse(cx + 3, cy, hw, hh, 0, -1.1, 1.1); ctx.stroke();
    // the chrome jaw: the lower face replaced, years ago, by someone who meant well
    if (f.chromeJaw) {
      const cg = ctx.createLinearGradient(0, cy + 8, 0, cy + hh);
      cg.addColorStop(0, '#e8eef4'); cg.addColorStop(0.5, '#8a96a3'); cg.addColorStop(1, '#d0d8e0');
      ctx.fillStyle = cg;
      ctx.fillRect(cx - hw - 2, cy + 12, hw * 2 + 4, hh);
      ctx.strokeStyle = 'rgba(0,0,0,0.35)'; ctx.lineWidth = 1;
      for (let x = cx - 14; x <= cx + 14; x += 7) { ctx.beginPath(); ctx.moveTo(x, cy + 14); ctx.lineTo(x, cy + hh); ctx.stroke(); }
    }
    if (f.beard) {
      ctx.fillStyle = f.hairColor;
      ctx.globalAlpha = 0.92;
      ctx.beginPath();
      ctx.moveTo(cx - hw, cy + 2);
      ctx.quadraticCurveTo(cx - hw + 2, cy + hh + 6, cx, cy + hh + 6);
      ctx.quadraticCurveTo(cx + hw - 2, cy + hh + 6, cx + hw, cy + 2);
      ctx.lineTo(cx + hw - 6, cy + 10); ctx.quadraticCurveTo(cx, cy + 14, cx - hw + 6, cy + 10);
      ctx.fill();
      ctx.globalAlpha = 1;
    }
    if (f.old) {
      ctx.strokeStyle = 'rgba(80,40,30,0.3)'; ctx.lineWidth = 1;
      [[-14, -14], [-12, -18]].forEach(([dx, dy]) => { ctx.beginPath(); ctx.moveTo(cx + dx, cy + dy); ctx.quadraticCurveTo(cx, cy + dy - 3, cx - dx, cy + dy); ctx.stroke(); });
      ctx.beginPath(); ctx.moveTo(cx - 13, cy + 10); ctx.quadraticCurveTo(cx - 16, cy + 18, cx - 12, cy + 22); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(cx + 13, cy + 10); ctx.quadraticCurveTo(cx + 16, cy + 18, cx + 12, cy + 22); ctx.stroke();
    }
    if (f.scar) {
      ctx.strokeStyle = 'rgba(120,40,40,0.6)'; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(cx + 8, cy - 14); ctx.lineTo(cx + 15, cy + 4); ctx.stroke();
    }
    ctx.restore();

    // eyes
    const ey = cy - 2, ex = 10;
    const lid = f.bags ? 0.3 : 0.1;
    if (f.sunglasses) {
      ctx.fillStyle = '#0a0c12';
      roundRect(ctx, cx - 22, ey - 7, 19, 13, 4); ctx.fill();
      roundRect(ctx, cx + 3, ey - 7, 19, 13, 4); ctx.fill();
      ctx.fillRect(cx - 4, ey - 4, 8, 2);
      ctx.fillStyle = 'rgba(255,255,255,0.35)';
      ctx.fillRect(cx - 19, ey - 5, 6, 2); ctx.fillRect(cx + 6, ey - 5, 6, 2);
    } else if (f.visor) {
      const vg = ctx.createLinearGradient(cx - 28, 0, cx + 28, 0);
      vg.addColorStop(0, hexA(f.bg, 0.4)); vg.addColorStop(0.5, hexA(f.bg, 0.95)); vg.addColorStop(1, hexA(f.bg, 0.4));
      ctx.fillStyle = vg;
      roundRect(ctx, cx - 28, ey - 6, 56, 11, 5); ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.75)';
      const scan = ((t * 40) % 56);
      ctx.fillRect(cx - 28 + scan, ey - 5, 3, 9);
    } else {
      [-1, 1].forEach((side) => {
        const x = cx + side * ex;
        if (f.cyberEye && side === 1) {
          ctx.fillStyle = '#1a0a0e';
          ctx.beginPath(); ctx.arc(x, ey, 5.5, 0, 7); ctx.fill();
          ctx.fillStyle = '#ff2a4a'; ctx.shadowColor = '#ff2a4a'; ctx.shadowBlur = 8;
          ctx.beginPath(); ctx.arc(x, ey, 2.6, 0, 7); ctx.fill();
          ctx.shadowBlur = 0;
          ctx.strokeStyle = '#8a96a3'; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.arc(x, ey, 6.5, 0, 7); ctx.stroke();
          return;
        }
        ctx.fillStyle = '#f4f2ee';
        ctx.beginPath(); ctx.ellipse(x, ey, 5.5, 3.8 - angry * 0.8, 0, 0, 7); ctx.fill();
        ctx.fillStyle = f.eyes;
        ctx.beginPath(); ctx.arc(x + side * 0.4, ey + 0.3, 2.6, 0, 7); ctx.fill();
        ctx.fillStyle = '#111';
        ctx.beginPath(); ctx.arc(x + side * 0.4, ey + 0.3, 1.2, 0, 7); ctx.fill();
        ctx.fillStyle = 'rgba(255,255,255,0.8)';
        ctx.fillRect(x - 1.2, ey - 1.4, 1.2, 1.2);
        // heavy lids for the tired ones
        ctx.fillStyle = shade(f.skin, -0.1);
        ctx.beginPath(); ctx.ellipse(x, ey - 2.5, 6.2, 3.8 * (lid + angry * 0.25), 0, Math.PI, 0); ctx.fill();
        if (f.bags) {
          ctx.strokeStyle = 'rgba(70,40,60,0.45)'; ctx.lineWidth = 1.4;
          ctx.beginPath(); ctx.arc(x, ey + 3, 4.5, 0.3, Math.PI - 0.3); ctx.stroke();
        }
      });
      if (f.glasses) {
        ctx.strokeStyle = '#1a1d24'; ctx.lineWidth = 2;
        roundRect(ctx, cx - 20, ey - 6, 15, 11, 3); ctx.stroke();
        roundRect(ctx, cx + 5, ey - 6, 15, 11, 3); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(cx - 5, ey - 2); ctx.lineTo(cx + 5, ey - 2); ctx.stroke();
      }
    }
    // eyebrows: down at the middle when angry, up when delighted
    if (!f.visor) {
      ctx.strokeStyle = shade(f.hairColor === '#e8e2d8' ? '#9a948a' : f.hairColor, -0.1);
      ctx.lineWidth = 2.6; ctx.lineCap = 'round';
      [-1, 1].forEach((side) => {
        const x = cx + side * ex;
        const inner = ey - 9 + angry * 4 - happy * 1.5;
        const outer = ey - 10 - angry * 1 + happy * 0.5;
        ctx.beginPath(); ctx.moveTo(x - side * 5, inner); ctx.lineTo(x + side * 5, outer); ctx.stroke();
      });
    }
    // nose
    ctx.strokeStyle = shade(f.skin, -0.28); ctx.lineWidth = 1.6;
    ctx.beginPath(); ctx.moveTo(cx + 1, ey + 3); ctx.quadraticCurveTo(cx + 4, ey + 10, cx, ey + 12); ctx.stroke();
    // mouth
    const my = cy + 17 * f.face;
    ctx.lineCap = 'round';
    if (f.chromeJaw) {
      ctx.strokeStyle = '#2a2f38'; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(cx - 9, my); ctx.lineTo(cx + 9, my + (angry - happy) * 3); ctx.stroke();
    } else if (happy > 0.45) {
      ctx.fillStyle = '#5a1a20';
      ctx.beginPath(); ctx.moveTo(cx - 10, my - 1); ctx.quadraticCurveTo(cx, my + 9 + happy * 3, cx + 10, my - 1); ctx.closePath(); ctx.fill();
      ctx.fillStyle = '#f4f2ee';
      ctx.beginPath(); ctx.moveTo(cx - 8, my); ctx.quadraticCurveTo(cx, my + 3, cx + 8, my); ctx.lineTo(cx + 7, my - 0.5); ctx.lineTo(cx - 7, my - 0.5); ctx.fill();
    } else {
      ctx.strokeStyle = '#6a2a2a'; ctx.lineWidth = 2.2;
      const curve = happy * 6 - angry * 6;
      ctx.beginPath(); ctx.moveTo(cx - 9, my); ctx.quadraticCurveTo(cx, my + curve, cx + 9, my); ctx.stroke();
      if (angry > 0.7) { ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(cx - 6, my + 2); ctx.lineTo(cx + 6, my + 2); ctx.stroke(); }
    }

    // hair on top
    drawHair(ctx, f, cx, cy, hw, hh);
    if (f.cap) {
      ctx.fillStyle = shade(f.top, 0.15);
      ctx.beginPath(); ctx.ellipse(cx, cy - hh + 10, hw + 3, 16, 0, Math.PI, 0); ctx.fill();
      ctx.fillStyle = shade(f.top, -0.2);
      ctx.beginPath(); ctx.ellipse(cx + 12, cy - hh + 11, 22, 5, -0.05, 0, Math.PI); ctx.fill();
      ctx.fillStyle = f.bg; ctx.fillRect(cx - 4, cy - hh - 1, 8, 4);
    }
    if (f.headset) {
      ctx.strokeStyle = '#1a1d24'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.ellipse(cx, cy - 2, hw + 4, hh + 2, 0, Math.PI * 1.05, Math.PI * 1.95); ctx.stroke();
      ctx.fillStyle = '#1a1d24'; ctx.fillRect(cx - hw - 7, cy - 6, 6, 14);
      ctx.strokeStyle = '#1a1d24'; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(cx - hw - 4, cy + 6); ctx.quadraticCurveTo(cx - 20, cy + 22, cx - 8, my + 2); ctx.stroke();
      ctx.fillStyle = '#3dff9a'; ctx.beginPath(); ctx.arc(cx - 8, my + 2, 2, 0, 7); ctx.fill();
    }

    // a thin neon frame
    ctx.strokeStyle = hexA(f.bg, 0.7); ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, 126, 126);
    ctx.restore();
  };

  function headPath(ctx, cx, cy, hw, hh, jaw) {
    ctx.beginPath();
    ctx.moveTo(cx - hw, cy - 4);
    ctx.bezierCurveTo(cx - hw, cy - hh * 1.25, cx + hw, cy - hh * 1.25, cx + hw, cy - 4);
    ctx.bezierCurveTo(cx + hw, cy + hh * 0.55, cx + hw * 0.55 * jaw, cy + hh, cx, cy + hh);
    ctx.bezierCurveTo(cx - hw * 0.55 * jaw, cy + hh, cx - hw, cy + hh * 0.55, cx - hw, cy - 4);
    ctx.closePath();
  }

  function drawHair(ctx, f, cx, cy, hw, hh) {
    const c = f.hairColor;
    ctx.fillStyle = c;
    const top = cy - hh * 0.95;
    switch (f.hair) {
      case 'bald':
        ctx.fillStyle = 'rgba(255,255,255,0.25)';
        ctx.beginPath(); ctx.ellipse(cx - 8, top + 8, 7, 3, -0.3, 0, 7); ctx.fill();
        break;
      case 'mohawk':
        ctx.fillStyle = f.hairColor === '#1b1414' ? '#ff2bd6' : c;
        ctx.beginPath(); ctx.moveTo(cx - 5, top + 14);
        for (let i = 0; i <= 6; i++) ctx.lineTo(cx - 6 + i * 2, top - 10 + (i % 2) * 6);
        ctx.lineTo(cx + 6, top + 14); ctx.fill();
        break;
      case 'bun':
        ctx.beginPath(); ctx.ellipse(cx, top + 9, hw + 2, 14, 0, Math.PI, 0); ctx.fill();
        ctx.beginPath(); ctx.arc(cx, top - 6, 9, 0, 7); ctx.fill();
        break;
      case 'curly':
        for (let i = 0; i < 9; i++) {
          const a = Math.PI + (i / 8) * Math.PI;
          ctx.beginPath(); ctx.arc(cx + Math.cos(a) * (hw - 2), top + 12 + Math.sin(a) * 12, 8, 0, 7); ctx.fill();
        }
        break;
      case 'messy':
        ctx.beginPath(); ctx.moveTo(cx - hw - 2, top + 16);
        for (let i = 0; i <= 10; i++) ctx.lineTo(cx - hw + i * (hw * 0.2), top + (i % 2 ? -4 : 6));
        ctx.lineTo(cx + hw + 2, top + 16); ctx.quadraticCurveTo(cx, top + 6, cx - hw - 2, top + 16); ctx.fill();
        break;
      case 'swoop':
        ctx.beginPath(); ctx.moveTo(cx - hw - 1, top + 16);
        ctx.quadraticCurveTo(cx - hw, top - 6, cx + 4, top - 8);
        ctx.quadraticCurveTo(cx + hw + 12, top - 6, cx + hw + 2, top + 12);
        ctx.quadraticCurveTo(cx, top + 2, cx - hw - 1, top + 16); ctx.fill();
        break;
      case 'side':
        ctx.beginPath(); ctx.ellipse(cx, top + 10, hw + 2, 13, 0, Math.PI, 0); ctx.fill();
        ctx.beginPath(); ctx.moveTo(cx - 6, top - 2); ctx.quadraticCurveTo(cx + hw, top, cx + hw + 2, top + 16); ctx.lineTo(cx + hw - 4, top + 12); ctx.quadraticCurveTo(cx + 6, top + 6, cx - 6, top + 6); ctx.fill();
        break;
      case 'long':
      case 'bob':
        ctx.beginPath(); ctx.ellipse(cx, top + 10, hw + 4, 15, 0, Math.PI, 0); ctx.fill();
        ctx.beginPath(); ctx.moveTo(cx - hw - 4, top + 10); ctx.quadraticCurveTo(cx - 6, top + 22, cx + 2, top + 8); ctx.fill();
        break;
      default: // short
        ctx.beginPath(); ctx.ellipse(cx, top + 10, hw + 1.5, 12, 0, Math.PI, 0); ctx.fill();
    }
    // a lit edge on the hair too
    if (f.hair !== 'bald') {
      ctx.strokeStyle = 'rgba(41,240,255,0.35)'; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.ellipse(cx, top + 10, hw + 2, 13, 0, -1.2, -0.2); ctx.stroke();
    }
  }

  // a car with no driver: a screen for a face
  function drawRobot(ctx, f, m, happy, angry, t) {
    const cx = 64, cy = 58;
    const body = ctx.createLinearGradient(0, 80, 0, 128);
    body.addColorStop(0, '#3a4655'); body.addColorStop(1, '#1a2028');
    ctx.fillStyle = body;
    roundRect(ctx, 22, 92, 84, 50, 10); ctx.fill();
    ctx.fillStyle = '#2a323c';
    ctx.fillRect(cx - 8, 80, 16, 14);
    const hg = ctx.createLinearGradient(0, 22, 0, 92);
    hg.addColorStop(0, '#c8d0da'); hg.addColorStop(1, '#6a7682');
    ctx.fillStyle = hg;
    roundRect(ctx, 28, 22, 72, 62, 14); ctx.fill();
    ctx.fillStyle = '#05080f';
    roundRect(ctx, 34, 30, 60, 46, 10); ctx.fill();
    const col = angry > 0.3 ? '#ff2a4a' : happy > 0.3 ? '#3dff9a' : '#29f0ff';
    ctx.fillStyle = col; ctx.shadowColor = col; ctx.shadowBlur = 10;
    [-1, 1].forEach((side) => {
      const x = cx + side * 13;
      if (happy > 0.3) { ctx.lineWidth = 3; ctx.strokeStyle = col; ctx.beginPath(); ctx.arc(x, 48, 6, Math.PI * 1.1, Math.PI * 1.9); ctx.stroke(); }
      else if (angry > 0.3) { ctx.beginPath(); ctx.moveTo(x - 7, 42 + (side > 0 ? 0 : 0)); ctx.lineTo(x + 7, 46 - side * 3); ctx.lineTo(x + 7, 50); ctx.lineTo(x - 7, 50); ctx.fill(); }
      else { roundRect(ctx, x - 6, 42, 12, 8, 2); ctx.fill(); }
    });
    // the mouth is a level meter
    for (let i = 0; i < 7; i++) {
      const h = 2 + Math.abs(Math.sin(t * 6 + i * 1.3)) * 6;
      ctx.fillRect(cx - 17 + i * 5, 64 - h / 2, 3, h);
    }
    ctx.shadowBlur = 0;
    ctx.fillStyle = '#ffb020';
    ctx.beginPath(); ctx.arc(cx, 18, 3, 0, 7); ctx.fill();
    ctx.strokeStyle = '#8a96a3'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(cx, 21); ctx.lineTo(cx, 25); ctx.stroke();
    ctx.strokeStyle = hexA(f.bg, 0.7); ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, 126, 126);
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
  }
  function hexA(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  function shade(hex, k) {
    const n = parseInt(hex.slice(1), 16);
    let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    if (k > 0) { r += (255 - r) * k; g += (255 - g) * k; b += (255 - b) * k; } else { r *= 1 + k; g *= 1 + k; b *= 1 + k; }
    return `rgb(${r | 0},${g | 0},${b | 0})`;
  }
  F.hexA = hexA;
  F.roundRect = roundRect;
})();

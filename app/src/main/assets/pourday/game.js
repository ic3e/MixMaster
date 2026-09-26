/*
 * Pour Day — one slab, one day, in first person.
 *
 * The day runs on a game clock. What you do moves the clock at a pace that suits it (a walk to
 * the van is a minute or two, waiting for concrete can be hours), and the slab hardens by the
 * weather: warm, dry, windy and thick go faster; cold, damp, thin and a soupy mix go slower.
 */
(function () {
  'use strict';

  // ------------------------------------------------------------------ helpers
  const $ = (s) => document.querySelector(s);
  const rnd = (a, b) => a + Math.random() * (b - a);
  const irnd = (a, b) => Math.floor(rnd(a, b + 1));
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  const chance = (p) => Math.random() < p;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const hyp = (ax, az, bx, bz) => Math.hypot(ax - bx, az - bz);
  const DEBUG = /[?&]debug\b/.test(location.search);
  const CALM = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

  function clock(m) {
    const mm = Math.floor(m);
    const h = Math.floor((((mm % 1440) + 1440) % 1440) / 60);
    const mi = ((mm % 60) + 60) % 60;
    return String(h).padStart(2, '0') + ':' + String(mi).padStart(2, '0');
  }
  function clockDay(m) { return clock(m) + (m >= 1440 ? ' (next day)' : ''); }
  function dur(min) {
    min = Math.max(0, Math.round(min));
    const h = Math.floor(min / 60), m = min % 60;
    return h ? (m ? `${h} h ${m} min` : `${h} h`) : `${m} min`;
  }
  function weighted(list) {
    const total = list.reduce((s, x) => s + x[0], 0);
    let r = Math.random() * total;
    for (const x of list) { r -= x[0]; if (r <= 0) return x[1]; }
    return list[list.length - 1][1];
  }
  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch (e) { return null; }
    return null;
  }

  // ------------------------------------------------------------------ the site
  const NX = 12, NZ = 8;            // the slab: 12 x 8 m, in 1 m cells
  const SLAB = { x0: -6, x1: 6, z0: -4, z1: 4 };
  const VIS = 2;                    // concrete drawn twice its real depth, so 5 mm is something you can see
  const EYE = 1.7;
  const REACH = 5.5;
  const P = (x, z) => ({ x, z });
  const POS = {
    van: P(-17, 9), vanDoor: P(-14.4, 8.0),
    ibc: P(-12, -8), ibcFront: P(-12, -6.6),
    kiosk: P(-31, -23), kioskFront: P(-28.4, -21.2),
    tripod: P(-8.6, 0.6),
    cornerNW: P(-6.8, -4.8), cornerSE: P(6.8, 4.8),
    chairs: P(0, 0),
    pump: P(21.5, -3), pumpOut: P(17.9, -2.6),
    mixer: P(23, 4.5),
    pile: P(16.2, 1.6),
    loo: P(-23, -14),
    office: P(-26, 12),
  };
  const PIPE_ROUTE = [P(16.3, -2.3), P(14.1, -2.0), P(11.9, -1.6), P(9.7, -1.1), P(7.7, -0.6), P(6.4, -0.2)];
  const PENETRATIONS = [{ i: 3, j: 5 }, { i: 8, j: 2 }];
  const EDGE_SPOTS = [
    { x: -6.6, z: -4.6, label: 'Corner' }, { x: 6.6, z: -4.6, label: 'Corner' },
    { x: -6.6, z: 4.6, label: 'Corner' }, { x: 6.6, z: 4.6, label: 'Corner' },
    { x: 0, z: -4.7, label: 'Edge' }, { x: 0, z: 4.7, label: 'Edge' },
    { x: -6.7, z: 0, label: 'Edge' }, { x: 6.7, z: 0, label: 'Edge' },
    { x: -2.5, z: 1.5, label: 'Pipe collar', pipe: 0 }, { x: 2.5, z: -1.5, label: 'Pipe collar', pipe: 1 },
  ];
  const TRUCK_M3 = 8;

  // ------------------------------------------------------------------ what people say
  const L = {
    alarm: [
      '04:45. The alarm. Your back already knows what day it is.',
      '04:45. The alarm goes off. So does your knee, in sympathy.',
      '04:45. It is dark, it is cold, and somewhere a concrete plant is warming up just for you.',
    ],
    drive: [
      ['Clear roads. Suspicious.', 0],
      ['Stuck behind a tractor doing 23 km/h. The farmer waves. You do not.', 12],
      ['The petrol station coffee machine is "being cleaned". For the third week.', 6],
      ['Roadworks. Three men watching one man dig. You feel at home.', 9],
    ],
    arrive: [
      'First on site. The birds look at you like you owe them money.',
      'Nobody here. Just you, the slab base and a cat that belongs to no one.',
      'The site is quiet. Enjoy it. It will not last.',
    ],
    pumpArrive: [
      '"Morning. Where do you want the pipes? Don\'t say \'in the van\'."',
      '"I\'ve been driving since four. If this pour blocks, I\'m blaming you personally."',
      '"Nice base. Shame about what\'s going to happen to it."',
    ],
    pumpLate: [
      'Pump driver: "Running a bit late, the last site had a dog." ETA {eta}.',
      'Pump driver: "Coming! Just finishing my second breakfast." ETA {eta}.',
      'Pump driver: "The satnav took me to a lake. I\'m on my way." ETA {eta}.',
    ],
    truckLate: [
      'Plant: "The truck left ten minutes ago." It did not. ETA {eta}.',
      'Plant: "The driver is on his way, he just had to finish his sausage." ETA {eta}.',
      'Plant: "Traffic." Plant always says traffic. ETA {eta}.',
    ],
    truckDriver: [
      '"Plant says S3. The plant also says it loves you."',
      '"Where do you want it? Don\'t say \'on the slab\', everyone says that."',
      '"Quick one today? I have a funeral at two. Mine, if I\'m late."',
    ],
    pipe: [
      'Clunk. The coupling bites your finger. You learn a new word.',
      'Pipe in. It weighs exactly as much as you remember, plus two kilos.',
      'You clamp it. The clamp clamps you back.',
      'Pipe down. Your back files a complaint with HR.',
    ],
    pourJokes: [
      'Pump driver, from his remote: "Faster! I get paid by the hour, but I don\'t like it."',
      'The mixer driver starts his crossword. Seven letters, "grey and heavy". He writes "MONDAYS".',
      'Somebody on the pavement films you. Wave. Now you\'re content.',
      'Your phone buzzes. It\'s the foreman asking if it\'s done yet. It is 08:15.',
      'A bird lands on the formwork, looks at the concrete, and decides against it. Smart bird.',
    ],
    falls: [
      'You sit down in it. The slab now has a perfect print of your behind.',
      'Your boot stays. You don\'t. Graceful, in a way.',
      'You slip, flail, and land on your butt. The pump driver claps.',
      'Down you go. Somewhere, a health and safety officer feels a chill.',
    ],
    stuck: [
      'Your boot is stuck. You stand there like a garden gnome until it lets go.',
      'The concrete has your left boot. It is negotiating.',
    ],
    blocked: [
      'BANG. The line is blocked. The pump driver looks at you. You look at the pipe. Hit it.',
      'The pump groans and stops. Blocked. Somewhere a pipe needs a hammer.',
    ],
    unblocked: [
      'Clang clang clang. It coughs, spits and runs again. So does your nose.',
      'You hit the pipe. It forgives you. The pump driver does not.',
    ],
    blowout: 'The formwork on the {side} opens up. Concrete is leaving the building. Fix it!',
    battery: 'The laser beeps once and dies. The spare batteries are in the van. Of course they are.',
    wash: [
      'The pump driver hoses down his pipes, the pump, your van and, briefly, you.',
      'The pump driver washes out his pipes. The run-off heads straight for the neighbour\'s rose bed.',
    ],
    thumb: [
      [15, 'Your thumb goes in to the second knuckle. It is basically porridge.'],
      [25, 'Thumb leaves a deep print. It gives like a good mattress.'],
      [45, 'A clear print. You could start the pans soon.'],
      [65, 'A faint print. Blades are coming.'],
      [90, 'Barely a mark. It is getting there.'],
      [101, 'Nothing. It is hard. Like the foreman\'s heart.'],
    ],
    coffee: [
      'Thermos coffee. Tastes like 6 a.m. and diesel. Perfect.',
      'You pour a cup. The steam is the only warm thing on this site.',
      'Coffee. Your heart rate rejoins the conversation.',
    ],
    noCoffee: 'The thermos is empty. So is your soul. The kebab stand sells coffee.',
    cross: [
      { who: 'Neighbour in slippers', ask: '"Can I just quickly cross? My car\'s on the other side. I\'ll step lightly."', back: '"Charming. I\'ll tell your mother."' },
      { who: 'The electrician', ask: '"Mate, I just need to get to that socket. Two steps. Tiny steps."', back: '"Fine! I\'ll go round. Like an animal."' },
      { who: 'The client', ask: '"It\'s technically my concrete. I\'ll just have a look from the middle."', back: '"I\'m paying for this, you know." You know.' },
      { who: 'Delivery driver', ask: '"Parcel for... someone? Is this the shortcut?"', back: '"Wow. Okay. Five stars anyway."' },
      { who: 'Site manager in white trainers', ask: '"Just checking progress! Don\'t mind me."', back: '"I\'ll write that down." He does not have a pen.' },
      { who: 'A jogger', ask: '(already jogging towards the slab) "Sorry! I\'m on a streak!"', back: '"Rude!" (jogs off, streak intact)' },
      { who: 'Man with a clipboard', ask: '"Council inspection. I need to measure something in the middle."', back: '"I\'ll measure it from here then." He squints heroically.' },
    ],
    crossAnyway: [
      'They walk across anyway. Confident stride, size 45. Perfect prints.',
      'They nod, agree with everything you said, and step straight on it.',
      '"I\'m light!" They are not light.',
    ],
    crossAway: [
      'While you were away someone crossed the slab. Size 45, confident stride. Probably the electrician.',
      'You find footprints across the slab. They stop in the middle, turn around, and go back. Why.',
      'Someone crossed while you were gone. There\'s a coffee cup lid in the middle as a signature.',
    ],
    dog: {
      who: 'A dog',
      ask: 'A dog trots up to the edge of the slab. It looks at you. It looks at the slab. It has made its decision.',
      away: [
        'Paw prints. Lots of them. In circles. The dog had a lovely time.',
        'While you were gone a dog did three laps of the slab. You can tell it was happy.',
      ],
    },
    bird: 'A seagull landed on the slab, walked three steps, and left you a little something extra.',
    foreman: [
      '"Is it hard yet? The client wants to drive a forklift on it at two."',
      '"Quick one — can we do the second floor tomorrow? There is no second floor. Doesn\'t matter."',
      '"Just checking you\'re not on your phone." You are, because he called.',
    ],
    lunch: 'KEBAB & COFFEE. The owner nods at you like he knows exactly how your day is going.',
    vanNap: [
      'You wake up with the seatbelt printed on your face.',
      'You dreamt about troweling. Very relaxing. Then you woke up and had to do it.',
      'You nap. The radio plays the same four songs. Your dream now has a chorus.',
    ],
    tooSoftMachine: 'It\'s soup. The machine would sink to the gearbox. Give it time.',
    tooSoftBlades: 'Blades on this? It would tear the paste off. Pans first, and patience.',
    sleepy: 'You fell asleep leaning on the rake. {m} minutes gone. The rake is fine.',
    homeEarly: 'The foreman: "95% or you sleep here." There\'s a sleeping bag in the van for a reason.',
  };

  // ------------------------------------------------------------------ one day
  let day;
  function newDay() {
    const season = pick(['winter', 'spring', 'summer', 'summer', 'autumn', 'autumn']);
    const baseTemp = { winter: rnd(-3, 4), spring: rnd(6, 14), summer: rnd(16, 27), autumn: rnd(3, 11) }[season];
    return {
      season,
      baseTemp: Math.round(baseTemp * 10) / 10,
      rh: Math.round(rnd(35, 95)),
      wind: Math.round(rnd(0, 11)),
      thick: pick([80, 100, 120, 150, 150, 180, 200, 250]),
      pumpDelay: pick([0, 0, 0, 15, 25, 45, 90]),
      truckDelays: [pick([0, 0, 10, 20, 40, 60]), pick([5, 15, 25, 45, 70]), pick([10, 20, 35, 60, 90]), pick([15, 40, 80]), pick([20, 50, 90])],
      mix: pick(['stiff', 'ok', 'ok', 'soup']),
      formworkWeak: chance(0.55),
      batteryDies: chance(0.35),
      dogChance: rnd(0.18, 0.32),
    };
  }
  function tempAt(t) { return day.baseTemp + 4 * Math.sin(2 * Math.PI * (t - 540) / 1440); }
  function volumeNeeded() { return (NX * NZ * day.thick) / 1000; }

  /*
   * How fast the slab gains hardness, in % per game minute. Calibrated so a 150 mm slab at 20 °C,
   * 55 % humidity and no wind is ready for pans about three hours after the pour and 95 % hard
   * after about nine. Thicker slabs keep their heat and go faster; a soupy mix has water to lose
   * first. Capped so even a frozen day ends — late.
   */
  function cureRate(t) {
    const fT = clamp(Math.pow(1.7, (tempAt(t) - 20) / 10), 0.2, 2.2);
    const fRH = (1.25 - 0.006 * day.rh) / 0.92;
    const fW = 1 + day.wind / 40;
    const fTh = 0.6 + day.thick / 375;
    return Math.max(0.075, 0.181 * fT * fRH * fW * fTh * gs.mixFactor);
  }
  function stageMul(H) { return H < 15 ? 0.6 : H < 85 ? 1.2 : 0.7; }
  function etaTo(target) {
    if (gs.H >= target) return 0;
    let H = gs.H, t = gs.t, m = 0;
    while (H < target && m < 4000) { H += cureRate(t) * stageMul(H) * 5; t += 5; m += 5; }
    return m;
  }

  // ------------------------------------------------------------------ state
  let gs;
  function freshState() {
    const cells = [];
    for (let j = 0; j < NZ; j++) for (let i = 0; i < NX; i++) {
      cells.push({ i, j, idx: j * NX + i, fill: 0, pan: 0, blade: 0, covP: false, covB: false, marks: [], defect: false });
    }
    return {
      t: 4 * 60 + 45, phase: 'title',
      arrived: 0,
      energy: 76, cups: 3, sausage: false,
      tool: 'hands', carrying: false, toolsUnloaded: false,
      prep: { unload: false, formNW: false, formSE: false, laser: false, chairs: false },
      laserOn: false, laserBattery: true,
      pumpAt: 0, pumpHere: false, pipes: 0, pipesGone: 0,
      trucks: [], truck: null, truckNo: 0, truckWaitPaid: 0,
      mixFactor: 1, water: 0, mixState: 'ok',
      blocked: -1, blowout: null, blowoutDone: false, batteryDone: false, fellInPour: false, stuckUntil: 0,
      pourStarted: false, pourDone: false, pourEnd: 0, pouredM3: 0, waste: 0, extraTrucks: 0,
      washed: false,
      H: 0, poured: false,
      panPasses: [], bladePasses: [],
      edgesDone: 0, edgeNotes: [],
      waitMode: null, fastForward: null,
      schedule: [], nextNuisance: Infinity, rained: false, lastSleep: 0,
      milestones: {},
      stats: { hell: 0, crossed: 0, dogs: 0, falls: 0, prints: 0, repaired: 0, blockages: 0, coffee: 0, own: 0 },
      story: [],
      cells,
    };
  }
  function at(minute, fn) {
    gs.schedule.push({ at: minute, fn });
    gs.schedule.sort((a, b) => a.at - b.at);
  }
  function remember(text) {
    if (gs.story.includes(text)) return;
    if (gs.story.length < 40) gs.story.push(text);
  }

  // ------------------------------------------------------------------ renderer and scene
  const canvas = $('#gl');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.outputEncoding = THREE.sRGBEncoding;
  const scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x9ec3e0, 40, 160);
  const camera = new THREE.PerspectiveCamera(72, 1, 0.05, 400);
  camera.rotation.order = 'YXZ';
  scene.add(camera);
  const hemi = new THREE.HemisphereLight(0xdde6f5, 0x584a3b, 0.85);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xfff0d8, 0.9);
  scene.add(sun);
  const flood = new THREE.PointLight(0xffe2b8, 0, 60, 1.2);
  flood.position.set(-11, 8, 4);
  scene.add(flood);

  function resize() {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.fov = w < h ? 78 : 68;
    camera.updateProjectionMatrix();
  }
  window.addEventListener('resize', resize);
  resize();

  const MATS = {};
  function lam(c) { return MATS[c] || (MATS[c] = new THREE.MeshLambertMaterial({ color: c })); }
  function mesh(geo, color, x, y, z, parent) {
    const m = new THREE.Mesh(geo, typeof color === 'number' ? lam(color) : color);
    m.position.set(x || 0, y || 0, z || 0);
    (parent || scene).add(m);
    return m;
  }
  function box(w, h, d, color, x, y, z, parent) { return mesh(new THREE.BoxGeometry(w, h, d), color, x, y, z, parent); }
  function cyl(rt, rb, h, color, x, y, z, parent, seg) { return mesh(new THREE.CylinderGeometry(rt, rb, h, seg || 14), color, x, y, z, parent); }

  function noiseTex(rgb, spread, repeat) {
    const size = 128, c = document.createElement('canvas');
    c.width = c.height = size;
    const g = c.getContext('2d'), img = g.createImageData(size, size);
    for (let i = 0; i < size * size; i++) {
      const n = (Math.random() - 0.5) * spread;
      img.data[i * 4] = clamp(rgb[0] + n, 0, 255);
      img.data[i * 4 + 1] = clamp(rgb[1] + n, 0, 255);
      img.data[i * 4 + 2] = clamp(rgb[2] + n * 0.9, 0, 255);
      img.data[i * 4 + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(repeat, repeat);
    t.encoding = THREE.sRGBEncoding;
    return t;
  }

  function textSprite(text, opts) {
    opts = opts || {};
    const c = document.createElement('canvas');
    c.width = 512; c.height = 128;
    const g = c.getContext('2d');
    g.font = `800 ${opts.size || 54}px Manrope, Roboto, sans-serif`;
    const w = Math.min(500, g.measureText(text).width + 44);
    g.fillStyle = opts.bg || 'rgba(16,20,26,0.82)';
    const x = (512 - w) / 2;
    g.beginPath();
    if (g.roundRect) g.roundRect(x, 18, w, 92, 22); else g.rect(x, 18, w, 92);
    g.fill();
    g.fillStyle = opts.color || '#ff6b1a';
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.fillText(text, 256, 66);
    const tex = new THREE.CanvasTexture(c);
    tex.encoding = THREE.sRGBEncoding;
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthTest: false, transparent: true }));
    s.scale.set(opts.w || 2.6, (opts.w || 2.6) / 4, 1);
    s.renderOrder = 10;
    return s;
  }

  // ground, base, formwork
  const groundTex = noiseTex([112, 100, 84], 38, 70);
  const ground = mesh(new THREE.PlaneGeometry(260, 260), new THREE.MeshLambertMaterial({ map: groundTex }), 0, 0, 0);
  ground.rotation.x = -Math.PI / 2;

  const road = box(260, 0.02, 9, 0x3b3d40, 0, 0.01, 44);
  box(9, 0.02, 60, 0x3b3d40, 50, 0.01, 14);
  void road;

  const baseTex = noiseTex([140, 134, 122], 60, 6);
  const base = mesh(new THREE.PlaneGeometry(12, 8), new THREE.MeshLambertMaterial({ map: baseTex }), 0, 0.012, 0);
  base.rotation.x = -Math.PI / 2;

  const rebar = (function () {
    const pts = [];
    for (let x = -6; x <= 6.001; x += 0.4) { pts.push(x, 0.07, -4, x, 0.07, 4); }
    for (let z = -4; z <= 4.001; z += 0.4) { pts.push(-6, 0.09, z, 6, 0.09, z); }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3));
    const l = new THREE.LineSegments(geo, new THREE.LineBasicMaterial({ color: 0x8e4b2c }));
    scene.add(l);
    return l;
  })();

  const ply = 0xc9a26b;
  box(12.3, 0.4, 0.06, ply, 0, 0.2, -4.03);
  box(12.3, 0.4, 0.06, ply, 0, 0.2, 4.03);
  box(0.06, 0.4, 8.1, ply, -6.03, 0.2, 0);
  box(0.06, 0.4, 8.1, ply, 6.03, 0.2, 0);
  for (let x = -6; x <= 6; x += 1.5) { box(0.05, 0.6, 0.05, 0x9b7a4a, x, 0.3, -4.12); box(0.05, 0.6, 0.05, 0x9b7a4a, x, 0.3, 4.12); }
  for (let z = -4; z <= 4; z += 1.6) { box(0.05, 0.6, 0.05, 0x9b7a4a, -6.12, 0.3, z); box(0.05, 0.6, 0.05, 0x9b7a4a, 6.12, 0.3, z); }

  const penetrationMeshes = PENETRATIONS.map((p) => cyl(0.08, 0.08, 0.8, 0xc4502a, -6 + p.i + 0.5, 0.4, -4 + p.j + 0.5));

  // the van
  const van = new THREE.Group();
  box(5.2, 2.1, 2.1, 0xe9e7e2, 0, 1.35, 0, van);
  box(1.6, 1.6, 2.05, 0xe9e7e2, 3.2, 1.1, 0, van);
  box(0.05, 0.7, 1.8, 0x22303a, 4.02, 1.5, 0, van);
  box(5.2, 0.25, 2.12, 0xff6b1a, 0, 1.0, 0, van);
  [[-1.8, 1.05], [-1.8, -1.05], [2.9, 1.05], [2.9, -1.05]].forEach(([x, z]) => {
    const w = cyl(0.42, 0.42, 0.3, 0x1c1d20, x, 0.42, z, van, 16);
    w.rotation.x = Math.PI / 2;
  });
  const vanLabel = textSprite('THE VAN', { w: 2.2 });
  vanLabel.position.set(0, 3.1, 0);
  van.add(vanLabel);
  van.position.set(POS.van.x, 0, POS.van.z);
  van.rotation.y = -0.25;
  scene.add(van);

  // water tank for washing
  const ibc = new THREE.Group();
  box(1.2, 1.1, 1.2, new THREE.MeshLambertMaterial({ color: 0xdfe6ea, transparent: true, opacity: 0.85 }), 0, 0.75, 0, ibc);
  box(1.3, 0.15, 1.3, 0x6c7176, 0, 0.12, 0, ibc);
  cyl(0.05, 0.05, 0.25, 0x333333, 0, 0.35, 0.7, ibc).rotation.x = Math.PI / 2;
  ibc.position.set(POS.ibc.x, 0, POS.ibc.z);
  scene.add(ibc);

  // kebab stand
  const kiosk = new THREE.Group();
  box(3.2, 2.6, 2.6, 0x2f6f6a, 0, 1.3, 0, kiosk);
  box(3.6, 0.12, 1.3, 0xd83a2e, 0, 2.5, 1.8, kiosk);
  box(3.2, 0.9, 0.05, 0x1b2224, 0, 1.6, 1.31, kiosk);
  const kLabel = textSprite('KEBAB & COFFEE', { w: 3.2, color: '#ffd23f' });
  kLabel.position.set(0, 3.3, 0);
  kiosk.add(kLabel);
  kiosk.position.set(POS.kiosk.x, 0, POS.kiosk.z);
  kiosk.rotation.y = 0.5;
  scene.add(kiosk);

  // site office container and the portable toilet, for the atmosphere
  const office = new THREE.Group();
  box(6, 2.6, 2.4, 0x3c6e9e, 0, 1.3, 0, office);
  box(1.2, 0.8, 0.05, 0x1e2a33, 1.2, 1.6, 1.21, office);
  office.position.set(POS.office.x, 0, POS.office.z);
  office.rotation.y = 0.1;
  scene.add(office);
  const loo = new THREE.Group();
  box(1.1, 2.2, 1.1, 0x2c6ad6, 0, 1.1, 0, loo);
  box(1.2, 0.08, 1.2, 0x1d4a99, 0, 2.24, 0, loo);
  loo.position.set(POS.loo.x, 0, POS.loo.z);
  scene.add(loo);

  // fence, with a gap for the gate on the east
  (function fence() {
    const posts = [];
    for (let x = -44; x <= 44; x += 3) { posts.push([x, -34], [x, 34]); }
    for (let z = -34; z <= 34; z += 3) { if (Math.abs(z) > 6) posts.push([44, z]); posts.push([-44, z]); }
    const geo = new THREE.BoxGeometry(0.08, 2, 0.08);
    const im = new THREE.InstancedMesh(geo, lam(0x8a9096), posts.length);
    const m4 = new THREE.Matrix4();
    posts.forEach(([x, z], k) => { m4.makeTranslation(x, 1, z); im.setMatrixAt(k, m4); });
    scene.add(im);
    const mesh2 = new THREE.MeshBasicMaterial({ color: 0x9aa1a7, transparent: true, opacity: 0.18, side: THREE.DoubleSide });
    box(88, 1.8, 0.02, mesh2, 0, 1.0, -34); box(88, 1.8, 0.02, mesh2, 0, 1.0, 34);
    box(0.02, 1.8, 68, mesh2, -44, 1.0, 0);
    box(0.02, 1.8, 28, mesh2, 44, 1.0, -20); box(0.02, 1.8, 28, mesh2, 44, 1.0, 20);
  })();

  // a work light on a pole by the van, for the dark end of the day
  cyl(0.07, 0.09, 8, 0x5a5f64, -11, 4, 4);
  const lamp = box(0.8, 0.35, 0.5, 0xfff3d6, -11, 8, 4);
  lamp.material = new THREE.MeshBasicMaterial({ color: 0x777777 });

  // the laser on its tripod — shown once it is set up
  const tripod = new THREE.Group();
  [0, 2.1, 4.2].forEach((a) => {
    const leg = cyl(0.02, 0.02, 1.5, 0xd8b23a, Math.cos(a) * 0.25, 0.7, Math.sin(a) * 0.25, tripod);
    leg.rotation.z = Math.cos(a) * 0.3;
    leg.rotation.x = -Math.sin(a) * 0.3;
  });
  const laserHead = new THREE.Group();
  box(0.22, 0.2, 0.22, 0xd84a2a, 0, 0, 0, laserHead);
  const beam = new THREE.Mesh(new THREE.BoxGeometry(14, 0.01, 0.01), new THREE.MeshBasicMaterial({ color: 0xff3b30, transparent: true, opacity: 0.55 }));
  beam.position.x = 7;
  laserHead.add(beam);
  laserHead.position.y = 1.5;
  tripod.add(laserHead);
  tripod.position.set(POS.tripod.x, 0, POS.tripod.z);
  tripod.visible = false;
  scene.add(tripod);

  // the pump and the mixer, off site until they come
  function wheelSet(g, xs, zOff) {
    xs.forEach((x) => [zOff, -zOff].forEach((z) => {
      const w = cyl(0.5, 0.5, 0.35, 0x1c1d20, x, 0.5, z, g, 16);
      w.rotation.x = Math.PI / 2;
    }));
  }
  const pump = new THREE.Group();
  box(8.6, 1.0, 2.4, 0x2a2d31, 0, 1.0, 0, pump);
  box(2.2, 2.2, 2.4, 0xf2f0ea, -3.4, 2.0, 0, pump);
  box(0.05, 0.8, 2.0, 0x22303a, -4.52, 2.4, 0, pump);
  box(4.8, 1.4, 2.3, 0xf2b705, 0.6, 2.2, 0, pump);
  box(1.3, 1.0, 1.8, 0x444a50, 3.6, 1.8, 0, pump);   // the hopper, at the back
  box(4.6, 0.1, 2.35, 0x2a2d31, 0.6, 2.95, 0, pump);
  wheelSet(pump, [-3.2, 1.6, 3.0], 1.25);
  const pumpLabel = textSprite('LINE PUMP', { w: 2.4, color: '#ffd23f' });
  pumpLabel.position.set(0.6, 3.8, 0);
  pump.add(pumpLabel);
  pump.position.set(70, 0, POS.pump.z);
  pump.visible = false;
  scene.add(pump);

  const mixer = new THREE.Group();
  box(9, 1.0, 2.4, 0x2a2d31, 0, 1.0, 0, mixer);
  box(2.2, 2.2, 2.4, 0xe0e3e6, -3.5, 2.0, 0, mixer);
  box(0.05, 0.8, 2.0, 0x22303a, -4.62, 2.4, 0, mixer);
  const drumPivot = new THREE.Group();
  drumPivot.position.set(1.0, 2.6, 0);
  drumPivot.rotation.z = -0.22;
  const drum = cyl(1.05, 0.75, 4.6, 0xff6b1a, 0, 0, 0, drumPivot, 18);
  drum.rotation.z = Math.PI / 2;
  const stripe = cyl(1.07, 1.07, 0.35, 0xf2f0ea, -0.6, 0, 0, drumPivot, 18);
  stripe.rotation.z = Math.PI / 2;
  mixer.add(drumPivot);
  wheelSet(mixer, [-3.3, 1.6, 3.1], 1.25);
  mixer.position.set(80, 0, POS.mixer.z);
  mixer.visible = false;
  scene.add(mixer);

  const pipeGroup = new THREE.Group();
  scene.add(pipeGroup);
  const pipeMeshes = [];
  const pile = new THREE.Group();
  for (let k = 0; k < 6; k++) {
    const p = cyl(0.07, 0.07, 3, 0x6d7278, 0, 0.1 + (k % 3) * 0.15, (Math.floor(k / 3) - 0.5) * 0.18 + (k % 3) * 0.05, pile, 10);
    p.rotation.z = Math.PI / 2;
  }
  pile.position.set(POS.pile.x, 0, POS.pile.z);
  pile.visible = false;
  scene.add(pile);

  function pipeBetween(a, b, color, radius) {
    const len = hyp(a.x, a.z, b.x, b.z);
    const m = cyl(radius || 0.07, radius || 0.07, len, color || 0x6d7278, (a.x + b.x) / 2, 0.12, (a.z + b.z) / 2, pipeGroup, 10);
    m.rotation.z = Math.PI / 2;
    m.rotation.y = -Math.atan2(b.z - a.z, b.x - a.x);
    const ring = cyl(0.1, 0.1, 0.12, 0x2f3338, b.x, 0.12, b.z, pipeGroup, 10);
    ring.rotation.z = Math.PI / 2;
    ring.rotation.y = m.rotation.y;
    return [m, ring];
  }

  // people and a dog
  function limb(w, h, d, color, x, y, z, parent) {
    const pivot = new THREE.Group();
    pivot.position.set(x, y, z);
    box(w, h, d, color, 0, -h / 2, 0, pivot);
    parent.add(pivot);
    return pivot;
  }
  function makePerson(shirt, pants, hat) {
    const g = new THREE.Group();
    const legL = limb(0.17, 0.86, 0.19, pants, -0.11, 0.88, 0, g);
    const legR = limb(0.17, 0.86, 0.19, pants, 0.11, 0.88, 0, g);
    box(0.48, 0.64, 0.27, shirt, 0, 1.2, 0, g);
    const armL = limb(0.13, 0.62, 0.14, shirt, -0.32, 1.5, 0, g);
    const armR = limb(0.13, 0.62, 0.14, shirt, 0.32, 1.5, 0, g);
    mesh(new THREE.SphereGeometry(0.15, 12, 10), 0xe0b390, 0, 1.68, 0, g);
    if (hat) box(0.34, 0.1, 0.34, hat, 0, 1.83, 0, g);
    g.userData = { legL, legR, armL, armR, phase: Math.random() * 6 };
    return g;
  }
  function makeDog() {
    const g = new THREE.Group();
    const c = pick([0x8a5a2b, 0x2b2622, 0xd9c6a1]);
    box(0.7, 0.3, 0.26, c, 0, 0.45, 0, g);
    box(0.26, 0.24, 0.22, c, 0.42, 0.62, 0, g);
    box(0.12, 0.08, 0.1, 0x1a1a1a, 0.58, 0.6, 0, g);
    const tail = limb(0.06, 0.3, 0.06, c, -0.36, 0.55, 0, g);
    tail.rotation.z = 0.9;
    const legs = [[0.25, 0.1], [0.25, -0.1], [-0.25, 0.1], [-0.25, -0.1]].map(([x, z]) => limb(0.08, 0.32, 0.08, c, x, 0.32, z, g));
    g.userData = { legs, tail, phase: 0 };
    return g;
  }
  const pumpGuy = makePerson(0xf2b705, 0x2a2d31, 0xffffff);
  pumpGuy.visible = false;
  scene.add(pumpGuy);

  // the slab itself: one box per square metre, drawn from the base up to the concrete's height
  const cellGeo = new THREE.BoxGeometry(0.995, 1, 0.995);
  cellGeo.translate(0, 0.5, 0);
  const cellMat = new THREE.MeshPhongMaterial({ color: 0xffffff, shininess: 24, specular: 0x2a2a2a });
  const cellMesh = new THREE.InstancedMesh(cellGeo, cellMat, NX * NZ);
  const tmpM = new THREE.Matrix4(), tmpC = new THREE.Color();
  for (let k = 0; k < NX * NZ; k++) { cellMesh.setColorAt(k, tmpC.setHex(0x888888)); }
  scene.add(cellMesh);

  // footprints and other marks, laid on top of the concrete
  function markTexture(kind) {
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d');
    g.fillStyle = '#fff';
    if (kind === 'boot') {
      g.beginPath(); g.ellipse(64, 40, 22, 32, 0, 0, Math.PI * 2); g.fill();
      g.beginPath(); g.ellipse(64, 100, 17, 20, 0, 0, Math.PI * 2); g.fill();
      g.globalCompositeOperation = 'destination-out';
      for (let y = 18; y < 70; y += 9) g.fillRect(44, y, 40, 3);
    } else if (kind === 'paw') {
      g.beginPath(); g.ellipse(64, 80, 22, 18, 0, 0, Math.PI * 2); g.fill();
      [[36, 46], [54, 32], [74, 32], [92, 46]].forEach(([x, y]) => { g.beginPath(); g.arc(x, y, 10, 0, Math.PI * 2); g.fill(); });
    } else if (kind === 'butt') {
      g.beginPath(); g.ellipse(44, 64, 34, 44, 0.15, 0, Math.PI * 2); g.fill();
      g.beginPath(); g.ellipse(84, 64, 34, 44, -0.15, 0, Math.PI * 2); g.fill();
    } else if (kind === 'rain') {
      for (let k = 0; k < 26; k++) { g.beginPath(); g.arc(Math.random() * 128, Math.random() * 128, 2 + Math.random() * 4, 0, Math.PI * 2); g.fill(); }
    } else {
      g.fillRect(58, 0, 12, 128);
    }
    const t = new THREE.CanvasTexture(c);
    return t;
  }
  const MARK_KINDS = { boot: [0.3, 0.3], paw: [0.16, 0.16], butt: [0.6, 0.6], rain: [0.95, 0.95], line: [0.08, 1.0], thumb: [0.07, 0.07] };
  const markMeshes = {};
  const MAX_MARKS = 260;
  Object.keys(MARK_KINDS).forEach((kind) => {
    const [w, h] = MARK_KINDS[kind];
    const geo = new THREE.PlaneGeometry(w, h);
    geo.rotateX(-Math.PI / 2);
    const mat = new THREE.MeshBasicMaterial({ map: markTexture(kind === 'thumb' ? 'paw' : kind), color: 0x2a2c2e, transparent: true, opacity: 0.75, depthWrite: false });
    const im = new THREE.InstancedMesh(geo, mat, MAX_MARKS);
    const zero = new THREE.Matrix4().makeScale(0, 0, 0);
    for (let k = 0; k < MAX_MARKS; k++) im.setMatrixAt(k, zero);
    im.renderOrder = 2;
    scene.add(im);
    markMeshes[kind] = { im, free: Array.from({ length: MAX_MARKS }, (_, k) => MAX_MARKS - 1 - k) };
  });

  // task markers
  const markers = [];
  function addMarker(id, p, label, hold, active, done, opts) {
    const g = new THREE.Group();
    const ringMat = new THREE.MeshBasicMaterial({ color: 0xff6b1a });
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.55, 0.05, 8, 36), ringMat);
    ring.rotation.x = Math.PI / 2;
    ring.position.y = 0.06;
    g.add(ring);
    const beamM = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 2.4, 8), new THREE.MeshBasicMaterial({ color: 0xff6b1a, transparent: true, opacity: 0.35 }));
    beamM.position.y = 1.2;
    g.add(beamM);
    const sprite = textSprite(label, { w: (opts && opts.w) || 2.6 });
    sprite.position.y = 2.7;
    g.add(sprite);
    g.position.set(p.x, (opts && opts.y) || 0, p.z);
    g.visible = false;
    scene.add(g);
    const m = { id, x: p.x, z: p.z, label, hold, active, done, group: g, ring, sprite };
    markers.push(m);
    return m;
  }
  function removeMarker(m) {
    const k = markers.indexOf(m);
    if (k >= 0) markers.splice(k, 1);
    scene.remove(m.group);
  }

  // what is in your hands
  const hands = new THREE.Group();
  hands.position.set(0.32, -0.34, -0.62);
  camera.add(hands);
  const viewTools = {};
  (function buildViewTools() {
    const hose = new THREE.Group();
    const h1 = cyl(0.035, 0.04, 0.55, 0x1d1f22, -0.02, -0.02, -0.3, hose);
    h1.rotation.x = Math.PI / 2 - 0.35;
    const nozzle = cyl(0.045, 0.045, 0.08, 0x44484d, -0.02, 0.07, -0.56, hose);
    nozzle.rotation.x = Math.PI / 2 - 0.35;
    viewTools.hose = hose;
    const fl = new THREE.Group();
    box(0.5, 0.02, 0.14, 0xaeb4b9, -0.05, -0.08, -0.25, fl);
    const fh = cyl(0.015, 0.015, 0.4, 0xc79a52, -0.05, 0.08, -0.25, fl);
    fh.rotation.z = 0.5;
    viewTools.float = fl;
    const pt = new THREE.Group();
    const ringP = mesh(new THREE.TorusGeometry(0.45, 0.04, 6, 24), 0x444a50, -0.3, -0.9, -1.0, pt);
    ringP.rotation.x = Math.PI / 2;
    const disc = cyl(0.44, 0.44, 0.02, 0x8a8f94, -0.3, -0.92, -1.0, pt, 24);
    disc.userData.spin = true;
    const bar = cyl(0.02, 0.02, 1.0, 0x2a2d31, -0.3, -0.45, -0.7, pt);
    bar.rotation.x = 0.7;
    box(0.5, 0.03, 0.03, 0x2a2d31, -0.3, -0.05, -0.38, pt);
    box(0.25, 0.22, 0.25, 0xff6b1a, -0.3, -0.75, -1.0, pt);
    viewTools.machine = pt;
    viewTools.machineDisc = disc;
    const carry = new THREE.Group();
    const cp = cyl(0.07, 0.07, 3, 0x6d7278, -0.3, 0.05, -0.2, carry, 10);
    cp.rotation.x = Math.PI / 2;
    cp.rotation.z = 0.15;
    viewTools.pipe = carry;
    const cup = new THREE.Group();
    cyl(0.05, 0.04, 0.12, 0xf2efe8, -0.05, 0, -0.2, cup);
    viewTools.cup = cup;
    Object.values(viewTools).forEach((o) => { if (o.isGroup) { o.visible = false; hands.add(o); } });
  })();

  const stream = cyl(0.06, 0.06, 1, 0x7d7f80, 0, 0, 0, scene, 8);
  stream.visible = false;
  const endHose = cyl(0.045, 0.045, 1, 0x1d1f22, 0, 0, 0, scene, 8);
  endHose.visible = false;
  function stretch(m, a, b) {
    const dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
    const len = Math.hypot(dx, dy, dz) || 0.001;
    m.position.set((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2);
    m.scale.set(1, len, 1);
    m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), new THREE.Vector3(dx / len, dy / len, dz / len));
  }

  // ------------------------------------------------------------------ the player
  const player = { x: POS.vanDoor.x, z: POS.vanDoor.z, yaw: -2.2, pitch: -0.12, fall: 0, bob: 0, stepAcc: 0, moving: false };
  const input = { keys: {}, jx: 0, jy: 0, action: false, actionTapped: false };

  function onSlab(x, z) { return x > SLAB.x0 && x < SLAB.x1 && z > SLAB.z0 && z < SLAB.z1; }
  function cellAt(x, z) {
    if (!onSlab(x, z)) return null;
    const i = clamp(Math.floor(x - SLAB.x0), 0, NX - 1), j = clamp(Math.floor(z - SLAB.z0), 0, NZ - 1);
    return gs.cells[j * NX + i];
  }
  function surfaceY(c) { return c ? (c.fill * VIS) / 1000 : 0; }
  function obstacles() {
    const list = [
      { x0: -19.9, x1: -14.3, z0: 7.3, z1: 10.9 },
      { x0: -12.7, x1: -11.3, z0: -8.7, z1: -7.3 },
      { x0: -33, x1: -29, z0: -25, z1: -21 },
      { x0: -29.2, x1: -22.8, z0: 10.6, z1: 13.4 },
      { x0: -23.6, x1: -22.4, z0: -14.6, z1: -13.4 },
    ];
    if (pump.visible) list.push({ x0: pump.position.x - 4.6, x1: pump.position.x + 4.4, z0: pump.position.z - 1.3, z1: pump.position.z + 1.3 });
    if (mixer.visible) list.push({ x0: mixer.position.x - 4.8, x1: mixer.position.x + 4.6, z0: mixer.position.z - 1.3, z1: mixer.position.z + 1.3 });
    return list;
  }
  function collide(x, z) {
    x = clamp(x, -43.5, 43.5);
    z = clamp(z, -33.5, 33.5);
    for (const o of obstacles()) {
      const pad = 0.35;
      if (x > o.x0 - pad && x < o.x1 + pad && z > o.z0 - pad && z < o.z1 + pad) {
        const dl = x - (o.x0 - pad), dr = o.x1 + pad - x, du = z - (o.z0 - pad), dd = o.z1 + pad - z;
        const m = Math.min(dl, dr, du, dd);
        if (m === dl) x = o.x0 - pad; else if (m === dr) x = o.x1 + pad; else if (m === du) z = o.z0 - pad; else z = o.z1 + pad;
      }
    }
    return [x, z];
  }

  // ------------------------------------------------------------------ marks on the concrete
  function stamp(kind, x, z, rot, silent) {
    const c = cellAt(x, z);
    if (!c || !gs.poured || gs.H >= 60 || c.fill < 20) return false;
    const pool = markMeshes[kind];
    if (!pool || !pool.free.length) return false;
    const slot = pool.free.pop();
    const y = surfaceY(c) + 0.006;
    tmpM.makeRotationY(rot || 0).setPosition(x, y, z);
    pool.im.setMatrixAt(slot, tmpM);
    pool.im.instanceMatrix.needsUpdate = true;
    c.marks.push({ kind, slot });
    if (!silent) gs.stats.prints++;
    return true;
  }
  function clearMarks(c) {
    if (!c.marks.length) return false;
    const zero = new THREE.Matrix4().makeScale(0, 0, 0);
    c.marks.forEach((mk) => {
      const pool = markMeshes[mk.kind];
      pool.im.setMatrixAt(mk.slot, zero);
      pool.im.instanceMatrix.needsUpdate = true;
      pool.free.push(mk.slot);
    });
    c.marks = [];
    return true;
  }
  /** The turn about the vertical that points a mark's toe (drawn towards -z) along dx, dz. */
  function headingOf(dx, dz) { return Math.atan2(-dx, -dz); }
  function stampLine(kind, a, b, spacing) {
    const len = hyp(a.x, a.z, b.x, b.z) || 0.001;
    const ux = (b.x - a.x) / len, uz = (b.z - a.z) / len;
    const rot = headingOf(ux, uz);
    let n = 0;
    for (let s = 0; s <= len; s += spacing) {
      const f = s / len, side = (Math.round(s / spacing) % 2 ? 0.12 : -0.12);
      const x = lerp(a.x, b.x, f) - uz * side, z = lerp(a.z, b.z, f) + ux * side;
      if (stamp(kind, x, z, rot)) n++;
    }
    return n;
  }

  // ------------------------------------------------------------------ the slab, as numbers
  function neighbours(c) {
    const out = [];
    for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
      if (!di && !dj) continue;
      const i = c.i + di, j = c.j + dj;
      if (i >= 0 && i < NX && j >= 0 && j < NZ) out.push(gs.cells[j * NX + i]);
    }
    return out;
  }
  function filledShare() { return gs.cells.filter((c) => c.fill >= day.thick - 10).length / gs.cells.length; }
  function rms() {
    let s = 0;
    gs.cells.forEach((c) => { const d = c.fill - day.thick; s += d * d; });
    return Math.sqrt(s / gs.cells.length);
  }
  function laserWorks() { return gs.prep.laser && gs.laserBattery; }

  let cellsDirty = true;
  function paintCells() {
    const wet = 1 - gs.H / 100;
    for (const c of gs.cells) {
      const h = Math.max(0.0005, surfaceY(c));
      tmpM.makeScale(1, h, 1).setPosition(SLAB.x0 + c.i + 0.5, 0, SLAB.z0 + c.j + 0.5);
      if (c.fill <= 0.5) tmpM.makeScale(0, 0, 0);
      cellMesh.setMatrixAt(c.idx, tmpM);
      if (gs.laserOn && laserWorks() && !gs.pourDone) {
        const d = c.fill - day.thick;
        if (Math.abs(d) <= 3) tmpC.setHex(0x4fae6a);
        else if (d > 0) tmpC.setHex(d > 10 ? 0xd8392f : 0xe0873a);
        else tmpC.setHex(d < -10 ? 0x2f6fd0 : 0x5aa9ff);
      } else {
        const v = lerp(0.62, 0.36, wet) + c.pan * 0.035 + c.blade * 0.05 - (c.defect ? 0.08 : 0);
        tmpC.setRGB(v * 0.98, v, v * 1.02 + c.blade * 0.02);
      }
      cellMesh.setColorAt(c.idx, tmpC);
    }
    cellMesh.instanceMatrix.needsUpdate = true;
    if (cellMesh.instanceColor) cellMesh.instanceColor.needsUpdate = true;
    cellsDirty = false;
  }

  // ------------------------------------------------------------------ HUD bits
  const toastBox = $('#toasts');
  function toast(text, kind) {
    const d = document.createElement('div');
    d.className = 'toast' + (kind ? ' ' + kind : '');
    d.textContent = text;
    toastBox.prepend(d);
    while (toastBox.children.length > 3) toastBox.lastChild.remove();
    setTimeout(() => d.remove(), 6500);
  }

  const modalQueue = [];
  let modalOpen = null;
  function modal(spec) {
    if (modalOpen) { modalQueue.push(spec); return; }
    modalOpen = spec;
    gs.waitMode = gs.waitMode === 'van' ? 'van' : null;
    if (gs.waitMode !== 'van') gs.fastForward = null;
    input.action = false;
    $('#mWho').textContent = spec.who || '';
    $('#mTitle').textContent = spec.title || '';
    $('#mText').textContent = spec.text || '';
    const box2 = $('#mChoices');
    box2.innerHTML = '';
    (spec.choices || [{ label: 'OK', primary: true }]).forEach((ch) => {
      const b = document.createElement('button');
      b.textContent = ch.label;
      if (ch.primary) b.className = 'primary';
      if (ch.danger) b.className = 'danger';
      b.addEventListener('click', () => {
        $('#modal').hidden = true;
        modalOpen = null;
        if (ch.fn) ch.fn();
        if (!modalOpen && modalQueue.length) modal(modalQueue.shift());
      });
      box2.appendChild(b);
    });
    $('#modal').hidden = false;
  }

  // ------------------------------------------------------------------ time
  function timeScale() {
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end' || gs.phase === 'morning') return 0;
    if (gs.waitMode === 'van') return 25;
    if (gs.waitMode === 'guard') return 8;
    if (gs.fastForward) return 14;
    return { prep: 0.6, pipes: 0.6, pour: 0.45, wash: 0.5, cure: 1.0 }[gs.phase] || 0;
  }
  function awayNow() {
    return gs.waitMode === 'van' || hyp(player.x, player.z, 0, 0) > 18;
  }
  function simulate(dm, away) {
    while (dm > 0) {
      const step = Math.min(dm, 2);
      gs.t += step;
      dm -= step;
      if (gs.poured && gs.H < 100) {
        const before = gs.H;
        gs.H = Math.min(100, gs.H + cureRate(gs.t) * stageMul(gs.H) * step);
        milestone(before, gs.H);
        if (Math.floor(before) !== Math.floor(gs.H)) cellsDirty = true;
      }
      if (gs.waitMode === 'van') gs.energy = clamp(gs.energy + 0.12 * step, 0, 100);
      else gs.energy = clamp(gs.energy - 0.04 * step, 0, 100);
      if (gs.truck && gs.truck.waiting) gs.truckWaitPaid += 1.5 * step;
      while (gs.schedule.length && gs.schedule[0].at <= gs.t) {
        const ev = gs.schedule.shift();
        ev.fn(away);
        if (modalOpen) break;
      }
      if (gs.phase === 'cure' || gs.phase === 'wash') {
        if (gs.poured && gs.H < 75 && gs.t >= gs.nextNuisance) {
          gs.nextNuisance = gs.t + rnd(22, 50);
          nuisance(away);
        }
        if (gs.H >= 60) hardenMarks();
      }
      if (modalOpen) break;
    }
  }
  function milestone(before, now) {
    const hit = (x) => before < x && now >= x;
    const note = (key, text) => {
      if (gs.milestones[key]) return;
      gs.milestones[key] = true;
      if (gs.waitMode) { gs.waitMode = null; showWait(); }
      toast(text, 'good');
    };
    if (hit(25)) note('p', 'Hardness 25%: it will carry the pans now. First pass window is open.');
    if (hit(55)) note('b', 'Hardness 55%: blades time. Keep the pans if it still needs flattening.');
    if (hit(92)) note('late', 'Hardness 92%: too hard to close the surface any more.');
    if (hit(95)) note('95', 'Hardness 95%. You may, legally, go home.');
  }
  function hardenMarks() {
    gs.cells.forEach((c) => {
      if (c.marks.length && !c.defect) { c.defect = true; cellsDirty = true; }
    });
  }

  // ------------------------------------------------------------------ nuisances
  function crossPath(anyway) {
    const fromWest = chance(0.5);
    const z1 = rnd(-3, 3), z2 = rnd(-3, 3);
    const a = { x: fromWest ? -11 : 11, z: z1 }, b = { x: fromWest ? 11 : -11, z: z2 };
    if (anyway) return [a, b];
    const side = chance(0.5) ? -5.8 : 5.8;
    return [a, { x: fromWest ? -7.2 : 7.2, z: side }, { x: fromWest ? 7.2 : -7.2, z: side }, b];
  }
  const walkers = [];
  function spawnWalker(kind, path, speed, onDone) {
    const m = kind === 'dog' ? makeDog() : makePerson(pick([0x3b5b8c, 0x8c3b3b, 0x4e7a44, 0x5a4f7a, 0xc9c3b8]), pick([0x2f3540, 0x3a3226, 0x23262b]), chance(0.3) ? 0xf2b705 : null);
    m.position.set(path[0].x, 0, path[0].z);
    scene.add(m);
    walkers.push({ kind, m, path, seg: 0, speed, acc: 0, onDone });
  }
  function nuisance(away) {
    const kinds = [[0.44, 'cross'], [day.dogChance, 'dog'], [0.1, 'bird'], [0.12, 'foreman'], [0.08, 'kid']];
    if (day.rh > 76 && !gs.rained) kinds.push([0.12, 'rain']);
    const kind = weighted(kinds);
    if (kind === 'cross') {
      const who = pick(L.cross);
      if (away) {
        if (chance(0.65)) {
          const p = crossPath(true);
          const n = stampLine('boot', p[0], p[1], 0.72);
          if (n) { gs.stats.crossed++; const line = pick(L.crossAway); toast(line, 'warn'); remember(line); }
        }
        return;
      }
      modal({
        who: who.who, title: 'Can I cross?', text: who.ask,
        choices: [
          { label: 'Go to hell!', primary: true, fn: () => {
            gs.stats.hell++;
            if (chance(0.72)) { toast(who.back); spawnWalker('person', crossPath(false), 1.4); }
            else { const l = pick(L.crossAnyway); toast(l, 'warn'); remember(`${who.who}: told to go to hell, crossed anyway.`); gs.stats.crossed++; spawnWalker('person', crossPath(true), 1.5); }
          } },
          { label: 'Walk around, please.', fn: () => {
            if (chance(0.85)) { toast('They walk around, muttering about "concrete people".'); spawnWalker('person', crossPath(false), 1.3); }
            else { toast('They hear "around" as "across". It happens.', 'warn'); gs.stats.crossed++; spawnWalker('person', crossPath(true), 1.3); }
          } },
          { label: 'Fine. Quickly.', danger: true, fn: () => {
            toast('They are not quick. They stop in the middle to take a phone call.', 'warn');
            remember(`You let ${who.who.toLowerCase()} cross. They took a phone call in the middle.`);
            gs.stats.crossed++;
            spawnWalker('person', crossPath(true), 0.8);
          } },
        ],
      });
    } else if (kind === 'dog') {
      gs.stats.dogs++;
      const loops = () => {
        const pts = [{ x: -9, z: rnd(-3, 3) }];
        for (let k = 0; k < 5; k++) pts.push({ x: rnd(-5, 5), z: rnd(-3.4, 3.4) });
        pts.push({ x: 12, z: rnd(-6, 6) });
        return pts;
      };
      if (away) {
        if (chance(0.85)) {
          const pts = loops();
          for (let k = 1; k < pts.length; k++) stampLine('paw', pts[k - 1], pts[k], 0.42);
          const line = pick(L.dog.away);
          toast(line, 'warn');
          remember(line);
        }
        return;
      }
      const choices = [];
      if (gs.sausage) choices.push({ label: 'Throw it your spare sausage', primary: true, fn: () => {
        gs.sausage = false;
        toast('The dog catches the sausage mid-air and leaves with it. Best trade of the day.', 'good');
        spawnWalker('dog', [{ x: -9, z: 0 }, { x: -20, z: -12 }], 5);
      } });
      choices.push({ label: 'SHOO!', primary: !choices.length, fn: () => {
        if (chance(0.5)) { toast('The dog hears "PLAY WITH ME". Laps of honour on the slab.', 'warn'); remember('A dog did laps of the slab. You shouted. It loved it.'); spawnWalker('dog', loops(), 4.5); }
        else { toast('The dog gives you a look of deep disappointment and leaves.', 'good'); spawnWalker('dog', [{ x: -9, z: 2 }, { x: -25, z: 20 }], 4.5); }
      } });
      choices.push({ label: 'Accept your fate', danger: true, fn: () => { toast('You watch. It is almost beautiful.', 'warn'); remember('A dog ran across the slab. You watched it happen.'); spawnWalker('dog', loops(), 4.2); } });
      modal({ who: L.dog.who, title: 'Uh oh.', text: L.dog.ask, choices });
    } else if (kind === 'bird') {
      const x = rnd(-5, 5), z = rnd(-3, 3);
      stampLine('paw', { x, z }, { x: x + 0.6, z: z + 0.3 }, 0.2);
      toast(L.bird);
    } else if (kind === 'foreman') {
      if (away) { toast('Three missed calls from the foreman. And one voice message that is just breathing.'); return; }
      modal({
        who: 'Foreman, on the phone', title: 'Ring ring.', text: pick(L.foreman),
        choices: [
          { label: 'It\'s basically hard.', fn: () => toast('It is not basically hard. You both know it.') },
          { label: 'Tell the truth.', fn: () => toast('Foreman: "Concrete is just stubborn water." He hangs up.') },
          { label: 'Pretend the signal is bad.', primary: true, fn: () => toast('"Kkkhhh... you\'re... breaking... kkkhh." Flawless.', 'good') },
        ],
      });
    } else if (kind === 'kid') {
      if (away || chance(0.4)) {
        const p = crossPath(true);
        if (stampLine('line', p[0], p[1], 0.9)) { toast('A kid on a scooter drew a perfect line across the slab. Straight, at least.', 'warn'); remember('A scooter kid left a line across the slab.'); }
        return;
      }
      toast('A kid on a scooter eyes the slab. You give the Look. The kid turns around. You still have it.', 'good');
    } else if (kind === 'rain') {
      gs.rained = true;
      const pit = () => {
        let n = 0;
        for (let k = 0; k < 12; k++) if (stamp('rain', rnd(-5.5, 5.5), rnd(-3.5, 3.5), rnd(0, 6))) n++;
        if (n) { toast('Rain. The surface looks like the moon now. Float it out while you still can.', 'warn'); remember('It rained on the fresh slab.'); }
      };
      if (away) { pit(); return; }
      modal({
        who: 'The sky', title: 'Dark clouds.', text: 'Rain in ten minutes. The plastic sheet is in the van, folded by someone who hated you.',
        choices: [
          { label: 'Cover the slab (20 min)', primary: true, fn: () => { gs.energy = clamp(gs.energy - 8, 0, 100); simulate(20, true); toast('Covered. The sheet flapped you in the face twice. The slab is fine.', 'good'); } },
          { label: 'It\'ll pass.', danger: true, fn: () => (chance(0.6) ? pit() : toast('It passed. Lucky. Don\'t get used to it.', 'good')) },
        ],
      });
    }
  }

  // ------------------------------------------------------------------ falling over
  function fall(text) {
    if (player.fall > 0) return;
    player.fall = 1.8;
    gs.stats.falls++;
    gs.energy = clamp(gs.energy - 5, 0, 100);
    const c = cellAt(player.x, player.z);
    if (c && gs.phase === 'pour') { c.fill = Math.max(0, c.fill - 12); cellsDirty = true; }
    else if (c) stamp('butt', player.x, player.z, player.yaw, true);
    const line = text || pick(L.falls);
    toast(line, 'warn');
    remember(line);
  }

  // ------------------------------------------------------------------ the day, phase by phase
  function startDay() {
    $('#title').hidden = true;
    $('#hud').hidden = false;
    gs.phase = 'morning';
    const alarm = pick(L.alarm);
    const driveTo = (snooze) => {
      const d = pick(L.drive);
      gs.t = 5 * 60 + 25 + snooze + d[1];
      modal({
        who: 'On the way', title: 'The drive in', text: d[0],
        choices: [{ label: 'Arrive', primary: true, fn: () => arrive() }],
      });
    };
    modal({
      who: 'Alarm', title: 'Rise and shine.', text: alarm,
      choices: [
        { label: 'Get up', primary: true, fn: () => driveTo(0) },
        { label: 'Snooze (9 min)', fn: () => driveTo(chance(0.5) ? 9 : 27) },
        { label: 'Snooze and pretend it\'s Sunday', danger: true, fn: () => { remember('You pretended it was Sunday. It was not Sunday.'); driveTo(irnd(45, 75)); } },
      ],
    });
  }

  function arrive() {
    gs.phase = 'prep';
    gs.arrived = gs.t;
    player.x = POS.vanDoor.x; player.z = POS.vanDoor.z;
    player.yaw = Math.atan2(player.x - 0, player.z - 0);
    gs.pumpAt = 7 * 60 + day.pumpDelay;
    const late = gs.t - 6 * 60;
    if (gs.t >= gs.pumpAt) {
      gs.pumpAt = gs.t + 2;
      toast('The pump driver is already here, arms crossed, eating something. You are late.', 'warn');
      remember(`You arrived at ${clock(gs.t)}, after the pump. The pump driver will tell this story at his wedding.`);
    } else if (late > 15) {
      toast(`You arrive at ${clock(gs.t)}. Not first on site. The birds are disappointed.`, 'warn');
    } else {
      toast(pick(L.arrive));
    }
    at(gs.pumpAt, pumpArrives);
    if (day.pumpDelay >= 25 && gs.t < 7 * 60 - 5) {
      at(6 * 60 + 50, () => toast(pick(L.pumpLate).replace('{eta}', clock(gs.pumpAt)), 'warn'));
    }
    buildPrepMarkers();
  }

  function buildPrepMarkers() {
    addMarker('unload', POS.vanDoor, 'Unload tools', 2.2, () => !gs.prep.unload, () => {
      gs.prep.unload = true; gs.toolsUnloaded = true;
      toast('Tools out: floats, rakes, laser, the good shovel. The bad shovel came too, it always does.');
    });
    addMarker('formNW', POS.cornerNW, 'Check formwork', 1.8, () => !gs.prep.formNW && !gs.pourStarted, () => {
      gs.prep.formNW = true;
      toast(day.formworkWeak ? 'You find two loose stakes on the north-west side and knock them back in. Good catch.' : 'Formwork is solid. The carpenter lives to see another day.');
    });
    addMarker('formSE', POS.cornerSE, 'Check formwork', 1.8, () => !gs.prep.formSE && !gs.pourStarted, () => {
      gs.prep.formSE = true;
      toast(day.formworkWeak ? 'South-east board was held on with hope and one nail. Fixed.' : 'Solid. Suspiciously solid.');
    });
    addMarker('laser', POS.tripod, 'Set up laser', 2.6, () => !gs.prep.laser, () => {
      if (!gs.prep.unload) { toast('The laser is still in the van. Unload the tools first.', 'warn'); return false; }
      gs.prep.laser = true;
      tripod.visible = true;
      toast(`Laser set to ${day.thick} mm above the base. It spins. You feel like a scientist.`, 'good');
      return true;
    });
    addMarker('chairs', POS.chairs, 'Fix mesh chairs', 2.0, () => !gs.prep.chairs && !gs.pourStarted, () => {
      gs.prep.chairs = true;
      toast('You kick the rebar chairs into place. Three of them kick back, all at shin height.');
    });
  }

  function driveIn(group, target, seconds, done) {
    group.visible = true;
    drives.push({ group, from: group.position.x, to: target, t: 0, seconds, done });
  }
  const drives = [];

  function pumpArrives() {
    gs.pumpHere = true;
    gs.fastForward = null;
    pump.position.x = 60;
    driveIn(pump, POS.pump.x, 5, () => {
      pumpGuy.visible = true;
      pumpGuy.position.set(POS.pump.x - 5.2, 0, POS.pump.z + 1.8);
    });
    pile.visible = true;
    if (gs.phase === 'prep') gs.phase = 'pipes';
    modal({ who: 'Pump driver', title: 'The pump is here.', text: pick(L.pumpArrive) + '\n\nLay the pipe line from the pump to the slab: grab a pipe from the pile, carry it to the next marker, clamp it.', choices: [{ label: 'On it', primary: true }] });
    buildPipeMarkers();
    const first = Math.max(7 * 60 + 30 + day.truckDelays[0], gs.t + 25);
    scheduleTruck(first);
    if (first - (7 * 60 + 30) >= 20) at(Math.max(gs.t + 5, 7 * 60 + 25), () => toast(pick(L.truckLate).replace('{eta}', clock(first)), 'warn'));
  }

  function buildPipeMarkers() {
    addMarker('pile', POS.pile, 'Grab a pipe', 1.2, () => gs.pipes < 6 && !gs.carrying, () => {
      gs.carrying = true;
      toast(gs.pipes === 0 ? 'A pipe: 3 m of steel and regret. Carry it to the first marker by the pump.' : 'Another one. Your shoulder remembers the last.');
    });
    PIPE_ROUTE.forEach((p, k) => {
      addMarker('pipe' + k, p, `Pipe ${k + 1} of 6`, 1.6, () => gs.carrying && gs.pipes === k, () => {
        gs.carrying = false;
        gs.pipes++;
        pipeMeshes.push(pipeBetween(k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], p));
        toast(pick(L.pipe));
        gs.energy = clamp(gs.energy - 2, 0, 100);
        if (gs.pipes === 6) { toast('Line laid, pump to slab. Now we wait for the mixer.', 'good'); pile.visible = false; maybeStartPour(); }
      }, { w: 2.2 });
    });
  }

  function scheduleTruck(when) {
    const no = gs.truckNo + 1;
    gs.nextTruckAt = when;
    at(when, () => truckArrives(no));
    if (when - gs.t >= 30 && no > 1) toast(pick(L.truckLate).replace('{eta}', clock(when)), 'warn');
  }

  function truckArrives(no) {
    gs.truckNo = no;
    gs.truck = { no, left: TRUCK_M3, waiting: true };
    gs.fastForward = null;
    mixer.position.x = 70;
    driveIn(mixer, POS.mixer.x, 5);
    if (no === 1) {
      gs.mixState = day.mix;
      gs.mixFactor = day.mix === 'soup' ? 0.75 : day.mix === 'stiff' ? 1.08 : 1;
    }
    toast(`Truck ${no} is here with ${TRUCK_M3} m³.` + (gs.pipes < 6 ? ' The line isn\'t laid. The driver starts a waiting-time clock at 90 €/h.' : ''), gs.pipes < 6 ? 'warn' : '');
    maybeStartPour();
  }

  function maybeStartPour() {
    if (!gs.truck || gs.pipes < 6) return;
    gs.truck.waiting = false;
    if (gs.pourStarted) { toast(`Truck ${gs.truck.no} backs up to the pump. Keep going.`, 'good'); return; }
    gs.pourStarted = true;
    gs.phase = 'pour';
    gs.tool = 'hose';
    const mixText = {
      stiff: 'It comes down the chute like wet sand. Stiff. The pump is going to hate this.',
      ok: 'It flows like it should. Nobody trust it.',
      soup: 'It comes out like soup. Somebody at the plant was generous with the tap. This will take ages to harden.',
    }[day.mix];
    const choices = [];
    if (day.mix === 'stiff') {
      choices.push({ label: 'Add a splash of water', fn: () => { gs.water = 1; gs.mixFactor = 0.9; gs.mixState = 'ok'; toast('The driver adds "a splash". It is a bucket. Pumps easier, hardens slower.'); } });
      choices.push({ label: 'Pump it as it is', primary: true, fn: () => toast('Brave. Keep the hammer handy.', 'warn') });
    } else if (day.mix === 'soup') {
      choices.push({ label: 'Send it back (costs time)', fn: () => {
        gs.truck.waiting = true;
        gs.mixState = 'ok';
        gs.mixFactor = 1;
        const back = irnd(60, 100);
        toast(`The driver goes back to the plant, swearing in two languages. New load in ${back} minutes.`, 'warn');
        remember('You sent a truck of soup back to the plant.');
        gs.extraTrucks++;
        gs.truck = null;
        mixer.visible = false;
        scheduleTruck(gs.t + back);
      } });
      choices.push({ label: 'Use it anyway', primary: true, fn: () => { toast('Soup it is. It levels itself, and it will cure like it has all the time in the world.', 'warn'); remember('You poured soup. It took its time.'); } });
    } else {
      choices.push({ label: 'Pour!', primary: true });
    }
    modal({
      who: `Mixer driver · ${volumeNeeded().toFixed(1)} m³ needed`, title: 'The concrete is here.',
      text: pick(L.truckDriver) + '\n\n' + mixText + `\n\nHose it in with the hose and look at where it goes. Float it to the laser with the float. Aim for ${day.thick} mm everywhere. Don't pour more than you need: there are only ${Math.ceil(volumeNeeded() * 1.03 / TRUCK_M3)} trucks ordered.`,
      choices,
    });
  }

  function truckEmpty() {
    const ordered = Math.ceil(volumeNeeded() * 1.03 / TRUCK_M3) + gs.extraTrucks;
    gs.truck = null;
    setTimeout(() => { mixer.visible = false; }, 0);
    if (filledShare() >= 0.97) { toast('That was the last of it. Level it up and finish the pour.', 'good'); return; }
    if (gs.truckNo < ordered) {
      const delay = day.truckDelays[Math.min(gs.truckNo, day.truckDelays.length - 1)];
      scheduleTruck(gs.t + 12 + delay);
      toast(`Truck ${gs.truckNo} is empty. The next one is due at ${clock(gs.nextTruckAt)}. Float what you have.`);
      return;
    }
    modal({
      who: 'The plant', title: 'Out of concrete.',
      text: `You're short. The plant can send one more truck, "in about an hour and a half, the driver is on his lunch".`,
      choices: [
        { label: 'Order one more truck', primary: true, fn: () => { gs.extraTrucks++; remember('You ran out of concrete and ordered an extra truck.'); scheduleTruck(gs.t + irnd(70, 110)); } },
        { label: 'Make do with what\'s there', danger: true, fn: () => toast('Bold. The low spots will remember this.', 'warn') },
      ],
    });
  }

  function finishPour() {
    const share = filledShare();
    const dev = rms();
    const left = gs.truck ? gs.truck.left : 0;
    gs.waste += left;
    gs.pourDone = true;
    gs.poured = true;
    gs.pourEnd = gs.t;
    gs.phase = 'wash';
    gs.tool = 'hands';
    gs.laserOn = false;
    gs.truck = null;
    mixer.visible = false;
    gs.nextNuisance = gs.t + rnd(14, 28);
    cellsDirty = true;
    if (share < 0.97) gs.cells.forEach((c) => { if (c.fill < day.thick - 10) c.defect = true; });
    remember(`Pour finished at ${clock(gs.t)}, ±${dev.toFixed(1)} mm off the laser.`);
    modal({
      who: 'Pour done', title: `Poured at ${clock(gs.t)}.`,
      text: `Average error: ±${dev.toFixed(1)} mm.` + (left > 0.2 ? `\n${left.toFixed(1)} m³ left in the truck. The driver dumps it behind the site office. That's where the foreman parks.` : '') +
        `\n\nWash your tools at the water tank before they set. The pump driver does his own pipes.`,
      choices: [{ label: 'To the tank', primary: true }],
    });
    at(gs.t + 6, () => { toast(pick(L.wash)); });
    at(gs.t + 10, () => { pipeGroup.clear(); gs.pipesGone = 1; });
    at(gs.t + 16, () => {
      pumpGuy.visible = false;
      drives.push({ group: pump, from: pump.position.x, to: 70, t: 0, seconds: 6, done: () => { pump.visible = false; } });
      toast('The pump leaves, honking twice. Once for goodbye, once for you personally.');
    });
  }

  function buildLateMarkers() {
    addMarker('wash', POS.ibcFront, 'Wash tools', 4, () => gs.phase === 'wash', () => {
      gs.washed = true;
      gs.phase = 'cure';
      gs.tool = 'hands';
      toast('Tools washed. You washed your boots too, then stepped in the slurry. Classic.');
      modal({
        who: 'Now it hardens', title: 'The waiting part.',
        text: `It's ${Math.round(tempAt(gs.t))} °C with ${day.rh}% humidity and a ${day.thick} mm slab. Keep an eye on the hardness meter.\n\n` +
          '• Pans (float pan on the power trowel) from about 25%. One to three passes.\n• Blades from about 55%.\n• Hand trowel the edges, corners and pipe collars.\n• Nobody leaves before 95%.\n\n' +
          'Meanwhile: guard the slab, nap in the van, or walk to the kebab stand. Footprints can be floated out while it\'s still under 50%.',
        choices: [{ label: 'Right', primary: true }],
      });
    });
    addMarker('batteries', POS.vanDoor, 'Get batteries', 1.4, () => !gs.laserBattery, () => {
      gs.laserBattery = true;
      toast('Fresh batteries. The laser beeps like nothing happened. You know what happened.', 'good');
    });
    addMarker('lunch', POS.kioskFront, 'Lunch', 1.2, () => gs.phase === 'cure' && gs.H < 90, () => { lunch(); });
    addMarker('home', POS.vanDoor, 'Go home', 1.2, () => gs.phase === 'cure' && gs.panPasses.length > 0, () => { tryGoHome(); });
    EDGE_SPOTS.forEach((e, k) => {
      addMarker('edge' + k, e, e.label, 1.6, () => gs.phase === 'cure' && gs.H >= 18 && !e.done, () => {
        if (gs.H < 25) { toast('Too soft. You\'re drawing in it, not troweling it. Give it a bit.', 'warn'); return false; }
        e.done = true;
        gs.edgesDone++;
        if (gs.H > 85) { gs.edgeNotes.push('late'); toast(`${e.label}: too hard to close properly. It'll do. It won't be pretty.`, 'warn'); }
        else toast(`${e.label} troweled. On your knees, with a hand trowel, like a monk. ${10 - gs.edgesDone} to go.`);
        return true;
      }, { w: 2.2 });
    });
  }

  function lunch() {
    modal({
      who: 'Kebab & Coffee', title: 'What\'ll it be?', text: L.lunch,
      choices: [
        { label: 'Kebab, extra garlic (40 min)', primary: true, fn: () => { gs.energy = clamp(gs.energy + 45, 0, 100); simulate(40, true); toast('Kebab. The garlic will guard the slab for you for the rest of the day.', 'good'); remember('Kebab with extra garlic.'); } },
        { label: 'Sausage in a bun, one to go (30 min)', fn: () => { gs.energy = clamp(gs.energy + 32, 0, 100); gs.sausage = true; simulate(30, true); toast('You keep one sausage "for later". Later has plans for it.', 'good'); } },
        { label: 'Coffee and a thermos refill (10 min)', fn: () => { gs.energy = clamp(gs.energy + 12, 0, 100); gs.cups = 3; simulate(10, true); toast('Thermos full. You are, again, a person.', 'good'); } },
        { label: 'Nothing. Back to the slab.' },
      ],
    });
  }

  function tryGoHome() {
    const missing = [];
    if (gs.H < 95) missing.push(`It's only ${Math.floor(gs.H)}% hard. 95% or you sleep here.`);
    if (!gs.panPasses.length) missing.push('No pan pass yet.');
    if (!gs.bladePasses.length) missing.push('No blade pass yet.');
    if (gs.edgesDone < EDGE_SPOTS.length) missing.push(`${EDGE_SPOTS.length - gs.edgesDone} edges, corners or collars still to hand trowel.`);
    if (missing.length) {
      modal({ who: 'Foreman, in your head', title: 'Not yet.', text: missing.join('\n'), choices: [{ label: 'Fine', primary: true }] });
      return false;
    }
    endDay();
    return true;
  }

  // ------------------------------------------------------------------ troweling
  function passCoverage(key) { return gs.cells.filter((c) => c[key]).length / gs.cells.length; }
  function completePass(kind) {
    const H = gs.H;
    let verdict, good;
    if (kind === 'pan') {
      good = H >= 25 && H <= 65;
      verdict = H < 25 ? 'Too early: the pans dug in and made waves.' : H <= 65 ? 'Pan pass in the window. Flatter already.' : 'Late pan pass: skated over the top. Better than nothing.';
      gs.cells.forEach((c) => {
        const nb = neighbours(c);
        const avg = nb.reduce((s, n) => s + n.fill, 0) / nb.length;
        c.fill = lerp(c.fill, lerp(avg, day.thick, 0.5), H <= 65 ? 0.45 : 0.2);
        if (H < 25) c.fill += rnd(-4, 4);
        c.pan++;
        c.covP = false;
        if (H < 60 && c.marks.length) { clearMarks(c); gs.stats.repaired++; }
      });
      gs.panPasses.push({ H, good });
    } else {
      good = H >= 55 && H <= 92 && gs.panPasses.length > 0;
      verdict = !gs.panPasses.length ? 'Blades with no pan pass first. It shines, but it isn\'t flat.' : H < 55 ? 'Blades too early: tore the paste a bit.' : H <= 92 ? 'Blade pass in the window. It\'s starting to shine.' : 'Blades on a slab that\'s already hard. You\'re polishing a stone.';
      gs.cells.forEach((c) => { c.blade++; c.covB = false; });
      gs.bladePasses.push({ H, good });
    }
    cellsDirty = true;
    toast(`${kind === 'pan' ? 'Pan' : 'Blade'} pass ${kind === 'pan' ? gs.panPasses.length : gs.bladePasses.length} done at ${Math.floor(H)}%. ${verdict}`, good ? 'good' : 'warn');
  }

  // ------------------------------------------------------------------ the end
  function endDay() {
    gs.phase = 'end';
    $('#hud').hidden = true;
    const dev = rms();
    const defects = gs.cells.filter((c) => c.defect).length;
    const late = Math.max(0, gs.arrived - 6 * 60);
    const goodPans = gs.panPasses.filter((p) => p.good).length;
    const goodBlades = gs.bladePasses.filter((p) => p.good).length;
    let score = 1000 - dev * 22 - defects * 18 - late * 1.5 - gs.waste * 35 - gs.truckWaitPaid * 0.5 - gs.stats.falls * 10 - gs.edgeNotes.length * 12
      + Math.min(goodPans, 3) * 40 + Math.min(goodBlades, 3) * 40 + gs.stats.hell * 5;
    score = Math.round(clamp(score, 0, 1200));
    const rank = score >= 950 ? 'Slab wizard' : score >= 800 ? 'Proper concrete person' : score >= 620 ? 'Adequate slab operator' : score >= 420 ? 'Footprint curator' : 'The dog\'s favourite';
    const best = Number(store('pourday.best') || 0);
    if (score > best) store('pourday.best', String(score));
    $('#eRank').textContent = rank;
    $('#eScore').textContent = `${score} points` + (score > best ? ' · new best' : best ? ` · best ${best}` : '');
    const rows = [
      ['Arrived', clock(gs.arrived) + (late > 15 ? ` (${dur(late)} late)` : '')],
      ['Poured', clock(gs.pourEnd)],
      ['Home', clockDay(gs.t)],
      ['Shift', dur(gs.t - gs.arrived)],
      ['Weather', `${day.baseTemp.toFixed(0)} °C · ${day.rh}% RH · wind ${day.wind} m/s`],
      ['Slab', `${day.thick} mm · ${volumeNeeded().toFixed(1)} m³`],
      ['Flatness', `±${dev.toFixed(1)} mm`],
      ['Pan / blade passes', `${gs.panPasses.length} / ${gs.bladePasses.length} (${goodPans + goodBlades} in the window)`],
      ['Marks left in it', String(defects)],
      ['Concrete wasted', `${gs.waste.toFixed(1)} m³`],
      ['Truck waiting time', `${Math.round(gs.truckWaitPaid)} €`],
      ['People told to go to hell', String(gs.stats.hell)],
      ['Times on your butt', String(gs.stats.falls)],
    ];
    $('#eStats').innerHTML = rows.map(([k, v]) => `<span>${k}</span><b>${v}</b>`).join('');
    const hour = (gs.t % 1440) / 60;
    const home = gs.t >= 1440 ? `You get home at ${clock(gs.t)}. The cat has moved into your side of the bed.`
      : hour >= 21 ? `Home at ${clock(gs.t)}. Dinner is cold, like your trowel.`
      : hour >= 18 ? `Home at ${clock(gs.t)}. Just in time to fall asleep during the news.`
      : `Home at ${clock(gs.t)}. Before dinner. Suspicious. The neighbours think you've been fired.`;
    const story = gs.story.slice(-7).concat([home]);
    $('#eStory').innerHTML = story.map((s) => `<li>${s.replace(/</g, '&lt;')}</li>`).join('');
    $('#end').hidden = false;
  }

  // ------------------------------------------------------------------ what the big button does
  function tools() {
    if (gs.phase === 'pour') return ['hose', 'float'];
    if (gs.phase === 'cure') return ['hands', 'float', 'pans', 'blades'];
    return ['hands'];
  }
  const TOOL_NAMES = { hands: 'Hands', hose: 'Hose', float: 'Float', pans: 'Trowel pans', blades: 'Trowel blades' };

  let target = null;          // the cell under the crosshair, in reach
  let nearMarker = null;
  let holdT = 0;
  function context() {
    if (player.fall > 0) return null;
    if (nearMarker) return { kind: 'marker', label: nearMarker.label, hold: nearMarker.hold };
    const t = gs.tool;
    if (gs.phase === 'pour') {
      if (t === 'hose') {
        if (gs.blocked >= 0) return { kind: 'none', label: 'Line blocked' };
        if (!gs.truck || gs.truck.waiting) return { kind: 'none', label: 'No concrete' };
        if (target) return { kind: 'pour', label: 'Hold: pour' };
        return { kind: 'none', label: 'Aim at the slab' };
      }
      if (t === 'float') {
        if (!gs.prep.unload) return { kind: 'none', label: 'Floats are in the van' };
        if (target && target.fill > 5) return { kind: 'level', label: 'Hold: float' };
        return { kind: 'none', label: 'Aim at concrete' };
      }
    }
    if (gs.phase === 'cure') {
      if (t === 'float' && target) return { kind: 'repair', label: target.marks.length ? 'Hold: float out marks' : 'Hold: float' };
      if ((t === 'pans' || t === 'blades') && onSlab(player.x, player.z)) return { kind: 'trowel', label: `Hold: ${t === 'pans' ? 'pan' : 'blade'} pass` };
      if (t === 'pans' || t === 'blades') return { kind: 'none', label: 'Walk onto the slab' };
      if (t === 'hands' && target) return { kind: 'thumb', label: 'Thumb test' };
    }
    return { kind: 'none', label: '—' };
  }

  function doAction(ctx, dt) {
    if (!ctx) return;
    if (ctx.kind === 'marker') {
      holdT += dt;
      if (holdT >= nearMarker.hold) {
        holdT = 0;
        const m = nearMarker;
        const ok = m.done();
        if (ok !== false && !m.active()) m.group.visible = false;
      }
      return;
    }
    if (ctx.kind === 'pour') pourTick(dt);
    else if (ctx.kind === 'level') levelTick(target, dt);
    else if (ctx.kind === 'repair') repairTick(target, dt);
    else if (ctx.kind === 'trowel') trowelTick(dt);
    else if (ctx.kind === 'thumb' && input.actionTapped) thumb(target);
  }

  let pourSeconds = 0;
  function pourTick(dt) {
    if (!target || !gs.truck || gs.truck.left <= 0) return;
    const speed = gs.mixState === 'stiff' ? 90 : gs.mixState === 'soup' ? 125 : 110;
    const add = speed * dt;
    const nb = neighbours(target);
    const spread = gs.mixState === 'soup' ? 0.4 : 0.25;
    target.fill += add * (1 - spread);
    nb.forEach((n) => { n.fill += (add * spread) / nb.length; });
    const m3 = add / 1000;
    gs.truck.left -= m3;
    gs.pouredM3 += m3;
    cellsDirty = true;
    pourSeconds += dt;
    const progress = filledShare();
    // what goes wrong while pouring
    if (gs.mixState === 'stiff' && chance(0.025 * dt)) {
      gs.blocked = irnd(1, 5);
      gs.stats.blockages++;
      toast(pick(L.blocked), 'warn');
      blockMarker();
    }
    if (day.formworkWeak && !gs.blowoutDone && !gs.blowout && progress > 0.35 && (!gs.prep.formNW || !gs.prep.formSE)) startBlowout();
    if (day.batteryDies && gs.prep.laser && !gs.batteryDone && progress > 0.45) {
      gs.batteryDone = true;
      gs.laserBattery = false;
      gs.laserOn = false;
      toast(L.battery, 'warn');
    }
    if (gs.truck.left <= 0) truckEmpty();
  }
  function blockMarker() {
    const k = gs.blocked;
    const a = k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], b = PIPE_ROUTE[k];
    const m = addMarker('block', { x: (a.x + b.x) / 2, z: (a.z + b.z) / 2 }, 'Hit the pipe!', 2.4, () => gs.blocked >= 0, () => {
      gs.blocked = -1;
      toast(pick(L.unblocked), 'good');
      removeMarker(m);
    }, { w: 2.2 });
  }
  function startBlowout() {
    const sides = [];
    if (!gs.prep.formNW) sides.push({ name: 'north side', p: P(-2, -4.8), cells: (c) => c.j === 0 }, { name: 'west side', p: P(-6.8, 1), cells: (c) => c.i === 0 });
    if (!gs.prep.formSE) sides.push({ name: 'south side', p: P(2, 4.8), cells: (c) => c.j === NZ - 1 }, { name: 'east side', p: P(6.8, -1), cells: (c) => c.i === NX - 1 });
    const s = pick(sides);
    gs.blowout = s;
    toast(L.blowout.replace('{side}', s.name), 'warn');
    remember(`The formwork burst on the ${s.name}.`);
    const m = addMarker('blowout', s.p, 'Fix the formwork!', 2.8, () => !!gs.blowout, () => {
      gs.blowout = null;
      gs.blowoutDone = true;
      toast('Stakes, a board and a lot of swearing. It holds.', 'good');
      removeMarker(m);
    }, { w: 2.6 });
  }
  function levelTick(c, dt) {
    const rate = 55 * dt * (gs.mixState === 'soup' ? 1.5 : gs.mixState === 'stiff' ? 0.65 : 1);
    const diff = c.fill - day.thick;
    const nb = neighbours(c);
    if (diff > 0.3) {
      const low = nb.reduce((a, b) => (b.fill < a.fill ? b : a));
      if (low.fill < c.fill) { const amt = Math.min(rate, diff, (c.fill - low.fill) / 2); c.fill -= amt; low.fill += amt; }
    } else if (diff < -0.3) {
      const high = nb.reduce((a, b) => (b.fill > a.fill ? b : a));
      if (high.fill > c.fill) { const amt = Math.min(rate, -diff, (high.fill - c.fill) / 2); c.fill += amt; high.fill -= amt; }
    }
    cellsDirty = true;
  }
  let repairT = 0;
  function repairTick(c, dt) {
    if (!c.marks.length) return;
    if (gs.H >= 50) { if (input.actionTapped) toast('Too hard to float that out now. It\'s part of the story.', 'warn'); return; }
    repairT += dt;
    if (repairT >= 0.9) {
      repairT = 0;
      clearMarks(c);
      c.defect = false;
      gs.stats.repaired++;
      cellsDirty = true;
      toast('Floated out. Nobody will ever know. Except you. Forever.', 'good');
    }
  }
  function trowelTick() {
    const key = gs.tool === 'pans' ? 'covP' : 'covB';
    if (gs.tool === 'pans' && gs.H < 18) { if (input.actionTapped) toast(L.tooSoftMachine, 'warn'); return; }
    if (gs.tool === 'blades' && gs.H < 40) { if (input.actionTapped) toast(L.tooSoftBlades, 'warn'); return; }
    if (gs.tool === 'pans' && gs.panPasses.length >= 3 && input.actionTapped) toast('Three pan passes is plenty. Now you\'re just polishing the pans.');
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const mx = player.x + fx * 1.1, mz = player.z + fz * 1.1;
    let changed = false;
    for (const c of gs.cells) {
      if (c[key]) continue;
      const cx = SLAB.x0 + c.i + 0.5, cz = SLAB.z0 + c.j + 0.5;
      if (hyp(cx, cz, mx, mz) < 1.0) { c[key] = true; changed = true; }
    }
    if (changed && passCoverage(key) >= 0.9) {
      if (gs.tool === 'pans' && gs.panPasses.length >= 3) { gs.cells.forEach((c) => { c.covP = false; }); return; }
      if (gs.tool === 'blades' && gs.bladePasses.length >= 3) { gs.cells.forEach((c) => { c.covB = false; }); toast('Three blade passes. It shines like a bowling alley. Stop.'); return; }
      completePass(gs.tool === 'pans' ? 'pan' : 'blade');
    }
  }
  function thumb(c) {
    if (!gs.poured) { toast('It\'s still a building site, not a slab. Nothing to test.'); return; }
    const line = L.thumb.find(([h]) => gs.H < h)[1];
    toast(`${Math.floor(gs.H)}% · ${line}`);
    if (gs.H < 30) stamp('thumb', SLAB.x0 + c.i + 0.5 + rnd(-0.3, 0.3), SLAB.z0 + c.j + 0.5 + rnd(-0.3, 0.3), 0);
  }

  // ------------------------------------------------------------------ controls
  const touchLayer = $('#touch');
  const joy = $('#joy'), joyKnob = $('#joyKnob');
  let joyId = null, lookId = null, joyCx = 0, joyCy = 0, lookX = 0, lookY = 0;
  touchLayer.addEventListener('pointerdown', (e) => {
    if (gs.phase === 'title' || gs.phase === 'end' || modalOpen) return;
    try { touchLayer.setPointerCapture(e.pointerId); } catch (err) { /* not every pointer can be captured */ }
    if (e.pointerType !== 'mouse' && e.clientX < window.innerWidth * 0.45 && joyId === null) {
      joyId = e.pointerId; joyCx = e.clientX; joyCy = e.clientY;
      joy.style.left = joyCx + 'px'; joy.style.top = joyCy + 'px'; joy.hidden = false;
      joyKnob.style.transform = 'translate(0,0)';
    } else if (lookId === null) {
      lookId = e.pointerId; lookX = e.clientX; lookY = e.clientY;
    }
  });
  touchLayer.addEventListener('pointermove', (e) => {
    if (e.pointerId === joyId) {
      let dx = e.clientX - joyCx, dy = e.clientY - joyCy;
      const len = Math.hypot(dx, dy), max = 52;
      if (len > max) { dx = (dx / len) * max; dy = (dy / len) * max; }
      input.jx = dx / max; input.jy = dy / max;
      joyKnob.style.transform = `translate(${dx}px, ${dy}px)`;
    } else if (e.pointerId === lookId) {
      const sens = e.pointerType === 'mouse' ? 0.0045 : 0.0058;
      player.yaw -= (e.clientX - lookX) * sens;
      player.pitch = clamp(player.pitch - (e.clientY - lookY) * sens, -1.35, 1.1);
      lookX = e.clientX; lookY = e.clientY;
    }
  });
  const release = (e) => {
    if (e.pointerId === joyId) { joyId = null; input.jx = 0; input.jy = 0; joy.hidden = true; }
    if (e.pointerId === lookId) lookId = null;
  };
  touchLayer.addEventListener('pointerup', release);
  touchLayer.addEventListener('pointercancel', release);

  window.addEventListener('keydown', (e) => {
    input.keys[e.code] = true;
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end') return;
    if (e.code === 'KeyE' || e.code === 'Space') { if (!input.action) input.actionTapped = true; input.action = true; e.preventDefault(); }
    if (e.code === 'KeyQ') cycleTool();
    if (e.code === 'KeyL') toggleLaser();
    if (e.code === 'KeyT') waitMenu();
    if (e.code === 'KeyC') coffee();
    if (e.code === 'Escape' || e.code === 'KeyP') pauseMenu();
  });
  window.addEventListener('keyup', (e) => {
    input.keys[e.code] = false;
    if (e.code === 'KeyE' || e.code === 'Space') input.action = false;
  });

  const btnAction = $('#btnAction');
  btnAction.addEventListener('pointerdown', (e) => { e.preventDefault(); input.action = true; input.actionTapped = true; btnAction.classList.add('held'); });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => btnAction.addEventListener(ev, () => { input.action = false; btnAction.classList.remove('held'); }));
  $('#btnTool').addEventListener('click', () => cycleTool());
  $('#btnLaser').addEventListener('click', () => toggleLaser());
  $('#btnWait').addEventListener('click', () => waitMenu());
  $('#btnCoffee').addEventListener('click', () => coffee());
  $('#btnFinish').addEventListener('click', () => {
    modal({
      who: 'Finish the pour?', title: `${Math.round(filledShare() * 100)}% filled, ±${rms().toFixed(1)} mm.`,
      text: 'Once it\'s finished there\'s no more floating to the laser — it goes to the pans from here.',
      choices: [{ label: 'Finish the pour', primary: true, fn: () => finishPour() }, { label: 'Keep floating' }],
    });
  });

  function cycleTool() {
    const list = tools();
    if (gs.carrying) { toast('You\'re carrying a pipe. One thing at a time.'); return; }
    const k = list.indexOf(gs.tool);
    gs.tool = list[(k + 1) % list.length];
    toast(`In your hands: ${TOOL_NAMES[gs.tool]}.`);
  }
  function toggleLaser() {
    if (!gs.prep.laser) { toast('The laser isn\'t set up. Tripod by the west edge — and the tools out of the van first.', 'warn'); return; }
    if (!gs.laserBattery) { toast('Dead batteries. The spares are in the van.', 'warn'); return; }
    if (gs.pourDone) { toast('The pour is done. It\'s the pans that flatten it now.'); return; }
    gs.laserOn = !gs.laserOn;
    cellsDirty = true;
    toast(gs.laserOn ? 'Laser receiver on: green is on height, red is high, blue is low.' : 'Laser receiver off. Eyeballing it. Bold.');
  }
  function coffee() {
    if (gs.cups <= 0) { toast(L.noCoffee, 'warn'); return; }
    gs.cups--;
    gs.stats.coffee++;
    gs.energy = clamp(gs.energy + 15, 0, 100);
    simulate(2, awayNow());
    viewTools.cup.visible = true;
    setTimeout(() => { viewTools.cup.visible = false; }, 1600);
    toast(`${pick(L.coffee)} (${gs.cups} left)`);
  }
  function showWait() {
    const b = $('#waitBadge');
    if (!gs.waitMode && !gs.fastForward) { b.hidden = true; return; }
    b.hidden = false;
    b.innerHTML = gs.waitMode === 'van' ? 'In the van · time flies<small>Tap Wait to get out</small>'
      : gs.waitMode === 'guard' ? 'Guarding the slab · time flies<small>Move or tap Wait to stop</small>'
      : 'Waiting · time flies<small>Move to stop</small>';
  }
  function waitMenu() {
    if (gs.waitMode || gs.fastForward) {
      if (gs.waitMode === 'van') { player.x = POS.vanDoor.x; player.z = POS.vanDoor.z; toast(pick(L.vanNap)); }
      gs.waitMode = null; gs.fastForward = null; showWait();
      return;
    }
    const choices = [];
    if ((gs.phase === 'prep' || gs.phase === 'pipes') && !gs.pumpHere) choices.push({ label: `Wait for the pump (due ${clock(gs.pumpAt)})`, primary: true, fn: () => { gs.fastForward = 'pump'; showWait(); } });
    if ((gs.phase === 'pipes' || gs.phase === 'pour') && gs.pipes === 6 && !gs.truck && gs.nextTruckAt > gs.t) choices.push({ label: `Wait for the truck (due ${clock(gs.nextTruckAt)})`, primary: true, fn: () => { gs.fastForward = 'truck'; showWait(); } });
    if (gs.phase === 'cure' || gs.phase === 'wash') {
      if (hyp(player.x, player.z, 0, 0) < 16) choices.push({ label: 'Stand guard by the slab', primary: true, fn: () => { gs.waitMode = 'guard'; showWait(); } });
      choices.push({ label: 'Nap in the van (fastest, but nobody guards the slab)', fn: () => { gs.waitMode = 'van'; showWait(); } });
    }
    if (!choices.length) { toast('Nothing to wait for. There is always something to do. That\'s the job.'); return; }
    choices.push({ label: 'Never mind' });
    modal({ who: 'Wait', title: 'Let time do its thing.', text: gs.poured ? `Hardness ${Math.floor(gs.H)}%. Pans in ${dur(etaTo(25))}, blades in ${dur(etaTo(55))}, 95% in ${dur(etaTo(95))}.` : '', choices });
  }

  // ------------------------------------------------------------------ per frame
  const tmpV = new THREE.Vector3(), tmpV2 = new THREE.Vector3();
  let stuckMsg = 0;
  function updatePlayer(dt) {
    let mx = input.jx, my = input.jy;
    if (input.keys.KeyW || input.keys.ArrowUp) my -= 1;
    if (input.keys.KeyS || input.keys.ArrowDown) my += 1;
    if (input.keys.KeyA || input.keys.ArrowLeft) mx -= 1;
    if (input.keys.KeyD || input.keys.ArrowRight) mx += 1;
    const len = Math.hypot(mx, my);
    if (len > 1) { mx /= len; my /= len; }
    const wants = len > 0.08;
    if (wants && (gs.waitMode === 'guard' || gs.fastForward)) { gs.waitMode = null; gs.fastForward = null; showWait(); }
    if (gs.waitMode === 'van') { player.x = POS.van.x + 0.6; player.z = POS.van.z - 0.2; player.moving = false; return; }
    if (player.fall > 0 || performance.now() < gs.stuckUntil) { player.moving = false; return; }
    const c = cellAt(player.x, player.z);
    const wet = gs.phase === 'pour' && c && c.fill > 20;
    let speed = 4.2;
    if (wet) speed = 1.7;
    if (gs.phase === 'cure' && (gs.tool === 'pans' || gs.tool === 'blades') && c) speed = 1.4;
    if (gs.carrying) speed *= 0.7;
    if (gs.energy < 20) speed *= 0.8;
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
    const vx = (fx * -my + rx * mx) * speed, vz = (fz * -my + rz * mx) * speed;
    const [nx, nz] = collide(player.x + vx * dt, player.z + vz * dt);
    const moved = hyp(nx, nz, player.x, player.z);
    player.x = nx; player.z = nz;
    player.moving = moved > 0.001;
    player.bob += moved * 2.6;
    // walking on it
    const now = cellAt(player.x, player.z);
    if (now && moved > 0) {
      player.stepAcc += moved;
      if (gs.phase === 'pour' && gs.pourStarted && now.fill > 30) {
        if (!gs.fellInPour && filledShare() > 0.2 && chance(0.0015)) { gs.fellInPour = true; fall(); }
        else if (chance(0.0025) && performance.now() > stuckMsg) { gs.stuckUntil = performance.now() + 2600; stuckMsg = performance.now() + 60000; toast(pick(L.stuck)); }
      }
      if (gs.poured && gs.H < 30 && !(gs.tool === 'pans' || gs.tool === 'blades') && player.stepAcc > 0.75) {
        player.stepAcc = 0;
        if (stamp('boot', player.x, player.z, player.yaw)) {
          gs.stats.own++;
          if (gs.stats.own === 1) toast('You are leaving footprints in your own slab. The dog is laughing at you.', 'warn');
        }
      }
      if (gs.poured && (gs.tool === 'pans' || gs.tool === 'blades') && gs.H < 25 && chance(0.004)) fall('The machine sinks, twists, and throws you on your butt. Told you it was early.');
      if (gs.energy < 18 && chance(0.003)) fall('Your legs file for early retirement. Down you go.');
    }
  }

  function updateTarget() {
    camera.getWorldDirection(tmpV);
    const o = camera.getWorldPosition(tmpV2);
    target = null;
    if (tmpV.y < -0.02) {
      const yPlane = gs.pourStarted ? (day.thick * VIS) / 1000 : 0.02;
      const t = (yPlane - o.y) / tmpV.y;
      const x = o.x + tmpV.x * t, z = o.z + tmpV.z * t;
      if (onSlab(x, z) && hyp(x, z, player.x, player.z) < REACH) { target = cellAt(x, z); target._hx = x; target._hz = z; }
    }
    nearMarker = null;
    let best = 2.0;
    for (const m of markers) {
      if (!m.active()) continue;
      const d = hyp(m.x, m.z, player.x, player.z);
      if (d < best) { best = d; nearMarker = m; }
    }
  }

  let lastCtxKind = '';
  const btnState = { idle: null, label: null, fill: null };
  const actLabel = $('#actLabel'), actFill = $('#actFill');
  function updateAction(dt) {
    updateTarget();
    const ctx = context();
    if (!ctx || ctx.kind !== lastCtxKind) holdT = 0;
    lastCtxKind = ctx ? ctx.kind : '';
    if (input.action && ctx && ctx.kind !== 'none') {
      if (gs.waitMode === 'guard') { gs.waitMode = null; showWait(); }
      doAction(ctx, dt);
    } else {
      holdT = 0; repairT = 0;
      if (input.actionTapped && ctx && ctx.kind === 'none' && ctx.label !== '—') toast(ctx.label + '.');
    }
    if (ctx && ctx.kind === 'pour' && input.action && target && gs.truck && gs.truck.left > 0) {
      stream.visible = true;
      const nozzle = new THREE.Vector3(0.2, -0.45, -1.2);
      camera.localToWorld(nozzle);
      stretch(stream, nozzle, new THREE.Vector3(target._hx, (target.fill * VIS) / 1000, target._hz));
    } else stream.visible = false;
    input.actionTapped = false;
    // the hand-held end hose, from the last pipe to you
    if (gs.phase === 'pour' && gs.tool === 'hose') {
      const hand = new THREE.Vector3(0.36, -0.75, -0.35);
      camera.localToWorld(hand);
      const last = PIPE_ROUTE[5];
      endHose.visible = true;
      stretch(endHose, new THREE.Vector3(last.x, 0.15, last.z), hand);
    } else endHose.visible = false;
    // HUD button
    const idle = !ctx || ctx.kind === 'none';
    const label = ctx ? (ctx.kind === 'marker' ? `Hold: ${ctx.label}` : ctx.label) : '—';
    const fill = ctx && ctx.kind === 'marker' ? `${Math.min(100, (holdT / ctx.hold) * 100)}%` : '0%';
    if (idle !== btnState.idle) { btnAction.classList.toggle('idle', idle); btnState.idle = idle; }
    if (label !== btnState.label) { actLabel.textContent = label; btnState.label = label; }
    if (fill !== btnState.fill) { actFill.style.width = fill; btnState.fill = fill; }
  }

  function updateWalkers(dt) {
    for (let k = walkers.length - 1; k >= 0; k--) {
      const w = walkers[k];
      const a = w.path[w.seg], b = w.path[w.seg + 1];
      if (!b) { scene.remove(w.m); walkers.splice(k, 1); if (w.onDone) w.onDone(); continue; }
      const len = hyp(a.x, a.z, b.x, b.z) || 0.001;
      w.acc += (w.speed * dt) / len;
      if (w.acc >= 1) { w.acc = 0; w.seg++; continue; }
      const x = lerp(a.x, b.x, w.acc), z = lerp(a.z, b.z, w.acc);
      const moved = hyp(x, z, w.m.position.x, w.m.position.z);
      w.m.position.set(x, 0, z);
      w.m.rotation.y = -Math.atan2(b.z - a.z, b.x - a.x) + (w.kind === 'dog' ? 0 : Math.PI / 2);
      const u = w.m.userData;
      u.phase += moved * (w.kind === 'dog' ? 9 : 4.5);
      if (w.kind === 'dog') {
        u.legs.forEach((l, n) => { l.rotation.z = Math.sin(u.phase + (n % 2) * Math.PI) * 0.6; });
        u.tail.rotation.x = Math.sin(u.phase * 2) * 0.6;
      } else {
        u.legL.rotation.x = Math.sin(u.phase) * 0.5; u.legR.rotation.x = -Math.sin(u.phase) * 0.5;
        u.armL.rotation.x = -Math.sin(u.phase) * 0.4; u.armR.rotation.x = Math.sin(u.phase) * 0.4;
      }
      w.dist = (w.dist || 0) + moved;
      const spacing = w.kind === 'dog' ? 0.42 : 0.72;
      if (w.dist > spacing) { w.dist = 0; stamp(w.kind === 'dog' ? 'paw' : 'boot', x, z, headingOf(b.x - a.x, b.z - a.z)); }
    }
  }

  function updateWorld(dt) {
    for (let k = drives.length - 1; k >= 0; k--) {
      const d = drives[k];
      d.t += dt / d.seconds;
      const e = 1 - Math.pow(1 - Math.min(1, d.t), 3);
      d.group.position.x = lerp(d.from, d.to, e);
      if (d.t >= 1) { drives.splice(k, 1); if (d.done) d.done(); }
    }
    updateWalkers(dt);
    if (mixer.visible) drum.rotation.y += dt * (gs.truck && !gs.truck.waiting ? 2.2 : 0.6);
    if (tripod.visible) laserHead.rotation.y += dt * 6;
    beam.visible = gs.laserOn && laserWorks();
    // blowout drains the edge
    if (gs.blowout && gs.phase === 'pour') {
      gs.cells.forEach((c) => { if (gs.blowout.cells(c) && c.fill > 0) { c.fill = Math.max(0, c.fill - 14 * dt); } });
      cellsDirty = true;
    }
    // soup levels itself, slowly
    if (gs.phase === 'pour' && (gs.mixState === 'soup' || gs.water)) {
      const k = (gs.mixState === 'soup' ? 0.35 : 0.12) * dt;
      for (const c of gs.cells) {
        if (c.i < NX - 1) { const r = gs.cells[c.idx + 1]; const f = (c.fill - r.fill) * k; c.fill -= f; r.fill += f; }
        if (c.j < NZ - 1) { const d = gs.cells[c.idx + NX]; const f = (c.fill - d.fill) * k; c.fill -= f; d.fill += f; }
      }
      cellsDirty = true;
    }
    // too tired to stand
    if (gs.energy < 8 && gs.phase !== 'end' && gs.t - gs.lastSleep > 120 && !modalOpen) {
      gs.lastSleep = gs.t;
      const m = irnd(20, 35);
      simulate(m, true);
      gs.energy = clamp(gs.energy + 20, 0, 100);
      toast(L.sleepy.replace('{m}', m), 'warn');
      remember('You fell asleep standing up.');
    }
    if (gs.fastForward === 'pump' && gs.pumpHere) { gs.fastForward = null; showWait(); }
    if (gs.fastForward === 'truck' && gs.truck) { gs.fastForward = null; showWait(); }
  }

  // light and sky by the clock
  const SKY = [[0, 0x0a0f1c], [300, 0x101a30], [360, 0xd88f64], [450, 0xa7c6e2], [720, 0x8fbde6], [1050, 0xa8c2da], [1140, 0xe08e5a], [1230, 0x1a2140], [1440, 0x0a0f1c]];
  const cA = new THREE.Color(), cB = new THREE.Color(), grey = new THREE.Color(0x8e959c);
  function skyAt(t) {
    const m = ((t % 1440) + 1440) % 1440;
    for (let k = 0; k < SKY.length - 1; k++) {
      if (m >= SKY[k][0] && m <= SKY[k + 1][0]) {
        const f = (m - SKY[k][0]) / (SKY[k + 1][0] - SKY[k][0]);
        return cA.setHex(SKY[k][1]).lerp(cB.setHex(SKY[k + 1][1]), f);
      }
    }
    return cA.setHex(SKY[0][1]);
  }
  function light(t) {
    const sky = skyAt(t).clone();
    if (day && day.rh > 78) sky.lerp(grey, 0.35 * (day.rh - 78) / 17);
    scene.background = sky;
    scene.fog.color.copy(sky);
    const m = ((t % 1440) + 1440) % 1440;
    const dayness = clamp(Math.sin(Math.PI * (m - 330) / 840), 0, 1);
    hemi.intensity = 0.28 + 0.62 * dayness;
    sun.intensity = 0.95 * dayness;
    const ang = Math.PI * (m - 330) / 840;
    sun.position.set(Math.cos(ang) * 60, Math.max(8, Math.sin(ang) * 70), 25);
    const dark = dayness < 0.25;
    flood.intensity = dark ? 1.4 : 0;
    lamp.material.color.setHex(dark ? 0xfff3d6 : 0x777777);
  }

  function placeCamera(dt) {
    let y = EYE;
    const c = cellAt(player.x, player.z);
    if (c && gs.phase !== 'pour') y += surfaceY(c) * 0.9;
    if (c && gs.phase === 'pour') y += surfaceY(c) * 0.3;
    let roll = 0;
    if (player.fall > 0) {
      player.fall -= dt;
      const f = player.fall > 1.2 ? (1.8 - player.fall) / 0.6 : player.fall > 0.4 ? 1 : player.fall / 0.4;
      y -= 1.2 * f;
      roll = 0.5 * f;
    }
    if (gs.waitMode === 'van') y = 1.55;
    const bob = CALM ? 0 : Math.sin(player.bob) * 0.035;
    camera.position.set(player.x, y + (player.moving ? bob : 0), player.z);
    camera.rotation.set(player.pitch, player.yaw, roll);
    // tools in hand
    const t = gs.tool;
    viewTools.hose.visible = t === 'hose' && !gs.carrying;
    viewTools.float.visible = t === 'float';
    viewTools.machine.visible = t === 'pans' || t === 'blades';
    viewTools.pipe.visible = gs.carrying;
    if (viewTools.machine.visible) {
      viewTools.machineDisc.material = lam(t === 'pans' ? 0x8a8f94 : 0x3d4247);
      if (input.action) viewTools.machineDisc.rotation.y += dt * 18;
    }
  }

  // ------------------------------------------------------------------ HUD
  let hudT = 0;
  const mapCtx = $('#map').getContext('2d');
  function objective() {
    const nextPrep = () => {
      const p = gs.prep, left = [];
      if (!p.unload) left.push('unload the tools');
      if (!p.formNW || !p.formSE) left.push('check the formwork');
      if (!p.laser) left.push('set up the laser');
      if (!p.chairs) left.push('fix the mesh chairs');
      return left;
    };
    switch (gs.phase) {
      case 'prep': {
        const left = nextPrep();
        const due = `Pump due ${clock(gs.pumpAt)}`;
        return left.length ? `Before the pump: ${left.join(', ')}.<small>${due}</small>` : `Prep done. Coffee, or wait for the pump.<small>${due}</small>`;
      }
      case 'pipes':
        if (gs.pipes < 6) return (gs.carrying ? `Carry the pipe to marker ${gs.pipes + 1}.` : `Grab a pipe from the pile (${gs.pipes}/6 laid).`) + (nextPrep().length ? `<small>Still open: ${nextPrep().join(', ')}</small>` : '');
        return `Line laid. Waiting for the mixer.<small>Truck due ${clock(gs.nextTruckAt)}</small>`;
      case 'pour': {
        if (gs.blocked >= 0) return 'The line is blocked. Find the orange marker and hit the pipe!';
        if (gs.blowout) return `The formwork burst on the ${gs.blowout.name}. Fix it before you lose more!`;
        if (!laserWorks() && gs.prep.laser) return 'Laser batteries are dead. Spares are in the van.';
        const f = Math.round(filledShare() * 100);
        const sub = gs.truck && !gs.truck.waiting ? `Hose: pour · Float: level · Laser shows the height` : `Next truck due ${clock(gs.nextTruckAt)}. Float what you have.`;
        return `Pour to ${day.thick} mm: ${f}% filled, ±${rms().toFixed(1)} mm.<small>${sub}</small>`;
      }
      case 'wash': return 'Wash your tools at the water tank before they set.';
      case 'cure': {
        const marks = gs.cells.filter((c) => c.marks.length && !c.defect).length;
        const warn = marks && gs.H < 50 ? `<small>${marks} m² with marks — float them out before 50%.</small>` : '';
        if (gs.H < 25) return `Let it harden. Guard it, eat, or nap.${warn || '<small>Pans from 25%.</small>'}`;
        if (!gs.panPasses.length) return `Pans on (Tool → Trowel pans). Hold the button and walk the whole slab.${warn}`;
        if (gs.edgesDone < EDGE_SPOTS.length) return `Hand trowel edges, corners and collars (${gs.edgesDone}/${EDGE_SPOTS.length}).${warn || '<small>More pan passes flatten it; blades from 55%.</small>'}`;
        if (!gs.bladePasses.length) return gs.H < 55 ? `Wait for blades (55%).<small>Another pan pass never hurt anyone.</small>` : 'Blades on (Tool → Trowel blades). Walk the whole slab.';
        if (gs.H < 95) return `Wait for 95%, then go home.<small>${dur(etaTo(95))} to go. Another blade pass shines it up.</small>`;
        return 'It\'s 95%. Walk to the van and go home.';
      }
      default: return '';
    }
  }
  function updateHUD(dt) {
    hudT -= dt;
    if (hudT > 0) return;
    hudT = 0.12;
    $('#clock').textContent = clock(gs.t);
    $('#phase').textContent = { morning: 'Morning', prep: 'Prep', pipes: 'Pipes', pour: 'Pour', wash: 'Wash up', cure: gs.H >= 25 ? 'Trowel' : 'Curing', end: 'Home' }[gs.phase] || '';
    $('#objective').innerHTML = objective();
    const T = tempAt(gs.t);
    $('#weather').innerHTML = `${T.toFixed(1)} °C <span>·</span> ${day.rh}% RH <span>·</span> wind ${day.wind}<br><span>Slab</span> ${day.thick} mm <span>· energy</span>` +
      `<div id="energyRow"><div id="energyBar"><div id="energyFill" style="width:${gs.energy}%;background:${gs.energy < 25 ? '#ff3b30' : gs.energy < 50 ? '#ffd23f' : '#6bd68a'}"></div></div><span>${gs.cups} cups</span></div>`;
    const hard = $('#hard');
    hard.hidden = !gs.poured;
    if (gs.poured) {
      $('#hardPct').textContent = `${Math.floor(gs.H)}%`;
      $('#hardFill').style.width = `${gs.H}%`;
      const eta = gs.H < 25 ? `Pans in <b>${dur(etaTo(25))}</b>` : gs.H < 55 ? `Blades in <b>${dur(etaTo(55))}</b>` : gs.H < 95 ? `95% in <b>${dur(etaTo(95))}</b>` : '<b>Hard enough to leave</b>';
      const passes = gs.phase === 'cure' ? `<br>Pans ${gs.panPasses.length} · blades ${gs.bladePasses.length} · edges ${gs.edgesDone}/${EDGE_SPOTS.length}` : '';
      const cov = gs.tool === 'pans' || gs.tool === 'blades' ? `<br>This pass <b>${Math.round(passCoverage(gs.tool === 'pans' ? 'covP' : 'covB') * 100)}%</b>` : '';
      $('#hardEta').innerHTML = eta + passes + cov;
    }
    const ti = $('#truckInfo');
    ti.hidden = !(gs.phase === 'pour' || (gs.phase === 'pipes' && gs.pipes === 6));
    if (!ti.hidden) {
      const need = gs.cells.reduce((sum, c) => sum + Math.max(0, day.thick - c.fill), 0) / 1000;
      ti.innerHTML = gs.truck ? `Truck ${gs.truck.no}: <b>${Math.max(0, gs.truck.left).toFixed(1)} m³</b> left<br><span style="color:#aeb2b8">low spots need ≈ ${need.toFixed(1)} m³</span>`
        : `Next truck <b>${clock(gs.nextTruckAt || gs.t)}</b><br><span style="color:#aeb2b8">low spots need ≈ ${need.toFixed(1)} m³</span>`;
    }
    $('#btnFinish').hidden = !(gs.phase === 'pour' && filledShare() >= 0.97);
    $('#btnTool').textContent = TOOL_NAMES[gs.tool];
    $('#btnLaser').classList.toggle('on', gs.laserOn);
    $('#btnWait').textContent = gs.waitMode || gs.fastForward ? 'Stop' : 'Wait';
    $('#btnCoffee').textContent = `Coffee ${gs.cups}`;
    // what you're looking at
    const info = $('#targetInfo');
    if (target && gs.pourStarted) {
      const d = target.fill - day.thick;
      const name = `${String.fromCharCode(65 + target.i)}${target.j + 1}`;
      if (gs.phase === 'pour' && gs.laserOn && laserWorks()) {
        const cls = Math.abs(d) <= 3 ? 'dev-ok' : d > 0 ? 'dev-hi' : 'dev-lo';
        info.innerHTML = `${name} · <span class="${cls}">${d > 0 ? '+' : ''}${d.toFixed(0)} mm</span>`;
      } else if (gs.phase === 'pour') {
        info.textContent = `${name} · ${target.fill < 5 ? 'empty' : 'looks about right?'}`;
      } else {
        info.textContent = `${name}` + (target.marks.length ? ` · ${target.marks.length} mark${target.marks.length > 1 ? 's' : ''}` : '') + (target.defect ? ' · set in' : '');
      }
    } else info.textContent = nearMarker ? nearMarker.label : '';
    drawMap();
  }
  /*
   * A map that follows you, 60 m across and turned so up is straight ahead. Anything off the
   * edge — the kebab stand, a marker behind the van — sits on the rim in its direction.
   */
  function drawMap() {
    const g = mapCtx, W = 300, half = W / 2, span = 60, s = W / span;
    const cos = Math.cos(player.yaw), sin = Math.sin(player.yaw);
    const toMap = (x, z) => {
      const dx = x - player.x, dz = z - player.z;
      // forward (-sin, -cos) goes up the screen, right (cos, -sin) goes right
      const right = dx * cos - dz * sin, fwd = -dx * sin - dz * cos;
      return [half + right * s, half - fwd * s];
    };
    const onRim = (pt) => {
      const dx = pt[0] - half, dy = pt[1] - half, r = Math.hypot(dx, dy), max = half - 10;
      return r > max ? [half + (dx / r) * max, half + (dy / r) * max, true] : [pt[0], pt[1], false];
    };
    g.clearRect(0, 0, W, W);
    g.save();
    g.beginPath(); g.arc(half, half, half - 2, 0, Math.PI * 2); g.clip();
    g.fillStyle = 'rgba(90,80,64,0.55)';
    g.fillRect(0, 0, W, W);
    const poly = (x0, z0, x1, z1, col) => {
      const pts = [toMap(x0, z0), toMap(x1, z0), toMap(x1, z1), toMap(x0, z1)];
      g.fillStyle = col; g.beginPath(); g.moveTo(pts[0][0], pts[0][1]);
      for (let k = 1; k < 4; k++) g.lineTo(pts[k][0], pts[k][1]);
      g.closePath(); g.fill();
    };
    const v = gs.poured ? Math.round(90 + gs.H * 1.2) : 150;
    poly(-6, -4, 6, 4, gs.pourStarted ? `rgb(${v},${v},${v + 4})` : '#8c8577');
    poly(-19.6, 7.8, -14.4, 10.2, '#e9e7e2');
    poly(-12.6, -8.6, -11.4, -7.4, '#cfe3ea');
    poly(-32.6, -24.4, -29.4, -21.6, '#2f6f6a');
    poly(-29, 10.8, -23, 13.2, '#3c6e9e');
    if (pump.visible) poly(pump.position.x - 4.3, pump.position.z - 1.2, pump.position.x + 4.3, pump.position.z + 1.2, '#f2b705');
    if (mixer.visible) poly(mixer.position.x - 4.5, mixer.position.z - 1.2, mixer.position.x + 4.5, mixer.position.z + 1.2, '#ff6b1a');
    g.restore();
    const pulse = 5 + Math.sin(performance.now() / 200) * 1.5;
    g.fillStyle = '#ff6b1a';
    markers.forEach((m) => {
      if (!m.active()) return;
      const [x, y, rim] = onRim(toMap(m.x, m.z));
      g.beginPath(); g.arc(x, y, rim ? 6 : pulse, 0, Math.PI * 2); g.fill();
    });
    g.fillStyle = '#5aa9ff';
    walkers.forEach((w) => { const [x, y] = onRim(toMap(w.m.position.x, w.m.position.z)); g.beginPath(); g.arc(x, y, 6, 0, Math.PI * 2); g.fill(); });
    g.fillStyle = '#f2efe8';
    g.beginPath(); g.moveTo(half, half - 14); g.lineTo(half + 10, half + 10); g.lineTo(half, half + 4); g.lineTo(half - 10, half + 10); g.closePath(); g.fill();
  }

  // ------------------------------------------------------------------ the loop
  let last = performance.now();
  let titleSpin = 0;
  let fpsNow = 0, fpsN = 0, fpsT = 0;
  function frame(now) {
    // Up to a tenth of a second a frame: an older phone at 12 frames a second still plays in real
    // time, and a long stall (the app in the background) doesn't jump the day forward.
    const dt = Math.min(0.1, (now - last) / 1000);
    fpsN++; fpsT += now - last;
    if (fpsT > 1000) { fpsNow = fpsN; fpsN = 0; fpsT = 0; }
    last = now;
    const playing = gs.phase !== 'title' && gs.phase !== 'end';
    if (playing && !modalOpen) {
      updatePlayer(dt);
      updateAction(dt);
      updateWorld(dt);
      simulate(dt * timeScale(), awayNow());
      markers.forEach((m) => {
        const on = m.active();
        m.group.visible = on;
        if (on) m.ring.scale.setScalar(1 + Math.sin(now / 250) * 0.08);
      });
    }
    if (cellsDirty) paintCells();
    light(gs.t);
    if (gs.phase === 'title') {
      titleSpin += dt * 0.12;
      camera.position.set(Math.cos(titleSpin) * 18, 7, Math.sin(titleSpin) * 14);
      camera.lookAt(0, 0, 0);
    } else if (gs.phase !== 'end') {
      placeCamera(dt);
      updateHUD(dt);
    }
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }

  // ------------------------------------------------------------------ title
  function showTitle() {
    gs = freshState();
    day = newDay();
    cellsDirty = true;
    const season = { winter: 'Winter', spring: 'Spring', summer: 'Summer', autumn: 'Autumn' }[day.season];
    $('#dayCard').innerHTML = [
      [`${season}`, `${day.baseTemp.toFixed(0)} °C`],
      ['Humidity', `${day.rh}%`],
      ['Wind', `${day.wind} m/s`],
      ['Slab', `${day.thick} mm · ${volumeNeeded().toFixed(1)} m³`],
    ].map(([k, v]) => `<div>${k}<b>${v}</b></div>`).join('');
    const best = Number(store('pourday.best') || 0);
    $('#bestLine').textContent = best ? `Best shift so far: ${best} points` : 'Pump at seven. Mixer at half past. Probably.';
    $('#title').hidden = false;
    $('#end').hidden = true;
    $('#hud').hidden = true;
  }
  function resetWorld() {
    markers.slice().forEach(removeMarker);
    walkers.slice().forEach((w) => scene.remove(w.m));
    walkers.length = 0;
    drives.length = 0;
    pipeGroup.clear();
    pipeMeshes.length = 0;
    pump.visible = false; mixer.visible = false; pumpGuy.visible = false; pile.visible = false; tripod.visible = false;
    EDGE_SPOTS.forEach((e) => { e.done = false; });
    Object.values(markMeshes).forEach((pool) => {
      const zero = new THREE.Matrix4().makeScale(0, 0, 0);
      for (let k = 0; k < MAX_MARKS; k++) pool.im.setMatrixAt(k, zero);
      pool.im.instanceMatrix.needsUpdate = true;
      pool.free = Array.from({ length: MAX_MARKS }, (_, k) => MAX_MARKS - 1 - k);
    });
    rebar.visible = true;
    player.fall = 0;
  }
  $('#btnStart').addEventListener('click', () => {
    resetWorld();
    const keepDay = day;
    gs = freshState();
    day = keepDay;
    cellsDirty = true;
    buildLateMarkers();
    startDay();
  });
  $('#btnReroll').addEventListener('click', () => showTitle());

  // Inside MixMaster the app hands the page a way out; in a plain browser there isn't one.
  const bridge = window.MixMaster && typeof window.MixMaster.quit === 'function' ? window.MixMaster : null;
  const quit = () => { if (bridge) bridge.quit(); };
  $('#btnQuitTitle').hidden = !bridge;
  $('#btnQuitEnd').hidden = !bridge;
  $('#btnQuitTitle').addEventListener('click', quit);
  $('#btnQuitEnd').addEventListener('click', quit);

  const PHASE_NAMES = { morning: 'the morning', prep: 'prep', pipes: 'the pipes', pour: 'the pour', wash: 'washing up', cure: 'curing' };
  function howToPlay() {
    modal({
      who: 'How to play', title: 'The short version.',
      text: 'Left thumb walks, right thumb looks around.\n\nHold the big button to work: on whatever glows orange nearby, or on the slab under the cross with the tool you carry. Tool swaps it — hose and float in the pour; float, pans and blades later.\n\nLaser colours the slab by height while you pour: green on height, red high, blue low.\n\nWait fast-forwards: for the pump, the truck, or the concrete. The map turns with you; orange dots are jobs.\n\nPans from 25%, blades from 55%, edges and collars by hand, home at 95%.',
      choices: [{ label: 'Back to work', primary: true }],
    });
  }
  function pauseMenu() {
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end') return;
    const choices = [
      { label: 'Resume', primary: true },
      { label: 'How to play', fn: () => howToPlay() },
      { label: 'Start a new day', fn: () => modal({
        who: 'New day', title: 'Walk off this one?', text: 'This slab stays how it is. The foreman will hear about it.',
        choices: [{ label: 'New day', danger: true, fn: () => { resetWorld(); showTitle(); } }, { label: 'Keep going', primary: true }],
      }) },
    ];
    if (bridge) choices.push({ label: 'Back to MixMaster', danger: true, fn: quit });
    modal({
      who: 'Paused', title: 'Take five.',
      text: `${clock(gs.t)}, ${PHASE_NAMES[gs.phase] || 'on site'}.` + (gs.poured ? ` Hardness ${Math.floor(gs.H)}%.` : '') + ' Time stops while you\'re here. The concrete will pretend it did too.',
      choices,
    });
  }
  $('#btnMenu').addEventListener('click', () => pauseMenu());
  /** The phone's back: pauses a shift under way, leaves from the title and end screens. */
  window.pdBack = () => {
    if (gs.phase === 'title' || gs.phase === 'end') return 'quit';
    if (!modalOpen) pauseMenu();
    return 'paused';
  };
  $('#btnAgain').addEventListener('click', () => { resetWorld(); showTitle(); });

  if (document.fonts && document.fonts.load) {
    Promise.all([document.fonts.load('800 20px Manrope'), document.fonts.load('700 20px Manrope')]).catch(() => {}).then(() => {});
  }
  showTitle();
  requestAnimationFrame(frame);

  if (DEBUG) {
    window.__pd = {
      get gs() { return gs; }, get day() { return day; },
      get near() { return nearMarker && nearMarker.id; }, get target() { return target && target.idx; }, get input() { return input; },
      get fps() { return fpsNow; }, context: () => context(),
      markers, player, simulate, finishPour, tryGoHome, completePass, nuisance, fall,
      doMarker(id) { const m = markers.find((x) => x.id === id && x.active()); if (m) { m.done(); return true; } return false; },
      fillLevel() { gs.cells.forEach((c) => { c.fill = day.thick + rnd(-3, 3); }); cellsDirty = true; },
      setTool(t) { gs.tool = t; },
      closeModal() { const b = document.querySelector('#mChoices button'); if (b) b.click(); },
      modalOpen() { return !!modalOpen; },
      pourSome(sec) { for (let k = 0; k < sec * 30; k++) { target = gs.cells[k % gs.cells.length]; target._hx = 0; target._hz = 0; pourTick(1 / 30); } },
      sweep(kind) { gs.tool = kind; for (const c of gs.cells) { player.x = SLAB.x0 + c.i + 0.5; player.z = SLAB.z0 + c.j + 0.5 + 1.1; player.yaw = 0; trowelTick(); } },
    };
  }
})();

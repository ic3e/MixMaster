/*
 * Car Mech — the garage in 3D: the bay, the anti-grav lift, the neon, the city in the rain outside,
 * the car on the lift and its insides when the scanner sees through it.
 *
 * Only the neon glows: a second, small render draws everything that isn't a light in black, blurs
 * what's left and adds it over the picture (CMScene.render). Bloom over the whole frame would have
 * made the paint and the floor glow too, and washed the night out.
 */
(function () {
  'use strict';
  const T = window.THREE;
  const S = {};
  window.CMScene = S;

  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const rnd = (a, b) => a + Math.random() * (b - a);
  const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
  const easeOut = (t) => 1 - Math.pow(1 - t, 3);
  const easeIn = (t) => t * t * t;

  let renderer, scene, camera, canvas;
  let W = 1, H = 1, dpr = 1;
  let time = 0;
  // 2: full bloom, 1: bloom at a lower resolution, 0: no bloom (a phone that can't keep up)
  let quality = 2;

  // ---------------------------------------------------------------- tweens
  const tweens = [];
  function tween(dur, fn, done) {
    const tw = { t: 0, dur: Math.max(0.001, dur), fn, done, dead: false };
    tweens.push(tw);
    fn(0);
    return tw;
  }
  function stepTweens(dt) {
    for (let i = tweens.length - 1; i >= 0; i--) {
      const tw = tweens[i];
      if (tw.dead) { tweens.splice(i, 1); continue; }
      tw.t += dt;
      const k = Math.min(1, tw.t / tw.dur);
      // one broken animation must never stop every frame after it
      try { tw.fn(k); } catch (e) { tw.dead = true; console.error(e); continue; }
      if (tw.dead) continue;
      if (k >= 1) {
        tweens.splice(i, 1);
        if (tw.done) tw.done();
      }
    }
  }

  // ---------------------------------------------------------------- textures drawn on the spot
  function canvasTex(w, h, draw, srgb = true) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    draw(c.getContext('2d'), w, h);
    const t = new T.CanvasTexture(c);
    if (srgb) t.encoding = T.sRGBEncoding;
    t.anisotropy = 4;
    return t;
  }

  const radialTex = canvasTex(128, 128, (g, w, h) => {
    const gr = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
    gr.addColorStop(0, 'rgba(255,255,255,1)');
    gr.addColorStop(0.25, 'rgba(255,255,255,0.55)');
    gr.addColorStop(0.6, 'rgba(255,255,255,0.12)');
    gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
  }, false);

  // a light's long reflection in a wet floor: bright at the near end, fading away
  const streakTex = canvasTex(64, 256, (g, w, h) => {
    const gr = g.createLinearGradient(0, 0, 0, h);
    gr.addColorStop(0, 'rgba(255,255,255,0)');
    gr.addColorStop(0.15, 'rgba(255,255,255,0.7)');
    gr.addColorStop(0.35, 'rgba(255,255,255,0.35)');
    gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
    const sd = g.createLinearGradient(0, 0, w, 0);
    sd.addColorStop(0, 'rgba(0,0,0,1)'); sd.addColorStop(0.5, 'rgba(0,0,0,0)'); sd.addColorStop(1, 'rgba(0,0,0,1)');
    g.globalCompositeOperation = 'destination-out';
    g.fillStyle = sd; g.fillRect(0, 0, w, h);
  }, false);

  function noise(g, w, h, n, a, b, alpha) {
    for (let i = 0; i < n; i++) {
      const v = Math.floor(rnd(a, b));
      g.fillStyle = `rgba(${v},${v},${v + 4},${alpha})`;
      const s = rnd(1, 3);
      g.fillRect(rnd(0, w), rnd(0, h), s, s);
    }
  }

  // the concrete: worn tiles, stains, the odd painted line
  function floorTextures() {
    const map = canvasTex(1024, 1024, (g, w, h) => {
      g.fillStyle = '#1b1f27'; g.fillRect(0, 0, w, h);
      noise(g, w, h, 26000, 20, 52, 0.5);
      for (let i = 0; i < 26; i++) {
        const x = rnd(0, w), y = rnd(0, h), r = rnd(20, 120);
        const gr = g.createRadialGradient(x, y, 0, x, y, r);
        gr.addColorStop(0, 'rgba(6,7,10,0.55)'); gr.addColorStop(1, 'rgba(6,7,10,0)');
        g.fillStyle = gr; g.beginPath(); g.ellipse(x, y, r, r * rnd(0.5, 1), rnd(0, 3), 0, 7); g.fill();
      }
      g.strokeStyle = 'rgba(0,0,0,0.6)'; g.lineWidth = 3;
      for (let i = 0; i <= 4; i++) {
        g.beginPath(); g.moveTo(i * 256, 0); g.lineTo(i * 256, h); g.stroke();
        g.beginPath(); g.moveTo(0, i * 256); g.lineTo(w, i * 256); g.stroke();
      }
      g.strokeStyle = 'rgba(255,255,255,0.05)'; g.lineWidth = 1;
      for (let i = 0; i <= 4; i++) {
        g.beginPath(); g.moveTo(i * 256 + 2, 0); g.lineTo(i * 256 + 2, h); g.stroke();
        g.beginPath(); g.moveTo(0, i * 256 + 2); g.lineTo(w, i * 256 + 2); g.stroke();
      }
    });
    map.wrapS = map.wrapT = T.RepeatWrapping;
    map.repeat.set(4, 3);
    // puddles: where the floor is wet it is smooth, and the lights show in it
    const rough = canvasTex(512, 512, (g, w, h) => {
      g.fillStyle = 'rgb(190,190,190)'; g.fillRect(0, 0, w, h);
      for (let i = 0; i < 22; i++) {
        const x = rnd(0, w), y = rnd(0, h), r = rnd(30, 110);
        const gr = g.createRadialGradient(x, y, 0, x, y, r);
        gr.addColorStop(0, 'rgba(25,25,25,0.95)'); gr.addColorStop(0.7, 'rgba(25,25,25,0.6)'); gr.addColorStop(1, 'rgba(25,25,25,0)');
        g.fillStyle = gr; g.beginPath(); g.ellipse(x, y, r * 1.6, r, rnd(0, 3), 0, 7); g.fill();
      }
    }, false);
    rough.wrapS = rough.wrapT = T.RepeatWrapping;
    rough.repeat.set(2, 1.5);
    return { map, rough };
  }

  // the ring round the lift: hazard stripes and the bay number
  const liftTex = canvasTex(1024, 1024, (g, w, h) => {
    const cx = w / 2, cy = h / 2;
    g.fillStyle = '#0d1016'; g.fillRect(0, 0, w, h);
    g.save(); g.translate(cx, cy);
    const n = 48;
    for (let i = 0; i < n; i++) {
      g.fillStyle = i % 2 ? '#14161b' : '#c9a227';
      g.beginPath(); g.arc(0, 0, 500, (i / n) * Math.PI * 2, ((i + 1) / n) * Math.PI * 2); g.arc(0, 0, 452, ((i + 1) / n) * Math.PI * 2, (i / n) * Math.PI * 2, true); g.fill();
    }
    g.fillStyle = '#10141b'; g.beginPath(); g.arc(0, 0, 450, 0, 7); g.fill();
    // brushed rings
    for (let r = 440; r > 0; r -= 3) {
      g.strokeStyle = `rgba(255,255,255,${0.012 + Math.random() * 0.02})`;
      g.beginPath(); g.arc(0, 0, r, 0, 7); g.stroke();
    }
    g.font = '800 64px Orbitron, sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = 'rgba(41,240,255,0.16)';
    g.fillText('BAY 07', 0, 300);
    g.restore();
  });

  // ---------------------------------------------------------------- materials
  const M = {};
  function neon(hex, mul = 1) {
    const m = new T.MeshBasicMaterial({ color: new T.Color(hex).multiplyScalar(mul), toneMapped: false });
    return m;
  }

  // the holographic see-through look the scanner gives the car's body
  function holoMaterial(hex) {
    return new T.ShaderMaterial({
      uniforms: { uTime: { value: 0 }, uColor: { value: new T.Color(hex) }, uAlpha: { value: 1 } },
      vertexShader: `
        varying vec3 vN; varying vec3 vV; varying vec3 vW;
        void main(){
          vec4 w = modelMatrix * vec4(position,1.0);
          vW = w.xyz;
          vN = normalize(mat3(modelMatrix) * normal);
          vV = normalize(cameraPosition - w.xyz);
          gl_Position = projectionMatrix * viewMatrix * w;
          #include <clipping_planes_vertex>
        }`,
      fragmentShader: `
        #include <clipping_planes_pars_fragment>
        uniform float uTime; uniform vec3 uColor; uniform float uAlpha;
        varying vec3 vN; varying vec3 vV; varying vec3 vW;
        void main(){
          #include <clipping_planes_fragment>
          float f = 1.0 - abs(dot(normalize(vN), normalize(vV)));
          f = pow(f, 2.2);
          float lines = smoothstep(0.82, 1.0, sin(vW.y * 60.0 - uTime * 3.0) * 0.5 + 0.5) * 0.35;
          float sweep = smoothstep(0.0, 0.3, sin(vW.x * 1.5 - uTime * 2.0) * 0.5 + 0.5) * 0.15;
          float a = (0.06 + f * 0.95 + lines + sweep) * uAlpha;
          gl_FragColor = vec4(uColor * (0.6 + f * 1.6), a);
        }`,
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.FrontSide,
      vertexColors: false,
    });
  }
  // the vertex shader above uses the clipping chunks, which want this declared
  function withClipVaryings(mat) {
    mat.vertexShader = '#include <clipping_planes_pars_vertex>\n' + mat.vertexShader.replace('vec4 w = modelMatrix', 'vec4 mvPosition = modelViewMatrix * vec4(position,1.0);\n          vec4 w = modelMatrix');
    return mat;
  }

  // ---------------------------------------------------------------- the glow pass
  const glow = { on: true };
  const QUAD_VS = 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }';
  function setupGlow() {
    const opts = { minFilter: T.LinearFilter, magFilter: T.LinearFilter, format: T.RGBAFormat, depthBuffer: false, stencilBuffer: false };
    glow.rtScene = new T.WebGLRenderTarget(4, 4, Object.assign({}, opts, { depthBuffer: true }));
    glow.rtA = new T.WebGLRenderTarget(4, 4, opts);
    glow.rtB = new T.WebGLRenderTarget(4, 4, opts);
    glow.rtC = new T.WebGLRenderTarget(4, 4, opts);
    glow.rtD = new T.WebGLRenderTarget(4, 4, opts);
    glow.black = new T.MeshBasicMaterial({ color: 0x000000, fog: false });
    glow.blur = new T.ShaderMaterial({
      uniforms: { tex: { value: null }, dir: { value: new T.Vector2() } },
      vertexShader: QUAD_VS,
      fragmentShader: `
        uniform sampler2D tex; uniform vec2 dir; varying vec2 vUv;
        void main(){
          vec4 c = texture2D(tex, vUv) * 0.2270270270;
          c += texture2D(tex, vUv + dir * 1.3846153846) * 0.3162162162;
          c += texture2D(tex, vUv - dir * 1.3846153846) * 0.3162162162;
          c += texture2D(tex, vUv + dir * 3.2307692308) * 0.0702702703;
          c += texture2D(tex, vUv - dir * 3.2307692308) * 0.0702702703;
          gl_FragColor = c;
        }`,
      depthTest: false, depthWrite: false,
    });
    glow.comp = new T.ShaderMaterial({
      uniforms: { a: { value: null }, b: { value: null }, s: { value: 1 } },
      vertexShader: QUAD_VS,
      fragmentShader: `
        uniform sampler2D a; uniform sampler2D b; uniform float s; varying vec2 vUv;
        void main(){
          vec3 c = texture2D(a, vUv).rgb * 1.25 + texture2D(b, vUv).rgb * 1.6;
          gl_FragColor = vec4(c * s, 1.0);
        }`,
      transparent: true, blending: T.AdditiveBlending, depthTest: false, depthWrite: false, toneMapped: false,
    });
    glow.quad = new T.Mesh(new T.PlaneGeometry(2, 2), glow.blur);
    glow.quad.frustumCulled = false;
    glow.quadScene = new T.Scene();
    glow.quadScene.add(glow.quad);
    glow.quadCam = new T.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    glow.dirty = true;
    glow.dark = [];
    glow.hide = [];
  }
  function sizeGlow() {
    // the glow is soft anyway: a fraction of the screen's pixels is plenty, and cheap
    const f = quality >= 2 ? 0.5 : 0.33;
    const w = Math.max(16, Math.round(W * f)), h = Math.max(16, Math.round(H * f));
    glow.rtScene.setSize(w, h);
    glow.rtA.setSize(w, h);
    glow.rtB.setSize(w, h);
    glow.rtC.setSize(Math.max(8, w >> 1), Math.max(8, h >> 1));
    glow.rtD.setSize(Math.max(8, w >> 1), Math.max(8, h >> 1));
  }
  // what is a light, what hides one, and what isn't there for the glow at all
  function collectGlow() {
    glow.dark.length = 0; glow.hide.length = 0;
    scene.traverse((o) => {
      if (!o.isMesh && !o.isPoints && !o.isLine && !o.isLineSegments && !o.isSprite) return;
      if (o.userData.glow) return;
      if (o.userData.fx || o.isPoints || o.isSprite || o.isLine || o.isLineSegments || (o.material && o.material.blending === T.AdditiveBlending)) glow.hide.push(o);
      else glow.dark.push(o);
    });
    glow.dirty = false;
  }
  function blurPass(src, dst, dx, dy) {
    glow.quad.material = glow.blur;
    glow.blur.uniforms.tex.value = src.texture;
    glow.blur.uniforms.dir.value.set(dx / src.width, dy / src.height);
    renderer.setRenderTarget(dst);
    renderer.render(glow.quadScene, glow.quadCam);
  }
  const BLACK = new T.Color(0, 0, 0);
  const bgColor = new T.Color(0x05080f);
  function renderFrame() {
    if (glow.on && quality > 0) {
      if (glow.dirty) collectGlow();
      const dark = glow.dark, hide = glow.hide;
      for (let i = 0; i < dark.length; i++) { const o = dark[i]; o.userData._m = o.material; o.material = o.userData.black || glow.black; }
      for (let i = 0; i < hide.length; i++) { const o = hide[i]; o.userData._v = o.visible; o.visible = false; }
      renderer.setRenderTarget(glow.rtScene);
      renderer.setClearColor(BLACK, 1);
      renderer.clear();
      renderer.render(scene, camera);
      for (let i = 0; i < dark.length; i++) { const o = dark[i]; o.material = o.userData._m; }
      for (let i = 0; i < hide.length; i++) { const o = hide[i]; o.visible = o.userData._v; }
      blurPass(glow.rtScene, glow.rtA, 1, 0);
      blurPass(glow.rtA, glow.rtB, 0, 1);
      blurPass(glow.rtB, glow.rtC, 2, 0);
      blurPass(glow.rtC, glow.rtD, 0, 2);
    }
    renderer.setRenderTarget(null);
    renderer.setClearColor(bgColor, 1);
    renderer.clear();
    renderer.render(scene, camera);
    if (glow.on && quality > 0) {
      glow.quad.material = glow.comp;
      glow.comp.uniforms.a.value = glow.rtB.texture;
      glow.comp.uniforms.b.value = glow.rtD.texture;
      glow.comp.uniforms.s.value = glow.strength;
      renderer.render(glow.quadScene, glow.quadCam);
    }
  }
  glow.strength = 1;

  // ---------------------------------------------------------------- the garage
  const G = {};           // things in the garage that move or change
  const W0 = { backZ: -6, leftX: -9.5, rightX: 9, roof: 5.4 };

  function box(w, h, d, mat, x, y, z, parent) {
    const m = new T.Mesh(new T.BoxGeometry(w, h, d), mat);
    m.position.set(x, y, z);
    (parent || scene).add(m);
    return m;
  }
  function glowMesh(geo, mat, parent) {
    const m = new T.Mesh(geo, mat);
    m.userData.glow = true;
    (parent || scene).add(m);
    return m;
  }
  function neonBar(len, thick, hex, mul, x, y, z, axis, parent) {
    const geo = axis === 'x' ? new T.BoxGeometry(len, thick, thick) : axis === 'y' ? new T.BoxGeometry(thick, len, thick) : new T.BoxGeometry(thick, thick, len);
    const m = glowMesh(geo, neon(hex, mul), parent);
    m.position.set(x, y, z);
    return m;
  }
  // a light's reflection lying in the wet floor
  function floorStreak(x, z, w, len, hex, alpha, rotY) {
    const m = new T.Mesh(new T.PlaneGeometry(w, len), new T.MeshBasicMaterial({
      map: streakTex, color: new T.Color(hex), transparent: true, opacity: alpha, blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
    }));
    m.rotation.x = -Math.PI / 2;
    m.rotation.z = rotY || 0;
    m.position.set(x, 0.012, z);
    m.userData.fx = true;
    scene.add(m);
    return m;
  }
  function spill(x, y, z, size, hex, alpha, parent) {
    const m = new T.Mesh(new T.PlaneGeometry(size, size), new T.MeshBasicMaterial({
      map: radialTex, color: new T.Color(hex), transparent: true, opacity: alpha, blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
    }));
    m.rotation.x = -Math.PI / 2;
    m.position.set(x, y, z);
    m.userData.fx = true;
    (parent || scene).add(m);
    return m;
  }

  // a word in neon tubes, drawn on a canvas and lit
  function neonSign(lines, w, h, hex, opts = {}) {
    const tex = canvasTex(1024, Math.round(1024 * h / w), (g, cw, ch) => {
      g.clearRect(0, 0, cw, ch);
      g.textAlign = 'center'; g.textBaseline = 'middle';
      lines.forEach((ln) => {
        g.font = ln.font;
        let size = parseFloat(/(\d+)px/.exec(ln.font)[1]);
        while (g.measureText(ln.text).width > cw * 0.9 && size > 10) { size -= 4; g.font = ln.font.replace(/\d+px/, size + 'px'); }
        g.lineJoin = 'round';
        g.strokeStyle = ln.color || hex; g.lineWidth = ln.stroke || 6;
        g.strokeText(ln.text, cw / 2, ch * ln.y);
        g.fillStyle = ln.core || '#ffffff';
        if (ln.fill !== false) g.fillText(ln.text, cw / 2, ch * ln.y);
      });
    });
    const mat = new T.MeshBasicMaterial({ map: tex, transparent: true, toneMapped: false, depthWrite: false, color: new T.Color(opts.mul || 1, opts.mul || 1, opts.mul || 1) });
    const m = glowMesh(new T.PlaneGeometry(w, h), mat);
    return m;
  }

  function buildGarage() {
    const floor = floorTextures();
    M.floor = new T.MeshStandardMaterial({ map: floor.map, roughnessMap: floor.rough, roughness: 1, metalness: 0.15, envMapIntensity: 1.1, color: 0xb8c0cc });
    const fl = new T.Mesh(new T.PlaneGeometry(40, 30), M.floor);
    fl.rotation.x = -Math.PI / 2;
    fl.position.set(0, 0, 3);
    scene.add(fl);

    M.wall = new T.MeshStandardMaterial({ color: 0x121722, roughness: 0.85, metalness: 0.25 });
    M.wallDark = new T.MeshStandardMaterial({ color: 0x0d1018, roughness: 0.7, metalness: 0.4 });
    M.metal = new T.MeshStandardMaterial({ color: 0x5b6573, roughness: 0.35, metalness: 0.85 });
    M.darkMetal = new T.MeshStandardMaterial({ color: 0x232a35, roughness: 0.45, metalness: 0.8 });
    M.rubber = new T.MeshStandardMaterial({ color: 0x101114, roughness: 0.9, metalness: 0 });

    const bz = W0.backZ, lx = W0.leftX, rx = W0.rightX, roof = W0.roof;
    // the back wall, with the window on the city cut out of it: x -1.5..8, y 1.1..4.3
    box(8, roof, 0.3, M.wall, -5.5, roof / 2, bz);           // left of the window
    box(1.2, roof, 0.3, M.wall, 8.6, roof / 2, bz);          // right of it
    box(9.6, 1.1, 0.3, M.wall, 3.25, 0.55, bz);              // under it
    box(9.6, roof - 4.3, 0.3, M.wall, 3.25, (roof + 4.3) / 2, bz); // over it
    // window mullions
    for (let i = 0; i <= 4; i++) box(0.12, 3.2, 0.18, M.darkMetal, -1.5 + i * 2.375, 2.7, bz + 0.05);
    box(9.5, 0.12, 0.2, M.darkMetal, 3.25, 1.12, bz + 0.05);
    box(9.5, 0.12, 0.2, M.darkMetal, 3.25, 4.28, bz + 0.05);
    // the side walls: the left one is the door to the street
    box(0.3, roof, 4.5, M.wall, lx, roof / 2, bz + 2.25);
    box(0.3, roof - 4.1, 7, M.wall, lx, (roof + 4.1) / 2, bz + 8);
    box(0.3, roof, 8, M.wall, lx, roof / 2, bz + 15.5);
    box(0.3, roof, 24, M.wall, rx, roof / 2, bz + 12);
    // the roller door, half up, and its frame
    box(0.4, 0.7, 7, M.darkMetal, lx + 0.1, 3.75, bz + 8);
    for (let i = 0; i < 5; i++) box(0.42, 0.04, 7, M.metal, lx + 0.1, 3.45 + i * 0.13, bz + 8);
    G.doorStripe = neonBar(7, 0.05, 0xffb020, 1.4, lx + 0.32, 3.36, bz + 8, 'z');
    // the ceiling, and its girders
    const ceil = new T.Mesh(new T.PlaneGeometry(40, 30), M.wallDark);
    ceil.rotation.x = Math.PI / 2; ceil.position.set(0, roof, 3); scene.add(ceil);
    for (let i = -3; i <= 3; i++) box(0.3, 0.4, 24, M.darkMetal, i * 3, roof - 0.2, bz + 12);

    // neon: along the foot of the back wall, along its top, and up the pillars
    G.neonCyan = neon(0x29f0ff, 1.6);
    G.neonPink = neon(0xff2bd6, 1.5);
    const footCyan = glowMesh(new T.BoxGeometry(17.5, 0.05, 0.05), G.neonCyan); footCyan.position.set(-0.25, 0.08, bz + 0.18);
    const topPink = glowMesh(new T.BoxGeometry(17.5, 0.06, 0.06), G.neonPink); topPink.position.set(-0.25, 4.75, bz + 0.18);
    [-8.6, -1.6].forEach((x) => { const p = glowMesh(new T.BoxGeometry(0.06, 4.6, 0.06), G.neonPink); p.position.set(x, 2.4, bz + 0.18); });
    const rightFoot = glowMesh(new T.BoxGeometry(0.05, 0.05, 14), G.neonCyan); rightFoot.position.set(rx - 0.18, 0.08, 0);
    floorStreak(-3, bz + 1.6, 3.5, 3.2, 0x29f0ff, 0.35, 0);
    floorStreak(4, bz + 1.6, 3.5, 3.2, 0x29f0ff, 0.3, 0);
    spill(-4, 0.011, bz + 0.6, 6, 0x29f0ff, 0.25);
    spill(3, 0.011, bz + 0.6, 6, 0x29f0ff, 0.25);
    spill(rx - 0.5, 0.011, -1, 5, 0x29f0ff, 0.22);

    // the sign: RUST & REGRET, one letter forever about to go out
    const sign = neonSign([
      { text: 'RUST & REGRET', font: '900 150px Orbitron, sans-serif', y: 0.36, color: '#ff2bd6', stroke: 14, core: '#ffd6f6' },
      { text: 'AUTO REPAIR  ·  EST. 2061  ·  NO REFUNDS', font: '700 52px Orbitron, sans-serif', y: 0.78, color: '#29f0ff', stroke: 7, core: '#e6feff' },
    ], 6, 1.6, '#ff2bd6');
    sign.position.set(-5.2, 3.15, bz + 0.17);
    G.sign = sign;
    const signBack = box(6.4, 1.9, 0.08, M.wallDark, -5.2, 3.15, bz + 0.12);
    signBack.userData.noGlow = true;
    floorStreak(-5.2, bz + 2.4, 4.5, 4.5, 0xff2bd6, 0.22, 0);

    // the window and the city behind it
    buildCity();
    const glass = new T.Mesh(new T.PlaneGeometry(9.5, 3.2), new T.MeshStandardMaterial({ color: 0x8fb6d9, roughness: 0.05, metalness: 0.9, transparent: true, opacity: 0.12, envMapIntensity: 1.5 }));
    glass.position.set(3.25, 2.7, bz + 0.02);
    glass.userData.fx = true;
    scene.add(glass);

    // ceiling lights, and the light falling from them through the haze
    G.ceilLights = [];
    [[-4.5, 0], [0, 0], [4.5, 0], [0, 3.5]].forEach(([x, z]) => {
      const bar = glowMesh(new T.BoxGeometry(2.6, 0.06, 0.22), neon(0xdff8ff, 1.2));
      bar.position.set(x, roof - 0.45, z);
      box(2.8, 0.08, 0.32, M.darkMetal, x, roof - 0.39, z);
      G.ceilLights.push(bar);
    });
    G.beams = [];
    [[-4.5, 0], [0, 0], [4.5, 0]].forEach(([x, z]) => G.beams.push(beam(x, roof - 0.5, z, 0.3, 2.4, roof - 0.5, 0xbfe9ff, 0.16)));

    buildLift();
    buildProps();
    buildStreet();
    buildDust();

    // the light: a cold sky, the bay's key light over the lift, and the neon's colour on things
    G.hemi = new T.HemisphereLight(0x8fa8d8, 0x101018, 0.45);
    scene.add(G.hemi);
    G.key = new T.SpotLight(0xe8f4ff, 2.2, 22, 0.75, 0.6, 1.2);
    G.key.position.set(1.5, 5.2, 3.5);
    G.key.target.position.set(0, 0.6, 0);
    scene.add(G.key); scene.add(G.key.target);
    G.cyanLight = new T.PointLight(0x29f0ff, 1.6, 12, 1.5); G.cyanLight.position.set(2, 1.2, -4.5); scene.add(G.cyanLight);
    G.pinkLight = new T.PointLight(0xff2bd6, 1.0, 9, 1.8); G.pinkLight.position.set(-5.5, 3.6, -4.8); scene.add(G.pinkLight);
    G.fill = new T.PointLight(0x7c8cff, 0.8, 16, 1.2); G.fill.position.set(6, 3, 6); scene.add(G.fill);
    G.liftLight = new T.PointLight(0x29f0ff, 0.0, 6, 1.5); G.liftLight.position.set(0, 0.4, 0); scene.add(G.liftLight);
    G.red = new T.PointLight(0xff2030, 0, 30, 1); G.red.position.set(0, 4.5, 1); scene.add(G.red);
  }

  // a cone of light through the dust, fading towards its edges and down to the floor
  function beam(x, y, z, rTop, rBottom, h, hex, alpha) {
    const geo = new T.CylinderGeometry(rTop, rBottom, h, 32, 1, true);
    geo.translate(0, -h / 2, 0);
    const mat = new T.ShaderMaterial({
      uniforms: { uColor: { value: new T.Color(hex) }, uAlpha: { value: alpha }, uH: { value: h } },
      vertexShader: `
        varying float vY; varying vec3 vN; varying vec3 vV;
        void main(){
          vY = position.y;
          vec4 w = modelMatrix * vec4(position,1.0);
          vN = normalize(mat3(modelMatrix) * normal);
          vV = normalize(cameraPosition - w.xyz);
          gl_Position = projectionMatrix * viewMatrix * w;
        }`,
      fragmentShader: `
        uniform vec3 uColor; uniform float uAlpha; uniform float uH;
        varying float vY; varying vec3 vN; varying vec3 vV;
        void main(){
          float edge = abs(dot(normalize(vN), normalize(vV)));
          float fall = clamp(1.0 + vY / uH, 0.0, 1.0);
          float a = pow(edge, 1.6) * fall * fall * uAlpha;
          gl_FragColor = vec4(uColor * a, a);
        }`,
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.DoubleSide, toneMapped: false,
    });
    const m = new T.Mesh(geo, mat);
    m.position.set(x, y, z);
    m.userData.fx = true;
    scene.add(m);
    return m;
  }

  function buildLift() {
    const lift = new T.Group();
    scene.add(lift);
    G.lift = lift;
    const base = new T.Mesh(new T.CylinderGeometry(3.15, 3.25, 0.1, 72), new T.MeshStandardMaterial({ map: liftTex, roughness: 0.4, metalness: 0.7, color: 0xffffff }));
    base.position.y = 0.05;
    lift.add(base);
    G.liftRings = [];
    [1.15, 2.05, 2.75].forEach((r, i) => {
      const ring = glowMesh(new T.RingGeometry(r - 0.025, r + 0.025, 96), neon(0x29f0ff, 1.4), lift);
      ring.rotation.x = -Math.PI / 2;
      ring.position.y = 0.105 + i * 0.001;
      G.liftRings.push(ring);
    });
    // the emitters round its edge
    G.emitters = [];
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * Math.PI * 2;
      const e = glowMesh(new T.BoxGeometry(0.28, 0.05, 0.08), neon(0x29f0ff, 1.2), lift);
      e.position.set(Math.cos(a) * 3.0, 0.12, Math.sin(a) * 3.0);
      e.rotation.y = -a;
      G.emitters.push(e);
    }
    // the field holding the car up, seen when it's lifted
    const colGeo = new T.CylinderGeometry(2.2, 2.6, 1, 48, 1, true);
    colGeo.translate(0, 0.5, 0);
    G.column = new T.Mesh(colGeo, new T.ShaderMaterial({
      uniforms: { uTime: { value: 0 }, uAlpha: { value: 0 } },
      vertexShader: 'varying vec2 vUv; varying vec3 vN; varying vec3 vV; void main(){ vUv = uv; vec4 w = modelMatrix * vec4(position,1.0); vN = normalize(mat3(modelMatrix)*normal); vV = normalize(cameraPosition - w.xyz); gl_Position = projectionMatrix * viewMatrix * w; }',
      fragmentShader: `
        uniform float uTime; uniform float uAlpha; varying vec2 vUv; varying vec3 vN; varying vec3 vV;
        void main(){
          float edge = 1.0 - abs(dot(normalize(vN), normalize(vV)));
          float bands = 0.5 + 0.5 * sin(vUv.y * 26.0 - uTime * 5.0);
          float a = (0.25 + edge * 0.9) * (1.0 - vUv.y) * (0.55 + bands * 0.45) * uAlpha;
          gl_FragColor = vec4(vec3(0.16, 0.94, 1.0) * a, a);
        }`,
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.DoubleSide, toneMapped: false,
    }));
    G.column.userData.fx = true;
    G.column.position.y = 0.1;
    G.column.scale.y = 0.001;
    lift.add(G.column);
    G.liftGlow = spill(0, 0.11, 0, 7.5, 0x29f0ff, 0.18, lift);
  }

  // the things a garage collects: tyres, drums, a bench, a coffee machine, a robot that won't work
  function buildProps() {
    const bz = W0.backZ;
    // tyre stack
    for (let i = 0; i < 4; i++) {
      const t = new T.Mesh(new T.TorusGeometry(0.36, 0.14, 12, 28), M.rubber);
      t.rotation.x = Math.PI / 2;
      t.position.set(-7.6 + (i === 3 ? 0.08 : 0), 0.14 + i * 0.27, bz + 1.0);
      scene.add(t);
    }
    // oil drums
    const drumMat = new T.MeshStandardMaterial({ color: 0x8a2a2a, roughness: 0.55, metalness: 0.6 });
    const drumMat2 = new T.MeshStandardMaterial({ color: 0x2a4a8a, roughness: 0.55, metalness: 0.6 });
    [[7.8, 1.2, drumMat], [7.9, 2.1, drumMat2], [7.1, 1.6, drumMat]].forEach(([x, z, m]) => {
      const d = new T.Mesh(new T.CylinderGeometry(0.32, 0.32, 0.95, 24), m);
      d.position.set(x, 0.475, z);
      scene.add(d);
      const rim = new T.Mesh(new T.TorusGeometry(0.32, 0.02, 6, 24), M.metal);
      rim.rotation.x = Math.PI / 2; rim.position.set(x, 0.62, z); scene.add(rim);
    });
    // workbench and tool cabinet against the window wall
    box(2.6, 0.08, 0.8, M.metal, 6.6, 0.95, bz + 0.6);
    box(2.5, 0.9, 0.7, M.darkMetal, 6.6, 0.45, bz + 0.6);
    for (let i = 0; i < 4; i++) neonBar(2.2, 0.02, 0xffb020, 1.1, 6.6, 0.2 + i * 0.2, bz + 0.96, 'x');
    // a holographic screen over the bench, showing a diagnostic nobody reads
    const scr = canvasTex(512, 256, (g, w, h) => {
      g.fillStyle = 'rgba(8,30,40,0.0)'; g.fillRect(0, 0, w, h);
      g.strokeStyle = '#29f0ff'; g.lineWidth = 3; g.strokeRect(6, 6, w - 12, h - 12);
      g.font = '700 30px Orbitron, sans-serif'; g.fillStyle = '#29f0ff'; g.fillText('PARTS PRINTER', 24, 48);
      g.font = '600 22px "Chakra Petch", sans-serif'; g.fillStyle = 'rgba(200,250,255,0.85)';
      ['QUEUE: 1 BOLT (FAILED)', 'TONER: EMOTIONAL', 'STATUS: JUDGING YOU'].forEach((s, i) => g.fillText(s, 24, 96 + i * 34));
      g.strokeStyle = '#ff2bd6'; g.beginPath();
      for (let x = 0; x < 200; x++) g.lineTo(290 + x, 200 - Math.abs(Math.sin(x * 0.13) * 40) * Math.random());
      g.stroke();
    });
    G.screen = glowMesh(new T.PlaneGeometry(1.6, 0.8), new T.MeshBasicMaterial({ map: scr, transparent: true, opacity: 0.9, toneMapped: false, depthWrite: false, side: T.DoubleSide }));
    G.screen.position.set(6.6, 1.75, bz + 0.65);

    // the coffee machine: the only thing in here that is always on
    const vend = new T.Group();
    vend.position.set(W0.rightX - 0.6, 0, -2.6);
    vend.rotation.y = -Math.PI / 2;
    scene.add(vend);
    box(1.1, 2.1, 0.8, M.darkMetal, 0, 1.05, 0, vend);
    const face = canvasTex(256, 512, (g, w, h) => {
      const gr = g.createLinearGradient(0, 0, 0, h);
      gr.addColorStop(0, '#2a0a24'); gr.addColorStop(1, '#0a1020');
      g.fillStyle = gr; g.fillRect(0, 0, w, h);
      g.font = '900 34px Orbitron, sans-serif'; g.textAlign = 'center';
      g.fillStyle = '#ff2bd6'; g.fillText('BEAN', w / 2, 70); g.fillText('MACHINE', w / 2, 110);
      g.font = '600 20px "Chakra Petch", sans-serif'; g.fillStyle = '#ffd6f6';
      g.fillText('NOW 4% BEAN', w / 2, 150);
      g.fillStyle = '#29f0ff';
      for (let i = 0; i < 6; i++) { g.fillRect(40 + (i % 3) * 64, 200 + Math.floor(i / 3) * 70, 44, 44); }
      g.fillStyle = '#05080f'; g.fillRect(60, 380, 136, 90);
      g.strokeStyle = '#ffb020'; g.lineWidth = 3; g.strokeRect(60, 380, 136, 90);
    });
    const vf = glowMesh(new T.PlaneGeometry(0.95, 1.9), new T.MeshBasicMaterial({ map: face, toneMapped: false, color: new T.Color(0.9, 0.9, 0.9) }), vend);
    vf.position.set(0, 1.05, 0.41);
    G.vendLight = new T.PointLight(0xff2bd6, 0.9, 5, 1.5);
    G.vendLight.position.set(W0.rightX - 1.4, 1.4, -2.6);
    scene.add(G.vendLight);
    spill(W0.rightX - 1.3, 0.012, -2.6, 3.2, 0xff2bd6, 0.3);

    // the robot: brand new, top of the range, and on strike
    const bot = new T.Group();
    bot.position.set(-4.2, 0, bz + 1.1);
    scene.add(bot);
    const botMat = new T.MeshStandardMaterial({ color: 0xd8dde4, roughness: 0.3, metalness: 0.6 });
    const c1 = new T.Mesh(new T.CylinderGeometry(0.42, 0.5, 0.35, 24), M.darkMetal); c1.position.y = 0.175; bot.add(c1);
    const arm1 = box(0.22, 1.3, 0.22, botMat, 0, 0.95, 0, bot); arm1.rotation.z = 0.25;
    const j1 = new T.Mesh(new T.SphereGeometry(0.17, 16, 12), M.darkMetal); j1.position.set(-0.16, 1.6, 0); bot.add(j1);
    const arm2 = box(1.1, 0.18, 0.18, botMat, -0.62, 1.42, 0, bot); arm2.rotation.z = 0.5;
    const head = box(0.25, 0.2, 0.3, M.darkMetal, -1.1, 1.12, 0, bot); head.rotation.z = 0.5;
    G.botEye = neonBar(0.06, 0.05, 0xff3b5c, 2, -1.18, 1.06, 0.16, 'x', bot);
    const strike = neonSign([
      { text: 'ON STRIKE', font: '900 170px Orbitron, sans-serif', y: 0.5, color: '#ff3b5c', stroke: 16, core: '#ffe0e4' },
    ], 1.5, 0.4, '#ff3b5c');
    strike.position.set(-4.2, 2.25, bz + 0.6);
    G.strike = strike;
  }

  // the city: towers in the smog, their windows, its signs, and the traffic in the air
  function buildCity() {
    const bz = W0.backZ;
    const sky = new T.Mesh(new T.PlaneGeometry(80, 30), new T.ShaderMaterial({
      uniforms: { uTop: { value: new T.Color(0x0a1022) }, uBot: { value: new T.Color(0x3a1840) } },
      vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
      fragmentShader: 'uniform vec3 uTop; uniform vec3 uBot; varying vec2 vUv; void main(){ gl_FragColor = vec4(mix(uBot, uTop, smoothstep(0.25, 0.75, vUv.y)), 1.0); }',
      depthWrite: false, toneMapped: false,
    }));
    sky.position.set(4, 8, -60);
    scene.add(sky);
    G.sky = sky;

    const layers = [
      { z: -48, h: 14, n: 22, c: '#0b1020', win: 0.18, w: 70, y: 3.5 },
      { z: -34, h: 11, n: 16, c: '#0d1426', win: 0.26, w: 52, y: 2.5 },
      { z: -22, h: 8, n: 11, c: '#111a2e', win: 0.32, w: 36, y: 1.6 },
    ];
    G.cityLayers = [];
    layers.forEach((L, li) => {
      const tex = canvasTex(2048, 512, (g, w, h) => {
        g.clearRect(0, 0, w, h);
        let x = 0;
        while (x < w) {
          const bw = rnd(60, 190), bh = rnd(h * 0.35, h * 0.98);
          g.fillStyle = L.c;
          g.fillRect(x, h - bh, bw, bh);
          if (Math.random() < 0.35) g.fillRect(x + bw * 0.4, h - bh - rnd(20, 70), 4, rnd(20, 70));
          // windows
          for (let wy = h - bh + 10; wy < h - 6; wy += 11) {
            for (let wx = x + 6; wx < x + bw - 6; wx += 9) {
              if (Math.random() < L.win) {
                const warm = Math.random() < 0.6;
                g.fillStyle = warm ? `rgba(255,${180 + Math.random() * 60 | 0},120,${rnd(0.35, 0.9)})` : `rgba(140,230,255,${rnd(0.35, 0.9)})`;
                g.fillRect(wx, wy, 4, 5);
              }
            }
          }
          // a sign down the side of a tower
          if (Math.random() < 0.28) {
            const col = Math.random() < 0.5 ? '#ff2bd6' : (Math.random() < 0.5 ? '#29f0ff' : '#ffb020');
            g.fillStyle = col;
            g.fillRect(x + bw * 0.15, h - bh + 20, 7, rnd(40, 120));
          }
          x += bw + rnd(-10, 14);
        }
      });
      const m = new T.Mesh(new T.PlaneGeometry(L.w, L.w / 4), new T.MeshBasicMaterial({ map: tex, transparent: true, toneMapped: false, depthWrite: false, fog: false }));
      m.position.set(4, L.y + L.w / 8 - 2, L.z);
      m.userData.glow = true;
      m.renderOrder = -10 + li;
      scene.add(m);
      G.cityLayers.push(m);
    });

    // traffic in the air: lights crossing between the towers
    G.flyers = [];
    for (let i = 0; i < 14; i++) {
      const col = [0xff3b5c, 0xffffff, 0x29f0ff, 0xffb020][i % 4];
      const f = new T.Mesh(new T.PlaneGeometry(1.1, 0.35), new T.MeshBasicMaterial({ map: radialTex, color: new T.Color(col).multiplyScalar(1.5), transparent: true, blending: T.AdditiveBlending, depthWrite: false, toneMapped: false, fog: false }));
      f.userData.glow = true;
      f.userData.speed = rnd(2.5, 7) * (Math.random() < 0.5 ? -1 : 1);
      f.position.set(rnd(-25, 30), rnd(3, 11), rnd(-40, -16));
      const s = clamp(1.4 - (-f.position.z - 16) / 30, 0.4, 1.3);
      f.scale.set(s, s, s);
      scene.add(f);
      G.flyers.push(f);
    }
    // a billboard in the city, too bright to be legal
    const ad = neonSign([
      { text: 'BUY A NEW CAR', font: '900 120px Orbitron, sans-serif', y: 0.35, color: '#29f0ff', stroke: 12, core: '#e6feff' },
      { text: 'or don\'t. we\'ll still repossess something.', font: '600 54px "Chakra Petch", sans-serif', y: 0.75, color: '#ffb020', stroke: 5, core: '#fff2d6' },
    ], 9, 2.4, '#29f0ff');
    ad.position.set(9, 9.5, -30);
    ad.material.fog = false;
    G.ad = ad;

    // rain, falling past the window and the door
    const N = 700;
    const pos = new Float32Array(N * 6);
    for (let i = 0; i < N; i++) {
      let x, z;
      if (i < N * 0.7) { x = rnd(-3, 10); z = rnd(bz - 12, bz - 0.5); } else { x = rnd(W0.leftX - 8, W0.leftX - 0.5); z = rnd(-4, 9); }
      const y = rnd(0, 9);
      pos.set([x, y, z, x - 0.04, y + 0.45, z], i * 6);
    }
    const rg = new T.BufferGeometry();
    rg.setAttribute('position', new T.BufferAttribute(pos, 3));
    G.rain = new T.LineSegments(rg, new T.ShaderMaterial({
      uniforms: { uTime: { value: 0 }, uColor: { value: new T.Color(0x9fc4ff) } },
      vertexShader: `
        uniform float uTime; varying float vA;
        void main(){
          vec3 p = position;
          float seed = fract(sin(dot(p.xz, vec2(12.9898, 78.233))) * 43758.5453);
          p.y = mod(p.y - uTime * (13.0 + seed * 5.0), 9.5) - 0.5;
          vA = 0.25 + seed * 0.35;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
        }`,
      fragmentShader: 'uniform vec3 uColor; varying float vA; void main(){ gl_FragColor = vec4(uColor * vA, vA); }',
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, toneMapped: false,
    }));
    G.rain.frustumCulled = false;
    G.rain.userData.fx = true;
    scene.add(G.rain);
  }

  // outside the door: the wet street and a lamp that hums
  function buildStreet() {
    const lx = W0.leftX;
    const road = new T.Mesh(new T.PlaneGeometry(14, 30), new T.MeshStandardMaterial({ color: 0x0b0d12, roughness: 0.18, metalness: 0.5, envMapIntensity: 1.4 }));
    road.rotation.x = -Math.PI / 2; road.position.set(lx - 7, 0.002, 3); scene.add(road);
    const pole = box(0.12, 6, 0.12, M.darkMetal, lx - 4, 3, -1.5);
    pole.userData.noGlow = true;
    G.lamp = glowMesh(new T.BoxGeometry(1.2, 0.08, 0.25), neon(0xffb020, 1.6));
    G.lamp.position.set(lx - 3.5, 5.9, -1.5);
    G.lampLight = new T.PointLight(0xffb020, 1.4, 14, 1.4);
    G.lampLight.position.set(lx - 3.5, 5.5, -1.5);
    scene.add(G.lampLight);
    spill(lx - 3.5, 0.01, -1.2, 7, 0xffb020, 0.22);
    floorStreak(lx - 3.4, 1.5, 1.4, 6, 0xffb020, 0.3, Math.PI / 2 - 0.3);
  }

  // dust turning in the light over the bay
  function buildDust() {
    const N = 160;
    const pos = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) pos.set([rnd(-6, 6), rnd(0.3, 5), rnd(-4, 4)], i * 3);
    const geo = new T.BufferGeometry();
    geo.setAttribute('position', new T.BufferAttribute(pos, 3));
    G.dust = new T.Points(geo, new T.ShaderMaterial({
      uniforms: { uTime: { value: 0 }, uSize: { value: 26 } },
      vertexShader: `
        uniform float uTime; uniform float uSize; varying float vA;
        void main(){
          vec3 p = position;
          float s = fract(sin(dot(p, vec3(12.9, 78.2, 37.7))) * 43758.5);
          p.x += sin(uTime * 0.13 + s * 30.0) * 0.6;
          p.y += sin(uTime * 0.09 + s * 17.0) * 0.4;
          p.z += cos(uTime * 0.11 + s * 11.0) * 0.5;
          vec4 mv = modelViewMatrix * vec4(p, 1.0);
          gl_PointSize = uSize * (0.4 + s) / -mv.z;
          vA = 0.12 + 0.3 * s;
          gl_Position = projectionMatrix * mv;
        }`,
      fragmentShader: 'varying float vA; void main(){ vec2 c = gl_PointCoord - 0.5; float d = 1.0 - smoothstep(0.1, 0.5, length(c)); gl_FragColor = vec4(vec3(0.75, 0.9, 1.0) * d * vA, d * vA); }',
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, toneMapped: false,
    }));
    G.dust.frustumCulled = false;
    scene.add(G.dust);
  }

  // what the paint reflects: a dark room with a few bright panels, the colours of the garage's own lights
  function buildEnvironment() {
    const env = new T.Scene();
    const room = new T.Mesh(new T.BoxGeometry(30, 12, 30), new T.MeshBasicMaterial({ color: 0x0c0f16, side: T.BackSide }));
    env.add(room);
    const panel = (w, h, col, mul, x, y, z, rx, ry) => {
      const m = new T.Mesh(new T.PlaneGeometry(w, h), new T.MeshBasicMaterial({ color: new T.Color(col).multiplyScalar(mul), side: T.DoubleSide }));
      m.position.set(x, y, z); m.rotation.set(rx || 0, ry || 0, 0);
      env.add(m);
    };
    panel(10, 2, 0xffffff, 3.2, 0, 5.8, 0, Math.PI / 2, 0);
    panel(8, 1.2, 0xdff4ff, 2.2, 0, 5.5, 6, Math.PI / 2, 0);
    panel(16, 0.4, 0x29f0ff, 3.5, 0, 0.6, -14.5, 0, 0);
    panel(16, 0.4, 0xff2bd6, 3.5, 0, 4.5, -14.5, 0, 0);
    panel(0.6, 8, 0xff2bd6, 2.5, 14.5, 3, 4, 0, Math.PI / 2);
    panel(6, 3, 0x2a3b66, 1.5, -14.5, 3, 0, 0, Math.PI / 2);
    panel(3, 3, 0xffb020, 1.2, -14.5, 2, 8, 0, Math.PI / 2);
    const pm = new T.PMREMGenerator(renderer);
    const rt = pm.fromScene(env, 0.035);
    scene.environment = rt.texture;
    pm.dispose();
  }

  // ---------------------------------------------------------------- the car
  /*
   * Cars are drawn from a side profile. The profile is the car's outline seen from the side; it is
   * filled in as the two side panels (with a grid of points inside, so a door skin can bow out the
   * way a real one does), joined across the car's width, with the edge between side and roof
   * rounded. Then the body is shaped: narrower at the nose and tail seen from above, tucked in at
   * the sills, flared over the wheels, the glass pulled in towards the roof.
   *
   * Each body style is a handful of numbers: where the wheels sit, how high the bonnet is, how far
   * the nose leans back, where the windscreen and the rear window start and stop.
   */
  const STYLES = {
    hatch: { L: 3.95, w: 1.8, r: 0.34, wb: 2.52, clear: 0.2, nose: 0.78, slope: 0.18, belt: 1.0, tail: 1.02, roof: 1.54, shield: 0.8, rf: -0.12, rr: -1.5, rear: -1.86, rearSlope: 0.1, taper: 0.16, bulge: 0.05, flare: 0.05 },
    sedan: { L: 4.7, w: 1.88, r: 0.36, wb: 2.85, clear: 0.19, nose: 0.82, slope: 0.24, belt: 1.0, tail: 1.02, roof: 1.48, shield: 1.0, rf: 0.02, rr: -0.98, rear: -1.72, rearSlope: 0.5, taper: 0.14, bulge: 0.05, flare: 0.04 },
    sport: { L: 4.5, w: 1.98, r: 0.34, wb: 2.66, clear: 0.13, nose: 0.8, slope: 0.32, belt: 0.92, tail: 0.98, roof: 1.27, shield: 0.72, rf: -0.32, rr: -1.0, rear: -2.05, rearSlope: 0.65, taper: 0.2, bulge: 0.04, flare: 0.08 },
    van: { L: 4.95, w: 1.98, r: 0.37, wb: 3.0, clear: 0.25, nose: 0.92, slope: 0.2, belt: 1.12, tail: 1.16, roof: 2.02, shield: 1.55, rf: 0.95, rr: -2.25, rear: -2.4, rearSlope: 0.02, box: true, taper: 0.1, bulge: 0.03, flare: 0.03 },
    pickup: { L: 5.1, w: 2.0, r: 0.42, wb: 3.15, clear: 0.3, nose: 1.02, slope: 0.14, belt: 1.2, tail: 1.22, roof: 1.86, shield: 1.15, rf: 0.55, rr: -0.55, rear: -0.75, rearSlope: 0.06, taper: 0.08, bulge: 0.03, flare: 0.09 },
    wedge: { L: 4.35, w: 1.92, r: 0.35, wb: 2.6, clear: 0.16, nose: 0.88, slope: 0.5, belt: 1.0, tail: 1.1, roof: 1.38, shield: 0.95, rf: -0.22, rr: -1.4, rear: -1.98, rearSlope: 0.15, taper: 0.12, bulge: 0.02, flare: 0.06 },
  };
  S.styles = Object.keys(STYLES);

  // a van: the glass runs right to the back, as a box
  const isBox = (p) => !!p.box;
  function lowerShape(p) {
    const s = new T.Shape();
    const L2 = p.L / 2, cl = p.clear, ra = p.r + 0.08, ay = p.r + 0.02;
    const fa = p.wb / 2, rax = -p.wb / 2;
    s.moveTo(-L2 + 0.26, cl);
    s.lineTo(rax - ra - 0.06, cl);
    s.absarc(rax, ay, ra, Math.PI, 0, true);
    s.lineTo(rax + ra + 0.06, cl);
    s.lineTo(fa - ra - 0.06, cl);
    s.absarc(fa, ay, ra, Math.PI, 0, true);
    s.lineTo(fa + ra + 0.06, cl);
    // the chin, then the nose leaning back to the bonnet
    s.lineTo(L2 - 0.24, cl + 0.02);
    s.quadraticCurveTo(L2 - 0.02, cl + 0.04, L2, cl + 0.24);
    s.lineTo(L2 - p.slope, p.nose - 0.07);
    s.quadraticCurveTo(L2 - p.slope - 0.03, p.nose, L2 - p.slope - 0.26, p.nose + 0.01);
    s.quadraticCurveTo(p.shield + 0.6, p.belt - 0.03, p.shield, p.belt);
    s.lineTo(p.rear + 0.05, p.belt + 0.01);
    s.quadraticCurveTo(-L2 + 0.36, p.tail + 0.02, -L2 + 0.1, p.tail - 0.01);
    // a little lip at the tail, the way they all have now
    s.quadraticCurveTo(-L2 - 0.03, p.tail - 0.04, -L2, p.tail - 0.2);
    s.lineTo(-L2 + 0.04, cl + 0.24);
    s.quadraticCurveTo(-L2 + 0.06, cl, -L2 + 0.26, cl);
    return s;
  }
  function cabinShape(p) {
    const s = new T.Shape();
    const b = p.belt - 0.04;
    s.moveTo(p.shield, b);
    s.quadraticCurveTo(p.rf + (p.shield - p.rf) * 0.32, p.roof - 0.01, p.rf, p.roof);
    s.lineTo(p.rr, p.roof);
    if (isBox(p)) {
      s.quadraticCurveTo(p.rear + 0.04, p.roof, p.rear + 0.05, p.roof - 0.12);
      s.lineTo(p.rear + 0.05, b);
    } else {
      s.quadraticCurveTo(p.rear + (p.rr - p.rear) * (1 - p.rearSlope), p.roof - 0.02, p.rear, b);
    }
    s.lineTo(p.shield, b);
    return s;
  }

  // Normals worked out from the faces: smoothed between faces that meet at a shallow angle and
  // left sharp where they meet at a crease, so curves read as curves and edges as edges.
  function creasedNormals(geo, angle) {
    const pos = geo.attributes.position;
    const n = pos.count;
    const cos = Math.cos(angle);
    const fnx = [], fny = [], fnz = [];
    const a = new T.Vector3(), b = new T.Vector3(), c = new T.Vector3(), ab = new T.Vector3(), cb = new T.Vector3();
    for (let i = 0; i < n; i += 3) {
      a.fromBufferAttribute(pos, i); b.fromBufferAttribute(pos, i + 1); c.fromBufferAttribute(pos, i + 2);
      cb.subVectors(c, b); ab.subVectors(a, b); cb.cross(ab);
      const len = cb.length() || 1;
      fnx.push(cb.x / len); fny.push(cb.y / len); fnz.push(cb.z / len);
    }
    const key = (i) => `${Math.round(pos.getX(i) * 1000)},${Math.round(pos.getY(i) * 1000)},${Math.round(pos.getZ(i) * 1000)}`;
    const map = new Map();
    for (let i = 0; i < n; i++) {
      const k = key(i);
      let l = map.get(k);
      if (!l) { l = []; map.set(k, l); }
      l.push(i);
    }
    const normals = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const f = (i / 3) | 0;
      const l = map.get(key(i));
      let x = 0, y = 0, z = 0;
      for (let j = 0; j < l.length; j++) {
        const g = (l[j] / 3) | 0;
        const d = fnx[f] * fnx[g] + fny[f] * fny[g] + fnz[f] * fnz[g];
        if (d >= cos) { x += fnx[g]; y += fny[g]; z += fnz[g]; }
      }
      const len = Math.hypot(x, y, z) || 1;
      normals[i * 3] = x / len; normals[i * 3 + 1] = y / len; normals[i * 3 + 2] = z / len;
    }
    geo.setAttribute('normal', new T.BufferAttribute(normals, 3));
    return geo;
  }

  // the outline with no edge longer than [step], so the body can bend along its length
  function resample(pts, step) {
    const out = [];
    for (let i = 0; i < pts.length; i++) {
      const a = pts[i], b = pts[(i + 1) % pts.length];
      const n = Math.max(1, Math.ceil(a.distanceTo(b) / step));
      for (let k = 0; k < n; k++) out.push(new T.Vector2(lerp(a.x, b.x, k / n), lerp(a.y, b.y, k / n)));
    }
    return out;
  }
  function inside(pt, poly) {
    let c = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const a = poly[i], b = poly[j];
      if ((a.y > pt.y) !== (b.y > pt.y) && pt.x < ((b.x - a.x) * (pt.y - a.y)) / (b.y - a.y) + a.x) c = !c;
    }
    return c;
  }
  function distToPoly(pt, poly) {
    let d = Infinity;
    for (let i = 0; i < poly.length; i++) {
      const a = poly[i], b = poly[(i + 1) % poly.length];
      const abx = b.x - a.x, aby = b.y - a.y;
      const t = clamp(((pt.x - a.x) * abx + (pt.y - a.y) * aby) / (abx * abx + aby * aby || 1), 0, 1);
      d = Math.min(d, Math.hypot(pt.x - (a.x + abx * t), pt.y - (a.y + aby * t)));
    }
    return d;
  }

  /*
   * A solid from a side profile: [width] across, the edge rounded with radius [round], and every
   * vertex then moved sideways by [shapeZ](x, y), the factor the half-width is multiplied by there.
   */
  function solidBody(shape, width, round, shapeZ) {
    const R = round, halfW = width / 2, K = 4;
    let pts = shape.extractPoints(16).shape;
    if (pts.length > 1 && pts[0].distanceTo(pts[pts.length - 1]) < 1e-5) pts.pop();
    if (T.ShapeUtils.isClockWise(pts)) pts.reverse();
    pts = resample(pts, 0.16);
    const n = pts.length;
    // each point's way outwards, and how far to step along it to inset the outline evenly
    const nx = [], ny = [], miter = [];
    for (let j = 0; j < n; j++) {
      const p0 = pts[(j - 1 + n) % n], p1 = pts[j], p2 = pts[(j + 1) % n];
      let ax = p1.x - p0.x, ay = p1.y - p0.y; let la = Math.hypot(ax, ay) || 1; ax /= la; ay /= la;
      let bx = p2.x - p1.x, by = p2.y - p1.y; let lb = Math.hypot(bx, by) || 1; bx /= lb; by /= lb;
      const n1x = ay, n1y = -ax, n2x = by, n2y = -bx;
      let vx = n1x + n2x, vy = n1y + n2y; const lv = Math.hypot(vx, vy) || 1; vx /= lv; vy /= lv;
      nx.push(vx); ny.push(vy);
      miter.push(1 / Math.max(0.45, vx * n1x + vy * n1y));
    }
    const verts = [];
    const ring = (inset, z) => {
      const start = verts.length / 3;
      for (let j = 0; j < n; j++) verts.push(pts[j].x - nx[j] * inset * miter[j], pts[j].y - ny[j] * inset * miter[j], z);
      return start;
    };
    // rings from one side's edge, across the walls, to the other side's
    const rings = [];
    for (let k = K; k >= 0; k--) { const f = (k / K) * Math.PI / 2; rings.push(ring(R * (1 - Math.cos(f)), -(halfW - R + R * Math.sin(f)))); }
    for (let k = 0; k <= K; k++) { const f = (k / K) * Math.PI / 2; rings.push(ring(R * (1 - Math.cos(f)), halfW - R + R * Math.sin(f))); }
    const idx = [];
    for (let r = 0; r < rings.length - 1; r++) {
      const A = rings[r], B = rings[r + 1];
      for (let j = 0; j < n; j++) {
        const j1 = (j + 1) % n;
        idx.push(A + j, A + j1, B + j1, A + j, B + j1, B + j);
      }
    }
    // the side panels: the inset outline, filled with a grid of points so they can bend
    const contour = [];
    for (let j = 0; j < n; j++) contour.push(new T.Vector2(pts[j].x - nx[j] * R * miter[j], pts[j].y - ny[j] * R * miter[j]));
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    contour.forEach((c) => { minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x); minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y); });
    const grid = [];
    const g = 0.1;
    for (let y = minY + g * 0.5; y < maxY; y += g) {
      for (let x = minX + g * 0.5; x < maxX; x += g) {
        const q = new T.Vector2(x, y);
        if (inside(q, contour) && distToPoly(q, contour) > g * 0.45) grid.push([q]);
      }
    }
    const tris = T.ShapeUtils.triangulateShape(contour.slice(), grid.map((h) => h.slice()));
    const all = contour.concat(grid.map((h) => h[0]));
    [-1, 1].forEach((side) => {
      const start = verts.length / 3;
      all.forEach((q) => verts.push(q.x, q.y, side * halfW));
      tris.forEach((t) => {
        const a = all[t[0]], b = all[t[1]], c = all[t[2]];
        const ccw = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) > 0;
        // the near side faces +z, so it wants its triangles anticlockwise seen from there
        if (ccw === (side > 0)) idx.push(start + t[0], start + t[1], start + t[2]);
        else idx.push(start + t[0], start + t[2], start + t[1]);
      });
    });
    for (let i = 0; i < verts.length; i += 3) verts[i + 2] *= shapeZ(verts[i], verts[i + 1]);
    let geo = new T.BufferGeometry();
    geo.setAttribute('position', new T.Float32BufferAttribute(verts, 3));
    geo.setIndex(idx);
    geo = geo.toNonIndexed();
    creasedNormals(geo, 0.62);
    // The side panels' normals straight from the shape, not from their triangles: worked out from
    // the triangles, a strip light reflected along a door came out as a stack of broken lines.
    const pos = geo.attributes.position, nrm = geo.attributes.normal, e = 0.01;
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
      if (Math.abs(Math.abs(z) - halfW * shapeZ(x, y)) > 1e-4) continue;
      const sgn = z > 0 ? 1 : -1;
      const gx = halfW * (shapeZ(x + e, y) - shapeZ(x - e, y)) / (2 * e);
      const gy = halfW * (shapeZ(x, y + e) - shapeZ(x, y - e)) / (2 * e);
      const l = Math.hypot(gx, gy, 1);
      nrm.setXYZ(i, -gx / l, -gy / l, sgn / l);
    }
    return geo;
  }

  // how much wider or narrower the body is at a point on its side profile
  function lowerShapeZ(p) {
    const L2 = p.L / 2, ay = p.r + 0.02, ra = p.r + 0.08;
    return (x, y) => {
      const t = Math.min(1, Math.abs(x) / L2);
      let f = 1 - p.taper * Math.pow(t, 4);
      const u = clamp((y - p.clear) / (p.belt - p.clear), 0, 1);
      f *= 1 + p.bulge * Math.sin(Math.PI * Math.pow(u, 0.7)) - 0.035 * Math.pow(1 - u, 3);
      // flared over the wheels
      [p.wb / 2, -p.wb / 2].forEach((ax) => {
        const d = Math.hypot(x - ax, y - ay);
        if (y > p.clear - 0.01) f += p.flare * Math.exp(-Math.pow((d - ra - 0.1) / 0.13, 2)) * (y < p.belt ? 1 : 0.4);
      });
      return f;
    };
  }
  function cabinShapeZ(p) {
    const mid = (p.shield + p.rear) / 2, half = Math.max(0.5, (p.shield - p.rear) / 2);
    return (x, y) => {
      const t = Math.min(1, Math.abs(x - mid) / half);
      const v = clamp((y - p.belt) / (p.roof - p.belt), 0, 1);
      return (1 - 0.1 * Math.pow(t, 4)) * (1 - (isBox(p) ? 0.06 : 0.2) * v * v);
    };
  }

  /*
   * The paint, two-tone the way cars are: dark trim along the sills and up the bumpers, and the
   * seams of the doors and bonnet as fine dark lines. Done in the paint's own shader from where on
   * the body a point is, so there are no extra pieces to line up.
   */
  function paintMaterial(col, dirty, clip, p) {
    const m = new T.MeshPhysicalMaterial({
      color: col, metalness: 0.32, roughness: 0.24 + dirty * 0.38, clearcoat: 1 - dirty * 0.75, clearcoatRoughness: 0.05 + dirty * 0.3,
      envMapIntensity: 1.15, clippingPlanes: [clip],
    });
    const doorA = p.shield - 0.12, doorB = isBox(p) ? p.shield - 1.05 : (p.rf + p.rr) / 2 - 0.05, doorC = isBox(p) ? p.rr + 0.25 : p.rr + 0.08;
    m.onBeforeCompile = (sh) => {
      sh.uniforms.uCut = { value: p.clear + 0.13 };
      sh.uniforms.uTrim = { value: new T.Color(0x0b0d11) };
      sh.uniforms.uSeams = { value: new T.Vector4(doorA, doorB, doorC, p.belt - 0.02) };
      sh.uniforms.uHood = { value: p.shield + 0.08 };
      sh.vertexShader = sh.vertexShader
        .replace('#include <common>', '#include <common>\nvarying vec3 vLocal;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvLocal = position;');
      sh.fragmentShader = sh.fragmentShader
        .replace('#include <common>', '#include <common>\nvarying vec3 vLocal; uniform float uCut; uniform vec3 uTrim; uniform vec4 uSeams; uniform float uHood;\nfloat seamLine(float d){ return 1.0 - smoothstep(0.004, 0.011, abs(d)); }')
        .replace('#include <color_fragment>', `#include <color_fragment>
          float trimK = smoothstep(uCut - 0.006, uCut + 0.006, vLocal.y);
          diffuseColor.rgb = mix(uTrim, diffuseColor.rgb, trimK);
          float doorRange = step(uCut, vLocal.y) * step(vLocal.y, uSeams.w);
          float seam = (seamLine(vLocal.x - uSeams.x) + seamLine(vLocal.x - uSeams.y) + seamLine(vLocal.x - uSeams.z)) * doorRange;
          seam += seamLine(vLocal.x - uHood) * step(uSeams.w - 0.25, vLocal.y);
          diffuseColor.rgb *= 1.0 - 0.85 * clamp(seam, 0.0, 1.0);`)
        .replace('#include <roughnessmap_fragment>', '#include <roughnessmap_fragment>\nroughnessFactor = mix(0.62, roughnessFactor, trimK);')
        .replace('#include <metalnessmap_fragment>', '#include <metalnessmap_fragment>\nmetalnessFactor = mix(0.1, metalnessFactor, trimK);');
    };
    return m;
  }

  function tireGeometry(r, w) {
    const pts = [];
    const ri = r * 0.64, wr = w / 2;
    pts.push(new T.Vector2(ri, -wr));
    for (let i = 0; i <= 10; i++) {
      const a = -Math.PI / 2 + (i / 10) * Math.PI;
      pts.push(new T.Vector2(r - 0.06 + Math.cos(a) * 0.06, Math.sin(a) * wr));
    }
    pts.push(new T.Vector2(ri, wr));
    const g = new T.LatheGeometry(pts, 40);
    g.rotateX(Math.PI / 2);
    return g;
  }

  let car = null;
  const PAINTS = [0xc81d3b, 0x1d5fc8, 0xe8e8ea, 0x1a1c20, 0xe2a21b, 0x2fae6a, 0x7a2fc8, 0x9aa3ad, 0x0f6b72, 0xd94f12, 0xff6fae, 0x3a3f2a];
  const ACCENTS = [0x29f0ff, 0xff2bd6, 0xffb020, 0x3dff9a, 0xff3b5c, 0x8a7dff];
  S.paints = PAINTS;
  S.accents = ACCENTS;

  // a cone of light from a headlight, narrow at the lamp, fading out ahead of the car
  function headBeam(len, r, hex, alpha) {
    const geo = new T.CylinderGeometry(0.05, r, len, 24, 1, true);
    geo.translate(0, -len / 2, 0);
    const mat = new T.ShaderMaterial({
      uniforms: { uColor: { value: new T.Color(hex) }, uAlpha: { value: alpha }, uH: { value: len } },
      vertexShader: `
        varying float vY; varying vec3 vN; varying vec3 vV;
        void main(){
          vY = position.y;
          vec4 w = modelMatrix * vec4(position,1.0);
          vN = normalize(mat3(modelMatrix) * normal);
          vV = normalize(cameraPosition - w.xyz);
          gl_Position = projectionMatrix * viewMatrix * w;
        }`,
      fragmentShader: `
        uniform vec3 uColor; uniform float uAlpha; uniform float uH;
        varying float vY; varying vec3 vN; varying vec3 vV;
        void main(){
          float edge = abs(dot(normalize(vN), normalize(vV)));
          float fall = clamp(1.0 + vY / uH, 0.0, 1.0);
          float a = pow(edge, 1.4) * fall * fall * uAlpha;
          gl_FragColor = vec4(uColor * a, a);
        }`,
      transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.DoubleSide, toneMapped: false,
    });
    const m = new T.Mesh(geo, mat);
    // the cone's length runs along the car, forwards
    m.rotation.z = Math.PI / 2;
    m.userData.fx = true;
    return m;
  }

  // the parts the scanner can see through the body, and where on the car they are
  function partLayout(p) {
    const fa = p.wb / 2, ra = -p.wb / 2;
    const wz = p.w / 2 - 0.13;
    return {
      engine: [fa - 0.25, p.clear + 0.42, 0],
      igniters: [fa - 0.25, p.clear + 0.75, 0],
      oil: [fa - 0.3, p.clear + 0.1, 0],
      coolant: [fa + 0.45, p.nose - 0.12, p.w * 0.26],
      battery: [0, p.clear + 0.1, 0],
      aicore: [p.shield - 0.45, p.belt + 0.08, 0],
      firmware: [p.shield - 0.25, p.belt - 0.1, p.w * 0.22],
      lights: [p.L / 2 + 0.02, p.nose - 0.16, p.w * 0.3],
      exhaust: [ra - 0.4, p.clear + 0.05, p.w * 0.25],
      body: [ra + 0.3, (p.clear + p.belt) / 2 + 0.05, p.w / 2 + 0.02],
      wheelF: [fa, p.r + 0.02, wz],
      wheelR: [ra, p.r + 0.02, wz],
      brakesF: [fa, p.r + 0.02, wz - 0.1],
      brakesR: [ra, p.r + 0.02, wz - 0.1],
      suspF: [fa, p.r + 0.32, wz - 0.18],
      suspR: [ra, p.r + 0.32, wz - 0.18],
      junk: [fa + 0.1, p.clear + 0.55, 0.2],
    };
  }

  const holoParts = {};
  function buildCar(spec) {
    const p = Object.assign({}, STYLES[spec.style] || STYLES.sedan);
    const group = new T.Group();
    const body = new T.Group();      // the paint and glass, what the scanner sees through
    const insides = new T.Group();   // the parts, seen only through the scanner
    const wheels = [];
    group.add(body); group.add(insides);
    insides.visible = false;

    const dirty = spec.grime || 0;
    // three takes a hex colour as linear light; paint is chosen by eye, so it's converted
    const paintCol = new T.Color(spec.paint).convertSRGBToLinear();
    if (dirty > 0) paintCol.lerp(new T.Color(0x3a3328).convertSRGBToLinear(), dirty * 0.35);
    // the paint shows behind the scanner's sweep (x below it), the hologram ahead of it
    const clip = new T.Plane(new T.Vector3(-1, 0, 0), 999);
    const holoClip = new T.Plane(new T.Vector3(1, 0, 0), -999);
    const paint = paintMaterial(paintCol, dirty, clip, p);
    const glass = new T.MeshPhysicalMaterial({ color: 0x04060a, metalness: 0.4, roughness: 0.06, clearcoat: 1, clearcoatRoughness: 0.02, envMapIntensity: 1.6, clippingPlanes: [clip] });
    const trim = new T.MeshStandardMaterial({ color: 0x15181d, roughness: 0.5, metalness: 0.5, clippingPlanes: [clip] });
    const chrome = new T.MeshStandardMaterial({ color: 0xd0d6de, roughness: 0.12, metalness: 1, clippingPlanes: [clip] });
    const blackClip = new T.MeshBasicMaterial({ color: 0, fog: false, clippingPlanes: [clip] });
    const holo = withClipVaryings(holoMaterial(0x29f0ff));
    holo.clippingPlanes = [holoClip];
    holo.clipping = true;
    const accent = new T.Color(spec.accent);

    const zLower = lowerShapeZ(p);
    const lower = new T.Mesh(solidBody(lowerShape(p), p.w, 0.075, zLower), paint);
    lower.userData.black = blackClip;
    body.add(lower);
    const cabin = new T.Mesh(solidBody(cabinShape(p), p.w * 0.86, 0.07, cabinShapeZ(p)), glass);
    cabin.userData.black = blackClip;
    body.add(cabin);
    // the holographic twins of the body, shown behind the scanner's sweep
    const hl = new T.Mesh(lower.geometry, holo); hl.userData.glow = true; hl.renderOrder = 5; body.add(hl);
    const hc = new T.Mesh(cabin.geometry, holo); hc.userData.glow = true; hc.renderOrder = 5; body.add(hc);
    hl.visible = hc.visible = false;

    // dark wheel wells, so the arches aren't windows through the car
    [p.wb / 2, -p.wb / 2].forEach((ax) => {
      const well = new T.Mesh(new T.BoxGeometry((p.r + 0.08) * 2, p.r + 0.08, p.w - 0.34), trim);
      well.position.set(ax, p.r + 0.02 + (p.r + 0.08) / 2 - 0.02, 0);
      well.userData.black = blackClip;
      body.add(well);
    });
    // a chrome line under the side windows
    const zCab = cabinShapeZ(p);
    [1, -1].forEach((sgn) => {
      const len = (p.shield - p.rear) * 0.9;
      const cx = (p.shield + p.rear) / 2;
      const ch = new T.Mesh(new T.BoxGeometry(len, 0.018, 0.01), chrome);
      ch.position.set(cx, p.belt - 0.01, sgn * (p.w * 0.86 / 2) * zCab(cx, p.belt - 0.01) + sgn * 0.004);
      ch.userData.black = blackClip;
      body.add(ch);
    });

    // the lights: a bar across the nose and one across the tail, and a line down each side
    const L2 = p.L / 2;
    const faceTop = p.nose - 0.07, faceBot = p.clear + 0.24;
    const lean = Math.atan2(p.slope, faceTop - faceBot);
    const fy = lerp(faceBot, faceTop, 0.72), fx = lerp(L2, L2 - p.slope, 0.72);
    const frontW = p.w * 0.8 * zLower(fx, fy);
    const front = new T.Mesh(new T.BoxGeometry(0.03, 0.045, frontW), new T.MeshBasicMaterial({ color: new T.Color(0xe6fbff).multiplyScalar(1.6), toneMapped: false, clippingPlanes: [clip] }));
    front.position.set(fx + 0.012, fy, 0);
    front.rotation.z = lean;
    front.userData.glow = true;
    body.add(front);
    const ry = p.tail - 0.14;
    const rear = new T.Mesh(new T.BoxGeometry(0.03, 0.05, p.w * 0.8 * zLower(-L2, ry)), new T.MeshBasicMaterial({ color: new T.Color(0xff1030).multiplyScalar(1.6), toneMapped: false, clippingPlanes: [clip] }));
    rear.position.set(-L2 - 0.012, ry, 0);
    rear.userData.glow = true;
    body.add(rear);
    const sideY = p.clear + (p.belt - p.clear) * 0.5;
    const sideLen = p.wb - (p.r + 0.08) * 2 - 0.3;
    const sideZ = (p.w / 2) * zLower(0, sideY) + 0.006;
    const sideMat = new T.MeshBasicMaterial({ color: accent.clone().multiplyScalar(1.5), toneMapped: false, clippingPlanes: [clip] });
    [1, -1].forEach((sgn) => {
      const line = new T.Mesh(new T.BoxGeometry(sideLen, 0.016, 0.012), sideMat);
      line.position.set(0, sideY, sgn * sideZ);
      line.userData.glow = true;
      body.add(line);
    });
    // a pickup's bed, under a cover
    if (spec.style === 'pickup') {
      const bed = new T.Mesh(new T.BoxGeometry(L2 + p.rear - 0.2, 0.04, p.w * 0.78), trim);
      bed.position.set((-L2 + p.rear) / 2 - 0.02, p.tail + 0.01, 0);
      bed.userData.black = blackClip;
      body.add(bed);
    }
    // the headlights' beams through the haze
    const beams = [1, -1].map((sgn) => {
      const m = headBeam(4.2, 1.0, 0xdff6ff, 0.12);
      m.position.set(fx + 0.02, fy, sgn * frontW * 0.38);
      group.add(m);
      return m;
    });
    // what the headlights throw on the floor
    const throwLight = spill(p.L / 2 + 2.2, 0.02, 0, 4.5, 0xdff6ff, 0.25, group);
    throwLight.scale.set(1.3, 0.8, 1);
    // underglow
    const under = spill(0, 0.02, 0, 1, spec.accent, 0.55, group);
    under.scale.set(p.L * 1.15, p.w * 1.5, 1);
    under.userData.under = true;

    // wheels
    const tg = tireGeometry(p.r, 0.25);
    const rimMat = new T.MeshStandardMaterial({ color: 0xa8b0bb, roughness: 0.25, metalness: 0.95 });
    const ringMat = new T.MeshBasicMaterial({ color: accent.clone().multiplyScalar(1.6), toneMapped: false });
    [[p.wb / 2, 1, 'F'], [-p.wb / 2, 1, 'R'], [p.wb / 2, -1, 'F2'], [-p.wb / 2, -1, 'R2']].forEach(([x, sgn, id]) => {
      const wg = new T.Group();
      wg.position.set(x, p.r + 0.02, sgn * (p.w / 2 - 0.13));
      const spin = new T.Group();
      wg.add(spin);
      const tire = new T.Mesh(tg, M.rubber);
      spin.add(tire);
      const rim = new T.Mesh(new T.CylinderGeometry(p.r * 0.62, p.r * 0.62, 0.2, 28), rimMat);
      rim.rotation.x = Math.PI / 2;
      spin.add(rim);
      for (let k = 0; k < 5; k++) {
        const sp = new T.Mesh(new T.BoxGeometry(p.r * 1.1, 0.05, 0.04), rimMat);
        sp.rotation.z = (k / 5) * Math.PI;
        sp.position.z = sgn * 0.11;
        spin.add(sp);
      }
      const hub = new T.Mesh(new T.CylinderGeometry(0.06, 0.06, 0.24, 12), M.darkMetal);
      hub.rotation.x = Math.PI / 2;
      spin.add(hub);
      const ring = new T.Mesh(new T.TorusGeometry(p.r * 0.66, 0.012, 6, 48), ringMat);
      ring.position.z = sgn * 0.105;
      ring.userData.glow = true;
      spin.add(ring);
      group.add(wg);
      wheels.push({ g: wg, spin, tire, id, x, sgn });
    });

    // a spoiler for the ones that think they're fast
    if (spec.style === 'sport' || spec.style === 'wedge') {
      const sp = new T.Mesh(new T.BoxGeometry(0.35, 0.04, p.w * 0.9), trim);
      sp.position.set(-p.L / 2 + 0.25, p.tail + 0.18, 0);
      sp.userData.black = blackClip;
      body.add(sp);
      [-1, 1].forEach((s) => { const st = new T.Mesh(new T.BoxGeometry(0.06, 0.18, 0.04), trim); st.position.set(-p.L / 2 + 0.3, p.tail + 0.08, s * p.w * 0.32); st.userData.black = blackClip; body.add(st); });
    }
    // a roof rack, for the ones that need to carry their whole life about
    if (spec.style === 'van' || spec.style === 'hatch') {
      [-1, 1].forEach((s) => { const rk = new T.Mesh(new T.BoxGeometry(Math.abs(p.rf - p.rr) * 0.8, 0.04, 0.04), trim); rk.position.set((p.rf + p.rr) / 2, p.roof + 0.06, s * p.w * 0.33); rk.userData.black = blackClip; body.add(rk); });
    }

    // the insides, for the scanner
    const lay = partLayout(p);
    const hp = {};
    const partMat = () => new T.MeshBasicMaterial({ color: new T.Color(0x29f0ff).multiplyScalar(0.7), toneMapped: false, transparent: true, opacity: 0.9, wireframe: false });
    const addPart = (id, geo, at, rot) => {
      const m = new T.Mesh(geo, partMat());
      m.position.set(at[0], at[1], at[2]);
      if (rot) m.rotation.set(rot[0], rot[1], rot[2]);
      m.userData.glow = true;
      insides.add(m);
      const edges = new T.LineSegments(new T.EdgesGeometry(geo), new T.LineBasicMaterial({ color: 0xbffcff, toneMapped: false, transparent: true, opacity: 0.8 }));
      edges.userData.glow = true;
      m.add(edges);
      hp[id] = (hp[id] || []).concat([m]);
      return m;
    };
    addPart('engine', new T.BoxGeometry(0.85, 0.5, 0.9), lay.engine);
    for (let k = 0; k < 4; k++) addPart('igniters', new T.CylinderGeometry(0.04, 0.04, 0.18, 8), [lay.igniters[0] - 0.3 + k * 0.2, lay.igniters[1], 0]);
    addPart('oil', new T.BoxGeometry(0.6, 0.12, 0.6), lay.oil);
    addPart('coolant', new T.CylinderGeometry(0.11, 0.11, 0.26, 16), lay.coolant);
    addPart('battery', new T.BoxGeometry(p.wb * 0.6, 0.12, p.w * 0.66), lay.battery);
    addPart('aicore', new T.IcosahedronGeometry(0.15, 1), lay.aicore);
    addPart('firmware', new T.BoxGeometry(0.22, 0.12, 0.3), lay.firmware);
    addPart('exhaust', new T.CylinderGeometry(0.06, 0.06, p.wb * 0.9, 10), [lay.exhaust[0] + p.wb * 0.3, lay.exhaust[1] + 0.05, lay.exhaust[2]], [0, 0, Math.PI / 2]);
    addPart('exhaust', new T.BoxGeometry(0.5, 0.18, 0.26), lay.exhaust);
    addPart('lights', new T.BoxGeometry(0.06, 0.07, p.w * 0.84), [p.L / 2 + 0.02, lay.lights[1], 0]);
    addPart('brakesF', new T.CylinderGeometry(p.r * 0.5, p.r * 0.5, 0.04, 24), lay.brakesF, [Math.PI / 2, 0, 0]);
    addPart('brakesR', new T.CylinderGeometry(p.r * 0.5, p.r * 0.5, 0.04, 24), lay.brakesR, [Math.PI / 2, 0, 0]);
    addPart('suspF', new T.CylinderGeometry(0.07, 0.07, 0.36, 10), lay.suspF);
    addPart('suspR', new T.CylinderGeometry(0.07, 0.07, 0.36, 10), lay.suspR);
    addPart('junk', new T.IcosahedronGeometry(0.14, 0), lay.junk);
    hp.junk[0].visible = false;
    // whatever is living in the engine looks out through the grille now and then
    const eyes = new T.Group();
    eyes.position.set(p.L / 2 + 0.09, p.nose - 0.32, 0.14);
    eyes.visible = false;
    [-0.05, 0.05].forEach((z) => { const e = new T.Mesh(new T.SphereGeometry(0.018, 8, 6), new T.MeshBasicMaterial({ color: new T.Color(0xfff27a).multiplyScalar(2), toneMapped: false })); e.position.z = z; e.userData.glow = true; eyes.add(e); });
    body.add(eyes);

    // the crack the welder closes: a jagged line down the side
    const crack = new T.Mesh(new T.PlaneGeometry(0.7, 0.32), new T.MeshBasicMaterial({ map: crackTex, transparent: true, toneMapped: false, depthWrite: false, color: new T.Color(1.4, 0.5, 0.3), polygonOffset: true, polygonOffsetFactor: -2 }));
    crack.position.set(lay.body[0], lay.body[1], (p.w / 2) * zLower(lay.body[0], lay.body[1]) + 0.012);
    crack.visible = false;
    crack.userData.glow = true;
    body.add(crack);

    group.userData = { p, body, insides, wheels, paint, glass, holo, holoTwins: [hl, hc], clip, holoClip, lay, hp, eyes, crack, under, throwLight, front, rear, beams };
    return group;
  }

  const crackTex = canvasTex(256, 128, (g, w, h) => {
    g.clearRect(0, 0, w, h);
    g.strokeStyle = '#ffffff'; g.lineWidth = 3; g.lineJoin = 'round';
    g.beginPath(); let x = 10, y = h / 2; g.moveTo(x, y);
    while (x < w - 10) { x += rnd(10, 22); y = clamp(y + rnd(-18, 18), 15, h - 15); g.lineTo(x, y); }
    g.stroke();
    g.lineWidth = 1.5;
    for (let i = 0; i < 6; i++) { g.beginPath(); const sx = rnd(30, w - 30), sy = rnd(30, h - 30); g.moveTo(sx, sy); g.lineTo(sx + rnd(-25, 25), sy + rnd(-25, 25)); g.stroke(); }
  }, false);

  // ---------------------------------------------------------------- car on stage
  const CAR_IN_X = -16;
  const stage = { lifted: 0, hover: 0, xray: 0, wheelSpin: 0, driving: false };

  S.newCar = function (spec) {
    if (car) { scene.remove(car); disposeTree(car); }
    car = buildCar(spec);
    car.position.set(CAR_IN_X, 0, 0);
    scene.add(car);
    if (liftTw) { liftTw.dead = true; liftTw = null; }
    stage.lifted = 0; stage.xray = 0; stage.driving = false;
    setXray(false, true);
    glow.dirty = true;
    S.setFaults({});
    return car;
  };
  // straight onto the lift, for the title screen
  S.parkCar = function () { if (car) car.position.x = 0; };
  S.hasCar = () => !!car;
  S.removeCar = function () {
    if (car) { scene.remove(car); disposeTree(car); car = null; glow.dirty = true; }
  };
  function disposeTree(o) {
    o.traverse((c) => {
      if (c.geometry) c.geometry.dispose();
      if (c.material) {
        const ms = Array.isArray(c.material) ? c.material : [c.material];
        ms.forEach((m) => { if (m !== M.rubber && m !== M.metal && m !== M.darkMetal) m.dispose(); });
      }
    });
  }

  // Each animation of a car holds on to that car: if it is taken away (a collapse, the end of the
  // day) or replaced by the next customer's, the animation stops rather than moving the wrong car.
  S.driveIn = function (done) {
    if (!car) return done && done();
    const c = car, d = c.userData;
    d.front.material.color.setScalar(1.6);
    stage.driving = true;
    const from = CAR_IN_X;
    let last = from;
    const tw = tween(3.2, (k) => {
      if (car !== c) { tw.dead = true; return; }
      const e = easeOut(k);
      const x = lerp(from, 0, e);
      stage.wheelSpin += (x - last) / d.p.r;
      last = x;
      c.position.x = x;
      // the nose dips as it brakes
      c.rotation.z = k > 0.7 ? -Math.sin((k - 0.7) / 0.3 * Math.PI) * 0.018 : 0;
    }, () => { stage.driving = false; c.rotation.z = 0; if (done) done(); });
  };
  S.driveOut = function (done) {
    if (!car) return done && done();
    const c = car, d = c.userData;
    stage.driving = true;
    let last = 0;
    const tw = tween(2.6, (k) => {
      if (car !== c) { tw.dead = true; stage.driving = false; return; }
      const x = lerp(0, CAR_IN_X - 2, easeIn(k));
      stage.wheelSpin += (x - last) / d.p.r;
      last = x;
      c.position.x = x;
    }, () => { stage.driving = false; if (car === c) S.removeCar(); if (done) done(); });
  };
  // one lift at a time: a new move (or a new car) takes over from whatever the lift was doing
  let liftTw = null;
  S.lift = function (up, done) {
    if (liftTw) liftTw.dead = true;
    const from = stage.lifted, to = up ? 1 : 0;
    const tw = liftTw = tween(1.6, (k) => { stage.lifted = lerp(from, to, easeInOut(k)); }, () => { if (liftTw === tw) liftTw = null; if (done) done(); });
  };

  // the scanner: a sheet of light sweeps the car from front to back and leaves it see-through
  S.scan = function (done) {
    if (!car) return done && done();
    const c = car, d = c.userData;
    const L2 = d.p.L / 2 + 0.3;
    d.insides.visible = true;
    d.holoTwins.forEach((m) => { m.visible = true; });
    if (!G.scanPlane) {
      G.scanPlane = new T.Mesh(new T.PlaneGeometry(1, 1), new T.MeshBasicMaterial({ color: new T.Color(0x29f0ff).multiplyScalar(0.5), transparent: true, opacity: 0.1, side: T.DoubleSide, toneMapped: false, depthWrite: false, blending: T.AdditiveBlending }));
      G.scanPlane.userData.glow = true;
      const edge = new T.Mesh(new T.BoxGeometry(0.012, 1, 1.02), neon(0x9ff8ff, 1.2));
      edge.userData.glow = true;
      G.scanPlane.add(edge);
      scene.add(G.scanPlane);
      glow.dirty = true;
    }
    G.scanPlane.visible = true;
    G.scanPlane.rotation.y = Math.PI / 2;
    G.scanPlane.scale.set(d.p.w + 0.8, d.p.roof + 0.6, 1);
    const tw = tween(2.2, (k) => {
      if (car !== c) { tw.dead = true; G.scanPlane.visible = false; return; }
      const x = lerp(L2, -L2, easeInOut(k));
      // clip planes are in world space, so the car's own position is added to the sweep's
      const sx = x + c.position.x;
      d.clip.constant = sx;
      d.holoClip.constant = -sx;
      G.scanPlane.position.set(c.position.x + x, c.position.y + (d.p.roof + 0.6) / 2, c.position.z);
      d.insides.children.forEach((m) => { if (m.material) m.material.opacity = clamp((m.position.x - x) * 2, 0, 0.9); });
    }, () => {
      G.scanPlane.visible = false;
      if (car !== c) return;
      stage.xray = 1;
      setXray(true, true);
      if (done) done();
    });
  };
  function setXray(on, instant) {
    if (!car) return;
    const d = car.userData;
    stage.xray = on ? 1 : 0;
    if (on) {
      d.clip.constant = -999; d.holoClip.constant = 999;
      d.insides.visible = true;
      d.holoTwins.forEach((m) => { m.visible = true; });
      d.insides.children.forEach((m) => { if (m.material && m.userData.state !== 'hidden') m.material.opacity = 0.9; });
    } else {
      d.clip.constant = 999; d.holoClip.constant = -999;
      d.insides.visible = false;
      d.holoTwins.forEach((m) => { m.visible = false; });
    }
    void instant;
  }
  S.xray = function (on) { setXray(on); };
  S.isXray = () => stage.xray > 0;

  // which parts are broken (red, beating), fixed (green), or not yet found
  const STATE_COL = { bad: 0xff2a4a, fixed: 0x3dff9a, ok: 0x29f0ff, hidden: 0x29f0ff, work: 0xffb020 };
  let faultState = {};
  S.setFaults = function (map) {
    faultState = map || {};
    if (!car) return;
    const d = car.userData;
    Object.keys(d.hp).forEach((id) => {
      const st = faultState[id] || 'ok';
      d.hp[id].forEach((m) => {
        m.userData.state = st;
        m.material.color.set(STATE_COL[st]).multiplyScalar(st === 'ok' || st === 'hidden' ? 0.45 : 1.3);
        m.children.forEach((e) => e.material.color.set(STATE_COL[st]).multiplyScalar(st === 'ok' || st === 'hidden' ? 0.6 : 1.6));
      });
    });
    d.hp.junk[0].visible = !!faultState.junk && faultState.junk !== 'fixed';
    d.eyes.visible = !!faultState.junk && faultState.junk !== 'fixed';
    d.crack.visible = !!faultState.body && faultState.body !== 'fixed';
    // a flat tyre sits flat
    const flatF = faultState.wheelF && faultState.wheelF !== 'fixed' && faultState._flat === 'F';
    const flatR = faultState.wheelR && faultState.wheelR !== 'fixed' && faultState._flat === 'R';
    d.wheels.forEach((w) => {
      const flat = (w.id === 'F' && flatF) || (w.id === 'R' && flatR);
      w.tire.scale.set(flat ? 1.06 : 1, flat ? 0.86 : 1, 1);
      w.tire.position.y = flat ? -0.04 : 0;
    });
    d.flat = flatF ? 'F' : flatR ? 'R' : '';
    // broken lights flicker; a dead battery leaves the car dark
    d.lightsBroken = !!faultState.lights && faultState.lights !== 'fixed';
    d.dead = !!faultState.battery && faultState.battery !== 'fixed';
  };

  // where a part is, in the world, for the markers over it
  const tmpV = new T.Vector3();
  S.partWorld = function (id, out) {
    out = out || new T.Vector3();
    if (!car) return out.set(0, 1, 0);
    const lay = car.userData.lay;
    const a = lay[id] || lay.engine;
    out.set(a[0], a[1], a[2]);
    return car.localToWorld(out);
  };
  // the same on screen, in CSS pixels; z > 1 means behind the camera
  S.project = function (v) {
    tmpV.copy(v).project(camera);
    return { x: (tmpV.x * 0.5 + 0.5) * W, y: (-tmpV.y * 0.5 + 0.5) * H, z: tmpV.z };
  };
  S.partScreen = function (id) {
    return S.project(S.partWorld(id, new T.Vector3()));
  };

  // ---------------------------------------------------------------- effects: sparks and steam
  const SPARKS = 220;
  const fx = { sp: [], pos: null, col: null, geo: null, pts: null, smoke: [] };
  function buildFx() {
    fx.pos = new Float32Array(SPARKS * 3);
    fx.col = new Float32Array(SPARKS * 3);
    fx.geo = new T.BufferGeometry();
    fx.geo.setAttribute('position', new T.BufferAttribute(fx.pos, 3));
    fx.geo.setAttribute('color', new T.BufferAttribute(fx.col, 3));
    fx.pts = new T.Points(fx.geo, new T.PointsMaterial({ size: 0.06, vertexColors: true, transparent: true, blending: T.AdditiveBlending, depthWrite: false, toneMapped: false, map: radialTex }));
    fx.pts.frustumCulled = false;
    fx.pts.userData.glow = true;
    scene.add(fx.pts);
    for (let i = 0; i < SPARKS; i++) fx.sp.push({ life: 0, x: 0, y: -10, z: 0, vx: 0, vy: 0, vz: 0, r: 1, g: 1, b: 1 });
    for (let i = 0; i < 24; i++) {
      const s = new T.Sprite(new T.SpriteMaterial({ map: radialTex, color: 0xcfd8e6, transparent: true, opacity: 0, depthWrite: false }));
      s.visible = false;
      s.userData.fx = true;
      scene.add(s);
      fx.smoke.push({ s, life: 0, max: 1, vy: 0 });
    }
  }
  S.sparks = function (at, n, hex) {
    const c = new T.Color(hex || 0xffb84a);
    for (let k = 0, i = 0; i < SPARKS && k < (n || 30); i++) {
      const p = fx.sp[i];
      if (p.life > 0) continue;
      p.life = rnd(0.4, 1.0);
      p.x = at.x; p.y = at.y; p.z = at.z;
      p.vx = rnd(-2.5, 2.5); p.vy = rnd(0.5, 4); p.vz = rnd(-2.5, 2.5);
      p.r = c.r * 2; p.g = c.g * 2; p.b = c.b * 2;
      k++;
    }
  };
  S.steam = function (at, n, hex) {
    for (let k = 0, i = 0; i < fx.smoke.length && k < (n || 6); i++) {
      const p = fx.smoke[i];
      if (p.life > 0) continue;
      p.life = p.max = rnd(1.4, 2.6);
      p.vy = rnd(0.4, 0.9);
      p.s.visible = true;
      p.s.material.color.set(hex || 0xcfd8e6);
      p.s.position.set(at.x + rnd(-0.2, 0.2), at.y, at.z + rnd(-0.2, 0.2));
      k++;
    }
  };
  S.burst = function (id, kind) {
    const v = S.partWorld(id, new T.Vector3());
    if (kind === 'steam') S.steam(v, 8);
    else if (kind === 'smoke') S.steam(v, 8, 0x55585e);
    else if (kind === 'good') S.sparks(v, 40, 0x3dff9a);
    else S.sparks(v, 40);
  };
  function stepFx(dt) {
    for (let i = 0; i < SPARKS; i++) {
      const p = fx.sp[i];
      if (p.life > 0) {
        p.life -= dt;
        p.vy -= 9.8 * dt;
        p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
        if (p.y < 0.02) { p.y = 0.02; p.vy *= -0.35; p.vx *= 0.6; p.vz *= 0.6; }
        const f = Math.max(0, p.life);
        fx.pos[i * 3] = p.x; fx.pos[i * 3 + 1] = p.y; fx.pos[i * 3 + 2] = p.z;
        fx.col[i * 3] = p.r * f; fx.col[i * 3 + 1] = p.g * f; fx.col[i * 3 + 2] = p.b * f;
      } else {
        fx.pos[i * 3 + 1] = -50;
      }
    }
    fx.geo.attributes.position.needsUpdate = true;
    fx.geo.attributes.color.needsUpdate = true;
    fx.smoke.forEach((p) => {
      if (p.life <= 0) return;
      p.life -= dt;
      const k = 1 - p.life / p.max;
      p.s.position.y += p.vy * dt;
      p.s.position.x += Math.sin(time * 2 + p.max * 10) * 0.2 * dt;
      const sc = 0.3 + k * 1.4;
      p.s.scale.set(sc, sc, sc);
      p.s.material.opacity = Math.sin(k * Math.PI) * 0.4;
      if (p.life <= 0) p.s.visible = false;
    });
  }

  // ---------------------------------------------------------------- camera
  const VIEWS = {
    title: { pos: [6.5, 2.2, 7.5], at: [-0.5, 1.1, -1] },
    overview: { pos: [5.6, 2.7, 6.6], at: [-0.4, 0.95, -0.4] },
    front: { pos: [5.2, 2.4, 2.8], at: [1.3, 1.1, 0.1] },
    rear: { pos: [-4.8, 2.3, 3.4], at: [-1.4, 1.1, 0.1] },
    wheelF: { pos: [2.9, 1.4, 3.6], at: [1.35, 0.95, 0.7] },
    wheelR: { pos: [0.2, 1.4, 4.0], at: [-1.35, 0.95, 0.7] },
    side: { pos: [0.4, 1.9, 5.4], at: [0, 1.05, 0] },
    cabin: { pos: [2.6, 3.2, 3.2], at: [0.4, 1.5, 0] },
    under: { pos: [3.4, 0.55, 3.8], at: [0, 0.75, 0] },
    door: { pos: [3.5, 2.2, 6.5], at: [-6, 1.2, 1] },
    end: { pos: [8.5, 4.2, 9.5], at: [-1, 1.5, -2] },
  };
  // which view shows a part best
  S.viewFor = function (id) {
    return ({ engine: 'front', igniters: 'front', oil: 'under', coolant: 'front', battery: 'under', aicore: 'cabin', firmware: 'cabin', lights: 'front', exhaust: 'rear', body: 'side', wheelF: 'wheelF', wheelR: 'wheelR', brakesF: 'wheelF', brakesR: 'wheelR', suspF: 'wheelF', suspR: 'wheelR', junk: 'front' })[id] || 'overview';
  };
  const cam = { pos: new T.Vector3(), at: new T.Vector3(), fromPos: new T.Vector3(), fromAt: new T.Vector3(), toPos: new T.Vector3(), toAt: new T.Vector3(), k: 1, dur: 1, view: 'title', orbit: false, shake: 0, sway: 0 };
  S.focus = function (name, instant, dur) {
    const v = VIEWS[name] || VIEWS.overview;
    cam.view = name;
    cam.orbit = name === 'title';
    cam.fromPos.copy(cam.pos); cam.fromAt.copy(cam.at);
    cam.toPos.set(v.pos[0], v.pos[1], v.pos[2]);
    cam.toAt.set(v.at[0], v.at[1], v.at[2]);
    // a lifted car is looked at a little higher
    if (name !== 'title' && name !== 'door' && name !== 'end') {
      cam.toPos.y += stage.lifted * 0.55; cam.toAt.y += stage.lifted * 0.62;
    }
    if (instant) { cam.pos.copy(cam.toPos); cam.at.copy(cam.toAt); cam.k = 1; }
    else { cam.k = 0; cam.dur = dur || 1.1; }
  };
  S.view = () => cam.view;
  S.shake = function (a) { cam.shake = Math.max(cam.shake, a); };
  // how tired the one holding the camera is: 0 fresh, 1 asleep on their feet
  S.setSway = function (s) { cam.sway = s; };
  function stepCamera(dt) {
    if (cam.k < 1) {
      cam.k = Math.min(1, cam.k + dt / cam.dur);
      const e = easeInOut(cam.k);
      cam.pos.lerpVectors(cam.fromPos, cam.toPos, e);
      cam.at.lerpVectors(cam.fromAt, cam.toAt, e);
    }
    let px = cam.pos.x, py = cam.pos.y, pz = cam.pos.z;
    if (cam.orbit) {
      const a = Math.sin(time * 0.12) * 0.35;
      const r = Math.hypot(cam.toPos.x - cam.at.x, cam.toPos.z - cam.at.z);
      const base = Math.atan2(cam.toPos.z - cam.at.z, cam.toPos.x - cam.at.x);
      px = cam.at.x + Math.cos(base + a) * r;
      pz = cam.at.z + Math.sin(base + a) * r;
    }
    // breathing: a held camera is never quite still, and a tired one drifts
    const b = 0.012 + cam.sway * 0.09;
    px += Math.sin(time * 0.7) * b; py += Math.sin(time * 0.9 + 1) * b * 0.7; pz += Math.cos(time * 0.6) * b;
    if (cam.shake > 0) {
      px += rnd(-1, 1) * cam.shake * 0.08; py += rnd(-1, 1) * cam.shake * 0.08;
      cam.shake = Math.max(0, cam.shake - dt * 2.5);
    }
    camera.position.set(px, py, pz);
    camera.lookAt(cam.at.x, cam.at.y + Math.sin(time * 0.5) * cam.sway * 0.05, cam.at.z);
  }

  // ---------------------------------------------------------------- the day outside, and the power
  const SKY = [
    // morning: grey and wet; noon: the smog; evening: the city's sunset, which is mostly pollution; night
    { at: 0, top: 0x2a3550, bot: 0x6a6f86, light: 0.75, city: 0.55 },
    { at: 0.35, top: 0x3a4660, bot: 0x8a8578, light: 0.8, city: 0.45 },
    { at: 0.7, top: 0x2a1840, bot: 0xd2553a, light: 0.62, city: 0.8 },
    { at: 0.9, top: 0x080c1c, bot: 0x3a1840, light: 0.5, city: 1.0 },
    { at: 1.2, top: 0x04060e, bot: 0x1a0c26, light: 0.45, city: 1.0 },
  ];
  let daylight = 0, power = 1, powerTarget = 1;
  S.setDaylight = function (f) { daylight = f; };
  S.powerCut = function (on) { powerTarget = on ? 0 : 1; };
  const cA = new T.Color(), cB = new T.Color();
  function stepEnvironment(dt) {
    let i = 0;
    while (i < SKY.length - 2 && daylight > SKY[i + 1].at) i++;
    const a = SKY[i], b = SKY[i + 1];
    const k = clamp((daylight - a.at) / (b.at - a.at), 0, 1);
    G.sky.material.uniforms.uTop.value.copy(cA.set(a.top).lerp(cB.set(b.top), k));
    G.sky.material.uniforms.uBot.value.copy(cA.set(a.bot).lerp(cB.set(b.bot), k));
    const cityGlow = lerp(a.city, b.city, k);
    G.cityLayers.forEach((m, li) => m.material.color.setScalar(cityGlow * (0.7 + li * 0.15)));
    G.hemi.intensity = lerp(a.light, b.light, k) * (0.25 + 0.75 * power);
    power += (powerTarget - power) * Math.min(1, dt * (powerTarget < power ? 6 : 1.5));
    // when the grid cuts out, the emergency light turns and everything else goes dim
    const pw = power;
    G.key.intensity = 2.2 * pw;
    G.cyanLight.intensity = 1.6 * pw; G.pinkLight.intensity = 1.0 * pw; G.fill.intensity = 0.8 * pw + 0.1;
    G.neonCyan.color.setRGB(0.16 * 1.6 * pw, 0.94 * 1.6 * pw, 1.6 * pw);
    G.neonPink.color.setRGB(1.5 * pw, 0.17 * 1.5 * pw, 0.84 * 1.5 * pw);
    G.ceilLights.forEach((m) => m.material.color.setRGB(0.87 * 1.2 * pw, 0.97 * 1.2 * pw, 1.2 * pw));
    G.beams.forEach((m) => { m.material.uniforms.uAlpha.value = 0.16 * pw; });
    G.red.intensity = (1 - pw) * (0.8 + Math.sin(time * 5) * 0.8);
    G.sign.material.color.setScalar(pw);
  }

  // ---------------------------------------------------------------- set-up and the frame
  S.init = function (cv, opts) {
    canvas = cv;
    opts = opts || {};
    quality = opts.quality == null ? 2 : opts.quality;
    renderer = new T.WebGLRenderer({ canvas, antialias: quality >= 2, powerPreference: 'high-performance', stencil: false });
    renderer.outputEncoding = T.sRGBEncoding;
    renderer.toneMapping = T.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.15;
    renderer.localClippingEnabled = true;
    renderer.autoClear = false;
    scene = new T.Scene();
    scene.fog = new T.FogExp2(0x0a0e1a, 0.028);
    camera = new T.PerspectiveCamera(36, 1, 0.1, 140);
    setupGlow();
    buildGarage();
    buildEnvironment();
    buildFx();
    S.resize();
    S.focus('title', true);
    glow.dirty = true;
    canvas.addEventListener('webglcontextlost', (e) => { e.preventDefault(); S.lost = true; });
    canvas.addEventListener('webglcontextrestored', () => { S.lost = false; });
  };
  S.resize = function () {
    W = Math.max(1, window.innerWidth); H = Math.max(1, window.innerHeight);
    const cap = quality >= 2 ? 2 : quality === 1 ? 1.5 : 1;
    dpr = Math.min(window.devicePixelRatio || 1, cap);
    renderer.setPixelRatio(dpr);
    renderer.setSize(W, H, false);
    camera.aspect = W / H;
    // a tall screen sees less of the bay side to side: widen the lens so the car still fits
    camera.fov = W / H < 1.5 ? 52 : W / H < 1.9 ? 42 : 36;
    camera.updateProjectionMatrix();
    sizeGlow();
  };
  S.setQuality = function (q) {
    if (q === quality) return;
    quality = q;
    S.resize();
  };
  S.quality = () => quality;
  S.setGlow = function (s) { glow.strength = s; };

  let fpsAcc = 0, fpsN = 0;
  S.frame = function (dt, noDraw) {
    if (S.lost || !renderer) return;
    time += dt;
    stepTweens(dt);
    stepEnvironment(dt);
    stepCamera(dt);
    stepFx(dt);
    // the lift and the car on it
    const lifted = stage.lifted;
    G.liftRings.forEach((r, i) => { r.material.color.setRGB(0.16, 0.94, 1).multiplyScalar(1.0 + lifted * 0.8 + Math.sin(time * 3 - i) * 0.3 * (0.3 + lifted)); });
    G.emitters.forEach((e, i) => { const k = 0.6 + lifted * 1.2 + Math.sin(time * 4 + i * 0.8) * 0.3; e.material.color.setRGB(0.16 * k, 0.94 * k, k); });
    G.column.material.uniforms.uTime.value = time;
    G.column.material.uniforms.uAlpha.value = lifted;
    G.column.scale.y = 0.001 + lifted * 0.85;
    G.liftLight.intensity = lifted * 1.4;
    G.liftGlow.material.opacity = 0.14 + lifted * 0.18;
    if (car) {
      const d = car.userData;
      stage.hover = lifted * (0.85 + Math.sin(time * 1.6) * 0.035);
      car.position.y = stage.hover;
      if (!stage.driving) car.rotation.x = Math.sin(time * 1.1) * 0.006 * lifted;
      d.wheels.forEach((w) => { w.spin.rotation.z = -stage.wheelSpin; });
      d.holo.uniforms.uTime.value = time;
      d.under.material.opacity = d.dead ? 0 : 0.5 * (1 - lifted * 0.6);
      const flick = d.lightsBroken ? (Math.random() < 0.15 ? 1.6 : 0.1) : 1.6;
      d.front.material.color.setScalar(d.dead ? 0.05 : flick);
      d.throwLight.material.opacity = d.dead ? 0 : (d.lightsBroken ? flick * 0.12 : 0.22);
      const beamA = d.dead || stage.xray ? 0 : d.lightsBroken ? flick * 0.06 : 0.12;
      d.beams.forEach((b) => { b.material.uniforms.uAlpha.value = beamA; });
      if (d.eyes.visible) d.eyes.scale.y = (time % 3.1) < 0.12 ? 0.1 : 1;
      if (stage.xray) {
        d.insides.children.forEach((m) => {
          if (m.userData.state === 'bad') { const k = 1 + Math.sin(time * 6) * 0.5; m.material.color.setRGB(1.3 * k, 0.16 * k, 0.3 * k); }
        });
      }
      // a flat tyre leaves the car leaning on that corner, until it's lifted off it
      if (!stage.driving) {
        const lean = d.flat && lifted < 0.5 ? 1 - lifted * 2 : 0;
        car.rotation.x += lean * 0.022;
        car.rotation.z = lean * (d.flat === 'F' ? -0.012 : 0.012);
      }
      // what the car's lights throw stays on the floor, whatever height the car is at
      d.throwLight.position.y = 0.02 - car.position.y;
      d.under.position.y = 0.02 - car.position.y;
    }
    G.rain.material.uniforms.uTime.value = time;
    G.dust.material.uniforms.uTime.value = time;
    G.flyers.forEach((f) => {
      f.position.x += f.userData.speed * dt;
      if (f.position.x > 34) f.position.x = -28; else if (f.position.x < -28) f.position.x = 34;
    });
    // the sign's last letter never quite works
    const t9 = time % 7;
    G.sign.material.opacity = (t9 > 5.1 && t9 < 5.25) || (t9 > 5.4 && t9 < 5.45) ? 0.35 : 1;
    G.strike.material.opacity = 0.75 + Math.sin(time * 2.2) * 0.25;
    G.botEye.material.color.setRGB(2 * (0.6 + Math.sin(time * 1.3) * 0.4), 0.2, 0.3);
    if (noDraw) return;
    renderFrame();
    // a phone that can't keep up gets the cheaper picture rather than a stutter
    // (the first seconds are left out: shaders compiling make any phone look slow)
    fpsAcc += dt; fpsN++;
    if (fpsAcc > 4) {
      const avg = fpsAcc / fpsN;
      fpsAcc = 0; fpsN = 0;
      if (time > 8 && S.autoQuality && quality > 0 && avg > (quality === 2 ? 1 / 40 : 1 / 30)) { S.setQuality(quality - 1); if (S.onQuality) S.onQuality(quality); }
    }
  };
  S.autoQuality = true;
  S.time = () => time;
  S.tween = tween;
})();

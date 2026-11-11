// Compiles the app's real GLSL in headless Chromium (WebGL 1 = GLSL ES 1.00, same language the
// Android GPU compiles) and checks the rendered pixels. Run: node tools/shader-test/run.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const dir = path.join(__dirname, '../../app/src/main/assets/shaders');
const src = (n) => fs.readFileSync(path.join(dir, n), 'utf8');

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  const result = await page.evaluate(({ vertex, grade, blur }) => {
    const W = 64, H = 64;
    const canvas = document.createElement('canvas');
    canvas.width = W; canvas.height = H;
    const gl = canvas.getContext('webgl', { preserveDrawingBuffer: true, premultipliedAlpha: false });
    if (!gl) return { fatal: 'no webgl' };
    const log = [];
    function program(fs) {
      const p = gl.createProgram();
      for (const [type, text] of [[gl.VERTEX_SHADER, vertex], [gl.FRAGMENT_SHADER, fs]]) {
        const s = gl.createShader(type);
        gl.shaderSource(s, text); gl.compileShader(s);
        if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
        gl.attachShader(p, s);
      }
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
      const active = [];
      for (let i = 0; i < gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS); i++) active.push(gl.getActiveUniform(p, i).name);
      return { p, active };
    }
    // Test picture: left half mid-grey (128), right half a saturated orange, one white column.
    const px = new Uint8Array(W * H * 4);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = (y * W + x) * 4;
      const c = x === 16 ? [255, 255, 255] : x < 32 ? [128, 128, 128] : [220, 120, 40];
      px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
    }
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, W, H, 0, gl.RGBA, gl.UNSIGNED_BYTE, px);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 0, 1, 1, -1, 0, 1, -1, 1, 0, 1, 1, 1, 0, 1]), gl.STATIC_DRAW);
    function draw(prog, uniforms) {
      gl.useProgram(prog.p);
      const loc = gl.getAttribLocation(prog.p, 'aFramePosition');
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, 4, gl.FLOAT, false, 0, 0);
      gl.uniform1i(gl.getUniformLocation(prog.p, 'uTexSampler'), 0);
      for (const [k, v] of Object.entries(uniforms)) {
        const u = gl.getUniformLocation(prog.p, k);
        if (u === null) throw new Error('uniform not active: ' + k);
        Array.isArray(v) ? gl.uniform2f(u, v[0], v[1]) : gl.uniform1f(u, v);
      }
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      const out = new Uint8Array(W * H * 4);
      gl.readPixels(0, 0, W, H, gl.RGBA, gl.UNSIGNED_BYTE, out);
      return (x, y) => Array.from(out.slice((y * W + x) * 4, (y * W + x) * 4 + 4));
    }
    const neutral = {
      uTexel: [1 / W, 1 / H], uExposure: 0, uBrightness: 0, uContrast: 0, uSaturation: 0, uTemperature: 0, uTint: 0,
      uHighlights: 0, uShadows: 0, uWhites: 0, uBlacks: 0, uSharpen: 0, uVignette: 0, uGrain: 0, uSeed: 1,
      uFade: 1, uWipe: 1, uWipeExit: 0, uOpacity: 1,
    };
    const checks = [];
    const ok = (name, cond, detail) => checks.push({ name, pass: !!cond, detail: JSON.stringify(detail) });
    let g, b;
    try { g = program(grade); b = program(blur); } catch (e) { return { fatal: String(e) }; }
    const near = (a, e, tol = 2) => a.every((v, i) => Math.abs(v - e[i]) <= tol);
    const lum = (c) => 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    const G = [8, 32], O = [48, 32];  // sample points: grey area, orange area

    ok('all grade uniforms are active', Object.keys(neutral).every((k) => g.active.includes(k)), g.active);
    let at = draw(g, neutral);
    ok('neutral grade is identity (grey)', near(at(...G), [128, 128, 128, 255]), at(...G));
    ok('neutral grade is identity (orange)', near(at(...O), [220, 120, 40, 255]), at(...O));
    at = draw(g, { ...neutral, uExposure: 0.5 });
    ok('exposure + brightens', lum(at(...G)) > 150, at(...G));
    at = draw(g, { ...neutral, uExposure: -0.5 });
    ok('exposure - darkens', lum(at(...G)) < 100, at(...G));
    at = draw(g, { ...neutral, uBrightness: 1 });
    ok('brightness adds', near(at(...G), [192, 192, 192, 255], 3), at(...G));
    at = draw(g, { ...neutral, uSaturation: -1 });
    ok('saturation -1 is greyscale', Math.abs(at(...O)[0] - at(...O)[2]) <= 2, at(...O));
    const greyLum = lum(at(...O));
    ok('greyscale keeps luminance', Math.abs(greyLum - lum([220, 120, 40])) < 3, greyLum);
    at = draw(g, { ...neutral, uSaturation: 1 });
    ok('saturation + widens channel spread', at(...O)[0] - at(...O)[2] > 180, at(...O));
    at = draw(g, { ...neutral, uContrast: 1 });
    ok('contrast keeps mid grey, pushes orange apart', near(at(...G), [128, 128, 128, 255], 3) && at(...O)[0] === 255 && at(...O)[2] < 10, [at(...G), at(...O)]);
    at = draw(g, { ...neutral, uTemperature: 1 });
    ok('temperature + is warmer', at(...G)[0] > at(...G)[2] + 30, at(...G));
    at = draw(g, { ...neutral, uTemperature: -1 });
    ok('temperature - is cooler', at(...G)[2] > at(...G)[0] + 30, at(...G));
    at = draw(g, { ...neutral, uTint: 1 });
    ok('tint + is magenta', at(...G)[1] < at(...G)[0] - 10, at(...G));
    at = draw(g, { ...neutral, uShadows: 1 });
    ok('shadows barely touch mid grey', Math.abs(lum(at(...G)) - 128) < 4, at(...G));
    at = draw(g, { ...neutral, uHighlights: -1 });
    ok('highlights - darkens the white line', at(16, 32)[0] < 200, at(16, 32));
    at = draw(g, { ...neutral, uWhites: 1 });
    ok('whites + lifts grey', lum(at(...G)) > 135, at(...G));
    at = draw(g, { ...neutral, uBlacks: 1 });
    ok('blacks leave mid grey alone', Math.abs(lum(at(...G)) - 128) < 3, at(...G));
    at = draw(g, { ...neutral, uVignette: 1 });
    ok('vignette darkens corners, not centre', lum(at(1, 1)) < 60 && near(at(28, 32), [128, 128, 128, 255], 3), [at(1, 1), at(28, 32)]);
    at = draw(g, { ...neutral, uGrain: 1 });
    const a1 = at(4, 4), a2 = at(5, 9), a3 = at(9, 20);
    ok('grain varies per pixel within bounds', !(a1[0] === a2[0] && a2[0] === a3[0]) && [a1, a2, a3].every((c) => Math.abs(c[0] - 128) <= 24), [a1, a2, a3]);
    at = draw(g, { ...neutral, uSharpen: 1 });
    ok('sharpen leaves flat areas, darkens next to the white line', near(at(...G), [128, 128, 128, 255], 2) && at(15, 32)[0] < 100, [at(...G), at(15, 32)]);
    at = draw(g, { ...neutral, uFade: 0.5 });
    ok('fade 0.5 halves', near(at(...G), [64, 64, 64, 255], 2), at(...G));
    at = draw(g, { ...neutral, uFade: 0 });
    ok('fade 0 is black, alpha kept', near(at(...O), [0, 0, 0, 255], 0), at(...O));
    at = draw(g, { ...neutral, uOpacity: 0.25 });
    ok('opacity dims', near(at(...G), [32, 32, 32, 255], 2), at(...G));
    at = draw(g, { ...neutral, uWipe: 0.5 });
    ok('wipe enter 0.5 shows left, hides right', near(at(...G), [128, 128, 128, 255]) && near(at(...O), [0, 0, 0, 255], 0), [at(...G), at(...O)]);
    at = draw(g, { ...neutral, uWipe: 0.5, uWipeExit: 1 });
    ok('wipe exit 0.5 hides left, shows right', near(at(...G), [0, 0, 0, 255], 0) && near(at(...O), [220, 120, 40, 255]), [at(...G), at(...O)]);

    ok('all blur uniforms are active', ['uTexel', 'uDirection', 'uRadius'].every((k) => b.active.includes(k)), b.active);
    const bu = { uTexel: [1 / W, 1 / H], uDirection: [1, 0], uRadius: 0 };
    at = draw(b, bu);
    ok('blur radius 0 is identity', near(at(16, 32), [255, 255, 255, 255], 1) && near(at(...O), [220, 120, 40, 255], 1), [at(16, 32), at(...O)]);
    at = draw(b, { ...bu, uRadius: 6 });
    ok('horizontal blur spreads the white line', at(16, 32)[0] < 200 && at(14, 32)[0] > 135, [at(16, 32), at(14, 32)]);
    ok('blur keeps flat areas and alpha', near(at(4, 32), [128, 128, 128, 255], 2), at(4, 32));
    at = draw(b, { ...bu, uRadius: 6, uDirection: [0, 1] });
    ok('vertical blur does not spread a vertical line sideways', near(at(16, 32), [255, 255, 255, 255], 2) && near(at(14, 32), [128, 128, 128, 255], 2), [at(16, 32), at(14, 32)]);
    return { checks, renderer: gl.getParameter(gl.VERSION) };
  }, { vertex: src('studio_vertex.glsl'), grade: src('studio_grade_fragment.glsl'), blur: src('studio_blur_fragment.glsl') });
  await browser.close();
  if (result.fatal) { console.error('FATAL', result.fatal); process.exit(1); }
  let failed = 0;
  for (const c of result.checks) { if (!c.pass) { failed++; console.log('FAIL', c.name, c.detail); } }
  console.log(`${result.checks.length - failed}/${result.checks.length} shader checks passed (${result.renderer})`);
  process.exit(failed ? 1 : 0);
})();

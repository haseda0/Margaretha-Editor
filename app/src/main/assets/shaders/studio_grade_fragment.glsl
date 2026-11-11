// Margaretha grade pass: sharpen, colour grade, vignette, grain, fade/wipe transition, opacity.
// Every control is neutral at 0 (uVisibility and uOpacity are neutral at 1).
// Old GPUs may lack high precision in fragment shaders; the maths still holds at medium.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexSampler;
uniform vec2 uTexel;        // 1 / frame size
uniform float uExposure;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uTemperature;
uniform float uTint;
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;
uniform float uSharpen;
uniform float uVignette;
uniform float uGrain;
uniform float uSeed;        // changes every frame so grain moves
uniform float uFade;        // 1 = fully visible, 0 = black
uniform float uWipe;        // 1 = fully revealed; < 1 hides the right part (enter) or the left part (exit)
uniform float uWipeExit;    // 0 = entering, 1 = exiting
uniform float uOpacity;
varying vec2 vUv;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float hash(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

void main() {
  vec4 src = texture2D(uTexSampler, vUv);
  vec3 c = src.rgb;

  // Unsharp mask against the four direct neighbours.
  vec3 around = texture2D(uTexSampler, vUv + vec2(uTexel.x, 0.0)).rgb
      + texture2D(uTexSampler, vUv - vec2(uTexel.x, 0.0)).rgb
      + texture2D(uTexSampler, vUv + vec2(0.0, uTexel.y)).rgb
      + texture2D(uTexSampler, vUv - vec2(0.0, uTexel.y)).rgb;
  c += (c - around * 0.25) * uSharpen * 1.5;

  // Exposure in stops, then white balance.
  c *= exp2(uExposure * 1.5);
  c.r *= 1.0 + 0.18 * uTemperature + 0.06 * uTint;
  c.g *= 1.0 - 0.12 * uTint;
  c.b *= 1.0 - 0.18 * uTemperature + 0.06 * uTint;
  c += uBrightness * 0.25;

  // Tonal ranges, selected by luminance.
  float l = clamp(dot(c, LUMA), 0.0, 1.0);
  c += uShadows * 0.25 * (1.0 - smoothstep(0.0, 0.5, l));
  c += uHighlights * 0.25 * smoothstep(0.5, 1.0, l);
  c += uBlacks * 0.15 * (1.0 - smoothstep(0.0, 0.25, l));
  c *= 1.0 + uWhites * 0.2 * l;

  c = (c - 0.5) * exp2(uContrast) + 0.5;
  c = mix(vec3(dot(c, LUMA)), c, 1.0 + uSaturation);
  c = clamp(c, 0.0, 1.0);

  float d = distance(vUv, vec2(0.5)) * 1.41421356;
  c *= 1.0 - uVignette * 0.85 * smoothstep(0.35, 1.0, d);
  c += (hash(vUv / uTexel + uSeed) - 0.5) * uGrain * 0.18;

  // Transitions animate against black.
  float edge = mix(vUv.x, 1.0 - vUv.x, uWipeExit);
  c *= uFade * uOpacity * step(edge, uWipe);

  gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}

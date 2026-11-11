// One direction of a separable Gaussian blur (13 taps spread over the radius).
// Run it twice: uDirection = (1,0) then (0,1). uRadius is in pixels; 0 leaves the frame untouched.
// Old GPUs may lack high precision in fragment shaders; the maths still holds at medium.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform vec2 uDirection;
uniform float uRadius;
varying vec2 vUv;

void main() {
  vec2 stepUv = uDirection * uTexel * (uRadius / 6.0);
  vec4 sum = texture2D(uTexSampler, vUv) * 0.1554;
  sum += (texture2D(uTexSampler, vUv + stepUv) + texture2D(uTexSampler, vUv - stepUv)) * 0.1442;
  sum += (texture2D(uTexSampler, vUv + stepUv * 2.0) + texture2D(uTexSampler, vUv - stepUv * 2.0)) * 0.1155;
  sum += (texture2D(uTexSampler, vUv + stepUv * 3.0) + texture2D(uTexSampler, vUv - stepUv * 3.0)) * 0.0798;
  sum += (texture2D(uTexSampler, vUv + stepUv * 4.0) + texture2D(uTexSampler, vUv - stepUv * 4.0)) * 0.0476;
  sum += (texture2D(uTexSampler, vUv + stepUv * 5.0) + texture2D(uTexSampler, vUv - stepUv * 5.0)) * 0.0244;
  sum += (texture2D(uTexSampler, vUv + stepUv * 6.0) + texture2D(uTexSampler, vUv - stepUv * 6.0)) * 0.0108;
  gl_FragColor = sum;
}

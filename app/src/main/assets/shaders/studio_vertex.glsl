// Full-frame quad. Texture coordinates follow Media3's convention: (0,0) is the bottom-left.
attribute vec4 aFramePosition;
varying vec2 vUv;

void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}

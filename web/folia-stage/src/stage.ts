// SPDX-License-Identifier: AGPL-3.0-or-later
// Host adapter only. Rendering and layout remain in the unmodified, pinned Folia sources.
import * as pixi from 'pixi.js';
import { createLyricWindow, type LyricWindow, type WindowTypography } from '../vendor/folia/src/components/visualizer/lumiere/text/lyricWindow';
import { createLightSprites } from '../vendor/folia/src/components/visualizer/lumiere/light/sprites';
import { rgbOf } from '../vendor/folia/src/components/visualizer/lumiere/color';
import { splitLyricGraphemes } from '../vendor/folia/src/utils/lyrics/graphemeTiming';
import type { Line } from '../vendor/folia/src/types';

interface Scene { lines: Line[]; seed: string; accent: string; fontScale: number; motion: boolean; extras: boolean }
const status = document.getElementById('status')!;
const subtitle = document.getElementById('subtitle')!;
const app = new pixi.Application();
let lyric: LyricWindow | undefined;
let sprites: ReturnType<typeof createLightSprites>;
let scene: Scene | undefined;
let baseTime = 0, clockAt = 0, playing = false, frameId = 0, disposed = false;
let currentTime = 0, width = 1, height = 1;

function native(action: string, positionMs?: number) {
  const bridge = (window as any).kmpJsBridge;
  bridge?.callNative('stageAction', JSON.stringify({ action, positionMs }), () => {});
}
function currentLine(time: number) {
  let low = 0, high = (scene?.lines.length ?? 0) - 1, active = -1;
  while (low <= high) {
    const middle = (low + high) >>> 1;
    if (scene!.lines[middle].startTime <= time) { active = middle; low = middle + 1; } else high = middle - 1;
  }
  return active;
}
function typographyOf(index: number): WindowTypography {
  const text = scene?.lines[index]?.fullText ?? '';
  // Folia's own three layouts: current phrase stays ordered; neighboring lines occupy bounded slots.
  // Vertical composition is reserved for CJK phrases in a tall viewport.
  if (height > width * 1.1 && /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u.test(text) && index % 3 === 1) return 'vertical';
  return index % 3 === 2 ? 'crossed' : 'horizontal';
}
function rebuild() {
  if (!scene || disposed) return;
  lyric?.destroy(); lyric = undefined;
  subtitle.textContent = '';
  if (!scene.lines.length) { status.textContent = '聆听此刻'; status.hidden = false; app.render(); return; }
  status.hidden = true;
  lyric = createLyricWindow(pixi, {
    width, height, lines: scene.lines, font: '"Noto Serif CJK SC", "Songti SC", "Noto Serif", serif', weight: 600,
    resolution: Math.min(window.devicePixelRatio || 1, 2),
    region: { cx: width / height * 0.5, cy: 0.44, w: width / height * 0.76, h: 0.58 },
    heroPx: Math.min(width * 0.12, height * 0.095, 76) * scene.fontScale,
    neighbors: 2, typography: 'horizontal', typographyOf, seed: scene.seed,
    decay: { strength: scene.motion ? 0.12 : 0, delay: 2.4 },
    drift: scene.motion ? 0.3 : 0, alwaysFly: scene.motion, letterSpacing: 0.025, sprites,
  });
  app.stage.addChild(lyric.view);
  renderAt(currentTime);
}
function renderAt(time: number) {
  if (disposed || !scene) return;
  currentTime = Math.max(0, time);
  const color = rgbOf(scene.accent);
  lyric?.update({ time: currentTime, beams: [], litColor: color, unlitColor: color,
    unlitAlpha: 0.26, intensity: 1, hideTrails: !scene.motion });
  const line = scene.lines[currentLine(currentTime)];
  subtitle.textContent = scene.extras && line ? [line.romanization, line.translation].filter(Boolean).join('\n') : '';
  app.render();
}
function tick() {
  frameId = 0;
  if (disposed || document.hidden || !playing) return;
  renderAt(baseTime + (performance.now() - clockAt) / 1000);
  frameId = requestAnimationFrame(tick);
}
function updateClock(positionMs: number, isPlaying: boolean) {
  if (disposed || !Number.isFinite(positionMs)) return;
  baseTime = Math.max(0, positionMs) / 1000; clockAt = performance.now(); playing = isPlaying;
  renderAt(baseTime);
  if (frameId) cancelAnimationFrame(frameId);
  frameId = playing && !document.hidden ? requestAnimationFrame(tick) : 0;
}
function anchors(lineIndex = currentLine(currentTime)) {
  if (!lyric || !scene?.lines[lineIndex]) return [];
  return splitLyricGraphemes(scene.lines[lineIndex].fullText).map((char, glyphIndex) => ({
    char, glyphIndex, ...lyric!.glyphAnchor(lineIndex, glyphIndex, currentTime),
  }));
}
function resize() {
  const w = Math.max(1, Math.round(innerWidth)), h = Math.max(1, Math.round(innerHeight));
  if (w === width && h === height) return;
  width = w; height = h; app.renderer.resize(w, h); rebuild();
}
function visibility() {
  if (frameId) cancelAnimationFrame(frameId);
  frameId = 0;
  if (!document.hidden && playing && !disposed) tick();
}
function destroy() {
  if (disposed) return;
  disposed = true;
  if (frameId) cancelAnimationFrame(frameId);
  removeEventListener('resize', resize); document.removeEventListener('visibilitychange', visibility);
  lyric?.destroy(); sprites?.destroy(); app.destroy(true, { children: true });
}
async function boot() {
  try {
    // Use the WebGL renderer shared by Android WebView, WKWebView and WebView2, not a bundled browser.
    await app.init({ width: innerWidth, height: innerHeight, backgroundAlpha: 0, autoStart: false,
      resolution: Math.min(devicePixelRatio || 1, 2), autoDensity: true, preference: 'webgl', antialias: true });
    sprites = createLightSprites(pixi);
    document.body.prepend(app.canvas);
    app.canvas.setAttribute('aria-label', 'Folia 逐字歌词舞台');
    app.canvas.addEventListener('click', event => {
      const rect = app.canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left) * width / rect.width;
      const y = (event.clientY - rect.top) * height / rect.height;
      const current = currentLine(currentTime);
      const hit = anchors(current).find(a => Math.abs(a.x - x) < a.fontPx * 0.6 && Math.abs(a.y - y) < a.fontPx * 0.7);
      if (hit && scene?.lines[current]) {
        const cue = lyric!.glyphTimes(current).find(glyph => glyph.glyphIndex === hit.glyphIndex)?.start ?? scene.lines[current].startTime;
        native('seek', Math.round(cue * 1000));
      }
      else native('blank');
    });
    (window as any).MingFoliaStage = {
      setScene(value: Scene) { if (disposed) return; scene = value; rebuild(); }, updateClock,
      renderAt, anchors, destroy, upstreamCommit: 'ced47852c64c2709faebe0190d430c3ab75ca2a4',
    };
    addEventListener('resize', resize); document.addEventListener('visibilitychange', visibility);
    addEventListener('pagehide', destroy, { once: true });
    resize(); status.textContent = '聆听此刻'; document.title = 'ming-folia-ready';
  } catch (error) {
    status.hidden = false; status.textContent = '舞台渲染初始化失败，请切换其他舞台';
    document.title = 'ming-folia-error'; console.error(error);
  }
}
void boot();

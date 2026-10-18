import { chromium } from 'playwright';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const html = path.resolve('../../shared/src/commonMain/composeResources/files/folia-stage.html');
const output = path.resolve('../../build/folia-stage-previews');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined,
  args: ['--enable-webgl', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const errors = [], requests = [];
const scene = { seed: 'fixture', accent: '#dce8c4', fontScale: 1, motion: true, extras: true, lines: [
  { fullText: '晚风吹过城市的灯火', startTime: 0, endTime: 8, words: [] },
  { fullText: '我们追着星光奔跑', startTime: 8, endTime: 16, words: [], translation: 'We follow the stars into the night' },
  { fullText: '把未完成的梦唱给你听', startTime: 16, endTime: 22, words: [
    { text: '把', startTime: 16, endTime: 17 }, { text: '未完成的', startTime: 17, endTime: 18.2 },
    { text: '梦', startTime: 18.2, endTime: 19.4 }, { text: '唱给你听', startTime: 19.4, endTime: 22 },
  ], translation: 'Let every unfinished dream become a song' },
  { fullText: '让旋律越过山海与你相遇', startTime: 26, endTime: 36, words: [] },
] };
try {
  const page = await browser.newPage({ viewport: { width: 430, height: 620 }, deviceScaleFactor: 1 });
  page.on('pageerror', error => errors.push(String(error)));
  page.on('request', request => { if (/^https?:/.test(request.url())) requests.push(request.url()); });
  await page.goto(pathToFileURL(html).href);
  await page.waitForFunction(() => document.title === 'ming-folia-ready', { timeout: 30_000 });
  await page.context().setOffline(true);
  // The production WebView is transparent over the native cover backdrop.
  await page.addStyleTag({ content: 'body{background:radial-gradient(ellipse at 30% 35%,#29392d 0%,#111b18 45%,#080d12 100%)}' });
  await page.evaluate(scene => {
    window.stageCalls = [];
    window.kmpJsBridge = { callNative(method, params) { window.stageCalls.push({ method, ...JSON.parse(params) }); } };
    window.MingFoliaStage.setScene(scene); window.MingFoliaStage.updateClock(18_800, false);
  }, scene);
  const anchors = await page.evaluate(() => window.MingFoliaStage.anchors());
  assert.equal(anchors.map(a => a.char).join(''), scene.lines[2].fullText);
  // Folia's current phrase is ordered; a line break can reset x only when y advances.
  for (let i = 1; i < anchors.length; i++) {
    assert.ok(anchors[i].x > anchors[i - 1].x || anchors[i].y - anchors[i - 1].y > 20, 'Settled phrase has a broken reading order');
  }
  for (const a of anchors) assert.ok(a.x >= 0 && a.x <= 430 && a.y >= 0 && a.y <= 620, 'Current phrase escaped viewport');
  await page.screenshot({ path: path.join(output, 'Folia-phone.png') });
  await page.waitForTimeout(220);
  assert.deepEqual(await page.evaluate(() => window.MingFoliaStage.anchors()), anchors, 'Paused scene should stay frozen');
  await page.mouse.click(anchors[5].x, anchors[5].y);
  assert.equal((await page.evaluate(() => window.stageCalls.at(-1))).positionMs, 18_200, 'Glyph click must preserve source cue timing');
  await page.mouse.click(4, 4);
  assert.equal((await page.evaluate(() => window.stageCalls.at(-1))).action, 'blank');
  await page.evaluate(() => { window.MingFoliaStage.updateClock(28_000, false); window.MingFoliaStage.updateClock(18_800, false); });
  assert.deepEqual(await page.evaluate(() => window.MingFoliaStage.anchors()), anchors, 'Seeking back must reproduce upstream geometry');
  await page.evaluate(() => window.MingFoliaStage.updateClock(11_000, false));
  const vertical = await page.evaluate(() => window.MingFoliaStage.anchors());
  assert.ok(Math.max(...vertical.map(a => a.y)) - Math.min(...vertical.map(a => a.y)) > 100, 'Tall CJK scene should use the upstream vertical layout');
  await page.screenshot({ path: path.join(output, 'Folia-vertical.png') });
  if (process.env.FOLIA_RENDER_MOTION === '1') {
    const frames = path.resolve('../../build/folia-stage-motion');
    await mkdir(frames, { recursive: true });
    for (let frame = 0; frame < 180; frame++) {
      await page.evaluate(ms => window.MingFoliaStage.updateClock(ms, false), 15_000 + frame * 1000 / 24);
      await page.screenshot({ path: path.join(frames, `frame-${String(frame).padStart(3, '0')}.png`) });
    }
  }
  await page.setViewportSize({ width: 960, height: 480 });
  await page.evaluate(() => window.MingFoliaStage.updateClock(18_800, false));
  await page.screenshot({ path: path.join(output, 'Folia-landscape.png') });
  const malicious = structuredClone(scene);
  malicious.lines[2].fullText = '<script>window.injected=true</script>🌙e\u0301👨‍👩‍👧‍👦'; malicious.lines[2].words = [];
  await page.evaluate(scene => { window.MingFoliaStage.setScene(scene); window.MingFoliaStage.updateClock(18_800, false); }, malicious);
  assert.equal(await page.evaluate(() => window.injected), undefined);
  const chars = await page.evaluate(() => window.MingFoliaStage.anchors().map(a => a.char));
  assert.ok(chars.includes('👨‍👩‍👧‍👦') && chars.includes('e\u0301'), 'Upstream grapheme handling must preserve combined characters');
  await page.evaluate(scene => { window.MingFoliaStage.setScene(scene); window.MingFoliaStage.updateClock(16_000, true); }, scene);
  const before = await page.evaluate(() => window.MingFoliaStage.anchors());
  await page.waitForTimeout(180);
  assert.notDeepEqual(await page.evaluate(() => window.MingFoliaStage.anchors()), before, 'Playing driver must animate');
  await page.evaluate(() => window.MingFoliaStage.destroy());
  assert.equal(await page.locator('canvas').count(), 0, 'Dispose must release the rendering surface');
  assert.deepEqual(errors, []); assert.deepEqual(requests, [], 'Stage must run completely offline');
  await writeFile(path.join(output, 'verification.json'), JSON.stringify({ upstreamCommit: 'ced47852c64c2709faebe0190d430c3ab75ca2a4',
    offline: true, geometry: 'original Folia', checks: ['reading order', 'viewport bounds', 'vertical layout', 'seek reproducibility', 'word tap', 'blank tap', 'pause', 'play', 'Unicode', 'lyric injection', 'dispose'] }, null, 2));
  console.log('11 Folia browser integration checks passed; 3 actual WebGL screenshots captured');
} finally { await browser.close(); }

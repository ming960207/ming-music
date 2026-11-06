import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { Script } from 'node:vm';

test('Folia rendering sources are byte-for-byte pinned to the recorded upstream commit', async () => {
  const upstream = JSON.parse(await readFile('vendor/folia/UPSTREAM.json', 'utf8'));
  assert.equal(upstream.commit, 'ced47852c64c2709faebe0190d430c3ab75ca2a4');
  assert.ok(Object.keys(upstream.files).length >= 20);
  for (const [file, digest] of Object.entries(upstream.files)) {
    assert.equal(createHash('sha256').update(await readFile(`vendor/folia/${file}`)).digest('hex'), digest, file);
  }
});

test('the shipped stage is offline and includes its copyright and source offer', async () => {
  const html = await readFile('../../shared/src/commonMain/composeResources/files/folia-stage.html', 'utf8');
  assert.ok(html.includes('GNU AFFERO GENERAL PUBLIC LICENSE'));
  assert.ok(html.includes('chthollyphile'));
  assert.ok(html.includes('<template id="stage-license-notices">'));
  assert.ok(!html.includes('Folia · 源码与许可证'));
  assert.ok(!html.includes('<details>'));
  assert.ok(html.includes('connect-src &#39;none&#39;') || html.includes("connect-src 'none'"));
  assert.doesNotMatch(html, /<(script|link)[^>]+(?:src|href)=["']https?:/i);
  assert.ok(html.includes('window.MingFoliaStage') || html.includes('.MingFoliaStage='));
  assert.ok(html.includes('ced47852c64c2709faebe0190d430c3ab75ca2a4'));
  const script = html.match(/<script>([\s\S]*)<\/script>/)[1];
  assert.doesNotThrow(() => new Script(script), 'Bundling must preserve literal JavaScript replacement patterns');
});

test('TV layout presets preserve the original Folia renderer and remove canvas focus outline', async () => {
  const stage = await readFile('src/stage.ts', 'utf8');
  const html = await readFile('src/index.html', 'utf8');
  assert.match(stage, /type StagePreset = 'original' \| 'focus' \| 'wide' \| 'crossed' \| 'classic' \| 'cantus' \| 'nimbus'/);
  assert.match(stage, /let preset: StagePreset = 'original'/);
  assert.match(stage, /setPreset, getPreset\(\)/);
  assert.match(stage, /createLyricWindow\(pixi,/);
  assert.match(stage, /app\.canvas\.tabIndex = -1/);
  assert.match(html, /canvas:focus,canvas:focus-visible\{outline:none;box-shadow:none\}/);
});

test('TV text modes use existing lyric timestamps and safe DOM text nodes', async () => {
  const stage = await readFile('src/stage.ts', 'utf8');
  const html = await readFile('src/index.html', 'utf8');
  assert.match(stage, /function renderTextStage\(time: number\)/);
  assert.match(stage, /textContent = active\.fullText/);
  assert.match(stage, /textContent = scene\.lines\[i\]\.fullText/);
  assert.match(stage, /native\('seek', Math\.round\(active\.startTime \* 1000\)\)/);
  assert.match(html, /mode-classic/);
  assert.match(html, /mode-cantus/);
  assert.match(html, /mode-nimbus/);
});

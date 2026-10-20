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

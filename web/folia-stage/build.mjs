import { build } from 'esbuild';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

process.chdir(path.dirname(fileURLToPath(import.meta.url)));
const upstream = JSON.parse(await readFile('vendor/folia/UPSTREAM.json', 'utf8'));
for (const [file, digest] of Object.entries(upstream.files)) {
  const content = await readFile(`vendor/folia/${file}`);
  if (createHash('sha256').update(content).digest('hex') !== digest) throw new Error(`Upstream source changed: ${file}`);
}
const bundle = await build({ entryPoints: ['src/stage.ts'], bundle: true, write: false, minify: true,
  format: 'iife', target: ['chrome100', 'safari16'], legalComments: 'inline', metafile: true });
const licenses = new Map([
  ['Folia — chthollyphile — AGPL-3.0', await readFile('vendor/folia/LICENSE', 'utf8')],
  // @pixi/colord's npm package omits its declared MIT license file; preserve the upstream license.
  ['@pixi/colord', await readFile('vendor/colord-LICENSE', 'utf8')],
]);
for (const file of Object.keys(bundle.metafile.inputs)) {
  const marker = file.lastIndexOf('node_modules/');
  if (marker < 0) continue;
  const parts = file.slice(marker + 13).split('/');
  const name = parts[0].startsWith('@') ? parts.slice(0, 2).join('/') : parts[0];
  const packageRoot = path.resolve(file.slice(0, marker + 13), name);
  if (licenses.has(name)) continue;
  for (const filename of ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'LICENSE-MIT']) {
    try { licenses.set(name, await readFile(path.join(packageRoot, filename), 'utf8')); break; } catch {}
  }
  if (!licenses.has(name)) throw new Error(`Missing runtime license: ${name}`);
}
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const notices = [...licenses].map(([name, text]) => `${name}\n\n${text}`).join('\n\n────────────────\n\n');
let html = await readFile('src/index.html', 'utf8');
html = html.replace('/* STAGE_BUNDLE */', () => bundle.outputFiles[0].text.replace(/<\/script/gi, '<\\/script'))
  .replace('<!-- LICENSES -->', () => escape(notices)).replaceAll('UPSTREAM_COMMIT', upstream.commit);
const target = '../../shared/src/commonMain/composeResources/files/folia-stage.html';
await mkdir(path.dirname(target), { recursive: true });
await writeFile(target, html);
console.log(`Offline Folia stage: ${Buffer.byteLength(html)} bytes, ${Object.keys(upstream.files).length} verified upstream files`);

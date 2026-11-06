# MingMusic Folia stage

The 字境 mode directly runs Folia's original `createLyricWindow` renderer in the system WebView.
Playback, lyrics association/offset, queue, signing and navigation remain in the native application.

## Source and licenses

- Upstream: https://github.com/chthollyphile/folia-major
- Pinned commit: `ced47852c64c2709faebe0190d430c3ab75ca2a4`
- The 29 files in `vendor/folia/src` are copied **without modifications**. `vendor/folia/UPSTREAM.json`
  records their SHA-256 digests; the build rejects edits. Preserve the upstream copyright notices
  and [AGPL-3.0 license](vendor/folia/LICENSE).
- `src/stage.ts` is the MingMusic host adapter, licensed AGPL-3.0-or-later. It selects upstream
  typography presets, forwards the music clock, and handles native seek/blank/source actions.
- The remainder of MingMusic retains its GPL-3.0 license. GPLv3/AGPLv3 section 13 permits this
  combination; the Folia component keeps its AGPL terms. Source and build instructions are included
  in this public repository and offered from the stage's **源码与许可证** panel.
- Runtime package notices are embedded in the offline HTML. `@pixi/colord` omits a LICENSE file
  from its npm tarball; `vendor/colord-LICENSE` preserves its MIT license from
  https://github.com/omgovich/colord/blob/master/LICENSE (blob `e437003733acd123c76c2a9df0c13a6f3b02b51e`).

## Build and verification

Use Node.js 24 and pnpm 11.25.0 in this directory:

```sh
pnpm install --frozen-lockfile
pnpm build
pnpm test
pnpm exec playwright install chromium
pnpm test:render
```

The checked-in output is `shared/src/commonMain/composeResources/files/folia-stage.html`.
It contains all renderer JavaScript, procedural textures and license text; it uses system fonts
and has no CDN, network runtime, local server or audio permission requirement. Android uses
system WebView, iOS WKWebView, and the existing desktop Nucleus WebView backend.

`test:render` checks the actual WebGL renderer offline, including reading order, bounds, vertical
composition, seeking, word clicks, pause/play, graphemes, untrusted lyric text and disposal.
Screenshots are written to `build/folia-stage-previews` at the repository root. Set
`FOLIA_RENDER_MOTION=1` to also capture 180 actual music-time frames in `build/folia-stage-motion`.
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` can select an existing local Chromium executable.

Updating upstream requires replacing the source files and their manifest together, reviewing the
license/dependency changes, then rebuilding and running both native bridge and browser checks.

## TV lyric layout presets (experimental)

The existing Folia glyph stage remains the default `original` preset. In the immersive player's stage settings, choose 原版字境 / 聚焦歌词 / 宽屏歌词 / 交错歌词. The Kotlin host forwards the selection through `MingFoliaStage.setPreset('original' | 'focus' | 'wide' | 'crossed')`.

These are four layouts of the **same** pinned Folia `createLyricWindow` renderer, not ports of Navidrome Stage's Classic/Cantus/Nimbus/etc. The player clock, lyric parsing, seeks and playback are unchanged. The setting uses Compose `rememberSaveable` (screen-state restoration, not a durable cross-launch preference).

The HTML canvas removes CSS border/outline/focus-visible styling and sets `tabIndex=-1`. If a yellow rectangle persists on a physical Android TV, inspect native Compose focus highlighting or TV accessibility focus; do not remove all keyboard/remote focus indication globally.

After modifications rebuild the bundled offline HTML with `pnpm build` before packaging Android. This branch has not yet been built or tested on a TV.

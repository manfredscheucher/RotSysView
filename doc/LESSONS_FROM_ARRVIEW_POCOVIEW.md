# Lessons from ArrView and PoCoView (for RotSysView)

A cheat sheet for the agent building RotSysView. Everything comes from the ArrView and PoCoView docs,
git logs, Manfred's global rules (`~/.claude/CLAUDE.md`) and the PoCoView memory
(`~/.claude/projects/-Users-manfred-github-PoCoView/memory/`). Pointers: `see <repo>/<file>:<section>`.
`ArrView/doc/SPEC.md` sections are dated headings, newest first.

## 0. State of RotSysView when this was written (2026-11-20)

- Core already ported: `RotationSystem`, `Planarization`, `Tutte.kt` (IteratedTutte taken from ArrView,
  with pyrslib draw2 / rotsys-sat weights), `Layout.kt` (`DrawStyle.DRAW2` / `ROTSYS`, graph_2_ipe
  control points, open uniform B-splines per edge), `Geometry.kt` (closed / open B-splines).
- Gaps compared with the siblings:
  - no `commonTest` / `jvmTest` source sets yet;
  - no `doc/SPEC.md` or `doc/DEVELOPMENT.md`, although README links both;
  - README still describes pseudocircles (copied from ArrView);
  - `jsMain/resources/index.html` has no WebGL probe (see section 3).

## 1. Manfred's working rules that bit us

- **Implement literally.** A formula, value, name or constant from Manfred goes in exactly as given.
  If something looks wrong, ask before deviating, never afterwards. See `~/.claude/CLAUDE.md`
  ("MACH GENAU DAS WAS MANFRED SAGT").
  - Example: ArrView first used its own Bezier tangents and weight guards instead of draw7.sage. Manfred
    called the curves buggy and "not his code", and everything was redone as a literal port. See
    `ArrView/doc/SPEC.md:2026-11-19 Curves and layout ported literally`.
- **Port the original sage/python literally; settle questions by reading and running it, not by
  asking.** Read every version (py2 `program/` and py3 `program_py3/` differ) and run sage
  (`/usr/local/bin/sage`) to compare. See `PoCoView memory: run-original-dont-ask.md`.
  - The py3 copy had an extra `assert(2 not in deg)`. Claude misread it as "the original refuses
    digons" and asked Manfred. His answer: "touchings sind dort!". See
    `ArrView/doc/SPEC.md:2026-11-19 ...: Model 1 digons checked against the original draw7.sage`.
  - PoCoView's optimizers are literal ports of pyotlib2/qtvis: "when something looks odd, check the
    original first". See `PoCoView/doc/DEVELOPMENT.md:Project layout`.
- **Explicit source you cannot read: stop and tell Manfred.** Do not fall back to another source.
  - ArrView left Ortner's 4-tuple criterion unimplemented because the source was not available locally.
    See `ArrView/doc/SPEC.md:2026-11-17 First version`.
  - ArrView's examples rule: "If the website cannot be read: stop that part and report." See
    `ArrView/doc/SPEC.md:2026-11-18 Examples dialog`.
- **Claude tests, Manfred does not.** Do automatic tests plus real GUI clicks (Compose UI tests),
  offscreen screenshots that Claude looks at, and the web build in headless Chrome. Never give Manfred
  a "please click X" list. See `~/.claude/CLAUDE.md:ENTWICKLUNGS-PIPELINE 3`.
- **Spec log.** Record every request, decision, interpretation and "Claude's choice (to confirm)" in
  `doc/SPEC.md`, newest first, quoting Manfred where useful. See `ArrView/doc/SPEC.md` (the model)
  and `PoCoView/doc/GUI_SPEC.md`.
- **Fresh reviewer.** Use one after implementing and for unclear requirements. Many ArrView commits
  are "Review fixes: ...". If Claude and the reviewer disagree, ask Manfred. See the `ArrView` git log.
- **Short texts, English.** Simple stays simple; uniform style does not mean uniform length. See
  `~/.claude/CLAUDE.md:Texte nicht aufblasen` and `ArrView/doc/SPEC.md:2026-11-18 Examples dialog`
  ("no padding; a fresh reviewer checks that they are uniform and not wordy").
- **Probes go into `scripts/probe/`.** Durable tooling goes into `scripts/`. See ArrView
  `scripts/probe/` (draw7_orig, layout_accel, web_cdp.py).
- **No push, fetch, gh, installs or branches.** Commit on main with short messages and no
  Co-Authored-By. Manfred pushes himself; give him the exact commands
  (`git -C ~/github/RotSysView push origin main`, `... push origin gh-pages`). See
  `~/.claude/CLAUDE.md`.
- **Short timeouts (~15 s).** Run targeted tests (`--tests X`). Long runs (ArrView's full sweep took
  4.5 min) only after asking. See `~/.claude/CLAUDE.md` and `ArrView/doc/DEVELOPMENT.md:Drawing sweep`.
- **No perf claims without measurements.** ArrView's "optimized layout" was measured (Anderson
  acceleration, tables), found not to match classic, and not shipped; the question went to Manfred.
  See `ArrView/doc/SPEC.md:2026-11-19 Layout implementation: classic / optimized`.

## 2. Drawing / visualisation

- **Iterated weighted Tutte: the sage code, not the paper.** The paper's formula taken literally
  collapsed the start example. draw7.sage: first iteration weights `1e-6`, then `w + 0.1(|uv|^2 - w)`
  plus `(j A(f))^4` for both faces of the edge (model 2: times 0.001), `A` = convex hull area, outer face
  included; stop test before the embedding; outer polygon `a0 = pi/l + pi/2` on the unit circle.
  RotSysView's draw2/rotsys weights differ (`RotSysView/.../core/Tutte.kt` KDoc). See
  `ArrView/doc/SPEC.md:2026-11-17 Phase 3a` and `:2026-11-19 Curves and layout ported literally`.
- **Solver**: warm-started CG; `TutteSolveTest` compares with Gaussian elimination on sage's matrix
  (1e-9 of the drawing size), which needed CG tolerance 1e-13 instead of 1e-10. See same section, "Solver".
- **No early convergence**: classic drifts to a limit (error ~1/j^4), so the picture depends on the
  iteration count and eps. Max iterations is Manfred's call (ArrView 50, sage 100, rotsys_draw 20).
  See `ArrView/doc/SPEC.md:2026-11-19 Layout implementation: classic / optimized`.
- **Curves = graph_2_ipe B-splines.** Per vertex `v1` the control points `xl, v1, xr`,
  `xl = v1 + l0 (v0 - v1)`, `l0 = sqrt(min(distances[v1]) / |v0 - v1|^2) / 3` (`distances[v]` = squared
  lengths of the curve edges at `v`), i.e. close to the vertex, so curves pass the crossings almost
  straight. Pseudocircles: closed uniform cubic B-spline (Ipe `u`); RotSysView edges: open form, ends
  tripled. Convert spans exactly to Bezier (`(P0+4P1+P2)/6, (2P1+P2)/3, (P1+2P2)/3, (P1+4P2+P3)/6`)
  so canvas, SVG, hit tests, labels and face fills share one curve. See
  `ArrView/doc/SPEC.md:2026-11-19 ...: What deviated before`, `RotSysView/.../core/Layout.kt:drawing`.
- **Model 1 vs model 2** (pseudocircles): model 1 = primal graph, intersecting only, digons as
  touchings (both splines share a straight piece); model 2 = primal-dual (vertex-edge-face) graph,
  always, digons as 2-gons. Model 2 is now the default, model 1 "legacy" and asks first. Help >
  Visualization shows primal / dual / primal-dual with figures drawn in the app (`HelpFigures.kt`).
  See `ArrView/doc/SPEC.md:2026-11-19 UI overhaul` items 1-3, 6.
- **Outer face**: draw7/draw3 take the face with the most automorphisms. ArrView's own "largest face"
  drew a tilted square and was replaced by the literal rule. Ties: sage's face order cannot be
  reproduced, take the first in your order and document it. "Make outer face" (right-click) is an undo
  step; after an edit the outer face stays if it still exists. RotSysView already ports draw2 (fewest
  crossings) and rotsys_draw (vertices only, else largest). See `ArrView/doc/SPEC.md:2026-11-19 ...:
  What deviated before`, `:2026-11-17 Phase 3a`, `RotSysView/.../core/Layout.kt:defaultOuterFace`.
- **Orientation**: RotSysView negates x when the embedding is mirrored (`mirrored`). Differences
  to sage that are only a rotation / mirror of the outer polygon are accepted. See
  `ArrView/doc/SPEC.md:...: Remaining deviations`.
- **Compare with the original numerically**: ArrView ran draw7.sage (minimal documented patches) and
  matched control points to 1e-10 of the radius after aligning rotation / reflection. Recipe:
  `ArrView/scripts/probe/draw7_orig/` (`compare.py`, `render.py`). See `ArrView/doc/SPEC.md:...: Model 1
  digons checked against the original draw7.sage`.
- **Geometry sweep**: `DrawingSweepTest` runs all small inputs and all flips through `CurveCheck`
  (finite, no self-crossing, pairs cross exactly as the combinatorics says; 64 points per Bezier piece,
  contact zones 1e-5 of the drawing). A representative part in `jvmTest` (~25 s), the full one behind
  `-PfullSweep`. For RotSysView: edges cross exactly at their planarization crossings. See
  `ArrView/doc/SPEC.md:...: The sweep`, `ArrView/doc/DEVELOPMENT.md:Drawing sweep`.
- **Numerical robustness**: a non-finite solve stops the iteration and keeps the last positions (a
  crash guard only). No guards that change the result (ArrView's weight clamps were removed). `fit`
  uses `maxOf(1e-9, w)`, `resize` ignores size 0, PoCoView fits an empty set. See
  `ArrView/doc/SPEC.md:...: Remaining deviations`, `ArrView/.../ui/ArrangementCanvas.kt`.
- **Layout off the UI thread**: one embedding per `step()` with `yield()` (web stays responsive);
  cancel on change, keep the old drawing until the new one is done, ignore clicks on a stale drawing,
  cache the last 32. See `ArrView/doc/SPEC.md:2026-11-17 Phase 3a`.
- **Coloured crossings only from real data**: ArrView reads them from the website's ipe drawings,
  tests them (isomorphic, proper, on a triangle), shows them only while the arrangement equals the
  stored example. "Do not invent colourings." See `ArrView/doc/SPEC.md:2026-11-19 Examples by purpose`.
- **Colour schemes**: Standard (default), rainbow, rainbow2, in OKLCH (C 0.16, L 0.42..0.72); unknown
  stored enum -> default; line width 0.5..8, default 4; "3.0" not "3" on the web (`oneDecimal`). See
  `ArrView/doc/SPEC.md:2026-11-18 Rainbow schemes in OKLCH`, `ArrView/.../core/ColorPalettes.kt`.
- **Exact integers (PoCoView)**: Long coordinates, exact orientations. "Snap with grid refinement":
  try the 4 grid corners nearest first, else double all coordinates (view zooms out, picture stays),
  cap ±2^29, one undo step. See `PoCoView/doc/GUI_SPEC.md:2026-11-19 Snap with grid refinement`.

## 3. UI conventions (settled)

- **Menus**: the first menu is named after the object and starts with Examples... (ArrView
  "Arrangement", PoCoView "Point Set"; for RotSysView "Rotation system"), then Edit, View, Help (pages,
  About last). ktoy `MenuButton`: one open menu, Escape closes, one-shot press guard, app keys off while
  a menu is open. See `ArrView/doc/SPEC.md:2026-11-17 Phase 3b`, `PoCoView/doc/GUI_SPEC.md:Adopt ktoy's look`.
- **Header**: bold app name in the menu bar, web-only subtitle ("Arrangement viewer", "Point
  configuration viewer"); status right in the toolbar, separator " · ", min width 280 dp, green info
  fades after 3 s. See `PoCoView/doc/GUI_SPEC.md:Header and status in the toolbar`.
- **Widgets**: columns beside the canvas (stacked in portrait), draggable dividers. At most 2 by
  default; one more closes the least recently clicked (a press in it or opening it counts). Settings >
  Widgets "Max. widgets: 2" with − / +. Old stored widget ids still open renamed widgets. See
  `ArrView/doc/SPEC.md:2026-11-19 UI overhaul` item 8, commit `0b454de`.
- **Settings slim**: explanations in round (i) popovers (`InfoButton`), not as text; choices as
  dropdowns in Settings (`SelectField` row, label left, 150 dp field right), not toolbar chips (ArrView
  removed its Model 1/2 chips); headed subgroups (Display / Widgets / Layout / Editing); one
  always-enabled "Reset to defaults". See `ArrView/doc/SPEC.md:2026-11-19 UI overhaul` items 1, 7, 9.
- **Remembered settings**: localStorage (web), Java prefs (desktop); tests use in-memory prefs
  (`-Dpocoview.prefs=memory`); corrupt values -> default or clamped. See
  `PoCoView/doc/GUI_SPEC.md:Adopt ktoy's look` (D).
- **Help pages with figures**, short plain paragraphs; `HelpClaimsTest` checks Help and USER_GUIDE
  against the code. See `ArrView/doc/DEVELOPMENT.md:Tests`.
- **Examples dialog**: groups by purpose, each opening with 1-2 sentences and links; short uniform
  leaf labels ("n = 7, 10 triangles"), no counts on group headers. Info area: description, n, count,
  member chooser `− [ 17 ] + of 2131` ("1 to M" in red, Load disabled), links, "Data: <path>". Only
  verified links; arXiv as abs page (RotSysView already links arxiv.org/abs/2504.02650). Website and
  data differ: data wins, report it. See `ArrView/doc/SPEC.md:2026-11-19 Examples by purpose`,
  `:2026-11-18 Examples dialog`.
- **Example data compiled in**: `composeApp/examples/` + Gradle `generateExamples` ->
  `GeneratedExamples.kt` (no "Loading..."); a test fails if a file and the catalogue disagree.
  PoCoView: one JSON object per set with metadata (`name, n, group, description, reference, url,
  source, claim, note, properties`) listed in `index.json`; ArrView is moving to that. Names
  `n<N>-<class>-<description>`, members `<set> #i`. See `PoCoView/doc/DEVELOPMENT.md:Example point
  sets`, `ArrView/doc/DEVELOPMENT.md:Example data`.
- **Sets** (several objects per file / example set): toolbar `< i / n >`, PageUp / PageDown, `,` /
  `.`; title `name · set i/n`; a switch clears undo and asks "Save changes?" for a draft; members
  decoded lazily; canvas takes focus back. After the load a notice "k arrangements loaded ... arrows
  < > ..." with "Don't show again". See `ArrView/doc/SPEC.md:2026-11-19 UI overhaul` item 10,
  `:2026-11-19 Curves and layout ...: Settings, undo, sets`.
- **"Don't show again"** only where it makes sense: notices (Welcome, sets notice, model 1 notices),
  not decisions such as "Save changes?" (coordinator's rule). Wording "Do not" vs "Don't" still open.
  See `ArrView/doc/SPEC.md:2026-11-19 UI overhaul: Decisions to confirm`.
- **"Save changes?"** (Save JSON / Save text / Discard / Cancel) before Load, Import, Open, Examples,
  window close, Cmd+Q; an unapplied widget draft counts as unsaved, never saved implicitly. See
  `PoCoView/doc/GUI_SPEC.md:Adopt ktoy's look` (review fixes 12, 13).
- **Fit / resize**: fit after a load, to the new drawing (commit `18f1448`). Coordinator's settled
  rule: refit on resize unless the user zoomed or panned. Note: today's `ViewTransform.resize` in both
  apps only keeps centre and zoom (`ArrView/.../ui/ArrangementCanvas.kt:resize`).
- **Input**: right-click, Ctrl+click (Mac), long press (touch, web) open the context menu (ktoy
  `MenuGestures`); moving < 4 px is a click, a drag pans. See `ArrView/doc/SPEC.md:2026-11-18 Long
  press opens the face menu`, `:Outer face clickable`.
- **Compact number fields** (ktoy): button height, width from the maximum, red border + small hint;
  never Material's 280 dp field. See `ArrView/doc/SPEC.md:2026-11-18 Compact number fields`.
- **No glyphs needing web fallback fonts**: draw checkmarks / tree arrows as paths; bundled DejaVu
  Sans Mono. See `ArrView/doc/SPEC.md:2026-11-17 Phase 3a`, `PoCoView/doc/GUI_SPEC.md:Adopt ktoy's look`.
- **WebGL probe in `index.html`**: Skiko needs webgl2; without it Chrome dies with "Cannot read
  properties of undefined (reading 'getParameter')" (reproduce: `--disable-webgl`). Test
  `getContext('webgl2') || getContext('webgl')` before loading, show "<App> needs WebGL. Turn on
  hardware acceleration ...", map the getParameter error to the same note. Done in ArrView, PoCoView,
  ktoy, ksat-chess, ksat-extra playground; **missing in RotSysView**. See `ArrView/doc/SPEC.md:2026-11-19
  UI overhaul` item 11, commit `0a7e3e7` / `67f0d34`.
- **Welcome**: Manfred's wording, 1-2 sentences on what the object is, feedback box ("Ideas or general
  feedback?" + mail). See `ArrView/doc/SPEC.md:2026-11-18 Welcome text says what pseudocircles are`.

## 4. Testing and deploy recipes

- **Targeted**: `./gradlew :composeApp:jvmTest --tests 'org.bytefred.rotsysview.core.XTest'`;
  `scripts/test.sh` = all jvmTest. jsTest stays out (mocha 2 s per test). See
  `ArrView/doc/DEVELOPMENT.md:Tests`.
- **Broad tests** (Manfred asked for them in ArrView): every example through every text format;
  every menu item and dialog button clicked once with an assertion; Copy to a fake clipboard; clicks
  via Compose UI tests. See `ArrView/doc/SPEC.md:2026-11-17 Broad tests`, table in
  `ArrView/doc/DEVELOPMENT.md:What the tests cover`.
- **Screenshots**: `RenderTest` renders offscreen into `composeApp/build/screenshots/` (every widget,
  dialog, Help page); Claude looks at them after UI changes.
- **Web check (headless Chrome)**, from `ArrView/doc/DEVELOPMENT.md:Web check`:
  ```
  ./scripts/buildWeb.sh
  python3 -m http.server 8612 --bind 127.0.0.1 --directory composeApp/build/dist/js/productionExecutable
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --use-angle=swiftshader \
    --enable-unsafe-swiftshader --user-data-dir=<scratch>/chrome-prof --window-size=1200,800 \
    --remote-debugging-port=9333 http://127.0.0.1:8612/
  python3 scripts/probe/web_cdp.py 9333 <out-dir> steps.json
  ```
  `web_cdp.py` (stdlib only; copy from `ArrView/scripts/probe/`): steps `wait, shot, click (button,
  mods), move, text, wheel, key, eval, reload`; collects console output and exceptions.
- **Deploy**: `./scripts/deployPages.sh` commits the build into the `.gh-pages/` worktree of
  `gh-pages`, no push; Manfred runs `git push origin gh-pages`. See `PoCoView/doc/DEVELOPMENT.md:Deploy`.
- **Port 8612**, in `composeApp/webpack.config.d/watch.js` and `PORT=` in `scripts/runWeb.sh` (keep
  both equal). Registry `~/github/.dev-ports.md` (ArrView 8611, PoCoView 8607).
- **Provenance**: a table in DEVELOPMENT.md (bundled file -> original path) and an import script that
  copies verbatim (`ArrView/scripts/import_examples.py`). See `ArrView/doc/DEVELOPMENT.md:Example data`.

## 5. Pitfalls we hit

- **Misread assert**: the py3-only `assert(2 not in deg)` read as "no digons in the original". Read
  all versions, run them. See section 1.
- **Unverified links / names**: the website links a wrong arXiv id for the colourings paper and
  "arxiv.org/abs/todo" for digongraphs; `n8_koester` has 7 circles. Verify, and note source errors in
  the spec. See `ArrView/doc/SPEC.md:2026-11-19 Examples by purpose`.
- **Website hedge copied**: "maybe the maximum" for n = 8, 9 although the research data had the maxima
  (38, 50). See `ArrView/doc/SPEC.md:2026-11-20 Maximum triangles for n = 8 and 9`.
- **Theorem hypotheses**: Linhart-Ortner Thm 4.5 (ii) assumes ccw orientations and wrongly rejected
  mirror images and flips. See `ArrView/doc/SPEC.md:2026-11-17 First version`.
- **Own "improvements" changed the picture**: largest outer face, own tangents, weight clamps (all
  reverted); Anderson acceleration could not reproduce classic. See section 2.
- **UI changes break click tests**: the new example tree broke the dialog and set tests (commit
  `1800310`). Update the click tests in the same change when adding or restructuring dialogs and
  notices.
- **Focus**: after a click on `>`, PageUp did nothing until the canvas took focus again; only the web
  check found it (commit `4591270`).
- **Fit too early**: after a load the view fitted the previous drawing while the new layout was
  computing; fit only once the drawing belongs to the current object (commit `18f1448`).
- **Sage 10.9** drops repeated edges unless `Graph(..., multiedges=True)`. See
  `ArrView/doc/SPEC.md:...: Model 1 digons checked against the original draw7.sage`.
- **sparse6 export** has an arbitrary vertex numbering: equal to the research strings only up to
  isomorphism; say so. See `ArrView/doc/SPEC.md:2026-11-17 First version`.
- **Web number format**: `3.0` showed as `3` on the web; format decimals explicitly (commit `6f2aaae`).

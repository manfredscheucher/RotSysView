# Development

## Run and test

```
./scripts/runWeb.sh       # browser with hot reload, http://localhost:8612/
./scripts/runJVM.sh       # desktop app
./scripts/test.sh         # unit tests (JVM)
./scripts/buildWeb.sh     # static site in composeApp/build/dist/js/productionExecutable/
./scripts/deployPages.sh  # commits the site to gh-pages (no push)
```

Targeted: `./gradlew :composeApp:jvmTest --tests 'org.bytefred.rotsysview.core.CoreTest'`.
`FullSweepTest` (routing on all 11556 drawings of K_7) runs only with `-PfullSweep`.

## Project layout

Single module `composeApp`, package `org.bytefred.rotsysview`; `core/` is pure Kotlin, `ui/` Compose.

| File | Content | From |
|---|---|---|
| `RotationSystem.kt` | rows, `sees`, crossings, lex-min, perfect C_n / T_n | pyrslib `RotationSystem.py`, rotsys-sat |
| `Properties.kt` | patterns (obstructions, convex, h-convex, gen. twisted), crossings, flips | rotsys-sat `rotsys.py`, pyrslib `enum_flips.py` |
| `FaceRouting.kt` | planarization by routing (default) | new (see SPEC) |
| `SatPlanarization.kt` | planarization by SAT (CaDiCaL) | rotsys-sat `rotsys_draw.sage` |
| `Planarization.kt` | planarization by search, planarity test | pyrslib `draw2.sage` |
| `Tutte.kt` | iterated Tutte embedding | ArrView (keep in sync), weights of pyrslib |
| `Layout.kt` | outer face, B-splines, face polygons | `graph_2_ipe`, draw2 / rotsys_draw |
| `Formats.kt`, `Examples.kt` | text formats, bundled examples | |

The UI is ported from ArrView (which took it from PoCoView). `Tutte.kt`, `Geometry.kt` and the UI
shell are shared in spirit with ArrView: changes there should be carried over (and a memo left in
the other repo's `doc/`).

## SAT solver

`cadical-kotlin` and `ksat-common` are submodules (only their `src/commonMain/kotlin` is compiled
in). Locally their `origin` points to `~/github/cadical-kotlin` and `~/github/ksat-common`.

## Example data

As ArrView and PoCoView: `composeApp/examples/` holds one JSON file per set, `index.json` lists them
as a two-level tree (supergroups, groups) in display order, the Gradle task `generateExamples` embeds
all of them into `GeneratedExamples.kt`, and `core/Examples.kt` parses them at first use (members
are decoded when shown). `ExamplesTest` fails if a file is not in the index or the other way round.

`index.json` (PoCoView's schema): `{"supergroups": [{"id", "title", "description"?, "links"?,
"groups": [{"id", "title", "description"?, "links"?, "files": [...]}]}]}`. Group ids are unique
among all groups. The dialog's tree keys are `SG/<supergroup id>` and `G/<supergroup id>/<group id>`;
the generated families (`ExampleFamily`, C_n and T_n for any n) are the first group of
`sat-framework`, key `G/sat-framework/families`. A group's links are shown unless they are exactly
its supergroup's (`sat-framework` links two papers, so each group shows its own).

A file is the metadata, then the data, RotSysView's saved JSON format (Open reads it too):

```json
{
  "name": "n5-all",
  "n": 5,
  "group": "All drawings of small K_n",
  "label": "n5-all",
  "description": "All 5 drawings of K_5.",
  "source": "rotsys-sat rotsys.py 5 -a -lex",
  "rotation_systems": [
    [[1,2,3,4],[0,2,3,4],[0,1,3,4],[0,1,2,4],[0,1,2,3]],
    ...
  ]
}
```

`"links"` (a list of `{"title", "url"}`) is optional; the dialog shows only those its group and
supergroup do not show. The files were converted from the former catalogue in code by
`scripts/probe/ConvertExamplesProbe.kt` (one-off).

Provenance:

| `composeApp/examples/` | Source |
|---|---|
| `all/n{4..7}-all.json` | rotsys-sat `sat/rotsys.py n -a -lex -r2f`: all drawings up to relabelling and mirroring, 2, 5, 102, 11556 (OEIS A276110; Aichholzer et al., Graphs and Combinatorics 2015, enumerated them up to n = 8) |
| `convexity/*` | the obstructions Π of rotsys-sat `rotsys.py` (Bergold, Scheucher, arXiv:2504.02650) |
| other groups | rotsys-sat `examples/`, the file named in each `"source"` |

## Web check

As ArrView: build, serve `composeApp/build/dist/js/productionExecutable` on 8612, headless Chrome
with `--remote-debugging-port`, then `python3 scripts/probe/web_cdp.py 9333 <out> steps.json`.

## Deploy to GitHub Pages

```
./scripts/deployPages.sh
git push origin gh-pages
```

`deployPages.sh` builds the site, copies it into a worktree of the `gh-pages` branch (`.gh-pages/`,
created on first use) and commits it there. It does not push. The site is served from
https://scheucher.dev/RotSysView/ (one-time setup on github.com: Settings, Pages, deploy
from branch `gh-pages`, root).

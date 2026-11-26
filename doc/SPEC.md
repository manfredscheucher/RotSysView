# RotSysView spec log

Newest first. Requests are Manfred's (summarized, quoted where useful), decisions and
interpretations are recorded so that a reviewer can check them.

## 2026-11-26: Sources on the examples

Request (Manfred): sources belong to the examples, not to the groups (in ArrView the newest maximal
triangles sets are new data, not on the old website); several sources per example are fine; the
complete lists may name Lidický's data as a source with the side remark that it counts mirror images
separately; in the opened example the description and parameters come first, the sources last ("auch
bei pocoview bitte": forwarded to the PoCoView session).
- `index.json` has no links; every set file has `"links"`. All drawings: Bergold and Scheucher,
  Aichholzer et al., OEIS A276110, Lidický's data (mirror images counted separately). n8-unlucky also
  Aichholzer et al. (Fig. 7, the only K_8 without a lucky vertex). Files without a paper link the
  rotsys-sat examples on GitHub.

## 2026-11-24: Database from rotsys-sat, history squashed

Request (Manfred): n5-all showed 6 drawings, the paper and OEIS A276110 count 5 (mirror images are
not counted twice); the data should come from his own tools, not from lidicky.name. Decisions
(AskUserQuestion): enumerate with rotsys-sat `rotsys.py n -a -lex` (lexicographic minima up to
relabelling and mirroring), cite the paper and A276110 as the source (not the tool), and squash the
RotSysView history into one initial commit.
- n4..n7-all: 2, 5, 102, 11556 drawings, the A276110 values. Lidický's data, the Hill paper and
  its link are gone; About links A276110. Lidický's line format stays readable (Open, Import): Manfred first asked to remove it, then "lass drin".
- Manfred: cite Aichholzer, Hackl, Pilz, Ramos, Sacristán, Vogtenhuber, "Empty triangles in good
  drawings of the complete graph" (Graphs and Combinatorics 2015) by its Springer link (not open
  access, the draft is ~/github/research/empty_triangles_GC.pdf). Its table has #RS 2, 5, 102,
  11556, 5370725 for n = 4..8; it is the paper of the All drawings group and replaces the arXiv
  link at Empty triangles (lucky vertices, Section 3).
- Monospace only for code (Manfred, via the peer sessions): the license texts in About use
  bodySmall. Still mono, as data or code: the lex-min JSON line, the Encoding and Export/Import text
  fields, the Import format examples, the number fields.
- Examples as ArrView and PoCoView (Manfred: "machn wie arrview und pocoview mit json"):
  `composeApp/examples/index.json` plus one JSON file per set (metadata and `"rotation_systems"`),
  ArrView's `core/Examples.kt` ported; the old copies (`all/*.txt`, `rotsys-sat/`) are gone. Ids:
  supergroups `database`, `sat-framework`; groups `all`, `convexity`, `all-edges-crossed`,
  `crossing-families`, `avoiding`, `empty-triangles`, `plane-hc`. Labels are the set names for now.
- Terms & privacy in About is collapsed like the license texts (Manfred): a TextButton "Terms & privacy" /
  "Hide terms & privacy" at the heading's place; open, the Privacy and Terms parts follow.
- gh-pages was squashed once to a single commit (Manfred, via the peer sessions; only RotSysView,
  only this once). deployPages.sh is unchanged and adds a commit per deploy.
- Texts without parentheses (Manfred): the intro defines a simple drawing in its own sentence,
  the Welcome hint "To flip, click a light yellow face." is a sentence of its own.

## 2026-11-24: GitHub page, no Google Fonts

Request (Manfred): prepare the GitHub page, checked with the ArrView and PoCoView sessions; the web
apps must not load fonts from fonts.gstatic.com (via turingmachine-ef and pocoview-06).
- Page as ArrView and PoCoView: deployPages.sh (local gh-pages commit, Manfred pushes), README link,
  DEVELOPMENT.md section; nothing more (no meta tags, no web user guide).
- Compose 1.12.1 downloads Noto fallback fonts from fonts.gstatic.com for glyphs the bundled fonts lack.
  RotSysView's own texts never need it; typed emoji, CJK, Korean or Hebrew (e.g. in Import) do. The
  downloader is internal, so index.html has `<meta http-equiv="Content-Security-Policy"
  content="connect-src 'self'">` (the same line in all three apps): the browser blocks the request
  before it leaves, such glyphs show as boxes and the console logs the failed download.

## 2026-11-24: Examples in supergroups

Request (Manfred, via the coordinator, the same for ArrView and PoCoView): group the examples in
supergroups with groups inside: "Database" (the complete lists n = 4..7), then "SAT framework examples"
(the topic groups from rotsys-sat). Same terms and dialog behaviour in all three apps.

Decisions:
- Data model: `ExampleSupergroup` (title, description, papers) in display order; every `ExampleGroup`
  has its supergroup. `Examples.groupsIn(sg)`, `Examples.familiesIn(sg)`; `inGroup` still sorts by n.
- Database: one group, All drawings of small K_n (paper link of Balogh, Lidický, Salazar stays on the
  group). Description "Complete lists for small n.", no links.
- SAT framework examples: description "From the SAT framework rotsys-sat and its papers.", links to
  both SAT papers (arXiv:2504.02650, arXiv:2305.09432; abs pages). Groups: Families (C_n, T_n, generated
  by rotsys.py), Not convex or not h-convex (rotsys.py patterns), Every edge crossed, No large crossing
  family, Avoiding C_k and T_k, Empty triangles, Plane Hamiltonian cycles. Each group keeps its own
  paper link and the sourced descriptions. Interpretation: Families and the convexity patterns were
  not named in the request; they come from rotsys-sat's rotsys.py, so they go here (no third
  supergroup).
- Dialog: a supergroup header looks like a group header (drawn arrow, SemiBold) with the larger
  titleLarge font, at depth 0; groups at depth 1, sets at depth 2. An open supergroup starts with its
  description and links. Everything starts collapsed; the state is kept while the app runs. Collapsing
  a supergroup or group that holds the selection (set or family) clears it (as ArrView).
- Start stays n6-all (`Examples.start`). `doc/img/examples.png` is RenderTest's
  `dialog-examples-tree.png` (both supergroups open).

Aligned with ArrView/RotSysView/PoCoView: an open group no longer repeats a paper link its supergroup
already shows (the SAT framework groups now show only their description; All drawings of small K_n
keeps its Hill paper). Later (with pca-a5): a group hides its links only when they equal the
supergroup's links exactly. SAT framework examples links two papers, so each of its groups shows its
own again (the set descriptions cite sections of it). Tree keys as ArrView's and PoCoView's: "SG/<supergroup>",
"G/<supergroup>/<group>", the families "G/<supergroup>/families"; links are compared by URL. Everything else already matched (titleLarge supergroup headers, bodyLarge
group headers without counts, 16 dp per level, collapse rules); `doc/img/examples.png` is unchanged.

## 2026-11-21: User manual and GUI walkthrough

Request (Manfred via pocoview-06, as in ArrView and PoCoView): a user manual `doc/USER_GUIDE.md`, go
through every GUI feature, list what looks broken or inconsistent and fix it.

Fixed:
- Links to https://scheucher.dev/RotSysView/ (README, deploy script); About links the web app and asks
  "Interesting rotation systems, ideas or general feedback?" (was "early development").
- Examples: the sets of each group by n; group "Not convex or not h-convex".
- Start: the whole set n6-all at #1 (as Examples > Load), with `<` `>`.
- Settings: Max. widgets (PoCoView's row); a third widget closes the least recently clicked one (was the
  first opened); Style and Planarization full width; Vertex numbers and eps remembered; Reset also
  resets the outer face and fits, as a style change does.
- Canvas: a "!" in a corner for the outer face (ArrView's); the "edge ab" label is cleared on a change.
- draw2's search stops when its drawing is no longer needed.
- Open file: `.json0` too; desktop filter "Rotation systems" (was "Arrangements").
- Messages "Applied the rotations" / "Cannot apply the JSON"; the routing timeout no longer points to
  doc/SPEC.md or SAT.
- Help: enum_all_flips; the planarizations need not give the same drawing; Widgets lists all Settings.

Later the same day (Manfred):
- Edge names with a separator, for every n: `{a,b}`, crossings `{a,b}×{c,d}` (was `ab`, unreadable from
  n = 11: "1011").
- Non-drawable systems (Manfred: "decide sensibly with a reviewer"): no flips (no drawing, so no
  triangles); the Flips widget says "Not drawable.", as Crossings and Properties ("flips -").
- Colours: Standard (12) and Ipe (17) switch to rainbow when there are more items than colours
  (draw2 colours edges, so Standard is rainbow from K_6 on; Ipe from n = 18).
- Descriptions of Empty triangles and Plane Hamiltonian cycles (Manfred: "lucky" is from a paper, look
  it up): from Bergold, Felsner, Reddy, Scheucher, arXiv:2305.09432 (Sections 4.1, 4.3) and Aichholzer
  et al., arXiv:1306.5081 (lucky vertices). n11/n12 h-convex and n11-noT5 are in no paper: only what
  the file name says.
- Sets notice after loading several rotation systems (ArrView's), with "Don't show again".
- Via pocoview-06 (as in ArrView and PoCoView): desktop Save asks "<name> already exists. Replace it?"
  (No: back to the chooser), for every save and export; menu items that open a dialog end in "..."
  (Export SVG..., the Help pages, About RotSysView...).

Open (Manfred decides): see the end of the 2026-11-21 report (SAT/draw2 time
budget, non-drawable flips, colours repeating for large n, descriptions of Empty triangles and Plane
Hamiltonian cycles, marking examples that time out, sets notice).

## 2026-11-21: Examples by property, families C_n / T_n, shortcuts, line width

### Requests (Manfred)

- "die examples bitte feiner strukturieren ... glieder bitte nach properties, was sie
  veranschaulichen und was das interessante is ... is ja nicht wesentlich wo's her kommt, auch wenn
  man das erwähnen sollte"
- "bei twisted und convex kannst auch family machn bzw generator ... convex = identity in jeder
  zeile, twisted is mitten drin umgedreht"; "wie bei horton sets also parameter n" (PoCoView).
- "die obstructions zu rotation systems sind irrelevant für die website. das sind ja eben genau die
  sachn die KEINE drawings sind"
- "line width 5 px default" (ArrView should have 5 too; PoCoView's lines stay as they are).
- "shortcuts vlt bei allen? ctrl+z undo und so standard zeugs? ctrl s speichern" (all apps: RotSysView,
  ArrView, PoCoView, ksat-chess, ksat-extra).

### Decisions

- Examples dialog as ArrView's: top-level groups by what they show, each with a one-line description
  and the paper; the source stays in each set's "Data:" line. Groups: All drawings of small K_n; Not
  convex, not h-convex; Every edge crossed; No large crossing family; Avoiding C_k and T_k; Empty
  triangles; Plane Hamiltonian cycles. The groups "Examples of the SAT paper" and "Singletons" are gone.
- "Families" node first (as PoCoView's): perfect convex C_n and perfect twisted T_n with an n field,
  generated by rotsys.py's `perfect_convex` / `perfect_twisted` (exactly Manfred's description).
  n = 4..20 for C_n, 4..18 for T_n: T_20 does not route within the 15 s budget in the browser (it does on
  the JVM), C_20 and T_18 draw in a few seconds (checked in headless Chrome). Default n = 6.
- The not-drawable examples (Π4o, Π5,1o, Π5,2o) are removed; the convex / h-convex patterns stay
  (they are drawings). Typed-in non-drawable systems still show their obstruction.
- Line width default 5 px (was 3).
- Shortcuts: Ctrl+O open file, Ctrl+S save file (new; also while a text field has focus, so the
  browser does not open or save the page; checked in Chrome: `defaultPrevented`), Ctrl+Z,
  Ctrl+Shift+Z, Ctrl+Y (existed). Cmd works like Ctrl. Menus show the keys. The other apps: memos
  sent 2026-11-21 to pocoview-06 (ArrView, PoCoView, and ArrView's line width 4 -> 5) and
  sat-chess-kotlin-5e (ksat-chess, ksat-extra playground); Claude changed none of those repos.
- Web: Compose does not prevent the browser's default for keys from a focused text field, so
  jsMain `main.kt` prevents Ctrl/Cmd+O/S on `window` in the bubble phase (pocoview-06's fix, PoCoView
  a7eb90c, taken over verbatim). Checked in headless Chrome: Ctrl+S and Cmd+S in the Encoding field
  open the app's Save dialog with `defaultPrevented`. Inside a dialog (Import, Examples) the browser's
  action is prevented too, but the app's may not run. On macOS Ctrl+O in a text field inserts a line
  break (system binding); Cmd+O works.

## 2026-11-20: Examples of the SAT paper and the obstructions

### Request (Manfred)

"hast du beispiele? in den andren repos sollten paar beispiele gewesen sein bzw im paper"

### Decisions

- All 25 files of rotsys-sat `examples/` are bundled verbatim (`composeApp/examples/rotsys-sat/`),
  group "Examples of the SAT paper". Descriptions only say what the paper states (Sections 5.3 to
  5.5, checked by reading arXiv:2504.02650); files the paper does not mention say "not in the paper"
  (empty_triangles, plane_HC, n20_hconvex_noC6, the `_sub` files; the paper says n = 22 timed out).
- Group "Obstructions": Π4o, Π5,1o, Π5,2o (not drawable), the two K5 forbidden by `--convex`, and
  Π6oh (`--hconvex`), from the patterns of rotsys.py.
- `ExamplesTest` checks the claims that can be computed: every `hc_aec` drawing (n = 11..21) is
  h-convex with every edge crossed; n12_GT_noT8 is generalized twisted; the obstructions are what
  they say. The C_k / T_k claims (Ramsey numbers) are not checked.
- The convex patterns were labelled Π5,1oc / Π5,2oc by Claude, probably swapped (the paper: Π5,1oc
  is T5); now named after rotsys.py's comments ("obstruction with 3 crossings", "T5").

### Routing for large n

- First version had two flaws: a cap of 64 routes per edge (could cut off the right route: not
  exact) and a free start for the first edge of a new vertex (many routes). Now: the first edge of a
  new vertex is routed from its other end (fixed start, the new vertex goes where it ends); the
  crossing side is enforced (rotsys-sat `dcross`; the convention is fixed by a test, the other one
  fails); routes are generated lazily; "fail first" (always the edge with the fewest routes next,
  one route = forced); backtracking over all vertices (drawings with the same rotation system differ
  by triangle flips (Gioan), not every drawing of K_k extends).
- Result: all of n <= 7 and 20 of the 25 SAT examples (up to n = 23, 4321 crossings) in under 2 s.
  n16_hc_aec, n20_hc_aec, n20_hconvex_noC6, n21_hconvex_noC6, n22_hc_aec_sub still backtrack too
  long; the app gives up after 15 s with a note. **TODO**: e.g. try triangle flips of the partial
  drawing when a vertex cannot be inserted, or SAT with the rotations fixed.
- Pattern search (convex, h-convex, ...) checks each part of the labelling as soon as it is
  assigned (same result, h-convex of K_21 in well under a second).

## 2026-11-19/20: First version

### Request (Manfred)

- A website like ArrView and PoCoView for rotation systems of complete graphs: visualize them "wie
  im draw programm" (pyrslib `sage_scripts/`), show the encodings (the rotation system is the
  sequence; compare small lambda in PoCoView), flips under their special conditions ("weiß auch gar
  nicht ob flip graph connected ist"), interactive local changes, settings and widget layout as in
  ArrView. GitHub page.
- Sources: [Bergold, Scheucher, arXiv:2504.02650](https://arxiv.org/abs/2504.02650) (definitions and
  axioms), [arXiv:2403.12898](https://arxiv.org/abs/2403.12898), JGAA article 2999.
- Later (TODO, not now): move rotsys-sat and a Rust port of pyrslib (`rusty-rotationsystems`) to
  `~/github`.
- "Erste Version wird eh nicht perfekt, zieh durch." Do not improvise on the algorithms; ask first.

### Decisions (asked and answered)

- Drawing style: both sage programs, default pyrslib `draw2.sage` (Settings, Style). The other is
  rotsys-sat `rotsys_draw.sage`.
- Planarization: Manfred first said "machs wie in draw2", then SAT (rotsys_draw) when draw2 was too
  slow. Measured (2026-11-20): draw2 literally does not finish many K_6 in 3 s; the SAT encoding of
  rotsys_draw takes 12 to more than 20 s with the native CaDiCaL on K_6 (so it is the encoding, not the
  Kotlin port); draw1's ideas (fewest crossings first, prefix test) leave 7 of 165 K_6 unfinished
  after 5 s. All three test planarity only and check the rotations afterwards. Manfred agreed to
  Claude's proposal, **routing**: insert the edges one by one into the plane map, leaving and entering
  the vertices where the rotations say, crossing exactly the required edges once, with backtracking.
  All 165 K_6 in 0.3 s, a sample of 230 K_7 in 0.6 s. SAT and draw2 stay as options (Settings,
  Planarization). Tutte, B-splines and the outer face are those of the scripts.
- Flips: pyrslib's `enum_all_flips` literally (a, b, c with c right after b around a and a right
  after c around b; swap in the rotations of a and b). Only flips whose result is drawable are
  offered, with the full test (Π4o, Π5,1o, Π5,2o), not only "type I" as in `enum_flips.py`.

### Literal ports and where they deviate

- Crossings: rotsys-sat `does_ab_cross_cd`; pyrslib's `enumerate_crossing_pairs` (lex-min of each
  K4) is ported too and used by the draw2 search. A test checks both agree on all drawings, n <= 6.
- Obstructions and classes: the patterns of rotsys-sat `rotsys.py` (`--valid4tuples`,
  `--valid5tuples`, `--convex`, `--hconvex`, `--gtwisted`, `forbid_subrs` semantics incl. mirror).
  `--gtwisted` forbids the K6 pattern only for n == 6, as in the script.
- draw2.sage refers to `forbidden_pairs`, whose computation is commented out (the script would stop
  with a NameError). Here that list is empty.
- Tutte: ArrView's `IteratedTutte` with the pyrslib / rotsys-sat weights: start `1e-6`, then
  `w + 0.01 (|uv|^2 - w) + j A(f)` for both faces (convex hull area, outer face included), stop when
  the normalized weights change by less than `1e-10`; 100 iterations (draw2) or 20 (rotsys_draw).
- Curves: `graph_2_ipe` control points per edge (`xl, v, xr`); Ipe reads `p0 m p1 ... pk c` with
  more than three points as a spline. Read here as an open uniform cubic B-spline with the end points
  tripled (starts at a, ends at b). **Claude's reading, to confirm.**
- Outer face: draw2 the first face with the fewest crossing vertices; rotsys_draw a face of vertices
  only, else the last largest. Sage's face order is not reproduced (first in our order).
- Orientation: if the embedding comes out mirrored, x is negated so the drawing shows the rotations
  counterclockwise (a test checks every vertex).
- Colours: draw2 colours edges (sage `color_by_label`, here the rainbow over the edges);
  rotsys_draw colours vertices with `IPE_COLORS` (edge ab solid in a's colour, dashed in b's).

### Claude's choices (to confirm)

- Vertices are numbered from 0 (as pyrslib and rotsys-sat), also in the GUI.
- Examples: all drawings of K_n for n = 4..7 from Lidický's data (`rotsys_upto7_unlabeled.txt`, mirror
  images counted separately: 2, 6, 165, 22730), perfect convex and twisted C_n, T_n for n = 5..9.
- Clicking a light yellow face flips: a flip touches the faces at its corners at a (between b, c) and
  at b (between c, a).
- Non-drawable systems can be loaded (Import, Encoding); they are shown with the obstruction they
  contain, without a drawing.

### Open / TODO

- Outer face: pocoview-06 relayed (2026-11-20) that Manfred wants ArrView's outer face rule (most
  symmetries, then largest) in RotSysView too. That differs from draw2 / rotsys_draw; ask Manfred
  which applies.
- SAT and draw2 run on the UI thread on the web (they block); a web worker would help.
- c-monotone classes (need the two extra vertices of rotsys-sat), the flip graph (components,
  connectivity), sets notice and (i) popovers as in ArrView, UI click tests, screenshot tests.
- Move rotsys-sat and the Rust port to `~/github` (Manfred: later).

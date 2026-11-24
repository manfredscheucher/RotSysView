# RotSysView

View and explore rotation systems of complete graphs (simple drawings of K_n), right in the browser.

**Web app:** https://scheucher.dev/RotSysView/

RotSysView reads rotation systems (rows, JSON lines as rotsys-sat prints them, Lidický's data
lines), checks drawability (the obstructions of Bergold and Scheucher, Theorem 3), draws them as
pyrslib's `draw2.sage` and rotsys-sat's `rotsys_draw.sage` do (planarization, iterated Tutte
embedding, B-splines), shows crossings and classes (convex, h-convex, generalized twisted,
crossing-maximal) and lets you change them by flips. It is written in Kotlin Multiplatform (Compose)
and also runs as a desktop app.

- User manual: [doc/USER_GUIDE.md](doc/USER_GUIDE.md)
- Specification and decisions: [doc/SPEC.md](doc/SPEC.md)
- Building, testing, deploying: [doc/DEVELOPMENT.md](doc/DEVELOPMENT.md)

## License

MIT, see [LICENSE](LICENSE).

package org.bytefred.rotsysview.core

/**
 * The planarization by inserting the edges one by one into the plane map drawn so far (Manfred,
 * 2026-11-20: the default; draw2's search and the SAT of rotsys_draw take too long from n = 6 on,
 * because they test only planarity and check the rotations afterwards).
 *
 * The map starts with vertex 0; then for k = 1, 2, ... the edges from k to 0, ..., k-1 ([insertStar]).
 * An edge uv
 * leaves u in the wedge where v belongs by the rotation of u, runs through the faces, crossing every
 * edge drawn so far that uv crosses (by the rotation system) exactly once and no other, without
 * crossing itself, and enters v in the wedge where u belongs by the rotation of v. All such routes
 * are tried (backtracking over the edges), so the result is exact: a planarization with the given
 * rotations, if the rotation system is drawable.
 *
 * Returns null if there is none.
 */
fun planarizeRouting(rs: RotationSystem, cancelled: () -> Boolean = { false }): Planarization? {
    if (rs.n < 2) return null
    val crossing = HashSet<Pair<Edge, Edge>>()
    for (c in rs.crossings()) {
        crossing += c.e to c.f
        crossing += c.f to c.e
    }
    return insertStar(rs, PlaneMap(rs.n), 1, listOf(Edge(0, 1)), crossing, cancelled)?.toPlanarization(rs)
}

/**
 * Inserts the edges [remaining] of the new vertex k into [map], then the stars of k + 1, ..., n - 1
 * (backtracking over all of them: drawings with the same rotation system can differ by triangle flips
 * (Gioan), and not every drawing of K_k extends). The first edge of a star is the
 * one with the fewest crossings with the drawing so far (draw1.sage's idea); it is routed from its
 * other end and k is placed where it ends. Then always the edge with the fewest routes next ("fail
 * first"): an edge with one route is forced, an edge without a route means backtracking.
 */
private fun insertStar(
    rs: RotationSystem,
    map: PlaneMap,
    k: Int,
    remaining: List<Edge>,
    crossing: Set<Pair<Edge, Edge>>,
    cancelled: () -> Boolean,
): PlaneMap? {
    if (remaining.isEmpty()) {
        if (k + 1 == rs.n) return map
        return insertStar(rs, map, k + 1, (0..k).map { Edge(it, k + 1) }, crossing, cancelled)
    }
    if (cancelled()) return null
    fun required(e: Edge) = map.edges().filter { (e to it) in crossing }.toSet()
    val e: Edge
    if (map.rot[k].isEmpty()) {
        e = remaining.minBy { required(it).size }
    } else {
        var best: Edge? = null
        var bestCount = Int.MAX_VALUE
        for (f in remaining) {
            val count = map.routes(rs, f.b, f.a, required(f), cancelled).take(2).count()
            if (count == 0) return null
            if (count < bestCount) {
                best = f
                bestCount = count
            }
            if (count == 1) break
        }
        e = best!!
    }
    // the first edge of k from its other end, the others from k
    val fresh = map.rot[k].isEmpty()
    val u = if (fresh) e.a else e.b
    val v = if (fresh) e.b else e.a
    for (route in map.routes(rs, u, v, required(e), cancelled)) {
        val next = map.copy()
        next.insert(rs, u, v, e, route)
        insertStar(rs, next, k, remaining - e, crossing, cancelled)?.let { return it }
    }
    return null
}

/** A crossed segment on a route: the dart (x -> y) in the face the route comes from. */
private class Crossed(val x: Int, val y: Int)

/**
 * A plane map: per vertex the counterclockwise rotation of its neighbours; per segment the edge of
 * K_n it belongs to. Vertices `0 until n` are those of K_n, the others are crossings. The face to
 * the left of the dart (x -> y) continues with (y -> z), z the neighbour before x around y.
 */
private class PlaneMap(val n: Int) {
    val rot = ArrayList<MutableList<Int>>()
    val owner = HashMap<Long, Edge>()
    /** Per dart (x -> y), whether it runs along its edge g from g.a to g.b. */
    val forward = HashMap<Long, Boolean>()
    /** The crossing pair of every crossing vertex `n + i`. */
    val crossingPairs = ArrayList<Pair<Edge, Edge>>()

    init {
        repeat(n) { rot += ArrayList<Int>() }
    }

    fun copy(): PlaneMap = PlaneMap(n).also { m ->
        for (v in rot.indices) if (v < n) m.rot[v].addAll(rot[v]) else m.rot += rot[v].toMutableList()
        m.owner.putAll(owner)
        m.forward.putAll(forward)
        m.crossingPairs.addAll(crossingPairs)
    }

    fun key(a: Int, b: Int): Long = minOf(a, b).toLong() * 1_000_000 + maxOf(a, b)

    fun dart(a: Int, b: Int): Long = a.toLong() * 1_000_000 + b

    fun edges(): Set<Edge> = owner.values.toSet()

    /** The K_n vertex reached from vertex [a] along its first segment towards K_n vertex [t] (a in K_n). */
    fun firstNeighbour(a: Int, t: Int): Int? = rot[a].firstOrNull { owner[key(a, it)] == Edge(minOf(a, t), maxOf(a, t)) }

    /** All faces as dart lists (x, y). */
    fun faces(): List<List<Pair<Int, Int>>> {
        val seen = HashSet<Long>()
        val faces = ArrayList<List<Pair<Int, Int>>>()
        fun dartKey(x: Int, y: Int) = x.toLong() * 1_000_000 + y
        for (x in rot.indices) for (y in rot[x]) {
            if (!seen.add(dartKey(x, y))) continue
            val face = ArrayList<Pair<Int, Int>>()
            var a = x
            var b = y
            face += a to b
            while (true) {
                val r = rot[b]
                val i = r.indexOf(a)
                val c = r[(i - 1 + r.size) % r.size]
                a = b
                b = c
                if (a == x && b == y) break
                seen.add(dartKey(a, b))
                face += a to b
            }
            faces += face
        }
        return faces
    }

    /**
     * Where the new edge to K_n vertex [t] leaves K_n vertex [a]: the face and the index of the dart
     * that starts the corner (the dart (a -> p), p the planarization neighbour before t around a),
     * or null if a has no edges yet.
     */
    fun wedge(rs: RotationSystem, faces: List<List<Pair<Int, Int>>>, a: Int, t: Int): Pair<Int, Int>? {
        if (rot[a].isEmpty()) return null
        // the K_n neighbours of a so far, in the order of the rotation of a; t's predecessor among them
        val targets = rs.rows[a].filter { it == t || firstNeighbour(a, it) != null }
        val i = targets.indexOf(t)
        val pred = targets[(i - 1 + targets.size) % targets.size]
        val succ = targets[(i + 1) % targets.size]
        val p = firstNeighbour(a, pred)!!
        val q = firstNeighbour(a, succ)!!
        // the wedge from p counterclockwise to q is the corner with darts (q -> a), (a -> p)
        for ((f, face) in faces.withIndex()) for ((j, d) in face.withIndex()) {
            if (d.first == a && d.second == p) {
                val prev = face[(j - 1 + face.size) % face.size]
                if (prev.first == q) return f to j
            }
        }
        error("no face for the wedge at $a")
    }

    /**
     * All routes of a new edge from [u] to [v] crossing exactly the edges [required], each once and
     * from the side the rotation system says (rotsys-sat's directed crossing `dcross`), as the
     * lists of crossed darts, one after the other (lazily). If [v] has no edges yet, a route may end
     * in any face (v is placed there).
     */
    fun routes(rs: RotationSystem, u: Int, v: Int, required: Set<Edge>, cancelled: () -> Boolean): Sequence<List<Crossed>> = sequence {
        val faces = faces()
        if (faces.isEmpty()) {
            if (required.isEmpty()) yield(emptyList())
            return@sequence
        }
        // the route crosses g = ab from the left of a -> b iff this is true
        val fromLeft = required.associateWith { g -> rs.dcross(u, v, g.a, g.b) != DCROSS_FROM_RIGHT }
        val faceOf = HashMap<Long, Pair<Int, Int>>()
        for ((f, face) in faces.withIndex()) for ((j, d) in face.withIndex()) faceOf[d.first.toLong() * 1_000_000 + d.second] = f to j
        val end = wedge(rs, faces, v, u)
        val startWedge = wedge(rs, faces, u, v)
        val chords = HashMap<Int, MutableList<Pair<Int, Int>>>()
        val crossed = HashSet<Edge>()
        val path = ArrayList<Crossed>()

        fun interleaves(f: Int, a: Int, b: Int): Boolean {
            val m = 2 * faces[f].size
            fun inside(x: Int) = ((x - a + m) % m) in 1 until ((b - a + m) % m)
            return chords[f].orEmpty().any { (c, d) -> inside(c) != inside(d) }
        }

        // entry: position on the boundary of f (2j corner, 2j + 1 segment), or -1 (free start)
        suspend fun SequenceScope<List<Crossed>>.go(f: Int, entry: Int) {
            if (cancelled()) return
            // finish at v (anywhere in the face if v is new)
            if (crossed.size == required.size && (end == null || f == end.first)) {
                if (end == null || entry < 0 || !interleaves(f, entry, 2 * end.second)) yield(path.toList())
            }
            for ((j, d) in faces[f].withIndex()) {
                val g = owner.getValue(key(d.first, d.second))
                if (g !in required || g in crossed) continue
                // the route comes from the left of the dart d
                if (forward.getValue(dart(d.first, d.second)) != fromLeft.getValue(g)) continue
                val pos = 2 * j + 1
                if (entry >= 0 && interleaves(f, entry, pos)) continue
                val (f2, j2) = faceOf.getValue(d.second.toLong() * 1_000_000 + d.first)
                if (entry >= 0) chords.getOrPut(f) { ArrayList() }.add(entry to pos)
                crossed += g
                path += Crossed(d.first, d.second)
                go(f2, 2 * j2 + 1)
                path.removeAt(path.lastIndex)
                crossed -= g
                if (entry >= 0) chords.getValue(f).removeAt(chords.getValue(f).lastIndex)
            }
        }

        if (startWedge != null) go(startWedge.first, 2 * startWedge.second)
        else for (f in faces.indices) go(f, -1)
    }

    /** Draws the edge [e] = uv along [route]: a crossing vertex per crossed segment. */
    fun insert(rs: RotationSystem, u: Int, v: Int, e: Edge, route: List<Crossed>) {
        val chain = ArrayList<Int>()
        chain += u
        for (c in route) {
            val x = rot.size
            rot += ArrayList<Int>()
            crossingPairs += (if (e < owner.getValue(key(c.x, c.y))) e to owner.getValue(key(c.x, c.y)) else owner.getValue(key(c.x, c.y)) to e)
            chain += x
        }
        chain += v
        // the ends: u and v get their new neighbour where the rotation says
        fun attach(a: Int, t: Int, nb: Int) {
            if (rot[a].isEmpty()) {
                rot[a].add(nb)
                return
            }
            val targets = rs.rows[a].filter { it == t || firstNeighbour(a, it) != null }
            val i = targets.indexOf(t)
            val p = firstNeighbour(a, targets[(i - 1 + targets.size) % targets.size])!!
            rot[a].add(rot[a].indexOf(p) + 1, nb)
        }
        attach(u, v, chain[1])
        attach(v, u, chain[chain.size - 2])
        // the crossings: segment x - y becomes x - c - y; around c: y, previous, x, next (the
        // route comes from the left of x -> y)
        for ((i, c) in route.withIndex()) {
            val cv = chain[i + 1]
            val g = owner.remove(key(c.x, c.y))!!
            val fw = forward.remove(dart(c.x, c.y))!!
            forward.remove(dart(c.y, c.x))
            forward[dart(c.x, cv)] = fw
            forward[dart(cv, c.y)] = fw
            forward[dart(cv, c.x)] = !fw
            forward[dart(c.y, cv)] = !fw
            rot[c.x][rot[c.x].indexOf(c.y)] = cv
            rot[c.y][rot[c.y].indexOf(c.x)] = cv
            owner[key(c.x, cv)] = g
            owner[key(cv, c.y)] = g
            rot[cv].addAll(listOf(c.y, chain[i], c.x, chain[i + 2]))
        }
        for (i in 1 until chain.size) {
            owner[key(chain[i - 1], chain[i])] = e
            forward[dart(chain[i - 1], chain[i])] = u == e.a
            forward[dart(chain[i], chain[i - 1])] = u != e.a
        }
    }

    fun toPlanarization(rs: RotationSystem): Planarization {
        val paths = LinkedHashMap<Edge, IntArray>()
        for (a in 0 until n) for (b in a + 1 until n) {
            val e = Edge(a, b)
            val path = arrayListOf(a)
            var prev = -1
            var cur = a
            while (cur != b) {
                val nxt = rot[cur].first { it != prev && owner[key(cur, it)] == e }
                prev = cur
                cur = nxt
                path += cur
            }
            paths[e] = path.toIntArray()
        }
        return Planarization(rs, crossingPairs.toList(), paths)
    }

    private companion object {
        /** Which side `dcross(u, v, a, b)` means; fixed by CoreTest.routingPlanarizesAllSmallDrawings (the other value fails there). */
        const val DCROSS_FROM_RIGHT = false
    }
}

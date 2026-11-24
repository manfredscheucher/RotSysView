package org.bytefred.rotsysview.core

import kotlin.random.Random

/**
 * The planarization of a drawing of K_n: the n vertices (`0 until n`) and a vertex per crossing
 * (`n + i` for [crossings]`[i]`), and per edge of K_n its vertex sequence from `a` to `b`.
 */
class Planarization(
    val rs: RotationSystem,
    val crossings: List<Pair<Edge, Edge>>,
    /** Per edge of K_n its vertices from a to b (crossings in between). */
    val paths: Map<Edge, IntArray>,
) {
    val n: Int get() = rs.n
    val vertexCount: Int get() = n + crossings.size

    /** The edges of the plane graph (`edges[2i]`, `edges[2i + 1]`) and the K_n edge each belongs to. */
    val edges: IntArray
    val edgeOwner: List<Edge>

    init {
        val list = ArrayList<Int>()
        val owner = ArrayList<Edge>()
        for ((e, path) in paths) for (i in 1 until path.size) {
            list += path[i - 1]
            list += path[i]
            owner += e
        }
        edges = list.toIntArray()
        edgeOwner = owner
    }

    val adjacency: List<Set<Int>> by lazy {
        val adj = List(vertexCount) { LinkedHashSet<Int>() }
        for (i in 0 until edges.size / 2) {
            adj[edges[2 * i]].add(edges[2 * i + 1])
            adj[edges[2 * i + 1]].add(edges[2 * i])
        }
        adj
    }

    /** The faces as vertex cycles (consistently oriented); the embedding is unique (3-connected, n >= 4). */
    val faces: List<List<Int>> by lazy { PlanarEmbedding.faces(vertexCount, adjacency) }

    /** Number of K_n vertices on face [f]... and crossing vertices: draw2's quality counts the crossings. */
    fun crossingsOn(face: List<Int>): Int = face.count { it >= n }
}

/**
 * The planarization as `rec_draw` of pyrslib's `draw2.sage` finds it: the edges in the order of
 * `combinations(N, 2)`; for the first edge not yet drawn all permutations of its crossings, in random
 * order, each tried if the graph drawn so far plus this edge's path is planar; backtracking.
 * draw2.sage refers to `forbidden_pairs`, whose computation is commented out (the script would stop
 * with a NameError); here the list is empty, so that check never skips a permutation.
 *
 * Returns null if no planarization exists (not drawable).
 */
fun planarizeDraw2(rs: RotationSystem, random: Random = Random.Default, cancelled: () -> Boolean = { false }): Planarization? {
    val n = rs.n
    val pairs = rs.crossingPairsPyrslib()
    val index = HashMap<Pair<Edge, Edge>, Int>()
    for ((i, p) in pairs.withIndex()) index[p] = n + i
    val all = ArrayList<Edge>()
    for (a in 0 until n) for (b in a + 1 until n) all += Edge(a, b)
    val crossesOf = all.associateWith { ArrayList<Edge>() }
    for ((ab, cd) in pairs) {
        crossesOf.getValue(ab).add(cd)
        crossesOf.getValue(cd).add(ab)
    }
    val vertexCount = n + pairs.size
    val adj = List(vertexCount) { HashSet<Int>() }
    val paths = LinkedHashMap<Edge, IntArray>()

    fun crossingVertex(e: Edge, f: Edge) = index.getValue(if (e < f) e to f else f to e)

    fun rec(k: Int): Boolean {
        if (k == all.size) return true
        if (cancelled()) return false
        val e = all[k]
        val onE = crossesOf.getValue(e).map { crossingVertex(e, it) }
        val perms = permutations(onE).shuffled(random)
        for (order in perms) {
            if (cancelled()) return false
            val verts = listOf(e.a) + order + e.b
            val added = ArrayList<Pair<Int, Int>>()
            for (i in 1 until verts.size) {
                val u = verts[i - 1]
                val v = verts[i]
                if (adj[u].add(v)) {
                    adj[v].add(u)
                    added += u to v
                }
            }
            if (isPlanar(vertexCount, adj)) {
                paths[e] = verts.toIntArray()
                if (rec(k + 1)) return true
                paths.remove(e)
            }
            for ((u, v) in added) {
                adj[u].remove(v)
                adj[v].remove(u)
            }
        }
        return false
    }

    if (!rec(0)) return null
    return Planarization(rs, pairs, all.associateWith { paths.getValue(it) })
}

/** All permutations of [list] (itertools order). */
internal fun <T> permutations(list: List<T>): List<List<T>> {
    if (list.size <= 1) return listOf(list)
    val result = ArrayList<List<T>>()
    for (i in list.indices) {
        val rest = list.subList(0, i) + list.subList(i + 1, list.size)
        for (p in permutations(rest)) result += listOf(list[i]) + p
    }
    return result
}

/**
 * Planarity of a simple graph (sage's `is_planar`): every biconnected component with at least 3
 * vertices is embedded with Demoucron-Malgrange-Pertuiset ([PlanarEmbedding]).
 */
fun isPlanar(vertexCount: Int, adj: List<Set<Int>>): Boolean {
    for (block in biconnectedComponents(vertexCount, adj)) {
        val verts = block.flatMap { listOf(it.first, it.second) }.distinct()
        if (verts.size < 5) continue
        if (block.size > 3 * verts.size - 6) return false
        val local = HashMap<Int, Int>()
        verts.forEachIndexed { i, v -> local[v] = i }
        val sub = List(verts.size) { HashSet<Int>() }
        for ((u, v) in block) {
            sub[local.getValue(u)].add(local.getValue(v))
            sub[local.getValue(v)].add(local.getValue(u))
        }
        try {
            PlanarEmbedding.faces(verts.size, sub)
        } catch (_: IllegalArgumentException) {
            return false
        }
    }
    return true
}

/** The edge sets of the biconnected components (Hopcroft-Tarjan, iterative). */
internal fun biconnectedComponents(vertexCount: Int, adj: List<Set<Int>>): List<List<Pair<Int, Int>>> {
    val disc = IntArray(vertexCount) { -1 }
    val low = IntArray(vertexCount)
    val result = ArrayList<List<Pair<Int, Int>>>()
    val edgeStack = ArrayList<Pair<Int, Int>>()
    var time = 0
    val neighbours = Array(vertexCount) { adj[it].toIntArray() }
    for (root in 0 until vertexCount) {
        if (disc[root] >= 0 || neighbours[root].isEmpty()) continue
        disc[root] = time
        low[root] = time
        time++
        // frames: vertex, parent, next neighbour index
        val stack = ArrayList<IntArray>()
        stack += intArrayOf(root, -1, 0)
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            val u = frame[0]
            if (frame[2] < neighbours[u].size) {
                val v = neighbours[u][frame[2]++]
                if (v == frame[1]) continue
                if (disc[v] < 0) {
                    edgeStack += u to v
                    disc[v] = time
                    low[v] = time
                    time++
                    stack += intArrayOf(v, u, 0)
                } else if (disc[v] < disc[u]) {
                    edgeStack += u to v
                    low[u] = minOf(low[u], disc[v])
                }
            } else {
                stack.removeAt(stack.lastIndex)
                val p = frame[1]
                if (p >= 0) {
                    low[p] = minOf(low[p], low[u])
                    if (low[u] >= disc[p]) {
                        val comp = ArrayList<Pair<Int, Int>>()
                        while (true) {
                            val e = edgeStack.removeAt(edgeStack.lastIndex)
                            comp += e
                            if (e.first == p && e.second == u) break
                        }
                        result += comp
                    }
                }
            }
        }
    }
    return result
}


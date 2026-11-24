package org.bytefred.rotsysview.core

/**
 * Planar embedding of a simple 2-connected graph with the algorithm of Demoucron, Malgrange and
 * Pertuiset: start with a cycle and repeatedly route a path of a fragment (bridge) through a face
 * that can take all of the fragment's attachments, preferring fragments with a single admissible
 * face. Used to find the faces of the model 1 dual graph (SageMath used its planarity test there).
 */
internal object PlanarEmbedding {
    /**
     * The faces of a planar embedding as vertex cycles, all oriented consistently (every edge is
     * walked once in each direction).
     * @throws IllegalArgumentException if the graph is not connected, not 2-connected or not planar
     */
    fun faces(vertexCount: Int, adjacency: List<Set<Int>>): List<List<Int>> {
        require(vertexCount >= 3) { "graph has fewer than 3 vertices" }
        require(isConnected(vertexCount, adjacency)) { "graph is not connected" }
        val totalEdges = adjacency.sumOf { it.size } / 2
        val inH = BooleanArray(vertexCount)
        val edgesInH = HashSet<Long>()
        fun key(u: Int, v: Int): Long = minOf(u, v).toLong() * vertexCount + maxOf(u, v)
        fun addPath(path: List<Int>) {
            for (v in path) inH[v] = true
            for (idx in 0 until path.size - 1) edgesInH.add(key(path[idx], path[idx + 1]))
        }

        val cycle = findCycle(vertexCount, adjacency)
        addPath(cycle + cycle.first())
        val faces = mutableListOf(cycle, cycle.reversed())

        while (edgesInH.size < totalEdges) {
            val fragments = fragments(vertexCount, adjacency, inH, edgesInH, ::key)
            var chosen: Fragment? = null
            var chosenFaces = 0
            var chosenFace = -1
            for (fragment in fragments) {
                require(fragment.attachments.size >= 2) { "graph is not 2-connected" }
                val admissible = faces.indices.filter { f -> faces[f].containsAll(fragment.attachments) }
                require(admissible.isNotEmpty()) { "graph is not planar" }
                if (chosen == null || admissible.size < chosenFaces) {
                    chosen = fragment
                    chosenFaces = admissible.size
                    chosenFace = admissible.first()
                    if (chosenFaces == 1) break
                }
            }
            val path = pathThrough(chosen!!, adjacency)
            val face = faces[chosenFace]
            val ia = face.indexOf(path.first())
            val ib = face.indexOf(path.last())
            val interior = path.subList(1, path.size - 1)
            val first = cyclicSlice(face, ia, ib) + interior.reversed()
            val second = cyclicSlice(face, ib, ia) + interior
            faces[chosenFace] = first
            faces.add(second)
            addPath(path)
        }
        return faces
    }

    /** A fragment: the vertices outside H of one component (empty for a single edge) and its attachments. */
    private class Fragment(val inner: Set<Int>, val attachments: List<Int>)

    private fun fragments(
        vertexCount: Int,
        adjacency: List<Set<Int>>,
        inH: BooleanArray,
        edgesInH: Set<Long>,
        key: (Int, Int) -> Long,
    ): List<Fragment> {
        val result = mutableListOf<Fragment>()
        for (u in 0 until vertexCount) {
            if (!inH[u]) continue
            for (v in adjacency[u]) {
                if (u < v && inH[v] && key(u, v) !in edgesInH) result.add(Fragment(emptySet(), listOf(u, v)))
            }
        }
        val seen = BooleanArray(vertexCount)
        for (s in 0 until vertexCount) {
            if (inH[s] || seen[s]) continue
            val component = mutableSetOf<Int>()
            val attachments = linkedSetOf<Int>()
            val stack = ArrayDeque(listOf(s))
            seen[s] = true
            while (stack.isNotEmpty()) {
                val u = stack.removeLast()
                component.add(u)
                for (v in adjacency[u]) {
                    if (inH[v]) {
                        attachments.add(v)
                    } else if (!seen[v]) {
                        seen[v] = true
                        stack.addLast(v)
                    }
                }
            }
            result.add(Fragment(component, attachments.toList()))
        }
        return result
    }

    /** A path between two attachments of [fragment] through its inner vertices. */
    private fun pathThrough(fragment: Fragment, adjacency: List<Set<Int>>): List<Int> {
        if (fragment.inner.isEmpty()) return fragment.attachments
        val a = fragment.attachments.first()
        val parent = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        for (v in adjacency[a]) {
            if (v in fragment.inner && v !in parent) {
                parent[v] = a
                queue.addLast(v)
            }
        }
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val b = adjacency[u].firstOrNull { it != a && it in fragment.attachments }
            if (b != null) {
                val path = mutableListOf(b, u)
                var x = u
                while (parent.getValue(x) != a) {
                    x = parent.getValue(x)
                    path.add(x)
                }
                path.add(a)
                return path.reversed()
            }
            for (v in adjacency[u]) {
                if (v in fragment.inner && v !in parent) {
                    parent[v] = u
                    queue.addLast(v)
                }
            }
        }
        throw IllegalArgumentException("graph is not 2-connected")
    }

    /** `list[from], list[from + 1], ..., list[to]` (cyclically, inclusive). */
    private fun cyclicSlice(list: List<Int>, from: Int, to: Int): List<Int> {
        val result = mutableListOf<Int>()
        var i = from
        while (true) {
            result.add(list[i])
            if (i == to) return result
            i = (i + 1) % list.size
        }
    }

    private fun isConnected(vertexCount: Int, adjacency: List<Set<Int>>): Boolean {
        val seen = BooleanArray(vertexCount)
        val stack = ArrayDeque(listOf(0))
        seen[0] = true
        while (stack.isNotEmpty()) {
            for (v in adjacency[stack.removeLast()]) {
                if (!seen[v]) {
                    seen[v] = true
                    stack.addLast(v)
                }
            }
        }
        return seen.all { it }
    }

    /** Some cycle of the graph (from a BFS tree and one non-tree edge). */
    private fun findCycle(vertexCount: Int, adjacency: List<Set<Int>>): List<Int> {
        val parent = IntArray(vertexCount) { -1 }
        val depth = IntArray(vertexCount) { -1 }
        val queue = ArrayDeque(listOf(0))
        depth[0] = 0
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            for (v in adjacency[u]) {
                if (depth[v] < 0) {
                    depth[v] = depth[u] + 1
                    parent[v] = u
                    queue.addLast(v)
                } else if (v != parent[u] && parent[v] != u) {
                    // non-tree edge u-v: the two tree paths up to their common ancestor
                    val left = mutableListOf(u)
                    val right = mutableListOf(v)
                    var x = u
                    var y = v
                    while (x != y) {
                        if (depth[x] >= depth[y]) {
                            x = parent[x]
                            left.add(x)
                        } else {
                            y = parent[y]
                            right.add(y)
                        }
                    }
                    right.removeAt(right.lastIndex)
                    return left + right.reversed()
                }
            }
        }
        throw IllegalArgumentException("graph has no cycle")
    }
}

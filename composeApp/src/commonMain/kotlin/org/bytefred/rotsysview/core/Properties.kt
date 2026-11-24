package org.bytefred.rotsysview.core

/**
 * A forbidden sub-configuration of rotsys-sat (`forbid_subrs`): [rows] is a rotation system on
 * `0 until k`; a system contains it if some injective labelling `I` of its vertices makes every
 * `a` see `I[b], I[c], I[d]` counterclockwise for all `b, c, d` in row `a` (in row order), or
 * clockwise for all of them (the mirror image).
 */
class Pattern(val name: String, val rows: List<List<Int>>) {
    val k: Int get() = rows.size

    /** The triples `(a, b, c, d)` the clause of rotsys-sat checks. */
    private val checks: List<IntArray> = rows.indices.flatMap { a ->
        val row = rows[a]
        buildList {
            for (i in row.indices) for (j in i + 1 until row.size) for (l in j + 1 until row.size) add(intArrayOf(a, row[i], row[j], row[l]))
        }
    }

    /** The checks by the largest pattern vertex in them: they can be decided once that one is labelled. */
    private val checksAt: List<List<IntArray>> = List(k) { i -> checks.filter { it.max() == i } }

    /**
     * A labelling (`I[i]` is the vertex of pattern vertex `i`) under which [rs] contains this
     * pattern, or null. The labelling is built vertex by vertex; a partial one is dropped as soon
     * as its decided checks rule out both the pattern and its mirror image.
     */
    fun findIn(rs: RotationSystem): List<Int>? {
        if (rs.n < k) return null
        val labels = IntArray(k)
        val used = BooleanArray(rs.n)
        fun search(i: Int, canBeTrue: Boolean, canBeFalse: Boolean): Boolean {
            if (i == k) return true
            for (v in 0 until rs.n) {
                if (used[v]) continue
                labels[i] = v
                var t = canBeTrue
                var f = canBeFalse
                for ((a, b, c, d) in checksAt[i]) {
                    val seen = rs.sees(labels[a], labels[b], labels[c], labels[d])
                    if (seen) f = false else t = false
                    if (!t && !f) break
                }
                if (!t && !f) continue
                used[v] = true
                if (search(i + 1, t, f)) return true
                used[v] = false
            }
            return false
        }
        return if (search(0, canBeTrue = true, canBeFalse = true)) labels.toList() else null
    }

    companion object {
        /** Pi_4^o: the non-drawable pre-rotation system on 4 vertices (`--valid4tuples`). */
        val PI4 = Pattern("Π4o", listOf(listOf(1, 2, 3), listOf(0, 2, 3), listOf(0, 1, 3), listOf(0, 2, 1)))

        /** Pi_5,1^o ("forbidden type I", `--valid5tuples`). */
        val PI51 = Pattern("Π5,1o", listOf(listOf(1, 2, 3, 4), listOf(0, 2, 3, 4), listOf(0, 3, 1, 4), listOf(0, 4, 2, 1), listOf(0, 3, 1, 2)))

        /** Pi_5,2^o ("forbidden type II", `--valid5tuples`). */
        val PI52 = Pattern("Π5,2o", listOf(listOf(1, 2, 3, 4), listOf(0, 2, 4, 3), listOf(0, 3, 1, 4), listOf(0, 4, 2, 1), listOf(0, 1, 3, 2)))

        /** The obstructions of drawability (Theorem 3 of Bergold, Scheucher, arXiv:2504.02650). */
        val OBSTRUCTIONS = listOf(PI4, PI51, PI52)

        /** `--convex`: "obstruction with 3 crossings" and "T5, i.e., obstruction 5 crossings" (rotsys.py's comments). */
        val CONVEX = listOf(
            Pattern("convex obstruction with 3 crossings", listOf(listOf(1, 2, 3, 4), listOf(0, 2, 4, 3), listOf(0, 1, 3, 4), listOf(0, 1, 4, 2), listOf(0, 3, 1, 2))),
            Pattern("T5 (convex obstruction with 5 crossings)", listOf(listOf(1, 2, 3, 4), listOf(0, 2, 3, 4), listOf(0, 1, 3, 4), listOf(0, 1, 4, 2), listOf(0, 1, 3, 2))),
        )

        /** `--hconvex` (on top of convex). */
        val HCONVEX = Pattern(
            "Π6oh",
            listOf(listOf(1, 2, 3, 4, 5), listOf(0, 2, 3, 4, 5), listOf(0, 1, 5, 3, 4), listOf(0, 1, 2, 4, 5), listOf(0, 1, 2, 3, 5), listOf(0, 1, 3, 4, 2)),
        )

        /** `--gtwisted`: forbidden for n >= 4. */
        val GTWISTED4 = Pattern("gt K4", listOf(listOf(1, 2, 3), listOf(0, 3, 2), listOf(0, 1, 3), listOf(0, 2, 1)))

        /** `--gtwisted`: forbidden only for n == 6 (as in rotsys.py). */
        val GTWISTED6 = Pattern(
            "gt K6",
            listOf(listOf(1, 2, 3, 4, 5), listOf(0, 2, 3, 4, 5), listOf(0, 1, 3, 5, 4), listOf(0, 4, 1, 5, 2), listOf(0, 3, 1, 5, 2), listOf(0, 1, 3, 2, 4)),
        )

        /** `--gtwisted`: the four forbidden K5 types. */
        val GTWISTED5 = listOf(
            listOf(listOf(1, 2, 3, 4), listOf(0, 3, 4, 2), listOf(0, 1, 4, 3), listOf(0, 2, 4, 1), listOf(0, 3, 2, 1)),
            listOf(listOf(1, 2, 3, 4), listOf(0, 2, 3, 4), listOf(0, 1, 4, 3), listOf(0, 1, 2, 4), listOf(0, 1, 3, 2)),
            listOf(listOf(1, 2, 3, 4), listOf(0, 2, 4, 3), listOf(0, 1, 3, 4), listOf(0, 1, 4, 2), listOf(0, 3, 1, 2)),
            listOf(listOf(1, 2, 3, 4), listOf(0, 2, 3, 4), listOf(0, 1, 3, 4), listOf(0, 1, 2, 4), listOf(0, 1, 2, 3)),
        ).mapIndexed { i, rows -> Pattern("gt K5 #${i + 1}", rows) }
    }
}

/** An obstruction found in a system: the pattern and the vertices (pattern vertex i is `vertices[i]`). */
class Obstruction(val pattern: Pattern, val vertices: List<Int>) {
    override fun toString() = "${pattern.name} on ${vertices.joinToString(" ")}"
}

/** The first obstruction of [patterns] contained in [rs], or null. */
fun firstObstruction(rs: RotationSystem, patterns: List<Pattern>): Obstruction? {
    for (p in patterns) p.findIn(rs)?.let { return Obstruction(p, it) }
    return null
}

/** The first of Π4o, Π5,1o, Π5,2o in [rs], or null if it is drawable (Theorem 3). */
fun RotationSystem.obstruction(): Obstruction? = firstObstruction(this, Pattern.OBSTRUCTIONS)

val RotationSystem.isDrawable: Boolean get() = obstruction() == null

/** Convex (rotsys-sat `--convex`); assumes a drawable system. */
val RotationSystem.isConvex: Boolean get() = firstObstruction(this, Pattern.CONVEX) == null

/** h-convex (rotsys-sat `--hconvex`). */
val RotationSystem.isHConvex: Boolean get() = isConvex && Pattern.HCONVEX.findIn(this) == null

/** Generalized twisted, with the forbidden configurations of rotsys-sat's `--gtwisted`. */
val RotationSystem.isGeneralizedTwisted: Boolean
    get() {
        if (n >= 4 && Pattern.GTWISTED4.findIn(this) != null) return false
        if (n == 6 && Pattern.GTWISTED6.findIn(this) != null) return false
        return firstObstruction(this, Pattern.GTWISTED5) == null
    }

/** An edge `a < b`, written `{a,b}` (Manfred, 2026-11-21: with a separator, for every n). */
data class Edge(val a: Int, val b: Int) : Comparable<Edge> {
    override fun compareTo(other: Edge) = compareValuesBy(this, other, { it.a }, { it.b })
    override fun toString() = "{$a,$b}"
}

/** A crossing of the edges [e] < [f]. */
data class Crossing(val e: Edge, val f: Edge) {
    override fun toString() = "$e×$f"
}

/** All crossing pairs (by rotsys-sat's `does_ab_cross_cd`), sorted. */
fun RotationSystem.crossings(): List<Crossing> {
    val result = ArrayList<Crossing>()
    for (a in 0 until n) for (b in a + 1 until n) for (c in a + 1 until n) for (d in c + 1 until n) {
        if (c == b || d == b) continue
        if (crosses(a, b, c, d)) result += Crossing(Edge(a, b), Edge(c, d))
    }
    return result.sortedWith(compareBy({ it.e }, { it.f }))
}

/** Crossing-maximal: every K4 has a crossing, i.e. binom(n, 4) crossings. */
fun crossingMaximal(n: Int, crossingCount: Int): Boolean = crossingCount.toLong() == binomial(n, 4)

fun binomial(n: Int, k: Int): Long {
    if (k < 0 || k > n) return 0
    var r = 1L
    for (i in 0 until k) r = r * (n - i) / (i + 1)
    return r
}

/**
 * pyrslib's `enumerate_crossing_pairs` (used by draw2.sage): for every 4-subset, the lex-min of
 * the sub-system decides (`four_indices_have_crossing`: the number of rows with an even number of
 * inversions is not 2) and its labelling names the crossing edges.
 */
fun RotationSystem.crossingPairsPyrslib(): List<Pair<Edge, Edge>> {
    val result = ArrayList<Pair<Edge, Edge>>()
    for (p in 0 until n) for (q in p + 1 until n) for (r in q + 1 until n) for (s in r + 1 until n) {
        val cr = listOf(p, q, r, s)
        val (lex, perm) = sub(cr).lexmin()
        val even = lex.rows.count { row -> inversions(row) % 2 == 0 }
        if (even == 2) continue
        var (a, b) = cr[perm[0]] to cr[perm[2]]
        var (c, d) = cr[perm[1]] to cr[perm[3]]
        if (a > b) a = b.also { b = a }
        if (c > d) c = d.also { d = c }
        var ab = Edge(a, b)
        var cd = Edge(c, d)
        if (ab > cd) ab = cd.also { cd = ab }
        result += ab to cd
    }
    return result
}

private fun inversions(row: List<Int>): Int {
    var k = 0
    for (i in row.indices) for (j in i + 1 until row.size) if (row[i] > row[j]) k++
    return k
}

/**
 * A flip of pyrslib's `enum_all_flips`: at [a], [c] comes right after [b], and at [b], [a] comes
 * right after [c]; the flip swaps b and c in the rotation of a and a and c in the rotation of b.
 * The inverse flip is `(b, a, c)`.
 */
data class Flip(val a: Int, val b: Int, val c: Int) {
    override fun toString() = "($a,$b,$c)"
}

/** All flips whose condition holds, in pyrslib's order (permutations of the vertices). */
fun RotationSystem.flipCandidates(): List<Flip> {
    val k = n - 1
    val result = ArrayList<Flip>()
    for (a in 0 until n) for (b in 0 until n) for (c in 0 until n) {
        if (a == b || b == c || a == c) continue
        val oa = rows[a]
        val ob = rows[b]
        val iab = oa.indexOf(b)
        val iac = oa.indexOf(c)
        val iba = ob.indexOf(a)
        val ibc = ob.indexOf(c)
        if (iac == (iab + 1) % k && iba == (ibc + 1) % k) result += Flip(a, b, c)
    }
    return result
}

/** The system after [flip] (its condition must hold). */
fun RotationSystem.flipped(flip: Flip): RotationSystem {
    val (a, b, c) = flip
    val o = rows.map { it.toMutableList() }
    val iab = o[a].indexOf(b)
    val iac = o[a].indexOf(c)
    val iba = o[b].indexOf(a)
    val ibc = o[b].indexOf(c)
    o[a][iab] = c
    o[a][iac] = b
    o[b][iba] = c
    o[b][ibc] = a
    return RotationSystem(o)
}

/** The flips whose result is drawable (Manfred, 2026-11-19: only those are offered). */
fun RotationSystem.validFlips(): List<Flip> = flipCandidates().filter { flipped(it).isDrawable }

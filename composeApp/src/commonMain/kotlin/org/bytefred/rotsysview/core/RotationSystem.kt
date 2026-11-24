package org.bytefred.rotsysview.core

/** A text that is not a (pre-)rotation system, with the reason. */
class InvalidRotationSystemException(message: String) : IllegalArgumentException(message)

/**
 * A pre-rotation system of K_n on the vertices `0 until n`: per vertex `a` the counterclockwise
 * cyclic order of the other vertices. Every row is stored starting at its smallest entry (pyrslib's
 * `_cannonical_permutation`), so equal systems are equal objects.
 */
class RotationSystem(rows: List<List<Int>>) {
    val rows: List<List<Int>> = rows.map(::canonicalRow)
    val n: Int get() = rows.size

    /** `pos[a][x]`: the index of `x` in row `a` (`-1` for `x = a`). */
    private val pos: Array<IntArray> = Array(n) { a -> IntArray(n) { -1 }.also { p -> rows[a].forEachIndexed { i, x -> p[x] = i } } }

    init {
        for ((a, row) in rows.withIndex()) {
            val expected = (0 until n).filter { it != a }
            if (row.sorted() != expected) throw InvalidRotationSystemException(
                "the rotation of vertex $a is ${row.joinToString(" ")}, not an order of the other ${n - 1} vertices",
            )
        }
    }

    override fun equals(other: Any?) = other is RotationSystem && rows == other.rows
    override fun hashCode() = rows.hashCode()
    override fun toString() = toJson()

    /** pyrslib's `to_string`: one row per line, space separated. */
    fun toText(): String = rows.joinToString("\n") { it.joinToString(" ") }

    /** pyrslib's `to_json_string`: `[[1,2,3],[0,2,3],...]`. */
    fun toJson(): String = rows.joinToString("],[", "[[", "]]") { it.joinToString(",") }

    /** Whether vertex [a] sees [b], [c], [d] in this counterclockwise order (rotsys-sat's `does_a_see_bcd`). */
    fun sees(a: Int, b: Int, c: Int, d: Int): Boolean {
        val k = n - 1
        val pb = pos[a][b]
        return (pos[a][c] - pb + k) % k < (pos[a][d] - pb + k) % k
    }

    /** The directed crossing of rotsys-sat (`does_ab_cross_cd`): edge ab crosses edge cd in this direction. */
    fun dcross(a: Int, b: Int, c: Int, d: Int): Boolean =
        sees(a, c, b, d) && sees(b, d, a, c) && sees(c, b, d, a) && sees(d, a, c, b)

    /** Whether the edges ab and cd cross (four distinct vertices). */
    fun crosses(a: Int, b: Int, c: Int, d: Int): Boolean = dcross(a, b, c, d) || dcross(a, b, d, c)

    /** The sub-rotation-system on [perm], vertex `perm[i]` becoming `i` (pyrslib's `sub_rotation_system_from_permutation`). */
    fun sub(perm: List<Int>): RotationSystem =
        RotationSystem(perm.map { a -> rows[a].filter { it in perm }.map { perm.indexOf(it) } })

    /** All rows reversed (the mirror image, pyrslib's `calculate_mirrored`). */
    fun mirrored(): RotationSystem = RotationSystem(rows.map { it.reversed() })

    /** The system relabelled by [perm]: old vertex `perm[i]` becomes `i`. */
    fun relabelled(perm: List<Int>): RotationSystem = sub(perm)

    /**
     * pyrslib's `calculate_lexmin`: the smallest [toText] over the labellings that start with an
     * ordered pair (a, b) and follow the rotation of a, for the system and its mirror image.
     * Returns the system and the permutation (`perm[i]` is the old vertex of the new vertex `i`).
     */
    fun lexmin(): Pair<RotationSystem, List<Int>> {
        val mirror = mirrored()
        var best = this
        var bestText = toText()
        var bestPerm = (0 until n).toList()
        for (a in 0 until n) for (b in 0 until n) {
            if (a == b) continue
            val line = rows[a]
            val k0 = line.indexOf(b)
            val rest = line.subList(k0 + 1, line.size) + line.subList(0, k0)
            for ((source, perm) in listOf(this to listOf(a, b) + rest, mirror to listOf(a, b) + rest.reversed())) {
                val rs2 = source.sub(perm)
                val text = rs2.toText()
                if (text < bestText) {
                    best = rs2
                    bestText = text
                    bestPerm = perm
                }
            }
        }
        return best to bestPerm
    }

    /** The natural labelling holds: vertex 0 has the rotation 1, 2, ..., n - 1. */
    val isNatural: Boolean get() = rows.firstOrNull() == (1 until n).toList()

    companion object {
        /** The perfect convex C_n of rotsys-sat (`perfect_convex`). */
        fun perfectConvex(n: Int) = RotationSystem(List(n) { k -> (0 until k).toList() + (k + 1 until n).toList() })

        /** The perfect twisted T_n of rotsys-sat (`perfect_twisted`). */
        fun perfectTwisted(n: Int) = RotationSystem(List(n) { k -> (0 until k).toList() + (k + 1 until n).reversed() })
    }
}

/** A cyclic order rotated to start at its smallest element. */
private fun canonicalRow(row: List<Int>): List<Int> {
    if (row.isEmpty()) return row
    val k0 = row.indexOf(row.min())
    return row.subList(k0, row.size) + row.subList(0, k0)
}

package org.bytefred.rotsysview.core

import kotlin.math.atan2
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private fun all(n: Int): List<RotationSystem> {
        val set = Examples.byName("n$n-all")!!
        return (0 until set.size).map(set::rotationSystem)
    }

    @Test
    fun examplesLoadAndAreDrawable() {
        assertEquals(listOf(2, 5, 102), (4..6).map { all(it).size })
        for (n in 4..6) for (rs in all(n)) assertNull(rs.obstruction(), "$rs")
        assertEquals(11556, Examples.byName("n7-all")!!.size)
    }

    @Test
    fun crossingsOfPyrslibAndRotsysSatAgree() {
        for (n in 4..6) for (rs in all(n)) {
            val a = rs.crossings().map { it.e to it.f }.toSet()
            val b = rs.crossingPairsPyrslib().toSet()
            assertEquals(a, b, "$rs")
        }
    }

    @Test
    fun obstructions() {
        val pi4 = RotationSystem(Pattern.PI4.rows)
        assertEquals(Pattern.PI4, pi4.obstruction()?.pattern)
        assertNotNull(RotationSystem(Pattern.PI51.rows).obstruction())
        assertNotNull(RotationSystem(Pattern.PI52.rows).obstruction())
    }

    @Test
    fun perfectConvexAndTwisted() {
        for (n in 5..7) {
            val c = RotationSystem.perfectConvex(n)
            val t = RotationSystem.perfectTwisted(n)
            assertTrue(c.isDrawable && t.isDrawable)
            assertTrue(crossingMaximal(n, c.crossings().size))
            assertTrue(crossingMaximal(n, t.crossings().size))
            assertTrue(c.isConvex)
            assertTrue(t.isGeneralizedTwisted)
            assertFalse(t.isConvex)
        }
    }

    @Test
    fun flipsAreInvolutive() {
        for (rs in all(6)) for (f in rs.validFlips()) {
            val next = rs.flipped(f)
            assertTrue(Flip(f.b, f.a, f.c) in next.flipCandidates(), "$rs $f")
            assertEquals(rs, next.flipped(Flip(f.b, f.a, f.c)))
        }
    }

    @Test
    fun lexminIsNaturalAndInvariant() {
        for (rs in all(5)) {
            val (lex, perm) = rs.lexmin()
            assertTrue(lex.isNatural, "$lex")
            assertEquals(lex, rs.relabelled(perm).let { if (it == lex) it else it.mirrored() })
        }
    }

    @Test
    fun formatsRoundTrip() {
        val rs = all(6)[17]
        assertEquals(rs, TextFormat.ROWS.read(rs.toText()))
        assertEquals(rs, TextFormat.JSON.read(rs.toJson()))
        val oneBased = rs.rows.joinToString("\n") { r -> r.joinToString(" ") { "${it + 1}" } }
        assertEquals(rs, TextFormat.ROWS.read(oneBased))
        val file = rotationSystemsFile("x", listOf(rs, all(6)[3]), FileFormat.JSON)
        assertEquals(listOf(rs, all(6)[3]), readRotationSystems(file).map { it.rs })
        val txt = rotationSystemsFile("x", listOf(rs, all(6)[3]), FileFormat.TXT)
        assertEquals(listOf(rs, all(6)[3]), readRotationSystems(txt).map { it.rs })
        assertEquals(2, readRotationSystems("4 0  1:432 2:431 3:412 4:123\n4 0  1:432 2:341 3:142 4:123").size)
    }

    @Test
    fun routingPlanarizesAllSmallDrawings() {
        for (n in 4..6) for (rs in all(n)) {
            val p = planarizeRouting(rs)
            assertNotNull(p, "$rs")
            // Euler: V - E + F = 2, and V = n + crossings
            assertEquals(2, p.vertexCount - p.edges.size / 2 + p.faces.size, "$rs")
            assertEquals(n + rs.crossings().size, p.vertexCount)
        }
    }

    /** The three methods find the same planarization (the same face sizes) where draw2 and SAT are fast (n <= 5). */
    @Test
    fun methodsAgree() {
        for (n in 4..5) for (rs in all(n)) {
            fun sizes(p: Planarization?) = p!!.faces.map { it.size }.sorted()
            val routing = sizes(planarizeRouting(rs))
            assertEquals(routing, sizes(planarizeDraw2(rs, Random(1))), "$rs")
            assertEquals(routing, sizes(planarizeSat(rs)), "$rs")
        }
    }

    @Test
    fun nonDrawableHasNoRouting() {
        assertNull(planarizeRouting(RotationSystem(Pattern.PI51.rows)))
    }

    @Test
    fun drawingShowsTheRotations() {
        for (style in DrawStyle.entries) for (rs in all(6).take(20) + all(5)) {
            val p = planarizeRouting(rs)!!
            val layout = RotLayout(p, defaultOuterFace(p, style))
            val d = layout.drawing(IteratedTutte(layout.problem, LayoutSettings(maxIterations = style.maxIterations)).run(), style)
            assertTrue(d.vertices.all { it.x.isFinite() && it.y.isFinite() })
            // around every vertex the curves leave in the order of its rotation (counterclockwise)
            for (a in 0 until rs.n) {
                val angle = rs.rows[a].associateWith { b ->
                    val c = d.curves.getValue(Edge(minOf(a, b), maxOf(a, b)))
                    val q = if (a < b) c.first().c1 else c.last().c2
                    atan2(q.y - d.vertices[a].y, q.x - d.vertices[a].x)
                }
                val ccw = rs.rows[a].sortedBy { angle.getValue(it) }
                val i = ccw.indexOf(rs.rows[a][0])
                assertEquals(rs.rows[a], ccw.subList(i, ccw.size) + ccw.subList(0, i), "$style $rs vertex $a")
            }
        }
    }
}

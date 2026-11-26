package org.bytefred.rotsysview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled examples load and are what their descriptions say. */
class ExamplesTest {
    private fun rs(name: String) = Examples.byName(name)!!.rotationSystem(0)

    @Test
    fun everyExampleLoadsWithItsN() {
        for (set in Examples.sets) {
            assertEquals(set.n, set.rotationSystem(0).n, set.name)
            assertTrue(set.size >= 1)
            assertEquals(set.name, readRotationSystems(GeneratedExamples.FILES.getValue(set.path).joinToString("")).first().name?.substringBefore(" #"))
        }
        // every bundled file is in the index, and the other way round
        assertEquals(GeneratedExamples.FILES.keys - Examples.INDEX, Examples.sets.map { it.path }.toSet())
        for (g in Examples.groups) Examples.inGroup(g).map { it.n }.let { assertEquals(it.sorted(), it, g.id) }
    }

    @Test
    fun supergroups() {
        val db = Examples.supergroup("database")
        val sat = Examples.supergroup("sat-framework")
        assertEquals(listOf(db, sat), Examples.supergroups)
        assertEquals(listOf("Database", "SAT framework examples"), Examples.supergroups.map { it.title })
        assertEquals(listOf(Examples.group("all")), Examples.groupsIn(db))
        assertEquals(
            listOf("convexity", "all-edges-crossed", "crossing-families", "avoiding", "empty-triangles", "plane-hc"),
            Examples.groupsIn(sat).map { it.id },
        )
        assertEquals(ExampleFamily.entries, Examples.familiesIn(sat))
        assertTrue(Examples.familiesIn(db).isEmpty())
        assertEquals((4..7).map { "n$it-all" }, Examples.inGroup(Examples.group("all")).map { it.name })
        assertEquals(listOf(2, 5, 102, 11556), Examples.inGroup(Examples.group("all")).map { it.size })
        // the sources are on the sets (Manfred), not on the groups and supergroups
        for (g in Examples.groups) assertTrue(g.links.isEmpty() && g.supergroup.links.isEmpty(), g.id)
        for (set in Examples.sets) assertTrue(set.links.isNotEmpty(), set.name)
    }

    @Test
    fun claimsOfTheSatExamples() {
        for (n in 11..21) {
            val r = rs("n$n-hconvex-all-edges-crossed")
            assertTrue(r.isDrawable && r.isHConvex, "n$n")
            val crossed = r.crossings().flatMap { listOf(it.e, it.f) }.toSet()
            assertEquals(n * (n - 1) / 2, crossed.size, "n$n: every edge crossed")
        }
        assertTrue(rs("n16-hconvex-no-4-crossing-family").isHConvex)
        assertTrue(rs("n21-hconvex-no-C6").isHConvex)
        assertTrue(rs("n20-hconvex-no-C6").isHConvex)
        assertTrue(rs("n12-gtwisted-no-T8").isGeneralizedTwisted)
    }

    @Test
    fun convexityExamples() {
        for (name in listOf("n5-not-convex-1", "n5-not-convex-2")) {
            assertNull(rs(name).obstruction(), name)
            assertFalse(rs(name).isConvex, name)
        }
        assertTrue(rs("n6-not-hconvex").isConvex)
        assertFalse(rs("n6-not-hconvex").isHConvex)
    }

    @Test
    fun families() {
        for (f in ExampleFamily.entries) for (n in f.minN..f.maxN) {
            val r = f.generate(n)
            assertEquals(n, r.n)
            assertTrue(r.isDrawable, "${f.id} n=$n")
            assertTrue(crossingMaximal(n, r.crossings().size), "${f.id} n=$n")
        }
        for (n in 4..9) {
            assertTrue(ExampleFamily.PERFECT_CONVEX.generate(n).isHConvex, "C_$n")
            assertTrue(ExampleFamily.PERFECT_TWISTED.generate(n).isGeneralizedTwisted, "T_$n")
        }
    }
}

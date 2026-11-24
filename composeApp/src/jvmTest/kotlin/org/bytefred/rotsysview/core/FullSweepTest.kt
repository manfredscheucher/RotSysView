package org.bytefred.rotsysview.core

import kotlin.test.Test
import kotlin.test.assertNotNull

/** All 11556 drawings of K_7 are planarized by routing (only with -PfullSweep). */
class FullSweepTest {
    @Test
    fun allOfK7() {
        val set = Examples.byName("n7-all")!!
        for (i in 0 until set.size) assertNotNull(planarizeRouting(set.rotationSystem(i)), "n7 #$i")
    }
}

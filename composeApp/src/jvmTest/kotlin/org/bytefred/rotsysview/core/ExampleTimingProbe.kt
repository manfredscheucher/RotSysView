package org.bytefred.rotsysview.core

import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test

/**
 * How long the examples take (only with -PtimingProbe; writes build/example-timing.md): per set
 * its first member, per family a few n. Planarization by routing, SAT and the draw2 search with a
 * budget of [BUDGET_MS] each, then the draw2 layout (100 Tutte iterations) on the routing result.
 * SAT cannot be cancelled and fills the heap when left running (n = 6: 15 s with the cadical binary):
 * only for n <= 5, and once it runs over the budget, larger n are skipped.
 */
class ExampleTimingProbe {
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }

    /** The time in ms (null: over the budget) and the result; "OOM" in [oom] if it ran out of memory. */
    private var oom = false
    private var stuck = false

    private fun <T> timed(budget: Long, cancel: (() -> Boolean) -> T): Pair<Long?, T?> {
        oom = false
        val start = System.nanoTime()
        val over = { (System.nanoTime() - start) / 1_000_000 > budget }
        val f = pool.submit<T> { cancel(over) }
        return try {
            val r = f.get(budget + 500, TimeUnit.MILLISECONDS)
            val ms = (System.nanoTime() - start) / 1_000_000
            if (ms > budget) null to null else ms to r
        } catch (_: TimeoutException) {
            // the next measurement starts only when this one has stopped (all but SAT check the budget)
            try { f.get(30, TimeUnit.SECONDS) } catch (_: Exception) { stuck = true }
            null to null
        } catch (e: ExecutionException) {
            if (e.cause !is OutOfMemoryError) throw e
            oom = true
            System.gc()
            null to null
        }
    }

    @Test
    fun timing() {
        val cases = buildList {
            for (s in Examples.sets.sortedBy { it.n }) add(s.name to s.rotationSystem(0))
            for (f in ExampleFamily.entries) for (n in listOf(6, 8, 10, 12, 14, 16, 18, 20)) if (n <= f.maxN) add("${f.id} n=$n" to f.generate(n))
        }
        val rows = ArrayList<String>()
        val out = File("build/example-timing.md")
        out.writeText(
            "Milliseconds, first member of each set, budget $BUDGET_MS ms.\n\n" +
                "| example | n | routing | SAT | draw2 search | layout |\n|---|---|---|---|---|---|\n",
        )
        fun over() = if (oom) "OOM" else ">$BUDGET_MS"
        var satSkip = false
        val t0 = System.nanoTime()
        for ((name, rs) in cases) {
            stuck = false
            if (rs.obstruction() != null) { rows += "| $name | ${rs.n} | not drawable | | | |"; out.appendText(rows.last() + "\n"); continue }
            val (routeMs, p) = timed(BUDGET_MS) { stop -> planarizeRouting(rs, stop) }
            val route = routeMs?.toString() ?: over()
            val sat = if (satSkip || rs.n >= 6) "skipped" else timed(BUDGET_MS) { _ -> planarizeSat(rs) }.first?.let { "$it" }
                ?: over().also { satSkip = true }
            // the draw2 search does not stop soon after its budget from n = 10 on: only up to n = 8
            val draw2 = if (rs.n > 8) "skipped" else timed(BUDGET_MS) { stop -> planarizeDraw2(rs, cancelled = stop) }.first?.let { "$it" } ?: over()
            val layout = p?.let {
                timed(BUDGET_MS) { stop ->
                    val l = RotLayout(it, defaultOuterFace(it, DrawStyle.DRAW2))
                    val t = IteratedTutte(l.problem, LayoutSettings())
                    while (!t.step() && !stop()) Unit
                }.first?.let { ms -> "$ms" } ?: over()
            } ?: "-"
            rows += "| $name | ${rs.n} | $route | $sat | $draw2 | $layout |"
            out.appendText(rows.last() + (if (stuck) " (a run did not stop)" else "") + "\n")
            println("[${(System.nanoTime() - t0) / 1_000_000_000} s] ${rows.last()}")
        }
    }

    /** The SAT formulas of a few examples as DIMACS (build/sat-<name>.cnf), to compare with the cadical binary. */
    @Test
    fun dumpDimacs() {
        for (name in listOf("n5-all", "n6-all", "n7-all", "n8-unlucky")) {
            val rs = Examples.byName(name)!!.rotationSystem(0)
            timed(1_000) { _ -> planarizeSat(rs) { File("build/sat-$name.cnf").writeText(it) } }
        }
    }

    companion object {
        const val BUDGET_MS = 5_000L
    }
}

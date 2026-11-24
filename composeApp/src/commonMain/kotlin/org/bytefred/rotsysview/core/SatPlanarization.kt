package org.bytefred.rotsysview.core

import org.bytefred.ksat.SatResult
import org.bytefred.ksat.cadical.CaDiCaL

/** How the planarization (the order of the crossings along every edge) is found. */
enum class PlanarizationMethod(val label: String) {
    /** Insert the edges one by one, routed through the faces ([planarizeRouting]); fast and exact. */
    ROUTING("routing (default)"),

    /** rotsys-sat `rotsys_draw.sage` (`find_planarization`): SAT, planarity by three Schnyder orders; slow from n = 6 on. */
    SAT("SAT (rotsys_draw)"),

    /** pyrslib `draw2.sage` (`rec_draw`): permutations and planarity tests; slow from n = 6 on. */
    DRAW2("search (pyrslib draw2)"),
}

/**
 * The planarization as `find_planarization` of rotsys-sat's `rotsys_draw.sage` finds it, solved
 * with CaDiCaL (cadical-kotlin; the script uses pysat's Cadical153). Per edge ab its vertices
 * a, the crossings, b; variables for the position of every vertex along every edge, the planar
 * edges between consecutive ones, and planarity by Schnyder's criterion: there are three linear
 * orders such that for every edge uv and every other vertex x, x is above u and v in one of them
 * (Felsner, Definition 1.1 of "Geometric Graphs and Arrangements" / the script's comment).
 *
 * Returns null if the formula is unsatisfiable (not drawable).
 */
fun planarizeSat(rs: RotationSystem, dimacs: ((String) -> Unit)? = null): Planarization? {
    val n = rs.n
    val crossings = rs.crossings()
    val pairs = crossings.map { it.e to it.f }
    val crossingId = HashMap<Pair<Edge, Edge>, Int>()
    for ((i, p) in pairs.withIndex()) crossingId[p] = n + i
    val vertexCount = n + pairs.size
    val edges = ArrayList<Edge>()
    for (a in 0 until n) for (b in a + 1 until n) edges += Edge(a, b)
    // vertices_along_edge
    val along = edges.associateWith { ArrayList<Int>().apply { add(it.a) } }
    for ((e, f) in pairs) {
        along.getValue(e).add(crossingId.getValue(e to f))
        along.getValue(f).add(crossingId.getValue(e to f))
    }
    for (e in edges) along.getValue(e).add(e.b)

    var next = 0
    fun newVar() = ++next
    val clauses = ArrayList<IntArray>()
    fun clause(vararg lits: Int) {
        clauses += lits
    }

    // order(e, i, v): v is the i-th vertex along e; order2(e, i, u, v): u at i - 1 and v at i
    val order = edges.associateWith { e -> val l = along.getValue(e).size; Array(l) { IntArray(l) { newVar() } } }
    val order2 = edges.associateWith { e -> val l = along.getValue(e).size; Array(l) { Array(l) { IntArray(l) { newVar() } } } }
    // edge(u, v), symmetric (one variable per unordered pair)
    val edgeVar = Array(vertexCount) { IntArray(vertexCount) }
    for (u in 0 until vertexCount) for (v in u + 1 until vertexCount) newVar().let { edgeVar[u][v] = it; edgeVar[v][u] = it }
    val smaller = Array(3) { Array(vertexCount) { IntArray(vertexCount) } }
    for (i in 0..2) for (u in 0 until vertexCount) for (v in 0 until vertexCount) if (u != v) smaller[i][u][v] = newVar()

    val potential = HashSet<Long>()
    fun key(u: Int, v: Int) = minOf(u, v).toLong() * vertexCount + maxOf(u, v)

    // permutation of the crossings along every edge, a and b fixed at both ends
    for (e in edges) {
        val vs = along.getValue(e)
        val l = vs.size
        val o = order.getValue(e)
        for (i in 0 until l) {
            clause(*IntArray(l) { o[i][it] })
            for (x in 0 until l) for (y in x + 1 until l) clause(-o[i][x], -o[i][y])
        }
        for (x in 0 until l) {
            clause(*IntArray(l) { o[it][x] })
            for (i in 0 until l) for (j in i + 1 until l) clause(-o[i][x], -o[j][x])
        }
        clause(o[0][0])
        clause(o[l - 1][l - 1])
        // edges from permutations
        val o2 = order2.getValue(e)
        for (x in 0 until l) for (y in 0 until l) {
            if (x == y) continue
            for (i in 1 until l) {
                clause(-o[i - 1][x], -o[i][y], o2[i][x][y])
                clause(o[i - 1][x], -o2[i][x][y])
                clause(o[i][y], -o2[i][x][y])
            }
        }
        for (x in 0 until l) for (y in x + 1 until l) {
            val u = vs[x]
            val v = vs[y]
            val ev = edgeVar[u][v]
            clause(-ev, *IntArray(l - 1) { o2[it + 1][x][y] }, *IntArray(l - 1) { o2[it + 1][y][x] })
            for (i in 1 until l) {
                clause(ev, -o2[i][x][y])
                clause(ev, -o2[i][y][x])
            }
            potential += key(u, v)
        }
    }
    // only valid edges
    for (u in 0 until vertexCount) for (v in u + 1 until vertexCount) if (key(u, v) !in potential) clause(-edgeVar[u][v])
    // planarity: three linear orders
    for (i in 0..2) {
        val s = smaller[i]
        for (u in 0 until vertexCount) for (v in 0 until vertexCount) {
            if (u == v) continue
            clause(s[u][v], s[v][u])
            clause(-s[u][v], -s[v][u])
            for (w in 0 until vertexCount) if (w != u && w != v) clause(-s[u][v], -s[v][w], s[u][w])
        }
    }
    // third_is_biggest(i, u, v, w) for u < v: u < w and v < w in order i
    val biggest = HashMap<Long, Int>()
    fun bigKey(i: Int, u: Int, v: Int, w: Int) = ((i.toLong() * vertexCount + u) * vertexCount + v) * vertexCount + w
    for (i in 0..2) for (u in 0 until vertexCount) for (v in u + 1 until vertexCount) for (w in 0 until vertexCount) {
        if (w == u || w == v) continue
        val b = newVar()
        biggest[bigKey(i, u, v, w)] = b
        clause(-b, smaller[i][u][w])
        clause(-b, smaller[i][v][w])
        clause(b, -smaller[i][u][w], -smaller[i][v][w])
    }
    for (u in 0 until vertexCount) for (v in u + 1 until vertexCount) {
        if (key(u, v) !in potential) continue
        for (x in 0 until vertexCount) if (x != u && x != v) {
            clause(-edgeVar[u][v], *IntArray(3) { i -> biggest.getValue(bigKey(i, u, v, x)) })
        }
    }

    dimacs?.invoke(buildString {
        append("p cnf $next ${clauses.size}\n")
        for (c in clauses) append(c.joinToString(" ", postfix = " 0\n"))
    })
    val solver = CaDiCaL(next)
    for (c in clauses) solver.addClause(c)
    if (solver.solve() != SatResult.SAT) return null
    val paths = edges.associateWith { e ->
        val vs = along.getValue(e)
        val o = order.getValue(e)
        IntArray(vs.size) { i -> vs[(vs.indices).first { solver.valueOf(o[i][it]) }] }
    }
    return Planarization(rs, pairs, paths)
}

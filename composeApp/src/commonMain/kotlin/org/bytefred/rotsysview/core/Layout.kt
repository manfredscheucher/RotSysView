package org.bytefred.rotsysview.core

import kotlin.math.atan2
import kotlin.math.sqrt

/** The two sage drawing programs (Manfred, 2026-11-19: both, pyrslib's draw2 as default). */
enum class DrawStyle(val label: String, val maxIterations: Int) {
    /**
     * pyrslib `sage_scripts/pyrslib.draw2.sage`: outer face with the fewest crossings
     * (`force_outer_vertices`), 100 iterations, one colour per edge.
     */
    DRAW2("pyrslib draw2", 100),

    /**
     * rotsys-sat `sat/visualization/rotsys_draw.sage`: outer face of vertices only, else the
     * largest; 20 iterations; edge ab in the colour of a, dashed in the colour of b; coloured vertices.
     */
    ROTSYS("rotsys-sat rotsys_draw", 20),
}

/**
 * The default outer face of [p] for [style] (an index into [Planarization.faces]).
 *
 * draw2: the first face with the fewest crossing vertices (sage's face order is not reproduced;
 * the first in [Planarization.faces]). rotsys_draw: the last face of maximum length, unless some
 * face has vertices only, then the first such face.
 */
fun defaultOuterFace(p: Planarization, style: DrawStyle): Int {
    val faces = p.faces
    return when (style) {
        DrawStyle.DRAW2 -> faces.indices.minBy { p.crossingsOn(faces[it]) }
        DrawStyle.ROTSYS -> {
            val maxLen = faces.maxOf { it.size }
            var best = -1
            for ((i, f) in faces.withIndex()) {
                if (f.size == maxLen) best = i
                if (f.all { it < p.n }) {
                    best = i
                    break
                }
            }
            best
        }
    }
}

/** A key of a face that does not depend on the face order: its sorted vertices. */
fun faceKey(face: List<Int>): String = face.sorted().joinToString(",")

/** The face of [p] in the corner at [a] between the edges to [b] and [c], or -1. */
fun cornerFace(p: Planarization, a: Int, b: Int, c: Int): Int {
    fun first(x: Int): Int {
        val path = p.paths.getValue(Edge(minOf(a, x), maxOf(a, x)))
        return if (path.first() == a) path[1] else path[path.size - 2]
    }
    val nb = first(b)
    val nc = first(c)
    for ((i, f) in p.faces.withIndex()) {
        for (j in f.indices) {
            if (f[j] != a) continue
            val prev = f[(j - 1 + f.size) % f.size]
            val next = f[(j + 1) % f.size]
            if ((prev == nb && next == nc) || (prev == nc && next == nb)) return i
        }
    }
    return -1
}

/**
 * Lays out [planarization] with face [outerFace] outside: [problem] for [IteratedTutte], [drawing]
 * turns the positions into curves.
 */
class RotLayout(val planarization: Planarization, val outerFace: Int) {
    private val p = planarization

    val problem: TutteProblem = TutteProblem(
        p.vertexCount, p.edges, p.faces.map { it.toIntArray() }, p.faces[outerFace].toIntArray(), areaFactor = 1.0,
    )

    /**
     * The drawing for the vertex [positions]. If the embedding shows the mirror image of the
     * rotation system (the face orientation of the embedding is arbitrary), x is negated, so the
     * rotations are counterclockwise as in the system.
     */
    fun drawing(positions: DoubleArray, style: DrawStyle): RotDrawing {
        val pos = positions.copyOf()
        if (mirrored(pos)) for (i in 0 until p.vertexCount) pos[2 * i] = -pos[2 * i]
        fun at(v: Int) = Point(pos[2 * v], pos[2 * v + 1])
        // graph_2_ipe: distances[v] holds the squared lengths of the curve edges at v
        val distances = HashMap<Int, MutableList<Double>>()
        for (path in p.paths.values) for (i in 1 until path.size) {
            val d = at(path[i - 1]) - at(path[i])
            val d2 = d.dot(d)
            distances.getOrPut(path[i - 1]) { ArrayList() }.add(d2)
            distances.getOrPut(path[i]) { ArrayList() }.add(d2)
        }
        val curves = LinkedHashMap<Edge, List<Cubic>>()
        // per edge, the curve parameter of the knot of every vertex on it
        val knots = HashMap<Edge, DoubleArray>()
        for ((e, path) in p.paths) {
            val lc = path.size
            val ctrl = ArrayList<Point>()
            val vertexIndex = IntArray(lc)
            for (i in 0 until lc) {
                val x1 = at(path[i])
                val dmin = distances.getValue(path[i]).min()
                if (i != 0) {
                    val x0 = at(path[i - 1])
                    ctrl += x1 + (x0 - x1) * (sqrt(dmin / (x0 - x1).dot(x0 - x1)) / 3)
                }
                vertexIndex[i] = ctrl.size
                ctrl += x1
                if (i != lc - 1) {
                    val x2 = at(path[i + 1])
                    ctrl += x1 + (x2 - x1) * (sqrt(dmin / (x2 - x1).dot(x2 - x1)) / 3)
                }
            }
            val pieces = openUniformBSpline(ctrl)
            curves[e] = pieces
            // with the first point doubled in front, control point j is P[j + 2]; the piece
            // starting at the knot of P[j + 2] is piece j + 1 (piece k runs from the knot of P[k + 1])
            knots[e] = DoubleArray(lc) { i -> (vertexIndex[i] + 1).toDouble().coerceAtMost(pieces.size.toDouble()) }.also {
                it[0] = 0.0
                it[lc - 1] = pieces.size.toDouble()
            }
        }
        // the boundary of every face along the curves
        val segment = HashMap<Long, List<Point>>()
        fun key(u: Int, v: Int) = u.toLong() * p.vertexCount + v
        for ((e, path) in p.paths) {
            val curve = curves.getValue(e)
            val k = knots.getValue(e)
            for (i in 1 until path.size) {
                val pts = flatten(subCurve(curve, k[i - 1], k[i])) + at(path[i])
                segment[key(path[i - 1], path[i])] = listOf(at(path[i - 1])) + pts.drop(1).dropLast(1) + at(path[i])
                segment[key(path[i], path[i - 1])] = segment.getValue(key(path[i - 1], path[i])).reversed()
            }
        }
        val polygons = p.faces.map { f ->
            buildList {
                for (j in f.indices) addAll(segment.getValue(key(f[j], f[(j + 1) % f.size])).dropLast(1))
            }
        }
        return RotDrawing(p, outerFace, style, List(p.n) { at(it) }, curves, polygons)
    }

    /** Whether the counterclockwise order of the edges around vertex 0 is the reverse of its rotation. */
    private fun mirrored(pos: DoubleArray): Boolean {
        if (p.n < 4) return false
        val a = 0
        val angle = (1 until p.n).associateWith { x ->
            val path = p.paths.getValue(Edge(a, x))
            val v = path[1]
            atan2(pos[2 * v + 1] - pos[2 * a + 1], pos[2 * v] - pos[2 * a])
        }
        val ccw = (1 until p.n).sortedBy { angle.getValue(it) }
        val rot = p.rs.rows[a]
        val start = ccw.indexOf(rot[0])
        val rotated = ccw.subList(start, ccw.size) + ccw.subList(0, start)
        return rotated != rot
    }

    private companion object {
        /** The part of [curve] from parameter [from] to [to] (piece index + t, `from <= to`). */
        fun subCurve(curve: List<Cubic>, from: Double, to: Double): List<Cubic> {
            val result = ArrayList<Cubic>()
            var i = from.toInt()
            while (i < to && i < curve.size) {
                val lo = maxOf(from, i.toDouble()) - i
                val hi = minOf(to, i + 1.0) - i
                if (hi > lo) result += curve[i].part(lo, hi)
                i++
            }
            return result
        }
    }
}

/**
 * A finished drawing: the vertex positions, the curve of every edge and the boundary of every
 * face of the planarization as a polygon (along the curves).
 */
class RotDrawing(
    val planarization: Planarization,
    val outerFace: Int,
    val style: DrawStyle,
    val vertices: List<Point>,
    val curves: Map<Edge, List<Cubic>>,
    val polygons: List<List<Point>>,
) {
    val rs: RotationSystem get() = planarization.rs

    /** Smallest and largest coordinates: (minX, minY, maxX, maxY). */
    val bounds: DoubleArray by lazy {
        val all = curves.values.flatten().flatMap { listOf(it.p0, it.c1, it.c2, it.p3) } + vertices
        if (all.isEmpty()) doubleArrayOf(-1.0, -1.0, 1.0, 1.0)
        else doubleArrayOf(all.minOf { it.x }, all.minOf { it.y }, all.maxOf { it.x }, all.maxOf { it.y })
    }

    /** The bounded face containing [p] (the smallest one), else the outer face. */
    fun faceAt(p: Point): Int {
        var best: Int? = null
        var bestArea = Double.MAX_VALUE
        for ((f, poly) in polygons.withIndex()) {
            if (f == outerFace || poly.size < 3 || !polygonContains(poly, p)) continue
            val a = kotlin.math.abs(polygonArea(poly))
            if (a < bestArea) {
                best = f
                bestArea = a
            }
        }
        return best ?: outerFace
    }

    /** The edge whose curve passes within [radius] of [p] (the nearest), or null. */
    fun edgeAt(p: Point, radius: Double): Edge? {
        var best: Edge? = null
        var bestDist = radius
        for ((e, curve) in curves) {
            val pts = flatten(curve) + (curve.lastOrNull()?.p3 ?: continue)
            for (i in 1 until pts.size) {
                val d = distanceToSegment(p, pts[i - 1], pts[i])
                if (d <= bestDist) {
                    best = e
                    bestDist = d
                }
            }
        }
        return best
    }

    /** The vertex within [radius] of [p], or null. */
    fun vertexAt(p: Point, radius: Double): Int? =
        vertices.indices.filter { (vertices[it] - p).length <= radius }.minByOrNull { (vertices[it] - p).length }
}

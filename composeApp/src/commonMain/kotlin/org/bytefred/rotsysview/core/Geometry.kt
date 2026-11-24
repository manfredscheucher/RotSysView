package org.bytefred.rotsysview.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** A point of the drawing (y up). */
data class Point(val x: Double, val y: Double) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)
    operator fun times(s: Double) = Point(x * s, y * s)
    fun dot(o: Point) = x * o.x + y * o.y
    val length: Double get() = hypot(x, y)

    /** This vector scaled to length 1, or the zero vector. */
    fun normalized(): Point = length.let { if (it > 0.0 && it.isFinite()) Point(x / it, y / it) else ZERO }

    companion object {
        val ZERO = Point(0.0, 0.0)
    }
}

/** A cubic Bezier piece. */
data class Cubic(val p0: Point, val c1: Point, val c2: Point, val p3: Point) {
    fun at(t: Double): Point {
        val s = 1 - t
        return p0 * (s * s * s) + c1 * (3 * s * s * t) + c2 * (3 * s * t * t) + p3 * (t * t * t)
    }

    fun reversed() = Cubic(p3, c2, c1, p0)

    /** The part from parameter [a] to [b] (`0 <= a < b <= 1`), exact (de Casteljau). */
    fun part(a: Double, b: Double): Cubic {
        if (a <= 0.0 && b >= 1.0) return this
        val right = if (a <= 0.0) this else split(a).second
        return if (b >= 1.0) right else right.split((b - a) / (1 - a)).first
    }

    /** The two halves at parameter [t] (de Casteljau). */
    fun split(t: Double): Pair<Cubic, Cubic> {
        fun lerp(p: Point, q: Point) = p + (q - p) * t
        val a = lerp(p0, c1)
        val b = lerp(c1, c2)
        val c = lerp(c2, p3)
        val d = lerp(a, b)
        val e = lerp(b, c)
        val m = lerp(d, e)
        return Cubic(p0, a, d, m) to Cubic(m, e, c, p3)
    }
}

/**
 * The closed uniform cubic B-spline with control points [ctrl] (Ipe's closed spline, path operator
 * `u`) as cubic Bezier pieces, by the exact conversion: piece `i` is the span of
 * `ctrl[i], .., ctrl[i + 3]` (indices cyclic) and starts at `(ctrl[i] + 4 ctrl[i + 1] + ctrl[i + 2]) / 6`.
 */
fun closedUniformBSpline(ctrl: List<Point>): List<Cubic> {
    val m = ctrl.size
    return List(m) { i ->
        val p0 = ctrl[i]
        val p1 = ctrl[(i + 1) % m]
        val p2 = ctrl[(i + 2) % m]
        val p3 = ctrl[(i + 3) % m]
        Cubic((p0 + p1 * 4.0 + p2) * (1.0 / 6), (p1 * 2.0 + p2) * (1.0 / 3), (p1 + p2 * 2.0) * (1.0 / 3), (p1 + p2 * 4.0 + p3) * (1.0 / 6))
    }
}

/**
 * The open uniform cubic B-spline with control points [ctrl], the first and the last tripled, so
 * that it starts at the first and ends at the last point: how Ipe reads a path `p0 m p1 ... pk c`
 * with more than three points after `m` (the old spline form `graph_2_ipe` writes for an edge).
 */
fun openUniformBSpline(ctrl: List<Point>): List<Cubic> {
    if (ctrl.size < 2) return emptyList()
    val p = listOf(ctrl.first(), ctrl.first()) + ctrl + listOf(ctrl.last(), ctrl.last())
    return List(p.size - 3) { i -> bSplinePiece(p[i], p[i + 1], p[i + 2], p[i + 3]) }
}

/** The span of a uniform cubic B-spline with control points [p0] .. [p3] as a Bezier piece. */
private fun bSplinePiece(p0: Point, p1: Point, p2: Point, p3: Point) =
    Cubic((p0 + p1 * 4.0 + p2) * (1.0 / 6), (p1 * 2.0 + p2) * (1.0 / 3), (p1 + p2 * 2.0) * (1.0 / 3), (p1 + p2 * 4.0 + p3) * (1.0 / 6))

/** Points per cubic piece when flattening. */
private const val SAMPLES = 12

/** The pieces as a polyline (without the very last point). */
fun flatten(pieces: List<Cubic>): List<Point> = buildList {
    for (c in pieces) for (i in 0 until SAMPLES) add(c.at(i.toDouble() / SAMPLES))
}

/** Even-odd point in polygon test. */
fun polygonContains(poly: List<Point>, p: Point): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Signed area of a polygon (positive if counterclockwise). */
fun polygonArea(poly: List<Point>): Double {
    var s = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        s += a.x * b.y - b.x * a.y
    }
    return s / 2
}

/** Distance from [p] to the segment [a]-[b]. */
fun distanceToSegment(p: Point, a: Point, b: Point): Double {
    val ab = b - a
    val len2 = ab.dot(ab)
    val t = if (len2 > 0.0) ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0) else 0.0
    return (p - (a + ab * t)).length
}

package org.bytefred.rotsysview.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.bytefred.rotsysview.core.Cubic
import org.bytefred.rotsysview.core.Point
import org.bytefred.rotsysview.core.RotDrawing
import kotlin.math.abs
import kotlin.math.round

/**
 * Maps the drawing's [bounds] (minX, minY, maxX, maxY, y up) into a [size] x [size] box with
 * [margin] on all sides, keeping the aspect ratio, y down (PoCoView's ExportFrame).
 */
private class ExportFrame(bounds: DoubleArray, size: Double, margin: Double) {
    private val minX = bounds[0]
    private val maxY = bounds[3]
    private val scale = (size - 2 * margin) / maxOf(1e-9, bounds[2] - bounds[0], bounds[3] - bounds[1])
    private val left = (size - (bounds[2] - bounds[0]) * scale) / 2
    private val top = (size - (bounds[3] - bounds[1]) * scale) / 2

    fun x(p: Point) = left + (p.x - minX) * scale
    fun y(p: Point) = top + (maxY - p.y) * scale
}

/** [decimals] decimals (rounded), no exponent notation. */
private fun fmt(v: Double, decimals: Int = 1): String {
    var f = 1L
    repeat(decimals) { f *= 10 }
    val r = round(v * f).toLong()
    val sign = if (r < 0) "-" else ""
    val a = abs(r)
    return if (decimals == 0) "$sign$a" else "$sign${a / f}.${(a % f).toString().padStart(decimals, '0')}"
}

fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** SVG of [d] as the canvas draws it (curves, dashes, vertices and their numbers), in a 1000 x 1000 box. */
fun drawingSvg(d: RotDrawing, scheme: ColorScheme, lineWidth: Float, labels: Boolean): String {
    val fr = ExportFrame(d.bounds, SVG_SIZE, SVG_MARGIN)
    fun pt(p: Point) = "${fmt(fr.x(p))},${fmt(fr.y(p))}"
    fun path(pieces: List<Cubic>) = buildString {
        append("M${pt(pieces.first().p0)}")
        for (c in pieces) append(" C${pt(c.c1)} ${pt(c.c2)} ${pt(c.p3)}")
    }
    val size = fmt(SVG_SIZE, 0)
    return buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" width=\"$size\" height=\"$size\">\n")
        append("<rect width=\"$size\" height=\"$size\" fill=\"white\"/>\n")
        for (s in curveStrokes(d, scheme)) {
            if (s.pieces.isEmpty()) continue
            val dash = if (s.dashed) " stroke-dasharray=\"10 8\"" else ""
            append("<path d=\"${path(s.pieces)}\" fill=\"none\" stroke=\"${s.color.toHex()}\" stroke-width=\"${fmt(lineWidth.toDouble())}\"$dash/>\n")
        }
        for (m in vertexMarks(d, scheme)) {
            append("<circle cx=\"${fmt(fr.x(m.at))}\" cy=\"${fmt(fr.y(m.at))}\" r=\"7\" fill=\"${m.fill.toHex()}\" stroke=\"black\"/>\n")
            if (labels) append(
                "<text x=\"${fmt(fr.x(m.at) + SVG_LABEL_OFFSET)}\" y=\"${fmt(fr.y(m.at) - SVG_LABEL_OFFSET)}\" fill=\"${m.labelColor.toHex()}\" font-size=\"20\" " +
                    "font-weight=\"bold\" font-family=\"sans-serif\">${m.vertex}</text>\n",
            )
        }
        append("</svg>\n")
    }
}

private const val SVG_SIZE = 1000.0

/** Room around the drawing for the labels. */
private const val SVG_MARGIN = 50.0

/** Distance between a vertex and its number. */
private const val SVG_LABEL_OFFSET = 9.0

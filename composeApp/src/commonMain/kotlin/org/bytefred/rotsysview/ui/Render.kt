package org.bytefred.rotsysview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.rotsysview.core.Cubic
import org.bytefred.rotsysview.core.DrawStyle
import org.bytefred.rotsysview.core.Edge
import org.bytefred.rotsysview.core.Point
import org.bytefred.rotsysview.core.RotDrawing

/** One curve to draw: an edge of K_n in a colour, maybe dashed (canvas and SVG draw the same list). */
class CurveStroke(val edge: Edge, val pieces: List<Cubic>, val color: Color, val dashed: Boolean)

/** A vertex mark: position, fill and label (as `graph_2_ipe` writes them). */
class VertexMark(val vertex: Int, val at: Point, val fill: Color, val labelColor: Color)

/**
 * The curves of [d] in [scheme]: draw2, one curve per edge in the colour of its edge (edges in
 * `combinations` order); rotsys_draw, edge ab in the colour of a and dashed on top in the colour of b.
 */
fun curveStrokes(d: RotDrawing, scheme: ColorScheme): List<CurveStroke> {
    val n = d.rs.n
    val edges = d.curves.keys.sorted()
    return when (d.style) {
        DrawStyle.DRAW2 -> edges.mapIndexed { i, e -> CurveStroke(e, d.curves.getValue(e), scheme.color(i, edges.size, d.style), false) }
        DrawStyle.ROTSYS -> edges.flatMap { e ->
            val c = d.curves.getValue(e)
            listOf(CurveStroke(e, c, scheme.color(e.a, n, d.style), false), CurveStroke(e, c, scheme.color(e.b, n, d.style), true))
        }
    }
}

/** The vertices: black disks (draw2) or disks in the vertex colours (rotsys_draw), labelled with their number. */
fun vertexMarks(d: RotDrawing, scheme: ColorScheme): List<VertexMark> = d.vertices.mapIndexed { v, at ->
    when (d.style) {
        DrawStyle.DRAW2 -> VertexMark(v, at, Color.Black, Color.Black)
        DrawStyle.ROTSYS -> VertexMark(v, at, scheme.color(v, d.rs.n, d.style), Color.Black)
    }
}

package org.bytefred.rotsysview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.rotsysview.core.ColorPalette
import org.bytefred.rotsysview.core.DrawStyle

/**
 * The colours of the drawing (Settings widget). draw2 colours the edges (sage's `color_by_label`:
 * a rainbow over the edges), rotsys_draw the vertices (`IPE_COLORS`; an edge ab is drawn in the
 * colour of a, dashed in the colour of b). [AUTO] takes what the chosen style does.
 */
enum class ColorScheme(val label: String, private val palette: ColorPalette?, private val list: List<Color>?) {
    AUTO("as the style", null, null),
    RAINBOW("rainbow", ColorPalette.RAINBOW, null),
    RAINBOW2("rainbow2", ColorPalette.RAINBOW2, null),
    STANDARD("Standard", null, STANDARD_COLORS),
    IPE("Ipe (rotsys_draw)", null, IPE_COLORS),
    ;

    /** The colour of item [i] of [count] (an edge in draw2, a vertex in rotsys_draw) for [style]. */
    fun color(i: Int, count: Int, style: DrawStyle): Color {
        val chosen = if (this == AUTO) (if (style == DrawStyle.DRAW2) RAINBOW else IPE) else this
        // a fixed list with fewer colours than items would repeat colours: rainbow instead (Manfred, 2026-11-21)
        val scheme = if ((chosen.list?.size ?: Int.MAX_VALUE) < count) RAINBOW else chosen
        scheme.palette?.let { return Color(it.color(count, i).toInt()) }
        return scheme.list!![i]
    }

    companion object {
        val DEFAULT = AUTO
    }
}

/** The Standard colours of ArrView (item i gets colour i; more items than colours: rainbow). */
private val STANDARD_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF7CB342), Color(0xFF3949AB),
    Color(0xFFFFB300), Color(0xFF546E7A),
)

/** rotsys_draw.sage's `IPE_COLORS`, with the RGB values of Ipe's standard style sheet. */
private val IPE_COLORS = listOf(
    Color(0xFFFF0000), // red
    Color(0xFF0000FF), // blue
    Color(0xFF00FF00), // green
    Color(0xFFFFA500), // orange
    Color(0xFFFFC0CB), // pink
    Color(0xFFADD8E6), // lightblue
    Color(0xFFA020F0), // purple
    Color(0xFFD3D3D3), // lightgray
    Color(0xFF000000), // black
    Color(0xFFA9A9A9), // darkgray
    Color(0xFF000080), // navy
    Color(0xFF8B0000), // darkred
    Color(0xFF006400), // darkgreen
    Color(0xFFA52A2A), // brown
    Color(0xFFFFD700), // gold
    Color(0xFF90EE90), // lightgreen
    Color(0xFF40E0D0), // turquoise
)

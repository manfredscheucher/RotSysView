package org.bytefred.rotsysview.ui

import org.bytefred.rotsysview.core.DrawStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/** No colour twice, also with more items than a fixed list has colours. */
class ColorSchemeTest {
    @Test
    fun noRepeatedColours() {
        for (scheme in ColorScheme.entries) for (style in DrawStyle.entries) for (count in listOf(6, 12, 17, 18, 23, 253)) {
            val colors = (0 until count).map { scheme.color(it, count, style) }
            assertEquals(count, colors.toSet().size, "$scheme $style $count")
        }
    }
}

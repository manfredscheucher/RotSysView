package org.bytefred.rotsysview.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * The rainbow colour schemes of RotSysView, computed in OKLCH (Manfred, 2026-11-18; rainbow3-2 and
 * rainbow3-k removed 2026-11-19). The structure comes from blurred-memory's `ColorPalettes.kt` (hue
 * sweep, falling lightness), but the colours are OKLCH with chroma [CHROMA], hue `index / count * 360 + 25` degrees and
 * lightness in [LMIN]..[LMAX], so that no yellow is too light on white. RotSysView deviates from
 * blurred-memory on purpose here. Reference: `scripts/probe/colour_schemes_preview.py`
 * (`oklch`, `rainbow_ok`, `rainbow2_ok`).
 *
 * Every scheme takes count (here n) and index (here the vertex or edge), and gives 0xAARRGGBB.
 */
enum class ColorPalette {
    /** rainbow: hue by index, fixed lightness [L_RAINBOW]. */
    RAINBOW,

    /** rainbow2: hue by index, lightness falling from [LMAX] to [LMIN] across the range. */
    RAINBOW2,
    ;

    /** The OKLCH lightness for [index] of [count]. */
    fun lightness(count: Int, index: Int): Double {
        val n = max(count, 1)
        val i = index.coerceIn(0, n - 1)
        return when (this) {
            RAINBOW -> L_RAINBOW
            RAINBOW2 -> LMAX - (LMAX - LMIN) * (if (n <= 1) 0.0 else i.toDouble() / (n - 1))
        }
    }

    /** The colour for [index] of [count], as 0xAARRGGBB (full alpha). */
    fun color(count: Int, index: Int): Long {
        val n = max(count, 1)
        val i = index.coerceIn(0, n - 1)
        return oklchToArgb(lightness(n, i), CHROMA, hueFor(n, i) + HUE_SHIFT)
    }

    companion object {
        const val CHROMA = 0.16
        const val HUE_SHIFT = 25.0
        const val LMIN = 0.42
        const val LMAX = 0.72
        const val L_RAINBOW = 0.60
    }
}

/** Full-wheel hue by index: 0 for index 0, sweeping 360 degrees as index -> count. */
private fun hueFor(count: Int, index: Int): Double = if (count <= 1) 0.0 else index.toDouble() / count * 360.0

/** OKLCH (hue in degrees) -> sRGB, clamped to [0, 1] after the conversion, as 0xAARRGGBB. */
internal fun oklchToArgb(lightness: Double, chroma: Double, hue: Double): Long {
    val rad = hue * PI / 180
    val a = chroma * cos(rad)
    val b = chroma * sin(rad)
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    // round half to even, as Python's round() in the reference
    fun channel(x: Double): Long = round(gamma(x).coerceIn(0.0, 1.0) * 255).toLong()
    return (0xFFL shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(bb)
}

/** Linear sRGB -> sRGB (the transfer function). */
private fun gamma(x: Double): Double = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055

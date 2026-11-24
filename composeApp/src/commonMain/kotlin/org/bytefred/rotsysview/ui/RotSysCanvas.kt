package org.bytefred.rotsysview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.bytefred.rotsysview.core.Cubic
import org.bytefred.rotsysview.core.Edge
import org.bytefred.rotsysview.core.Flip
import org.bytefred.rotsysview.core.Point
import org.bytefred.rotsysview.core.RotDrawing
import org.bytefred.rotsysview.core.RotationSystem
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/** World (drawing coordinates, y up) <-> screen (pixels, y down): screen = origin + (x, -y) * scale. */
class ViewTransform {
    var scale by mutableDoubleStateOf(1.0)
    var ox by mutableDoubleStateOf(0.0)
    var oy by mutableDoubleStateOf(0.0)
    var size by mutableStateOf(IntSize.Zero)

    /** The [AppState.fitRequest] this view was last fitted for: a re-created canvas keeps the zoom. */
    var fittedRequest = -1

    fun toScreen(p: Point) = Offset((ox + p.x * scale).toFloat(), (oy - p.y * scale).toFloat())
    fun toWorld(s: Offset) = Point((s.x - ox) / scale, (oy - s.y) / scale)

    fun zoomAt(anchor: Offset, factor: Double) {
        val f = factor.coerceIn(0.02, 50.0)
        val newScale = (scale * f).coerceIn(1e-3, 1e7)
        val k = newScale / scale
        ox = anchor.x - (anchor.x - ox) * k
        oy = anchor.y - (anchor.y - oy) * k
        scale = newScale
    }

    /** New canvas size (e.g. a widget column opened or closed): the view keeps its centre. */
    fun resize(newSize: IntSize) {
        // keep the last real size while the canvas is squeezed to nothing
        if (newSize.width == 0 || newSize.height == 0) return
        if (size != IntSize.Zero) {
            ox += (newSize.width - size.width) / 2.0
            oy += (newSize.height - size.height) / 2.0
        }
        size = newSize
    }

    fun zoomCenter(factor: Double) = zoomAt(Offset(size.width / 2f, size.height / 2f), factor)

    /** Shows the box (minX, minY, maxX, maxY) in the middle, with room for the labels around it. */
    fun fit(bounds: DoubleArray) {
        if (size.width == 0 || size.height == 0) return
        val (minX, minY, maxX, maxY) = bounds.toList()
        val w = maxOf(1e-9, maxX - minX)
        val h = maxOf(1e-9, maxY - minY)
        scale = min((size.width - 2 * FIT_MARGIN) / w, (size.height - 2 * FIT_MARGIN) / h).coerceAtLeast(1e-3)
        ox = size.width / 2.0 - (minX + maxX) / 2 * scale
        oy = size.height / 2.0 + (minY + maxY) / 2 * scale
    }

    private companion object {
        /** Pixels left free around the drawing (labels). */
        const val FIT_MARGIN = 36.0
    }
}


private val HOVER_FILL = Color(0x332F5D62)

/** Faces where a click flips: a light fill. */
internal val FLIP_FILL = Color(0xFFFFF0C8)

/** Pixels a press may move and still be a click. */
private const val TOUCH_SLOP = 4f

/** Pixels around a curve that hit its edge (hover). */
private const val EDGE_RADIUS = 6.0

/**
 * The drawing of the rotation system: the faces that a click flips filled, the curves and the
 * vertices. Click such a face to flip (a menu if there are several flips); right-click (or long
 * press) a face for its menu (also "Make outer face"); drag to pan, wheel to zoom.
 */
@Composable
fun RotSysCanvas(state: AppState, view: ViewTransform, modifier: Modifier = Modifier, onInteract: () -> Unit = {}) {
    val st by rememberUpdatedState(state)
    val textMeasurer = rememberTextMeasurer()
    val drawing = state.drawing

    val ready = view.size.width > 0 && view.size.height > 0 && state.currentDrawing != null
    LaunchedEffect(state.fitRequest, ready) {
        val d = st.currentDrawing
        if (ready && d != null && view.fittedRequest != st.fitRequest) {
            view.fit(d.bounds)
            view.fittedRequest = st.fitRequest
        }
    }

    val gestures = LocalMenuGestures.current
    // the face whose context menu is open, for which rotation system, and where it opens
    var faceMenu by remember { mutableStateOf<Triple<RotationSystem, Int, Offset>?>(null) }
    // the face with several flips whose menu is open
    var flipMenu by remember { mutableStateOf<Triple<RotationSystem, Int, Offset>?>(null) }
    var hoverEdge by remember { mutableStateOf<Edge?>(null) }

    fun hit(pos: Offset): Int? = st.currentDrawing?.faceAt(view.toWorld(pos))
    fun edgeHit(pos: Offset): Edge? = st.currentDrawing?.edgeAt(view.toWorld(pos), EDGE_RADIUS / view.scale)

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("canvas")
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
                .onSizeChanged { view.resize(it) }
                .pointerInput(gestures) {
                    awaitPointerEventScope {
                        var down: Offset? = null
                        var last = Offset.Zero
                        var moved = false
                        var pinchDist = 0f
                        var pinchCenter = Offset.Zero
                        // a one-finger press held still until then is a long press (event time)
                        var longPressAt: Long? = null
                        var lastTime = 0L
                        while (true) {
                            val deadline = longPressAt?.takeIf { down != null && !moved }
                            val ev = if (deadline == null) awaitPointerEvent()
                            else withTimeoutOrNull((deadline - lastTime).coerceAtLeast(0L)) { awaitPointerEvent() }
                            if (ev == null) {
                                // long press: the face menu like a right-click, the release then does nothing
                                longPressAt = null
                                val at = down
                                val f = if (at != null) hit(at) else null
                                if (at != null && f != null) { faceMenu = Triple(st.rs, f, at); down = null }
                                continue
                            }
                            lastTime = ev.changes.first().uptimeMillis
                            val pressed = ev.changes.filter { it.pressed }
                            when (ev.type) {
                                PointerEventType.Scroll -> {
                                    val c = ev.changes.first()
                                    // mouse wheels report about 1 per notch; some browsers report pixels
                                    val dy = c.scrollDelta.y.toDouble()
                                    val notches = (if (abs(dy) >= 10) dy / 100 else dy).coerceIn(-5.0, 5.0)
                                    view.zoomAt(c.position, 1.15.pow(-notches))
                                    c.consume()
                                }
                                PointerEventType.Press -> {
                                    if (st.consumeMenuPress()) {
                                        // the press only closes the open menu (its popup is not focusable)
                                        st.openMenu = null
                                        down = null
                                    } else {
                                        onInteract()
                                        if (gestures.rightClick && (ev.buttons.isSecondaryPressed || ctrlClickIsRightClick && ev.keyboardModifiers.isCtrlPressed)) {
                                            down = null
                                            if (pressed.size == 1) hit(pressed[0].position)?.let { faceMenu = Triple(st.rs, it, pressed[0].position) }
                                        } else if (pressed.size == 1) {
                                            down = pressed[0].position
                                            last = pressed[0].position
                                            moved = false
                                            longPressAt = if (gestures.longPress) lastTime + viewConfiguration.longPressTimeoutMillis else null
                                        } else if (pressed.size == 2) {
                                            // second finger: pinch zoom + two-finger pan
                                            moved = true
                                            pinchDist = (pressed[0].position - pressed[1].position).getDistance()
                                            pinchCenter = (pressed[0].position + pressed[1].position) / 2f
                                        }
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                PointerEventType.Move -> {
                                    if (pressed.size >= 2) {
                                        val d = (pressed[0].position - pressed[1].position).getDistance()
                                        val center = (pressed[0].position + pressed[1].position) / 2f
                                        view.ox += (center.x - pinchCenter.x).toDouble()
                                        view.oy += (center.y - pinchCenter.y).toDouble()
                                        if (pinchDist > 0f && d > 0f) view.zoomAt(center, (d / pinchDist).toDouble())
                                        pinchDist = d
                                        pinchCenter = center
                                    } else if (pressed.size == 1 && down != null) {
                                        val pos = pressed[0].position
                                        if (!moved && (pos - down).getDistance() > TOUCH_SLOP) moved = true
                                        if (moved) {
                                            view.ox += (pos.x - last.x).toDouble()
                                            view.oy += (pos.y - last.y).toDouble()
                                        }
                                        last = pos
                                    } else if (pressed.isEmpty()) {
                                        val pos = ev.changes.firstOrNull()?.position
                                        st.hoverFace = pos?.let { hit(it) }
                                        hoverEdge = pos?.let { edgeHit(it) }
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                PointerEventType.Release -> {
                                    if (pressed.isEmpty()) {
                                        val start = down
                                        if (start != null && !moved) hit(start)?.let { f ->
                                            if (st.clickFace(f)) flipMenu = Triple(st.rs, f, start)
                                        }
                                        down = null
                                        pinchDist = 0f
                                    } else {
                                        // back from pinch to one finger: continue panning from here
                                        last = pressed[0].position
                                        pinchDist = 0f
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                PointerEventType.Exit -> { st.hoverFace = null; hoverEdge = null }
                            }
                        }
                    }
                },
        ) {
            val d = drawing?.takeIf { it.rs == state.rs && it.style == state.style }
            if (d != null) {
                drawRotSys(d, view, state, hoverEdge)
                if (state.showLabels) for (m in vertexMarks(d, state.colorScheme)) {
                    val s = view.toScreen(m.at) + Offset(LABEL_OFFSET, -LABEL_OFFSET)
                    val layout = textMeasurer.measure("${m.vertex}", TextStyle(color = m.labelColor, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    val topLeft = s - Offset(0f, layout.size.height.toFloat())
                    if (topLeft.x >= 0f && topLeft.y >= 0f && topLeft.x + layout.size.width < size.width && topLeft.y + layout.size.height < size.height)
                        drawText(layout, topLeft = topLeft)
                }
                // only an edge of this drawing (the pointer may not have moved since a load)
                hoverEdge?.takeIf { it in d.curves }?.let { e ->
                    val layout = textMeasurer.measure("edge $e", TextStyle(color = Color.DarkGray, fontSize = 12.sp))
                    drawText(layout, topLeft = Offset(8f, size.height - layout.size.height - 6f))
                }
            } else {
                val msg = state.drawError ?: if (state.computing) "Computing the drawing ..." else null
                if (msg != null) {
                    val layout = textMeasurer.measure(msg, TextStyle(color = Color.DarkGray, fontSize = 14.sp), constraints = Constraints(maxWidth = (size.width - 32).toInt().coerceAtLeast(1)))
                    drawText(layout, topLeft = Offset(16f, 16f))
                }
            }
        }
        LaunchedEffect(state.rs) {
            hoverEdge = null
            if (flipMenu?.first != state.rs) flipMenu = null
            if (faceMenu?.first != state.rs) faceMenu = null
        }
        faceMenu?.takeIf { it.first == state.rs }?.let { (_, f, at) -> FaceMenu(state, f, at) { faceMenu = null } }
        flipMenu?.takeIf { it.first == state.rs }?.let { (_, f, at) ->
            val entries = remember(state.rs, f) { state.flipsAt(f).map { flipNode(state, it) } }
            if (entries.isNotEmpty()) ActionMenu(state, at, "Flip", entries) { flipMenu = null }
        }
    }
}

/** Pixels between a vertex and its label (graph_2_ipe: +8, +8 in draw2). */
private const val LABEL_OFFSET = 7f

/** A flip as a menu entry: highlights the three edges and the faces it touches. */
fun flipNode(state: AppState, flip: Flip): MenuNode =
    MenuNode("Flip ${flip.a} ${flip.b} ${flip.c}", flipHighlight(state, flip)) { state.flip(flip) }

/** What a flip highlights: the edges ab, ac, bc and its faces. */
fun flipHighlight(state: AppState, flip: Flip): Highlight {
    fun e(x: Int, y: Int) = Edge(minOf(x, y), maxOf(x, y))
    return Highlight(state.flipFaces(flip), setOf(e(flip.a, flip.b), e(flip.a, flip.c), e(flip.b, flip.c)))
}

/** The context menu of face [f]: its flips and Make outer face. */
@Composable
private fun FaceMenu(state: AppState, f: Int, at: Offset, close: () -> Unit) {
    val outer = f == state.outerFace
    val entries = remember(state.rs, f, outer) {
        buildList {
            addAll(state.flipsAt(f).map { flipNode(state, it) })
            if (!outer) add(MenuNode("Make outer face", Highlight(setOf(f))) { state.makeOuterFace(f) })
        }
    }
    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    ActionMenu(state, at, "Face" + if (outer) " (outer face)" else "", entries, close)
}

/** The curve pieces as a path in screen coordinates. */
private fun path(pieces: List<Cubic>, view: ViewTransform): Path = Path().apply {
    if (pieces.isEmpty()) return@apply
    val start = view.toScreen(pieces.first().p0)
    moveTo(start.x, start.y)
    for (c in pieces) {
        val a = view.toScreen(c.c1)
        val b = view.toScreen(c.c2)
        val e = view.toScreen(c.p3)
        cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
}

/** A polygon in screen coordinates. */
private fun polygonPath(points: List<Point>, view: ViewTransform): Path = Path().apply {
    if (points.isEmpty()) return@apply
    val s = view.toScreen(points.first())
    moveTo(s.x, s.y)
    for (p in points.drop(1)) view.toScreen(p).let { lineTo(it.x, it.y) }
    close()
}

/** The "!" of a face where a click flips: a round badge with a bar and a dot, drawn (no font needed). */
internal val MARK_FILL = Color(0xFF2F5D62)

/** Highlighted edges (menu and list entries): a wide band under the curves. */
internal val HIGHLIGHT_SIDE = Color(0xFFFFB300)

/** A face highlighted from a menu entry. */
internal val HIGHLIGHT_FILL = Color(0x88FFD600)

private fun DrawScope.drawRotSys(d: RotDrawing, view: ViewTransform, state: AppState, hoverEdge: Edge?) {
    val flipFaces = state.flips.flatMap { state.flipFaces(it) }.toSet()
    for (f in flipFaces) if (f != d.outerFace) drawPath(polygonPath(d.polygons[f], view), FLIP_FILL)
    val hover = state.hoverFace
    if (hover != null && hover != d.outerFace && hover in d.polygons.indices) drawPath(polygonPath(d.polygons[hover], view), HOVER_FILL)
    val highlight = state.menuHighlight
    if (highlight != null) {
        for (f in highlight.faces) if (f != d.outerFace && f in d.polygons.indices) drawPath(polygonPath(d.polygons[f], view), HIGHLIGHT_FILL)
        for (e in highlight.edges) d.curves[e]?.let { drawPath(path(it, view), HIGHLIGHT_SIDE, style = Stroke(state.lineWidth + 8f, cap = StrokeCap.Round)) }
    }
    hoverEdge?.let { e -> d.curves[e]?.let { drawPath(path(it, view), HOVER_FILL, style = Stroke(state.lineWidth + 8f, cap = StrokeCap.Round)) } }
    val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    for (s in curveStrokes(d, state.colorScheme)) {
        drawPath(path(s.pieces, view), s.color, style = Stroke(state.lineWidth, cap = StrokeCap.Round, pathEffect = if (s.dashed) dashes else null))
    }
    for (m in vertexMarks(d, state.colorScheme)) {
        val c = view.toScreen(m.at)
        drawCircle(m.fill, VERTEX_RADIUS, c)
        if (m.fill != Color.Black) drawCircle(Color.Black, VERTEX_RADIUS, c, style = Stroke(1.5f))
    }
    // "!" in the hovered face when a click flips there; for the outer face in a corner (ArrView's)
    if (hover != null && hover in flipFaces) {
        if (hover == d.outerFace) {
            outerMarkCorner(d, view, size.width, size.height)?.let { drawActionMark(it) }
        } else {
            val poly = d.polygons[hover]
            val cx = poly.sumOf { it.x } / poly.size
            val cy = poly.sumOf { it.y } / poly.size
            drawActionMark(view.toScreen(Point(cx, cy)))
        }
    }
}

private const val VERTEX_RADIUS = 6f

private fun DrawScope.drawActionMark(at: Offset) {
    drawCircle(MARK_FILL, 9f, at)
    drawLine(Color.White, at + Offset(0f, -5f), at + Offset(0f, 1.5f), strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawCircle(Color.White, 1.5f, at + Offset(0f, 5f))
}

/** Pixels from the canvas corners to the outer face's "!". */
internal const val CORNER_INSET = 28f

/**
 * Where the outer face's "!" goes: of the four canvas corners (moved [CORNER_INSET] inwards) that
 * lie in the outer face, the one farthest from the drawing; null if none does (ArrView's).
 */
internal fun outerMarkCorner(d: RotDrawing, view: ViewTransform, width: Float, height: Float): Offset? {
    val corners = listOf(
        Offset(CORNER_INSET, CORNER_INSET), Offset(width - CORNER_INSET, CORNER_INSET),
        Offset(CORNER_INSET, height - CORNER_INSET), Offset(width - CORNER_INSET, height - CORNER_INSET),
    )
    val points = d.polygons.flatten()
    return corners
        .filter { d.faceAt(view.toWorld(it)) == d.outerFace }
        .maxByOrNull { c -> val w = view.toWorld(c); points.minOfOrNull { hypot(it.x - w.x, it.y - w.y) } ?: 0.0 }
}

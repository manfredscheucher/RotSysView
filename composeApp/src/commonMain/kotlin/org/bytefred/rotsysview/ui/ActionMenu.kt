package org.bytefred.rotsysview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.bytefred.rotsysview.core.Edge
import kotlin.math.roundToInt

/** What a menu or list entry highlights in the canvas while the pointer is on it: faces of the planarization and edges of K_n. */
data class Highlight(val faces: Set<Int> = emptySet(), val edges: Set<Edge> = emptySet())

/**
 * An entry of an [ActionMenu]: a leaf runs [run]; a group has [children], which open in a second
 * column next to the menu (a submenu) when the pointer is on it or it is clicked.
 */
class MenuNode(val label: String, val highlight: Highlight, val children: List<MenuNode> = emptyList(), val run: (() -> Unit)? = null)

/** An entry for [options] under [label]: a group, or the single option itself (a level with one option is skipped). */
fun group(label: String, highlight: Highlight, options: List<MenuNode>): MenuNode =
    options.singleOrNull()?.let { MenuNode(label, it.highlight, run = it.run) } ?: MenuNode(label, highlight, options)

/**
 * A menu of flips at [at] (pixels in the parent): a heading, then the entries; a group's entries
 * show in a second column to its right. Equal labels in a column get " (1)", " (2)", ...
 * Hovering an entry highlights its cell (and sides) in the canvas ([AppState.menuHighlight]).
 * Escape or a click outside closes the menu.
 */
@Composable
fun ActionMenu(state: AppState, at: Offset, heading: String, nodes: List<MenuNode>, close: () -> Unit) {
    var open by remember(nodes) { mutableStateOf<MenuNode?>(null) }
    fun done() {
        state.menuHighlight = null
        close()
    }

    @Composable
    fun Entries(entries: List<MenuNode>, top: Boolean) {
        for ((node, label) in entries.zip(menuLabels(entries.map { it.label }))) {
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    val run = node.run
                    if (run != null) {
                        done()
                        run()
                    } else {
                        open = node
                    }
                },
                trailingIcon = if (node.run == null) ({ Chevron() }) else null,
                modifier = Modifier.pointerInput(node) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            when (ev.type) {
                                // the highlight stays until the pointer is on another entry
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    state.menuHighlight = node.highlight
                                    if (top) open = node.takeIf { it.run == null }
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    // a zero-size anchor at the pointer: the menu opens right there
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = ::done) {
            // the menu takes the focus, so that Escape closes it
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(
                Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min).focusRequester(focus).focusable().onKeyEvent {
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        done()
                        true
                    } else false
                },
            ) {
                Column(Modifier.width(IntrinsicSize.Max).testTag("action-menu")) {
                    MenuHeading(heading)
                    Entries(nodes, top = true)
                }
                val groups = nodes.filter { it.run == null }
                if (groups.isNotEmpty()) {
                    VerticalDivider()
                    // room for the largest submenu from the start, so the menu does not move or
                    // grow under the pointer when a submenu opens
                    Box {
                        for (g in groups) if (g !== open) SubmenuPlaceholder(g)
                        open?.let { g ->
                            Column(Modifier.width(IntrinsicSize.Max).testTag("action-submenu")) {
                                MenuHeading(g.label)
                                Entries(g.children, top = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An invisible copy of [g]'s submenu, a bit wider than the real one: it only takes its room. */
@Composable
private fun SubmenuPlaceholder(g: MenuNode) {
    Column(Modifier.alpha(0f).clearAndSetSemantics {}.padding(end = 8.dp)) {
        MenuHeading(g.label)
        for (c in g.children) {
            // as a menu entry: at least 48 dp high, at most 280 dp wide (longer labels wrap)
            Box(Modifier.heightIn(min = 48.dp).widthIn(max = 280.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(c.label + " (99)", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A drawn ">" (no font glyph): the entry opens a submenu. */
@Composable
private fun Chevron() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density
        drawLine(color, Offset(w * 0.35f, h * 0.2f), Offset(w * 0.7f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, h * 0.5f), Offset(w * 0.35f, h * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** The entry texts for [labels]: equal ones get " (1)", " (2)", ... */
fun menuLabels(labels: List<String>): List<String> {
    val counts = labels.groupingBy { it }.eachCount()
    val seen = HashMap<String, Int>()
    return labels.map { l ->
        val k = (seen[l] ?: 0) + 1
        seen[l] = k
        if (counts.getValue(l) > 1) "$l ($k)" else l
    }
}

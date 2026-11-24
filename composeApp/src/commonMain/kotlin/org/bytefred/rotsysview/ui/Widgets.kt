package org.bytefred.rotsysview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.bytefred.rotsysview.core.Crossing
import org.bytefred.rotsysview.core.DrawStyle
import org.bytefred.rotsysview.core.Edge
import org.bytefred.rotsysview.core.PlanarizationMethod
import org.bytefred.rotsysview.core.TextFormat
import org.bytefred.rotsysview.core.binomial
import org.bytefred.rotsysview.core.crossingMaximal
import org.bytefred.rotsysview.core.crossings
import org.bytefred.rotsysview.core.flipCandidates
import org.bytefred.rotsysview.core.isConvex
import org.bytefred.rotsysview.core.isGeneralizedTwisted
import org.bytefred.rotsysview.core.isHConvex
import org.bytefred.rotsysview.core.obstruction
import kotlin.math.roundToInt

/** The content of a widget (without scrolling; the caller scrolls). */
@Composable
fun WidgetContent(state: AppState, w: Widget) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (w) {
            Widget.PROPERTIES -> PropertiesWidget(state)
            Widget.ENCODING -> EncodingWidget(state)
            Widget.CROSSINGS -> CrossingsWidget(state)
            Widget.FLIPS -> FlipsWidget(state)
            Widget.SETTINGS -> SettingsWidget(state)
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)

@Composable
private fun ValueRow(label: String, value: String) = Row(Modifier.padding(vertical = 1.dp)) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no"

/** The numbers and classes of the rotation system; crossings and flips large on top. */
@Composable
private fun PropertiesWidget(state: AppState) {
    val rs = state.rs
    val obstruction = remember(rs) { rs.obstruction() }
    val crossings = remember(rs) { if (obstruction == null) rs.crossings().size else 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigNumber("crossings", if (obstruction == null) crossings.toString() else "-", Modifier.weight(1f))
        BigNumber("flips", if (obstruction == null) state.flips.size.toString() else "-", Modifier.weight(1f))
    }
    ValueRow("n", "${rs.n}")
    ValueRow("drawable", if (obstruction == null) "yes" else "no")
    if (obstruction != null) {
        Text("contains $obstruction", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("obstruction"))
        return
    }
    ValueRow("crossing-maximal", yesNo(crossingMaximal(rs.n, crossings)) + " (${binomial(rs.n, 4)} K4s)")
    ValueRow("natural labelling", yesNo(rs.isNatural))
    val (lex, _) = remember(rs) { rs.lexmin() }
    ValueRow("lex-min labelling", yesNo(lex == rs))
    Heading("Classes")
    ValueRow("convex", yesNo(remember(rs) { rs.isConvex }))
    ValueRow("h-convex", yesNo(remember(rs) { rs.isHConvex }))
    ValueRow("generalized twisted", yesNo(remember(rs) { rs.isGeneralizedTwisted }))
    state.currentDrawing?.let { d ->
        Heading("Planarization")
        ValueRow("vertices", "${d.planarization.vertexCount}")
        ValueRow("edges", "${d.planarization.edges.size / 2}")
        ValueRow("faces", "${d.planarization.faces.size}")
    }
}

/** A count in large digits with its name below. */
@Composable
private fun BigNumber(name: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(name, style = MaterialTheme.typography.bodySmall)
    }
}

/** The rotations (one line per vertex) and the JSON line, editable; the lex-min form below. */
@Composable
private fun EncodingWidget(state: AppState) {
    Heading(TextFormat.ROWS.label)
    DraftEditor(state, TextFormat.ROWS, wrap = false)
    Heading(TextFormat.JSON.label)
    DraftEditor(state, TextFormat.JSON, wrap = true, copy = true)
    Heading("Lex-min labelling (pyrslib)")
    val (lex, perm) = remember(state.rs) { state.rs.lexmin() }
    Text(lex.toJson(), style = MonoStyle, modifier = Modifier.testTag("lexmin"))
    Text("new vertex i = old vertex ${perm.joinToString(" ")}", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { state.relabel(lex, "Relabelled to the lex-min labelling") }, enabled = lex != state.rs) { Text("Relabel") }
}

/**
 * The [format] text, editable, with Apply and Revert. The draft lives in the state, so closing the
 * widget does not lose it. [wrap]: long lines wrap, else they scroll sideways.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftEditor(state: AppState, format: TextFormat, wrap: Boolean, copy: Boolean = false) {
    val draft = state.drafts[format]
    val text = draft ?: state.text(format)
    val tag = format.name
    if (wrap) {
        OutlinedTextField(
            text, onValueChange = { state.editDraft(format, it) }, textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().textFocus(state).testTag("editor-$tag"),
        )
    } else {
        val hScroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            ScrollbarBox(null, hScroll = hScroll) {
                Box(Modifier.horizontalScroll(hScroll).padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        text, onValueChange = { state.editDraft(format, it) }, textStyle = MonoStyle,
                        modifier = Modifier.widthIn(min = width).textFocus(state).testTag("editor-$tag"),
                    )
                }
            }
        }
    }
    val clipboard = LocalClipboardManager.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.applyDraft(format) }, Modifier.testTag("apply-$tag"), enabled = draft != null) { Text("Apply") }
        OutlinedButton(onClick = { state.revertDraft(format) }, Modifier.testTag("revert-$tag"), enabled = draft != null) { Text("Revert") }
        if (copy) OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)); state.showInfo("Copied") }) { Text("Copy") }
    }
}

/** The crossing pairs and the number of crossings per edge; pointing at an entry highlights its edges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrossingsWidget(state: AppState) {
    val rs = state.rs
    if (rs.obstruction() != null) {
        Text("Not drawable.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val crossings = remember(rs) { rs.crossings() }
    Text("${crossings.size} crossings (of ${binomial(rs.n, 4)} K4s)", style = MaterialTheme.typography.bodyMedium)
    FlowRow(Modifier.testTag("crossing-list")) {
        for ((k, c) in crossings.withIndex()) key(c) {
            Row {
                LinkEntry(state, c.toString(), "crossing-$c", Highlight(edges = setOf(c.e, c.f))) {}
                if (k < crossings.size - 1) Text(", ", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Heading("Crossings per edge")
    val perEdge = remember(rs) { crossingsPerEdge(rs.n, crossings) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((e, k) in perEdge) LinkEntry(state, "$e: $k", "edge-$e", Highlight(edges = setOf(e))) {}
    }
    val uncrossed = perEdge.filter { it.second == 0 }.map { it.first }
    ValueRow("uncrossed edges", "${uncrossed.size}")
}

private fun crossingsPerEdge(n: Int, crossings: List<Crossing>): List<Pair<Edge, Int>> {
    val count = HashMap<Edge, Int>()
    for (c in crossings) {
        count[c.e] = (count[c.e] ?: 0) + 1
        count[c.f] = (count[c.f] ?: 0) + 1
    }
    return buildList { for (a in 0 until n) for (b in a + 1 until n) Edge(a, b).let { add(it to (count[it] ?: 0)) } }
}

/**
 * The flips of pyrslib's `enum_all_flips` whose result is drawable, as links: a click flips,
 * pointing at one highlights its three edges and the faces at its corners.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlipsWidget(state: AppState) {
    val rs = state.rs
    // as Crossings: a system that is not drawable has no triangles to flip
    if (rs.obstruction() != null) {
        Text("Not drawable.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val flips = state.flips
    val candidates = remember(rs) { rs.flipCandidates().size }
    Text(
        "A flip (a, b, c) needs c right after b in the rotation of a and a right after c in the rotation of b; " +
            "it swaps them. Only flips that give a drawable rotation system are listed.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("possible flips (${flips.size}): ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("flip-count"))
    FlowRow(Modifier.testTag("flip-list")) {
        if (flips.isEmpty()) Text("none", style = MaterialTheme.typography.bodyMedium)
        for ((k, f) in flips.withIndex()) key(f) {
            Row {
                LinkEntry(state, "${f.a} ${f.b} ${f.c}", "flip-${f.a}-${f.b}-${f.c}", flipHighlight(state, f)) { state.flip(f) }
                if (k < flips.size - 1) Text(", ", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (candidates > flips.size) Text(
        "${candidates - flips.size} more flip(s) would give a system that is not drawable.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** A link in a list: pointing at it highlights [highlight] on the canvas, a tap runs [onClick]. */
@Composable
private fun LinkEntry(state: AppState, label: String, tag: String, highlight: Highlight, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    DisposableEffect(highlight) {
        onDispose { if (state.menuHighlight == highlight) state.menuHighlight = null }
    }
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
        color = LINK_COLOR,
        modifier = Modifier
            .testTag(tag)
            .pointerInput(highlight) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> state.menuHighlight = highlight
                            PointerEventType.Exit -> if (state.menuHighlight == highlight) state.menuHighlight = null
                        }
                    }
                }
            }
            // a tap, not clickable: the entry must not take the keyboard focus
            .pointerInput(Unit) { detectTapGestures { click() } }
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics {
                role = Role.Button
                onClick { click(); true }
            },
    )
}

/** Display (style, colours, line width, labels), Widgets (max. widgets) and Layout (iterations, eps), and Reset to defaults. */
@Composable
private fun SettingsWidget(state: AppState) {
    val settings = state.layoutSettings
    Text("Display", style = MaterialTheme.typography.titleSmall)
    // the long labels get the full width below (ArrView's Visualization)
    Text("Style", style = MaterialTheme.typography.bodyMedium)
    SelectField(null, state.style.label, DrawStyle.entries, { it.label }, Modifier.fillMaxWidth().testTag("draw-style")) {
        state.changeStyle(it)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Colour scheme", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        SelectField(null, state.colorScheme.label, ColorScheme.entries, { it.label }, Modifier.width(150.dp).testTag("color-scheme")) {
            state.colorScheme = it
        }
    }
    Text("Line width: ${oneDecimal(state.lineWidth)} px", style = MaterialTheme.typography.bodyMedium)
    Slider(state.lineWidth, { state.lineWidth = it }, valueRange = AppState.LINE_WIDTH_RANGE, modifier = Modifier.testTag("line-width"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(state.showLabels, onCheckedChange = { state.showLabels = it })
        Text("Vertex numbers", style = MaterialTheme.typography.bodyMedium)
    }

    Spacer(Modifier.height(12.dp))
    Text("Widgets", style = MaterialTheme.typography.titleSmall)
    // PoCoView's Max. widgets row
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Max. widgets: ${state.maxColumns}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { state.changeMaxColumns(state.maxColumns - 1) }, enabled = state.maxColumns > 1, modifier = Modifier.testTag("max-widgets-minus")) { Text("-") }
        TextButton(onClick = { state.changeMaxColumns(state.maxColumns + 1) }, enabled = state.maxColumns < Widget.entries.size, modifier = Modifier.testTag("max-widgets-plus")) { Text("+") }
    }

    Spacer(Modifier.height(12.dp))
    Text("Layout", style = MaterialTheme.typography.titleSmall)
    Text(LAYOUT_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Planarization", style = MaterialTheme.typography.bodyMedium)
    SelectField(null, state.method.label, PlanarizationMethod.entries, { it.label }, Modifier.fillMaxWidth().testTag("planarization")) {
        state.changeMethod(it)
    }
    fun iterations(n: Int) = state.changeLayoutSettings(state.layoutSettings.copy(maxIterations = n))
    SettingRow("Max. iterations") {
        CompactNumberField(
            state, settings.maxIterations.toString(), { it.toIntOrNull()?.takeIf { n -> n >= 1 } }, "at least 1",
            ITERATION_CHARS, "max-iterations",
            Steppers({ iterations(settings.maxIterations - 1) }, settings.maxIterations > 1, { iterations(settings.maxIterations + 1) }, true),
        ) { n -> if (n != null) iterations(n) }
    }
    SettingRow("eps") {
        CompactNumberField(
            state, epsText(settings.eps), { it.toDoubleOrNull()?.takeIf { e -> e.isFinite() && e > 0.0 } }, "a positive number",
            MAX_FIELD_LENGTH, "eps",
        ) { e -> if (e != null) state.changeLayoutSettings(state.layoutSettings.copy(eps = e)) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = state::resetSettings) { Text("Reset to defaults") }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f).padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
        control()
    }
}

private const val ITERATION_CHARS = 6

internal const val LAYOUT_NOTE = "Parameters of the drawing only (iterated Tutte embedding of the planarization); " +
    "they do not change the rotation system. The style sets the iterations (draw2: 100, rotsys_draw: 20)."

/** [v] with one decimal, the same on the JVM and the web (where 3.0 prints as "3"). */
internal fun oneDecimal(v: Float): String {
    val tenths = (v * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** A real dropdown: read-only text field with an arrow; click opens the list (PoCoView's). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String?, current: String, items: List<T>, itemLabel: (T) -> String, modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            current, onValueChange = {}, readOnly = true, singleLine = true,
            label = label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, onDismissRequest = { open = false }) {
            for (it in items) DropdownMenuItem(text = { Text(itemLabel(it)) }, onClick = { onSelect(it); open = false })
        }
    }
}

/** Counts focused text fields in [AppState.textFieldsFocused], so shortcuts do not fire while typing. */
fun Modifier.textFocus(state: AppState): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused) state.textFieldsFocused-- } }
    onFocusChanged {
        if (it.isFocused != focused) {
            focused = it.isFocused
            state.textFieldsFocused += if (focused) 1 else -1
        }
    }
}

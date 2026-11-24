package org.bytefred.rotsysview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Landscape (side panel) needs at least this width in AUTO layout mode. */
private val WIDE_MIN = 760.dp

/** Below MIN_CANVAS + MIN_COLUMN per widget, the side by side widget columns scroll horizontally. */
private val MIN_COLUMN = 260.dp
private val MIN_CANVAS = 320.dp

/** The canvas's share next to widget columns of share 1 each, until a divider is dragged. */
private const val WIDE_CANVAS_WEIGHT = 2f

/**
 * The whole app: menu bar, toolbar, the canvas and the widget columns next to it or below it.
 * [initialDialog]: the dialog shown at start (default: Welcome, unless it was turned off).
 * [closeRequests] counts the desktop window's close requests (and Cmd+Q): each one asks "Save
 * changes?" if there are unsaved changes, then calls [onClose].
 */
@Composable
fun App(
    initialState: AppState? = null,
    initialView: ViewTransform? = null,
    initialDialog: DialogKind? = null,
    closeRequests: Int = 0,
    onClose: () -> Unit = {},
) {
    RotSysViewTheme { ProvideMonoStyle {
        val state = remember { initialState ?: AppState() }
        // the display settings are stored while the app is shown
        LaunchedEffect(state) { state.keepDisplayPrefs() }
        LaunchedEffect(closeRequests) {
            if (closeRequests > 0) state.leave { state.saveDisplayPrefs(); onClose() }
        }
        val view = remember { initialView ?: ViewTransform() }
        val focus = remember { FocusRequester() }
        // scroll positions survive closing and reopening a widget
        val scrolls = remember { Widget.entries.associateWith { ScrollState(0) } }
        val columnsScroll = rememberScrollState()

        // shortcuts on the root: key events of the focused canvas, chip or button bubble up here;
        // they are ignored while a text field has focus or a menu is open (Escape closes the menu),
        // except Ctrl+O and Ctrl+S (else the browser would open or save the page)
        var dialog by remember {
            mutableStateOf(initialDialog ?: if (loadPref(WELCOME_PREF) == "off") DialogKind.NONE else DialogKind.WELCOME)
        }
        val opener = rememberFileOpener(onError = { state.error = it }) { name, bytes -> state.open(name, bytes) }
        val files = FileKeys(open = { state.leave { opener() } }, save = { dialog = DialogKind.SAVE })
        Surface(Modifier.fillMaxSize().onKeyEvent { closeMenuOnEscape(it, state) || handleFileKey(it, state, files) || state.shortcutsActive && handleKey(it, state, view) }) {
            Dialogs(state, dialog, open = { dialog = it }) { dialog = DialogKind.NONE }
            Column(Modifier.fillMaxSize()) {
                MenuBar(state, view, files.open) { dialog = it }
                ToolBar(state)
                HorizontalDivider()
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val wide = when (state.layoutMode) {
                        LayoutMode.LANDSCAPE -> true
                        LayoutMode.PORTRAIT -> false
                        LayoutMode.AUTO -> maxWidth >= WIDE_MIN
                    }
                    val canvas = @Composable { m: Modifier ->
                        Box(m.focusRequester(focus).focusable()) {
                            // the canvas is composed inside BoxWithConstraints (subcomposition),
                            // so request the initial focus from here, once it is attached
                            // (again after a widget was opened or closed, and after a load or a set
                            // step, e.g. by the toolbar's < >: keeps the shortcuts working);
                            // not while a menu is open: the menu bar holds the focus for Escape
                            LaunchedEffect(state.openWidgets.toList(), state.fitRequest) {
                                if (state.textFieldsFocused == 0 && state.openMenu == null) focus.requestFocus()
                            }
                            RotSysCanvas(state, view, onInteract = { focus.requestFocus() })
                        }
                    }
                    val column = @Composable { w: Widget, m: Modifier ->
                        Column(m.touches(state, w)) {
                            WidgetHeader(state, w)
                            ScrollColumn(Modifier.weight(1f), scrolls.getValue(w)) {
                                WidgetContent(state, w)
                            }
                        }
                    }
                    val open = state.openWidgets.toList()
                    // the canvas (key null) and the widgets in opening order, with draggable dividers
                    fun panels(canvasWeight: Float): List<SplitPanel<Widget?>> =
                        listOf(SplitPanel<Widget?>(null, canvasWeight, canvas)) + open.map { w -> SplitPanel<Widget?>(w) { m -> column(w, m) } }
                    if (wide && maxWidth < MIN_CANVAS + MIN_COLUMN * open.size) {
                        // too narrow to split: the canvas keeps MIN_CANVAS, the MIN_COLUMN wide
                        // columns right of it scroll horizontally
                        Row(Modifier.fillMaxSize()) {
                            canvas(Modifier.width(MIN_CANVAS).fillMaxHeight())
                            ScrollbarBox(null, Modifier.weight(1f).fillMaxHeight(), columnsScroll) {
                                Row(Modifier.fillMaxHeight().horizontalScroll(columnsScroll)) {
                                    for (w in open) key(w) {
                                        VerticalDivider()
                                        column(w, Modifier.width(MIN_COLUMN).fillMaxHeight())
                                    }
                                }
                            }
                        }
                    } else if (wide) {
                        // the widgets as columns right of the canvas
                        SplitPane(panels(WIDE_CANVAS_WEIGHT), state.wideWeights, { state.wideWeights = it }, horizontal = true, Modifier.fillMaxSize())
                    } else {
                        // portrait: the widgets stacked below the canvas, each with its own scrolling
                        SplitPane(panels(1f), state.stackedWeights, { state.stackedWeights = it }, horizontal = false, Modifier.fillMaxSize())
                    }
                }
            }
        }
    } }
}

/** Any press inside a widget column makes it the most recently clicked one (not consumed; PoCoView's). */
private fun Modifier.touches(state: AppState, w: Widget) = pointerInput(w) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press) state.touchWidget(w)
        }
    }
}

/** Escape closes the open menu (the menu bar takes the focus while one is open). */
private fun closeMenuOnEscape(e: KeyEvent, state: AppState): Boolean {
    if (e.type != KeyEventType.KeyDown || e.key != Key.Escape || state.openMenu == null) return false
    state.openMenu = null
    return true
}

/** The file actions of the shortcuts Ctrl+O and Ctrl+S (the same as the menu entries). */
private class FileKeys(val open: () -> Unit, val save: () -> Unit)

private fun handleFileKey(e: KeyEvent, state: AppState, files: FileKeys): Boolean {
    if (e.type != KeyEventType.KeyDown || !(e.isCtrlPressed || e.isMetaPressed) || e.isShiftPressed) return false
    if (e.key == Key.O || e.key == Key.S) state.openMenu = null
    when (e.key) {
        Key.O -> files.open()
        Key.S -> files.save()
        else -> return false
    }
    return true
}

private fun handleKey(e: KeyEvent, state: AppState, view: ViewTransform): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val cmd = e.isCtrlPressed || e.isMetaPressed
    when {
        cmd && e.key == Key.Z && e.isShiftPressed -> state.redo()
        cmd && e.key == Key.Z -> state.undo()
        cmd && e.key == Key.Y -> state.redo()
        cmd -> return false
        e.key == Key.Plus || e.key == Key.Equals || e.key == Key.NumPadAdd -> view.zoomCenter(ZOOM_STEP)
        e.key == Key.Minus || e.key == Key.NumPadSubtract -> view.zoomCenter(1 / ZOOM_STEP)
        e.key == Key.MoveHome || e.key == Key.Zero -> state.requestFit()
        e.key == Key.PageUp || e.key == Key.Comma -> state.showSet(state.setIndex - 1)
        e.key == Key.PageDown || e.key == Key.Period -> state.showSet(state.setIndex + 1)
        else -> return false
    }
    return true
}

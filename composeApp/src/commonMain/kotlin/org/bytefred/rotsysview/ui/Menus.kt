package org.bytefred.rotsysview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A menu title that opens a [DropdownMenu] (PoCoView's). Plain clickable text rather than a
 * TextButton, so the bar stays narrow. The popup is not focusable, so a click outside it still
 * reaches what is underneath: clicking another menu title closes this menu and opens that one in
 * one click. A click on the open menu's own title closes it: the popup's outside-click dismissal
 * fires first (on press), so the title click right after it must not reopen the menu. The open
 * menu is [AppState.openMenu], one for all menus.
 */
@Composable
fun MenuButton(state: AppState, label: String, items: @Composable () -> Unit) {
    var dismissedAt by remember { mutableStateOf<TimeMark?>(null) }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .testTag("menu-$label")
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    val justClosed = dismissedAt?.let { it.elapsedNow() < MENU_CLICK_GUARD } == true
                    state.openMenu = menuAfterTitleClick(state.openMenu, label, justClosed)
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        DropdownMenu(
            expanded = state.openMenu == label,
            // only close if this menu is still the open one: the outside click
            // may have just opened another menu
            onDismissRequest = {
                if (state.openMenu == label) {
                    dismissedAt = TimeSource.Monotonic.markNow()
                    state.closeMenuByOutsideClick()
                }
            },
            properties = PopupProperties(focusable = false),
        ) {
            items()
        }
    }
}

/**
 * The open menu after a click on the title [label] while [open] is open: a click on the open
 * menu's title closes it, as does one [justClosed] (the same click already closed it by its
 * outside-click dismissal); any other click opens [label], switching from another open menu.
 */
fun menuAfterTitleClick(open: String?, label: String, justClosed: Boolean): String? =
    if (open == label || justClosed) null else label

// a title or canvas click this soon after a menu's outside-click dismissal belongs to
// the same click (press dismissed, release clicked)
internal val MENU_CLICK_GUARD = 500.milliseconds

/** A menu entry: closes the menu, then runs [onClick]. */
@Composable
fun MenuItem(state: AppState, text: String, enabled: Boolean = true, shortcut: String? = null, onClick: () -> Unit) =
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = shortcut?.let { { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        enabled = enabled,
        onClick = { state.openMenu = null; onClick() },
    )

/** A menu toggle with a drawn checkmark; a click keeps the menu open (to toggle several). */
@Composable
fun CheckMenuItem(text: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) =
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Box(Modifier.size(18.dp)) { if (checked) CheckIcon() } },
        enabled = enabled,
        onClick = onToggle,
    )

/** A checkmark drawn as a path (no glyph, so no font fallback on the web). */
@Composable
fun CheckIcon(modifier: Modifier = Modifier.size(18.dp)) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.76f)
            lineTo(w * 0.84f, h * 0.26f)
        }
        drawPath(path, color, style = Stroke(w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The grey heading of a radio group in a menu, or of a context menu. */
@Composable
fun MenuHeading(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
)

/** Rotation system / Edit / View / Help on the left, the app name (and on the web a subtitle) on the right. */
@Composable
fun MenuBar(state: AppState, view: ViewTransform, openFile: () -> Unit, openDialog: (DialogKind) -> Unit) {
    val saver = rememberFileSaver(onError = { state.error = it }) { name -> if (name != null) state.showInfo("Saved $name") }
    // the popups are not focusable (see MenuButton), so the bar takes the focus while a menu
    // is open; Escape bubbles up from here to the app's key handler, which closes the menu
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(state.openMenu) { if (state.openMenu != null) barFocus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().background(MENU_BAR_COLOR).padding(start = 4.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // scrolls sideways when the menus do not fit
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .testTag("menu-bar")
                .focusRequester(barFocus)
                .focusable(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(state, "Rotation system") {
                MenuItem(state, "Examples...") { openDialog(DialogKind.EXAMPLES) }
                MenuItem(state, "Import...") { openDialog(DialogKind.IMPORT) }
                MenuItem(state, "Export...") { openDialog(DialogKind.EXPORT) }
                HorizontalDivider()
                MenuItem(state, "Open file...", shortcut = "Ctrl+O") { openFile() }
                MenuItem(state, "Save file...", shortcut = "Ctrl+S") { openDialog(DialogKind.SAVE) }
                HorizontalDivider()
                MenuItem(state, "Export SVG...", enabled = state.currentDrawing != null) {
                    state.currentDrawing?.let { saver("${state.fileName}.svg", drawingSvg(it, state.colorScheme, state.lineWidth, state.showLabels).encodeToByteArray()) }
                }
            }
            MenuButton(state, "Edit") {
                MenuItem(state, "Undo", enabled = state.canUndo, shortcut = "Ctrl+Z") { state.undo() }
                MenuItem(state, "Redo", enabled = state.canRedo, shortcut = "Ctrl+Shift+Z") { state.redo() }
            }
            MenuButton(state, "View") {
                MenuItem(state, "Fit", shortcut = "0") { state.requestFit() }
                MenuItem(state, "Zoom in", shortcut = "+") { view.zoomCenter(ZOOM_STEP) }
                MenuItem(state, "Zoom out", shortcut = "-") { view.zoomCenter(1 / ZOOM_STEP) }
                HorizontalDivider()
                for (w in Widget.entries) CheckMenuItem(w.label, w in state.openWidgets) { state.toggleWidget(w) }
                HorizontalDivider()
                MenuHeading("Layout")
                for (m in LayoutMode.entries) CheckMenuItem(m.name.lowercase(), state.layoutMode == m) { state.layoutMode = m }
            }
            MenuButton(state, "Help") {
                for (page in HelpPage.entries) MenuItem(state, "${page.title}...") { state.helpPage = page; openDialog(DialogKind.HELP) }
                MenuItem(state, "About RotSysView...") { openDialog(DialogKind.ABOUT) }
            }
        }
        // one line + ellipsis, shrinking (weight), so the header cannot push the menus off-screen
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("RotSysView") }
                if (isWeb) append("  Rotation system viewer")
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
        )
    }
}

/** View menu, + and - keys: zoom factor per step. */
const val ZOOM_STEP = 1.2

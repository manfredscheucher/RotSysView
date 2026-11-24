package org.bytefred.rotsysview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The Help menu's pages, in menu order (About has its own dialog). */
enum class HelpPage(val title: String) {
    GENERAL("General"),
    VISUALIZATION("Visualization"),
    WIDGETS("Widgets"),
    FORMATS("Formats"),
    SHORTCUTS("Keyboard shortcuts"),
}

/** One Help page in a [BigDialog]: plain paragraphs; the shortcuts page lists the keys in bold. */
@Composable
fun HelpDialog(page: HelpPage, onDismiss: () -> Unit) {
    BigDialog(page.title, onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(vScroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (page == HelpPage.SHORTCUTS) 4.dp else 12.dp),
            ) {
                if (page == HelpPage.SHORTCUTS) {
                    for ((key, action) in SHORTCUTS) KeyLine(key, action)
                    Paragraph(SHORTCUTS_NOTE)
                } else {
                    for (text in helpText(page)) Paragraph(text)
                }
            }
        }
    }
}

/** The paragraphs of [page] as plain text (the shortcuts page: "key: action" per key). */
internal fun helpText(page: HelpPage): List<String> = when (page) {
    HelpPage.GENERAL -> GENERAL_TEXT
    HelpPage.VISUALIZATION -> VISUALIZATION_TEXT
    HelpPage.WIDGETS -> WIDGETS_TEXT
    HelpPage.FORMATS -> FORMATS_TEXT
    HelpPage.SHORTCUTS -> SHORTCUTS.map { (key, action) -> "$key: $action" } + SHORTCUTS_NOTE
}

@Composable
private fun Paragraph(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/** A shortcut: the key in bold, then what it does. */
@Composable
private fun KeyLine(key: String, action: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(key) }
            append(": $action")
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** [eps] as typed and shown: `1e-8`, not `1.0E-8` (the JVM's toString). */
fun epsText(eps: Double): String = eps.toString().replace("E", "e").replace(".0e", "e")

/** What rotation systems are (Welcome dialog and the first Help paragraph). */
internal const val ROTSYS_INTRO = "RotSysView is a viewer for rotation systems of complete graphs K_n. In a simple " +
    "drawing of K_n, every two edges meet at most once, at a common vertex or in a proper crossing. The rotation of " +
    "a vertex is the counterclockwise order of its edges. The rotation system determines which edges cross and " +
    "the drawing on the sphere."

/** The Welcome dialog's text. */
internal const val WELCOME_TEXT = "$ROTSYS_INTRO Load an example, look at its properties and change it by flips. " +
    "To flip, click a light yellow face."

private val GENERAL_TEXT = listOf(
    ROTSYS_INTRO,
    "The drawing follows the sage programs pyrslib draw2.sage (default) and rotsys-sat rotsys_draw.sage; the " +
        "Help page Visualization explains it.",
    "A flip (a, b, c) swaps b and c in the rotation of a and a and c in the rotation of b; it is possible if c " +
        "comes right after b around a and a right after c around b (pyrslib's enum_all_flips). RotSysView offers only " +
        "the flips that give a drawable rotation system. Faces at the corners of a flip are light yellow: click one " +
        "to flip (a menu if there are several flips). Right-click a face (Ctrl+click on a Mac, long press on " +
        "touch screens) for its flips and Make outer face. Drag to pan, zoom with the wheel or by pinching.",
    "A rotation system is drawable if it contains none of the obstructions Π4o, Π5,1o, Π5,2o (Bergold, " +
        "Scheucher, Theorem 3, after Kynčl). Anything else is shown with the obstruction it contains.",
    "In the Rotation system menu, Examples... has the families C_n and T_n (choose n) and " +
        "rotation systems grouped by what they show, e.g. all drawings of K_n for n ≤ 7. Loading a member of a set opens the whole set: < and > in the " +
        "toolbar (or PageUp / PageDown) step through it. Import... reads pasted text, Export... gives the text to " +
        "copy, Open file... and Save file... read and write files, Export SVG... saves the picture. Undo and Redo " +
        "are in the Edit menu.",
)

private val VISUALIZATION_TEXT = listOf(
    "Planarization: every crossing becomes a vertex, so the drawing becomes a plane graph. Which edges cross " +
        "follows from the rotation system; the order of the crossings along each edge is found by routing " +
        "(default: the edges are inserted one by one into the drawing so far, leaving and entering their " +
        "vertices where the rotations say and crossing exactly the right edges), by SAT as in rotsys_draw.sage, " +
        "or by draw2.sage's search (Settings, Planarization). Each gives a drawing with this rotation system, not necessarily the same one; SAT and the " +
        "search are slow from n = 6 on.",
    "Iterated Tutte embedding: the outer face is a regular polygon and every other vertex sits at the weighted " +
        "average of its neighbours. The first embedding has equal weights; then each edge weight moves towards " +
        "the squared edge length and grows with the areas of its two faces, and the graph is embedded again, up " +
        "to Max. iterations times (draw2: 100, rotsys_draw: 20).",
    "Every edge is a B-spline through its crossings. The outer face is the face with the fewest crossings " +
        "(draw2), or a face of vertices only, else the largest (rotsys_draw); right-click a face for Make outer face.",
)

private val WIDGETS_TEXT = listOf(
    "Widgets are columns next to the canvas (below it on a narrow window). The toolbar and the View menu " +
        "show and hide them.",
    "Properties: n, the number of crossings and flips, whether the system is drawable, crossing-maximal (every K4 " +
        "crossed), naturally labelled (vertex 0 sees 1, 2, ..., n-1) or lex-min labelled, convex, h-convex and " +
        "generalized twisted (the forbidden configurations of rotsys-sat), and the size of the planarization.",
    "Encoding: the rotations, one line per vertex, and the same as a JSON line; edit them and press Apply. " +
        "Below, pyrslib's lex-min labelling; Relabel switches to it.",
    "Crossings: every crossing pair {a,b}×{c,d} and the crossings per edge; pointing at one highlights its edges.",
    "Flips: the possible flips; click one to flip, pointing at one highlights its edges and faces.",
    "Settings: the style (pyrslib draw2 or rotsys-sat rotsys_draw), colours, line width, vertex numbers, the " +
        "number of widgets open at once, the planarization (routing, SAT or draw2's search) and the parameters of " +
        "the Tutte iteration (max. iterations, eps).",
)

private val FORMATS_TEXT = listOf(
    "Rotations: one line per vertex 0, 1, ..., n-1, its rotation (0-based, as pyrslib's to_string). " +
        "Lines with the numbers 1..n are read as 1-based. Several systems are separated by an empty line.",
    "JSON: [[1,2,3],[0,2,3],[0,1,3],[0,1,2]], one system per line (rotsys-sat's output).",
    "Lidický's lines: n 0  1:432 2:431 3:412 4:123, 1-based (the data of Balogh, Lidický, Salazar).",
    "Save file writes JSON {\"name\": ..., \"rotation_systems\": [...]} or the JSON lines as text. A file with " +
        "several systems is loaded as a set.",
)

/** The keyboard shortcuts page: key and action. */
private val SHORTCUTS = listOf(
    "Esc" to "close the open menu",
    "Ctrl+O" to "open a file",
    "Ctrl+S" to "save to a file",
    "Ctrl+Z" to "undo",
    "Ctrl+Shift+Z or Ctrl+Y" to "redo",
    "+ / -" to "zoom in / out",
    "Home or 0" to "fit",
    "PageUp or , / PageDown or ." to "previous / next rotation system of a file or example set",
    "Mouse" to "click a light yellow face to flip, right-click a face (long press on touch screens) for its " +
        "menu, drag to pan, wheel or pinch to zoom",
)

private const val SHORTCUTS_NOTE = "On a Mac, Cmd works like Ctrl and Ctrl+click like a right-click. The keys " +
    "do nothing while you type in a field or a menu is open (except Esc, Ctrl+O and Ctrl+S)."

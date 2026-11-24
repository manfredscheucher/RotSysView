package org.bytefred.rotsysview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.bytefred.rotsysview.GeneratedBuildInfo
import org.bytefred.rotsysview.GeneratedLicenses
import org.bytefred.rotsysview.core.FileFormat
import org.bytefred.rotsysview.core.TextFormat

/** Which dialog is open (the menu bar and the Welcome dialog open them); HELP shows [AppState.helpPage]. */
enum class DialogKind { NONE, ABOUT, IMPORT, EXPORT, SAVE, EXAMPLES, WELCOME, HELP }

/** The modal dialogs: the open [kind], "Save changes?" and the error dialog on top. */
@Composable
fun Dialogs(state: AppState, kind: DialogKind, open: (DialogKind) -> Unit, close: () -> Unit) {
    val saver = rememberFileSaver(onError = { state.error = it }, onSaved = state::fileSaved)
    fun save(format: FileFormat) = saver(state.saveName(format), state.beginSave(format))
    when (kind) {
        DialogKind.NONE -> {}
        DialogKind.IMPORT -> ImportDialog(state, close)
        DialogKind.EXPORT -> ExportDialog(state, close)
        // "Save changes?" replaces it (no two format dialogs on top of each other)
        DialogKind.SAVE -> if (state.pendingLeave == null) {
            SaveChoiceDialog("Save file", null, FileFormat.entries, onPick = { save(it); close() }, onDismiss = close) {
                TextButton(onClick = close) { Text("Cancel") }
            }
        }
        DialogKind.EXAMPLES -> ExamplesDialog(state, close)
        DialogKind.WELCOME -> WelcomeDialog(close = close, showExamples = { open(DialogKind.EXAMPLES) })
        DialogKind.ABOUT -> AboutDialog(close)
        DialogKind.HELP -> HelpDialog(state.helpPage, close)
    }
    LaunchedEffect(state.pendingLeave != null) { if (state.pendingLeave != null && kind == DialogKind.SAVE) close() }
    // Load / Import / Open file / closing with unsaved changes (PoCoView's); after the other
    // dialogs, so it shows on top of the one that asked. The format buttons save the rotation systems;
    // a widget draft is never saved implicitly, only discarded.
    if (state.pendingLeave != null) SaveChoiceDialog(
        "Save changes?",
        unsavedText(state),
        if (state.rsUnsaved) FileFormat.entries else emptyList(),
        onPick = ::save,
        onDismiss = state::cancelLeave,
    ) {
        TextButton(onClick = state::discardAndLeave) { Text("Discard") }
        TextButton(onClick = state::cancelLeave) { Text("Cancel") }
    }
    // last, so it shows on top of the dialog the error came from
    state.loadedSetsNotice?.let { count -> if (loadPref(SETS_NOTICE_PREF) != "off") SetsLoadedDialog(state, count) else state.loadedSetsNotice = null }
    state.error?.let { ErrorDialog(it) { state.error = null } }
}

/** A load or apply error the user must see (the toolbar status is for info): one OK button. */
@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Error") },
    text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
)

/** What "Save changes?" says is unsaved, or null if nothing (the dialog shows no empty text). */
private fun unsavedText(state: AppState): String? = buildList {
    if (state.rsUnsaved) add("Your changes to \"${state.name}\" are not saved.")
    if (state.unappliedDraft) add("A text edited in the Encoding widget is not applied.")
}.joinToString(" ").takeIf { it.isNotEmpty() }

/**
 * Which format to save as (PoCoView's): [message], one full-width button per format in
 * [formats], and [buttons] below them (Cancel, or Discard / Cancel in "Save changes?").
 */
@Composable
private fun SaveChoiceDialog(
    title: String,
    message: String?,
    formats: List<FileFormat>,
    onPick: (FileFormat) -> Unit,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { Row { buttons() } },
    title = { Text(title) },
    text = {
        Column(Modifier.widthIn(min = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            for (format in formats) FilledTonalButton(onClick = { onPick(format) }, Modifier.fillMaxWidth()) {
                Text(saveLabel(format))
            }
        }
    },
)

private fun saveLabel(format: FileFormat) = when (format) {
    FileFormat.JSON -> "Save JSON"
    FileFormat.TXT -> "Save text"
}

/** The current rotation system as text to copy. */
@Composable
private fun ExportDialog(state: AppState, close: () -> Unit) {
    var format by remember { mutableStateOf(TextFormat.ROWS) }
    val clipboard = LocalClipboardManager.current
    val text = state.text(format)
    BigDialog(
        "Export",
        onDismiss = close,
        buttons = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)); state.showInfo("Copied") }) { Text("Copy") }
            TextButton(onClick = close) { Text("Close") }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (f in TextFormat.entries) FilterChip(
                selected = format == f,
                onClick = { format = f },
                label = { Text(f.label) },
            )
        }
        OutlinedTextField(
            text, onValueChange = {}, readOnly = true,
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).textFocus(state).testTag("export-text"),
        )
    }
}

/** Paste rotation systems; the format is detected. */
@Composable
private fun ImportDialog(state: AppState, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    BigDialog(
        "Import",
        onDismiss = close,
        buttons = {
            TextButton(onClick = close) { Text("Cancel") }
            TextButton(onClick = {
                state.parseImport(text)?.let { read -> state.leave { state.loadImport(read); close() } }
            }) { Text("Import") }
        },
    ) {
        Text(IMPORT_FORMATS, style = MonoStyle)
        OutlinedTextField(
            text, onValueChange = { text = it },
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).textFocus(state).testTag("import-text"),
        )
    }
}

private const val IMPORT_FORMATS = """1 2 3 4 / 0 2 3 4 / ...          one rotation per line (0-based)
[[1,2,3,4],[0,2,3,4],...]          JSON, one system per line
5 0  1:2345 2:1345 ...             one system per line, 1-based
{"name": ..., "rotation_systems": [...]}   JSON file"""

/** Yellow box asking for interesting rotation systems and feedback, with a clickable e-mail address. */
@Composable
fun FeedbackBox(question: String) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A3000))
        LinkText(EMAIL, feedbackMailto(), MaterialTheme.typography.bodyMedium)
    }
}

private fun feedbackMailto(): String {
    fun enc(s: String) = buildString {
        for (b in s.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c)
            else append("%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
    val subject = "RotSysView: rotation system / feedback"
    val body = "Hi Manfred,\n\n(an interesting rotation system, an idea, or other feedback)\n\n" +
        "Rotation system (Rotation system > Export):\n\n"
    return "mailto:$EMAIL?subject=${enc(subject)}&body=${enc(body)}"
}

private const val EMAIL = "manfred.scheucher@proton.me"

/** After loading a file or an example set with several rotation systems (ArrView's / PoCoView's sets notice). */
@Composable
private fun SetsLoadedDialog(state: AppState, count: Int) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(SETS_NOTICE_PREF, "off"); state.loadedSetsNotice = null }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("OK") } },
        title = { Text("$count rotation systems loaded") },
        text = {
            Column {
                Text(
                    "Showing ${if (state.setIndex == 0) "the first" else "rotation system ${state.setIndex + 1}"} of $count. Switch between them with the arrows < > at the top (or PageUp / PageDown).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DontShowAgain(dontShow) { dontShow = it }
            }
        },
    )
}

/** "Don't show again" (the label toggles too). */
@Composable
private fun DontShowAgain(checked: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
    verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(checked, onCheckedChange = null)
    Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
}

/** Shown at start until "Don't show again" is ticked. */
@Composable
private fun WelcomeDialog(close: () -> Unit, showExamples: () -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(WELCOME_PREF, "off"); close() }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("Start") } },
        dismissButton = { TextButton(onClick = { if (dontShow) savePref(WELCOME_PREF, "off"); showExamples() }) { Text("Examples") } },
        title = { Text("Welcome to RotSysView") },
        text = {
            Column {
                Text(
                    WELCOME_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                FeedbackBox("Interesting rotation systems, ideas or general feedback?")
                DontShowAgain(dontShow) { dontShow = it }
            }
        },
    )
}

/** About: description, feedback box, project links and literature, build identity, license texts. */
@Composable
private fun AboutDialog(close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    confirmButton = { TextButton(onClick = close) { Text("OK") } },
    title = { Text("RotSysView") },
    text = {
        ScrollColumn(Modifier.heightIn(max = 520.dp)) {
            Text(ABOUT, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FeedbackBox("Interesting rotation systems, ideas or general feedback?")
            Spacer(Modifier.height(8.dp))
            LinkText("RotSysView on the web", "https://scheucher.dev/RotSysView/", MaterialTheme.typography.bodyMedium)
            LinkText("RotSysView on GitHub", "https://github.com/manfredscheucher/RotSysView", MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Literature", style = MaterialTheme.typography.titleSmall)
            for (paper in PAPERS) Text(paper, Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
            for ((title, url) in PAPER_LINKS) LinkText(title, url, MaterialTheme.typography.bodySmall)
            if (isWeb) TermsAndPrivacy()
            Spacer(Modifier.height(8.dp))
            Text(buildLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var showLicenses by remember { mutableStateOf(false) }
            TextButton(onClick = { showLicenses = !showLicenses }) {
                Text(if (showLicenses) "Hide license texts" else "License texts")
            }
            if (showLicenses) {
                for (notice in GeneratedLicenses.NOTICES) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(notice.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(notice.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    },
)

/**
 * The Terms & privacy note of the website (web only, as ktoy's), shown after a click on its button, word for word the text of
 * misc/turingmachine/doc/privacy-github-pages.md; "please let me know" mails the feedback address.
 */
@Composable
private fun TermsAndPrivacy() {
    val link = TextLinkStyles(SpanStyle(color = LINK_COLOR, textDecoration = TextDecoration.Underline))
    fun linked(before: String, label: String, url: String, after: String) = buildAnnotatedString {
        append(before)
        withLink(LinkAnnotation.Url(url, link)) { append(label) }
        append(after)
    }
    val body = MaterialTheme.typography.bodySmall
    // collapsed like the license texts (Manfred)
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Hide terms & privacy" else "Terms & privacy") }
    if (!open) return
    Text("Privacy", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    for (p in WEB_PRIVACY) Text(p, style = body, modifier = Modifier.padding(top = 4.dp))
    Text(
        linked(WEB_HOSTING_BEFORE, "privacy statement", GITHUB_PRIVACY_URL, WEB_HOSTING_AFTER),
        style = body, modifier = Modifier.padding(top = 4.dp),
    )
    Text("Terms", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Text(
        linked(WEB_TERMS_BEFORE, "please let me know", feedbackMailto(), "!"),
        style = body, modifier = Modifier.padding(top = 4.dp),
    )
}

private val WEB_PRIVACY = listOf(
    "I don't collect anything. This site has no server of its own, no ads, no analytics and no trackers, and it " +
        "sets no cookies. Your browser downloads the scripts, and then everything runs locally on your machine. " +
        "Whatever you do on this site stays on your computer.",
    "Your preferred settings are kept in your browser's local storage, so they're still there next time. They never " +
        "leave your computer. Clearing your browser data removes them.",
    "The app is built with Compose Multiplatform. Compose would like to fetch extra fonts from Google when you type " +
        "characters its built-in fonts don't know (emoji, Chinese, ...). That's switched off here: everything this " +
        "page loads comes from GitHub Pages, so such characters just show up as boxes.",
)

private const val WEB_HOSTING_BEFORE = "The site is hosted on GitHub Pages. Like most web hosts, GitHub keeps server logs " +
    "that include visitors' IP addresses, for security purposes; details are in GitHub's "
private const val WEB_HOSTING_AFTER = ". I don't have access to these logs. You might also download the source and run it locally."
private const val GITHUB_PRIVACY_URL = "https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement"
private const val WEB_TERMS_BEFORE = "There is no guarantee of correctness or availability. I'll do my best to keep this site " +
    "running and to fix problems. If you experience problems or have general feedback or ideas, "

private const val ABOUT = "RotSysView shows rotation systems of complete graphs K_n (simple drawings), draws them as " +
    "pyrslib's draw2.sage and rotsys-sat's rotsys_draw.sage do (planarization, iterated Tutte embedding, B-splines) " +
    "and lets you change them by flips."

private val PAPERS = listOf(
    "H. Bergold, M. Scheucher: Investigating Simple Drawings of K_n using SAT (2025).",
    "H. Bergold, S. Felsner, M. M. Reddy, J. Orthaber, M. Scheucher: Plane Hamiltonian Cycles in Convex Drawings (SoCG 2024).",
    "H. Bergold, S. Felsner, M. M. Reddy, M. Scheucher: Using SAT to study plane Hamiltonian substructures in simple drawings (2023).",
    "O. Aichholzer, T. Hackl, A. Pilz, P. Ramos, V. Sacristán, B. Vogtenhuber: Empty triangles in good drawings of the complete graph. Graphs and Combinatorics 31 (2015).",
    "J. Kynčl: Simple realizability of complete abstract topological graphs simplified. Discrete & Computational Geometry 64 (2020).",
)

private val PAPER_LINKS = listOf(
    "Bergold, Scheucher at arXiv" to "https://arxiv.org/abs/2504.02650",
    "Bergold et al., convex drawings, at arXiv" to "https://arxiv.org/abs/2403.12898",
    "Bergold et al., plane Hamiltonian substructures, at arXiv" to "https://arxiv.org/abs/2305.09432",
    "rotsys-sat on GitHub" to "https://github.com/manfredscheucher/rotsys-sat",
    "Aichholzer et al., empty triangles, at Springer" to "https://link.springer.com/article/10.1007/s00373-015-1550-5",
    "Number of simple drawings of K_n, OEIS A276110" to "https://oeis.org/A276110",
)

/** "sha | committed <date> | built <date>" (GeneratedBuildInfo, written at build time); unknown parts are left out. */
private fun buildLine(): String = buildList {
    val sha = GeneratedBuildInfo.COMMIT_SHA
    if (sha != "unknown") add(if (GeneratedBuildInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha)
    if (GeneratedBuildInfo.COMMIT_DATE != "unknown") add("committed ${GeneratedBuildInfo.COMMIT_DATE}")
    add("built ${GeneratedBuildInfo.BUILD_DATE}")
}.joinToString(" | ")

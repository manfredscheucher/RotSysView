package org.bytefred.rotsysview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.bytefred.rotsysview.core.ExampleFamily
import org.bytefred.rotsysview.core.ExampleGroup
import org.bytefred.rotsysview.core.ExampleSet
import org.bytefred.rotsysview.core.ExampleSupergroup
import org.bytefred.rotsysview.core.Examples

/**
 * What the Examples dialog remembers between openings ([AppState.examples]): the expanded supergroups
 * and groups (everything starts collapsed), the selected set, the chosen member per set and the scroll
 * position. Reopening shows the tree exactly as it was left.
 */
class ExamplesState {
    val expanded = mutableStateListOf<String>()
    /** The selected set, or null (then maybe [family] is selected). */
    var selected by mutableStateOf<ExampleSet?>(null)
    var family by mutableStateOf<ExampleFamily?>(null)

    /** The chosen n per family. */
    val familyN = mutableStateMapOf<ExampleFamily, Int>()

    fun nOf(f: ExampleFamily): Int = familyN[f] ?: DEFAULT_FAMILY_N

    /** The chosen member (0-based) per set name; 0 if none was chosen. */
    val index = mutableStateMapOf<String, Int>()
    val scroll = ScrollState(0)

    fun indexOf(set: ExampleSet): Int = index[set.name] ?: 0

    /** Expands or collapses the node [key]; collapsing a node that holds the selection clears it (as ArrView, PoCoView). */
    fun toggle(key: String) {
        if (key !in expanded) expanded.add(key)
        else {
            expanded.remove(key)
            if (selected?.let { key in treeKeys(it) } == true) selected = null
            if (family?.let { key in treeKeys(it) } == true) family = null
        }
    }
}

/** The keys of the tree nodes that hold [set]: collapsing one of them hides it. */
private fun treeKeys(set: ExampleSet): List<String> = listOf(treeKey(set.group.supergroup), treeKey(set.group))

private fun treeKeys(f: ExampleFamily): List<String> = Examples.supergroup(f.supergroupId).let { listOf(treeKey(it), familiesKey(it)) }

/** The tree keys, as ArrView's and PoCoView's: "SG/<supergroup>", "G/<supergroup>/<group>" (also for the families). */
internal fun treeKey(sg: ExampleSupergroup): String = "SG/${sg.id}"

internal fun treeKey(group: ExampleGroup): String = "G/${group.supergroup.id}/${group.id}"

internal fun familiesKey(sg: ExampleSupergroup): String = "G/${sg.id}/families"

/**
 * The bundled examples ([Examples]) as a tree: supergroups, their groups, each set a leaf. The
 * selected set shows its description, size and, for a list, a chooser for the member. Load replaces the rotation system with
 * it (after "Save changes?", [AppState.leave]). The data is compiled in, so nothing has to load.
 */
@Composable
fun ExamplesDialog(state: AppState, close: () -> Unit) {
    val ex = state.examples
    val open = ex.expanded
    // a typed member number out of range: Load waits until it is fixed
    var indexError by remember { mutableStateOf(false) }
    fun load() {
        ex.family?.let { f ->
            val n = ex.nOf(f)
            state.leave { state.load("${f.id}-n$n", f.generate(n)); close() }
            return
        }
        val set = ex.selected ?: return
        val i = ex.indexOf(set)
        // the whole set, shown at member i (< > steps through it)
        state.leave { state.loadSet(set.name, RsSet(set.size, set::rotationSystem), i); close() }
    }
    BigDialog(
        "Examples",
        onDismiss = close,
        buttons = {
            TextButton(onClick = close) { Text("Cancel") }
            TextButton(onClick = ::load, enabled = (ex.selected != null || ex.family != null) && !indexError) { Text("Load") }
        },
    ) {
        ScrollColumn(Modifier.fillMaxSize(), ex.scroll) {
            FeedbackBox("Know a rotation system that belongs here, or have general feedback?")
            Spacer(Modifier.height(4.dp))
            // two levels: the supergroups (Database, SAT framework), then their groups; an open node
            // starts with what it is. The generated families (with n) are a supergroup's first group.
            for (sg in Examples.supergroups) {
                TreeHeader(sg.title, 0, treeKey(sg) in open, top = true) { ex.toggle(treeKey(sg)) }
                HorizontalDivider()
                if (treeKey(sg) !in open) continue
                SupergroupIntro(sg)
                val families = Examples.familiesIn(sg)
                if (families.isNotEmpty()) {
                    TreeHeader(FAMILIES, 1, familiesKey(sg) in open) { ex.toggle(familiesKey(sg)) }
                    HorizontalDivider(Modifier.padding(start = INDENT))
                    if (familiesKey(sg) in open) for (f in families) {
                        TreeLeaf(f.title, 2, ex.family == f) {
                            if (ex.family != f) indexError = false
                            ex.family = f
                            ex.selected = null
                        }
                        if (ex.family == f) FamilyDetails(state, f, ex.nOf(f)) { n ->
                            if (n != null) ex.familyN[f] = n
                            indexError = n == null
                        }
                    }
                }
                for (group in Examples.groupsIn(sg)) {
                    TreeHeader(group.title, 1, treeKey(group) in open) { ex.toggle(treeKey(group)) }
                    HorizontalDivider(Modifier.padding(start = INDENT))
                    if (treeKey(group) !in open) continue
                    GroupIntro(group)
                    for (set in Examples.inGroup(group)) {
                        TreeLeaf(leafLabel(set), 2, ex.selected == set) {
                            // the same leaf again keeps its (maybe invalid) number field
                            if (ex.selected != set) indexError = false
                            ex.selected = set
                            ex.family = null
                        }
                        if (ex.selected == set) key(set.name) {
                            ExampleDetails(state, set, ex.indexOf(set), 2) { i ->
                                if (i != null) ex.index[set.name] = i
                                indexError = i == null
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The text of a leaf: the label and, for a list, how many rotation systems it has. */
internal fun leafLabel(set: ExampleSet): String = if (set.size == 1) set.label else "${set.label} (${set.size})"

/** The description and links of an open supergroup, above its groups. */
@Composable
private fun SupergroupIntro(sg: ExampleSupergroup) {
    Column(
        Modifier.padding(start = INDENT + 8.dp, bottom = 4.dp).testTag("supergroup-info"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sg.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        for (l in sg.links.distinctBy { it.url }) LinkText(l.title, l.url, MaterialTheme.typography.labelSmall)
    }
}

/** The description and links of an open group, above its sets; its links are left out only if their URLs are exactly the supergroup's. */
@Composable
private fun GroupIntro(group: ExampleGroup) {
    Column(
        Modifier.padding(start = INDENT * 2 + 8.dp, bottom = 4.dp).testTag("group-info"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        group.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val own = group.links.map { it.url }.distinct()
        if (own != group.supergroup.links.map { it.url }.distinct()) {
            for (l in group.links.distinctBy { it.url }) LinkText(l.title, l.url, MaterialTheme.typography.labelSmall)
        }
    }
}

/** The details of the selected family: its n (typed or with − and +), description and paper. [onN] gets null while n is invalid. */
@Composable
private fun FamilyDetails(state: AppState, f: ExampleFamily, n: Int, onN: (Int?) -> Unit) {
    Column(
        Modifier.padding(start = INDENT * 2 + 48.dp, bottom = 8.dp).testTag("family-info"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(f.description, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("n =", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
            CompactNumberField(
                state, n.toString(), { it.toIntOrNull()?.takeIf { k -> k in f.minN..f.maxN } },
                "${f.minN} to ${f.maxN}", f.maxN.toString().length, "family-n",
                Steppers({ onN(n - 1) }, n > f.minN, { onN(n + 1) }, n < f.maxN),
            ) { k -> onN(k) }
        }
        LinkText(f.link.title, f.link.url, MaterialTheme.typography.labelSmall)
        Text("Generated (rotsys-sat rotsys.py)", style = MaterialTheme.typography.labelSmall)
    }
}

/** The top node of the families. */
private const val FAMILIES = "Families"

/** The n a family starts with. */
private const val DEFAULT_FAMILY_N = 6


/** Indentation per tree level. */
private val INDENT = 16.dp

/**
 * A clickable, expand/collapse node at [depth] with a drawn arrow (no glyph: the web font lacks them).
 * A supergroup ([top]) has the larger title font.
 */
@Composable
private fun TreeHeader(label: String, depth: Int, open: Boolean, top: Boolean = false, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(start = INDENT * depth).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TreeArrow(open)
        Text(
            label,
            style = if (top) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A triangle pointing right (collapsed) or down (expanded). */
@Composable
private fun TreeArrow(open: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (open) {
                moveTo(0.05f * w, 0.25f * h)
                lineTo(0.95f * w, 0.25f * h)
                lineTo(0.5f * w, 0.85f * h)
            } else {
                moveTo(0.25f * w, 0.05f * h)
                lineTo(0.85f * w, 0.5f * h)
                lineTo(0.25f * w, 0.95f * h)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** An example leaf: a radio button; a click selects it. */
@Composable
private fun TreeLeaf(name: String, depth: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = INDENT * depth)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The info of the selected set, as PoCoView's: description, n and the number of rotation systems (for a
 * list the member number, typed or with − and +) and where its data is from (the paper is the
 * group's). [onIndex] gets the 0-based member, or null while the typed number is
 * invalid.
 */
@Composable
private fun ExampleDetails(state: AppState, set: ExampleSet, index: Int, depth: Int, onIndex: (Int?) -> Unit) {
    Column(
        Modifier.padding(start = INDENT * depth + 48.dp, bottom = 8.dp).testTag("example-info"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (set.description.isNotEmpty()) Text(set.description, style = MaterialTheme.typography.bodySmall)
        Text(
            "n = ${set.n}, " + if (set.size == 1) "1 rotation system" else "${set.size} rotation systems",
            style = MaterialTheme.typography.bodySmall,
        )
        if (set.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField(
                state, (index + 1).toString(), { it.toIntOrNull()?.takeIf { i -> i in 1..set.size } },
                "1 to ${set.size}", set.size.toString().length, "example-index",
                Steppers({ onIndex(index - 1) }, index > 0, { onIndex(index + 1) }, index < set.size - 1),
            ) { i -> onIndex(i?.minus(1)) }
            Text("of ${set.size}", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
        val shown = (set.group.links + set.group.supergroup.links).map { it.url }.toSet()
        for (l in set.links.filter { it.url !in shown }.distinctBy { it.url }) LinkText(l.title, l.url, MaterialTheme.typography.labelSmall)
        Text(
            set.source?.let { "Data: $it" } ?: "Computed",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

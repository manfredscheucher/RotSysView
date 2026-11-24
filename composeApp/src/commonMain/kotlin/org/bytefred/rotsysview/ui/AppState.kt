package org.bytefred.rotsysview.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.bytefred.rotsysview.core.DrawStyle
import org.bytefred.rotsysview.core.Examples
import org.bytefred.rotsysview.core.FileFormat
import org.bytefred.rotsysview.core.Flip
import org.bytefred.rotsysview.core.InvalidRotationSystemException
import org.bytefred.rotsysview.core.IteratedTutte
import org.bytefred.rotsysview.core.LayoutSettings
import org.bytefred.rotsysview.core.NamedRotationSystem
import org.bytefred.rotsysview.core.Planarization
import org.bytefred.rotsysview.core.PlanarizationMethod
import org.bytefred.rotsysview.core.RotDrawing
import org.bytefred.rotsysview.core.RotLayout
import org.bytefred.rotsysview.core.RotationSystem
import org.bytefred.rotsysview.core.TextFormat
import org.bytefred.rotsysview.core.cornerFace
import org.bytefred.rotsysview.core.defaultOuterFace
import org.bytefred.rotsysview.core.faceKey
import org.bytefred.rotsysview.core.flipped
import org.bytefred.rotsysview.core.obstruction
import org.bytefred.rotsysview.core.planarizeDraw2
import org.bytefred.rotsysview.core.planarizeRouting
import org.bytefred.rotsysview.core.planarizeSat
import org.bytefred.rotsysview.core.rotationSystemsFile
import org.bytefred.rotsysview.core.readRotationSystems
import org.bytefred.rotsysview.core.validFlips
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The panels next to the canvas; each one is a column that can be shown or hidden. */
enum class Widget(val label: String) {
    PROPERTIES("Properties"),
    ENCODING("Encoding"),
    CROSSINGS("Crossings"),
    FLIPS("Flips"),
    SETTINGS("Settings"),
}

/** The widget stored under [id]. */
internal fun widgetById(id: String): Widget? = Widget.entries.firstOrNull { it.name == id }

enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }

/**
 * A status message (shown in the toolbar). A [transient] one (pure info such as "Flipped") disappears
 * after [AppState.INFO_DURATION_MS]; the others stay until the next message.
 */
class StatusMessage(val text: String, val transient: Boolean)

/** The rotation systems of a loaded file or example set (PoCoView's sets), decoded when first shown. */
class RsSet(val size: Int, private val member: (Int) -> RotationSystem) {
    private val decoded = HashMap<Int, RotationSystem>()

    operator fun get(i: Int): RotationSystem = decoded.getOrPut(i) { member(i) }

    companion object {
        fun of(all: List<RotationSystem>) = RsSet(all.size) { all[it] }
    }
}

/** One step of the undo history. */
private class HistoryEntry(
    val rs: RotationSystem,
    val name: String,
    val outerKey: String?,
    val set: RsSet,
    val setIndex: Int,
    val edits: Map<Int, RotationSystem>,
)

/** What a drawing is computed from; equal keys give equal drawings (the layout cache). */
private data class LayoutKey(val rs: RotationSystem, val style: DrawStyle, val method: PlanarizationMethod, val outerKey: String?, val settings: LayoutSettings)

/**
 * All GUI state and actions. The drawing (planarization, then iterated Tutte embedding) is computed
 * in a cancellable background job whenever the rotation system, the style, the outer face or the
 * layout settings change; until it is done the canvas says so (after a change of the outer face or the
 * layout settings the previous drawing stays).
 */
class AppState(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val layoutContext: CoroutineContext = Dispatchers.Default,
) {
    var rs by mutableStateOf(Examples.start.rotationSystem())
        private set

    /** Name of the loaded rotation system or set (an example name or a file name). */
    var name by mutableStateOf(Examples.start.set.name)
        private set

    // the whole start set, as Examples > Load gives it
    var rsSet by mutableStateOf(Examples.start.set.let { RsSet(it.size, it::rotationSystem) })
        private set

    var setIndex by mutableIntStateOf(Examples.start.index)
        private set

    /** Edited members of the set other than the shown one. */
    private var edits by mutableStateOf<Map<Int, RotationSystem>>(emptyMap())

    /** Members as last saved where they differ from the loaded ones. */
    private var savedEdits by mutableStateOf<Map<Int, RotationSystem>>(emptyMap())

    private val loaded: RotationSystem get() = rsSet[setIndex]

    val title: String
        get() {
            val base = if (rsSet.size > 1) "$name · set ${setIndex + 1}/${rsSet.size}" else name
            return if (rs == loaded) base else "$base (modified)"
        }

    private fun member(i: Int): RotationSystem = if (i == setIndex) rs else edits[i] ?: rsSet[i]

    private fun savedMember(i: Int): RotationSystem = savedEdits[i] ?: rsSet[i]

    /** The drawing program imitated (Settings widget). */
    var style by mutableStateOf(DrawStyle.DRAW2)
        private set

    /** How the planarization is found (Settings; Manfred, 2026-11-20: routing by default). */
    var method by mutableStateOf(PlanarizationMethod.ROUTING)
        private set

    fun changeMethod(m: PlanarizationMethod) {
        if (m == method) return
        method = m
        relayout()
    }

    /** The chosen outer face ([faceKey]), null for the default. */
    private var outerKey by mutableStateOf<String?>(null)

    val outerFace: Int? get() = currentDrawing?.outerFace

    var colorScheme by mutableStateOf(ColorScheme.DEFAULT)

    var lineWidth by mutableFloatStateOf(DEFAULT_LINE_WIDTH)

    /** Vertex numbers next to the vertices. */
    var showLabels by mutableStateOf(true)

    var layoutSettings by mutableStateOf(LayoutSettings(maxIterations = style.maxIterations))
        private set

    val settingsAreDefault: Boolean
        get() = colorScheme == ColorScheme.DEFAULT && lineWidth == DEFAULT_LINE_WIDTH && maxColumns == DEFAULT_MAX_WIDGETS &&
            style == DrawStyle.DRAW2 && method == PlanarizationMethod.ROUTING && layoutSettings == LayoutSettings(maxIterations = DrawStyle.DRAW2.maxIterations) && showLabels

    fun resetSettings() {
        colorScheme = ColorScheme.DEFAULT
        lineWidth = DEFAULT_LINE_WIDTH
        showLabels = true
        changeMaxColumns(DEFAULT_MAX_WIDGETS)
        // as a style change in Settings: the outer face back to the default, fit (one relayout)
        style = DrawStyle.DRAW2
        outerKey = null
        method = PlanarizationMethod.ROUTING
        layoutSettings = LayoutSettings(maxIterations = style.maxIterations)
        relayout()
        requestFit()
    }

    /** The drawing if it shows the current rotation system and style. */
    val currentDrawing: RotDrawing? get() = drawing?.takeIf { it.rs == rs && it.style == style }

    var drawing by mutableStateOf<RotDrawing?>(null)
        private set

    /** Why there is no drawing of the current rotation system (not drawable), or null. */
    var drawError by mutableStateOf<String?>(null)
        private set

    /** The layout is being computed. */
    var computing by mutableStateOf(false)
        private set

    /** The face under the mouse (of the drawing's planarization). */
    var hoverFace by mutableStateOf<Int?>(null)

    /** Faces and edges highlighted while the pointer is on a menu or list entry. */
    var menuHighlight by mutableStateOf<Highlight?>(null)

    private var flipsCache: Pair<RotationSystem, List<Flip>>? = null

    /**
     * The flips whose result is drawable (computed once per rotation system); none if the system is not
     * drawable (no drawing, so no triangles to flip).
     */
    val flips: List<Flip>
        get() = flipsCache?.takeIf { it.first == rs }?.second
            ?: (if (rs.obstruction() != null) emptyList() else rs.validFlips()).also { flipsCache = rs to it }

    /** The faces of the current drawing that each flip touches: the corners at a (b, c) and at b (c, a). */
    fun flipFaces(flip: Flip): Set<Int> {
        val d = currentDrawing ?: return emptySet()
        val p = d.planarization
        return setOf(cornerFace(p, flip.a, flip.b, flip.c), cornerFace(p, flip.b, flip.c, flip.a)).filter { it >= 0 }.toSet()
    }

    /** The flips touching face [f] of the current drawing. */
    fun flipsAt(f: Int): List<Flip> = flips.filter { f in flipFaces(it) }

    var textFieldsFocused by mutableIntStateOf(0)

    var openMenu by mutableStateOf<String?>(null)

    val shortcutsActive: Boolean get() = textFieldsFocused == 0 && openMenu == null

    private var menuClosedAt: TimeMark? = null

    fun closeMenuByOutsideClick() {
        menuClosedAt = TimeSource.Monotonic.markNow()
        openMenu = null
    }

    fun consumeMenuPress(): Boolean {
        val justClosed = menuClosedAt?.let { it.elapsedNow() < MENU_CLICK_GUARD } == true
        menuClosedAt = null
        return openMenu != null || justClosed
    }

    var layoutMode by mutableStateOf(LayoutMode.AUTO)

    var wideWeights by mutableStateOf<Map<Widget?, Float>>(emptyMap())
    var stackedWeights by mutableStateOf<Map<Widget?, Float>>(emptyMap())

    val openWidgets = mutableStateListOf(Widget.PROPERTIES, Widget.FLIPS)

    /** At most this many widget columns are open; opening one more closes the least recently clicked (PoCoView's). */
    var maxColumns by mutableIntStateOf(DEFAULT_MAX_WIDGETS)
        private set

    private val lastClicked = mutableMapOf<Widget, Long>()
    private var clickCounter = 0L

    init {
        for (w in openWidgets) touchWidget(w)
    }

    /** A click into a widget column (or opening it): it becomes the most recently used one. */
    fun touchWidget(w: Widget) {
        lastClicked[w] = ++clickCounter
    }

    fun toggleWidget(w: Widget) {
        if (w in openWidgets) {
            openWidgets.remove(w)
        } else {
            openWidgets.add(w)
            touchWidget(w)
            closeExtraWidgets()
        }
    }

    fun changeMaxColumns(k: Int) {
        maxColumns = k.coerceIn(1, Widget.entries.size)
        closeExtraWidgets()
    }

    private fun closeExtraWidgets() {
        while (openWidgets.size > maxColumns) openWidgets.remove(openWidgets.minBy { lastClicked[it] ?: 0L })
    }

    var status by mutableStateOf<StatusMessage?>(null)
        private set

    var error by mutableStateOf<String?>(null)

    var helpPage by mutableStateOf(HelpPage.GENERAL)

    val examples = ExamplesState()

    /** Unapplied edits of the Encoding widget, per format. */
    val drafts = mutableStateMapOf<TextFormat, String>()

    var fitRequest by mutableIntStateOf(0)
        private set

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val cache = LinkedHashMap<LayoutKey, RotDrawing>()
    private val planarizations = LinkedHashMap<Pair<RotationSystem, PlanarizationMethod>, Planarization?>()
    private var layoutJob: Job? = null

    init {
        loadDisplayPrefs()
        relayout()
    }

    // ---- remembered display settings ---------------------------------------------------

    private fun displayPrefs(): Map<String, String> = mapOf(
        LAYOUT_PREF to layoutMode.name,
        OPEN_WIDGETS_PREF to openWidgets.joinToString(",") { it.name },
        WIDE_WEIGHTS_PREF to encodeWeights(wideWeights),
        STACKED_WEIGHTS_PREF to encodeWeights(stackedWeights),
        MAX_WIDGETS_PREF to maxColumns.toString(),
        STYLE_PREF to style.name,
        METHOD_PREF to method.name,
        MAX_ITERATIONS_PREF to layoutSettings.maxIterations.toString(),
        COLOR_SCHEME_PREF to colorScheme.name,
        LINE_WIDTH_PREF to lineWidth.toString(),
        LABELS_PREF to showLabels.toString(),
        EPS_PREF to layoutSettings.eps.toString(),
    )

    private var savedDisplay: Map<String, String> = emptyMap()

    fun saveDisplayPrefs() {
        val now = displayPrefs()
        for ((key, value) in now) if (savedDisplay[key] != value) savePref(key, value)
        savedDisplay = now
    }

    suspend fun keepDisplayPrefs() {
        snapshotFlow { displayPrefs() }.collectLatest {
            delay(PREF_SAVE_DELAY_MS)
            saveDisplayPrefs()
        }
    }

    private fun loadDisplayPrefs() {
        enumPref<LayoutMode>(LAYOUT_PREF)?.let { layoutMode = it }
        loadPref(OPEN_WIDGETS_PREF)?.let { stored ->
            val open = stored.split(',').mapNotNull(::widgetById).distinct()
            openWidgets.clear()
            openWidgets.addAll(open)
            for (w in open) touchWidget(w)
        }
        loadPref(WIDE_WEIGHTS_PREF)?.let { wideWeights = decodeWeights(it) }
        loadPref(STACKED_WEIGHTS_PREF)?.let { stackedWeights = decodeWeights(it) }
        loadPref(MAX_WIDGETS_PREF)?.toIntOrNull()?.let { changeMaxColumns(it) }
        closeExtraWidgets()
        enumPref<DrawStyle>(STYLE_PREF)?.let { style = it; layoutSettings = LayoutSettings(maxIterations = it.maxIterations) }
        enumPref<PlanarizationMethod>(METHOD_PREF)?.let { method = it }
        loadPref(MAX_ITERATIONS_PREF)?.toIntOrNull()?.takeIf { it >= 1 }?.let { layoutSettings = layoutSettings.copy(maxIterations = it) }
        enumPref<ColorScheme>(COLOR_SCHEME_PREF)?.let { colorScheme = it }
        loadPref(LINE_WIDTH_PREF)?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { lineWidth = it.coerceIn(LINE_WIDTH_RANGE) }
        loadPref(LABELS_PREF)?.toBooleanStrictOrNull()?.let { showLabels = it }
        loadPref(EPS_PREF)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.let { layoutSettings = layoutSettings.copy(eps = it) }
        savedDisplay = displayPrefs()
    }

    // ---- status ------------------------------------------------------------------------

    fun showInfo(text: String) {
        val m = StatusMessage(text, transient = true)
        status = m
        scope.launch {
            delay(INFO_DURATION_MS)
            if (status === m) status = null
        }
    }

    fun showNote(text: String?) {
        status = text?.let { StatusMessage(it, transient = false) }
    }

    // ---- view --------------------------------------------------------------------------

    fun requestFit() {
        fitRequest++
    }

    /** Switches the drawing program (Settings); its number of iterations comes with it. */
    fun changeStyle(s: DrawStyle) {
        if (s == style) return
        style = s
        layoutSettings = layoutSettings.copy(maxIterations = s.maxIterations)
        outerKey = null
        relayout()
        requestFit()
    }

    fun changeLayoutSettings(settings: LayoutSettings) {
        layoutSettings = settings
        relayout()
    }

    /** Draws face [f] of the current drawing outside (undoable). */
    fun makeOuterFace(f: Int) {
        val d = currentDrawing ?: return
        if (f == d.outerFace || f !in d.planarization.faces.indices) return
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
        outerKey = faceKey(d.planarization.faces[f])
        relayout()
    }

    private fun relayout() {
        layoutJob?.cancel()
        val key = LayoutKey(rs, style, method, outerKey, layoutSettings)
        cache[key]?.let { drawing = it; drawError = null; computing = false; return }
        computing = true
        drawError = null
        layoutJob = scope.launch {
            val result = try {
                withContext(layoutContext) { computeDrawing(key) }
            } catch (e: IllegalArgumentException) {
                drawError = "Cannot draw: ${e.message}"
                computing = false
                return@launch
            }
            computing = false
            if (result == null) return@launch
            cache[key] = result
            while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
            drawError = null
            drawing = result
        }
    }

    /** The planarization of [rs] by [method], cached; null if none is found. */
    private fun planarization(rs: RotationSystem, method: PlanarizationMethod, job: Job?): Planarization? = planarizations.getOrPut(rs to method) {
        if (rs.n < 4) throw IllegalArgumentException("n must be at least 4")
        rs.obstruction()?.let { throw IllegalArgumentException("not drawable, it contains $it") }
        val start = TimeSource.Monotonic.markNow()
        var timedOut = false
        fun stop(): Boolean {
            if (start.elapsedNow().inWholeMilliseconds > ROUTING_BUDGET_MS) timedOut = true
            return timedOut || job?.isActive == false
        }
        when (method) {
            PlanarizationMethod.ROUTING -> planarizeRouting(rs, ::stop) ?: when {
                job?.isActive == false -> throw CancellationException()
                timedOut -> throw IllegalArgumentException(
                    "routing found no planarization within ${ROUTING_BUDGET_MS / 1000} s",
                )
                else -> null
            }
            PlanarizationMethod.SAT -> planarizeSat(rs)
            PlanarizationMethod.DRAW2 -> planarizeDraw2(rs, cancelled = { job?.isActive == false }) ?: if (job?.isActive == false) throw CancellationException() else null
        }
    }.also { while (planarizations.size > CACHE_SIZE) planarizations.remove(planarizations.keys.first()) }

    private suspend fun computeDrawing(key: LayoutKey): RotDrawing? {
        val p = planarization(key.rs, key.method, currentCoroutineContext()[Job]) ?: throw IllegalArgumentException("no planarization found")
        val outer = key.outerKey?.let { k -> p.faces.indexOfFirst { faceKey(it) == k }.takeIf { it >= 0 } }
            ?: defaultOuterFace(p, key.style)
        val layout = RotLayout(p, outer)
        val tutte = IteratedTutte(layout.problem, key.settings)
        while (!tutte.step()) yield()
        return layout.drawing(tutte.positions, key.style)
    }

    suspend fun awaitLayout() {
        layoutJob?.join()
    }

    // ---- editing -----------------------------------------------------------------------

    private fun snapshot() = HistoryEntry(rs, name, outerKey, rsSet, setIndex, edits)

    private fun restore(e: HistoryEntry) {
        rs = e.rs
        name = e.name
        outerKey = e.outerKey
        rsSet = e.set
        setIndex = e.setIndex
        edits = e.edits
        clearHighlights()
        relayout()
    }

    private fun clearHighlights() {
        hoverFace = null
        menuHighlight = null
    }

    /** Replaces the rotation system (undoable) and shows [info]. */
    private fun change(next: RotationSystem, info: String) {
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
        rs = next
        // the planarization's numbering changes: the outer face goes back to the default
        outerKey = null
        clearHighlights()
        relayout()
        showInfo(info)
    }

    private fun updateUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        updateUndoFlags()
        restore(prev)
        status = null
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        updateUndoFlags()
        restore(next)
        status = null
    }

    fun load(name: String, rs: RotationSystem) = loadSet(name, RsSet.of(listOf(rs)))

    /** The number of rotation systems just loaded, for the sets notice (null: none to show). */
    var loadedSetsNotice by mutableStateOf<Int?>(null)

    fun loadSet(name: String, set: RsSet, start: Int = 0) {
        require(start in 0 until set.size) { "no member ${start + 1} in ${set.size}" }
        loadedSetsNotice = set.size.takeIf { it > 1 }
        undoStack.addLast(snapshot())
        redoStack.clear()
        updateUndoFlags()
        restore(HistoryEntry(set[start], name, null, set, start, emptyMap()))
        savedEdits = emptyMap()
        drafts.clear()
        status = null
        requestFit()
    }

    fun showSet(index: Int) {
        if (index !in 0 until rsSet.size || index == setIndex) return
        if (unappliedDraft) pendingLeave = { switchToSet(index) } else switchToSet(index)
    }

    private fun switchToSet(index: Int) {
        val next = member(index)
        edits = storedEdits()
        setIndex = index
        rs = next
        outerKey = null
        clearHighlights()
        drafts.clear()
        undoStack.clear()
        redoStack.clear()
        updateUndoFlags()
        status = null
        relayout()
        requestFit()
    }

    private fun storedEdits(): Map<Int, RotationSystem> =
        if (rs == rsSet[setIndex]) edits - setIndex else edits + (setIndex to rs)

    fun open(fileName: String, bytes: ByteArray) {
        try {
            val read = readRotationSystems(bytes.decodeToString())
            loadSet(read.singleOrNull()?.name ?: fileName.substringBeforeLast('.'), RsSet.of(read.map { it.rs }))
            showInfo("Opened $fileName" + if (read.size > 1) ": ${read.size} rotation systems" else "")
        } catch (e: InvalidRotationSystemException) {
            error = "Cannot open $fileName: ${e.message}"
        }
    }

    fun parseImport(text: String): List<NamedRotationSystem>? =
        try {
            readRotationSystems(text)
        } catch (e: InvalidRotationSystemException) {
            error = "Cannot import the text: ${e.message}"
            null
        }

    fun loadImport(read: List<NamedRotationSystem>) =
        loadSet(read.singleOrNull()?.name ?: "imported", RsSet.of(read.map { it.rs }))

    val fileName: String get() = name.replace(Regex("[^A-Za-z0-9._#-]+"), "_")

    fun saveName(format: FileFormat): String = "$fileName.${format.extension}"

    // ---- unsaved changes ---------------------------------------------------------------

    val rsUnsaved: Boolean
        get() = (edits.keys + savedEdits.keys + setIndex).any { member(it) != savedMember(it) }

    val unappliedDraft: Boolean
        get() = drafts.any { (format, text) -> text != text(format) }

    val unsavedChanges: Boolean get() = rsUnsaved || unappliedDraft

    var pendingLeave by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun leave(action: () -> Unit) {
        if (unsavedChanges) pendingLeave = action else action()
    }

    fun discardAndLeave() {
        val action = pendingLeave ?: return
        pendingLeave = null
        action()
    }

    fun cancelLeave() {
        pendingLeave = null
    }

    private var saving: List<RotationSystem>? = null

    fun beginSave(format: FileFormat): ByteArray {
        val all = (0 until rsSet.size).map(::member)
        saving = all
        return rotationSystemsFile(name, all, format).encodeToByteArray()
    }

    fun fileSaved(fileName: String?) {
        val done = saving
        saving = null
        if (fileName == null) {
            cancelLeave()
            return
        }
        if (done != null) savedEdits = done.withIndex().filter { (i, r) -> r != rsSet[i] }.associate { it.index to it.value }
        showInfo("Saved $fileName")
        if (!unsavedChanges) discardAndLeave()
    }

    // ---- text forms ----------------------------------------------------------------------

    fun text(format: TextFormat): String = format.write(rs)

    fun editDraft(format: TextFormat, text: String) {
        if (text == text(format)) drafts.remove(format) else drafts[format] = text
    }

    fun revertDraft(format: TextFormat) {
        drafts.remove(format)
    }

    fun applyDraft(format: TextFormat) {
        val text = drafts[format] ?: return
        val next = try {
            format.read(text)
        } catch (e: InvalidRotationSystemException) {
            error = "Cannot apply the ${format.noun}: ${e.message}"
            return
        }
        drafts.remove(format)
        if (next == rs) return
        change(next, "Applied the ${format.noun}")
    }

    /** Relabels to pyrslib's lex-min labelling (undoable). */
    fun relabel(next: RotationSystem, info: String) {
        if (next == rs) {
            showInfo("Already in this labelling")
            return
        }
        change(next, info)
    }

    // ---- flips ---------------------------------------------------------------------------

    /** Applies [flip] (undoable). */
    fun flip(flip: Flip) {
        if (flip !in flips) return
        change(rs.flipped(flip), "Flipped $flip")
    }

    /** A click on face [f]: its only flip is done; true if there are several (the canvas opens a menu). */
    fun clickFace(f: Int): Boolean {
        val at = flipsAt(f)
        when {
            at.size == 1 -> flip(at.single())
            at.size >= 2 -> return true
        }
        return false
    }

    companion object {
        const val INFO_DURATION_MS = 3000L
        private const val PREF_SAVE_DELAY_MS = 500L
        private const val MAX_UNDO = 500
        const val DEFAULT_LINE_WIDTH = 5f
        val LINE_WIDTH_RANGE = 0.5f..8f
        const val DEFAULT_MAX_WIDGETS = 2
        private const val CACHE_SIZE = 32

        /** Routing gives up after this long (it backtracks; a few large examples take too long). */
        private const val ROUTING_BUDGET_MS = 15_000L
    }
}

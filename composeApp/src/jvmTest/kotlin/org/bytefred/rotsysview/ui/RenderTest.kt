package org.bytefred.rotsysview.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.rotsysview.core.DrawStyle
import org.bytefred.rotsysview.core.ExampleFamily
import org.bytefred.rotsysview.core.Examples
import org.bytefred.rotsysview.core.Pattern
import org.bytefred.rotsysview.core.RotationSystem
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Renders the app offscreen (crashes become failures) and writes screenshots to composeApp/build/screenshots/. */
class RenderTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun render(state: AppState, file: String, w: Int = 1200, h: Int = 800, dialog: DialogKind = DialogKind.NONE) {
        runBlocking { state.awaitLayout() }
        val scene = ImageComposeScene(w, h, Density(1f)) { App(state, initialDialog = dialog) }
        try {
            for (t in 0L until 4L) scene.render(t * 50_000_000L).close()
            val image = scene.render(400_000_000L)
            val dir = File("build/screenshots").apply { mkdirs() }
            dir.resolve(file).writeBytes(assertNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            image.close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun startBothStylesAndWidgets() {
        val s = state()
        render(s, "start-draw2.png")
        assertNotNull(s.currentDrawing)
        s.changeStyle(DrawStyle.ROTSYS)
        render(s, "start-rotsys.png")
        for (w in Widget.entries) {
            s.openWidgets.clear()
            s.openWidgets.add(w)
            render(s, "widget-${w.name.lowercase()}.png")
        }
        render(s, "portrait.png", w = 420, h = 860)
    }

    @Test
    fun flipChangesTheSystemAndUndoRestoresIt() {
        val s = state()
        runBlocking { s.awaitLayout() }
        val before = s.rs
        val f = s.flips.first()
        s.flip(f)
        assertNotEquals(before, s.rs)
        render(s, "after-flip.png")
        assertNotNull(s.currentDrawing)
        s.undo()
        assertEquals(before, s.rs)
    }

    @Test
    fun largerExamplesAndNonDrawable() {
        for (name in listOf("n7-all", "n11-hconvex-all-edges-crossed", "n16-no-C5-T6", "n12-gtwisted-no-T8")) {
            val s = state()
            val set = Examples.byName(name)!!
            s.loadSet(name, RsSet(set.size, set::rotationSystem), 0)
            // the sets notice once, then the drawing alone
            if (name == "n7-all") render(s, "sets-notice.png")
            s.loadedSetsNotice = null
            render(s, "$name.png")
            assertNotNull(s.currentDrawing, name)
        }
        for (f in ExampleFamily.entries) for (n in listOf(9, f.maxN)) {
            val s = state()
            s.load("${f.id}-n$n", f.generate(n))
            render(s, "${f.id}-n$n.png")
            assertNotNull(s.currentDrawing, "${f.id} n=$n")
        }
        val s = state()
        s.load("pi51", RotationSystem(Pattern.PI51.rows))
        render(s, "not-drawable.png")
        assertTrue(s.drawError?.contains("Π5,1o") == true, s.drawError)
        // no drawing, so no triangles to flip
        assertTrue(s.flips.isEmpty())
    }

    @Test
    fun dialogs() {
        for (d in listOf(DialogKind.WELCOME, DialogKind.EXAMPLES, DialogKind.ABOUT, DialogKind.IMPORT, DialogKind.EXPORT)) {
            render(state(), "dialog-${d.name.lowercase()}.png", dialog = d)
        }
        // the examples tree with both supergroups open (doc/img/examples.png)
        val s = state()
        for (sg in Examples.supergroups) s.examples.toggle(treeKey(sg))
        render(s, "dialog-examples-tree.png", dialog = DialogKind.EXAMPLES)
        // a group open with a selected set, and the families with one selected
        for (key in listOf(treeKey(Examples.group("all")), familiesKey(Examples.supergroup("sat-framework")))) s.examples.toggle(key)
        s.examples.selected = Examples.byName("n6-all")
        render(s, "dialog-examples-selected.png", dialog = DialogKind.EXAMPLES)
        for (p in HelpPage.entries) {
            val s = state()
            s.helpPage = p
            render(s, "help-${p.name.lowercase()}.png", dialog = DialogKind.HELP)
        }
    }
}

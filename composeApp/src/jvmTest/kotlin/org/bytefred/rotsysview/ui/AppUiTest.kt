package org.bytefred.rotsysview.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.rotsysview.core.ExampleFamily
import org.bytefred.rotsysview.core.Examples
import org.bytefred.rotsysview.core.flipped
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The real app driven by clicks and typing. */
@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun clickAFlipInTheListThenUndo() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }
        val before = s.rs
        val f = s.flips.first()
        onNodeWithTag("flip-${f.a}-${f.b}-${f.c}").performClick()
        waitForIdle()
        assertEquals(before.flipped(f), s.rs)
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }
        onNodeWithText("Edit").performClick()
        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertEquals(before, s.rs)
    }

    @Test
    fun widgetsStartSetAndMaxWidgets() = runComposeUiTest {
        val s = state()
        // the start is the whole set n6-all
        assertEquals(102, s.rsSet.size)
        assertEquals(0, s.setIndex)
        // a third widget closes the least recently clicked one
        s.touchWidget(Widget.PROPERTIES)
        s.toggleWidget(Widget.SETTINGS)
        assertEquals(listOf(Widget.PROPERTIES, Widget.SETTINGS), s.openWidgets.toList())
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithTag("max-widgets-plus").performClick()
        waitForIdle()
        assertEquals(3, s.maxColumns)
        s.toggleWidget(Widget.FLIPS)
        assertEquals(3, s.openWidgets.size)
    }

    @Test
    fun setsNotice() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        s.loadSet("two", RsSet.of(listOf(s.rs, s.rs)))
        waitForIdle()
        onNodeWithText("2 rotation systems loaded").assertIsDisplayed()
        onNodeWithText("Don't show again").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertNull(s.loadedSetsNotice)
        // turned off: the next set loads without it
        s.loadSet("two", RsSet.of(listOf(s.rs, s.rs)))
        waitForIdle()
        assertNull(s.loadedSetsNotice)
    }

    @Test
    fun keyboardShortcuts() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }
        val before = s.rs
        s.flip(s.flips.first())
        waitForIdle()
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        waitForIdle()
        assertEquals(before, s.rs)
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) } } }
        waitForIdle()
        assertEquals(false, before == s.rs)
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        onNodeWithText("Save JSON").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        // also while a text field has focus
        onNodeWithText("Encoding").performClick()
        onNodeWithTag("editor-ROWS").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        onNodeWithText("Save JSON").assertIsDisplayed()
    }

    @Test
    fun applyRowsValidAndInvalid() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Encoding").performClick()
        val perfectTwisted = "1 2 3 4 5\n0 5 4 3 2\n0 1 5 4 3\n0 1 2 5 4\n0 1 2 3 5\n0 1 2 3 4"
        onNodeWithTag("editor-ROWS").performTextReplacement(perfectTwisted)
        onNodeWithTag("apply-ROWS").performClick()
        waitForIdle()
        assertEquals(perfectTwisted, s.rs.toText())
        onNodeWithTag("editor-ROWS").performTextReplacement("1 2\n0 2\n0")
        onNodeWithTag("apply-ROWS").performClick()
        onNodeWithText("Error").assertIsDisplayed()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertNull(s.error)
        assertEquals(perfectTwisted, s.rs.toText())
    }

    @Test
    fun menusAndDialogsOpen() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Rotation system").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("SAT framework examples").performClick()
        onNodeWithText("Families").performClick()
        // the tree keys as ArrView's and PoCoView's
        assertEquals(listOf("SG/sat-framework", "G/sat-framework/families"), s.examples.expanded.toList())
        onNodeWithText("Perfect twisted T_n").performClick()
        onNodeWithText("Load").performClick()
        waitUntil(timeoutMillis = 10_000) { s.rs.n == 6 && s.currentDrawing != null }
        assertEquals(ExampleFamily.PERFECT_TWISTED.generate(6), s.rs)
        onNodeWithText("Rotation system").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("Every edge crossed").assertIsDisplayed()
        // collapsing the supergroup that holds the selection clears it
        onNodeWithText("SAT framework examples").performClick()
        assertNull(s.examples.family)
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Help").performClick()
        onNodeWithText("Visualization...").performClick()
        onNodeWithText("Iterated Tutte", substring = true).assertIsDisplayed()
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }
        assertNotNull(s.currentDrawing)
    }

    @Test
    fun examplesTreeSupergroupsGroupsLoad() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Rotation system").performClick()
        onNodeWithText("Examples...").performClick()
        // Database > All drawings of small K_n > n5-all: the whole set of 6
        onNodeWithText("Database").performClick()
        onNodeWithText("Complete lists for small n.").assertIsDisplayed()
        onNodeWithText("All drawings of small K_n").performClick()
        // the sources show with the selected set, not with the group
        onNodeWithText("Paper: Aichholzer", substring = true).assertDoesNotExist()
        onNodeWithText("n5-all (5)").performClick()
        onNodeWithTag("example-info").assertIsDisplayed()
        onNodeWithText("Paper: Aichholzer", substring = true).assertIsDisplayed()
        onNodeWithText("Data: Balogh, Lidický, Salazar", substring = true).assertIsDisplayed()
        // collapsing the group clears the selection, Load is disabled again
        onNodeWithText("All drawings of small K_n").performClick()
        assertNull(s.examples.selected)
        onNodeWithText("Load").assertIsNotEnabled()
        onNodeWithText("All drawings of small K_n").performClick()
        onNodeWithText("n5-all (5)").performClick()
        onNodeWithText("Load").performClick()
        waitUntil(timeoutMillis = 10_000) { s.rs.n == 5 && s.currentDrawing != null }
        assertEquals(5, s.rsSet.size)
        onNodeWithText("5 rotation systems loaded").assertIsDisplayed()
        onNodeWithText("OK").performClick()
        // SAT framework examples > Empty triangles > n8-unlucky, with its own papers
        onNodeWithText("Rotation system").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("SAT framework examples").performClick()
        onNodeWithText("Empty triangles").performScrollTo().performClick()
        onNodeWithText("Paper: Bergold, Felsner", substring = true).assertDoesNotExist()
        onNodeWithText("n8-unlucky").performScrollTo().performClick()
        onNodeWithText("Paper: Bergold, Felsner", substring = true).assertExists()
        onNodeWithText("Load").performClick()
        waitUntil(timeoutMillis = 10_000) { s.rs.n == 8 && s.currentDrawing != null }
        assertEquals(Examples.byName("n8-unlucky")!!.rotationSystem(0), s.rs)
        // the tree is as it was left (both groups open, n8-unlucky selected)
        onNodeWithText("Rotation system").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("n5-all (5)").assertExists()
        onNodeWithText("n8-unlucky").assertIsDisplayed()
        onNodeWithTag("example-info").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
    }
}

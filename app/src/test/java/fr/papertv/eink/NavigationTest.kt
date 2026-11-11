package fr.papertv.eink

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import fr.papertv.eink.data.Channels
import fr.papertv.eink.data.PrimeTimeEntry
import fr.papertv.eink.data.Programme
import fr.papertv.eink.ui.COLOR
import fr.papertv.eink.ui.EinkTheme
import fr.papertv.eink.ui.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Retour arrière depuis une fiche : on retrouve la même position dans la liste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h509dp-port-hdpi")
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 11)
    private val state = UiState(
        loading = false, today = today, selected = today, availableDates = listOf(today),
        entries = Channels.ALL.mapIndexed { i, c ->
            PrimeTimeEntry(c, Programme(c.id, 0, 3_600_000, "Programme $i", description = "Résumé $i"), null)
        },
    )

    @Test
    fun backReturnsToSamePosition() {
        compose.setContent { EinkTheme { App(state, onSelectDate = {}, onRefresh = {}) } }
        if (COLOR) {
            repeat(4) { compose.onRoot().performTouchInput { swipeUp(startY = centerY + 120f, endY = top + 60f) } }
        } else {
            repeat(3) { compose.onNodeWithText("Suiv. ›").performClick() }
        }
        compose.waitForIdle()
        val target = if (COLOR) firstVisible() else "Programme 9"
        compose.onNodeWithText(target).performClick()
        compose.onNodeWithText("‹ Retour").assertIsDisplayed()
        // Bouton retour du système.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(target).assertIsDisplayed()
        compose.onNodeWithText("Programme 0").assertDoesNotExist()
    }

    private fun firstVisible(): String =
        (5 until state.entries.size).map { "Programme $it" }
            .first { runCatching { compose.onNodeWithText(it).assertIsDisplayed() }.isSuccess }
}

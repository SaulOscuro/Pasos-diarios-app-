package com.ostwaldviz.pasosdiarios

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyTrackerUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun clearProgress() {
        rule.activity.getSharedPreferences("pasos_diarios_v1", Context.MODE_PRIVATE).edit().clear().commit()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun changeGoalPreservesChecksAndSurvivesActivityRecreation() {
        screenshot("01-today")
        rule.onNodeWithTag("block-0").performClick()
        openSettings("6", "500")
        screenshot("02-settings")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithTag("total-steps").assertTextEquals("500")
        rule.onNodeWithText("Meta: 3,000 pasos").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("block-0").assertIsOn()
        rule.onNodeWithTag("total-steps").assertTextEquals("500")
        screenshot("03-custom-goal")
    }

    @Test fun historicalDayShowsItsOriginalGoalAndReadOnlyBlocks() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        rule.activity.getSharedPreferences("pasos_diarios_v1", Context.MODE_PRIVATE).edit().clear()
            .putInt("blocks_$yesterday", 3).putInt("blocks_$today", 0).commit()
        rule.activityRule.scenario.recreate()
        openSettings("6", "500")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithText("Historial", useUnmergedTree = false).performClick()
        rule.onNodeWithText("2,000 / 10,000 pasos").assertExists()
        rule.onNodeWithText("0 / 3,000 pasos").assertExists()
        screenshot("04-history")
        val label = yesterday.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX")))
            .replaceFirstChar { it.uppercaseChar() }
        rule.onNodeWithText(label).performClick()
        rule.onNodeWithTag("total-steps").assertTextEquals("2,000")
        rule.onNodeWithText("Meta: 10,000 pasos").assertExists()
        rule.onAllNodes(isToggleable()).assertCountEquals(0)
        screenshot("05-history-detail")
    }

    @Test fun oneHundredBlocksCanBeConfiguredAndLastBlockCanBeCompleted() {
        openSettings("100", "250")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithText("Meta: 25,000 pasos").assertExists()
        screenshot("06-one-hundred-blocks")
        rule.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("block-99"))
        rule.onNodeWithTag("block-99").performClick().assertIsOn()
        rule.runOnIdle {
            val record = DailyRepository(StepPreferences(rule.activity)).current()
            assertEquals(setOf(99), record.completed)
            assertEquals(250, record.totalSteps)
        }
    }

    @Test fun invalidGoalCannotBeSaved() {
        openSettings("0", "500")
        rule.onNodeWithText("Guardar cambios").assertIsNotEnabled()
        rule.onNodeWithTag("block-count").performTextReplacement("101")
        rule.onNodeWithText("Guardar cambios").assertIsNotEnabled()
        rule.onNodeWithTag("block-count").performTextReplacement("6")
        rule.onNodeWithTag("steps-per-block").performTextReplacement("0")
        rule.onNodeWithText("Guardar cambios").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithText("Meta: 10,000 pasos").assertExists()
    }

    @Test fun removingCompletedBlocksWarnsAndCancelPreservesProgress() {
        val today = LocalDate.now()
        rule.activity.getSharedPreferences("pasos_diarios_v1", Context.MODE_PRIVATE).edit().clear()
            .putInt("blocks_$today", (1 shl 0) or (1 shl 9)).commit()
        rule.activityRule.scenario.recreate()
        openSettings("3", "500")
        rule.onNodeWithText("Al reducir la cantidad se quitará 1 bloque marcado.").assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithTag("total-steps").assertTextEquals("2,000")
        openSettings("3", "500")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithTag("total-steps").assertTextEquals("500")
        rule.runOnIdle {
            assertEquals(setOf(0), DailyRepository(StepPreferences(rule.activity)).current().completed)
        }
    }

    private fun openSettings(blocks: String, steps: String) {
        rule.onNodeWithText("Ajustar bloques y pasos").performClick()
        rule.onNodeWithTag("block-count").performTextReplacement(blocks)
        rule.onNodeWithTag("steps-per-block").performTextReplacement(steps)
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Connected tests uninstall the app afterwards, deleting its external-files directory.
        // Use the test shell to keep screenshots in shared Downloads until CI collects them.
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("mkdir -p /sdcard/Download/pasos-ui")
        ).use { it.readBytes() }
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("screencap -p /sdcard/Download/pasos-ui/$name.png")
        ).use { it.readBytes() }
    }
}

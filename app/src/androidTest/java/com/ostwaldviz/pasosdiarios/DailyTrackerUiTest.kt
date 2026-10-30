package com.ostwaldviz.pasosdiarios

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
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


    @Test fun directGoalEntryAndBlockChangesKeepExactlyTheSameTarget() {
        rule.onNodeWithText("Ajustar meta, bloques y horario").performClick()
        rule.onNodeWithTag("target-steps").performTextReplacement("12000")
        rule.onNodeWithTag("block-count").performTextReplacement("7")
        rule.onNodeWithText("Para alcanzar la meta exacta: 2 bloques de 1,715 pasos y 5 de 1,714.").assertExists()
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithText("Meta: 12,000 pasos").assertExists()
        rule.onNodeWithTag("block-0").performClick().assertIsOn()
        rule.onNodeWithTag("total-steps").assertTextEquals("1,715")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("block-0").assertIsOn()
        rule.onNodeWithTag("total-steps").assertTextEquals("1,715")
        rule.runOnIdle {
            val plan = DailyRepository(StepPreferences(rule.activity)).current().plan
            assertEquals(12_000, plan.targetSteps)
            assertEquals(7, plan.blockCount)
            assertEquals(2, plan.extraSteps)
        }
    }

    @Test fun changingStepsPerBlockRecalculatesTargetInsteadOfKeepingOldGoal() {
        rule.onNodeWithText("Ajustar meta, bloques y horario").performClick()
        rule.onNodeWithTag("target-steps").performTextReplacement("15000")
        rule.onNodeWithTag("steps-per-block").performTextReplacement("2000")
        rule.onNodeWithText("20,000 pasos").assertExists()
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithText("Meta: 20,000 pasos").assertExists()
        rule.onNodeWithTag("block-0").performClick()
        rule.onNodeWithTag("total-steps").assertTextEquals("2,000")
    }


    @Test fun selectedScheduleAppearsInTodaySettingsAndHistoricalDetails() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val repository = DailyRepository(StepPreferences(rule.activity))
        val old = DailyRepository(StepPreferences(rule.activity)) { yesterday }
        old.changePlan(yesterday, StepPlan().withTimeRange(LocalTime.of(7, 0), LocalTime.of(18, 0)))
        repository.changePlan(today, StepPlan().withTimeRange(LocalTime.of(9, 0), LocalTime.of(21, 0)))
        rule.activityRule.scenario.recreate()

        rule.onNodeWithTag("schedule-range").assertExists()
        rule.onNodeWithTag("block-0").assertExists()
        rule.onNodeWithText(LocalTime.of(9, 0).format(
            DateTimeFormatter.ofPattern("h:mm a", Locale("es", "MX"))
        )).assertExists()
        rule.onNodeWithText("Ajustar meta, bloques y horario").performClick()
        rule.onNodeWithTag("start-time").assertHasClickAction()
        rule.onNodeWithTag("end-time").assertHasClickAction()
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.runOnIdle {
            val plan = DailyRepository(StepPreferences(rule.activity)).current().plan
            assertEquals(LocalTime.of(9, 0), plan.startTime)
            assertEquals(LocalTime.of(21, 0), plan.endTime)
            assertEquals(10_000, plan.targetSteps)
        }

        rule.onNodeWithText("Historial").performClick()
        val yesterdayLabel = yesterday.format(DateTimeFormatter.ofPattern(
            "EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX")
        )).replaceFirstChar { it.uppercaseChar() }
        rule.onNodeWithText(yesterdayLabel).performClick()
        rule.runOnIdle {
            val previous = DailyRepository(StepPreferences(rule.activity)).history().last()
            assertEquals(LocalTime.of(7, 0), previous.plan.startTime)
            assertEquals(LocalTime.of(18, 0), previous.plan.endTime)
        }
        screenshot("07-custom-time-range")
    }

    @Test fun editingBlocksAndGoalKeepsPreviouslyChosenTimeWindow() {
        val existing = StepPlan().withTimeRange(LocalTime.of(6, 30), LocalTime.of(19, 15))
        DailyRepository(StepPreferences(rule.activity)).changePlan(LocalDate.now(), existing)
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Ajustar meta, bloques y horario").performClick()
        rule.onNodeWithTag("target-steps").performTextReplacement("12000")
        rule.onNodeWithTag("block-count").performTextReplacement("7")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.runOnIdle {
            val plan = DailyRepository(StepPreferences(rule.activity)).current().plan
            assertEquals(12_000, plan.targetSteps)
            assertEquals(7, plan.blockCount)
            assertEquals(LocalTime.of(6, 30), plan.startTime)
            assertEquals(LocalTime.of(19, 15), plan.endTime)
            assertEquals(LocalTime.of(6, 30), plan.suggestedTime(0))
            assertEquals(LocalTime.of(19, 15), plan.suggestedTime(6))
        }
    }

    private fun openSettings(blocks: String, steps: String) {
        rule.onNodeWithText("Ajustar meta, bloques y horario").performClick()
        rule.onNodeWithTag("block-count").performTextReplacement(blocks)
        rule.onNodeWithTag("steps-per-block").performTextReplacement(steps)
        rule.onNodeWithTag("steps-per-block").performImeAction()
        rule.onNodeWithText("Guardar cambios").assertIsDisplayed()
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

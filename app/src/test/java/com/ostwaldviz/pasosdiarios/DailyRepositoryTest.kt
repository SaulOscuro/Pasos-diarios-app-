package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class DailyRepositoryTest {
    private class MemoryStorage : ProgressStorage {
        val values = mutableMapOf<String, Any>()
        override fun keys() = values.keys.toSet()
        override fun text(key: String) = values[key] as? String
        override fun integer(key: String) = values[key] as? Int
        override fun write(values: Map<String, String>) { this.values.putAll(values) }
    }

    private val storage = MemoryStorage()
    private val firstDay = LocalDate.of(2026, 10, 27)
    private var date = firstDay
    private fun repository() = DailyRepository(storage) { date }

    @Test fun currentDayIsPersistedEvenWithoutCompletingABlock() {
        val record = repository().current()
        assertEquals(record, repository().history().single())
        assertEquals(record, repository().current())
    }

    @Test fun everyTapSurvivesClosingAndReopeningTheApp() {
        repository().toggle(date, 0)
        repository().toggle(date, 9)
        assertEquals(setOf(0, 9), repository().current().completed)
        repository().toggle(date, 0)
        assertEquals(setOf(9), repository().current().completed)
    }

    @Test fun midnightStartsAnEmptyDayAndArchivesThePreviousSnapshot() {
        var now = firstDay.atTime(23, 59, 59)
        val repo = DailyRepository(storage) { now.toLocalDate() }
        repo.changePlan(firstDay, StepPlan(12, 500))
        val yesterday = repo.toggle(firstDay, 11)
        now = now.plusSeconds(1)
        val today = repo.current()
        assertEquals(LocalDateTime.of(2026, 10, 28, 0, 0), now)
        assertEquals(StepPlan(12, 500), today.plan)
        assertTrue(today.completed.isEmpty())
        assertEquals(listOf(today, yesterday), repo.history())
    }

    @Test fun reopeningAfterSeveralClosedDaysRetainsHistoryAndUsesTheLatestPlan() {
        repository().changePlan(date, StepPlan(6, 1_500))
        val original = repository().toggle(date, 5)
        date = date.plusDays(4)
        val today = repository().current()
        assertEquals(StepPlan(6, 1_500), today.plan)
        assertTrue(today.completed.isEmpty())
        // Unused dates are not invented as zero-step days.
        assertEquals(listOf(today, original), repository().history())
    }

    @Test fun changingTodayNeverRecalculatesHistoricalStepsOrGoals() {
        val old = repository().toggle(date, 0)
        date = date.plusDays(1)
        repository().changePlan(date, StepPlan(15, 750))
        val current = repository().toggle(date, 14)
        assertEquals(750, current.totalSteps)
        assertEquals(11_250, current.plan.targetSteps)
        assertEquals(old, repository().history().last())
        assertEquals(1_000, repository().history().last().totalSteps)
    }

    @Test fun changingPlanPersistsDefaultAndDoesNotRestoreRemovedChecksWhenExpanded() {
        repository().toggle(date, 9)
        repository().toggle(date, 2)
        repository().changePlan(date, StepPlan(3, 250))
        assertEquals(setOf(2), repository().current().completed)
        repository().changePlan(date, StepPlan(40, 2_000))
        assertEquals(setOf(2), repository().current().completed)
        date = date.plusDays(1)
        assertEquals(StepPlan(40, 2_000), repository().current().plan)
    }

    @Test fun resetOnlyClearsTodayAndRetainsItsPlanAndPreviousDays() {
        val old = repository().toggle(date, 0)
        date = date.plusDays(1)
        repository().changePlan(date, StepPlan(2, 500))
        repository().toggle(date, 1)
        val reset = repository().reset(date)
        assertEquals(StepPlan(2, 500), reset.plan)
        assertTrue(reset.completed.isEmpty())
        assertEquals(old, repository().history().last())
    }

    @Test fun staleToggleResetAndSettingsAtMidnightCannotChangeTheNewDay() {
        val old = repository().toggle(date, 0)
        date = date.plusDays(1)
        val fresh = repository().current()
        assertEquals(fresh, repository().toggle(firstDay, 0))
        assertEquals(fresh, repository().reset(firstDay))
        assertEquals(fresh, repository().changePlan(firstDay, StepPlan(1, 1)))
        assertEquals(old, repository().history().last())
        date = date.plusDays(1)
        assertEquals(StepPlan(), repository().current().plan)
    }

    @Test fun legacyDaysAreMigratedDeduplicatedAndSortedWithoutChangingProgress() {
        storage.values["blocks_$firstDay"] = (1 shl 1) or (1 shl 9)
        storage.values["blocks_${firstDay.minusDays(2)}"] = 1
        val current = repository().current()
        assertEquals(setOf(1, 9), current.completed)
        repository().changePlan(date, StepPlan(20, 500))
        val history = repository().history()
        assertEquals(2, history.size)
        assertEquals(listOf(firstDay, firstDay.minusDays(2)), history.map { it.date })
        assertEquals(StepPlan(), history.last().plan)
        assertEquals(1_000, history.last().totalSteps)
        assertEquals(history, repository().history())
    }

    @Test fun legacyFallbackProtectsExistingProgressWhenANewRecordIsMalformed() {
        storage.values["blocks_$firstDay"] = 3
        storage.values["day_v2_$firstDay"] = "2|10|1000|99"
        assertEquals(setOf(0, 1), repository().current().completed)
        assertEquals(2_000, repository().history().single().totalSteps)
    }

    @Test fun invalidKeysAndFutureRecordsAreExcludedAndInvalidDefaultsAreSafe() {
        storage.values["blocks_not-a-date"] = 1
        storage.values["unrelated_setting"] = "value"
        storage.values["default_plan_v2"] = "2|0|1000"
        storage.values["day_v2_${date.plusDays(1)}"] = "2|10|1000|0"
        storage.values["day_v2_${date.minusDays(1)}"] = "malformed"
        val current = repository().current()
        assertEquals(StepPlan(), current.plan)
        assertEquals(listOf(current), repository().history())
    }

    @Test fun movingDeviceDateBackRestoresThatDaysOriginalRecord() {
        val original = repository().toggle(date, 9)
        date = date.plusDays(1)
        repository().changePlan(date, StepPlan(1, 500))
        date = firstDay
        assertEquals(original, repository().current())
        assertEquals(listOf(original), repository().history())
    }
    @Test fun exactTargetSurvivesRestartAndFutureDaysWithoutChangingPastHistory() {
        val original = repository().toggle(date, 9)
        date = date.plusDays(1)
        val goal = StepPlan.forTarget(12_000, 7)
        val current = repository().changePlan(date, goal).toggle(0)
        assertEquals(1_715, current.totalSteps)
        assertEquals(goal, repository().current().plan)
        assertEquals(12_000, repository().current().plan.targetSteps)
        assertEquals(original, repository().history().last())
        date = date.plusDays(1)
        assertEquals(goal, repository().current().plan)
        assertTrue(repository().current().completed.isEmpty())
        assertEquals(12_000, repository().current().plan.targetSteps)
        assertEquals(10_000, repository().history().last().plan.targetSteps)
    }

    @Test fun previouslySavedV2PlansAndRecordsStillLoadAndUpgradeToV3() {
        storage.values["day_v2_$firstDay"] = "2|7|1500|0,6"
        storage.values["default_plan_v2"] = "2|7|1500"
        val previous = repository().current()
        assertEquals(StepPlan(7, 1_500), previous.plan)
        assertEquals(3_000, previous.totalSteps)
        val changed = repository().changePlan(date, StepPlan.forTarget(12_000, 7))
        assertEquals(setOf(0, 6), changed.completed)
        assertEquals(3_429, changed.totalSteps)
        assertEquals("4|7|1714|2|08:00|20:00|0,6", storage.values["day_v2_$firstDay"])
        assertEquals("4|7|1714|2|08:00|20:00", storage.values["default_plan_v2"])
        assertEquals(changed, repository().current())
        date = date.plusDays(1)
        assertEquals(changed.plan, repository().current().plan)
    }

    @Test fun scheduleChangesPersistAcrossDaysAndLeavePastRecordsUntouched() {
        val original = repository().toggle(date, 2)
        date = date.plusDays(1)
        val scheduled = StepPlan.forTarget(12_000, 7)
            .withTimeRange(LocalTime.of(7, 30), LocalTime.of(19, 45))
        repository().changePlan(date, scheduled)
        val today = repository().toggle(date, 0)
        assertEquals(1_715, today.totalSteps)
        assertEquals(LocalTime.of(7, 30), today.plan.suggestedTime(0))
        assertEquals(LocalTime.of(19, 45), today.plan.suggestedTime(6))
        assertEquals("4|7|1714|2|07:30|19:45|0", storage.values["day_v2_$date"])
        assertEquals(original, repository().history().last())
        date = date.plusDays(1)
        assertEquals(scheduled, repository().current().plan)
        assertTrue(repository().current().completed.isEmpty())
        assertEquals(StepPlan(), repository().history().last().plan)
    }

    @Test fun olderV3RecordsAndDefaultsKeepTheirOriginalSchedule() {
        storage.values["day_v2_$firstDay"] = "3|7|1714|2|0,6"
        storage.values["default_plan_v2"] = "3|7|1714|2"
        val original = repository().current()
        assertEquals(12_000, original.plan.targetSteps)
        assertEquals(LocalTime.of(8, 0), original.plan.startTime)
        assertEquals(LocalTime.of(20, 0), original.plan.endTime)
        assertEquals(3_429, original.totalSteps)
        date = date.plusDays(1)
        assertEquals(original.plan, repository().current().plan)
        assertEquals(original, repository().history().last())
    }

    @Test fun scheduleOnlyChangePreservesExistingCompletedBlocksAndStepCounts() {
        val before = repository().toggle(date, 4)
        val after = repository().changePlan(date, before.plan.withTimeRange(
            LocalTime.of(9, 15), LocalTime.of(21, 15)
        ))
        assertEquals(before.completed, after.completed)
        assertEquals(before.totalSteps, after.totalSteps)
        assertEquals(before.plan.targetSteps, after.plan.targetSteps)
        assertEquals(LocalTime.of(9, 15), repository().current().plan.startTime)
    }

    @Test fun corruptedOrInvertedStoredScheduleCannotOverrideSavedLegacyData() {
        storage.values["blocks_$firstDay"] = 3
        storage.values["day_v2_$firstDay"] = "4|10|1000|0|20:00|08:00|0"
        assertEquals(setOf(0, 1), repository().current().completed)
        assertEquals(2_000, repository().current().totalSteps)
    }

}

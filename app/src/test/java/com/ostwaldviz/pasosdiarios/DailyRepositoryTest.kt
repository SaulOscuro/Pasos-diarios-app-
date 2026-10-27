package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalDateTime
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
}

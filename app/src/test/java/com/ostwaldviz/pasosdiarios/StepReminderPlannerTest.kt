package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class StepReminderPlannerTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val day = LocalDate.of(2026, 11, 5)

    private fun time(hour: Int, minute: Int = 0) =
        ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)

    @Test fun firstBlockInMorning() {
        val current = DailyProgress(day)
        val next = StepReminderPlanner.next(time(7, 0), current, StepPlan())
        assertEquals(day, next.day)
        assertEquals(0, next.index)
        assertEquals(LocalTime.of(8, 0), next.scheduledAt.toLocalTime())
    }

    @Test fun nextUncompletedBlockAtItsTime() {
        val current = DailyProgress(day, completed = setOf(2, 4))
        val next = StepReminderPlanner.next(time(10, 15), current, StepPlan())
        assertEquals(3, next.index) // 12:30, because block 3 at 11:00 was marked done.
        assertEquals(LocalTime.of(12, 30), next.scheduledAt.toLocalTime())
    }

    @Test fun skipsMissedTimesAndCompletedBlocks() {
        val current = DailyProgress(day, completed = setOf(6))
        val next = StepReminderPlanner.next(time(16, 55), current, StepPlan())
        assertEquals(7, next.index)
        assertEquals(LocalTime.of(18, 0), next.scheduledAt.toLocalTime())
    }

    @Test fun afterLastBlockSchedulesTomorrow() {
        val current = DailyProgress(day)
        val plan = StepPlan.forTarget(15_000, 5,
            LocalTime.of(9, 0), LocalTime.of(21, 0))
        val next = StepReminderPlanner.next(time(21, 0), current, plan)
        assertEquals(day.plusDays(1), next.day)
        assertEquals(0, next.index)
        assertEquals(LocalTime.of(9, 0), next.scheduledAt.toLocalTime())
    }

    @Test fun finishedDaySchedulesTomorrow() {
        val current = DailyProgress(day, completed = (0 until 10).toSet())
        val next = StepReminderPlanner.next(time(8, 45), current, StepPlan())
        assertEquals(day.plusDays(1), next.day)
        assertEquals(0, next.index)
    }

    @Test fun warnsOnlyForMatchingTimeAndIncompleteBlock() {
        val current = DailyProgress(day)
        val occurrence = StepReminder(day, 1, time(9, 30))
        assertTrue(StepReminderPlanner.stillRelevant(occurrence, time(9, 35), current))
        assertFalse(StepReminderPlanner.stillRelevant(occurrence, time(9, 29), current))
        assertFalse(StepReminderPlanner.stillRelevant(occurrence, time(12, 0), current))
        assertFalse(StepReminderPlanner.stillRelevant(
            occurrence, time(9, 35), current.toggle(1)
        ))
        assertFalse(StepReminderPlanner.stillRelevant(
            occurrence.copy(day = day.minusDays(1)), time(9, 35), current
        ))
    }

    @Test fun outdatedScheduleDoesNotNotify() {
        val current = DailyProgress(day, StepPlan().withTimeRange(
            LocalTime.of(10, 0), LocalTime.of(20, 0)
        ))
        val old = StepReminder(day, 0, time(8, 0))
        assertFalse(StepReminderPlanner.stillRelevant(old, time(10, 0), current))
    }
}

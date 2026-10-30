package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class StepTrackerTest {
    private val date = LocalDate.of(2026, 10, 27)

    @Test fun defaultPlanStillHasTenThousandSteps() {
        val record = DailyProgress(date)
        assertEquals(10_000, record.plan.targetSteps)
        assertEquals(0, record.totalSteps)
        assertEquals(0f, record.progress)
    }

    @Test fun customPlanCountsIndependentlySelectedBlocks() {
        val record = DailyProgress(date, StepPlan(12, 500)).toggle(0).toggle(11)
        assertEquals(setOf(0, 11), record.completed)
        assertEquals(1_000, record.totalSteps)
        assertEquals(6_000, record.plan.targetSteps)
        assertEquals(16, record.percent)
        assertEquals(setOf(0), record.toggle(11).completed)
    }

    @Test fun supportsOneHundredBlocksWithoutBitmaskOverflow() {
        val record = (0 until 100).fold(DailyProgress(date, StepPlan(100, 100_000))) { r, i -> r.toggle(i) }
        assertEquals(100, record.completedCount)
        assertEquals(10_000_000, record.totalSteps)
        assertEquals(100, record.percent)
        assertEquals(1f, record.progress)
        assertEquals(99, record.toggle(64).completedCount)
    }

    @Test fun removingBlocksKeepsOnlyCompletedBlocksStillInThePlan() {
        val record = DailyProgress(date).toggle(0).toggle(4).toggle(9).withPlan(StepPlan(5, 250))
        assertEquals(setOf(0, 4), record.completed)
        assertEquals(500, record.totalSteps)
    }

    @Test fun addingBlocksAndChangingStepsPreservesChecks() {
        val record = DailyProgress(date, StepPlan(3, 500)).toggle(2).withPlan(StepPlan(40, 1_500))
        assertEquals(setOf(2), record.completed)
        assertEquals(1_500, record.totalSteps)
        assertEquals(60_000, record.plan.targetSteps)
    }

    @Test fun legacyMasksKeepOriginalPlanAndIgnoreExtraBits() {
        val record = DailyProgress.fromLegacy(date, (1 shl 0) or (1 shl 9) or (1 shl 20))
        assertEquals(setOf(0, 9), record.completed)
        assertEquals(StepPlan(), record.plan)
        assertEquals(2_000, record.totalSteps)
    }

    @Test fun invalidPlansAndBlockIndicesAreRejected() {
        listOf(0 to 1_000, 101 to 1_000, 10 to 0, 10 to 1_000_001).forEach { (blocks, steps) ->
            assertThrows(IllegalArgumentException::class.java) { StepPlan(blocks, steps) }
        }
        assertThrows(IllegalArgumentException::class.java) { DailyProgress(date).toggle(-1) }
        assertThrows(IllegalArgumentException::class.java) { DailyProgress(date).toggle(10) }
        assertThrows(IllegalArgumentException::class.java) { DailyProgress(date, completed = setOf(10)) }
    }

    @Test fun schedulesAreValidForOneDefaultAndManyBlocks() {
        assertEquals(LocalTime.of(8, 0), StepPlan(1, 500).suggestedTime(0))
        assertEquals(LocalTime.of(9, 30), StepPlan().suggestedTime(1))
        for (count in listOf(2, 6, 10, 37, 100)) {
            val times = (0 until count).map { StepPlan(count, 500).suggestedTime(it) }
            assertEquals(LocalTime.of(8, 0), times.first())
            assertEquals(LocalTime.of(20, 0), times.last())
            assertTrue(times.zipWithNext().all { (a, b) -> a < b })
        }
    }
    @Test fun editingGoalKeepsBlockCountAndRedistributesSteps() {
        val plan = StepPlan().withTargetSteps(15_000)
        assertEquals(10, plan.blockCount)
        assertEquals(1_500, plan.stepsPerBlock)
        assertEquals(0, plan.extraSteps)
        assertEquals(15_000, plan.targetSteps)
        assertEquals(15_000, plan.cumulativeSteps(9))
    }

    @Test fun editingBlockCountPreservesExactGoalEvenWhenDivisionHasRemainder() {
        val plan = StepPlan().withTargetSteps(12_000).withBlockCount(7)
        assertEquals(12_000, plan.targetSteps)
        assertEquals(1_714, plan.stepsPerBlock)
        assertEquals(2, plan.extraSteps)
        assertEquals(1_715, plan.stepsForBlock(0))
        assertEquals(1_715, plan.stepsForBlock(1))
        assertEquals(1_714, plan.stepsForBlock(2))
        assertEquals(12_000, plan.cumulativeSteps(6))
        assertEquals(12_000, (0 until 7).sumOf { plan.stepsForBlock(it) })
        val progress = DailyProgress(date, plan).toggle(0).toggle(6)
        assertEquals(3_429, progress.totalSteps)
        assertEquals(28, progress.percent)
    }

    @Test fun editingStepsPerBlockUpdatesGoalAndResetsTheRemainder() {
        val plan = StepPlan.forTarget(12_000, 7).withStepsPerBlock(2_000)
        assertEquals(7, plan.blockCount)
        assertEquals(14_000, plan.targetSteps)
        assertEquals(0, plan.extraSteps)
        assertEquals(2_000, plan.stepsForBlock(6))
        assertEquals(14_000, plan.cumulativeSteps(6))
    }

    @Test fun goalAndBlocksMayBeChangedRepeatedlyWithoutLosingSteps() {
        val plan = StepPlan.forTarget(20_000).withBlockCount(7).withBlockCount(1)
        assertEquals(20_000, plan.targetSteps)
        assertEquals(20_000, plan.stepsForBlock(0))
        assertEquals(20_000, plan.withBlockCount(100).targetSteps)
        assertEquals(100, plan.withBlockCount(100).blockCount)
    }

    @Test fun remainderAndGoalLimitsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { StepPlan(7, 1_000, 7) }
        assertThrows(IllegalArgumentException::class.java) { StepPlan(7, 1_000, -1) }
        assertThrows(IllegalArgumentException::class.java) { StepPlan.forTarget(6, 7) }
        assertThrows(IllegalArgumentException::class.java) { StepPlan.forTarget(10_000_001) }
        assertEquals(10_000_000, StepPlan(100, 100_000).targetSteps)
        assertEquals(10_000_000, StepPlan.forTarget(10_000_000, 1).targetSteps)
    }

}

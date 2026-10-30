package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalTime

/**
 * Uniform step blocks with an optional one-step remainder distributed among the first blocks.
 * This keeps the exact daily goal even when it is not divisible by the number of blocks.
 */
data class StepPlan(
    val blockCount: Int = 10,
    val stepsPerBlock: Int = 1_000,
    val extraSteps: Int = 0
) {
    init {
        require(blockCount in 1..MAX_BLOCKS)
        require(stepsPerBlock in 1..MAX_STEPS_PER_BLOCK)
        require(extraSteps in 0 until blockCount)
        require(blockCount.toLong() * stepsPerBlock + extraSteps <= MAX_TOTAL_STEPS)
    }

    val targetSteps: Int get() = blockCount * stepsPerBlock + extraSteps

    fun stepsForBlock(index: Int): Int {
        require(index in 0 until blockCount)
        return stepsPerBlock + if (index < extraSteps) 1 else 0
    }

    fun cumulativeSteps(index: Int): Int {
        require(index in 0 until blockCount)
        return (index + 1) * stepsPerBlock + minOf(index + 1, extraSteps)
    }

    /** Changing the goal or the block count preserves the exact total. */
    fun withTargetSteps(target: Int): StepPlan = forTarget(target, blockCount)
    fun withBlockCount(count: Int): StepPlan = forTarget(targetSteps, count)

    /** Manually changing steps per block replaces any remainder and recalculates the total. */
    fun withStepsPerBlock(steps: Int): StepPlan = StepPlan(blockCount, steps)

    fun suggestedTime(index: Int): LocalTime {
        require(index in 0 until blockCount)
        // Keep the original schedule for the default ten-block plan.
        val minutes = if (blockCount == 10) {
            listOf(480, 570, 660, 750, 840, 930, 1_020, 1_080, 1_140, 1_200)[index]
        } else if (blockCount == 1) {
            480
        } else {
            480 + 720 * index / (blockCount - 1)
        }
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    companion object {
        const val MAX_BLOCKS = 100
        // Retain the old maximum possible goal (100 blocks x 100,000 steps).
        const val MAX_TOTAL_STEPS = 10_000_000
        const val MAX_STEPS_PER_BLOCK = MAX_TOTAL_STEPS

        fun forTarget(targetSteps: Int, blockCount: Int = 10): StepPlan {
            require(blockCount in 1..MAX_BLOCKS)
            require(targetSteps in blockCount..MAX_TOTAL_STEPS)
            return StepPlan(blockCount, targetSteps / blockCount, targetSteps % blockCount)
        }
    }
}

data class DailyProgress(
    val date: LocalDate,
    val plan: StepPlan = StepPlan(),
    val completed: Set<Int> = emptySet()
) {
    init {
        require(completed.all { it in 0 until plan.blockCount })
    }

    val completedCount: Int get() = completed.size
    val totalSteps: Int get() = completed.sumOf(plan::stepsForBlock)
    val progress: Float get() = totalSteps.toFloat() / plan.targetSteps
    val percent: Int get() = totalSteps * 100 / plan.targetSteps

    fun toggle(index: Int): DailyProgress {
        require(index in 0 until plan.blockCount)
        return copy(completed = if (index in completed) completed - index else completed + index)
    }

    fun withPlan(newPlan: StepPlan): DailyProgress =
        copy(plan = newPlan, completed = completed.filterTo(mutableSetOf()) { it < newPlan.blockCount })

    companion object {
        fun fromLegacy(date: LocalDate, mask: Int): DailyProgress = DailyProgress(
            date = date,
            completed = (0 until 10).filterTo(mutableSetOf()) { mask and (1 shl it) != 0 }
        )
    }
}

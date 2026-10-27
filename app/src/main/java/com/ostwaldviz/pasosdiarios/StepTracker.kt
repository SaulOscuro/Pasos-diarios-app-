package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalTime

data class StepPlan(val blockCount: Int = 10, val stepsPerBlock: Int = 1_000) {
    init {
        require(blockCount in 1..MAX_BLOCKS)
        require(stepsPerBlock in 1..MAX_STEPS_PER_BLOCK)
    }

    val targetSteps: Int get() = blockCount * stepsPerBlock

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
        const val MAX_STEPS_PER_BLOCK = 100_000
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
    val totalSteps: Int get() = completedCount * plan.stepsPerBlock
    val progress: Float get() = completedCount.toFloat() / plan.blockCount
    val percent: Int get() = completedCount * 100 / plan.blockCount

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

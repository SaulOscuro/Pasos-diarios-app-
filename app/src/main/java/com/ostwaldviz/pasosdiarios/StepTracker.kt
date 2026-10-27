package com.ostwaldviz.pasosdiarios

import android.content.Context
import java.time.LocalDate

/**
 * Ten independently selectable 1,000-step blocks.
 * Bits 0..9 represent the day's completed sessions.
 */
object StepTracker {
    const val BLOCK_COUNT = 10
    const val STEPS_PER_BLOCK = 1_000
    private const val VALID_BITS = (1 shl BLOCK_COUNT) - 1

    fun normalized(mask: Int): Int = mask and VALID_BITS

    fun isCompleted(mask: Int, index: Int): Boolean {
        require(index in 0 until BLOCK_COUNT)
        return normalized(mask) and (1 shl index) != 0
    }

    fun toggle(mask: Int, index: Int): Int {
        require(index in 0 until BLOCK_COUNT)
        return normalized(mask) xor (1 shl index)
    }

    fun completedCount(mask: Int): Int = Integer.bitCount(normalized(mask))

    fun steps(mask: Int): Int = completedCount(mask) * STEPS_PER_BLOCK

    fun progress(mask: Int): Float = completedCount(mask).toFloat() / BLOCK_COUNT
}

/**
 * Each local calendar day has its own persisted bitmask. No account, network,
 * step sensor, or permissions are required.
 */
class StepPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "pasos_diarios_v1",
        Context.MODE_PRIVATE
    )

    private fun key(date: LocalDate): String = "blocks_${date}"

    fun load(date: LocalDate): Int =
        StepTracker.normalized(prefs.getInt(key(date), 0))

    fun save(date: LocalDate, mask: Int) {
        prefs.edit().putInt(key(date), StepTracker.normalized(mask)).apply()
    }
}

package com.ostwaldviz.pasosdiarios

import java.time.LocalDate

interface ProgressStorage {
    fun keys(): Set<String>
    fun text(key: String): String?
    fun integer(key: String): Int?
    fun write(values: Map<String, String>)
}

/** Pure repository so migration, clock changes, and history can be tested without Android. */
class DailyRepository(
    private val storage: ProgressStorage,
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    private val defaultPlanKey = "default_plan_v2"
    private fun recordKey(date: LocalDate) = "day_v2_$date"

    fun current(): DailyProgress {
        val date = today()
        return load(date) ?: DailyProgress(date, defaultPlan()).also { save(it) }
    }

    fun history(): List<DailyProgress> {
        val currentDate = today()
        return storage.keys().mapNotNull { key ->
            val dateText = when {
                key.startsWith("day_v2_") -> key.removePrefix("day_v2_")
                key.startsWith("blocks_") -> key.removePrefix("blocks_")
                else -> return@mapNotNull null
            }
            runCatching { LocalDate.parse(dateText) }.getOrNull()
        }.distinct().filter { it <= currentDate }.sortedDescending().mapNotNull { load(it) }
    }

    // A tap/dialog belonging to yesterday must never modify today's fresh record.
    fun toggle(expectedDate: LocalDate, index: Int): DailyProgress {
        val record = current()
        if (record.date != expectedDate || index !in 0 until record.plan.blockCount) return record
        return record.toggle(index).also { save(it) }
    }

    fun reset(expectedDate: LocalDate): DailyProgress {
        val record = current()
        if (record.date != expectedDate) return record
        return record.copy(completed = emptySet()).also { save(it) }
    }

    fun changePlan(expectedDate: LocalDate, plan: StepPlan): DailyProgress {
        val record = current()
        if (record.date != expectedDate) return record
        return record.withPlan(plan).also {
            storage.write(mapOf(recordKey(it.date) to encode(it), defaultPlanKey to encodePlan(plan)))
        }
    }

    private fun defaultPlan(): StepPlan = decodePlan(storage.text(defaultPlanKey)) ?: StepPlan()

    private fun load(date: LocalDate): DailyProgress? {
        decode(date, storage.text(recordKey(date)))?.let { return it }
        return storage.integer("blocks_$date")?.let { mask ->
            DailyProgress.fromLegacy(date, mask).also { save(it) }
        }
    }

    private fun save(record: DailyProgress) {
        storage.write(mapOf(recordKey(record.date) to encode(record)))
    }

    // Versioned, dependency-free format: version|count|steps[|completed indices].
    private fun encodePlan(plan: StepPlan) = "2|${plan.blockCount}|${plan.stepsPerBlock}"
    private fun encode(record: DailyProgress) =
        "${encodePlan(record.plan)}|${record.completed.sorted().joinToString(",")}"

    private fun decodePlan(value: String?): StepPlan? = runCatching {
        val parts = requireNotNull(value).split('|')
        require(parts.size == 3 && parts[0] == "2")
        StepPlan(parts[1].toInt(), parts[2].toInt())
    }.getOrNull()

    private fun decode(date: LocalDate, value: String?): DailyProgress? = runCatching {
        val parts = requireNotNull(value).split('|')
        require(parts.size == 4)
        val plan = requireNotNull(decodePlan(parts.take(3).joinToString("|")))
        val completed = if (parts[3].isEmpty()) emptySet() else parts[3].split(',').map { it.toInt() }.toSet()
        DailyProgress(date, plan, completed)
    }.getOrNull()
}

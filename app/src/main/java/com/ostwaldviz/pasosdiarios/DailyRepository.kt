package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalTime

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

    /** Add or correct yesterday (or any past day) without touching today's progress. */
    fun recordPastSteps(date: LocalDate, steps: Int): DailyProgress {
        require(date.isBefore(today())) { "Selecciona un día anterior a hoy." }
        require(steps in 0..StepPlan.MAX_TOTAL_STEPS)
        val record = (load(date) ?: DailyProgress(date, defaultPlan())).withRecordedSteps(steps)
        save(record)
        return record
    }

    /** Correct old blocks independently; tapping clears an exact manual-step override. */
    fun togglePastBlock(date: LocalDate, index: Int): DailyProgress {
        require(date.isBefore(today())) { "Los bloques de hoy se editan en Hoy." }
        val record = requireNotNull(load(date)) { "El día no tiene un registro." }
        return record.toggle(index).also { save(it) }
    }

    /** The default schedule used when a new day starts. */
    fun reminderPlanForTomorrow(): StepPlan = defaultPlan()

    fun exportBackup(): String = BackupArchive.encode(defaultPlan(), history())

    /** Merge by date; imported matching dates replace their old records after UI confirmation. */
    fun restoreBackup(backup: ProgressBackup) {
        require(backup.days.size <= 10_000)
        require(backup.days.map { it.date }.distinct().size == backup.days.size)
        require(backup.days.all { !it.date.isAfter(today()) }) {
            "El respaldo contiene fechas futuras."
        }
        val changes = mutableMapOf(defaultPlanKey to encodePlan(backup.defaultPlan))
        backup.days.forEach { changes[recordKey(it.date)] = encode(it) }
        storage.write(changes)
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

    // Keep original keys for in-place upgrades. A v4 record adds start/end times.
    // Legacy masks, v2 and v3 plans continue to decode with their original schedule.
    private fun encodePlan(plan: StepPlan) =
        "4|${plan.blockCount}|${plan.stepsPerBlock}|${plan.extraSteps}|${plan.startTime}|${plan.endTime}"

    private fun encode(record: DailyProgress): String {
        val plan = encodePlan(record.plan)
        val blocks = record.completed.sorted().joinToString(",")
        return if (record.manualSteps == null) "${plan}|${blocks}"
        else "${plan.replaceFirst("4|", "5|")}|${blocks}|${record.manualSteps}"
    }

    private fun decodePlan(value: String?): StepPlan? = runCatching {
        val parts = requireNotNull(value).split('|')
        when {
            parts.size == 3 && parts[0] == "2" ->
                StepPlan(parts[1].toInt(), parts[2].toInt())
            parts.size == 4 && parts[0] == "3" ->
                StepPlan(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
            parts.size == 6 && parts[0] == "4" ->
                StepPlan(parts[1].toInt(), parts[2].toInt(), parts[3].toInt(),
                    LocalTime.parse(parts[4]), LocalTime.parse(parts[5]))
            else -> error("Unsupported plan version")
        }
    }.getOrNull()

    private fun decode(date: LocalDate, value: String?): DailyProgress? = runCatching {
        val parts = requireNotNull(value).split('|')
        val version = parts.firstOrNull()
        val planFields = when (version) {
            "2" -> 3
            "3" -> 4
            "4", "5" -> 6
            else -> error("Unsupported record version")
        }
        require(parts.size == planFields + (if (version == "5") 2 else 1))
        val encodedPlan = parts.take(planFields).toMutableList()
        if (version == "5") encodedPlan[0] = "4"
        val plan = requireNotNull(decodePlan(encodedPlan.joinToString("|")))
        val selected = parts[planFields]
        val completed = if (selected.isEmpty()) emptySet()
            else selected.split(',').map { it.toInt() }.toSet()
        val manualSteps = if (version == "5") parts[planFields + 1].toInt() else null
        DailyProgress(date, plan, completed, manualSteps)
    }.getOrNull()
}

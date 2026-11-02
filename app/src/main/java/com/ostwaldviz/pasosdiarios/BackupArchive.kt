package com.ostwaldviz.pasosdiarios

import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/** Portable, human-readable backup. It never includes Android-specific storage keys. */
data class ProgressBackup(val defaultPlan: StepPlan, val days: List<DailyProgress>)

object BackupArchive {
    private const val FORMAT = "pasos-diarios"
    private const val VERSION = 1
    private const val MAX_DAYS = 10_000

    fun encode(defaultPlan: StepPlan, days: List<DailyProgress>): String {
        val records = JSONArray()
        days.sortedBy { it.date }.forEach { day ->
            records.put(JSONObject().apply {
                put("date", day.date.toString())
                put("plan", planToJson(day.plan))
                put("checkedBlocks", JSONArray().apply { day.completed.sorted().forEach { put(it) } })
                put("manualSteps", day.manualSteps ?: JSONObject.NULL)
                put("totalSteps", day.totalSteps)
            })
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedOn", LocalDate.now().toString())
            put("defaultPlan", planToJson(defaultPlan))
            put("days", records)
        }.toString(2)
    }

    /** All records are validated before the caller is allowed to write anything. */
    fun decode(content: String): ProgressBackup = try {
        require(content.length <= 2_000_000) { "El archivo es demasiado grande." }
        val root = JSONObject(content)
        require(root.getString("format") == FORMAT) { "No es un respaldo de Pasos Diarios." }
        require(root.getInt("version") == VERSION) { "La versión del respaldo no es compatible." }
        val defaultPlan = planFromJson(root.getJSONObject("defaultPlan"))
        val daysJson = root.getJSONArray("days")
        require(daysJson.length() <= MAX_DAYS) { "Demasiados días en el respaldo." }
        val days = (0 until daysJson.length()).map { index ->
            val entry = daysJson.getJSONObject(index)
            val plan = planFromJson(entry.getJSONObject("plan"))
            val checked = entry.getJSONArray("checkedBlocks")
            val indices = (0 until checked.length()).map { checked.getInt(it) }
            require(indices.size == indices.toSet().size) { "Hay bloques duplicados." }
            val exactSteps = if (entry.isNull("manualSteps")) null else entry.getInt("manualSteps")
            val record = DailyProgress(
                date = LocalDate.parse(entry.getString("date")),
                plan = plan,
                completed = indices.toSet(),
                manualSteps = exactSteps
            )
            require(record.totalSteps == entry.getInt("totalSteps")) { "Los pasos no coinciden." }
            record
        }
        require(days.map { it.date }.distinct().size == days.size) { "Hay fechas duplicadas." }
        ProgressBackup(defaultPlan, days)
    } catch (error: Exception) {
        throw IllegalArgumentException("No se pudo leer el respaldo: ${error.message}", error)
    }

    private fun planToJson(plan: StepPlan): JSONObject = JSONObject().apply {
        put("targetSteps", plan.targetSteps)
        put("blockCount", plan.blockCount)
        put("stepsPerBlock", plan.stepsPerBlock)
        put("extraSteps", plan.extraSteps)
        put("startTime", plan.startTime.toString())
        put("endTime", plan.endTime.toString())
    }

    private fun planFromJson(value: JSONObject): StepPlan {
        val plan = StepPlan(
            blockCount = value.getInt("blockCount"),
            stepsPerBlock = value.getInt("stepsPerBlock"),
            extraSteps = value.getInt("extraSteps"),
            startTime = LocalTime.parse(value.getString("startTime")),
            endTime = LocalTime.parse(value.getString("endTime"))
        )
        require(plan.targetSteps == value.getInt("targetSteps")) { "La meta es inconsistente." }
        return plan
    }
}

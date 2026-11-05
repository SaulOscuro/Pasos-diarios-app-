package com.ostwaldviz.pasosdiarios

import android.content.Context

class StepPreferences(context: Context) : ProgressStorage {
    // Reuse the v1 file so an in-place update can migrate existing days.
    private val prefs = context.applicationContext.getSharedPreferences(
        "pasos_diarios_v1", Context.MODE_PRIVATE
    )

    override fun keys(): Set<String> = prefs.all.keys.toSet()
    override fun text(key: String): String? = prefs.all[key] as? String
    override fun integer(key: String): Int? = prefs.all[key] as? Int

    override fun write(values: Map<String, String>) {
        val editor = prefs.edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }
}

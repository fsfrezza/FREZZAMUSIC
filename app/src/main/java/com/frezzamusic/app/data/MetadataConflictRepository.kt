package com.frezzamusic.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent three-way comparison between source metadata and user overrides. */
class MetadataConflictRepository(context: Context) {
    private val prefs = context.getSharedPreferences("metadata_reconciliation", Context.MODE_PRIVATE)

    data class Difference(val entityId: String, val field: String, val previousSource: String?,
                          val incomingSource: String?, val userValue: String)

    private fun key(prefix: String, id: String, field: String) =
        prefix + ":" + id.length + ":" + id + ":" + field.length + ":" + field

    fun editedValue(id: String, field: String): String? =
        prefs.getString(key("edit", id, field), null)

    fun saveEdit(id: String, field: String, value: String) {
        prefs.edit().putString(key("edit", id, field), value).apply()
    }

    fun effectiveValue(id: String, field: String, source: String?): String? =
        editedValue(id, field) ?: source

    fun reconcile(id: String, sourceFields: Map<String, String?>): List<Difference> {
        val found = mutableListOf<Difference>()
        val editor = prefs.edit()
        for ((field, incoming) in sourceFields) {
            val snapshotKey = key("source", id, field)
            val initializedKey = key("initialized", id, field)
            val previous = prefs.getString(snapshotKey, null)
            val initialized = prefs.getBoolean(initializedKey, false)
            val edited = editedValue(id, field)
            if (initialized && previous != incoming && edited != null && edited != incoming) {
                found.add(Difference(id, field, previous, incoming, edited))
            }
            editor.putBoolean(initializedKey, true)
            if (incoming == null) editor.remove(snapshotKey) else editor.putString(snapshotKey, incoming)
        }
        editor.apply()
        if (found.isNotEmpty()) {
            val pending = pending().associateBy { key("pending", it.entityId, it.field) }.toMutableMap()
            found.forEach { pending[key("pending", it.entityId, it.field)] = it }
            savePending(pending.values.toList())
        }
        return found
    }

    fun pending(): List<Difference> = try {
        val array = JSONArray(prefs.getString("pending", "[]"))
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            Difference(obj.getString("id"), obj.getString("field"),
                if (obj.isNull("previous")) null else obj.getString("previous"),
                if (obj.isNull("incoming")) null else obj.getString("incoming"),
                obj.getString("user"))
        }
    } catch (_: Exception) { emptyList() }

    fun decide(id: String, field: String, overwrite: Boolean) {
        if (overwrite) prefs.edit().remove(key("edit", id, field)).apply()
        savePending(pending().filterNot { it.entityId == id && it.field == field })
    }

    fun overwriteAll() {
        val editor = prefs.edit()
        pending().forEach { editor.remove(key("edit", it.entityId, it.field)) }
        editor.putString("pending", "[]").apply()
    }

    fun keepAll() = savePending(emptyList())

    private fun savePending(differences: List<Difference>) {
        val array = JSONArray()
        differences.forEach {
            array.put(JSONObject().put("id", it.entityId).put("field", it.field)
                .put("previous", it.previousSource).put("incoming", it.incomingSource)
                .put("user", it.userValue))
        }
        prefs.edit().putString("pending", array.toString()).apply()
    }
}

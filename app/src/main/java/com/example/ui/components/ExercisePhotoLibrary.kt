package com.example.ui.components

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.text.Normalizer

/**
 * Curated, explicit exercise-photo assignments.
 *
 * Never auto-select a generic stock search hit as a technique demonstration.
 * Only photos approved for an exact exercise and movement stage are eligible.
 * A reviewer can approve an image from the photo picker. Persisted assignments
 * use stable Pixabay image IDs + attribution, not expiring image URLs.
 */
internal object ExercisePhotoLibrary {
    private const val PREFS = "FITMangoExercisePhotoLibraryV1"
    private const val KEY = "approved"

    fun normalize(name: String): String = Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

    fun allExercises(): List<String> = StrengthExerciseLibrary.exercises.map { it.name }.distinct()

    fun isApproved(context: Context, exercise: String, stage: Int): Boolean {
        return read(context).optJSONObject(normalize(exercise))?.has(stage.toString()) == true
    }

    fun approve(context: Context, exercise: String, stage: Int, photoId: Long, page: String, author: String) {
        require(stage in 0..3 && photoId > 0)
        require(page.startsWith("https://pixabay.com/"))
        val root = read(context)
        val exerciseKey = normalize(exercise)
        val stages = root.optJSONObject(exerciseKey) ?: JSONObject()
        stages.put(stage.toString(), JSONObject().put("photoId", photoId)
            .put("page", page.take(500)).put("author", author.take(120)))
        root.put(exerciseKey, stages)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }

    fun attribution(context: Context, exercise: String, stage: Int): String? {
        val info = read(context).optJSONObject(normalize(exercise))?.optJSONObject(stage.toString())
            ?: return null
        return "Pixabay: " + info.optString("author") + " • " + info.optString("page")
    }

    private fun read(context: Context): JSONObject = runCatching {
        JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}")
    }.getOrElse { JSONObject() }
}

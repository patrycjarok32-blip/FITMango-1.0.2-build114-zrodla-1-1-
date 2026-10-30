package com.example.viewmodel

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Three-column Renpho tiles: preserve spatial columns and never take the smaller +/- comparison. */
data class RenphoOcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = bottom - top
}
data class RenphoImportResult(val values: Map<String, String>, val warnings: List<String>)
object RenphoScreenshotImport {
    private fun key(text: String): String? {
        val s = ProduceSupport.normalize(text).replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
        return when {
            s.contains("beztluszczowa") || s.contains("fat free") || s.contains("lean body") -> "lean"
            s.contains("podskorn") || s.contains("subcutaneous") -> "subcutaneous"
            s.contains("trzewn") || s.contains("visceral") -> "visceral"
            s.contains("szkielet") || s.contains("skeletal") -> "skeletal"
            s.contains("miesniowa") || s.contains("muscle mass") -> "muscle"
            s.contains("kostna") || s.contains("bone mass") -> "bone"
            s.contains("metaboliczn") || s.contains("metabolic age") || s.contains("body age") -> "age"
            s == "bmr" || s.contains("basal metaboli") -> "bmr"
            s.contains("body water") || s.contains("woda w") || s == "woda" -> "water"
            s.contains("protein") || s.contains("bialko") -> "protein"
            s == "bmi" -> "bmi"
            s.replace(" ", "").contains("bodyfat") || s.contains("body fat") || s.contains("tkanka tluszczowa") -> "fat"
            s == "waga" || s == "weight" -> "weight"
            else -> null
        }
    }
    private fun number(text: String): Double? = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:kg|%|kcal|lat|years)?\\s*$", RegexOption.IGNORE_CASE)
        .matchEntire(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    fun parse(lines: List<RenphoOcrLine>, width: Int): RenphoImportResult {
        require(width > 0)
        val found = mutableMapOf<String, MutableSet<Double>>()
        lines.filter { it.height > 0 && it.right - it.left < width * .6 }.groupBy {
            (((it.left + it.right) / 2.0 / width) * 3).toInt().coerceIn(0, 2)
        }.values.forEach { column ->
            val ordered = column.sortedBy { it.top }
            ordered.forEachIndexed { i, line ->
                if (number(line.text) != null) return@forEachIndexed
                var label = line.text
                var last = i
                var metric = key(label)
                while (metric == null && last < minOf(i + 2, ordered.lastIndex)) {
                    val next = ordered[last + 1]
                    if (number(next.text) != null || next.top - ordered[last].bottom > line.height * 1.5) break
                    last++; label += " " + next.text; metric = key(label)
                }
                val k = metric ?: return@forEachIndexed
                val bottom = ordered[last].bottom
                val candidates = ordered.drop(last + 1).takeWhile { it.top - bottom < width * .23 && key(it.text) == null }
                val value = candidates.firstNotNullOfOrNull { candidate ->
                    number(candidate.text)?.takeIf { candidate.height >= line.height * 1.1 }
                }
                if (value != null) {
                    val spec = ScaleMeasurementsSupport.metrics.getValue(k)
                    if (value.isFinite() && value in spec.min..spec.max) found.getOrPut(k) { mutableSetOf() }.add(value)
                }
            }
        }
        val warnings = found.filterValues { it.size > 1 }.keys.map { "Różne wartości: ${ScaleMeasurementsSupport.metrics.getValue(it).label}. Uzupełnij to pole ręcznie." }
        return RenphoImportResult(found.filterValues { it.size == 1 }.mapValues { it.value.single().toString() }, warnings)
    }
    fun combine(results: List<RenphoImportResult>): RenphoImportResult {
        val fields = results.flatMap { it.values.entries }.groupBy { it.key }
        val conflicts = fields.filterValues { values -> values.map { it.value.toDouble() }.distinct().size > 1 }.keys
        return RenphoImportResult(fields.filterKeys { it !in conflicts }.mapValues { it.value.first().value },
            results.flatMap { it.warnings } + conflicts.map { "Zdjęcia mają różne wartości: ${ScaleMeasurementsSupport.metrics.getValue(it).label}. Sprawdź, czy dotyczą jednego ważenia." })
    }
    suspend fun read(context: Context, uris: List<Uri>): RenphoImportResult = withTimeout(60_000) {
        require(uris.size in 1..6) { "Wybierz od 1 do 6 zrzutów jednego ważenia Renpho." }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val results = uris.distinct().map { uri ->
                val image = withContext(Dispatchers.IO) { RecipeImageExtractor.boundedImage(context, uri) }
                val lines = suspendCancellableCoroutine<List<RenphoOcrLine>> { c ->
                    recognizer.process(image).addOnSuccessListener { text ->
                        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { r -> RenphoOcrLine(l.text, r.left, r.top, r.right, r.bottom) } }
                        if (c.isActive) c.resume(lines)
                    }.addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
                        .addOnCanceledListener { if (c.isActive) c.cancel() }
                }
                withContext(Dispatchers.Default) { parse(lines, image.width) }
            }
            combine(results).also { require(it.values.isNotEmpty()) { "Nie rozpoznano pól Renpho. Wybierz wyraźny ekran z trzema kolumnami wyników lub wpisz wartości ręcznie." } }
        } finally { recognizer.close() }
    }
}

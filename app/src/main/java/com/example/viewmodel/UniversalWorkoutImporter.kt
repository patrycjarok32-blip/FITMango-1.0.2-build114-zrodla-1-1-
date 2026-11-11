package com.example.viewmodel

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Local, review-first import; no existing plans are modified by reading a file. */
object UniversalWorkoutImporter {
    suspend fun read(context: Context, uri: Uri, mime: String?): String {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        val type = mime.orEmpty()
        return when {
            type == "application/pdf" || name.endsWith(".pdf") ->
                HybridPdfTextReader.extract(context, uri)
            type.startsWith("image/") || listOf(".jpg", ".jpeg", ".png", ".webp").any { name.endsWith(it) } -> {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    suspendCancellableCoroutine { continuation ->
                        recognizer.process(image)
                            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                    }
                } finally { recognizer.close() }
            }
            type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || name.endsWith(".docx") ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null && entry.name != "word/document.xml") entry = zip.nextEntry
                            require(entry != null) { "Nie znaleziono treści DOCX." }
                            val parser = XmlPullParserFactory.newInstance().newPullParser()
                            parser.setInput(zip, "UTF-8")
                            val text = StringBuilder()
                            var inText = false
                            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                                when (parser.eventType) {
                                    XmlPullParser.START_TAG -> {
                                        if (parser.name == "t") inText = true
                                        if (parser.name == "tab") text.append(" ")
                                    }
                                    XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                                    XmlPullParser.END_TAG -> {
                                        if (parser.name == "t") inText = false
                                        if (parser.name == "p" || parser.name == "tr") text.append('\n')
                                    }
                                }
                                parser.next()
                            }
                            text.toString()
                        }
                    } ?: error("Nie można otworzyć DOCX.")
                }
            else -> error("Obsługiwane formaty: PDF, DOCX, JPG, PNG i WebP.")
        }.take(150_000)
    }

    /** Conservative parser: ambiguous entries are skipped rather than silently invented. */
    fun parse(text: String): List<ImportedWorkoutTemplate> {
        val days = mutableListOf<ImportedWorkoutTemplate>()
        var title = "Trening 1"
        var exercises = mutableListOf<PlannedExercise>()
        fun flush() {
            if (exercises.isNotEmpty()) {
                days.add(ImportedWorkoutTemplate(title.take(120), exercises.take(30)))
                exercises = mutableListOf()
            }
        }
        val heading = Regex("""(?i)^(?:dzień|dzien|day|trening|workout|push|pull|legs|góra|gora|dół|dol|upper|lower)(?:\s*[:#-]?\s*[\wąćęłńóśźż -]{0,45})?$""")
        val setReps = Regex("""(?i)(\d{1,2})\s*(?:x|×|serie?\s*po|sets?\s*(?:x|of))\s*(\d{1,3})(?:\s*[-–]\s*\d{1,3})?""")
        val reverse = Regex("""(?i)(\d{1,3})\s*(?:powt(?:órzeń|orzen|\.)?|reps?)\s*[,; ]+\s*(\d{1,2})\s*(?:serie|series|sets)""")
        text.lines().map { it.trim().trimStart('•','-','*',' ') }.forEach { line ->
            if (line.isBlank()) return@forEach
            if (line.length <= 55 && heading.matches(line) && !line.contains(" x ", true)) {
                flush()
                title = line
                return@forEach
            }
            val match = setReps.find(line)
            val rev = if (match == null) reverse.find(line) else null
            val sets = (match?.groupValues?.get(1) ?: rev?.groupValues?.get(2))?.toIntOrNull()
            val reps = (match?.groupValues?.get(2) ?: rev?.groupValues?.get(1))?.toIntOrNull()
            if (sets == null || reps == null || sets !in 1..12 || reps !in 1..100) return@forEach
            val position = match?.range ?: rev!!.range
            val rawName = (line.substring(0, position.first) + " " + line.substring(position.last + 1))
                .trim(' ', ':', '-', '–', ',', ';', '.', '(', ')')
                .replace(Regex("""\s+"""), " ")
            if (rawName.length in 3..120 && exercises.size < 30) {
                exercises.add(PlannedExercise(rawName, sets, reps, 60))
            }
        }
        flush()
        return days.take(7)
    }
}

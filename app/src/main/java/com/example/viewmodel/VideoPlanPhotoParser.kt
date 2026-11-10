package com.example.viewmodel

/** Offline OCR post-processing. Suggestions must be reviewed before saving. */
object VideoPlanPhotoParser {
    data class Candidate(val title: String, val minutes: Int, val category: String,
        val confidence: String = "Do sprawdzenia")
    private val duration = Regex("(?i)\\b(\\d{1,3})\\s*(?:min|minutes|minut|m)\\b")
    private val heading = Regex("(?i)^(?:dzie[nń]|day|tydzie[nń]|week)\\s*\\d+.*")
    private val categoryWords = listOf("cardio", "brzuch", "poślad", "trening", "stretch",
        "joga", "pilates", "walking", "full body", "nogi", "ramiona", "plecy",
        "fitness", "tabata", "hiit", "rozciągan", "mobilno", "siłow")
    fun parse(text: String): List<Candidate> {
        val lines = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }.take(400)
        val results = mutableListOf<Candidate>()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.length !in 5..130 || heading.matches(line) ||
                categoryWords.none { line.contains(it, ignoreCase = true) }) continue
            val inline = duration.find(line)?.groupValues?.get(1)?.toIntOrNull()
            val adjacent = (i + 1..minOf(i + 2, lines.lastIndex))
                .mapNotNull { j ->
                    if (heading.matches(lines[j]) ||
                        categoryWords.any { lines[j].contains(it, ignoreCase = true) }) null
                    else duration.find(lines[j])?.groupValues?.get(1)?.toIntOrNull()
                }.firstOrNull()
            val minutes = (inline ?: adjacent)?.takeIf { it in 1..300 }
            val cleaned = line.replace(duration, "").trim(' ', '-', '•', '|', ':')
                .take(120)
            if (cleaned.length < 4) continue
            val category = when {
                cleaned.contains("cardio", true) || cleaned.contains("walking", true) ||
                    cleaned.contains("hiit", true) -> "Cardio"
                cleaned.contains("brzuch", true) -> "Core"
                cleaned.contains("stretch", true) || cleaned.contains("joga", true) ||
                    cleaned.contains("rozciągan", true) -> "Regeneracja"
                else -> "Wzmacnianie"
            }
            if (results.none { it.title.equals(cleaned, true) }) {
                results.add(Candidate(cleaned, minutes ?: 20, category,
                    if (minutes != null) "Czas odczytany ze zdjęcia" else "Czas 20 min — sprawdź"))
            }
            if (results.size == 30) break
        }
        return results
    }
}

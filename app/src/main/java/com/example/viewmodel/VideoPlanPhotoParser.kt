package com.example.viewmodel

/** Conservative OCR suggestions: a screenshot is not treated as an authoritative plan. */
object VideoPlanPhotoParser {
    data class Candidate(val title: String, val minutes: Int, val category: String)
    fun parse(text: String): List<Candidate> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val duration = Regex("(?i)\\b(\\d{1,3})\\s*(?:min|minutes|minut)\\b")
        val titles = lines.filter { line ->
            line.length in 4..100 &&
                (line.contains("cardio", true) || line.contains("brzuch", true) ||
                    line.contains("poślad", true) || line.contains("trening", true) ||
                    line.contains("stretch", true) || line.contains("joga", true) ||
                    line.contains("pilates", true) || line.contains("walking", true) ||
                    line.contains("full body", true))
        }.filterNot { it.contains("bez cardio", true) || it.contains("dance cardio", true) && it.length < 15 }
        val minutes = lines.mapNotNull { duration.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .filter { it in 1..300 }
        return titles.distinct().take(12).mapIndexed { i, title ->
            Candidate(title, minutes.getOrNull(i) ?: 20,
                when {
                    title.contains("cardio", true) || title.contains("walking", true) -> "Cardio"
                    title.contains("brzuch", true) -> "Core"
                    title.contains("stretch", true) || title.contains("joga", true) -> "Regeneracja"
                    else -> "Wzmacnianie"
                })
        }
    }
}

package com.example.viewmodel

/** Conservative keyword grouping. Unknown exercises are never recommended as equivalent. */
object MangoExerciseAlternatives {
    private val groups = mapOf(
        "pośladki" to listOf("glute", "hip thrust", "bridge", "poślad", "odwodzenie", "kickback"),
        "przysiad" to listOf("squat", "przysiad", "leg press", "suwnica"),
        "tył uda" to listOf("romanian", "rdl", "deadlift", "martwy ciąg", "leg curl", "uginanie nóg"),
        "klatka" to listOf("chest press", "bench press", "wyciskanie leżąc", "pompki", "push up"),
        "plecy" to listOf("row", "wiosłowanie", "lat pulldown", "ściąganie drążka", "podciąganie"),
        "barki" to listOf("shoulder press", "lateral raise", "unoszenie bokiem", "wyciskanie nad głowę"),
        "biceps" to listOf("biceps", "curl", "uginanie ramion"),
        "triceps" to listOf("triceps", "pushdown", "prostowanie ramion")
    )
    fun muscleGroup(name: String): String? {
        val normalized = name.lowercase(java.util.Locale.ROOT)
        return groups.entries.firstOrNull { (_, keys) -> keys.any { normalized.contains(it) } }?.key
    }
    private val gymEquipment = Regex(
        "maszyna|wyciąg|suwnica|smith|cable|machine|leg press|pulldown|leg curl",
        RegexOption.IGNORE_CASE
    )
    fun candidates(current: String, library: List<String>, home: Boolean): List<String> {
        val group = muscleGroup(current) ?: return emptyList()
        return library.filter { it != current && muscleGroup(it) == group }
            .filter { !home || !gymEquipment.containsMatchIn(it) }
            .distinct().sortedWith(compareBy<String> { if (home) 0 else if (gymEquipment.containsMatchIn(it)) 0 else 1 }.thenBy { it })
    }
}

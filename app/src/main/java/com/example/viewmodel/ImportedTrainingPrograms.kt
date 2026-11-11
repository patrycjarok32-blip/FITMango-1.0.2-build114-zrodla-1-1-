package com.example.viewmodel

/** Read-only workout templates transcribed from the user's own training documents.
 * Templates do not overwrite scheduled workouts until explicitly confirmed.
 */
data class ImportedWorkoutTemplate(val title: String, val exercises: List<PlannedExercise>)
data class ImportedTrainingProgram(val id: String, val name: String, val source: String, val days: List<ImportedWorkoutTemplate>)

object ImportedTrainingPrograms {
    private fun e(name: String, sets: Int, reps: Int) = PlannedExercise(name, sets, reps, 60)
    val all = listOf(
        ImportedTrainingProgram("home", "PUSH / PULL / LEGS — dom", "PUSH-PULL-DOWN-do-DOMU.docx", listOf(
            ImportedWorkoutTemplate("LEGS — dom", listOf(
                e("Wyprost biodra (na stronę)",4,10), e("Goblet squat",4,10),
                e("Zakroki (na stronę)",3,10), e("Martwy ciąg na prostych nogach",4,8),
                e("Odwodzenie z mini band",4,12), e("Spięcia brzucha",3,20))),
            ImportedWorkoutTemplate("PUSH — dom", listOf(
                e("Glute bridge",4,10), e("Floor press",4,10), e("Wyciskanie młotkowe",3,10),
                e("Unoszenie hantli nad głowę",3,12), e("Wznosy hantli bokiem",3,12),
                e("Prostowanie przedramion w opadzie",3,10),
                e("Prostowanie przedramion zza głowy",3,10), e("Dead bug",3,20))),
            ImportedWorkoutTemplate("PULL — dom", listOf(
                e("Przysiad sumo",4,10), e("Wiosłowanie w opadzie tułowia",4,10),
                e("Narciarz",4,10), e("Unoszenie sztangi do klatki",3,12),
                e("Uginanie hantli młotkowo",3,10), e("Uginanie z supinacją",3,10),
                e("Plank (60 sekund)",3,60)))
        )),
        ImportedTrainingProgram("glutes", "Góra / Dół — priorytet pośladki", "plan-treningowy-posladki.pdf", listOf(
            ImportedWorkoutTemplate("DÓŁ 1", listOf(
                e("Box squat",3,8), e("Hip thrust jednonóż (na nogę)",3,8),
                e("Martwy ciąg rumuński",3,8), e("Side lying z gumą (na nogę)",3,8),
                e("Uginanie ud na maszynie leżąc",3,8), e("Dead bug (na stronę)",3,8),
                e("Przeskoki do przysiadu z gumą",3,20))),
            ImportedWorkoutTemplate("GÓRA 1", listOf(
                e("Wyciskanie sztangi na ławce poziomej",3,8),
                e("Podciąganie nachwytem z gumą",3,8), e("OHP",3,8),
                e("Wiosłowanie hantlami w leżeniu na ławce",3,8),
                e("Wciąganie na wyciągu",3,10), e("Odwodzenie nogi w bok na wyciągu",3,8),
                e("Allahy",3,10))),
            ImportedWorkoutTemplate("DÓŁ 2", listOf(
                e("Hip thrust ze sztangą",3,8), e("Zakroki z podwyższenia (na nogę)",3,8),
                e("Martwy ciąg jednonóż (na nogę)",3,8), e("Kick back (na nogę)",3,8),
                e("Wyprosty tułowia na ławce rzymskiej",3,10),
                e("Przywodzenie nogi z gumą power band (na nogę)",3,10))),
            ImportedWorkoutTemplate("GÓRA 2", listOf(
                e("Wyciskanie hantli na skosie dodatnim",3,8), e("Gorilla row (na stronę)",3,8),
                e("Ściąganie drążka do klatki wąsko",3,8),
                e("Wznosy hantli na boki + w górę",3,8),
                e("Narciarz",3,10), e("Facepull z gumą",3,10),
                e("Spięcia brzucha na wydłużonej dźwigni",3,10)))
        ))
    )
}

/** Instructional links from the home workout document; only known video URLs are embedded. */
object ImportedExerciseVideos {
    private val urls = mapOf(
        "Wyprost biodra (na stronę)" to "https://www.youtube.com/watch?v=6MGYgc9-v_I",
        "Goblet squat" to "https://www.youtube.com/watch?v=CkFzgR55gho",
        "Zakroki (na stronę)" to "https://www.youtube.com/watch?v=B5yU9VvjOr8",
        "Martwy ciąg na prostych nogach" to "https://www.youtube.com/watch?v=o9puuLKBdjA",
        "Odwodzenie z mini band" to "https://www.youtube.com/watch?v=VGyomvk6gg8",
        "Spięcia brzucha" to "https://www.youtube.com/watch?v=MNcn1_YhEsQ",
        "Glute bridge" to "https://www.youtube.com/watch?v=6pOjT1gEMqQ",
        "Floor press" to "https://www.youtube.com/watch?v=Fs9gbE5VLKU",
        "Wyciskanie młotkowe" to "https://www.youtube.com/watch?v=G9L8l4N01-k",
        "Unoszenie hantli nad głowę" to "https://www.youtube.com/watch?v=pInYJYisaEo",
        "Prostowanie przedramion w opadzie" to "https://www.youtube.com/watch?v=-tCnjHpmTG4",
        "Prostowanie przedramion zza głowy" to "https://www.youtube.com/watch?v=bQdml0AuCgI",
        "Dead bug" to "https://www.youtube.com/watch?v=yUiZpr9ngIA",
        "Przysiad sumo" to "https://www.youtube.com/watch?v=mL789y2aqrE",
        "Wiosłowanie w opadzie tułowia" to "https://www.youtube.com/watch?v=MOlDmANU_4U",
        "Narciarz" to "https://www.youtube.com/watch?v=TgXJkJ3Hq2A",
        "Unoszenie sztangi do klatki" to "https://www.youtube.com/watch?v=Jr2jZECyKi0",
        "Uginanie hantli młotkowo" to "https://www.youtube.com/watch?v=s_ubLsRZ59I",
        "Uginanie z supinacją" to "https://www.youtube.com/watch?v=RTYQSbHTwOg",
        "Plank (60 sekund)" to "https://www.youtube.com/watch?v=Rhq4govSpAg"
    )
    fun forExercise(name: String): String? = urls[name]
}

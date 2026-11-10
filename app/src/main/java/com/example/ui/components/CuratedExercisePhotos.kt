package com.example.ui.components

/**
 * Manually mapped exercise media from yuhonas/free-exercise-db (Unlicense).
 * Images are two real stills supplied by the upstream dataset. They are not
 * certified technique instruction or three separate motion frames.
 * Source: https://github.com/yuhonas/free-exercise-db
 */
internal object CuratedExercisePhotos {
    private const val BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    private val images = mapOf(
        "Przysiad" to listOf("Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"),
        "Suwnica na nogi" to listOf("Leg_Press/0.jpg", "Leg_Press/1.jpg"),
        "Wykroki" to listOf("Dumbbell_Lunges/0.jpg", "Dumbbell_Lunges/1.jpg"),
        "Prostowanie nóg" to listOf("Leg_Extensions/0.jpg", "Leg_Extensions/1.jpg"),
        "Uginanie nóg" to listOf("Lying_Leg_Curls/0.jpg", "Lying_Leg_Curls/1.jpg"),
        "Hip thrust" to listOf("Barbell_Hip_Thrust/0.jpg", "Barbell_Hip_Thrust/1.jpg"),
        "Martwy ciąg rumuński" to listOf("Romanian_Deadlift/0.jpg", "Romanian_Deadlift/1.jpg"),
        "Odwodzenie nóg" to listOf("Thigh_Abductor/0.jpg", "Thigh_Abductor/1.jpg"),
        "Glute bridge" to listOf("Barbell_Glute_Bridge/0.jpg", "Barbell_Glute_Bridge/1.jpg"),
        "Wiosłowanie hantlami" to listOf("One-Arm_Dumbbell_Row/0.jpg", "One-Arm_Dumbbell_Row/1.jpg"),
        "Ściąganie drążka" to listOf("Wide-Grip_Lat_Pulldown/0.jpg", "Wide-Grip_Lat_Pulldown/1.jpg"),
        "Podciąganie" to listOf("Pullups/0.jpg", "Pullups/1.jpg"),
        "Wyciskanie na ławce" to listOf("Barbell_Bench_Press_-_Medium_Grip/0.jpg", "Barbell_Bench_Press_-_Medium_Grip/1.jpg"),
        "Pompki" to listOf("Pushups/0.jpg", "Pushups/1.jpg"),
        "Wyciskanie hantli" to listOf("Dumbbell_Shoulder_Press/0.jpg", "Dumbbell_Shoulder_Press/1.jpg"),
        "Unoszenie bokiem" to listOf("Side_Lateral_Raise/0.jpg", "Side_Lateral_Raise/1.jpg"),
        "Uginanie na biceps" to listOf("Barbell_Curl/0.jpg", "Barbell_Curl/1.jpg"),
        "Prostowanie na triceps" to listOf("Triceps_Pushdown/0.jpg", "Triceps_Pushdown/1.jpg"),
        "Plank" to listOf("Plank/0.jpg", "Plank/1.jpg"),
        "Dead bug" to listOf("Dead_Bug/0.jpg", "Dead_Bug/1.jpg"),
        "Przysiad goblet" to listOf("Goblet_Squat/0.jpg", "Goblet_Squat/1.jpg"),
        "Przysiad bułgarski" to listOf("Split_Squat_with_Dumbbells/0.jpg", "Split_Squat_with_Dumbbells/1.jpg"),
        "Hack squat" to listOf("Barbell_Hack_Squat/0.jpg", "Barbell_Hack_Squat/1.jpg"),
        "Przysiad Smitha" to listOf("Smith_Machine_Squat/0.jpg", "Smith_Machine_Squat/1.jpg"),
        "Step-up" to listOf("Dumbbell_Step_Ups/0.jpg", "Dumbbell_Step_Ups/1.jpg"),
        "Wykroki chodzone" to listOf("Barbell_Walking_Lunge/0.jpg", "Barbell_Walking_Lunge/1.jpg"),
        "Zakroki" to listOf("Dumbbell_Rear_Lunge/0.jpg", "Dumbbell_Rear_Lunge/1.jpg"),
        "Wspięcia na palce stojąc" to listOf("Standing_Calf_Raises/0.jpg", "Standing_Calf_Raises/1.jpg"),
        "Wspięcia na palce siedząc" to listOf("Seated_Calf_Raise/0.jpg", "Seated_Calf_Raise/1.jpg"),
        "Hip thrust na maszynie" to listOf("Barbell_Hip_Thrust/0.jpg", "Barbell_Hip_Thrust/1.jpg"),
        "Kickback na wyciągu" to listOf("Glute_Kickback/0.jpg", "Glute_Kickback/1.jpg"),
        "Frog pumps" to listOf("Barbell_Glute_Bridge/0.jpg", "Barbell_Glute_Bridge/1.jpg"),
        "Martwy ciąg klasyczny" to listOf("Barbell_Deadlift/0.jpg", "Barbell_Deadlift/1.jpg"),
        "Martwy ciąg sumo" to listOf("Sumo_Deadlift/0.jpg", "Sumo_Deadlift/1.jpg"),
        "Wiosłowanie sztangą" to listOf("Bent_Over_Barbell_Row/0.jpg", "Bent_Over_Barbell_Row/1.jpg"),
        "Wiosłowanie na maszynie" to listOf("Seated_Cable_Rows/0.jpg", "Seated_Cable_Rows/1.jpg"),
        "Wiosłowanie na wyciągu siedząc" to listOf("Seated_Cable_Rows/0.jpg", "Seated_Cable_Rows/1.jpg"),
        "Face pull" to listOf("Face_Pull/0.jpg", "Face_Pull/1.jpg"),
        "Pullover na wyciągu" to listOf("Straight-Arm_Pulldown/0.jpg", "Straight-Arm_Pulldown/1.jpg"),
        "Wyciskanie hantli na ławce" to listOf("Dumbbell_Bench_Press/0.jpg", "Dumbbell_Bench_Press/1.jpg"),
        "Wyciskanie na skosie dodatnim" to listOf("Barbell_Incline_Bench_Press_-_Medium_Grip/0.jpg", "Barbell_Incline_Bench_Press_-_Medium_Grip/1.jpg"),
        "Rozpiętki z hantlami" to listOf("Dumbbell_Flyes/0.jpg", "Dumbbell_Flyes/1.jpg"),
        "Rozpiętki na maszynie" to listOf("Butterfly/0.jpg", "Butterfly/1.jpg"),
        "Rozpiętki na bramie" to listOf("Cable_Crossover/0.jpg", "Cable_Crossover/1.jpg"),
        "Pompki na kolanach" to listOf("Pushups/0.jpg", "Pushups/1.jpg"),
        "Wyciskanie żołnierskie" to listOf("Standing_Military_Press/0.jpg", "Standing_Military_Press/1.jpg"),
        "Arnold press" to listOf("Arnold_Dumbbell_Press/0.jpg", "Arnold_Dumbbell_Press/1.jpg"),
        "Unoszenie hantli w przód" to listOf("Front_Dumbbell_Raise/0.jpg", "Front_Dumbbell_Raise/1.jpg"),
        "Odwrotne rozpiętki" to listOf("Reverse_Flyes/0.jpg", "Reverse_Flyes/1.jpg"),
        "Reverse pec deck" to listOf("Reverse_Machine_Flyes/0.jpg", "Reverse_Machine_Flyes/1.jpg"),
        "Uginanie młotkowe" to listOf("Hammer_Curls/0.jpg", "Hammer_Curls/1.jpg"),
        "Uginanie na modlitewniku" to listOf("Preacher_Curl/0.jpg", "Preacher_Curl/1.jpg"),
        "Uginanie na wyciągu" to listOf("Cable_Hammer_Curls_-_Rope_Attachment/0.jpg", "Cable_Hammer_Curls_-_Rope_Attachment/1.jpg"),
        "Uginanie koncentracyjne" to listOf("Concentration_Curls/0.jpg", "Concentration_Curls/1.jpg"),
        "Francuskie wyciskanie" to listOf("Lying_Triceps_Press/0.jpg", "Lying_Triceps_Press/1.jpg"),
        "Prostowanie tricepsa nad głową" to listOf("Standing_Dumbbell_Triceps_Extension/0.jpg", "Standing_Dumbbell_Triceps_Extension/1.jpg"),
        "Dipy na poręczach" to listOf("Dips_-_Triceps_Version/0.jpg", "Dips_-_Triceps_Version/1.jpg"),
        "Prostowanie tricepsa z gumą" to listOf("Triceps_Pushdown/0.jpg", "Triceps_Pushdown/1.jpg"),
        "Russian twist" to listOf("Russian_Twist/0.jpg", "Russian_Twist/1.jpg"),
        "Side plank" to listOf("Side_Bridge/0.jpg", "Side_Bridge/1.jpg"),
        "Pallof press" to listOf("Pallof_Press/0.jpg", "Pallof_Press/1.jpg"),
        "Farmer walk" to listOf("Farmers_Walk/0.jpg", "Farmers_Walk/1.jpg"),
        "Unoszenie kolan w zwisie" to listOf("Hanging_Leg_Raise/0.jpg", "Hanging_Leg_Raise/1.jpg"),
    )

    fun url(exercise: String, stage: Int): String? {
        val stills = images[exercise] ?: return null
        val index = when(stage) { 0, 1 -> 0; 2, 3 -> 1; else -> return null }
        return BASE + stills[index].split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
    }

    fun hasPhotos(exercise: String) = images.containsKey(exercise)
    fun coveredCount() = StrengthExerciseLibrary.exercises.count { hasPhotos(it.name) }
    const val CREDIT = "Zdjęcia: free-exercise-db (Unlicense). Ujęcia poglądowe; sprawdź technikę."
}

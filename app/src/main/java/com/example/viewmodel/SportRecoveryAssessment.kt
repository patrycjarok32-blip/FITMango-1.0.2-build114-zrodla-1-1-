package com.example.viewmodel

object SportRecoveryAssessment {
    data class Result(val message: String, val activeDays: Int)
    fun evaluate(energy: Int?, fatigue: Int?, sleepMinutes: Long?, activeDays: Int): Result {
        val e = energy?.takeIf { it in 1..5 }
        val f = fatigue?.takeIf { it in 1..5 }
        val sleep = sleepMinutes?.takeIf { it in 1L..1440L }
        val days = activeDays.coerceIn(0, 3)
        val message = when {
            e != null && e <= 2 || f != null && f >= 4 || sleep != null && sleep < 360L ->
                "Rozważ odpoczynek lub lżejszą aktywność."
            e == null || f == null || sleep == null ->
                "Niepełne dane — oceń samopoczucie i uzupełnij sen przed zwiększeniem obciążenia."
            days == 3 -> "Trzy dni z treningiem z rzędu — sprawdź regenerację przed kolejną sesją."
            e >= 4 && f <= 2 -> "Samopoczucie sprzyja aktywności; dostosuj intensywność do siebie."
            else -> "Dostosuj intensywność do samopoczucia."
        }
        return Result(message, days)
    }
}

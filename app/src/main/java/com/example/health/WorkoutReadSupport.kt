package com.example.health

/** Health Connect on older Android versions may end pagination with an empty token.
 * Do not send it as another request, which would fetch the first page again.
 * https://developer.android.com/health-and-fitness/health-connect/read-data */
object WorkoutReadSupport {
    suspend fun <T> readPages(fetch: suspend (String?) -> Pair<List<T>, String?>): List<T> {
        val records = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        var token: String? = null
        var pages = 0
        do {
            check(++pages <= 25) { "Odczyt treningów przekroczył limit stron. Ponów synchronizację." }
            val (page, next) = fetch(token)
            records.addAll(page)
            check(records.size <= 2000) { "Zbyt dużo treningów do jednorazowego odczytu." }
            token = next?.takeIf { it.isNotBlank() }
            if (token != null) check(seen.add(token!!)) { "Health Connect zwrócił powtarzającą się stronę. Zachowano poprzednią historię." }
        } while (token != null)
        return records
    }
}

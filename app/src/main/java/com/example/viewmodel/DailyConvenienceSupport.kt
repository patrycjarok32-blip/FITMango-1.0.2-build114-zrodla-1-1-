package com.example.viewmodel

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Input checks, not portion recommendations. The user can always keep the amount. */
object QuantityReviewSupport {
    fun warning(product: SubstituteCandidate, quantity: Float, unit: String, previous: Float? = null): String? {
        if (!quantity.isFinite() || quantity <= 0) return null
        val normalized = unit.trim().lowercase(Locale.ROOT)
        val grams = when (normalized) { "kg", "l" -> quantity * 1000; "g", "ml" -> quantity; else -> return null }
        val name = ProduceSupport.normalize(product.name)
        val reason = when {
            previous != null && previous.isFinite() && previous > 0 && quantity >= previous * 10 -> "To co najmniej 10 razy więcej niż poprzednia ilość w tej samej jednostce."
            grams > (if (normalized in listOf("ml", "l")) 3000 else 1000) -> "Sprawdź, czy ilość dotyczy jednej porcji i czy jednostka jest poprawna."
            grams >= 80 && (name.contains("maslo orzech") || name.contains("peanut butter")) -> "Sprawdź, czy chodzi o tę ilość masła orzechowego."
            grams >= 40 && Regex("\\b(olej|oliwa|oil)\\b").containsMatchIn(name) -> "Sprawdź ilość tłuszczu i wybraną jednostkę."
            else -> return null
        }
        return "${product.name}: ${MealPlannerSupport.formatQuantityNumber(quantity)} $unit.\n$reason"
    }
}

object EnergyExplanationSupport {
    fun describe(food: Int?, target: Int, expenditure: Double?, health: Boolean, finished: Boolean,
        complete: Boolean, approved: Boolean, modelNote: String? = null): String = buildString {
        append("Cel jedzenia: $target kcal — to limit spożycia, nie wartość spalania.\n")
        append("Zjedzone: ${food?.let { "$it kcal" } ?: "brak wpisów"}.\n")
        val valid = expenditure?.takeIf { it.isFinite() && it > 0 }
        append(if (health) "Spalanie ogółem z Health Connect: " else "Zapotrzebowanie przed deficytem z profilu: ")
        append(valid?.let { "${it.roundToInt()} kcal.\n" } ?: "brak danych.\n")
        if (valid != null && food != null) {
            val displayedExpenditure = valid.roundToInt()
            val difference = displayedExpenditure - food
            append("${if (finished) "Bilans dnia" else "Bilans roboczy — dzień jeszcze trwa"}: ")
            append("$displayedExpenditure − $food = $difference kcal (po zaokrągleniu).\n")
            append(if (difference > 0) "Deficyt: ${abs(difference)} kcal.\n" else if (difference < 0) "Nadwyżka: ${abs(difference)} kcal.\n" else "Bilans zerowy.\n")
        } else append("Nie wyliczamy deficytu ani nadwyżki przy brakujących danych.\n")
        append(if (health) "Spalanie ogółem powinno zawierać aktywność i metabolizm podstawowy. Nie dodajemy ponownie BMR ani kalorii treningu. Sprawdź pełny dzień w aplikacji źródłowej.\n"
            else (modelNote ?: "Szacunek: Mifflin–St Jeor × aktywność z aktualnego profilu.") + " Cel redukcji nie jest odejmowany drugi raz. Kalorie zegarka nie są dodawane.\n")
        if (health && !finished) append("Spalanie jest odczytem do tej chwili, a nie zapotrzebowaniem na całą dobę.\n")
        if (!finished) append("Dzisiejszy dzień nie wchodzi jeszcze do zakończonej analizy tygodnia.\n")
        if (!complete) append("Brakuje części wartości składników: spożycie i bilans są przybliżone.\n")
        if (!approved) append("Posiłki tego dnia nie są zatwierdzone.\n")
        append("Bilans energii nie jest pomiarem zmiany tkanki tłuszczowej ani prognozą wagi.")
    }
}

object ReleaseNotesSupport {
    val BUILD: Int = com.example.BuildConfig.VERSION_CODE
    val notes = listOf(
        "Start i Sport: wspólny wybór najnowszych kroków i snu, jednolite zaokrąglanie kalorii oraz czytelniejszy układ na telefonie.",
        "Szybsze wpisywanie: wyszukiwanie przepisów i produktów w tle, mniej przetwarzania podczas pisania w czacie i Profilu.",
        "Co nowego: poprawiono wyświetlanie informacji o aktualnej wersji aplikacji.",
        "Profil: trzy zakładki, mniejsze kafelki ze statusami, osobne widoki funkcji, skróty i zapamiętywanie miejsca na listach.",
        "Zdjęcia sylwetki: miniatury, pełny podgląd, starsze zestawy, porównywanie, ponowny wybór brakującego zdjęcia i trwałe kopie.",
        "Etapy redukcji: wykres kalorii w czasie, własne długości etapów i data startu, bieżący etap i termin kolejnej korekty.",
        "Uzupełnij wszystkie braki: wszystkie przepisy i jadłospis, zapisane dane i Open Food Facts, postęp, wybór niejednoznacznych wyników i zapis samych kalorii bez wymyślania makro.",
        "Dane składników: szersze rozpoznawanie jogurtu, mąki, jajek, owoców i produktów z bazy; jednostki szt. jajek oraz odmienione nazwy przypraw.",
        "Import: automatyczne rozpoznawanie odmienionych nazw czosnku, szalotki i oliwy; jawne szacunki masy ząbków, bez nadpisywania własnych danych.",
        "Analiza tygodnia: każdy zatwierdzony dzień to pełny zapis; ocena jadłospisu, porównanie okresów i konkretne wskazówki.",
        "Zakupy: wybór dat jadłospisu od–do, zapamiętanie zakresu i powrót do bieżącego tygodnia.",
        "Uzupełnij produkty: pomijanie przypraw i usuwanie z listy po zapisaniu pełnych wartości odżywczych.",
        "Testy zakupów: etapy przekreślenia i przesunięcia sprawdzane niezależnie od zegara emulatora.",
        "Zakupy: stan zaznaczenia pochodzi od razu z aktualnych danych; animacja zmienia wyłącznie kolejność.",
        "Poprawiono synchronizację testu animacji zakupów bez pomijania kontroli przekreślenia i kolejności.",
        "Dotyk: naprawiona reakcja przycisków, która wcześniej nie otrzymywała zdarzeń naciśnięcia.",
        "Animacje: test w Profilu, płynne rozwijanie kart oraz przejścia między krokami gotowania.",
        "Zakupy: najpierw przekreślenie zapisanego produktu, potem przesunięcie do Mam.",
        "Jadłospis: wyróżnienie najbliższego posiłku, kolorowe krawędzie i poprawione przewijanie po zjedzeniu.",
        "Wygląd: większe liczby, mniejsze jednostki, fioletowy sen i ilustracje pustych list.",
        "Postępy: rekordy z dostępnych sesji i porównanie dwóch zakończonych tygodni z liczbą dni danych.",
        "Start: szybki wspólny zapis energii, zmęczenia i głodu.",
        "Przypomnienia lokalne: ważenie, aktywność i raport z własną godziną i dniem tygodnia.",
        "AI: Dlaczego widzę taką ocenę pokazuje dane z chwili wygenerowania raportu.",
        "Wygląd: przewodnik mango, przesuwane podświetlenie nawigacji, animowane wykresy i zaokrąglone zdjęcia.",
        "Poprawiono import animacji kart, który blokował kompilację build93.",
        "Graficzna aktywność: czytelne kafelki, słupki tygodnia, wybór dnia i wykres snu z zachowaniem braków odczytu.",
        "Raport tygodniowy: cztery krótkie karty, rzeczywiste średnie z zatwierdzonych dni i rozwijane uzasadnienie AI.",
        "Start: szerszy przycisk Szczegóły i raport oraz liczby bez ściskania tekstu obok pierścienia.",
        "Aktywność i regeneracja: kroki, sen, czas sesji i porównanie zakończonych dni tygodnia.",
        "Cel tygodniowy w dniach, minutach lub spacerach oraz szybkie dodawanie aktywności bez zegarka.",
        "Samopoczucie i zmęczenie, wskazówki z kilku dni i raport AI za 7 zakończonych dni.",
        "Powtarzające się sesje są pomijane, nakładający się czas liczony raz, bez podwajania kalorii.",
        "Uzupełnione wartości produktu przeliczają także posiłki bez zamienników i ich sumę dnia.",
        "Poprawiono błąd kompilacji przy odczycie zapisanych wartości produktów w wyszukiwarce lokalnej.",
        "Co wymaga uwagi: wspólna lista brakujących danych produktów z zapisem wartości do ponownego użycia.",
        "Zakupy: brakujące produkty na wierzchu, posiadane w zwijanej sekcji Mam.",
        "Moje zestawy: zapis jednego posiłku i dodawanie zestawu do wybranej daty.",
        "Pomiary: źródło, godzina pomiaru i ostatni odczyt Health Connect.",
        "Gotowanie: liczba porcji i automatycznie przeliczane ilości składników.",
        "Historia: ostatnie zmiany posiłków i pomiarów z bieżącej sesji z bezpiecznym cofaniem."
    )
    fun shouldShow(installed: Int, acknowledged: Int) = installed == BUILD && acknowledged < installed
}

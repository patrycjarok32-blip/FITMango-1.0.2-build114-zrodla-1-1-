# FITMango 1.0.2 build101

## Zatwierdzone dni

Zatwierdzenie oznacza pełny, potwierdzony zapis jedzenia. Wszystkie takie dni
wchodzą do średnich kalorii i makro w analizie efektów, raporcie tygodniowym i AI.
Precyzja wartości składników jest opisana oddzielnie. Dzień nie staje się niepełny
przez samą obecność szacunków. Zmiana posiłków lub wartości jedzenia nadal wymaga
ponownego zatwierdzenia zmienionego zapisu. Zmiana spalania nie cofa zatwierdzenia.
Dzisiaj pozostaje w toku; raport obejmuje siedem zakończonych dni do wczoraj.

## Ocena jadłospisu

Nowa karta w Profilu i Sport → Raport działa lokalnie, bez AI:
- średnie kalorii, białka, węglowodanów i tłuszczów ze wszystkich zatwierdzonych dni;
- realizacja celu białka z aktualnego profilu (nie jako domniemany cel historyczny);
- porównanie kalorii z celami zapisanymi dla konkretnych dni;
- warzywa i owoce tylko z rozpoznanych wpisów o znanej gramaturze;
- zapisane napoje i własny cel napojów;
- mocne strony, obserwacje i do trzech działań na kolejny tydzień;
- osobne średnie oraz liczby dni poprzedniego okresu.
Braki wpisów nie są zerami. Brak pomiaru warzyw nie jest dowodem ich braku w diecie.
Dane o napojach nie oznaczają pomiaru całkowitej wody ani stanu nawodnienia.

AI otrzymuje lokalnie obliczone liczby, liczbę dni, informację o pełnym zatwierdzeniu
oraz kontekst porównawczy poprzedniego okresu. Instrukcje zabraniają dopowiadania
błonnika, soli, cukrów dodanych, tłuszczów nasyconych, witamin, pór jedzenia,
rozkładu białka między posiłkami lub składników z samych nazw dań.
Zachowano analizę celu, trendu masy, aktywności, regeneracji i samopoczucia.
Nie zmienia się automatycznie celów użytkownika. Automatyczna propozycja korekty
nadal wymaga dłuższej obserwacji i precyzyjnych wartości składników; to odrębne
kryterium od kompletności zatwierdzonego dnia.

## Wdrożenie bez zbędnych uruchomień

W jednym commicie na gałęzi mangofit-fix wgraj nowy ZIP build101 i nowy codemagic.yaml.
YAML musi znajdować się w katalogu głównym pod nazwą codemagic.yaml.
Nie łącz tej konfiguracji ze starym ZIP — kontroluje SHA-256 i versionCode=101.
Identyfikator workflow pozostaje fitmango-build100, a jego nazwa wyświetla build101.
Zachowano pakiet com.example i dotychczasowy klucz z grupy fitmango_signing.
Kolejność CI: testy powiązane → wszystkie testy → lint → APK → weryfikacja podpisu.
Nie ma clean ani nowych automatycznych wyzwalaczy. Zachowano cache Gradle.
Nie uruchomiono Codemagic ani nie wywołano Gemini w trakcie przygotowania zmiany.

## Weryfikacja i ograniczenia środowiska

Dodano sześć testów NutritionWeekReviewTest obejmujących:
pełny zatwierdzony tydzień z szacunkami; odrzucenie nieaktualnych zatwierdzeń,
dzisiejszych dni i duplikatów; niezależne średnie poprzedniego tygodnia; cele
historyczne i aktualne; nieznane gramatury; brak automatycznej korekty z szacunków.
Zaktualizowano regresje EffectsAnalysisTest i ProgressAiContextTest.
Sprawdzono lokalnie składnię konfiguracji YAML, Bash i Python, wymagane pliki,
zgodność wersji i SHA-256 oraz integralność ZIP. Przeprowadzono przegląd zmian kodu.
Dodatkowo 12 przypadków wcześniejszej poprawki rozpoznawania oleju sprawdzono na JVM.
Nie wykonano testów Kotlin/Robolectric, Android lint ani budowy APK: środowisko
nie ma Android SDK, kompilatora Kotlin i cache zależności Gradle.
Nie wykonano podglądu ekranów na urządzeniu. Te sprawdzenia pozostają do wykonania
w przygotowanym CI. Nie przedstawiamy statycznego przeglądu jako wyniku testów Androida.

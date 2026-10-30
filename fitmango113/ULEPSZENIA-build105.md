# FITMango 1.0.2 build105 — wykres etapów redukcji

Efekty i analiza → Etapy redukcji → Ustaw etapy.
Przykład do zapisania: 3 tygodnie 1800 kcal, 3 tygodnie 1700 kcal,
3 tygodnie 1600 kcal. Można zmieniać datę startu, kalorie, długość etapów
(1–52 tygodnie), dodawać i usuwać etapy (1–12 etapów).
Przykład nie jest uruchamiany bez zapisania go przez użytkownika.

Wykres schodkowy pokazuje kalorie w czasie; długość stopnia odpowiada
liczbie dni. Zielony punkt wskazuje dzisiejszy dzień. Lista podaje kalorie,
numery tygodni, dokładne daty każdego etapu, bieżący etap oraz liczbę dni
do następnego etapu lub końca planu. Działa również bez pomiarów wagi.
Po zakończeniu planu nie są dopisywane kolejne obniżki.

Jest to harmonogram użytkownika, nie automatyczna rekomendacja dietetyczna.
Zapis planu nie zmienia celu w Profilu i nie zatwierdza proponowanej przez
analizę korekty. Rozbieżność celu profilu i planu na dziś jest pokazana obok
przycisku przejścia do Profilu. Istniejące kryteria analizy efektów pozostają.
Dane planu zapisują się w ustawieniach analizy objętych kopią zapasową.
Stare kopie bez planu zachowują zgodność; plan nie jest dopisywany samoczynnie.

Dodano ReductionPlanTest (4 scenariusze: daty korekt i koniec planu,
rok przestępny i własne długości, walidacja, zapis/odtworzenie kopii)
oraz ReductionPlanUiTest (2 scenariusze: edycja i zapis własnej długości,
wykres bez historii wagi i jego opis dostępności).

Lokalna próba uruchomienia testów zatrzymała się przed kompilacją:
brak lokalnej dystrybucji Gradle 9.3.1 i dostępu do services.gradle.org.
Nie wykonano testów Kotlin/Robolectric, lint, budowy APK ani podglądu UI.
Weryfikacja lokalna obejmuje integralność paczki, zgodność wersji i SHA-256,
parsowanie YAML oraz składnię skryptów Python/Bash w konfiguracji CI.
Testy Android i kontrola ekranu pozostają do uruchomienia w CI/na urządzeniu.

Wgraj ZIP build105 oraz codemagic.yaml w jednym commicie na mangofit-fix.
YAML musi mieć nazwę codemagic.yaml w katalogu głównym repozytorium.
Zachowano identyfikator workflow fitmango-build100 i dotychczasowy podpis.
Zmieniono krótki etap fail-fast na testy nowej funkcji i powiązanej analizy;
po nim pozostają pełne testy, lint, APK i kontrola podpisu.
Nie uruchomiono Codemagic podczas przygotowania tej poprawki.

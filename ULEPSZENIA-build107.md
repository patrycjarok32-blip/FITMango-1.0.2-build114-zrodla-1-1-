# FITMango 1.0.2 build107 — nowa nawigacja Profilu

Profil ma trzy zakładki:
- Mój profil: dane/kalorie/makro, zdrowie, brakujące produkty, historia zmian.
- Postępy: pomiary, zdjęcia, Renpho, efekty i etapy redukcji, ocena jadłospisu,
  analiza postępów AI.
- Ustawienia: wspólny ekran wyglądu i animacji, przypomnienia, zdjęcia potraw,
  Gemini, kopia zapasowa oraz informacje o aplikacji.

Zamiast dużych, rozwijanych kart są mniejsze kafelki. Dotknięcie całego kafelka
otwiera oddzielny widok wybranej funkcji, od razu z dostępnymi kontrolkami.
Nagłówek ma Wróć; systemowy przycisk Wstecz również obsługuje powrót.
Pozostałe ekrany aplikacji zachowują dotychczasowe rozwijane karty.
Wygląd i animacje mają jeden kafelek, a ekran zawiera ustawienia motywu,
przewodnika mango i ograniczenia animacji.

Trzy stałe skróty: Dodaj pomiar, Dodaj zdjęcia, Zobacz postępy.
Skrót zdjęć pokazuje formularz nowego zestawu przed historią zdjęć.
Zwykły kafelek zdjęć pokazuje najpierw historię, miniatury i porównywanie.
Skrót postępów otwiera istniejącą analizę efektów i etapy redukcji.
Przejście z efektów do celu w Profilu oraz z kalkulatora do efektów
zachowuje ścieżkę powrotu. Powrót z funkcji nie przenosi na początek listy.

Zapamiętujemy osobno pozycję trzech list i ostatnią zakładkę, także na kolejne
uruchomienie aplikacji. Stan szczegółów i ich przewijania jest zachowywany
przy powrocie oraz odtwarzaniu stanu ekranu. Dane nawigacji są lokalne;
nie zmieniają zapisanego profilu, pomiarów, zdjęć ani celów kalorii.

Kafelki pokazują krótkie informacje: cel kcal, liczbę zestawów zdjęć,
ostatnie pomiary, liczbę produktów do uzupełnienia, etapy planu, połączenie
Health Connect, liczbę przypomnień i ciszę, motyw/animacje, tryb Gemini,
stan zdjęć potraw oraz datę kopii z rozróżnieniem lokalnej i pliku.
Status Gemini oznacza tryb i obecność klucza, nie potwierdza poprawności klucza
ani dostępności zdalnego API. Otwarcie nowego Profilu nie wysyła analizy AI
ani nie zmienia ustawień; zachowano dotychczasową obsługę kopii automatycznych.

Zachowano funkcje build105/106: plan etapów redukcji oraz galerię, trwałe kopie,
naprawianie i porównywanie zdjęć sylwetki. Nie zmieniono schematu bazy Room.

Dodano ProfileNavigationUiTest: pięć scenariuszy dla połączenia ustawień wyglądu,
powrotu i przewijania, szybkiego dodawania zdjęć, odtworzenia stanu ekranu
oraz wczytania poprzedniej zakładki. Dwa istniejące testy ekranu Health Connect
przechodzą teraz przez kafelek, zamiast szukać sekcji na starej długiej liście.
Pozostałe dotychczasowe testy zachowano.

Lokalna próba uruchomienia testów zatrzymała się przed kompilacją: dystrybucja
Gradle 9.3.1 nie jest dostępna, a services.gradle.org jest nieosiągalne.
Nie uruchomiono Kotlin/Robolectric, lint, budowy APK ani podglądu na urządzeniu.
Sprawdzenia lokalne: bilans nawiasów zmienionych plików, zgodność katalogu
funkcji z obsługą ekranów, integralność ZIP i SHA-256, wersja107,
składnia YAML/Bash/osadzonego Pythona i ekstrakcja paczki przez skrypt CI.
Testy Android i ocena wyglądu na telefonie pozostają do wykonania w CI.

Wgraj ZIP build107 i codemagic.yaml w jednym commicie na mangofit-fix.
YAML musi mieć nazwę codemagic.yaml w katalogu głównym.
Workflow zachowuje identyfikator fitmango-build100, podpis i brak nowych
wyzwalaczy. Najpierw testy nawigacji i powiązanych funkcji, następnie pełne
testy, lint, APK i kontrola podpisu. Nie uruchamiano Codemagic w tej sesji.

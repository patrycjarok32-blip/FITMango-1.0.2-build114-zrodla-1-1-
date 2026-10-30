# FITMango 1.0.2 build109 — spójność odczytów i wpisywanie tekstu

Zrzuty build108: karta zdrowia miała 2272 kroki i 396 minut snu,
a karta sportu 97 kroków i 324 minuty. Obie używały osobnych zapisów,
odświeżanych w różnych terminach. Górny bilans obcinał kalorie (877),
a szczegóły zaokrąglały tę samą wartość (878).

Dodano wspólny wybór bieżącego odczytu CurrentActivitySupport:
- Pulpit przekazuje jeden wynik do obu kart; szczegóły Sportu używają
  tej samej reguły wyboru aktualnego wyniku.
- Kroki i sen pochodzą z najnowszego dostępnego odczytu dla bieżącego dnia.
  Remis preferuje zapis głównej synchronizacji. Nie wybieramy większej liczby:
  późniejsza korekta może obniżyć wynik.
- Brak pola w nowszym odczycie pozostawia dostępny wynik drugiego źródła.
  Zero kroków jest prawdziwą wartością; brak snu nie jest fikcyjnym zerem.
- Stara data głównej synchronizacji nie udaje dzisiejszych danych.
- Sen głównej synchronizacji pozostaje opisany jako ostatni odczyt, ponieważ
  może dotyczyć ostatniej dostępnej nocy; zapis sportowy przypisany do daty
  pobudki jest opisany „Sen po pobudce dziś”. Nie przepisujemy ostatniej nocy
  do innego dnia historycznego i nie zmieniamy zapisanych danych raportów.
- Kalorie bilansu i szczegółów używają zaokrąglania do najbliższej kcal.

Układ sportowych kafelków: przy szerokości poniżej 480 dp kroki/ruch mają
po pół wiersza, a sen pełną szerokość. Przy dużej czcionce kafelki są pionowo.
Szersze ekrany zachowują trzy kolumny. Format liczb i czasu jest wspólny.

Odciążenie pól tekstowych:
- Przepisy: indeks tekstu i metadanych jest tworzony w tle po zmianie danych,
  zamiast analizowania całych opisów i składników przy każdym znaku.
  Filtrowanie działa poza wątkiem UI; wyniki odświeżają się po 150 ms przerwy.
  Poprzednie wyszukiwanie jest anulowane, tekst pola aktualizuje się od razu.
  Animacje wyników nie restartują się dla każdego wpisanego znaku.
- Produkty lokalne: lista kandydatów jest budowana w tle raz na zmianę danych,
  a wyszukiwanie również działa w tle po krótkiej pauzie.
- Czat: pole wiadomości ma odrębny zakres kompozycji, więc pisanie nie przebudowuje
  historii wiadomości. Niewysłany szkic jest zachowywany przy odtworzeniu stanu.
- Profil: statusy kafelków i odczyt klucza nie są przeliczane przy każdej literze
  formularza. Sport: podsumowania tygodni są pamiętane do zmiany danych/daty.

Nie zmieniono schematu bazy, surowych pomiarów, ręcznych wpisów ani zasad
liczenia historycznych sesji. Zachowano wszystkie wcześniejsze poprawki.
To usunięcie rozpoznanych obciążeń kodu; nie wykonano pomiaru opóźnienia pisania
na urządzeniu użytkownika ani testu działania klawiatury na jego telefonie.

Dodano CurrentActivitySupportTest (4 testy), IndexedRecipeSearchTest
(8 scenariuszy w jednym teście dla biblioteki 1000 przepisów) i
ResponsivenessUiTest (3 testy: szerokość snu, wspólne wartości kart,
szkic czatu i jawne wysłanie). Istniejących testów nie usunięto.

Próba lokalnego uruchomienia trzech nowych klas zatrzymała się przed kompilacją:
brak dystrybucji Gradle 9.3.1 i połączenia z services.gradle.org.
Nie wykonano testów Android/Kotlin, lint, APK ani wizualnej kontroli na urządzeniu.
Lokalnie sprawdzono nawiasy zmienionych źródeł, zakres zmian, zachowanie modeli
bazy i poprzednich poprawek, ZIP/SHA-256/wersję109, YAML, skrypty Bash/Python
oraz rzeczywiste wykonanie etapu ekstrakcji paczki z CI.

Wgraj ZIP build109 bez rozpakowania i codemagic.yaml do katalogu głównego
repozytorium razem w jednym nowym commicie na mangofit-fix.
Najpierw uruchamiane są nowe i powiązane testy, potem pełny zestaw, lint i APK.
Nie uruchomiono Codemagic w tej sesji; podpis i identyfikator workflow zachowano.

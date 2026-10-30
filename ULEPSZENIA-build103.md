# FITMango 1.0.2 build103

## Przyczyna i poprawka

Build102 obsługiwał zbyt wąski zbiór nazw. Na nowych zdjęciach brakuje jogurtu,
mąki i masy jajek w sztukach. Dodatkowo „cynamonu” nie pasowało do „cynamon”,
więc przyprawa niepotrzebnie blokowała pełną sumę.

Rozszerzono lokalną tabelę do 13 profili i 76 jawnych aliasów oraz dodano 36
aliasów korzystających z 17 istniejących profili bazy aplikacji. Obsługiwane są
m.in. jogurt naturalny / jogurtu naturalnego, mąka orkiszowa jasna / mąki
orkiszowej jasnej, jajko / jajka, proszek / proszku do pieczenia, banan / banana,
jabłko / jabłka, cukier / cukru, masło / masła, jasna mąka pszenna, woda,
a także odmiany nazw płatków, kasz, ryżu, makaronu, twarogu i innych produktów
z dotychczasowego katalogu. Gdy import nie zawiera sum kalorii lub makro, aplikacja wylicza je z rozpoznanych składników bez potrzeby edytowania posiłku. Pusty przepis nie dostaje sztucznego zera. Kompletne sumy źródłowe nieedytowanej porcji zachowują pierwszeństwo.

Nie są to dane pobrane z etykiety konkretnej marki.
Przykładowe wartości z tabel są oznaczane jako szacunki.

Dla jajek w sztukach bez zapisanej gramatury stosujemy oznaczony szacunek
1 sztuka ≈ 50 g części jadalnej. Własna gramatura i własne wartości odżywcze
mają pierwszeństwo. Różne formy jednostki (szt., sztuki, sztuk) są rozpoznawane.
Zachowano podobne przeliczanie ząbków czosnku. Nie zgadujemy ilości, gdy jej brak.
Nie stosujemy profilu jasnej mąki do pełnoziarnistej, zwykłego jogurtu do smakowego
ani zwykłego masła do masła orzechowego.

Cynamonu, soli, pieprzu, kurkumy i pozostałe dodane odmiany nazw przypraw są
pomijane zgodnie z dotychczasowym wyborem użytkownika. Proszek do pieczenia,
cukier i masło pozostają liczone — to nie przyprawy do pominięcia.

Poprawka działa również dla istniejących wpisów po instalacji aktualizacji,
bez konieczności ponownego importu i bez nadpisywania oryginalnego tekstu przepisu.
Zachowano liczenie aktualnych składników po edycjach oraz rozróżnienie ilości
całego przepisu i podanych przez źródło sum dla porcji w nieedytowanych przepisach.
Nie usuwa się ostrzeżeń dla rzeczywiście nieznanych produktów i ilości.
Zachowano pełny status zatwierdzonych dni, analizę build101 i poprawkę statusu build102.

## Źródła wartości orientacyjnych

Jogurt naturalny i surowe całe jajko: USDA SR28, NDB 01116 i 01123:
https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/reports/sr28fg01.pdf
Jasna mąka orkiszowa — profil przykładowy Doves Farm White Spelt Flour:
https://www.dovesfarm.co.uk/products/organic-white-spelt-flour-1kg
Proszek do pieczenia — profil przykładowy Dr. Oetker:
https://www.oetker.co.uk/products/p/baking-powder
Owoce — USDA, NDB 09040 (banan), 09003 (jabłko); pozostałe profile ogólne
USDA: biały cukier, masło i jasna mąka pszenna. Woda ma zero kalorii i makro.
Wartości produktów mogą się różnić. Dane z zapisanej etykiety są nadrzędne.
Dotychczasowy katalog nie został zastąpiony nowymi, dublującymi tabelami.

## Testy i zakres weryfikacji

Dodano ImportedBreakfastNutritionTest obejmujący cały posiłek podobny do zdjęcia,
kalorie i makro składników, pomijanie przypraw, aktualne sumy, pierwszeństwo
zmierzonej masy jajek i zapisanych etykiet oraz odrzucanie niepasujących produktów.
Test posiłku zawiera też banana jako jawnie podany dodatkowy składnik testowy;
nie zakładamy, że to składnik niewidoczny na zdjęciu.
Lokalnie sprawdzono dane tabel i aliasy na JVM oraz wyrażenia regularne odczytane
ze źródeł: 41 przypadków nazw produktów i przypraw, zgodność aliasów katalogu,
pełną sumę testowego posiłku przed zaokrągleniem i po zaokrągleniu.
To walidacja danych i reguł na JVM, nie uruchomienie implementacji Kotlin.
Sprawdzono integralność ZIP, SHA-256, wersję 103, składnię YAML/Python/Bash,
rzeczywistą ekstrakcję źródeł z konfiguracji i odrzucenie złego SHA-256.
Pełnych testów Kotlin/Robolectric, Android lint, APK ani ekranów na urządzeniu
nie sprawdzono lokalnie: środowisko nie ma Android SDK i kompilatora Kotlin.

## Wdrożenie z oszczędzaniem limitu Codemagic

Podmień ZIP i codemagic.yaml w jednym commicie na gałęzi mangofit-fix.
YAML w głównym katalogu repozytorium musi mieć dokładnie nazwę codemagic.yaml.
Używaj dołączonej pary build103 ZIP + YAML; sprawdzane są SHA-256 i versionCode=103.
Identyfikator workflow pozostaje fitmango-build100, wyświetlana nazwa zawiera build103.
Zachowano dotychczasowy klucz, grupę fitmango_signing oraz pakiet com.example.
CI najpierw wykonuje test nowego posiłku i powiązane regresje, potem pełną serię,
lint, budowę APK i weryfikację podpisu. Bez clean i bez nowych automatycznych
wyzwalaczy; zachowano cache Gradle. Nie uruchomiono Codemagic ani Gemini.

Naprawiono też właściwą ścieżkę importu: kalkulator ImportedNutritionSupport
pomija przyprawy przed wymaganiem gramatury i profilu. Dzięki temu sam cynamon
nie blokuje obliczenia wartości całego importu. Liczenie respektuje liczbę porcji
źródłowych; dodano przypadek dwóch porcji. Zachowano kontrakt niskopoziomowego
calculateScaledNutrition — nowe uzupełnianie działa w obsłudze importu i posiłku.

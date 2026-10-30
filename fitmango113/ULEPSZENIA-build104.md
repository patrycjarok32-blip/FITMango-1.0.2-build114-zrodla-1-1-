# FITMango 1.0.2 build104 — wszystkie produkty z brakami kalorii

## Korekta testu zaokrąglania

Log pierwszego uruchomienia build104: 39/40 testów ukierunkowanych przeszło,
w tym wszystkie testy UniversalNutritionRepairTest. Jeden test śniadania miał
błędne oczekiwania: składniki dają 323,98 kcal, czyli 324 kcal po zaokrągleniu
sumy i 162 kcal przy dwóch porcjach. Poprawiono dokładne oczekiwania 325/163
na 324/162. Liczenie w aplikacji oraz zakres uzupełniania wszystkich produktów
pozostają zachowane. Nie usunięto ani nie pominięto testów.
Pełny zestaw testów i APK wymagają ponownego sprawdzenia w Codemagic.

## Zakres i obsługa

Profil lub Jadłospis → „Co wymaga uwagi?” → „Uzupełnij wszystkie braki”.
Lista obejmuje wszystkie aktywne składniki wszystkich zapisanych przepisów,
także bez zaplanowanych posiłków, oraz aktualne zamienniki i dodatki w jadłospisie.
Nie ogranicza się do nazw lokalnej tabeli. Przyprawy pozostają pomijane zgodnie
z wcześniejszym ustawieniem. Ten sam produkt z tą samą podstawą 100 g/ml i kodem
jest przetwarzany wspólnie we wszystkich jego wystąpieniach. Nie ma limitu 1000
produktów w operacji; długa lista na ekranie ma kolejne strony.

Operacja sprawdza stan zapisany w momencie uruchomienia. Nowe importy można
sprawdzić kolejnym uruchomieniem; aplikacja nie wysyła po cichu zapytań przy
każdym otwarciu ekranu. Znane profile z dotychczasowej tabeli nadal działają
automatycznie jak w build103.

Najpierw sprawdzane są zapisane dane produktów. Opcja „Szukaj także w Open Food
Facts” pozwala pobrać kolejne dane według nazwy lub kodu. Wysyłane są tylko te
identyfikatory, bez planu, profilu użytkownika i historii zdrowotnej. Nie używa
Gemini ani klucza AI. Wyłączona opcja sieciowa oznacza wyłącznie sprawdzenie lokalne.
Ustawienie „Tryb lokalny bez Gemini” nadal blokuje Gemini; opcja OFF jest odrębna.

Postęp obejmuje liczbę sprawdzonych i zapisanych produktów. Można przerwać;
zatwierdzone transakcje pozostają zapisane. Wyniki podają powód braku danych,
problemu sieci, niejednoznacznych rekordów lub brakującej masy. Raport można
zamknąć po przejrzeniu. Historia pokazuje źródło ostatniego uzupełnienia produktu
(w preferencjach objętych dotychczasową kopią zapasową), nie historię każdego pola.

## Dane i dokładność

Dopasowanie nazwy jest ścisłe po normalizacji wielkości liter, znaków i spacji;
nie wybieramy automatycznie produktu, którego nazwa tylko zawiera podobny wyraz.
Dla kodu kreskowego wymagany jest zgodny kod. Przy różnych danych pasujących
produktów aplikacja prosi o wybór właściwego rekordu. Samo sprawdzenie dowolnej
nazwy nie gwarantuje, że istnieje dla niej wiarygodny rekord. Nieznane dane
pozostają oznaczone; nie generujemy kalorii z domysłu ani nie wpisujemy fikcyjnych zer.

Można zapisać same znalezione kalorie na 100 g/ml. Brakujące makro pozostaje
brakujące (-1 w strukturach bazy), a prawdziwe zero pozostaje zerem. Nie wolno
zamieniać danych na porcję w dane na 100 g. Parser OFF korzysta z pól *_100g,
przelicza kJ na kcal i nie używa nieznormalizowanego energy-kcal_value.
Nie zamienia automatycznie gramów w mililitry i nie przypisuje kalorii produktu
do nieznanej masy sztuki. Jeśli kalorie profilu są znane, ale brakuje ilości,
przy składniku widać wartość na 100 g/ml i prośbę o ilość lub masę.

Zapis uzupełnia wyłącznie brakujące pola. Zostawia znane wartości, również zero,
a także nazwy, ilości, gramatury i wybrane zamienniki. Używa aktualnych danych
z bazy w chwili zapisu, aby nie przywrócić produktu usuniętego lub zmienionego
w trakcie wyszukiwania. Powtórzona nazwa w jednym przepisie nie tworzy podwójnych
rekordów. Masa szacunkowej sztuki nie zostaje zapisana jako zmierzona gramatura.

Zaktualizowano niezależne liczenie dostępnych sum: pełna suma kalorii nie ginie,
gdy nieznane jest makro. Taka sytuacja nadal ma oznaczenie braków makro.
Niepełna lista kalorii składników nie jest udawana jako kompletna suma całego dania.
Kompletne sumy źródłowe nieedytowanej porcji nadal mają pierwszeństwo; zachowano
liczbę porcji i zasady wcześniejszych świadomych edycji składników.

## Sieć i źródła

Źródło pobieranych danych: Open Food Facts (https://world.openfoodfacts.org),
baza ODbL. Przetwarzane są tylko odczyty, bez zapisu lub publikowania czegokolwiek
w Open Food Facts. Zwrócone dane nadal warto porównać z etykietą konkretnego produktu.
Dokumentacja pól:
https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_nutrition/
Zasady API i limity:
https://github.com/openfoodfacts/openfoodfacts-server/blob/main/docs/api/index.md
Zapytania tej operacji są sekwencyjne, rozdzielone co najmniej 6,5 s. Jedno
wyszukiwanie nie ukrywa dodatkowych żądań fallback. Odpowiedź tego samego zapytania
jest używana ponownie w obrębie operacji. Po trzech kolejnych błędach sieci
pozostałe lokalne dane są nadal sprawdzane, a brakujące wyszukiwania online
są jawnie oznaczone jako niewykonane — do ponowienia po odzyskaniu połączenia.

## Weryfikacja i CI

Dodano UniversalNutritionRepairTest: 12 scenariuszy dla całej biblioteki,
nieplanowanych przepisów, kodów i jednostek, sprzecznych rekordów, zachowania zer,
samych kalorii bez wymyślania makro, 1030 produktów, błędów sieci, cache,
anulowania, zapisu i ponownego odczytu z Room, zachowania gramatur, aktualizacji
sum posiłku oraz właściwej interpretacji energii z OFF.
Testy sieci używają wstrzykniętych odpowiedzi, nie prawdziwego internetu.
Sprawdzenia lokalne: integralność ZIP i SHA-256, wersja, składnia YAML/Bash/Python,
przegląd warunków i sygnatur kodu oraz ekstrakcja źródeł z konfiguracji CI.
Wykonano także poprzednią walidację tabel i 41 przypadków nazw na JVM.
Nie wykonano implementacji Kotlin, pełnych testów Robolectric, Android lint,
budowy APK ani podglądu ekranów: lokalnie brak kompilatora Kotlin i Android SDK.
Testy i uruchomienie na urządzeniu pozostają do wykonania w przygotowanym CI.

Wgraj ZIP build104 i codemagic.yaml w jednym commicie na mangofit-fix. YAML ma
być w katalogu głównym pod nazwą codemagic.yaml. Używaj wyłącznie tej pary,
ponieważ sprawdzane są versionCode=104 i SHA-256. Identyfikator workflow pozostaje
fitmango-build100, wyświetlana nazwa zawiera build104. Zachowano dotychczasowy
klucz podpisu i grupę fitmango_signing. CI najpierw uruchamia nowe i powiązane
testy, następnie pełny zestaw, lint, APK i kontrolę podpisu. Bez clean i bez
nowych automatycznych wyzwalaczy. Nie uruchomiono Codemagic podczas przygotowania.

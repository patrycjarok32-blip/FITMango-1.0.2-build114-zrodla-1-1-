# Aktualizacja build109 — odczyty i szybkość wpisywania

Wspólne wartości bieżącej aktywności, zaokrąglanie kcal, układ sportu na telefonie
i odciążenie wyszukiwania/czatu/Profilu. Szczegóły: ULEPSZENIA-build109.md.
Aktualna para: ZIP build109 + codemagic.yaml.

# Aktualizacja build108 — poprawka informacji o wersji

Numer nowości pochodzi z BuildConfig; naprawiono jedyny błąd z logu build107.
Szczegóły: ULEPSZENIA-build108.md. Aktualna para: ZIP build108 + codemagic.yaml.

# Aktualizacja build107 — nowy Profil

Trzy zakładki, mniejsze kafelki, skróty, statusy i osobne widoki funkcji.
Szczegóły: ULEPSZENIA-build107.md. Aktualna para: ZIP build107 + codemagic.yaml.

# Aktualizacja build106 — zdjęcia sylwetki

Miniatury, historia, podgląd, porównanie i trwały zapis zdjęć w Profilu.
Szczegóły: ULEPSZENIA-build106.md. Aktualna para: ZIP build106 + codemagic.yaml.

# Aktualizacja build105 — etapy redukcji

Wykres i edytowalne etapy w „Efekty i analiza”. Szczegóły: ULEPSZENIA-build105.md.
Aktualna para do CI: ZIP build105 + codemagic.yaml.

# Aktualizacja build104 — uzupełnianie wszystkich brakujących produktów

Profil lub Jadłospis → „Co wymaga uwagi?” → „Uzupełnij wszystkie braki”.
Operacja obejmuje wszystkie zapisane przepisy i jadłospis, a nie zamkniętą listę
nazw. Najpierw korzysta z zapisanych profili, potem opcjonalnie z Open Food Facts.
Pokazuje postęp, wyniki wymagające wyboru i pozostałe braki. Szczegóły:
ULEPSZENIA-build104.md. Aktualna para: ZIP build104 + codemagic.yaml.

# Aktualizacja build103 — brakujące kalorie po imporcie

Rozszerzono rozpoznawanie produktów i odmian nazw oraz przeliczanie jajek w sztukach.
Przyprawy typu „cynamonu” nie blokują kompletności. Własne dane mają pierwszeństwo.
Aktualna para plików: ZIP build103 oraz codemagic.yaml. Szczegóły: ULEPSZENIA-build103.md.

# Aktualizacja build102 — automatyczne dane składników

Rozpoznawanie odmienionych nazw działa również na już zaimportowanych przepisach:
czosnku, cebuli szalotki i oliwy z oliwek. Zapisane wartości mają pierwszeństwo;
wartości z tabel i masa ząbków są oznaczone jako szacunkowe.
Szczegóły: ULEPSZENIA-build102.md. Aktualną konfiguracją jest para build102 ZIP + codemagic.yaml.

# Aktualizacja build101 — analiza tygodnia

Zatwierdzony dzień jest pełnym, potwierdzonym zapisem i wchodzi do średnich,
także gdy wartości odżywcze są szacunkowe. Nowa karta „Ocena jadłospisu tygodnia”
znajduje się w Profilu i Sport → Raport. Szczegóły wdrożenia: ULEPSZENIA-build101.md.

# FITMango 1.0.2 build100

Zakupy: kalendarz od–do (oba dni włącznie), zapis wyboru i powrót do bieżącego tygodnia.
Obsługa pojedynczego dnia, przełomu tygodni i roku. Ręczne produkty i Mam są zachowywane
osobno dla każdego zakresu. Pełne tygodnie zachowują zgodność z wcześniejszymi listami.
Automatyczne przeliczenia odświeżają również zapisane zakresy.

Uzupełnij produkty: pomijanie przypraw i znikanie po zapisie pełnych wartości
odżywczych. Brak gramatury nadal wymaga uwagi w posiłku, bez powtarzania prośby o makro.
Dodano ShoppingRangeTest i NutritionAttentionQueueTest; zaktualizowano EverydayConvenienceTest.

Gradle nie został uruchomiony: Network is unreachable przy pobieraniu dystrybucji.
Pełne testy, lint i kompilacja do potwierdzenia w Codemagic.
Wgraj ZIP oraz codemagic.yaml do gałęzi mangofit-fix i uruchom fitmango-build100.
Zachowaj dotychczasową grupę podpisu fitmango_signing.

# FITMango 1.0.2 build99

Build98: 322 testy, 321 zaliczonych; pozostał timeout testu po ręcznym zatrzymaniu
zegara Compose. Poprzednie próby synchronizacji tego testu nie rozwiązały problemu.

Rozdzielono testowanie etapów prezentacji od testu interakcji UI. Produkcyjny
ShoppingMotionSupport wyznacza beforeMove (przekreślony produkt na dotychczasowym
miejscu) i afterMove (przeniesienie do Mam lub ukrycie), bez zegara/Androida.
rememberShoppingRows używa dokładnie tych etapów i dotychczasowego 160 ms przejścia.
Projekcja na aktualne dane nadal zapobiega starym nazwom/ilościom oraz usuniętym wierszom.

ShoppingMotionStagesTest sprawdza wcześniejszą pozycję z zaznaczeniem, końcową kolejność,
cofnięcie, zwiniętą sekcję Mam, ograniczone animacje, aktualizacje danych i usuwanie.
Dotychczasowy test UI korzysta z rzeczywistego Checkbox i normalnego zegara. Sprawdza
zaznaczenie, końcowe pozycje i odznaczenie/przywrócenie kolejności. Wszystkie wcześniejsze
warunki pozostają sprawdzane; asercje etapu pośredniego przeniesiono do testu produkcyjnych
etapów. Nie ma Ignore, retry ani pomijania testów. Zachowano wszystkie pozostałe testy,
lint i kontrolę integralności/versionCode/podpisu APK. Danych użytkownika nie zmieniono.

Lokalny Gradle jest blokowany przy pobieraniu dystrybucji (Network is unreachable).
Pełny przebieg testów i wynik kompilacji muszą zostać potwierdzone w Codemagic.

# FITMango 1.0.2 build98

Log build97 ponownie wykazał 319/320 testów i timeout w teście animacji zakupów.
Poprzednia synchronizacja za pomocą advanceTimeUntil była niewystarczająca.

ShoppingMotion prezentuje teraz stan każdego istniejącego produktu bezpośrednio z
aktualnego wejścia repozytorium. Staging zachowuje jedynie kolejność: przekreślenie,
ilość/nazwa i usunięcie nie czekają na mutację w LaunchedEffect. Po krótkiej fazie
przekreślenia kolejność nadal zmienia się animacją, a Mam pozostaje zwijane.

Test używa waitUntil z ograniczeniem 5 s czasu rzeczywistego, aby oddać sterowanie
Android/UI dispatcherowi obsługującemu powiadomienia snapshotów. W każdej iteracji
przesuwa zegar Compose o jedną klatkę. Zaznaczony commit potwierdza SideEffect,
następnie waitForIdle synchronizuje layout. Zachowano wszystkie asercje:
zaznaczenie przed przeniesieniem, końcowa kolejność i cofnięcie zaznaczenia.
Nie zwiększono czasu animacji, nie pominięto testu ani asercji.

Nowy test sprawdza aktualizację ilości i nazw razem z zaznaczeniem oraz usuwanie
produktów, bez commitów starych wartości. Osobny test zmiany tygodnia sprawdza brak
pozostałych wierszy z poprzedniej listy. Baza danych i obliczenia bez zmian.
Pełna kompilacja i ponowne testy wymagają Codemagic.

# FITMango 1.0.2 build97

Poprawka synchronizacji MotionPolishUiTest. Log build96: 320 testów, 319 zaliczonych,
1 niezaliczony. Test odczytywał semantykę po pierwszej klatce, zanim LaunchedEffect
zastosował aktualny stan prezentacji zakupów. Teraz SideEffect potwierdza commit
zaznaczonego produktu, a kontrolowany zegar testu czeka na ten commit maksymalnie
120 ms (wewnątrz 160 ms fazy przekreślenia). Kolejna klatka i waitForIdle synchronizują
semantykę/layout. Nadal sprawdzamy zaznaczenie przed przeniesieniem, końcową kolejność
i przywrócenie po odznaczeniu; żaden test ani kontrola nie zostały wyłączone.

Kod działania aplikacji jest identyczny jak build96 poza numerem i informacją o wydaniu.
Nie dodano bibliotek. Pełny powtórny przebieg testów wymaga Codemagic.

# FITMango 1.0.2 build96

Naprawa modyfikatora reakcji na dotyk: poprzednie prywatne InteractionSource nie było
podłączone do Button/clickable. Teraz dekoracja obserwuje rzeczywisty strumień wskaźnika,
nie konsumuje gestów ani nie wykonuje akcji, a anulowanie gestu zwalnia stan naciśnięcia.
Istniejące przyciski zachowują obsługę kliknięć, przewijania i stan disabled.

Karty ustawień i Start mają jeden właściciel animacji wejścia/wyjścia (220 ms),
zamiast natychmiastowego usuwania treści podczas zwijania. Profil → Wygląd i animacje
pokazuje aktywny stan ruchu i lokalny przycisk Sprawdź animacje. Ustawienia dostępności
Androida i ograniczenie ruchu w aplikacji pozostają respektowane; stan jest odświeżany
po powrocie do aplikacji. Podgląd nie zapisuje danych.

Zakupy: po rzeczywistym zapisie checked w repozytorium aktualny wiersz zostaje przekreślony
na 160 ms, po czym lista przenosi go na koniec (220 ms) lub ukrywa w zwiniętym Mam.
Stan prezentacji resetuje się przy zmianie tygodnia/filtra; cofnięcie zaznaczenia działa.
Bieżący tydzień poniedziałek–niedziela i dane zakupów pozostają bez zmian.

Jadłospis: faktycznie pierwszy niezjedzony posiłek ma znacznik Najbliższy posiłek.
Kolorowa krawędź i subtelne tło odróżniają kategorie. Po oznaczeniu jako zjedzone karta
kończy wyjście przed usunięciem z prezentacji, a przewijanie wskazuje kategorię kolejnego
posiłku (uwzględnia cztery karty narzędziowe i nagłówek kolejki).

Gotowanie: kierunkowe przejścia 200 ms i pasek postępu, oddzielne stany przewijania
wchodzącego/wychodzącego kroku. Zaznaczone składniki zachowane według identyfikatora kroku;
wychodzący krok nie przyjmuje już kliknięć. Nie przywracamy minutnika.

Większe liczby z mniejszymi jednostkami, fiolet snu we wskaźnikach i wykresach,
ilustracje offline w pustych wynikach przepisów i zakupach. Bez nowych zależności.
Nie zmieniono bazy Room, obliczeń kalorii, importu Health Connect ani zatwierdzeń dni.

Dodano MotionPolishUiTest: dotyk rzeczywistego przycisku/disabled, etap przekreślenia i
kolejność zakupów po zaznaczeniu/cofnięciu, ograniczony ruch i podgląd oraz zachowanie
zaznaczeń przy przejściu między krokami gotowania. Testy oraz pełna kompilacja nie zostały
uruchomione lokalnie: wrapper Gradle nie może pobrać dystrybucji (Network is unreachable).
Codemagic zachowuje czyste assemble, wszystkie testy, lint i bramki integralności APK.

# FITMango — build91, wygląd według referencji AI Studio

## Przeliczanie po uzupełnieniu produktu — build91

W logu Codemagic poprzedniej wersji: 293 testy, 292 zaliczone, jeden wykrył błąd zachowania starego podsumowania dla posiłku bez zamienników po zapisaniu profilu produktu. Dodano pominięcie tej ścieżki, gdy przepis zawiera produkt z jawnie zapisanymi wartościami: liczymy aktualne składniki również bez zamian. Nieudostępniona ilość pozostaje nieznana. Test zachowuje wymaganie 123 kcal w obu posiłkach oraz dodatkowo sprawdza podwójną porcję, makro i sumę dnia. Pełne ponowne wykonanie testów wymagane w Codemagic.

## Poprawka kompilacji — build90

Log kompilatora build89 wskazał trzy odwołania do `remembered` poza zakresem zmiennej w budowaniu kandydatów lokalnej bazy. Każdy składnik wyszukuje teraz własny zapamiętany profil po nazwie; brakujące kalorie i makro mogą z niego korzystać. Pełne zapisane wartości nie są zastępowane. Rozszerzono test uzupełniania produktu o lokalne wyszukiwanie i odczyt czterech wartości. Funkcje build89 i schemat bazy pozostają zachowane. Kompilacja oraz pełne testy Android wymagają ponownego uruchomienia w Codemagic.

## Sześć usprawnień codziennej obsługi — build89

- Wspólna, przeszukiwalna lista brakujących danych w Jadłospisie i Profilu. Uzupełnione wartości na 100 g/ml są ponownie używane dla identycznego produktu. Nieznana ilość nadal wymaga sprawdzenia w posiłku. Przyprawy są pomijane. Różne kody kreskowe nie są łączone, a pełne profile innych produktów pozostają bez zmian. Słownik wartości jest objęty lokalną kopią zapasową.
- Zakupy wyłącznie bieżącego tygodnia; produkty oznaczone Mam pozostają przekreślone, na końcu, i są domyślnie ukryte pod przyciskiem Pokaż Mam.
- Zapis posiłku jako własnego zestawu bezpośrednio w jego szczegółach; wybór docelowej daty dla zestawów. Zachowane porcje i zamienniki.
- Przy pomiarach składu ciała widoczne źródło i czas ważenia, a nad listą — ostatni zakończony odczyt Health Connect. Nie mieszamy dat ważenia i synchronizacji.
- W trybie gotowania można wybrać liczbę porcji; ilości i jawne połowy składników przeliczają się od razu. Zmiana nie modyfikuje zapisanego jadłospisu. Zmiana porcji resetuje zaznaczenia robocze.
- Historia ostatnich 10 zmian posiłków oraz 10 zmian pomiarów z bieżącego uruchomienia. Dostępna w Profilu, Jadłospisie i Pomiarach. Cofanie jest chronione porównaniem obecnego zapisu z wynikiem danej operacji; nowe dane nie są nadpisywane. Zamknięcie baneru nie usuwa historii. Przywrócenie kopii usuwa nieaktualną historię.

Brak zmian schematu bazy. Dodane testy obejmują uzupełnianie wartości, zestawy, porcje oraz wielokrotne cofanie i ochronę nowszych pomiarów. Pełna kompilacja i wykonanie testów Android pozostają wymagane w Codemagic.

## Bieżące zachowanie — build88

Zachowano wcześniejsze poprawki: Zakupy wyłącznie bieżący poniedziałek–niedziela; zapis przez repozytorium; gotowanie bez minutnika z przekreślaniem składników osobno w każdym kroku, ilościami po zmianach porcji i zamienników oraz jawną połową ilości. Testy migracji wykorzystują schemat starej tabeli bez DROP COLUMN, a opis wydania nie wymaga sztywnej liczby punktów.

Automatyczny odczyt dostępnych pomiarów Renpho działa poprzez Health Connect po nadaniu uprawnień, przy powrocie do ekranu i przy synchronizacji zdrowia. Można go wyłączyć. Nie tworzymy brakujących wskaźników. Pozostałe pola można opcjonalnie uzupełnić w schowanej sekcji dodatkowej.

Ostatnie produkty mają jedną listę z wyszukiwaniem, zapamiętaną ilością, usuwaniem wpisów i rozwinięciem po trzech pozycjach. Usunięto powtórny blok szybkiego dodawania. Stare powtórzenia normalizujemy, zachowując ulubione i porcje; różne kody kreskowe pozostają osobnymi produktami.

Kompilacja i pełne testy wymagają uruchomienia w Codemagic.

## Składniki w trybie gotowania — build83

Przy kroku widoczna jest robocza lista składników powiązanych nazwą z instrukcją, z ilościami. Dla posiłku z jadłospisu używane są aktualne zamienniki i ilości po przeliczeniu porcji. „Użyte” ukrywa produkt w całej bieżącej sesji gotowania, a „Użyte — przywróć” pozwala cofnąć zaznaczenie. Przepis, jadłospis i bilans nie są modyfikowane. Pełna lista pozostałych składników jest dostępna również dla instrukcji ogólnych typu „wymieszaj pozostałe składniki”. Dopasowanie jest tekstowe; ilości dotyczą całego przygotowywanego dania, a nie automatycznie wyliczonych części w kolejnych krokach.

## Zakupy według tygodnia — build83

Lista zakupów ma wybór tygodnia od poniedziałku do niedzieli. Generator sumuje tylko posiłki z datami należącymi do tego tygodnia (z aktualnymi zamiennikami i porcjami). Produkty oznaczone „mam” są przekreślone, przygaszone i przeniesione pod brakujące; zaznaczenia są niezależne dla każdego tygodnia i kategorii. Dodane ręcznie produkty należą do wybranego tygodnia i nie znikają po ponownym generowaniu.

Migracja bazy 8→9 zachowuje dotychczasową zbiorczą listę pod opcją „Starsza lista bez przypisanego tygodnia”; nie przypisuje jej arbitralnie do bieżących dat. Starsze posiłki bez daty wymagają przypisania tygodnia w jadłospisie, zanim wejdą do zakupów. Nowe kopie zapasowe mają schemat 9; przywracanie kopii 8 pozostaje obsługiwane.

Kompilacja Android, testy jednostkowe/Robolectric i lint pozostają wymagane w Codemagic przed publikacją APK.

## Usuwanie pomiarów i odczyt Renpho ze zrzutów — build83

Pomiary & Progres: każdy wpis ma Usuń i potwierdzenie z datą/wagą. Operacja usuwa konkretny
BodyMeasurementEntity i odpowiadające wpisy WeightLogEntity o tej samej dacie i wadze tylko wtedy,
gdy żaden inny pomiar ciała z tego dnia ich nie potrzebuje. Różne formaty dat PL/ISO są rozpoznawane.
Pozostałe wagi/obwody pozostają. Nie zmieniamy bieżącego profilu ani nie kasujemy danych w Renpho/HC.
Dodano dwie operacje DAO @Delete bez zmiany encji lub schematu Room.

Dla usuniętego wpisu Health Connect utrwalamy ID w FITMangoUsability. Synchronizacja respektuje
ukryte ID pod wspólną bramką zapisu, a dodatkowa historia wagi Renpho nie odtwarza tego rekordu
w lokalnych analizach. Lista ID jest walidowana i objęta kopią/odtwarzaniem. Błąd transakcji bazy
przywraca poprzednią listę ID. Wpisy usunięte w sekcji składu ciała nadal mają własne zasady
usuwania lokalnego; ponowny import tych innych wskaźników może je przywrócić.

Profil → Renpho — skład ciała → Importuj zrzuty Renpho: wybór 1–6 obrazów jednego ważenia.
Lokalny, już dołączony model ML Kit OCR, bez Gemini/klucza/wysyłania zdjęcia. Czytnik obsługuje
trzykolumnowy ekran Renpho z polskimi/angielskimi etykietami dla wszystkich 13 wskaźników.
Zachowuje kolumny, rozpoznaje łamane etykiety i wartości z jednostkami/przecinkiem. Nie wybiera
małych porównawczych zmian +/−; duże wartości są odróżniane geometrią. Nieczytelne pola pozostają
puste, a sprzeczne wartości z wielu zrzutów wymagają uzupełnienia ręcznego. OCR może się mylić.
Nie gwarantujemy pełnego odczytu każdego zdjęcia lub innego układu ekranu.

Odczyt nie zapisuje automatycznie pomiaru: formularz pokazuje liczbę pól, pozwala poprawić wynik
i wymaga potwierdzenia daty oraz wartości. Data porównawcza na zrzucie nie jest automatycznie
używana. Wpis ma źródło Renpho — odczyt ze zdjęcia. Pozostaje edycja ręczna i import dostępnych
rekordów HC; nie obiecujemy pobrania wszystkich 13 typów przez Health Connect.
Timeout 60 s i błąd galerii są obsłużone; inne operacje panelu są blokowane w trakcie odczytu.
Dodano RenphoScreenshotImportTest (13 pól, układ kolumn, porównawcze zero, konflikty, łamane etykiety)
i MeasurementRemovalTest (prawdziwa baza, duplikat dnia, powiązane logi, ukryte ID i kopia).
Kompilacja/testy Androida wymagają uruchomienia w Codemagic.


## AI respektuje zatwierdzenie dnia — build81

Warstwa raportu AI traktuje każdy zakończony dzień z aktualnym zatwierdzeniem użytkownika
jako zatwierdzony, także jeśli użytkownik zaakceptował szacunkowe wartości składników.
Dni te wchodzą do średnich kalorii/makro i bilansu wspólnych dat ze spalaniem. Licznik jest
spójny w lokalnym nagłówku, kontrolnych faktach i sekcji analizy przekazywanej modelowi.
Przy 7 takich dniach raport pokazuje 7/7 zatwierdzonych dni; szacunkowość opisuje wartości
odżywcze, nie nazywa dnia niepełnym ani niezatwierdzonym.

System prompt jawnie zakazuje nazywania zatwierdzonego dnia niepełnym. Z opisu posiłków
wyłącznie w kopii dla AI usuwa końcowy znacznik „niepełne dane” dla zatwierdzonych dni.
Nie zmienia utrwalonych wpisów ani fingerprintów. Usunięto sprzeczne liczniki i opisy
ze starszego raportu opartego tylko na w pełni rozpoznanych składnikach.

Brak wpisu, faktyczny brak aktualnego zatwierdzenia i dzisiejszy dzień są nadal odrębne.
Nie zmieniono danych źródłowych, zapisu zgody na szacunkowe wartości ani ostrożnych warunków
liczbowej korekty kalorii w długoterminowym silniku. Sam raport nie zmienia celu.
Zaktualizowane testy sprawdzają średnią/bilans zatwierdzonych dni ze szacunkami i pełny tydzień
7/7 bez określania zatwierdzonych dni jako niepełnych. Kompilacja/testy wymagają CI.


## Wiarygodniejsze fakty w raporcie AI — build80

Nowa warstwa ProgressEvidence oblicza lokalnie jakość danych, średnie kcal/B/W/T oraz różnicę
względem celów kalorii zapisanych dla tych samych dni. Uwzględnia tylko kompletne, aktualnie
zatwierdzone i zakończone dni; niepełne, niezatwierdzone i brakujące dane są oddzielnie policzone.
Dzisiejszy dzień nie wchodzi do tej warstwy, także w dłuższych analizach.

Bilans Health Connect sumuje jedzenie minus odczytane spalanie wyłącznie ze wspólnych dat
kompletnego jedzenia i dodatniego odczytu z pełnym oknem doby. Daty są przekazywane AI.
Nie przelicza bilansu na utratę tłuszczu. Pełne okno zapytania nie gwarantuje kompletnego pomiaru.

Dla krótkiego okresu porównuje średnią masy z pierwszych trzech i ostatnich trzech dni, wymagając
co najmniej dwóch różnych dat w każdym, bez nachodzenia okien. To obserwacja masy, nie kg/tydzień,
metabolizm ani zmiana tłuszczu/mięśni. Brak danych daje null i wyraźne ograniczenie.
Trendy długookresowe i warunki korekt kalorii pozostają bez zmian.

Różnice Renpho są obliczane osobno dla każdego wskaźnika i źródła, z dwóch różnych dat; wiele
pomiarów tego samego dnia daje ostatni odczyt. Zmiany procentów opisuje w punktach procentowych.
Inne źródła i kg/% nie są łączone. BIA nadal ma ograniczenia — nie gwarantujemy dokładnej diagnozy
lub prognozy. Wynik AI jest komentarzem do liczb lokalnych i może się mylić.

Nagłówek raportu zawiera czas wygenerowania i zakres. Użytkownik widzi lokalne podsumowanie
przed odpowiedzią AI; po zmianie danych ponownie generuje raport. Nie ma automatycznej zmiany
celów ani wysyłania w tle. Dodano ProgressEvidenceTest: selekcja kompletnych dni, wspólne daty,
braki zamiast zer, nieprzecinające się okna masy i różnice BIA z tego samego źródła.
Kompilacja/testy Androida są wymagane w Codemagic; kontrola struktury ich nie zastępuje.


## Renpho — skład ciała — build79

Profil → Renpho — skład ciała: ręczny wpis 13 wskaźników z jednego ważenia: waga, BMI, tłuszcz %, 
tłuszcz podskórny %, wskaźnik tłuszczu trzewnego, mięśnie kg, mięśnie szkieletowe %, kości kg,
beztłuszczowa masa kg, wiek metaboliczny, BMR kcal/dobę, woda % i białko w ciele %.
Data jest wybierana w kalendarzu. Nie wprowadzamy automatycznie danych ze zrzutu bez daty ważenia.
Puste pola pozostają brakami, przecinek dziesiętny jest obsługiwany. Nieprawidłowe wartości i przyszłe
daty blokują zapis. Zakresy walidacji są ograniczeniami aplikacji, nie oceną medyczną.

Osobny przycisk uprawnień i importu Health Connect odczytuje dostępne rekordy wagi, tłuszczu,
beztłuszczowej masy, kości, masy wody i BMR z ostatnich 30 dni. Masa wody kg jest przechowywana
osobno od procentu wody; nie przeliczamy jej przy użyciu wagi z innej daty. Import nie gwarantuje,
że Renpho udostępnia wszystkie te typy. Inne wskaźniki wymagają wpisu ręcznego. Brakujące zgody
nie blokują odczytu pozostałych udostępnionych typów. Błąd typu daje komunikat i zachowuje historię.
Rekordy mają ID, datę, czas i rzeczywiste źródło; ponowny import jest idempotentny. Pomiary z różnych
źródeł/czasów nie są scalane w jedno rzekome ważenie. Ostatnie wartości pokazują własne daty/źródła.
Historia pokazuje ostatnie 20 zapisów, a magazyn zachowuje 5000 najnowszych rekordów. Usunięcie lokalne
nie usuwa rekordu w Health Connect; kolejny import może go przywrócić.

Nowy JSON w FITMangoUsability jest walidowany i objęty istniejącym eksportem/odtwarzaniem kopii.
Nie ma zmiany schematu Room ani usuwania danych. Zapis jest chroniony bramką danych i wykrywa
zmianę historii podczas odczytu. Import/sam wpis nie zmienia profilu, celu kalorii ani starej historii wagi.
Do lokalnego raportu AI dodatkowe wagi uzupełniają jedynie daty bez istniejącego pomiaru wagi.

Raport AI otrzymuje maks. 300 rekordów Renpho tylko z wybranego okresu (Profil: 7 zakończonych dni).
BIA jest opisane jako szacunek zależny od warunków i nawodnienia. Model nie sumuje nachodzących
kategorii, nie traktuje białka w ciele jako białka w diecie ani wieku metabolicznego jako diagnozy.
BMR z wagi nie zastępuje zapotrzebowania i nie jest do niego dodawane. Nie obiecujemy większej
precyzji metabolizmu wyłącznie z liczby wskaźników. Cele nadal wymagają osobnego zatwierdzenia.
Dodano ScaleMeasurementsTest: 13 pól/jednostki, walidacja, kopia, deduplikacja, filtry tygodnia,
niezależność celów i uzupełnianie brakujących dat wagi. Kompilacja/testy Androida wymagają CI.
Źródła: https://developer.android.com/health-and-fitness/health-connect/data-types
https://renpho.com/pages/faq-for-renpho-health-app


## Podsumowanie AI po tygodniu — build78

Profil → Analiza postępów AI → Podsumuj tydzień z AI uruchamia analizę ostatnich 7 zakończonych
dni (D−7 do D−1), bez dzisiaj. Np. 17 października: 10–16 października. Zakres dat jest
w nagłówku wyniku i kontekście AI. Przycisk uruchamia raport na żądanie, nie w tle.

Tylko te daty obejmują zjedzone posiłki, makro, zatwierdzenia, sesje treningowe, spalanie ogółem,
pomiary wagi/obwodów oraz samopoczucie. Nie dodajemy miesiąca ani poprzedniego tygodnia.
Ostatnia migawka kroków/snu jest dołączana tylko wtedy, gdy pochodzi z tego zakresu.
Brakujące dni nie są zerowane; zbyt mało pomiarów oznacza brak pewnej oceny trendu.
Tygodniowy raport nie spełnia długoterminowych warunków liczbowej korekty kalorii.
Pozostałe zakresy 14/30/90 dni w Efektach zachowują dotychczasowe działanie.
Dodano testy dokładnych granic D−7 i D−1 oraz wykluczenia dzisiaj i starszych wpisów.
Kompilacja i pełne testy są wymagane w Codemagic; kontrola struktury nie jest kompilacją.


## Analiza AI: posiłki, aktywność i kierunek celu — build77

Profil → Analiza postępów AI przekazuje kontekst ostatnich 30 dni do Gemini po kliknięciu.
To jawnie opisane na ekranie. Tryb lokalny i dotychczasowe sprawdzanie klucza nadal blokują
zapytania. Nazwa użytkownika, zdjęcia i klucze nie są w nowym kontekście.

Dołączane są zapisane zjedzone posiłki (nie plany), kcal/B/W/T, cel jedzenia zapisany dla dnia,
zatwierdzenie i kompletność, napoje oraz warzywa/owoce. Osobno aktualny cel i szacowane utrzymanie.
Treningi pochodzą z ostatnio odczytanej historii Health Connect: daty, nazwa, minuty, źródło,
opcjonalny dystans/kalorie. Spalanie ogółem ma własne daty i informację o pełnym oknie doby.
Ostatnia migawka kroków/snu ma datę i nie jest przedstawiana jako średnia miesiąca. Nie wykonujemy
cichego dodatkowego odczytu; świeże dane trzeba zsynchronizować w Sporcie lub Health Connect.

Wspólny raport lokalny wnosi trend i daty pomiarów wagi, zmianę talii, jakość/kompletność zapisów,
samopoczucie i dopuszczalną propozycję korekty. AI ma oddzielać zgodność realizacji celu jedzenia
od obserwowanego trendu; zaznaczać braki, nie diagnozować ani obiecywać zmiany tłuszczu/mięśni.
Nie podwaja kalorii treningu/BMR, nie zmienia celu i nie proponuje obniżenia kalorii bez propozycji
lokalnego raportu. Brak wpisu nie oznacza zera; nazwa posiłku nie dowodzi pełnego składu.

Kontekst jest ograniczony do 10 nazw posiłków na dzień i 100 szczegółów sesji; podaje liczby
pominiętych nazw/szczegółów. Powtórzone ID sesji są scalane. Dane JSON są traktowane jako dane,
nie instrukcje z nazw przepisów. Historia sesji obejmuje zwykle 30 dni, także w analizie 90-dniowej.
Błąd przygotowania jest wyświetlany również w Profilu. Przycisk używa właściwego stanu ładowania.
Dodano ProgressAiContextTest (zakres dat, posiłki/makro/cel, braki, zatwierdzenie, sesje,
nieznane wartości, daty odczytów i ograniczanie kontekstu). Testy wymagają uruchomienia w CI.


## Historia treningów bez dziennika — build76

Ekran Sport otwiera bezpośrednio historię treningów z ostatnich 30 dni. Usunięto zakładkę
Dziennik, formularz serii, ręczne operacje na ćwiczeniach i kalendarz dziennika. Cel tygodniowy
liczy wyłącznie dni z sesjami treningowymi odczytanymi z Health Connect. Minutnik przerw zostaje.
Starsze wpisy serii/plany pozostają w lokalnych kopiach, lecz nie są już dodawane do pierścienia.
Żadne dane użytkownika ani schemat Room nie są usuwane.

Naprawiono obsługę pageToken: null oraz pusty/pustobiały token kończą odczyt. Na starszych
wersjach Health Connect pusty string może oznaczać koniec, a wysłanie go ponownie odczytywało
pierwszą stronę i powodowało błąd powtarzania. Opaque niepuste tokeny są przekazywane bez zmian.
Rzeczywiste zapętlenie, limit stron/liczby rekordów i timeout nadal przerywają odczyt; poprzednia
historia zostaje. Odczyt nie zwraca częściowego wyniku jako kompletnej historii.
Źródło: https://developer.android.com/health-and-fitness/health-connect/read-data

Wymagana jest zgoda na treningi i wpisy sesji udostępnione przez aplikację źródłową (np. Zepp).
Same kroki, sen lub kalorie nie są zastępowane wymyślonymi treningami. Dystans/kalorie sesji są
opcjonalne i nie są doliczane ponownie do jedzenia. Dodano WorkoutHistoryPaginationTest oraz
zaktualizowano testy kontrolek synchronizacji i liczenia aktywnych dni.


## Kalkulator profilu — build75

Profil zawiera szczegółowy opis codziennego ruchu/pracy, opcjonalne kroki z typowego tygodnia,
typ treningu, częstotliwość, czas i intensywność. Wynik pokazuje oddzielnie spoczynkowy szacunek
Mifflina–St Jeora, zapotrzebowanie na utrzymanie, deficyt/nadwyżkę i cel jedzenia oraz makro.
Deficyt można wybrać od 5 do 20%, nadwyżkę od 2 do 10%; utrzymanie ma korektę 0%.
Białko: 1,2–2,0 g/kg (maks. 30% energii), tłuszcz ok. 25%, reszta węglowodany.
To propozycja ogólna; ręczne cele kalorii i makro nadal są dostępne.

Dobór PAL w trybie szczegółowym jest jawną heurystyką aplikacji, nie zwalidowanym pomiarem
metabolizmu. Praca i kroki nakładają się (wybieramy wyższy poziom ruchu, nie sumujemy ich).
Marsz/bieg lub trening mieszany zawarty w podanych krokach nie zwiększa drugi raz PAL.
Bez średniej kroków trening jest uwzględniany osobno w doborze PAL. Kalorii zegarka nie dodajemy
do szacowanego zapotrzebowania. Więcej pól nie gwarantuje większej dokładności.

Przed zapisem jest podgląd starego i nowego celu. Zapis obejmuje ustawienia, cele i nowy okres
obserwacji; nie nadpisuje profilu zmienionego podczas podglądu. Nieprawidłowe pola blokują wynik,
nie są zastępowane domyślnym wiekiem/wagą. Kalkulator automatyczny obsługuje dorosłych i ma własny
zakres 1500–15000 kcal (ograniczenie aplikacji, nie uniwersalne minimum żywieniowe).
Tryb indywidualny wyłącza automatyczne cele/korekty, zachowując dotychczasowe cele i pomiary.

Profil, tydzień i Efekty używają tego samego modelu. Dopasowanie na podstawie trendu wymaga
co najmniej 6 pomiarów rozłożonych na 21 dni oraz 21 kompletnych, zatwierdzonych dni jedzenia
(min. 70% okresu); po zmianie czekamy na nową fazę obserwacji. Korekty wymagają osobnej zgody.
Sen, energia i głód są kontekstem, nie arbitralnym dodatkiem do kalorii. Stare daty pomiarów
w formacie dd.MM.yyyy są rozpoznawane bez przepisywania zapisanej historii.

Bez zatwierdzenia nowych ustawień pozostaje dotychczasowy PAL i cel. Nowe ustawienia są
uwzględnione w lokalnych kopiach. Schemat Room i podpis aplikacji pozostają bez zmian.
Dodano EnergyCalculatorTest i EnergyProfileIntegrationTest, obejmujące m.in. nakładanie aktywności,
trzy cele, kopię ustawień, brak zapisu przy podglądzie, odrzucenie starego profilu i rollback błędu bazy.
Pełne testy Android/Robolectric i kompilację uruchamia Codemagic; sprawdzenie struktury nie jest kompilacją.


Istniejąca aplikacja Android: Kotlin, Compose, Room schema 8 i pakiet `com.example`.
Nowy ciemny motyw, zwarty bilans dnia, kafelki makro, szybka woda i kreatyna,
przewijana baza przepisów, kompaktowe karty posiłków i lista zakupów.
Pozostałe funkcje oraz siedem zakładek są zachowane. Ta zmiana nie przenosi
danych ani aplikacji do projektu webowego w AI Studio.

## Kolorowe nagłówki 3D — build75

Na Starcie dodano większe ikony 3D do bilansu dnia, makro, wody, kreatyny,
warzyw i owoców, zdrowia, sportu i efektów. Nagłówki kart rozwijanych mają
spójne ilustracje 44 dp z miękką kolorową poświatą. Bursztyn to bilans,
błękit to woda, turkus to aktywność, a fiolet to efekty i analiza. Te same
akcenty działają w ciemnym i jasnym motywie. Wykorzystano istniejące,
spójne grafiki pakowane w APK i pamięć podręczną małych bitmap, bez zapytań
sieciowych ani dodatkowych zapętlonych animacji. Ikony są dekoracyjne;
napisy pozostają opisem sekcji dla czytnika ekranu.

## Ikony i krótkie animacje — build75

- Mango 3D pozostaje znakiem aplikacji. Nawigacja i podstawowe działania mają
  proste ikony konturowe. Wybrana zakładka ma delikatne bursztynowe tło i stan
  zaznaczenia dostępny dla czytnika ekranu.
- Śniadanie: słońce i bursztyn; obiad: talerz i turkus; kolacja: księżyc i fiolet.
  Przy danych pokazujemy podpisane oznaczenia: pełne dane, do uzupełnienia
  i zegar przy dniu w toku.
- Po udanym zapisie Zjedzone pojawia się krótkie potwierdzenie. Karta zwija
  się do 180 ms; widok przechodzi do następnego posiłku. Zapis nie jest
  opóźniany przez animację i nie pokazujemy potwierdzenia dla błędu zapisu.
  Podwójne kliknięcie podczas zapisu jest blokowane dla tego posiłku.
- Karta wraca na miejsce po cofnięciu zjedzenia/usunięcia. Szybkie cofnięcie
  w czasie zwijania odwraca przejście zamiast duplikować kartę.
- Kalorie i makro w posiłku oraz sumie dnia płynnie zmieniają się do nowych
  wartości. Krótkie podświetlenie oznacza zmianę; nie startuje przy pierwszym
  wyświetleniu. Nieznane wartości nie są wyświetlane jako 0.
- Zmiana dnia: przesunięcie 18 dp przez 180 ms zgodne z kierunkiem daty.
  Menu Opcje: pojawienie przez 120 ms; obsługa przewijania, Back i dotyku poza
  menu. Wszystkie przyciski z poprzedniej wersji pozostają dostępne.
- Ograniczenie animacji w Profilu lub wyłączenie ich w Androidzie ustawia
  efekty na natychmiastowe przejścia; statusy, liczby i akcje pozostają.
  Zmianę systemowego ustawienia obserwujemy także przy otwartej aplikacji.

Dodano testy UI końcowych wartości, brakujących danych, powrotu posiłku,
menu i zaznaczonej nawigacji przy ograniczeniu ruchu. Kompilacja i Robolectric
wymagają uruchomienia w Codemagic przed publikacją APK.

## Wygodniejsze codzienne używanie — build75

- Start: przy pierścieniu wybierz „Wyjaśnij bilans”. W podsumowaniu tygodnia
  przycisk wyjaśnia zaznaczony dzień, a bez zaznaczenia ostatni dzień analizy.
  Pokazujemy cel jedzenia osobno od zapotrzebowania lub spalania Health Connect,
  odejmowanie, źródło, kompletność i status zatwierdzenia. Dzisiejszy wynik jest
  roboczy, a brak danych nie staje się zerem. Nie dodajemy drugi raz BMR/treningu.
- Dodaj produkt w posiłku: „Jak ostatnio” podaje ostatnią udanie zapisaną ilość
  i jednostkę. „Dodaj jak ostatnio” zapisuje tę ilość, „Popraw” wypełnia formularz
  do edycji. Dotychczasowe produkty bez ilości nie dostają zmyślonej gramatury.
  Ilości są zapamiętywane także po dodaniu skanerem oraz zamianie składnika;
  zapis sąsiaduje z dotychczasowymi preferencjami i jest objęty kopią danych.
- Przy dodawaniu, zamianie, skanerze i edycji gramatury składnika sprawdzamy
  m.in. 100 g masła orzechowego, duże ilości oleju oraz dziesięciokrotny wzrost
  względem poprzedniej ilości w tej samej jednostce. To kontrola pomyłek,
  nie zalecenie żywieniowe: można poprawić lub świadomie zachować ilość.
- Start i Profil pokazują zainstalowany build oraz „Co nowego?”. Po aktualizacji
  lista otwiera się raz, do zamknięcia; można ją później otworzyć ponownie.
- Karta posiłku jest zwarta: zdjęcie, aktualne kalorie/makro, składniki do
  rozwinięcia, „Zjedzone” i „Opcje”. Menu zawiera przepis/gotowanie, produkt,
  zjedzone gramy, wagę dania, kopiowanie, gotowanie na dni, porcje, zmianę,
  przenoszenie i usuwanie. Ukończone posiłki nadal trafiają do historii.

Dodano testy zapisu i odtworzenia ilości, starszych preferencji, obliczeń z
obu źródeł, pustych danych, kontroli jednostek, możliwości poprawy/zachowania
ilości i menu posiłku. Kompilacja, Robolectric i lint muszą przejść w Codemagic;
kontrole lokalne obejmują strukturę źródeł oraz integralność ZIP i konfiguracji.

## Poprawki zatwierdzania i Sport — build75

Zatwierdzenie dnia dotyczy jego posiłków. Odświeżenie całkowitego spalania,
zmiana aktualnego zapotrzebowania w profilu i wybór drugiej podstawy bilansu
nie cofają tego zatwierdzenia. Analiza używa aktualnych wartości wybranego
źródła; nie sumuje zapotrzebowania i spalania. Zmienione lub wcześniej
niesprawdzone dane Health Connect mają osobne ostrzeżenie. Przy chwilowym
braku spalania zatwierdzenie pozostaje widoczne, ale dzień nie wchodzi do
bilansu Health Connect do czasu otrzymania prawidłowej wartości. Edycja posiłków
lub cofnięcie zatwierdzenia nadal wymaga ponownego zatwierdzenia.

Sport: usunięto Plan i formularze planowania. Zostają Dziennik, Historia,
minutnik przerw i cel aktywności. Kalendarz oznacza wykonane serie i odczytane
sesje, a nie dawne plany. Starsze dane planów są zachowane w kopiach zapasowych.

## Efekty, analiza i przepisy z czatu AI — build75

Start → „Efekty i analiza” albo ikona wykresu w AI Dietetyku otwiera panel.

- Cele: redukcja, utrzymanie i budowanie masy. Wybór zmienia cel w Profilu,
  a korekty kalorii zatwierdza się oddzielnie.
- Wykres 14/30/90 dni: ostatni pomiar na datę, pomiary oraz 7-dniowa średnia
  dostępnych pomiarów (min. 2). Trend regresji wymaga min. 7 dni między pomiarami.
  Brakujące pomiary nie są zastępowane zerami. Dotknięcie pokazuje datę i wagę.
- Jakość: zakończone dni, zapisane dni, aktualne zatwierdzenia, kompletne dni,
  niepełne składniki i liczba pomiarów. Dzień bieżący jedzenia jest wyłączony.
  Zatwierdzenie z niepełnymi składnikami nadal działa w istniejącym panelu,
  ale nie służy do liczbowych korekt nowej analizy.
- Kalorie utrzymania: bieżący model Mifflin–St Jeor × aktywność, oddzielnie
  od celu jedzenia. Przy stabilnym trendzie i dobrych zapisach pokazujemy
  średnią spożycia jako punkt odniesienia, nie zmierzony metabolizm.
- Korekta +/-100 kcal jest opcjonalną, konserwatywną regułą aplikacji. Wymaga
  min. 6 pomiarów na przestrzeni 21 dni i 21 kompletnych, zatwierdzonych dni
  (min. 70% wybranego okresu), jednej fazy celu i upływu 21 dni od zmiany.
  Ograniczamy obniżenia przy niskim celu lub dużym głodzie/małej energii.
  Nie prognozujemy dokładnej utraty tłuszczu ani przyrostu mięśni.
- Zatwierdzenie ponownie sprawdza dane i dotychczasowy cel. Proteinę i tłuszcze
  zachowujemy, węglowodany dopasowujemy. Odrzucenie nie zmienia profilu.
- Raport tygodniowy: średnie jedzenia ostatnich i poprzednich 7 zakończonych
  dni z podaną liczbą zapisów, jakość i obserwacje. Wyniki ćwiczeń z dwóch dat,
  talia i samopoczucie są zestawiane bez przypisywania pewnej przyczyny.
- Samopoczucie: głód i energia 1–5, opcjonalny sen; można użyć ostatniego
  odczytu Health Connect. Własny przedział wagi służy ocenie utrzymania.
  Ustawienia, wpisy i daty korekt są objęte dotychczasową kopią danych.
- Obliczenia i raport lokalny nie potrzebują AI. „Wyjaśnij moje efekty” wysyła
  podsumowanie do istniejącego Gemini, respektuje tryb lokalny oraz klucz.
  To opcjonalna usługa zewnętrzna, której limity/rozliczenie zależą od konta.
  Wyjaśnienie jest opisem danych z chwili uruchomienia, nie analizą na żywo.
- Pod odpowiedzią AI zawierającą przepis wybierz „Dodaj przepis do jadłospisu”.
  Sprawdź/edytuj nazwę, składniki, ilości, kroki, porcje i szacunkowe makro.
  Wybierz konkretną datę, kategorię i porcje. Możesz też zapisać tylko do bazy.
  Przepis i wpis jadłospisu zapisują się w jednej transakcji; identyczny przepis
  z czatu można ponownie wykorzystać bez tworzenia drugiej kopii w bazie.
  Brakujących kalorii produktów nie uznajemy za pewne zero. Wartości AI są
  oznaczone jako niezweryfikowane i mogą wymagać uzupełnienia składników.

Testy obejmują trzy cele, jakość i braki, nieaktualne zatwierdzenia, trend,
kopię samopoczucia, odczyt przepisu Markdown i transakcyjny zapis do planu.
Pełna kompilacja i testy są wymagane w Codemagic; nie dołączono APK.

## Sport i animacje — build75

Na ekranie Start wybierz ikonę hantli przy dacie albo kartę „Sport i treningi”.
Kolejność i widoczność karty można zmienić w personalizacji Startu.

- Dziennik: wybierz datę, nazwę ćwiczenia, powtórzenia i ciężar, zapisz każdą
  serię osobno. 0 kg oznacza ćwiczenie z masą własnego ciała. Pokazujemy serie
  z poprzedniego dnia wykonania tego ćwiczenia i przycisk użycia poprzedniego
  wyniku. Serie zapisujemy tylko dla dzisiejszej lub wcześniejszej daty.
- Ekran Sport ma tylko zakładki Dziennik i Historia. Zakładka Plan została usunięta.
- Minutnik przerw: 60/90/120 s, pauza, wznowienie, reset i zachowanie terminu
  po wyjściu. Sygnał i drgnięcie działają przy otwartym ekranie Sport;
  nie dodano alarmu ani powiadomienia po całkowitym zamknięciu aplikacji.
- Historia: przyznaj odczyt treningów, dystansu i kalorii aktywnych, odśwież
  ostatnie 30 dni. Wszystkie źródła są opisane, także Zepp, jeśli przekazuje
  sesje do Health Connect. Nie odczytujemy bezpośrednio zegarka ani konta Zepp.
  Brak danych jest opisany jako brak danych, a nie zero. Nowy odczyt zastępuje
  historię bez duplikowania rekordów; błąd zachowuje poprzedni odczyt.
- Kalorie i dystans historii dotyczą źródła oraz czasu sesji. Mogą obejmować
  inną aktywność w tym przedziale. Nie dodajemy ich drugi raz do bilansu diety.
- Pierścień pokazuje unikalne dni aktywności w wybranym tygodniu i cel 1–7 dni.
  Liczymy wykonane serie i sesje Health Connect; plan i odpoczynek nie zwiększają
  wyniku. Kilka serii/treningów tego samego dnia daje jeden dzień aktywności.
- Dziennik, cel i ostatni odczyt są lokalne i objęte dotychczasową kopią
  danych oraz przywracaniem. Starsze kopie bez sportu nadal są obsługiwane.
- Zdjęcia pojawiają się łagodnie po załadowaniu, zapis ma krótkie ✓ i drgnięcie,
  pierścień płynnie zmienia wypełnienie, a dzień jest krótko podświetlany po
  zapisie treningu. Animacje są skończone i respektują ograniczenie ruchu.

Dodano testy poprzedniego wyniku, dat, liczenia aktywnych dni, kopii/odtworzenia,
walidacji, pauzy minutnika i widoczności jego przycisków. Pełna kompilacja,
Robolectric i lint wymagają wykonania w Codemagic przed udostępnieniem APK.

## Zdjęcia potraw — build75

- „Napraw i dobierz zdjęcia” obejmuje brakujące, uszkodzone oraz wcześniejsze
  automatyczne fotografie. Własne zdjęcia z telefonu pozostają chronione.
- Rozpoznajemy m.in. gofry, płatki, pulpety i gnocchi; typ dania ma pierwszeństwo
  przed samym składnikiem. Angielskie wyszukiwanie nie zmienia polskiej nazwy.
- Wyniki są sortowane według dopasowania dania i składników. Baza zdjęć nie
  gwarantuje identycznego przepisu: zdjęcia są poglądowe.
- Zdjęcie z wyników najpierw otwiera podgląd. „Zapisz zdjęcie” zatwierdza wybór
  dopiero po poprawnym pobraniu i sprawdzeniu pliku.
- Po nieudanym wyszukiwaniu/pobraniu z Pixabay próbujemy Wikimedia bez klucza,
  a dopiero później dotychczasowej ilustracji AI w dostępnym limicie.
- Fotografia ze strony przepisu jest sprawdzana i zapisywana lokalnie; nie
  polegamy wyłącznie na linku, który może później wygasnąć.
- Puste lub niedostępne zdjęcia mają czytelny komunikat, a na kartach przepisów
  opcję wyboru zdjęcia. Ręczna zmiana nie jest nadpisywana przez spóźniony zapis.
- Dodano testy dopasowania, rankingu wyników, naprawy oraz odczytu zdjęcia offline.

## Nowe funkcje i wygląd — build75

- Przytrzymaj uchwyt z kropkami na karcie niezjedzonego posiłku i przeciągnij
  na dzień widocznego tygodnia. Upuszczenie poza dniami anuluje akcję.
  „Cofnij” przywraca datę, ilości i składniki. Przycisk „Przenieś” nadal pozwala
  wybrać dowolną datę, także inny tydzień.
- W szczegółach przepisu lub instrukcji posiłku wybierz „Gotuj krok po kroku”.
  Duże kroki mają przyciski Wstecz/Następny/Gotowe. Minutnik 1–180 min działa
  na zapisanym terminie, ma pauzę/wznowienie/reset i przetrwa ponowne otwarcie.
  Sygnał jest dostępny w otwartym trybie gotowania; nie dodano powiadomienia
  alarmowego po całkowitym zamknięciu aplikacji. Ekran pozostaje włączony
  podczas gotowania i wraca do poprzedniego ustawienia po zamknięciu.
- Profil → Wygląd aplikacji: ciemny lub jasny kremowy motyw. Ustawienie jest
  lokalne, objęte kopią danych i odtwarzane po ponownym uruchomieniu.
- Śniadania: bursztyn, obiady: turkus, kolacje: fiolet. Logo mango pozostaje
  3D; przyciski edycji, wyszukiwania i nawigacji używają czytelnych ikon.
- Większe zdjęcia przepisów z widocznymi kaloriami i czasem, animacja wyników
  po filtrowaniu oraz krótkie ✓ po pomyślnym dodaniu posiłku.
- Obwódka w kalendarzu oznacza dziś, bursztynowa kropka plan, zielone ✓
  posiłki zjedzone. Oznaczenia nie opierają się wyłącznie na kolorze.
- Animacje są skończone i respektują ograniczenie ruchu w Profilu.

Nowe testy sprawdzają pauzę/zapis minutnika, wybór daty po przeciągnięciu,
cofnięcie przeniesienia wraz ze składnikami, kroki gotowania i wybór motywu.
Wymagane są pełne testy i lint w Codemagic przed udostępnieniem APK.

## Filtry przepisów — zachowane z build66

W wyborze przepisu z jadłospisu dodano filtry kategorii i czasu, wspólne z bazą
przepisów. Wyszukiwanie rozpoznaje polskie i angielskie nazwy kategorii,
liczbę pojedynczą/mnogą, wpisy bez polskich znaków oraz składniki.
Czas może pochodzić z ręcznego oznaczenia, opisu albo oznaczonego nagłówka
instrukcji. Przy imporcie stron zachowujemy podany totalTime lub komplet
prepTime + cookTime i źródłową kategorię. Nie zgadujemy czasu pojedynczego
kroku. Starsze przepisy bez czasu wymagają wpisania go w szczegółach przepisu
pod „Czas i sposób przygotowania”; są dostępne przez filtr „Brak czasu”.
Dodano testy reguł, metadanych importu i przycisków filtrów. Nie uruchomiono
ich lokalnie z powodu niedostępnego pobierania Gradle; wszystkie są wymagane
w Codemagic przed udostępnieniem APK.

## Budowanie bez limitu GitHub Actions

Wgraj dostarczony ZIP oraz oddzielny plik `codemagic.yaml` do głównego katalogu
repozytorium na gałęzi `mangofit-fix`. Codemagic wybiera dokładny ZIP po SHA256
i sprawdza `versionCode = 82`; inne archiwa są pomijane.
Nie rozpakowuj ZIP-u w repozytorium. `codemagic.yaml` leży obok ZIP-u, a nie
w `.github/workflows/` ani wewnątrz archiwum.

W Codemagic wybierz konto osobiste, repozytorium Dieta-1, gałąź mangofit-fix
i workflow FITMango build83. Używa maszyny `mac_mini_m2` i JDK 21.
W ustawieniach zmiennych utwórz grupę `fitmango_signing` i dodaj bezpieczną
zmienną `FITMANGO_KEYSTORE_BASE64` z tym samym kluczem co poprzednie APK.
Nie generuj nowego klucza. GitHub nie udostępnia odczytu zapisanych sekretów:
potrzebujesz własnej kopii klucza lub jego wcześniejszego zakodowanego tekstu.
Nie publikuj wartości zmiennej w kodzie ani na czacie.

Workflow uruchamia kompilację, wszystkie testy, lint, kontrolę DEX i podpisu,
sprawdza wersję skompilowanego APK i dopiero wtedy kopiuje je do artefaktów:
`FITMango-1.0.2-build83.apk`. Budowanie uruchamiasz ręcznie w przeglądarce.
Nie są skonfigurowane płatności, publikacja w Google Play ani powiadomienia.

## Weryfikacja

Sprawdzono strukturę źródeł i konfiguracji. Lokalna kompilacja była zablokowana
na pobieraniu Gradle (Network is unreachable). Dopiero wynik Codemagic
potwierdzi kompilację, testy oraz lint. Nie dołączono gotowego APK.
Sprawdź na telefonie aktualizację istniejącej aplikacji, makro po edycji,
import, skaner, sen i wagę, kopiowanie posiłków i ukrywanie zjedzonych.


## FITMango build92 — aktywność i regeneracja

- Start: zwarta karta kroków, snu i celu tygodniowego.
- Sport: dzienne odczyty Health Connect (do 15 dni przy odświeżaniu), historia sesji z 30 dni, cele w dniach/minutach/spacerach i porównanie tych samych zakończonych dni tygodnia.
- Ręcznie: spacer, bieganie, rower, pływanie, joga lub inna aktywność z datą, godziną i czasem. Wpisy lokalne pozostają po synchronizacji i można je usuwać. Bez szacowania spalonych kalorii.
- Samopoczucie: energia i zmęczenie 1–5 z datą; wskazówki z kilku dni, sen według daty pobudki.
- Raport AI: ostatnie 7 zakończonych dni, posiłki, cele, waga, sesje, dzienne kroki i sen oraz samopoczucie; wskazane źródła i braki odczytu. Korzysta z istniejącej konfiguracji AI w Profilu, bez automatycznego włączania chmury. Podsumowanie lokalne działa bez AI.
- Ochrona liczenia: konserwatywne wykrywanie kopii sesji (rodzaj, czas i min. 80% nakładania); oryginały pozostają do podglądu. Łączny czas sesji jest sumą rozłącznych przedziałów. Kalorie treningów nie są dodawane do celu jedzenia lub spalania ogółem.
- Dane sportowe i samopoczucie są zawarte w dotychczasowej lokalnej kopii preferencji. Stare kopie mają zgodne wartości domyślne, bez zmiany schematu Room.
- Testy regresji: ActivityRecoveryTest oraz rozszerzony ProgressAiContextTest. Pełna kompilacja, testy i lint są bramkami Codemagic; lokalny start Gradle był niemożliwy z powodu braku połączenia sieciowego.


## FITMango build93 — graficzne szczegóły aktywności i raport

Nowe widoki Aktywność / Raport tygodniowy realizują zaakceptowaną wizualizację, zachowując dotychczasową nawigację i ciemny/jasny motyw. Start ma trzy czytelne kafelki i przycisk na całą szerokość. Aktywność pokazuje siedem słupków minut z zapisanych sesji; dotknięcie dnia odsłania wpisy i źródła. Sen ma osobny wykres z przerwami tam, gdzie brakuje odczytów, oraz oceny energii i zmęczenia. Ustawienia celu i szczegóły synchronizacji są rozwijane.

Raport obejmuje ostatnie siedem zakończonych dni, oddzielnie od bieżącego tygodnia poniedziałek–niedziela. Średnie jedzenia liczą wyłącznie zatwierdzone zapisy (również zaakceptowane dane szacunkowe); brak dni nigdy nie jest zerem. Cztery karty to Posiłki i białko / Aktywność / Regeneracja / Na kolejny tydzień. AI jest przypisane do zakresu i czasu generowania, z pełnym oryginałem dostępnym po rozwinięciu. Nie wyciągamy automatycznego werdyktu z samych kolorów lub nazw potraw.

Wykresy i paski wykorzystują lokalne liczby, a nie obrazy z przykładowymi wartościami. Większa czcionka układa kafelki i raport pionowo. Animacje respektują ograniczenie ruchu. Brak zmian schematu bazy, podpisywania APK lub dotychczasowego przeliczania kalorii.


## FITMango build94

Poprawiono błędny import graphicsLayer w SportsGraphicCards.kt: funkcja pochodzi z androidx.compose.ui.graphics. Zachowano pełny układ graficzny build93. Trzy komunikaty kompilatora w przesłanym logu mają tę samą przyczynę. Numer źródeł i kontrola APK w Codemagic: 94. Pełna kompilacja, testy i lint wymagają wykonania w Codemagic.


## FITMango build95 — postępy, przypomnienia i animacje

- Postępy w sekcji Sport: najdłuższy zapisany spacer, najdłuższa trasa rowerowa z dostępnym dystansem i tydzień z największą liczbą aktywnych dni. Rekordy dotyczą dostępnej historii, nie nieodczytanych danych. Duplikaty sesji są pomijane.
- Dwa ostatnie zakończone tygodnie pn.–nd.: zapisany ruch, średnie jedzenie z zatwierdzonych dni, białko, sen i waga; przy każdej średniej liczba dni. Braki nie są zerami.
- Start: energia, zmęczenie i głód 1–5, wspólnie zapisane w jednym zatwierdzeniu preferencji. Poprzedni sen i inne dni pozostają zachowane. Karta podlega personalizacji Startu.
- Ustawienia przypomnień: opt-in ważenie, aktywność i raport; własne godziny i dzień tygodnia (ważenie/aktywność także codziennie), dotychczasowe godziny ciszy i zgoda telefonu. WorkManager może opóźnić lub pominąć powiadomienie przy oszczędzaniu energii; po długiej przerwie nie odtwarzamy kolejki spóźnionych alertów. Raport AI uruchamia wyłącznie użytkownik.
- Dlaczego widzę taką ocenę: czytelny zapis dat, posiłków, zatwierdzeń, treningów i źródeł, snu, pomiarów i zasad z dokładnego kontekstu przekazanego do AI podczas generowania. Zrzut pozostaje powiązany z czasem raportu, niezależnie od późniejszych zmian wpisów.
- Krótki ruch mango na powitanie, po zapisie, przy gotowym raporcie i po zakończeniu gotowania; możliwość wyłączenia przewodnika w Wyglądzie. Płynne podświetlenie nawigacji, wzrost słupków, zdjęcia z zaokrągleniem i istniejącym pojawianiem po wczytaniu. Animacje respektują ograniczenie ruchu i nie mają stałych pętli. Zachowano przejścia dni i proste ikony funkcji.
- Kopie zachowują nowe ustawienia przypomnień przez dotychczasowy eksport i zapis samopoczucia przez kopię preferencji. Bez zmiany schematu Room lub podpisu aplikacji.
- Testy: PersonalProgressTest i PersonalProgressUiTest oraz dotychczasowy komplet. Lokalna próba Gradle była zablokowana pobraniem dystrybucji (Network is unreachable); pełne kompilacja, testy i lint pozostają wymagane w Codemagic.

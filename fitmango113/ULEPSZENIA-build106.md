# FITMango 1.0.2 build106 — zdjęcia sylwetki

Naprawiona karta: Profil → Zdjęcia sylwetki.
Dotychczasowa karta pokazywała jedynie daty trzech ostatnich zestawów;
nie miała podglądu zapisanych zdjęć ani możliwości ich otwarcia.

Teraz zapisane zestawy mają miniatury przodu, boku i tyłu oraz pełny podgląd
bez przycinania. Starsze zestawy są dostępne przyciskiem „Pokaż starsze”,
bez dotychczasowego ograniczenia historii do trzech dat.
Można wybrać dwa zestawy i porównać ten sam widok (przód/bok/tył).
Kolejność porównania odpowiada kolejności zapisu zestawów.
Można uzupełnić lub ponownie wskazać pojedyncze zdjęcie w istniejącym zestawie;
data, identyfikator i pozostałe ujęcia zostają zachowane.
Usunięcie zestawu wymaga potwierdzenia; nie usuwa oryginałów w galerii telefonu.

Nowe zdjęcia są kopiowane do prywatnego folderu files/body-photos w aplikacji,
po sprawdzeniu formatu obrazu i limitu 64 MB na zdjęcie, bez kompresji i zmiany
oryginałów. Formularz pozostaje wypełniony przy błędzie; czyści się po udanym
zapisie. Niepełne zestawy (1–2 ujęcia) są obsługiwane. Nieudany zapis nie tworzy
pustego zestawu ani nie zostawia części nowych kopii. Kopie są uwzględniane
w istniejącym eksporcie i przywracaniu kopii zapasowej.

Po otwarciu karty aplikacja próbuje zabezpieczyć czytelne stare odnośniki
content:// przez skopiowanie zdjęć do własnej pamięci. Nie usuwa ani nie zeruje
odnośników niedostępnych; zachowuje historię i udostępnia ponowny wybór.
Jeżeli Android utracił dostęp do wcześniejszego pliku albo plik został usunięty,
aktualizacja nie odtworzy samych zdjęć z zapisanej daty. Należy wskazać plik
z galerii ponownie lub przywrócić kopię zapasową zawierającą zdjęcia.
Nie zmieniono schematu Room ani identyfikatorów zapisanych danych.
Zachowano wykres etapów redukcji z build105 i wcześniejsze poprawki kalorii.

Dodano BodyPhotoStoreTest (6 scenariuszy: trwałe kopie, wycofanie błędnego zapisu,
naprawa pojedynczego ujęcia, migracja czytelnych i zachowanie niedostępnych
odnośników, usuwanie z zachowaniem wspólnych odwołań i oryginałów, kopia zapasowa)
oraz BodyPhotosUiTest (3 scenariusze: starsze zestawy i akcje naprawy,
porównanie dwóch zestawów, potwierdzenie/anulowanie usuwania).

Próba uruchomienia testów lokalnie zakończyła się przed kompilacją: brak Gradle
9.3.1 i dostępu do services.gradle.org. Nie uruchomiono testów Kotlin/Robolectric,
lint, budowy APK ani podglądu UI. Lokalnie sprawdzono integralność ZIP, wersję106,
zgodność SHA-256 oraz składnię YAML, Bash i osadzonego Pythona.
Testy Android i sprawdzenie zdjęć na urządzeniu pozostają do wykonania w CI.

Wgraj ZIP build106 i codemagic.yaml razem na gałąź mangofit-fix.
YAML umieść w katalogu głównym jako codemagic.yaml.
Workflow zachowuje identyfikator fitmango-build100 i dotychczasowy podpis.
Etap fail-fast uruchamia nowe testy zdjęć, kopii zapasowej i poprzedniego planu;
następnie pozostają pełne testy, lint, budowa APK i kontrola podpisu.
Nie uruchomiono Codemagic podczas przygotowania tej poprawki.

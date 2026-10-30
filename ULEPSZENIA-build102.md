# FITMango 1.0.2 build102 — automatyczne dane po imporcie

## Co działa automatycznie

Nowe i stare przepisy korzystają z lokalnego rozpoznawania nazw bez ponownego
importu i bez zapisywania zmian do bazy. Dodano jawne aliasy świeżego czosnku,
surowej szalotki i oliwy, w tym nazwy ze zdjęcia: „Czosnku”, „Cebuli szalotki”,
„Oliwy z oliwek”. Rozpoznawane są także niektóre zwykłe określenia przygotowania,
np. „posiekanego” i „świeżego”. Nie stosujemy zgadywania dowolnych nazw.

Priorytet: wartości konkretnego składnika → dane zapisane przez użytkownika
(również pod rozpoznaną odmianą nazwy) → tabela referencyjna → dotychczasowa baza.
Znane wartości, także prawidłowe zera, pozostają bez zmian. W tabeli uzupełniamy
jedynie brakujące pola kalorii i makro rozpoznanego produktu. Przy składnikach
widać informację „szacunek z tabeli”. Dane etykietowe pozostają dokładniejszym
źródłem dla konkretnego produktu.

Dla czosnku w ząbkach, bez zapisanej gramatury, przyjmujemy orientacyjnie
1 ząbek ≈ 3 g. Założenie jest wyświetlane obok kalorii. Własna gramatura ma
pierwszeństwo, nawet gdy wartości odżywcze w rekordzie wymagają uzupełnienia.
Nie zgadujemy masy główki czosnku, sztuki szalotki ani niepodanej ilości.
Sosy, produkty marynowane, przetworzone i nieznane nazwy nie dostają tych profili.

Nie usuwamy ostrzeżenia dla prawdziwych braków. Lista uwagi zmniejsza się tylko
po rozpoznaniu danych, a pozostałe składniki nadal wymagają etykiety lub wpisu.
Automatyczne rozpoznanie nie przepisuje oryginalnych sum kalorii importowanej
porcji na sumę ilości, które mogły dotyczyć całego przepisu. Zachowano dotychczasową
logikę świadomych edycji składników i skalowania porcji.

Zachowano wszystkie zmiany analizy tygodnia z build101. Zatwierdzony dzień
pozostaje pełnym zapisem; precyzja składników jest osobną informacją.
Nie uruchomiono Gemini, wyszukiwania produktów online ani Codemagic.

## Źródła wartości orientacyjnych

USDA SR28: garlic raw NDB 11215 i shallots raw NDB 11677, na 100 g części jadalnej:
https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg11.pdf
Węglowodany są wartościami tabelarycznymi USDA, a nie odczytem z etykiety produktu.
Masa ząbka (orientacyjna miara kuchenna, rzeczywiste ząbki różnią się masą):
https://www.ars.usda.gov/arsuserfiles/80400525/data/hg72/hg72_2002.pdf
Oliwa: ponownie wykorzystano dotychczasowy profil lokalny aplikacji.

## Instalacja i limit CI

Wgraj ZIP build102 oraz codemagic.yaml do tego samego commita na mangofit-fix.
YAML ma być w katalogu głównym repozytorium pod nazwą codemagic.yaml.
Używaj tej pary plików: konfiguracja sprawdza SHA-256 i versionCode=102.
Nazwa wyświetlana workflow zawiera build102; identyfikator pozostaje fitmango-build100.
Zachowano dotychczasowy klucz podpisu, grupę fitmango_signing i pakiet com.example.
CI: testy powiązane → pełne testy → lint → APK → sprawdzenie podpisu.
Bez clean i bez nowych automatycznych wyzwalaczy; zachowano cache Gradle.

## Weryfikacja

Dodano sześć testów ImportedIngredientNutritionTest: przykłady ze zdjęcia,
pierwszeństwo własnych wartości i prawidłowych zer, zapis i odczyt profilu pod
odmienioną nazwą, pierwszeństwo zmierzonej masy, odrzucanie nieznanych produktów
i jednostek, obsługa starych importów bez przepisywania kalorii porcji.
Pełnych testów Kotlin/Robolectric, lint i budowy APK nie wykonano lokalnie:
brakuje Android SDK i kompilatora Kotlin. Nie zweryfikowano ekranów na urządzeniu.
Sprawdzenia lokalne obejmują integralność paczki, składnię YAML/Python/Bash,
poprawność ekstrakcji źródeł w CI i zgodność SHA-256 oraz wersji.

Dodatkowo zweryfikowano dane tabeli na JVM: 24 aliasy, 13 przypadków nazw,
wyliczenia dla ilości ze zdjęcia i skalowanie. To test danych oraz reguł dopasowania,
a nie uruchomienie kodu Kotlin ani pełnych testów Androida.

## Poprawka po logu 341 testów

Dostarczony log CI: 340/341 testów zakończyło się powodzeniem. Jedyny błąd:
PersonalProgressTest.aiEvidenceContainsActualPromptDatesSourcesAndApprovedEstimates,
linia 93. Przyczyna: zmiana etykiety „zatwierdzony” na „pełny i potwierdzony”.
Etykieta brzmi teraz „zatwierdzony • pełny i potwierdzony”. Zachowano dotychczasową
asercję i znaczenie kompletnego, zatwierdzonego dnia. Nie zmieniono obliczeń.
Nie uruchomiono ponownie pełnych testów Androida lokalnie ani Codemagic.
Konfiguracja najpierw wykona klasę PersonalProgressTest, a potem resztę testów.
Nowy ZIP ma nową sumę SHA-256 — używaj wyłącznie dołączonej konfiguracji.

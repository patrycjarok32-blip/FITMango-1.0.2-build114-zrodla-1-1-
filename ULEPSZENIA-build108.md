# FITMango 1.0.2 build108 — zgodność informacji o nowościach

Przesłany log build107: 378 zakończonych testów, 377 poprawnych i 1 błąd.
DailyConvenienceTest.releaseNotesBelongToInstalledBuildAndAreNotShownAgainAfterAcknowledgment:
expected 107, actual 104. Informacje o nowościach miały pozostawiony numer 104.
Przeszły testy nowej nawigacji Profilu, trwałego zapisu/migracji/porównywania
zdjęć sylwetki i etapów redukcji. Pełny etap trwał 7 min 15 s.
Log nie potwierdza wykonania późniejszych etapów lint ani budowy APK.

ReleaseNotesSupport.BUILD pobiera teraz numer z BuildConfig.VERSION_CODE.
Usunięto drugą, ręcznie wpisywaną liczbę. Uzupełniono nowości o nowy Profil,
zdjęcia sylwetki i etapy redukcji. Dotychczasowy test pozostaje bez zmian,
łącznie z kontrolą jednokrotnego pokazywania i niezgodnej wersji.

Nie zmieniono działania Profilu, zdjęć, redukcji, importu ani bazy danych.
Pierwszy filtr testów CI obejmuje obecnie wykryty błąd, aby zatrzymywać
podobne niezgodności przed pełnym zestawem. Pozostałe testy i wymagane
etapy CI zachowano, bez nowych automatycznych uruchomień.

Lokalnie sprawdzono powiązanie wersji z BuildConfig, niezmieniony test,
zakres różnic wobec build107, integralność ZIP, SHA-256 i wersję108,
składnię YAML/Bash/osadzonego Pythona oraz wykonanie ekstrakcji w CI.
Nie uruchamiano ponownie Android/JVM testów ani APK lokalnie: nadal brak
Gradle 9.3.1 i możliwości pobrania. Wynik 377/378 dotyczy przesłanego logu
build107, a testy build108 wymagają uruchomienia w Codemagic.
Nie uruchomiono Codemagic podczas przygotowania poprawki.

Wgraj ZIP build108 (bez rozpakowania) i codemagic.yaml razem w jednym
nowym commicie na mangofit-fix. YAML umieść w katalogu głównym jako
codemagic.yaml. Uruchom nowy build dla tego commita.
Identyfikator workflow i dotychczasowy podpis są zachowane.

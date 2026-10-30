# FITMango build114 — osobne zakładki i integracja aktywności

- Dodano dedykowane zakładki dolnej nawigacji „Postępy” i „Aktywność”, wykorzystujące istniejące ekrany `EffectsScreen` i `SportsScreen`; nie utworzono drugiego planera treningowego.
- Zakładka startowa otrzymała etykietę „Dieta”. Jadłospis, przepisy, zakupy i profil pozostają dostępne bezpośrednio z nawigacji.
- Dokumenty i AI Coach pozostają dostępne przez dotychczasowe punkty wejścia w ekranach głównych/profilu, zamiast zajmować osobne pozycje głównej nawigacji.
- Zachowano istniejące komponenty Health Connect, synchronizację i tygodniowy raport AI. Nie dodano równoległego systemu importu ani drugiego mechanizmu raportowania.
- Zaktualizowano test weryfikujący pozycje nawigacji, aby oczekiwał nowych zakładek „Postępy” i „Aktywność”.
- Numer wersji: `versionCode = 114`; `versionName = 1.0.2`.

## Zakres weryfikacji

Zmiany źródłowe i konfigurację CI należy sprawdzić pełnym zestawem testów jednostkowych/Robolectric, Android Lint, kompilacją APK oraz kontrolą podpisu i numeru wersji w Codemagic. Sama obecność kodu Health Connect nie gwarantuje dostępności każdego rodzaju pomiaru na każdym urządzeniu; wymaga to sprawdzenia uprawnień i dostępności danych.

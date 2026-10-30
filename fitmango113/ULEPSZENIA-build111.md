# FITMango build111 — jadłospis bliżej celu kalorycznego

- Funkcja „Generuj jadłospis” pobiera dzienny cel kalorii z profilu użytkownika.
- AI ma przygotować pięć alternatywnych zestawów śniadanie/obiad/kolacja, z kalorycznością ukierunkowaną na dzienny cel.
- Aplikacja lokalnie oblicza sumę kalorii każdego poprawnego zestawu i zapisuje ten, którego suma jest najbliższa celowi; przy identycznej różnicy preferuje zestaw nieprzekraczający celu.
- Zachowana jest obsługa starszej odpowiedzi AI w postaci bezpośredniej tablicy trzech posiłków.
- Wersja Android: versionCode 111, versionName 1.0.2.

Wymagana weryfikacja w Codemagic: testy, lint i kompilacja APK.

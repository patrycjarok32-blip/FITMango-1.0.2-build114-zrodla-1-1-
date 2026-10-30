# FITMango build112 — precyzja wagi Renpho

- Zwiększono dokładność odczytu wagi z Health Connect do dwóch miejsc po przecinku.
- Waga jest obcinana do dwóch miejsc po przecinku bez zaokrąglania (np. 72,389 kg → 72,38 kg).
- Historia pomiarów oraz najnowsza waga korzystają z tej samej reguły.
- Wyświetlanie wagi pokazuje dwie cyfry po przecinku.
- Dodano test regresyjny sprawdzający obcinanie, a nie zaokrąglanie wartości.
- Wersja Android: versionCode 112, versionName 1.0.2.

# FITMango build110 — poprawka liczby porcji przy imporcie AI

- Dodano odczyt jawnie podanej liczby porcji z tekstu źródłowego, m.in. formatu „Składniki na 3 porcje”.
- Jeśli AI zwróci domyślnie `servings = 1`, ale tekst źródłowy wyraźnie podaje inną liczbę porcji, podgląd importu zachowuje liczbę ze źródła.
- Rozszerzono instrukcję dla AI, aby nie ignorowała liczby porcji podanej w źródle.
- Dodano test regresyjny dla popularnych formatów zapisu liczby porcji.

Testy Gradle nie mogły zostać uruchomione w tym środowisku, ponieważ wrapper wymaga pobrania Gradle z internetu, a połączenie z `services.gradle.org` jest niedostępne.

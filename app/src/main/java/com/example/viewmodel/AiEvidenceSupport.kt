package com.example.viewmodel

import org.json.JSONObject

/** Readable evidence frozen from the actual prompt, never reconstructed from later edits. */
object AiEvidenceSupport {
    fun describe(prompt: String): String {
        val root = JSONObject(prompt.substringAfter("\n"))
        val facts = root.getJSONObject("kontrola_danych_obliczenia_lokalne")
        fun number(key: String, suffix: String) = if (facts.isNull(key)) "brak danych" else String.format(java.util.Locale.getDefault(), "%.1f %s", facts.getDouble(key), suffix)
        val food = root.getJSONArray("dni_jedzenia")
        val sessions = root.getJSONArray("treningi_health_connect")
        val daily = root.optJSONArray("kroki_i_sen_dziennie") ?: org.json.JSONArray()
        val profile = root.optJSONObject("profil")
        return buildString {
            append("Zakres: ${root.getString("od")} – ${root.getString("do")}\n")
            append("Cel podczas oceny: ${profile?.optString("cel") ?: "brak profilu"}.\n")
            append("Zatwierdzone dni: ${facts.getInt("zatwierdzone_dni")}/${facts.getInt("dni_okresu")}. ")
            append("Średnia jedzenia: ${number("srednie_kcal", "kcal")}; białko: ${number("srednie_bialko_g", "g")}.\n\n")
            append("POSIŁKI I DATY\n")
            if (food.length() == 0) append("Brak zapisanych dni w zakresie.\n")
            for (i in 0 until food.length()) {
                val d = food.getJSONObject(i)
                append("${d.getString("data")}: ${d.getInt("kcal_zapisane")} kcal • ${if (d.getBoolean("zatwierdzony")) "zatwierdzony • pełny i potwierdzony" else "bez aktualnego zatwierdzenia"}\n")
                val meals = d.getJSONArray("posilki")
                for (m in 0 until meals.length()) append("• ${meals.getString(m)}\n")
            }
            val diet = facts.optJSONObject("ocena_jadlospisu")
            if (diet != null) {
                append("\nOCENA JADŁOSPISU\n")
                append("Dni z celem białka: ${if (diet.isNull("dni_z_osiagnietym_aktualnym_celem_bialka")) "brak celu" else diet.getInt("dni_z_osiagnietym_aktualnym_celem_bialka")} z ${facts.getInt("zatwierdzone_dni")}. Cel z aktualnego profilu.\n")
                for (key in listOf("mocne_strony", "obserwacje", "propozycje_na_tydzien")) {
                    val entries = diet.getJSONArray(key)
                    for (i in 0 until entries.length()) append("• ${entries.getString(i)}\n")
                }
                append("Bez pomiarów nie oceniamy błonnika, soli, cukrów dodanych, tłuszczów nasyconych, witamin ani pór posiłków. Pełny zapis dnia pozostaje potwierdzony.\n")
            }
            append("\nAKTYWNOŚĆ\n")
            if (sessions.length() == 0) append("Brak odczytanych sesji w zakresie; nie oznacza to braku ruchu.\n")
            for (i in 0 until sessions.length()) { val s = sessions.getJSONObject(i)
                append("${s.getString("od")}: ${s.getString("nazwa")} • ${s.getLong("minuty")} min • ${s.getString("zrodlo")}\n")
            }
            val omitted = root.optInt("pominiete_szczegoly_treningow", 0)
            if (omitted > 0) append("Pozostałe sesje bez szczegółów w odpowiedzi: $omitted.\n")
            append("\nSEN I KROKI\n")
            if (daily.length() == 0) append("Brak dziennych odczytów.\n")
            for (i in 0 until daily.length()) { val d = daily.getJSONObject(i)
                append("${d.getString("data")}: sen ${if (d.isNull("sen_minuty")) "brak odczytu" else "${d.getLong("sen_minuty")} min"}, kroki ${if (d.isNull("kroki")) "brak odczytu" else d.getLong("kroki").toString()}\n")
                append("Źródła: ${d.optString("zrodla", "brak odczytu")}\n")
            }
            append("\nSAMOPOCZUCIE\n")
            val wellbeing = root.optJSONArray("samopoczucie_dziennie") ?: org.json.JSONArray()
            for (i in 0 until wellbeing.length()) { val d = wellbeing.getJSONObject(i)
                append("${d.getString("data")}: energia ${d.getInt("energia_1_do_5")}/5 • zmęczenie ${d.getInt("zmeczenie_1_do_5")}/5\n")
            }
            append(root.getJSONObject("lokalna_analiza").optString("samopoczucie", "Brak zapisów samopoczucia.")); append("\n")
            append("\nPOMIARY I BILANS\n")
            append("Średnia masy na początku: ${number("srednia_wagi_pierwsze_3_dni", "kg")}; na końcu: ${number("srednia_wagi_ostatnie_3_dni", "kg")}.\n")
            val weights = root.getJSONObject("lokalna_analiza").getJSONArray("pomiary_wagi")
            for (i in 0 until weights.length()) { val p = weights.getJSONObject(i); append("${p.getString("data")}: ${p.get("kg")} kg\n") }
            append("Zjedzone minus odczytane spalanie, tylko wspólne dni: ${number("zjedzone_minus_spalone_odczytane_kcal_lacznie", "kcal")}.\n")
            val scale = root.optJSONArray("sklad_ciala_z_wagi") ?: org.json.JSONArray()
            for (i in 0 until minOf(40, scale.length())) { val row = scale.getJSONObject(i)
                append("${row.getString("data")} • ${row.getString("zrodlo")}: ")
                val metrics = row.getJSONArray("pomiary")
                for (m in 0 until metrics.length()) { val v = metrics.getJSONObject(m); append("${v.getString("nazwa")}: ${v.get("wartosc")} ${v.getString("jednostka")}; ") }
                append("\n")
            }
            if (scale.length() > 40) append("Pozostałe wpisy składu ciała przekazane do analizy: ${scale.length() - 40}.\n")
            append("Obserwacja lokalna: ${facts.optString("obserwacja")}\n")
            append("\nZASADY OCENY\nZatwierdzone dni są uwzględniane także przy szacunkowych wartościach składników. Kalorie treningów nie są dodawane drugi raz. Brak odczytu pozostaje brakiem danych. Krótki trend wagi nie jest pomiarem utraty tłuszczu.\n")
            val reasons = facts.getJSONArray("ograniczenia")
            for (i in 0 until reasons.length()) append("• ${reasons.getString(i)}\n")
        }
    }
}

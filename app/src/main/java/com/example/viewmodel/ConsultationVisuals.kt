package com.example.viewmodel
import org.json.JSONObject
import org.json.JSONArray

object ConsultationVisuals {
    const val MARKER = "FITMANGO_VISUAL_V1:"
    fun snapshot(raw: String): JSONObject = runCatching {
        val root = JSONObject(raw).getJSONObject("raport_lokalny")
        val facts = root.getJSONObject("kontrola_danych_obliczenia_lokalne")
        val profile = root.optJSONObject("profil")
        val out = JSONObject()
        listOf("srednie_kcal", "srednie_bialko_g", "srednie_weglowodany_g", "srednie_tluszcze_g", "zatwierdzone_dni", "dni_okresu", "zatwierdzone_dni_z_wartosciami_szacunkowymi").forEach { k -> out.put(k, facts.opt(k) ?: JSONObject.NULL) }
        listOf("cel_jedzenia_kcal","cel_bialka_g","cel_weglowodanow_g","cel_tluszczu_g").forEach { k -> out.put(k, profile?.opt(k) ?: JSONObject.NULL) }
        val points = root.optJSONObject("lokalna_analiza")?.optJSONArray("pomiary_wagi") ?: JSONArray()
        val filtered = JSONArray()
        for (i in 0 until points.length()) {
            val row = points.optJSONObject(i) ?: continue
            val date = row.optString("data")
            val kg = row.optDouble("kg")
            if (date >= root.optString("od") && date <= root.optString("do") && kg.isFinite() && kg > 0) filtered.put(row)
        }
        out.put("weights", filtered)
        out.put("activity", root.optJSONObject("aktywnosc_okresu") ?: JSONObject.NULL)
        out
    }.getOrElse { JSONObject() }
    fun number(json: JSONObject, key: String): Double? = json.optDouble(key).takeIf { it.isFinite() && it >= 0 }
}

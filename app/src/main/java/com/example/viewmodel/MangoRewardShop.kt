package com.example.viewmodel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Cosmetic rewards only. Stored in FITMangoUsability, which is included in existing backups. */
data class MangoReward(val id: String, val title: String, val icon: String, val price: Int)
data class MangoRewardState(val purchased: Set<String> = emptySet(), val equipped: String = "classic")

object MangoRewardShop {
    const val KEY = "mango_rewards_v2"
    val catalog = listOf(
        MangoReward("classic", "Klasyczne Mango", "🥭", 0),
        MangoReward("cap", "Czapka Mango", "🧢🥭", 100),
        MangoReward("sport", "Sportowe Mango", "🎽🥭", 200),
        MangoReward("gold", "Złote Mango", "🥇🥭", 500),
        MangoReward("royal", "Królewskie Mango", "👑🥭", 1000)
    )
    fun decode(raw: String): MangoRewardState {
        if (raw.isBlank()) return MangoRewardState()
        val json = JSONObject(raw)
        val owned = json.optJSONArray("purchased") ?: JSONArray()
        require(owned.length() <= catalog.size) { "Nieprawidłowe nagrody" }
        val ids = (0 until owned.length()).map { owned.getString(it) }
        require(ids.size == ids.distinct().size && ids.all { id -> catalog.any { it.id == id } })
        val equipped = json.optString("equipped", "classic")
        require(equipped == "classic" || equipped in ids) { "Nieprawidłowy strój" }
        return MangoRewardState(ids.toSet(), equipped)
    }
    fun encode(state: MangoRewardState): String = JSONObject()
        .put("purchased", JSONArray(state.purchased.sorted()))
        .put("equipped", state.equipped).toString()
    fun spent(state: MangoRewardState): Int =
        catalog.filter { it.id in state.purchased }.sumOf { it.price }
    fun balance(earned: Int, state: MangoRewardState): Int =
        (earned - spent(state)).coerceAtLeast(0)
    fun purchase(state: MangoRewardState, id: String, earned: Int): MangoRewardState? {
        val item = catalog.firstOrNull { it.id == id } ?: return null
        if (id == "classic") return state.copy(equipped = id)
        if (id in state.purchased) return state.copy(equipped = id)
        if (balance(earned, state) < item.price) return null
        return state.copy(purchased = state.purchased + id, equipped = id)
    }
    fun load(context: Context): MangoRewardState = runCatching {
        decode(context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty())
    }.getOrDefault(MangoRewardState())
    fun save(context: Context, state: MangoRewardState) {
        decode(encode(state))
        context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE)
            .edit().putString(KEY, encode(state)).apply()
    }
}

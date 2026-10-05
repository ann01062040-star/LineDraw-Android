package com.linedraw.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Stored in the existing metadata table, atomically with queue changes. */
data class Continuation(
    val enabled: Boolean = false,
    val round: Int = 0,
    val cities: Set<String> = emptySet(),
    val query: String = "",
    val seen: Set<String> = emptySet(),
    val unresolvedAtStart: Set<String> = emptySet(),
    val statuses: Set<DrawStatus> = emptySet(),
    val products: Set<String> = emptySet(),
    val catalog: DrawCatalog = DrawCatalog.FUNBOX
) {
    fun matches(draw: Draw) = catalog.owns(draw) && CatalogFilter(cities,statuses,query,products).matches(draw)
    fun encode(): String = JSONObject().put("enabled",enabled).put("round",round).put("cities",JSONArray(cities.toList()))
        .put("query",query).put("seen",JSONArray(seen.toList()))
        .put("unresolvedAtStart",JSONArray(unresolvedAtStart.toList()))
        .put("statuses",JSONArray(statuses.map { it.name }))
        .put("products",JSONArray(products.toList())).put("catalog",catalog.id).toString()
    companion object {
        const val MAX_ROUNDS = 3
        fun decode(value: String?): Continuation {
            if (value == null) return Continuation()
            val json = JSONObject(value)
            fun strings(key: String): Set<String> = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            // Existing batches contain a single city. Preserve that scope when upgrading.
            val cities = if (json.has("cities")) json.getJSONArray("cities").let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } else
                json.optString("city","所有地區").takeUnless { it == "所有地區" }?.let(::setOf) ?: emptySet()
            val statuses = if (json.has("statuses")) json.getJSONArray("statuses").let { a ->
                (0 until a.length()).map { DrawStatus.valueOf(a.getString(it)) }.toSet()
            } else emptySet()
            val products = if (json.has("products")) json.getJSONArray("products").let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } else emptySet()
            return Continuation(json.optBoolean("enabled"),json.optInt("round"),cities,
                json.optString("query"),strings("seen"),strings("unresolvedAtStart"),statuses,products,
                if (json.has("catalog")) DrawCatalog.fromId(json.getString("catalog")) else DrawCatalog.FUNBOX)
        }
    }
}

package com.linedraw.app.data

import java.text.Normalizer
import java.util.Locale

data class ProductOption(val key: String, val label: String, val names: List<String>, val count: Int)

/** Funbox groups products by model, including its BGX -> BXG spelling alias. */
object ProductCatalog {
    private val model = Regex("(?<![A-Z0-9_])(BXG|BGX|BX|UX|CX)\\s*-\\s*([0-9]{2})(?![A-Z0-9_])")
    private val dashes = Regex("[‐‑‒–—−]")
    private val whitespace = Regex("\\s+")
    private val families = listOf("BX", "UX", "CX", "BXG")
    private const val NAME = "name:"

    fun key(product: String): String {
        val text = Normalizer.normalize(product, Normalizer.Form.NFKC)
            .replace(dashes, "-").replace(whitespace, " ").trim()
        val match = model.find(text.uppercase(Locale.ROOT)) ?: return NAME + text
        val family = match.groupValues[1].let { if (it == "BGX") "BXG" else it }
        return "$family-${match.groupValues[2]}"
    }

    fun label(key: String): String = key.removePrefix(NAME)

    /** Keep selected, temporarily absent products visible so a sync cannot broaden the scope. */
    fun options(catalog: List<Draw>, matching: List<Draw>, selected: Set<String>): List<ProductOption> {
        val groups = catalog.groupBy { key(it.product) }
        val counts = matching.groupingBy { key(it.product) }.eachCount()
        return (groups.keys + selected).map { key ->
            ProductOption(key, label(key), groups[key].orEmpty().map { it.product }.distinct(), counts[key] ?: 0)
        }.sortedWith(compareBy<ProductOption> {
            families.indexOf(it.key.substringBefore('-')).let { order -> if (order < 0) families.size else order }
        }.thenBy { it.label })
    }
}

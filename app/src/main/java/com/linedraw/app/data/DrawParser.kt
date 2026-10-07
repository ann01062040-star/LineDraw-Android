package com.linedraw.app.data

import org.jsoup.Jsoup

class DrawParser {
    fun parse(html: String, now: Long = System.currentTimeMillis()): List<Draw> {
        require(html.toByteArray().size <= 2_000_000) { "來源頁面過大" }
        val doc = Jsoup.parse(html)
        val stores = doc.select("#page-draws .draw-store")
        require(stores.isNotEmpty()) { "找不到抽選區；保留上次資料" }
        val result = mutableListOf<Draw>()
        val sourceIds = mutableSetOf<String>()
        stores.forEach { store ->
            val name = store.selectFirst(".draw-store-name")?.text()?.trim().orEmpty()
            val city = store.attr("data-draw-city").ifBlank { "未分類" }
            val label = store.selectFirst(".draw-start")?.text().orEmpty()
            require(name.isNotBlank()) { "店家名稱缺漏" }
            val (start, end) = DrawSchedule.parse(label)
            val rows = store.select(".draw-item")
            require(rows.isNotEmpty()) { "店家抽選資料不完整" }
            rows.forEach rowLoop@{ row ->
                // 原站也列出純販售商品；只略過沒有任何連結或操作標記的展示列。
                val actionable = row.hasAttr("data-draw-id") || row.hasAttr("data-draw-href") ||
                    row.hasClass("draw-item-clickable") || row.hasAttr("onclick") || row.hasAttr("onkeydown") ||
                    row.attr("role") == "link" || row.select("a[href], button").isNotEmpty()
                val labelOnly = row.selectFirst(".draw-product")?.text()?.trim().orEmpty()
                if (!actionable && labelOnly.isNotEmpty() && labelOnly.length < 500) return@rowLoop
                require(row.hasAttr("data-draw-href")) { "部分抽選列缺漏網址" }
                val url = row.attr("data-draw-href").trim()
                val product = row.selectFirst(".draw-product")?.text()?.trim().orEmpty()
                require(product.isNotEmpty() && product.length < 500 && name.length < 200) { "商品資料不完整" }
                val key = LinkPolicy.activityKey(url, name, start?.toString() ?: label)
                // 來源有些活動只提供網址。缺少 ID 時以內容產生穩定識別，不依賴排序或同步時間。
                // 活動紀錄仍使用既有 activityKey；來源日後補回 ID 也不會重抽同一活動。
                val id = row.attr("data-draw-id").trim().ifBlank {
                    "generated-" + digest(listOf(name, url, product, start?.toString() ?: label).joinToString("\u0000"))
                }
                require(sourceIds.add(id)) { "來源 ID 重複" }
                result += Draw("$id:${digest(url).take(16)}", id, key, name, city, product, url, label, start, end, result.size, syncedAt = now)
            }
        }
        require(result.size in 1..5000) { "抽選筆數異常" }
        result.groupBy { it.activityKey }.values.forEach { rows ->
            require(rows.map { it.store }.distinct().size == 1) { "同一活動的店家描述衝突" }
        }
        return result
    }

}

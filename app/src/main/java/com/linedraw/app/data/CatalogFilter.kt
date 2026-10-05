package com.linedraw.app.data

enum class DrawStatus(val label: String) {
    READY("可抽選"), NOT_STARTED("尚未開始"), UNKNOWN("時間未確認"), RECORDED("已有紀錄"), ARCHIVED("已封存")
}

/** OR within each group, AND between groups. An empty group means no restriction. */
data class CatalogFilter(
    val cities: Set<String> = emptySet(),
    val statuses: Set<DrawStatus> = emptySet(),
    val query: String = "",
    val products: Set<String> = emptySet()
) {
    fun matches(draw: Draw, hasRecord: Boolean = false, now: Long = System.currentTimeMillis()): Boolean {
        if (cities.isNotEmpty() && draw.city !in cities) return false
        if (products.isNotEmpty() && ProductCatalog.key(draw.product) !in products) return false
        if (query.isNotBlank() && !"${draw.product} ${draw.store} ${draw.city}".contains(query,true)) return false
        // As before, "全部" shows the current catalog; archived rows are explicitly requested.
        if (statuses.isEmpty()) return !draw.archived
        return statuses.any { status ->
            if (status == DrawStatus.ARCHIVED) draw.archived
            else !draw.archived && when (status) {
                DrawStatus.READY -> draw.runnable(now) && !hasRecord
                DrawStatus.NOT_STARTED -> draw.eligibility(now) == Eligibility.NOT_STARTED
                DrawStatus.UNKNOWN -> draw.eligibility(now) == Eligibility.UNKNOWN
                DrawStatus.RECORDED -> hasRecord
                DrawStatus.ARCHIVED -> false
            }
        }
    }
}

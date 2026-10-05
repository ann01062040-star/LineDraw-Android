package com.linedraw.app.data

/** All owner-provided test activities, in conversation order. Coupon identity survives upgrades. */
object TestCatalog {
    // Keep the original record namespace and sync key so existing participation is not lost.
    private const val PROFILE_PREFIX = "__five_links__:"
    const val SYNC_KEY = "lastFiveLinkSync"
    fun profile(name: String) = PROFILE_PREFIX + name
    fun isTestProfile(name: String) = name.startsWith(PROFILE_PREFIX)
    fun isTestRow(draw: Draw) = draw.rowKey in rowKeys
    const val LIFF_APP = "1654883387-DxN9w07M"
    const val STORE = "陀螺獵人BeybladeHunter"
    const val PRODUCT = "陀螺獵人抽選測試"
    private data class Activity(val short: String, val couponId: String, val start: Long, val end: Long, val period: String)
    private fun taipei(date: String) = java.time.LocalDate.parse(date)
        .atStartOfDay(java.time.ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
    private fun batch(start: String, endExclusive: String, period: String, vararg links: Pair<String, String>) =
        links.map { (short, id) -> Activity("https://lin.ee/$short", id, taipei(start), taipei(endExclusive), period) }

    // End boundaries include the entire final 23:59 minute. Historical coupons remain visible
    // with their historical dates; adding a link does not extend its campaign or reset its record.
    private val activities = batch("2026-09-22", "2026-09-30",
        "抽選時間：2026/09/22 00:00～2026/09/29 23:59（台灣時間；使用者更新期限）",
        "WGMIH4U" to "01M34QPHTDYX6TQ5F0M5TKP5J7",
        "niRxKxI" to "01M34QQ7QZNTMR8M7ADSWXY7AW",
        "Q8gr93W" to "01M34QQXZ27VBQPSY134CD6FG4",
        "x2IpNpc" to "01M34QRHHTGYP0NZ594RCR9Z6T",
        "TCxN08P" to "01M34QS2Y5Z75XA9E8ED6SWBD4"
    ) + batch("2026-09-28", "2026-09-30",
        "歷史測試期間：2026/09/28～2026/09/29 23:59（台灣時間；沿用當時測試清單設定）",
        "rCHS0jm" to "01M3M2KQZA4CBCKBQQYQ246W44",
        "rjAUraS" to "01M3M2M6GA2YKG0E43ER7VTK3P",
        "OFlFTEG" to "01M3M2MMNWGBVEPDZ0KCGQGSWA",
        "z0HAfIN" to "01M3M2N3P9YR2QRP58RJBPYN1W",
        "XYCMc6h" to "01M3M2NMF25JT75K2D8EX6SFED",
        "qFLFEqu" to "01M3M2QYX26FBEQ7BSTK8SW8AW",
        "Zc4cWg4" to "01M3M2RETMD5YTTH1Y9CZ3RYMH",
        "sMxf6vS" to "01M3M2RX0SYEWAHQCDNS0RN1EJ",
        "uEs2sF5" to "01M3M2SEQ7FYKSZXS0BPFJ7VNR",
        "8Qf0va2" to "01M3M2STPCFP24J5H6ZVGS8MBP"
    ) + batch("2026-09-29", "2026-11-01",
        "測試啟用：2026/09/29；截止：2026/10/31 23:59（台灣時間；使用者確認截止時間）",
        "NW1ffdO" to "01M3NR6Y99ART5N0WJ1XZVGZDP",
        "o3EdJA8F" to "01M3NR7CBXE30DDE4GGQB4QCGA",
        "6WyOJMT" to "01M3NR7QB9JFTEQAMAC38NZCA1",
        "PpGefet" to "01M3NR81J75SFMQWHRKGHBJ1H0",
        "ynnssrz" to "01M3NR8F5E3B3AR40SS9TM5SED",
        "XrzIhlc" to "01M3NYWYRQS7E5TJSH4AVFBVZT",
        "nM6W6PMb" to "01M3NYXBT8TATAW522BZF2THQ6",
        "w68FqJz" to "01M3NYXQJXB6YQAKYNPF39J62K",
        "OrL4Wzf" to "01M3NYY4GA9FE3PAQBVQA26X3S",
        "vQOsuLS" to "01M3NYYGJ71H8JH7HM5WG0JCFY",
        "o2HccT8" to "01M3P1WYDKQCAEXNS2ZH6J1VMN",
        "OTyB3Jk" to "01M3P1XATPMQ2H6488HC8XVAQ4",
        "97hZCi0" to "01M3P1XMQAXHWFWDZQ8EVFJ6WQ",
        "SES3j9s" to "01M3P1Y0D3K8KJZWXXZ8NM8A0S",
        "S4sB6XO" to "01M3P1YEB564HSFGP2FT72RT3N",
        "Qd5hJVq" to "01M3PFR9C1EN49S8949WFHDPGA",
        "QGhOsnX" to "01M3PFRTX70E47T6BY81FAZZSR",
        "XnMZVTX" to "01M3PFSB92G86W4Y7KWR01DNQZ",
        "W0zn6z4" to "01M3PFSRSA9210CX5B1VA6PH1T",
        "yz7xFEWc" to "01M3PFT6EBNAC6XZ8J4BCBHKNX"
    )
    val links = activities.map { it.short to it.couponId }
    val rowKeys = links.map { (_, id) -> "pilot:$id" }
    fun canonical(id: String) = "https://liff.line.me/$LIFF_APP/c/$id"
    fun allowsUrl(url: String): Boolean = links.any { (short, id) -> url == short || url == canonical(id) }
    fun allowsItem(item: BatchItem): Boolean = links.any { (short,id) ->
        item.activityKey == "coupon:$LIFF_APP:$id" && (item.url == short || item.url == canonical(id))
    }
    fun draws(now: Long = System.currentTimeMillis()) = activities.mapIndexed { index, activity ->
        Draw("pilot:${activity.couponId}", "pilot:${index+1}", "coupon:$LIFF_APP:${activity.couponId}", STORE, "實機測試",
            PRODUCT, activity.short, activity.period, activity.start, activity.end, index, syncedAt=now)
    }
    fun allowsDraw(draw: Draw): Boolean = draws(0).any { it.rowKey == draw.rowKey && it.activityKey == draw.activityKey && it.url == draw.url && !draw.demo && !draw.archived }
}

fun Batch.isFiveLinkTest(): Boolean = com.linedraw.app.BuildConfig.FIVE_LINK_TEST || TestCatalog.isTestProfile(profile)
fun Draw.runnable(now: Long = System.currentTimeMillis()): Boolean = eligibility(now) == Eligibility.READY &&
    (!(com.linedraw.app.BuildConfig.FIVE_LINK_TEST || TestCatalog.isTestRow(this)) || TestCatalog.allowsDraw(this))
fun Draw.eligibilityLabel(now: Long = System.currentTimeMillis()): String = if (TestCatalog.isTestRow(this) && runnable(now)) "可測試 · 第 ${ordinal+1} 筆" else eligibility(now).label

package com.linedraw.app.data

/** 獨立版僅讀取 Funbox，保留既有活動識別及本機紀錄格式。 */
enum class DrawCatalog(val id: String, val label: String, val url: String) {
    FUNBOX("funbox", "Funbox 原站", SOURCE_URL);

    val dataUrl get() = url
    fun parse(payload: String, now: Long = System.currentTimeMillis()): List<Draw> = DrawParser().parse(payload, now)
    fun owns(draw: Draw): Boolean = !draw.demo && !TestCatalog.isTestRow(draw) && !draw.rowKey.startsWith("catalog:")
    fun scope(draw: Draw): Draw = draw
    fun metaKey(key: String): String = key
    val preferenceScope get() = "website"

    companion object {
        fun fromId(id: String?): DrawCatalog {
            require(id == null || id == FUNBOX.id) { "此版本僅支援 Funbox 來源" }
            return FUNBOX
        }
    }
}

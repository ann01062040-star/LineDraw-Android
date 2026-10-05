package com.linedraw.app.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Only the range attached to the draw clause counts; separate coupon validity does not. */
object DrawSchedule {
    data class Period(val start: Long?, val end: Long?)
    private val date = Regex("\\d{4}/\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}")
    private val formatter = DateTimeFormatter.ofPattern("uuuu/M/d H:mm").withResolverStyle(ResolverStyle.STRICT)
    fun parse(label: String): Period {
        val normalized = label.replace('：', ':').replace('／', '/')
            .replace(Regex("[（(](?:星期|週|周)?[一二三四五六日天][）)]"), " ")
        val clause = normalized.split(Regex("[｜|；;\\n]"))
            .firstOrNull { Regex("抽[選籤]").containsMatchIn(it) } ?: return Period(null, null)
        val drawAt = Regex("抽[選籤]").find(clause)!!.range.first
        val text = clause.substring(drawAt)
        val first = date.find(text) ?: return Period(null, null)
        val start = parseDate(first.value)
        // A slash/joint heading such as 抽選/購買時間 describes one shared range.
        // A later 使用期限 label is separate and must never supply the draw's end.
        val tail = text.substring(first.range.last + 1)
        val separator = Regex("^\\s*[~～至到－—–-]\\s*").find(tail)
        val endText = separator?.let { date.find(tail, it.range.last + 1) }
            ?.takeIf { it.range.first == separator!!.range.last + 1 }?.value
        val end = endText?.let(::parseDate)
        require(start == null || end == null || end > start) { "抽選起訖時間衝突" }
        return Period(start, end)
    }
    private fun parseDate(value: String): Long? = runCatching {
        LocalDateTime.parse(value.replace(Regex("\\s+"), " "), formatter)
            .atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
    }.getOrNull()
}

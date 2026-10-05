package com.linedraw.app

import com.linedraw.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ParserTest {
    private fun html(label: String = "抽選時間：2026/09/24 11:00~2026/09/25 21:00", url: String = "https://lin.ee/test", rows: String? = null): String = """
        <div id="page-draws"><div class="draw-store" data-draw-city="台北市">
        <div class="draw-store-name">Funbox 測試店</div><div class="draw-start">$label</div>
        ${rows ?: """<div class="draw-item" data-draw-id="draw-a" data-draw-href="$url"><div class="draw-product">UX-03 魔導神杖</div></div>"""}
        </div></div>"""
    @Test fun taipeiTimesAndBoundaries() {
        val d = DrawParser().parse(html()).single()
        assertEquals(Instant.parse("2026-09-24T03:00:00Z").toEpochMilli(), d.startsAt)
        assertEquals(Eligibility.NOT_STARTED,d.eligibility(d.startsAt!!-1))
        assertEquals(Eligibility.READY,d.eligibility(d.startsAt))
        assertEquals(Eligibility.EXPIRED,d.eligibility(d.endsAt!!))
    }
    @Test fun separatePurchaseDeadlineIsNotDrawDeadline() {
        val d = DrawParser().parse(html("抽選開放：2026/09/24 11:00｜購買期限：2026/09/25 21:00")).single()
        assertNotNull(d.startsAt);assertNull(d.endsAt)
        assertEquals(Eligibility.UNKNOWN,d.eligibility(d.startsAt!!+1))
    }
    @Test fun timeAloneNeverAssumesToday() { val d=DrawParser().parse(html("11:00")).single();assertNull(d.startsAt);assertEquals(Eligibility.UNKNOWN,d.eligibility()) }
    @Test fun invalidCalendarDateIsUnknown() {assertNull(DrawParser().parse(html("抽選時間：2026/02/30 11:00~2026/03/01 21:00")).single().startsAt)}
    @Test(expected=IllegalArgumentException::class) fun invertedDatesRejected() {DrawParser().parse(html("抽選時間：2026/09/25 11:00~2026/09/24 21:00"))}
    @Test(expected=IllegalArgumentException::class) fun emptyPageRejected() {DrawParser().parse("<html>network error</html>")}
    @Test(expected=IllegalArgumentException::class) fun duplicateSourceIdsRejected() {val row="""<div class="draw-item" data-draw-id="same" data-draw-href="https://lin.ee/a"><div class="draw-product">商品</div></div>""";DrawParser().parse(html(rows=row+row))}
    @Test(expected=IllegalArgumentException::class) fun incompleteRowsRejected() {DrawParser().parse(html(rows="""<div class="draw-item"><div class="draw-product">無網址</div></div>"""))}
    @Test fun duplicateUrlsPreserveSourceRowsAndOrder() {val rows=listOf("a" to "UX-03 魔導神杖","b" to "BX-09 通行證").joinToString(""){(id,p)->"""<div class="draw-item" data-draw-id="$id" data-draw-href="https://lin.ee/shared"><div class="draw-product">$p</div></div>"""};val d=DrawParser().parse(html(rows=rows));assertEquals(listOf("a","b"),d.map{it.sourceId});assertEquals(d[0].activityKey,d[1].activityKey);assertNotEquals(d[0].rowKey,d[1].rowKey)}
    @Test fun linkAllowlistRejectsDeceptiveDestinations() {listOf("http://lin.ee/a","https://lin.ee.evil.test/a","https://lin.ee@evil.test/a","javascript:alert(1)","https://liff.line.me:8080/a","file:///data/private").forEach{assertFalse(it,LinkPolicy.allowed(it))}}
    @Test fun sameProductDifferentStoreIsDistinctWithoutCanonicalProof() {assertNotEquals(LinkPolicy.activityKey("https://lin.ee/a","台北"),LinkPolicy.activityKey("https://lin.ee/a","台中"))}
    @Test fun differentShortLinksRemainDistinct() {assertNotEquals(LinkPolicy.activityKey("https://lin.ee/a","台北"),LinkPolicy.activityKey("https://lin.ee/b","台北"))}
    @Test fun canonicalCouponIdentityIgnoresTracking() {assertEquals(LinkPolicy.activityKey("https://liff.line.me/app/c/ABC?x=1","店"),LinkPolicy.activityKey("https://liff.line.me/app/c/ABC?x=2","店"))}
    @Test fun reusedSourceIdNewLinkIsNewRow() {assertNotEquals(DrawParser().parse(html(url="https://lin.ee/a")).single().rowKey,DrawParser().parse(html(url="https://lin.ee/b")).single().rowKey)}
    @Test fun recycledShortUrlInNewPeriodDoesNotInheritCompletion() {assertNotEquals(DrawParser().parse(html()).single().activityKey,DrawParser().parse(html("抽選時間：2026/10/24 11:00~2026/10/25 21:00")).single().activityKey)}
    @Test fun realSnapshotPreservesTwentyRows() {val source=javaClass.getResource("/funbox-20260922.html")!!.readText();val d=DrawParser().parse(source);assertEquals(20,d.size);assertEquals(2,d.map{it.store}.distinct().size);assertEquals("BX-00 暴風天馬",d.first().product);assertTrue(d.all{it.endsAt!=null})}
    @Test fun displayOnlySalesRowDoesNotBlockValidDraws() {
        val source=html().replace("<div class=\"draw-item\"", "<div class=\"draw-item\"><div class=\"draw-product\">BX-37（採取上架販售）</div></div><div class=\"draw-item\"")
        val rows=DrawParser().parse(source)
        assertEquals(1,rows.size);assertEquals("draw-a",rows.single().sourceId);assertEquals(0,rows.single().ordinal)
    }
    @Test fun actionableRowsMissingIdentityStillRejectWholePage() {
        val badRows=listOf("data-draw-id=\"bad\"", "data-draw-href=\"https://lin.ee/bad\"", "onclick=\"openDraw()\"", "role=\"link\"")
        badRows.forEach { marker ->
            val bad="<div class=\"draw-item\" $marker><div class=\"draw-product\">抽選</div></div>"
            assertThrows(IllegalArgumentException::class.java) { DrawParser().parse(html().replace("</div></div>","</div>$bad</div>")) }
        }
    }
    @Test fun latestSourceWithSalesOnlyProductParsesAllDrawLinks() {
        val rows=DrawParser().parse(javaClass.getResource("/funbox-20261002.html")!!.readText())
        assertEquals(1289,rows.size);assertEquals(76,rows.map{it.store}.distinct().size)
        assertFalse(rows.any{it.product.contains("採取上架販售")})
        assertEquals("https://lin.ee/yF6X0kS",rows.first().url)
    }

}

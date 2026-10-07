package com.linedraw.app

import com.linedraw.app.data.*
import org.junit.Assert.*
import org.junit.Test

class FunboxIdentityTest {
    private fun row(url: String, product: String = "商品", id: String? = null) = """
        <div class="draw-item draw-item-clickable" data-draw-href="$url" ${id?.let { "data-draw-id=\"$it\"" }.orEmpty()}>
        <div class="draw-product">$product</div></div>
    """
    private fun page(rows: String, date: String = "2026/10/08") = """
        <div id="page-draws"><div class="draw-store" data-draw-city="高雄市">
        <div class="draw-store-name">測試店</div><div class="draw-start">抽選時間：$date 10:00~2026/10/31 23:59</div>
        $rows</div></div>
    """
    @Test fun october8SnapshotIncludesAllNineRowsWithoutSourceIds() {
        val draws = DrawParser().parse(javaClass.getResource("/funbox-20261008.html")!!.readText())
        assertEquals(431, draws.size)
        assertEquals(40, draws.map { it.store }.distinct().size)
        val added = draws.filter { it.store == "來玩聚-楠梓家樂福" }
        assertEquals(9, added.size)
        assertEquals("https://lin.ee/Xsi1PwB", added.first().url)
        assertEquals("https://lin.ee/VRkixSh", added.last().url)
        assertEquals(draws.size, draws.map { it.rowKey }.distinct().size)
        assertEquals(draws.indices.toList(), draws.map { it.ordinal })
        assertTrue(draws.all { LinkPolicy.allowed(it.url) })
    }
    @Test fun missingIdsRemainStableAcrossReorderInsertionAndFetchTime() {
        val a = row("https://lin.ee/a", "商品 A")
        val b = row("https://lin.ee/b", "商品 B")
        val before = DrawParser().parse(page(a + b), 1).associateBy { it.url }
        val after = DrawParser().parse(page(row("https://lin.ee/new") + b + a), 2).associateBy { it.url }
        before.forEach { (url, draw) ->
            assertEquals(draw.sourceId, after[url]!!.sourceId)
            assertEquals(draw.rowKey, after[url]!!.rowKey)
            assertEquals(draw.activityKey, after[url]!!.activityKey)
        }
    }
    @Test fun productsSharingOneUrlKeepSeparateRowsAndOneActivity() {
        val draws = DrawParser().parse(page(row("https://lin.ee/a", "商品 A") + row("https://lin.ee/a", "商品 B")))
        assertNotEquals(draws[0].rowKey, draws[1].rowKey)
        assertEquals(draws[0].activityKey, draws[1].activityKey)
    }
    @Test fun suppliedIdsKeepExistingRowIdentity() {
        val draw = DrawParser().parse(page(row("https://lin.ee/a", id = "draw-existing"))).single()
        assertEquals("draw-existing", draw.sourceId)
        assertEquals("draw-existing:${digest("https://lin.ee/a").take(16)}", draw.rowKey)
    }
    @Test fun blankIdAndLaterRestoredIdKeepActivityIdentity() {
        val missing = DrawParser().parse(page(row("https://lin.ee/a"))).single()
        val blank = DrawParser().parse(page(row("https://lin.ee/a", id = "  "))).single()
        val restored = DrawParser().parse(page(row("https://lin.ee/a", id = "draw-restored"))).single()
        assertEquals(missing.rowKey, blank.rowKey)
        assertEquals(missing.activityKey, restored.activityKey)
    }
    @Test fun reusedShortLinkInNewPeriodDoesNotInheritOldActivity() {
        val old = DrawParser().parse(page(row("https://lin.ee/a"))).single()
        val next = DrawParser().parse(page(row("https://lin.ee/a"), "2026/10/09")).single()
        assertNotEquals(old.activityKey, next.activityKey)
    }
    @Test fun missingIdNeverMakesMissingOrUnsafeUrlAcceptable() {
        listOf("", "http://lin.ee/a", "https://lin.ee.evil.test/a", "javascript:alert(1)").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { DrawParser().parse(page(row(url))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrawParser().parse(page("""<div class="draw-item" onclick="openDraw()"><div class="draw-product">商品</div></div>"""))
        }
    }
    @Test fun duplicateGeneratedRowsAndDuplicateSuppliedIdsRemainRejected() {
        val same = row("https://lin.ee/a")
        assertThrows(IllegalArgumentException::class.java) { DrawParser().parse(page(same + same)) }
        assertThrows(IllegalArgumentException::class.java) {
            DrawParser().parse(page(row("https://lin.ee/a", id = "same") + row("https://lin.ee/b", id = "same")))
        }
    }
}

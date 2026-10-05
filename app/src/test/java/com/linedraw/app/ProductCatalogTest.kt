package com.linedraw.app

import com.linedraw.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ProductCatalogTest {
    @Test fun currentWebsiteProductNamesMatchItsModelFilter() {
        val rows=javaClass.getResource("/funbox-product-models-20261001.tsv")!!.readText()
            .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t',limit=2) }.toList()
        assertEquals(92,rows.size)
        rows.forEach { (model,name) -> assertEquals(name,model,ProductCatalog.key(name)) }
        assertEquals(30,rows.map { it.first() }.distinct().size)
    }
    private fun row(id: String, product: String) = Draw(id,id,"coupon:app:$id","店家","台北",product,
        "https://liff.line.me/app/c/$id","",0,10_000,0)

    @Test fun modelKeysGroupNameVariantsAndMatchWebsiteAlias() {
        listOf("UX-03 魔導神杖", "ux - 03 不同店家寫法", "ＵＸ－０３魔導神杖", "UX–03 魔導神杖")
            .forEach { assertEquals(it,"UX-03",ProductCatalog.key(it)) }
        assertEquals("BXG-01",ProductCatalog.key("BGX-01 復刻版"))
        assertEquals("BXG-04",ProductCatalog.key("BXG-04"))
        assertEquals(ProductCatalog.key("BX-00 蒼龍神劍"),ProductCatalog.key("BX-00 暴風天馬"))
    }
    @Test fun similarOrEmbeddedCodesDoNotMatchAndUncodedProductsRemainSelectable() {
        listOf("UX-030", "UX-03A", "XUX-03", "BXG-010").forEach {
            assertTrue(ProductCatalog.key(it).startsWith("name:"))
            assertFalse(CatalogFilter(products=setOf("UX-03","BXG-01")).matches(row("A",it),now=1))
        }
        assertEquals("name:自動抽獎 測試",ProductCatalog.key("  自動抽獎　測試  "))
        val key=ProductCatalog.key("自動抽獎測試")
        assertTrue(CatalogFilter(products=setOf(key)).matches(row("A","自動抽獎測試"),now=1))
        assertFalse(CatalogFilter(products=setOf(key)).matches(row("A","別的抽獎"),now=1))
    }
    @Test fun optionsGroupActualCatalogKeepAbsentSelectionsAndCountOtherFilters() {
        val rows=listOf(row("A","UX-03 魔導神杖"),row("B","UX-03 別名"),row("C","CX-19 鱷魚裂甲"),
            row("D","BGX-01 復刻"),row("E","BXG-01 復刻商品"),row("F","BX-00 蒼龍神劍"))
        val options=ProductCatalog.options(rows,rows.take(3),setOf("UX-99"))
        assertEquals(listOf("BX-00","UX-03","UX-99","CX-19","BXG-01"),options.map { it.key })
        assertEquals(2,options.single { it.key=="UX-03" }.count)
        assertEquals(2,options.single { it.key=="BXG-01" }.names.size)
        assertEquals(0,options.single { it.key=="UX-99" }.count)
        assertFalse(CatalogFilter(products=setOf("UX-99")).matches(rows.first(),now=1))
    }
}

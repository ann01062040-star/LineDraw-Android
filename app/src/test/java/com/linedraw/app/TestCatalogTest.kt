package com.linedraw.app

import com.linedraw.app.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class TestCatalogTest {
    @Test fun allThirtyFiveLinksPreserveOwnerOrderAndCase() {
        val codes = listOf(
            "WGMIH4U", "niRxKxI", "Q8gr93W", "x2IpNpc", "TCxN08P",
            "rCHS0jm", "rjAUraS", "OFlFTEG", "z0HAfIN", "XYCMc6h",
            "qFLFEqu", "Zc4cWg4", "sMxf6vS", "uEs2sF5", "8Qf0va2",
            "NW1ffdO", "o3EdJA8F", "6WyOJMT", "PpGefet", "ynnssrz",
            "XrzIhlc", "nM6W6PMb", "w68FqJz", "OrL4Wzf", "vQOsuLS",
            "o2HccT8", "OTyB3Jk", "97hZCi0", "SES3j9s", "S4sB6XO",
            "Qd5hJVq", "QGhOsnX", "XnMZVTX", "W0zn6z4", "yz7xFEWc"
        )
        assertEquals(codes.map { "https://lin.ee/$it" },TestCatalog.draws().map{it.url})
        assertEquals((0..34).toList(),TestCatalog.draws().map{it.ordinal})
        assertEquals(35,TestCatalog.rowKeys.toSet().size)
    }
    @Test fun identicalNamesHaveSeparateActivityKeysAndCanonicalAliases() {
        val rows=TestCatalog.draws();assertEquals(35,rows.map{it.activityKey}.toSet().size)
        assertTrue(rows.all{it.store=="陀螺獵人BeybladeHunter" && it.product=="陀螺獵人抽選測試"})
        rows.forEach { row ->
            val canonical=LinkPolicy.canonicalUrl(row.activityKey)!!
            assertTrue(TestCatalog.allowsUrl(canonical))
            assertTrue(TestCatalog.allowsItem(BatchItem("b",row.ordinal,row.activityKey,row.product,row.store,canonical,row.startsAt,row.endsAt)))
        }
        // The full LIFF URL shared in the conversation is an alias of the first short link.
        assertEquals(1,rows.count { LinkPolicy.canonicalUrl(it.activityKey)=="https://liff.line.me/1654883387-DxN9w07M/c/01M34QPHTDYX6TQ5F0M5TKP5J7" })
    }
    @Test fun taipeiWindowsIncludeFinalMinuteWithoutExtendingHistoricalCoupons() {
        val rows=TestCatalog.draws()
        assertEquals(Instant.parse("2026-09-21T16:00:00Z").toEpochMilli(),rows.first().startsAt)
        rows.forEachIndexed { index,row ->
            val end=Instant.parse(if(index<15) "2026-09-29T16:00:00Z" else "2026-10-31T16:00:00Z").toEpochMilli()
            assertEquals(end,row.endsAt)
            assertEquals(Eligibility.NOT_STARTED,row.eligibility(row.startsAt!!-1))
            assertEquals(Eligibility.READY,row.eligibility(end-1))
            assertEquals(Eligibility.EXPIRED,row.eligibility(end))
        }
        val octoberFirst=Instant.parse("2026-10-01T02:00:00Z").toEpochMilli()
        assertEquals(20,rows.count { it.runnable(octoberFirst) })
        assertEquals(15,rows.count { it.eligibility(octoberFirst)==Eligibility.EXPIRED })
    }
    @Test fun otherLineLinksAndChangedDestinationAreRejected() {
        assertFalse(TestCatalog.allowsUrl("https://lin.ee/other"))
        assertFalse(TestCatalog.allowsUrl("https://lin.ee/WGMIH4U?next=evil"))
        val d=TestCatalog.draws().first()
        val wrong=BatchItem("b",0,d.activityKey,d.product,d.store,TestCatalog.links[1].first,d.startsAt,d.endsAt)
        assertFalse(TestCatalog.allowsItem(wrong))
        assertTrue(TestCatalog.allowsItem(wrong.copy(url=d.url)))
        assertTrue(TestCatalog.allowsItem(wrong.copy(url=TestCatalog.canonical(TestCatalog.links[0].second))))
    }
    @Test fun alteredRowsAndSimulationCannotEnterPilotCatalog() {
        val d=TestCatalog.draws().first()
        assertTrue(TestCatalog.allowsDraw(d));assertFalse(TestCatalog.allowsDraw(d.copy(url="https://lin.ee/other")))
        assertFalse(TestCatalog.allowsDraw(d.copy(demo=true)))
        assertFalse(TestCatalog.allowsDraw(d.copy(archived=true)))
    }
}

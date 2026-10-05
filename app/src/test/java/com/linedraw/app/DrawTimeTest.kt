package com.linedraw.app

import com.linedraw.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class DrawTimeTest {
    private fun at(time:String)=Instant.parse("2026-09-24T${time}Z").toEpochMilli()
    private fun draw(label:String):Draw = DrawParser().parse("""
        <div id="page-draws"><div class="draw-store" data-draw-city="彰化縣">
        <div class="draw-store-name">來玩聚 員林店</div><div class="draw-start">$label</div>
        <div class="draw-item" data-draw-id="a" data-draw-href="https://lin.ee/a"><div class="draw-product">測試商品</div></div>
        </div></div>
    """).single()
    @Test fun sharedDrawAndPurchasePeriodOpensAtTenTaipei() {
        val d=draw("抽選/購買時間：2026/09/24 10:00~2026/09/25 21:00")
        assertEquals(at("02:00:00"),d.startsAt)
        assertEquals(Instant.parse("2026-09-25T13:00:00Z").toEpochMilli(),d.endsAt)
        assertFalse(d.runnable(at("01:59:59")))
        assertTrue(d.runnable(at("02:00:00")))
        assertTrue(d.runnable(d.endsAt!!-1));assertFalse(d.runnable(d.endsAt))
    }
    @Test fun sharedHeadingsUseTheSameExplicitPeriod() {
        listOf("抽選/購買時間","抽選／販售時間","抽籤時間&使用期限","抽選/購買資格時間","戰鬥陀螺X抽籤及販售時間").forEach { heading ->
            val d=draw("$heading：2026/09/24 10:30～2026/09/25 21:00")
            assertEquals(heading,at("02:30:00"),d.startsAt)
            assertNotNull(heading,d.endsAt)
        }
    }
    @Test fun separateCouponUsePeriodIsNeverBorrowedAsDrawDeadline() {
        listOf(
            "抽選開放：2026/09/24 11:00｜購買資格券有效：2026/09/24 11:00~22:00、2026/09/25 11:00~21:00",
            "抽選：2026/09/24 11:00 開放｜購買資格券有效：2026/09/24 11:00~21:30、2026/09/25 11:00~21:00",
            "抽選開始：2026/09/24 11:00，使用期限：2026/09/25 21:00"
        ).forEach { label ->
            val d=draw(label);assertEquals(at("03:00:00"),d.startsAt);assertNull(d.endsAt)
            assertEquals(Eligibility.UNKNOWN,d.eligibility(at("03:00:00")))
        }
    }
    @Test fun explicitDrawRangeSurvivesSeparatePurchaseInstructions() {
        val d=draw("抽選資格：2026/09/24 11:00~2026/09/25 20:30｜中籤購買：9/24 11:00~21:30、9/25 11:00~20:30")
        assertEquals(Instant.parse("2026-09-25T12:30:00Z").toEpochMilli(),d.endsAt)
    }
    @Test fun weekdayDecorationRetainsStartButDoesNotInventDeadline() {
        val d=draw("抽選開始：2026/09/24（四）11:00（貼文未註明截止時間）")
        assertEquals(at("03:00:00"),d.startsAt);assertNull(d.endsAt)
    }
    @Test fun earlierPurchaseClauseCannotBecomeDrawStart() {
        val d=draw("使用期限：2026/09/23 10:00~2026/09/25 21:00｜抽選時間：2026/09/24 11:00~2026/09/24 12:00")
        assertEquals(at("03:00:00"),d.startsAt);assertEquals(at("04:00:00"),d.endsAt)
    }
    @Test fun deviceTimezoneDoesNotChangeTaiwanSchedule() {
        val before=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertTrue(draw("抽選/購買時間：2026/09/24 10:00~2026/09/25 21:00").runnable(at("02:00:00")))
        } finally {TimeZone.setDefault(before)}
    }
    @Test fun todaysSourceUnlocksOnlyStoresWhoseOpeningHasArrived() {
        val rows=DrawParser().parse(javaClass.getResource("/funbox-20260924.html")!!.readText())
        assertEquals(623,rows.size);assertEquals(51,rows.map{it.store}.distinct().size)
        assertEquals(0,rows.count{it.runnable(at("01:59:59"))})
        assertEquals(64,rows.count{it.runnable(at("02:00:00"))})
        assertEquals(6,rows.filter{it.runnable(at("02:00:00"))}.map{it.store}.distinct().size)
        assertEquals(87,rows.count{it.runnable(at("02:30:00"))})
        assertTrue(rows.all{it.ordinal==rows.indexOf(it)})
    }
}

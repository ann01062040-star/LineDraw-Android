package com.linedraw.app

import com.linedraw.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CatalogFilterTest {
    private val now=1_000L
    private val ready=Draw("row","row","coupon:app:A","台北店","台北","陀螺 A","https://liff.line.me/app/c/A","",0,10_000,0)
    private val future=ready.copy(rowKey="future",city="新北",startsAt=2_000)
    private fun CatalogFilter.accept(d:Draw,record:Boolean=false)=matches(d,record,now)

    @Test fun citiesAndStatusesEachUseOrAndCombineWithAnd() {
        val scope=CatalogFilter(setOf("台北","新北"),setOf(DrawStatus.READY,DrawStatus.NOT_STARTED))
        assertTrue(scope.accept(ready));assertTrue(scope.accept(future))
        assertFalse(scope.accept(ready.copy(city="高雄")))
        assertFalse(scope.accept(future.copy(city="高雄")))
        assertFalse(scope.accept(ready.copy(endsAt=now)))
    }
    @Test fun searchAppliesToEverySelectedCityAndStatus() {
        val scope=CatalogFilter(setOf("台北","新北"),setOf(DrawStatus.READY,DrawStatus.NOT_STARTED),"陀螺")
        assertTrue(scope.accept(ready));assertTrue(scope.accept(future))
        assertFalse(scope.accept(future.copy(product="收納盒")))
    }
    @Test fun readyAndRecordedIncludeBothWithoutMakingRecordsRunnableAgain() {
        assertFalse(CatalogFilter(statuses=setOf(DrawStatus.READY)).accept(ready,true))
        val scope=CatalogFilter(statuses=setOf(DrawStatus.READY,DrawStatus.RECORDED))
        assertTrue(scope.accept(ready));assertTrue(scope.accept(ready,true))
        assertFalse(scope.accept(future))
    }
    @Test fun archivedCanCombineWithActiveButIsNeverImplicitlyShown() {
        val archived=ready.copy(archived=true)
        assertFalse(CatalogFilter().accept(archived))
        assertFalse(CatalogFilter(statuses=setOf(DrawStatus.RECORDED)).accept(archived,true))
        val scope=CatalogFilter(statuses=setOf(DrawStatus.ARCHIVED,DrawStatus.READY))
        assertTrue(scope.accept(archived));assertTrue(scope.accept(ready));assertFalse(scope.accept(future))
        assertFalse(archived.runnable(now))
    }
    @Test fun allClearsRestrictionsAndKeepsExistingExpiredVisibility() {
        val scope=CatalogFilter()
        listOf(ready,future,ready.copy(city="高雄"),ready.copy(endsAt=now)).forEach { assertTrue(scope.accept(it)) }
    }
    @Test fun unknownAndFutureMayOverlapButFilteringDoesNotDuplicateRows() {
        val unknown=ready.copy(startsAt=null,endsAt=null)
        val futureWithRecord=future.copy(activityKey="same")
        val scope=CatalogFilter(statuses=setOf(DrawStatus.UNKNOWN,DrawStatus.NOT_STARTED,DrawStatus.RECORDED))
        val rows=listOf(ready,unknown,futureWithRecord).filter { scope.accept(it,it.activityKey=="same") }
        assertEquals(listOf(unknown,futureWithRecord),rows)
        assertFalse(unknown.runnable(now));assertFalse(future.runnable(now))
    }
    @Test fun boundariesDoNotAllowEarlyOrExpiredSubmission() {
        assertFalse(future.runnable(1_999));assertTrue(future.runnable(2_000))
        assertFalse(ready.runnable(10_000))
    }
    @Test fun multipleProductsCombineWithCitiesStatusesAndSearchWithoutReordering() {
        val rows=listOf(ready.copy(product="UX-03 魔導神杖"),future.copy(product="CX-19 鱷魚裂甲"),
            ready.copy(product="UX-03 魔導神杖",city="高雄"),ready.copy(product="UX-030 相似型號"),
            ready.copy(product="BX-00 暴風天馬"),ready.copy(product="CX-19 鱷魚裂甲",endsAt=now))
        val scope=CatalogFilter(setOf("台北","新北"),setOf(DrawStatus.READY,DrawStatus.NOT_STARTED),
            products=setOf("UX-03","CX-19"))
        assertEquals(rows.take(2),rows.filter { scope.accept(it) })
        assertEquals(listOf(rows[1]),rows.filter { scope.copy(query="鱷魚").accept(it) })
        assertFalse(scope.accept(rows[0],true))
        assertEquals(4,rows.filter { scope.copy(products=emptySet()).accept(it) }.size)
    }
}

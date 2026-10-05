package com.linedraw.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ContinuationTest {
    data class Row(val id:String,val target:String=id,val urlToken:String=id,val title:String=id)
    class Source:CatalogSource {
        var rows=listOf(Row("A"),Row("B"),Row("C"))
        var store="店家";var city="台北";var fail=false;var beforeFetch:(suspend ()->Unit)?=null
        var fetches=0;var resolutions=0
        override suspend fun fetch(catalog: DrawCatalog):String {
            fetches++;beforeFetch?.invoke();if(fail) error("test failure")
            return """<div id="page-draws"><div class="draw-store" data-draw-city="$city"><div class="draw-store-name">$store</div><div class="draw-start">抽選時間：2020/01/01 00:00~2099/12/31 23:59</div>${rows.joinToString(""){"""<div class="draw-item" data-draw-id="${it.id}" data-draw-href="https://lin.ee/${it.urlToken}"><div class="draw-product">${it.title}</div></div>"""}}</div></div>"""
        }
        override suspend fun resolve(url:String):String {
            resolutions++
            if(url.startsWith("https://liff.line.me/")) return url
            val row=rows.first {url=="https://lin.ee/${it.urlToken}"}
            if(row.target=="UNRESOLVED") error("link temporarily unavailable")
            return "https://liff.line.me/app/c/${row.target}"
        }
    }
    private lateinit var db:DrawDatabase
    private lateinit var repo:Repository
    private lateinit var source:Source
    @Before fun setup()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        db=Room.inMemoryDatabaseBuilder(context,DrawDatabase::class.java).allowMainThreadQueries().build()
        source=Source();repo=Repository(db,context,source,access=TestAccess){true};repo.sync();Unit
    }
    @After fun close(){db.close()}
    private fun key(id:String)="coupon:app:$id"
    private suspend fun start(vararg ids:String,auto:Boolean=true,city:String="所有地區",query:String=""):Batch {
        val rows=repo.dao.currentDraws(false).filter{it.sourceId in ids}
        return repo.start(rows.map{it.rowKey},"p",true,false,auto,if(city=="所有地區") emptySet() else setOf(city),query)
    }
    private suspend fun finishRound(b:Batch) {
        val current=repo.dao.activeBatch()!!
        for(position in current.currentIndex until current.total) repo.finish(b.id,position,Participation.SUBMITTED,"未讀取","test dispatched")
    }
    @Test fun appendOnlyNewIdsInWebsiteOrderAndExcludeUnselectedOldRows()=runBlocking {
        val b=start("A","B");finishRound(b)
        source.rows=listOf(Row("C"),Row("A"),Row("D"),Row("B"),Row("E"))
        assertTrue(repo.continueAfterQueue(b.id))
        assertEquals(listOf("A","B","D","E").map(::key),repo.dao.items(b.id).map{it.activityKey})
        finishRound(b);assertFalse(repo.continueAfterQueue(b.id));assertEquals("FINISHED",repo.dao.latestBatch()!!.state)
    }
    @Test fun renameReorderAndAliasNeverReplayAlreadySentCampaign()=runBlocking {
        val b=start("A");finishRound(b)
        source.store="店家新名稱";source.rows=listOf(Row("newRow","A","alias","新標題"),Row("B"),Row("A",title="更名"))
        assertFalse(repo.continueAfterQueue(b.id));assertEquals(1,repo.dao.items(b.id).size)
        assertEquals("SUBMITTED",repo.dao.record("p",key("A"))!!.status)
    }
    @Test fun recycledShortUrlResolvesAgainEvenWithSameHtml()=runBlocking {
        val b=start("A");finishRound(b)
        val originalHash=repo.dao.meta("sourceHash")!!.value
        source.rows=listOf(Row("A","NEW"),Row("B"),Row("C"))
        assertTrue(repo.continueAfterQueue(b.id))
        assertEquals(originalHash,repo.dao.meta("sourceHash")!!.value)
        assertEquals(key("NEW"),repo.dao.items(b.id).last().activityKey)
        assertEquals("https://liff.line.me/app/c/A",repo.dao.items(b.id).first().url)
    }
    @Test fun duplicateNewAliasesAppendOnce()=runBlocking {
        val b=start("A");finishRound(b)
        source.rows=source.rows+listOf(Row("D"),Row("Dalias","D"))
        assertTrue(repo.continueAfterQueue(b.id));assertEquals(2,repo.dao.items(b.id).size)
    }
    @Test fun manualSyncDuringRunningBatchDoesNotReorderItsSnapshot()=runBlocking {
        val b=start("A","B")
        source.rows=listOf(Row("D"),Row("B"),Row("A"),Row("C"));repo.sync()
        assertEquals(listOf(key("A"),key("B")),repo.dao.items(b.id).map{it.activityKey})
        finishRound(b);assertTrue(repo.continueAfterQueue(b.id));assertEquals(key("D"),repo.dao.items(b.id).last().activityKey)
    }
    @Test fun stopWhileSyncingCannotRestartQueue()=runBlocking {
        val b=start("A");finishRound(b)
        source.rows=source.rows+Row("D")
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        source.beforeFetch={entered.complete(Unit);release.await()}
        val work=async{repo.continueAfterQueue(b.id)};entered.await();repo.pause("stop",true);release.complete(Unit)
        assertFalse(work.await());assertEquals("STOPPED",repo.dao.latestBatch()!!.state);assertEquals(1,repo.dao.items(b.id).size)
    }
    @Test fun concurrentContinuationDoesNotAppendTwice()=runBlocking {
        val b=start("A");finishRound(b);source.rows=source.rows+Row("D")
        val results=(1..3).map{async{repo.continueAfterQueue(b.id)}}.awaitAll()
        assertEquals(1,results.count{it});assertEquals(2,repo.dao.items(b.id).size)
    }
    @Test fun additionalRoundsAreCappedAtThree()=runBlocking {
        val b=start("A")
        for(id in listOf("D","E","F")) {finishRound(b);source.rows=source.rows+Row(id);assertTrue(repo.continueAfterQueue(b.id))}
        finishRound(b);source.rows=source.rows+Row("G")
        assertFalse(repo.continueAfterQueue(b.id));assertEquals(4,repo.dao.items(b.id).size)
        assertTrue(repo.dao.latestBatch()!!.reason.contains("3 輪"));assertNull(repo.dao.record("p",key("G")))
    }
    @Test fun failureKeepsSnapshotAndEndsContinuation()=runBlocking {
        val before=repo.dao.currentDraws(false)
        val b=start("A");finishRound(b);source.fail=true
        assertFalse(repo.continueAfterQueue(b.id));assertEquals(before,repo.dao.currentDraws(false))
        assertEquals("FINISHED",repo.dao.latestBatch()!!.state)
    }
    @Test fun previousUnresolvedUnselectedUrlDoesNotBecomeAnAutomaticNewSelection()=runBlocking {
        source.rows=listOf(Row("A"),Row("U","UNRESOLVED"));repo.sync()
        val b=start("A");finishRound(b)
        source.rows=listOf(Row("A"),Row("U"),Row("D"))
        assertTrue(repo.continueAfterQueue(b.id));assertEquals(listOf(key("A"),key("D")),repo.dao.items(b.id).map{it.activityKey})
    }
    @Test fun scopeAndOptOutAreRespected()=runBlocking {
        val b=start("A",city="台北",query="A");finishRound(b)
        source.rows=source.rows+Row("D",title="other")
        assertFalse(repo.continueAfterQueue(b.id))
        val second=start("B",auto=false);finishRound(second)
        assertEquals("FINISHED",repo.dao.latestBatch()!!.state)
        val fetches=source.fetches;assertFalse(repo.continueAfterQueue(second.id));assertEquals(fetches,source.fetches)
    }
    @Test fun multipleCitiesPersistAndContinuationStillRespectsSearchAndWebsiteOrder()=runBlocking {
        val first=repo.dao.currentDraws(false).first { it.sourceId=="A" }
        val b=repo.start(listOf(first.rowKey),"p",true,false,cities=setOf("台北","新北"),query="A",statuses=setOf(DrawStatus.READY,DrawStatus.NOT_STARTED))
        val saved=Continuation.decode(repo.dao.meta("continuation:${b.id}")!!.value)
        assertEquals(setOf("台北","新北"),saved.cities)
        assertEquals(setOf(DrawStatus.READY,DrawStatus.NOT_STARTED),saved.statuses)
        finishRound(b)
        source.city="新北"
        source.rows=source.rows+listOf(Row("D",title="A 新北活動"),Row("E",title="其他商品"),Row("F",title="A 第二筆"))
        assertTrue(repo.continueAfterQueue(b.id))
        assertEquals(listOf("A","D","F").map(::key),repo.dao.items(b.id).map { it.activityKey })
        finishRound(b)
        source.city="高雄";source.rows=source.rows+Row("G",title="A 未選地區")
        assertFalse(repo.continueAfterQueue(b.id))
        assertEquals(3,repo.dao.items(b.id).size)
    }
    @Test fun oldSingleCityMetadataDoesNotBroadenToAllCitiesAfterUpgrade() {
        val old=Continuation.decode("""{"enabled":true,"round":1,"city":"台北","query":"陀螺","seen":["A"]}""")
        assertEquals(setOf("台北"),old.cities)
        assertTrue(old.statuses.isEmpty())
        assertTrue(old.products.isEmpty())
        assertEquals(old,Continuation.decode(old.encode()))
        assertTrue(Continuation.decode("""{"city":"所有地區"}""").cities.isEmpty())
        assertTrue(Continuation.decode(null).cities.isEmpty())
        assertEquals(setOf("新北","桃園"),Continuation.decode("""{"cities":["新北","桃園"],"city":"所有地區"}""").cities)
    }
    @Test fun invalidMultiCityMetadataFailsInsteadOfBroadeningTheScope() {
        assertTrue(runCatching { Continuation.decode("""{"cities":"台北"}""") }.isFailure)
        assertTrue(runCatching { Continuation.decode("""{"cities":null}""") }.isFailure)
        assertTrue(runCatching { Continuation.decode("""{"statuses":["UNRECOGNIZED"]}""") }.isFailure)
        assertTrue(runCatching { Continuation.decode("""{"statuses":null}""") }.isFailure)
        assertTrue(runCatching { Continuation.decode("""{"products":"UX-03"}""") }.isFailure)
        assertTrue(runCatching { Continuation.decode("""{"products":null}""") }.isFailure)
    }
    @Test fun productScopeSurvivesRecoveryAndOnlyAppendsMatchingNewProducts()=runBlocking {
        source.rows=listOf(Row("A",title="UX-03 魔導神杖"),Row("B",title="CX-19 鱷魚裂甲"),Row("C",title="UX-03 未選舊活動"))
        repo.sync()
        val selected=repo.dao.currentDraws(false).filter { it.sourceId in setOf("A","B") }
        val b=repo.start(selected.map { it.rowKey },"p",true,false,products=setOf("UX-03","CX-19"))
        val state=Continuation.decode(repo.dao.meta("continuation:${b.id}")!!.value)
        assertEquals(setOf("UX-03","CX-19"),state.products)
        assertEquals(state,Continuation.decode(state.encode()))
        finishRound(b)
        val restored=Repository(db,ApplicationProvider.getApplicationContext<Application>(),source,access=TestAccess){true}
        restored.recover();restored.resume(false)
        source.rows=source.rows+listOf(Row("D",title="BX-00 暴風天馬"),Row("E",title="CX-19 新店"),
            Row("F",title="UX-030 相似型號"),Row("G",title="UX-03 新活動"))
        assertTrue(restored.continueAfterQueue(b.id))
        assertEquals(listOf("A","B","E","G").map(::key),repo.dao.items(b.id).map { it.activityKey })
        finishRound(b)
        source.rows=listOf(Row("H",title="BX-00 僅剩未選商品"))
        assertFalse(restored.continueAfterQueue(b.id))
        assertEquals("FINISHED",repo.dao.latestBatch()!!.state)
        assertEquals(4,repo.dao.items(b.id).size)
    }
    @Test fun batchStartRejectsRowsOutsideProductsEvenIfCallerSuppliesThem()=runBlocking {
        val draw=repo.dao.currentDraws(false).first()
        repo.dao.upsertDraws(listOf(draw.copy(product="CX-19 鱷魚裂甲")))
        assertTrue(runCatching { repo.start(listOf(draw.rowKey),"p",true,false,products=setOf("UX-03")) }.isFailure)
        assertNull(repo.dao.activeBatch())
    }
    @Test fun startingBatchRejectsHiddenOrNotYetRunnableSelections()=runBlocking {
        val draw=repo.dao.currentDraws(false).first { it.sourceId=="A" }
        assertTrue(runCatching {repo.start(listOf(draw.rowKey),"p",true,false,cities=setOf("高雄"))}.isFailure)
        assertTrue(runCatching {repo.start(listOf(draw.rowKey),"p",true,false,statuses=setOf(DrawStatus.NOT_STARTED))}.isFailure)
        repo.dao.upsertDraws(listOf(draw.copy(startsAt=System.currentTimeMillis()+86_400_000)))
        assertTrue(runCatching {repo.start(listOf(draw.rowKey),"p",true,false,statuses=setOf(DrawStatus.READY,DrawStatus.NOT_STARTED))}.isFailure)
        assertNull(repo.dao.activeBatch())
    }
    @Test fun loadFailureIsRetryableNextRunButNotAppendedAgainThisRun()=runBlocking {
        val b=start("A");repo.skipUnsent(b.id,0,"test timeout")
        assertNull(repo.dao.record("p",key("A")));assertEquals("LOAD_FAILED",repo.dao.items(b.id).single().state)
        assertFalse(repo.continueAfterQueue(b.id))
        assertNotNull(start("A",auto=false))
    }
    @Test fun syncRemovalAndExpiredRowSkipOnlyUnsentItems()=runBlocking {
        val b=start("A","B",auto=false);val item=repo.dao.items(b.id).first()
        source.rows=listOf(Row("B"),Row("C"));repo.sync()
        assertNotNull(repo.unavailableReason(b,item))
        repo.skipUnsent(b.id,0,"removed",false);assertEquals(1,repo.dao.activeBatch()!!.currentIndex)
        repo.intent(b.id,1,"SUBMIT")
        assertTrue(runCatching{repo.skipUnsent(b.id,1,"timeout")}.isFailure)
    }
    @Test fun continuationMetadataSurvivesRepositoryRecreationAndRecoveryDoesNotAutoRun()=runBlocking {
        val b=start("A");finishRound(b)
        val context=ApplicationProvider.getApplicationContext<Application>()
        val restored=Repository(db,context,source,access=TestAccess){true};restored.recover()
        assertEquals("PAUSED",restored.dao.latestBatch()!!.state)
        source.rows=source.rows+Row("D");assertFalse(restored.continueAfterQueue(b.id))
        restored.resume(false);assertTrue(restored.continueAfterQueue(b.id))
    }
    @Test fun pauseWhileSyncingDefersNewItemsUntilExplicitResume()=runBlocking {
        val b=start("A");finishRound(b);source.rows=source.rows+Row("D")
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        source.beforeFetch={entered.complete(Unit);release.await()}
        val work=async{repo.continueAfterQueue(b.id)};entered.await();repo.pause("pause");release.complete(Unit)
        assertFalse(work.await());assertEquals("PAUSED",repo.dao.latestBatch()!!.state)
        assertEquals(1,repo.dao.items(b.id).size)
        source.beforeFetch=null;repo.resume(false);assertTrue(repo.continueAfterQueue(b.id))
    }
    @Test fun friendProgressStaysWithinBatchAndSubmissionCannotBeRetried()=runBlocking {
        val b=start("A",auto=false)
        assertNotNull(repo.intent(b.id,0,"ADD_FRIEND"))
        repo.pause("offline timeout");repo.resume(false)
        assertTrue(repo.dao.item(b.id,0)!!.friendAttempted)
        assertNull(repo.intent(b.id,0,"ADD_FRIEND"))
        repo.skipUnsent(b.id,0,"loading failed")
        val retry=start("A",auto=false)
        assertFalse(repo.dao.item(retry.id,0)!!.friendAttempted)
        assertNotNull(repo.intent(retry.id,0,"SUBMIT"))
        repo.pause("process interrupted")
        assertEquals("REVIEW",repo.dao.record("p",key("A"))!!.status)
        assertTrue(runCatching{repo.resume(false)}.isFailure)
    }
    @Test fun expiredUnsentSnapshotIsSkippedAndRecordsRemainAfterArchive()=runBlocking {
        val b=start("A","B",auto=false)
        repo.finish(b.id,0,Participation.SUBMITTED,"未讀取","test")
        val draw=repo.dao.currentDraws(false).first{it.sourceId=="B"}
        repo.dao.upsertDraws(listOf(draw.copy(endsAt=System.currentTimeMillis()-1)))
        assertNotNull(repo.unavailableReason(b,repo.dao.item(b.id,1)!!))
        repo.skipUnsent(b.id,1,"expired",false)
        source.rows=listOf(Row("C"));repo.sync()
        assertEquals("SUBMITTED",repo.dao.record("p",key("A"))!!.status)
        assertNull(repo.dao.record("p",key("B")))
    }
    @Test fun vanishedButtonBeforeDispatchCanBeObservedAgainWithoutLosingFriendProgress()=runBlocking {
        val b=start("A",auto=false)
        repo.intent(b.id,0,"ADD_FRIEND")
        val before=repo.dao.item(b.id,0)!!
        val attempt=repo.intent(b.id,0,"SUBMIT")!!
        repo.cancelBeforeDispatch(attempt,before)
        val restored=repo.dao.item(b.id,0)!!
        assertFalse(restored.submitted);assertTrue(restored.friendAttempted)
        assertNull(repo.dao.record("p",key("A")))
        assertNotNull(repo.intent(b.id,0,"SUBMIT"))
    }
    @Test fun pauseWinsOverCancellationOfAnUnsentIntent()=runBlocking {
        val b=start("A",auto=false);val before=repo.dao.item(b.id,0)!!
        val attempt=repo.intent(b.id,0,"SUBMIT")!!
        repo.pause("paused")
        repo.cancelBeforeDispatch(attempt,before)
        assertEquals("PAUSED",repo.dao.latestBatch()!!.state)
        assertEquals("REVIEW",repo.dao.item(b.id,0)!!.state)
        assertEquals("REVIEW",repo.dao.record("p",key("A"))!!.status)
    }
    @Test fun dispatchedClickCannotBeWithdrawnForRetry()=runBlocking {
        val b=start("A",auto=false);val before=repo.dao.item(b.id,0)!!
        val attempt=repo.intent(b.id,0,"SUBMIT")!!
        repo.dao.attemptResult(attempt,"DISPATCHED")
        assertTrue(runCatching{repo.cancelBeforeDispatch(attempt,before)}.isFailure)
        assertTrue(repo.dao.item(b.id,0)!!.submitted)
        assertNull(repo.intent(b.id,0,"SUBMIT"))
    }
}

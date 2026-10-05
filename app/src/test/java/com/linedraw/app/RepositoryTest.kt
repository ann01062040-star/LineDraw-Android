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
class RepositoryTest {
    private lateinit var db:DrawDatabase
    private lateinit var repo:Repository
    @Before fun setup() = runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Application>()
        db=Room.inMemoryDatabaseBuilder(ctx,DrawDatabase::class.java).allowMainThreadQueries().build()
        repo=Repository(db,ctx,access=TestAccess);repo.seedDemo()
    }
    @After fun teardown() {db.close()}
    private suspend fun start(profile:String="p")=repo.start(listOf("demo:friend","demo:already"),profile,true,true)
    @Test fun missingConsentGuardDeniesEvenExistingQueue() = runBlocking {
        val b=start()
        val denied=Repository(db,ApplicationProvider.getApplicationContext<Application>())
        assertTrue(runCatching{denied.sync()}.exceptionOrNull() is com.linedraw.app.usage.AccessDenied)
        assertTrue(runCatching{denied.intent(b.id,0,"SUBMIT")}.exceptionOrNull() is com.linedraw.app.usage.AccessDenied)
        assertFalse(repo.dao.item(b.id,0)!!.submitted)
    }
    @Test fun concurrentStartHasOneWinner() = runBlocking {
        val outcomes=(1..8).map{async(Dispatchers.Default){runCatching{start()}.isSuccess}}.awaitAll()
        assertEquals(1,outcomes.count{it})
    }
    @Test fun submissionIntentDurableAndUnique() = runBlocking {
        val b=start();assertNotNull(repo.intent(b.id,0,"SUBMIT"));assertNull(repo.intent(b.id,0,"SUBMIT"))
        assertTrue(repo.dao.item(b.id,0)!!.submitted);assertEquals(1,repo.dao.attempts().size)
    }
    @Test fun recoveryDoesNotReplayInFlightSubmission() = runBlocking {
        val b=start();repo.intent(b.id,0,"SUBMIT");repo.recover()
        assertEquals("PAUSED",repo.dao.activeBatch()!!.state)
        assertEquals("REVIEW",repo.dao.record("p","demo:friend")!!.status)
        assertTrue(runCatching{repo.resume(false)}.isFailure)
        repo.resume(true);assertEquals(1,repo.dao.activeBatch()!!.currentIndex)
        assertEquals(1,repo.dao.attempts().size)
    }
    @Test fun pausePreventsNewDispatch() = runBlocking {
        val b=start();repo.pause();assertNull(repo.intent(b.id,0,"SUBMIT"))
    }
    @Test fun duplicateResultDoesNotAdvanceTwice() = runBlocking {
        val b=start();repo.finish(b.id,0,Participation.COMPLETE,"未中獎","明確结果")
        repo.finish(b.id,0,Participation.COMPLETE,"中獎","舊事件")
        assertEquals(1,repo.dao.activeBatch()!!.currentIndex)
        assertEquals("未中獎",repo.dao.record("p","demo:friend")!!.result)
    }
    @Test fun completionAndProgressSurviveDatabaseReopenSemantics() = runBlocking {
        val b=start();repo.finish(b.id,0,Participation.COMPLETE,"中獎","明確結果");repo.pause(stop=true)
        assertTrue(runCatching{repo.start(listOf("demo:friend"),"p",true,true)}.isFailure)
        assertEquals("COMPLETE",repo.dao.record("p","demo:friend")!!.status)
    }
    @Test fun profilesDoNotShareParticipation() = runBlocking {
        val b=start();repo.finish(b.id,0,Participation.COMPLETE,"中獎","明確結果");repo.pause(stop=true)
        assertNotNull(repo.start(listOf("demo:friend"),"other",true,true))
        assertNull(repo.dao.record("other","demo:friend"))
    }
    @Test fun batchSnapshotSurvivesCatalogChanges() = runBlocking {
        val b=start();val d=repo.dao.currentDraws(true).first();repo.dao.upsertDraws(listOf(d.copy(product="網站新名稱",url="linedraw-fixture://changed",archived=true)))
        assertEquals("UX-03 魔導神杖",repo.dao.item(b.id,0)!!.product)
        assertEquals("linedraw-fixture://friend",repo.dao.item(b.id,0)!!.url)
    }
    @Test fun unknownDeadlineExcludedFromBatch() = runBlocking {
        val d=repo.dao.currentDraws(true).first();repo.dao.upsertDraws(listOf(d.copy(endsAt=null)))
        assertTrue(runCatching{start()}.isFailure)
    }
    @Test fun manualMarkHasSeparateEvidenceAndCanBeUndone() = runBlocking {
        val d=repo.dao.currentDraws(true).first();repo.manual(d,"p");assertEquals("MANUAL",repo.dao.record("p",d.activityKey)!!.status)
        repo.undoManual("p",d.activityKey);assertNull(repo.dao.record("p",d.activityKey))
    }
    @Test fun manualUndoCannotDeleteConfirmedCompletion() = runBlocking {
        val b=start();repo.finish(b.id,0,Participation.COMPLETE,"中獎","明確結果")
        repo.pause(stop=true)
        assertTrue(runCatching{repo.undoManual("p","demo:friend")}.isFailure)
        assertNotNull(repo.dao.record("p","demo:friend"))
    }
    @Test fun aliasSkipCannotRestartPausedBatch() = runBlocking {
        val b=start()
        repo.dao.saveRecord(Record("p","demo:friend","商品","店家","COMPLETE",evidence="既有活動紀錄"))
        repo.pause();repo.skipKnown(b.id,0)
        assertEquals("PAUSED",repo.dao.activeBatch()!!.state)
        assertEquals(0,repo.dao.activeBatch()!!.currentIndex)
    }
    @Test fun combinedIntentPersistsBothFlagsAndCannotBeRepeated() = runBlocking {
        val b=start()
        assertNotNull(repo.intent(b.id,0,"ADD_FRIEND_AND_SUBMIT"))
        val item=repo.dao.item(b.id,0)!!
        assertTrue(item.friendAttempted);assertTrue(item.submitted);assertEquals("RESULT",item.stage)
        assertNull(repo.intent(b.id,0,"SUBMIT"));assertNull(repo.intent(b.id,0,"ADD_FRIEND"))
        assertNull(repo.intent(b.id,0,"ADD_FRIEND_AND_SUBMIT"))
        repo.recover();assertTrue(runCatching{repo.resume(false)}.isFailure)
    }
    @Test fun disconnectDoesNotOverwriteFirstPauseReason() = runBlocking {
        start();repo.pause("操作按鈕不唯一");repo.pause("無障礙服務中斷")
        assertEquals("操作按鈕不唯一",repo.dao.activeBatch()!!.reason)
        assertTrue(repo.diagnostic().contains("原因：操作按鈕不唯一"))
    }
    @Test fun submittedDrawAdvancesQueueAndCannotBeResubmitted() = runBlocking {
        val b=start();repo.intent(b.id,0,"SUBMIT")
        repo.finish(b.id,0,Participation.SUBMITTED,"未讀取","抽選已派送，繼續下一筆")
        assertEquals(1,repo.dao.activeBatch()!!.currentIndex)
        assertEquals("RUNNING",repo.dao.activeBatch()!!.state)
        assertEquals("SUBMITTED",repo.dao.record("p","demo:friend")!!.status)
        assertNull(repo.intent(b.id,0,"SUBMIT"))
        repo.finish(b.id,0,Participation.COMPLETE,"中獎","過期事件")
        assertEquals(1,repo.dao.activeBatch()!!.currentIndex)
        repo.pause(stop=true)
        assertTrue(runCatching{repo.start(listOf("demo:friend"),"p",true,true)}.isFailure)
    }

    @Test fun rejectedClickRollsBackIntentAndAllowsBoundedRetry() = runBlocking {
        val b=start();val previous=repo.dao.item(b.id,0)!!
        val id=repo.intent(b.id,0,"ADD_FRIEND_AND_SUBMIT")!!
        repo.cancelBeforeDispatch(id,previous)
        assertFalse(repo.dao.item(b.id,0)!!.submitted);assertFalse(repo.dao.item(b.id,0)!!.friendAttempted)
        assertEquals("CANCELLED_BEFORE_DISPATCH",repo.dao.attempt(id)?.disposition)
        assertNotNull(repo.intent(b.id,0,"ADD_FRIEND_AND_SUBMIT"))
    }
    @Test fun uncertainClickRecordsReviewAndContinuesWithoutReplaying() = runBlocking {
        val b=start();val id=repo.intent(b.id,0,"SUBMIT")!!
        repo.dao.attemptResult(id,"UNCERTAIN")
        repo.finish(b.id,0,Participation.REVIEW,"未確認","點擊回報不明")
        assertEquals(1,repo.dao.activeBatch()?.currentIndex)
        assertEquals("REVIEW",repo.dao.record("p","demo:friend")?.status)
        assertNull(repo.intent(b.id,0,"SUBMIT"))
        repo.pause(stop=true)
        assertTrue(runCatching { start() }.isFailure)
    }
    @Test fun friendAttemptInOldBatchDoesNotPoisonNextUserStartedBatch() = runBlocking {
        val b=start();repo.intent(b.id,0,"ADD_FRIEND");repo.pause(stop=true)
        val next=start();assertFalse(repo.dao.item(next.id,0)!!.friendAttempted)
        assertNotNull(repo.intent(next.id,0,"ADD_FRIEND"))
    }
    @Test fun confirmedFriendCanUseCombinedButtonWithoutSecondStandaloneFriendAction() = runBlocking {
        val b=start();repo.intent(b.id,0,"ADD_FRIEND")
        assertNull(repo.intent(b.id,0,"ADD_FRIEND"))
        assertNotNull(repo.intent(b.id,0,"ADD_FRIEND_AND_SUBMIT"))
        assertNull(repo.intent(b.id,0,"ADD_FRIEND_AND_SUBMIT"))
    }
    @Test fun delayedReviewCannotResumeOrAdvanceUserStoppedBatch() = runBlocking {
        val b=start();repo.intent(b.id,0,"SUBMIT");repo.pause("使用者停止",true)
        repo.finish(b.id,0,Participation.REVIEW,"未確認","晚到的回報")
        assertEquals("STOPPED",repo.dao.latestBatch()?.state);assertEquals(0,repo.dao.latestBatch()?.currentIndex)
    }

}

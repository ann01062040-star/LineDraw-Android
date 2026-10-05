package com.linedraw.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.usage.*
import com.linedraw.app.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ManualCompletionTest {
    private lateinit var db:DrawDatabase
    private lateinit var repo:Repository
    private lateinit var draw:Draw
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,DrawDatabase::class.java).allowMainThreadQueries().build()
        repo=Repository(db,context,access=TestAccess);repo.seedDemo()
        draw=repo.dao.currentDraws(true).first()
    }
    @After fun close(){db.close()}
    private fun old(status:String)=Record("p",draw.activityKey,"原商品","原店家",status,"未讀取","原始派送證據",123L)
    private suspend fun start()=repo.start(listOf(draw.rowKey),"p",true,true)

    @Test fun manualCompletionSurvivesRepositoryRecreationAndBlocksQueueUntilUndo():Unit=runBlocking {
        repo.manual(draw,"p")
        assertEquals("MANUAL",repo.dao.record("p",draw.activityKey)?.status)
        assertTrue(runCatching {start()}.isFailure)
        val restored=Repository(db,context,access=TestAccess)
        restored.undoManual("p",draw.activityKey)
        assertNull(repo.dao.record("p",draw.activityKey))
        assertNull(repo.dao.meta(ManualCompletion.key("p",draw.activityKey)))
        assertNotNull(start())
    }
    @Test fun undoRestoresSubmittedAndReviewRecordsExactlyAndStillPreventsReplay():Unit=runBlocking {
        for(status in listOf("SUBMITTED","REVIEW")) {
            val before=old(status);repo.dao.saveRecord(before)
            repo.manual(draw,"p");assertEquals("MANUAL",repo.dao.record("p",draw.activityKey)?.status)
            repo.undoManual("p",draw.activityKey)
            assertEquals(before,repo.dao.record("p",draw.activityKey))
            assertTrue(runCatching {start()}.isFailure)
        }
    }
    @Test fun confirmedAutomaticRecordsCannotBeOverwrittenOrUndone():Unit=runBlocking {
        for(status in listOf("COMPLETE","ALREADY")) {
            val before=old(status);repo.dao.saveRecord(before)
            assertTrue(runCatching {repo.manual(draw,"p")}.isFailure)
            assertTrue(runCatching {repo.undoManual("p",draw.activityKey)}.isFailure)
            assertEquals(before,repo.dao.record("p",draw.activityKey))
        }
    }
    @Test fun runningAndPausedBatchesPreventManualChanges():Unit=runBlocking {
        val other=repo.dao.currentDraws(true).last()
        repo.manual(other,"p");start()
        for(paused in listOf(false,true)) {
            if(paused) repo.pause()
            assertTrue(runCatching {repo.manual(draw,"p")}.isFailure)
            assertTrue(runCatching {repo.undoManual("p",other.activityKey)}.isFailure)
            assertEquals("MANUAL",repo.dao.record("p",other.activityKey)?.status)
        }
        repo.pause(stop=true);repo.undoManual("p",other.activityKey)
        assertNull(repo.dao.record("p",other.activityKey))
    }
    @Test fun concurrentMarksAndUndoNeverOverwriteTheSavedPreviousRecord():Unit=runBlocking {
        val before=old("REVIEW");repo.dao.saveRecord(before)
        val marks=(1..4).map {async {runCatching {repo.manual(draw,"p")}.isSuccess}}.awaitAll()
        assertEquals(1,marks.count {it})
        val undo=(1..4).map {async {runCatching {repo.undoManual("p",draw.activityKey)}.isSuccess}}.awaitAll()
        assertEquals(1,undo.count {it});assertEquals(before,repo.dao.record("p",draw.activityKey))
    }
    @Test fun profileRecordsAndUndoStayIsolated():Unit=runBlocking {
        repo.dao.saveRecord(old("SUBMITTED"));repo.manual(draw,"p");repo.manual(draw,"another")
        repo.undoManual("another",draw.activityKey)
        assertEquals("MANUAL",repo.dao.record("p",draw.activityKey)?.status)
        repo.undoManual("p",draw.activityKey);assertEquals(old("SUBMITTED"),repo.dao.record("p",draw.activityKey))
    }
    @Test fun vipIsRequiredForMarkAndUndo():Unit=runBlocking {
        val denied=Repository(db,context)
        assertTrue(runCatching {denied.manual(draw,"p")}.exceptionOrNull() is AccessDenied)
        repo.manual(draw,"p")
        assertTrue(runCatching {denied.undoManual("p",draw.activityKey)}.exceptionOrNull() is AccessDenied)
        assertEquals("MANUAL",repo.dao.record("p",draw.activityKey)?.status)
    }
    @Test fun expiredOrUnknownDatesCanBeMarkedWithoutStartingAnyDraw():Unit=runBlocking {
        repo.dao.upsertDraws(listOf(draw.copy(endsAt=null)))
        repo.manual(draw,"p");assertNull(repo.dao.activeBatch());assertTrue(repo.dao.attempts().isEmpty())
        repo.undoManual("p",draw.activityKey)
        assertTrue(runCatching {start()}.isFailure)
    }
    @Test fun changedActivityIdentityCannotMarkTheWrongCampaign():Unit=runBlocking {
        repo.dao.upsertDraws(listOf(draw.copy(activityKey="different")))
        assertTrue(runCatching {repo.manual(draw,"p")}.isFailure)
        assertNull(repo.dao.record("p",draw.activityKey));assertNull(repo.dao.record("p","different"))
    }
    @Test fun legacyManualUndoKeepsPriorSubmissionAsReview():Unit=runBlocking {
        val b=start();repo.intent(b.id,0,"SUBMIT");repo.pause(stop=true)
        repo.dao.saveRecord(old("MANUAL"))
        repo.undoManual("p",draw.activityKey)
        assertEquals("REVIEW",repo.dao.record("p",draw.activityKey)?.status)
        assertTrue(runCatching {start()}.isFailure)
    }
    @Test fun legacyManualWithoutSubmissionCanBeUndone():Unit=runBlocking {
        repo.dao.saveRecord(old("MANUAL"));repo.undoManual("p",draw.activityKey)
        assertNull(repo.dao.record("p",draw.activityKey))
    }
    @Test fun invalidBackupDoesNotEraseManualRecord():Unit=runBlocking {
        repo.manual(draw,"p")
        repo.dao.meta(Metadata(ManualCompletion.key("p",draw.activityKey),"{}"))
        assertTrue(runCatching {repo.undoManual("p",draw.activityKey)}.isFailure)
        assertEquals("MANUAL",repo.dao.record("p",draw.activityKey)?.status)
    }
}

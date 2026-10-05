package com.linedraw.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.usage.AccessDenied
import com.linedraw.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class FiveLinkAreaTest {
    private lateinit var db: DrawDatabase
    private lateinit var repo: Repository
    private lateinit var source: ContinuationTest.Source
    private val profile = TestCatalog.profile("我的紀錄")
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DrawDatabase::class.java).allowMainThreadQueries().build()
        source = ContinuationTest.Source()
        repo = Repository(db, context, source, access=TestAccess) { true }
        repo.sync()
        Unit
    }
    @After fun close() { db.close() }
    private fun keys() = TestCatalog.draws().map { it.rowKey }
    // Test participation behavior independently of the owner's historical activity windows.
    private suspend fun readyLinks() {
        repo.syncFiveLinks()
        val now=System.currentTimeMillis()
        repo.dao.upsertDraws(TestCatalog.draws().map { it.copy(startsAt=now-60_000,endsAt=now+86_400_000) })
    }
    private suspend fun testBatch() = repo.start(keys().reversed(), profile, true, false, autoContinue=true, fiveLinks=true)

    @Test fun importingAllLinksDoesNotFetchOrReplaceWebsiteData() = runBlocking {
        val website = repo.dao.currentDraws(false)
        val sync = repo.dao.meta("lastSync")
        val fetches = source.fetches
        repo.syncFiveLinks()
        assertEquals(fetches, source.fetches)
        assertEquals(website, repo.dao.currentDraws(false).filterNot(TestCatalog::isTestRow))
        assertEquals(sync, repo.dao.meta("lastSync"))
        assertNotNull(repo.dao.meta(TestCatalog.SYNC_KEY))
        assertEquals(TestCatalog.links.size, repo.dao.currentDraws(false).count(TestCatalog::isTestRow))
        assertEquals(TestCatalog.draws().map { it.endsAt }, repo.dao.currentDraws(false).filter(TestCatalog::isTestRow).map { it.endsAt })
    }
    @Test fun upgradingOriginalFivePreservesRecordsAndAddsThirtyOnlyOnce() = runBlocking {
        // Simulate the installed catalog before this update, including its former expiry.
        val oldRows = TestCatalog.draws().take(5).map { it.copy(endsAt=java.time.Instant.parse("2026-09-27T16:00:00Z").toEpochMilli()) }
        repo.dao.upsertDraws(oldRows)
        val oldRecord = Record(profile,oldRows[0].activityKey,oldRows[0].product,oldRows[0].store,"SUBMITTED",evidence="existing Android record")
        repo.dao.saveRecord(oldRecord)
        repo.syncFiveLinks()
        repo.syncFiveLinks()
        val updated = repo.dao.currentDraws(false).filter(TestCatalog::isTestRow)
        assertEquals(35,updated.size)
        assertEquals(oldRows.map { it.rowKey },updated.take(5).map { it.rowKey })
        assertEquals(oldRecord,repo.dao.record(profile,oldRows[0].activityKey))
        assertEquals(java.time.Instant.parse("2026-09-29T16:00:00Z").toEpochMilli(),updated[0].endsAt)
        updated.drop(1).forEach { assertNull(repo.dao.record(profile,it.activityKey)) }
        assertEquals(3,repo.dao.currentDraws(false).count { !TestCatalog.isTestRow(it) })
    }
    @Test fun websiteSyncPreservesTestRowsAndRecords() = runBlocking {
        repo.syncFiveLinks()
        val row = TestCatalog.draws().first()
        repo.manual(row, profile)
        val summary = repo.sync()
        assertTrue(summary.contains("封存 0"))
        assertEquals(TestCatalog.links.size, repo.dao.currentDraws(false).count(TestCatalog::isTestRow))
        repo.syncFiveLinks()
        assertEquals("MANUAL", repo.dao.record(profile, row.activityKey)?.status)
        assertNull(repo.dao.record("我的紀錄", row.activityKey))
    }
    @Test fun websiteSourceIdNamedPilotIsNotMistakenForTestData() = runBlocking {
        source.rows=source.rows+ContinuationTest.Row("pilot")
        repo.sync(); repo.syncFiveLinks()
        val website=repo.dao.currentDraws(false).filterNot(TestCatalog::isTestRow)
        assertEquals(4,website.size)
        assertTrue(website.any { it.sourceId=="pilot" })
        source.rows=source.rows.filterNot { it.id=="pilot" }
        repo.sync()
        assertEquals(3,repo.dao.currentDraws(false).count { !TestCatalog.isTestRow(it) })
        assertEquals(TestCatalog.links.size,repo.dao.currentDraws(false).count(TestCatalog::isTestRow))
    }
    @Test fun mixedListsAndWrongRecordSpaceCannotStart() = runBlocking {
        readyLinks()
        val web = repo.dao.currentDraws(false).first { !TestCatalog.isTestRow(it) }
        assertTrue(runCatching { repo.start(keys()+web.rowKey, profile, true, false, fiveLinks=true) }.isFailure)
        assertTrue(runCatching { repo.start(keys(), "我的紀錄", true, false, fiveLinks=true) }.isFailure)
        assertTrue(runCatching { repo.start(keys(), "我的紀錄", true, false) }.isFailure)
        assertTrue(runCatching { repo.start(listOf(web.rowKey), profile, true, false, fiveLinks=true) }.isFailure)
        assertTrue(runCatching { repo.manual(TestCatalog.draws().first(), "我的紀錄") }.isFailure)
        assertNull(repo.dao.activeBatch())
    }
    @Test fun allLinksKeepOriginalOrderAndStopAfterLastWithoutWebsiteSync() = runBlocking {
        readyLinks()
        val first = TestCatalog.draws().first()
        repo.dao.saveRecord(Record("我的紀錄",first.activityKey,"網站活動","店家","SUBMITTED",evidence="website record"))
        val b = testBatch()
        assertTrue(b.isFiveLinkTest())
        assertEquals(TestCatalog.links.map { it.first }, repo.dao.items(b.id).map { it.url })
        val fetches = source.fetches
        for (i in TestCatalog.links.indices) repo.finish(b.id,i,Participation.SUBMITTED,"未讀取","test only")
        assertEquals("FINISHED", repo.dao.latestBatch()?.state)
        assertFalse(repo.continueAfterQueue(b.id))
        assertEquals(fetches, source.fetches)
        assertEquals("website record", repo.dao.record("我的紀錄",first.activityKey)?.evidence)
        assertEquals("test only", repo.dao.record(profile,first.activityKey)?.evidence)
        assertTrue(runCatching { testBatch() }.isFailure)
    }
    @Test fun websiteContinuationNeverAppendsNewlyImportedTestRows() = runBlocking {
        val row = repo.dao.currentDraws(false).first()
        val b = repo.start(listOf(row.rowKey),"我的紀錄",true,false)
        repo.syncFiveLinks()
        repo.finish(b.id,0,Participation.SUBMITTED,"未讀取","test")
        source.rows = source.rows + ContinuationTest.Row("D")
        assertTrue(repo.continueAfterQueue(b.id))
        assertEquals(listOf(row.activityKey,"coupon:app:D"),repo.dao.items(b.id).map { it.activityKey })
    }
    @Test fun testFreshnessCannotMakeStaleWebsiteCatalogFresh() = runBlocking {
        val web = repo.dao.currentDraws(false).first()
        repo.dao.meta(Metadata("lastSync","0"))
        readyLinks()
        assertTrue(runCatching { repo.start(listOf(web.rowKey),"我的紀錄",true,false) }.isFailure)
        assertEquals("0",repo.dao.meta("lastSync")?.value)
        assertEquals(TestCatalog.links.size,testBatch().total)
    }
    @Test fun changedShortLinkIdentityIsRejectedInMainTestArea() = runBlocking {
        readyLinks()
        val b = testBatch()
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val changed = Repository(db,ctx,object:CatalogSource {
            override suspend fun fetch(catalog: DrawCatalog) = error("not used")
            override suspend fun resolve(url:String) = "https://liff.line.me/app/c/changed"
        },access=TestAccess) {true}
        assertTrue(runCatching { changed.resolve(b,repo.dao.items(b.id).first()) }.isFailure)
        assertEquals(TestCatalog.links.first().first,repo.dao.items(b.id).first().url)
    }
    @Test fun testAreaStillRequiresVip() = runBlocking {
        val denied = Repository(db,ApplicationProvider.getApplicationContext<Application>())
        assertTrue(runCatching { denied.syncFiveLinks() }.exceptionOrNull() is AccessDenied)
        assertEquals(0,repo.dao.currentDraws(false).count(TestCatalog::isTestRow))
    }
    @Test fun expiredTestActivityCannotStart() = runBlocking {
        repo.syncFiveLinks()
        val now=System.currentTimeMillis()
        repo.dao.upsertDraws(TestCatalog.draws().map { it.copy(startsAt=now-60_000,endsAt=now-1) })
        assertTrue(runCatching { testBatch() }.isFailure)
        assertEquals(0,repo.dao.currentDraws(false).filter(TestCatalog::isTestRow).count { it.runnable() })
    }
}

package com.linedraw.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FunboxIdentitySyncTest {
    private class Source : CatalogSource {
        var id: String? = "draw-original"
        var url = "https://lin.ee/test"
        override suspend fun fetch(catalog: DrawCatalog) = """
            <div id="page-draws"><div class="draw-store"><div class="draw-store-name">測試店</div>
            <div class="draw-start">抽選時間：2020/01/01 00:00~2099/12/31 23:59</div>
            <div class="draw-item" data-draw-href="$url" ${id?.let { "data-draw-id=\"$it\"" }.orEmpty()}>
            <div class="draw-product">商品</div></div></div></div>
        """
        override suspend fun resolve(url: String) = "https://liff.line.me/app/c/CAMPAIGN"
    }
    private lateinit var db: DrawDatabase
    private lateinit var repo: Repository
    private val source = Source()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DrawDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, context, source, access = TestAccess) { true }
    }
    @After fun close() { db.close() }
    @Test fun sourceIdRemovalAndRestorationPreserveRecordsAndPreventReplay() = runBlocking {
        repo.sync()
        val first = repo.dao.currentDraws(false).single()
        val records = listOf("SUBMITTED", "COMPLETE", "ALREADY", "MANUAL", "REVIEW").map { state ->
            Record(state, first.activityKey, first.product, first.store, state, evidence = "既有紀錄")
                .also { repo.dao.saveRecord(it) }
        }
        for (id in listOf(null, " ", "draw-restored")) {
            source.id = id
            repo.sync()
            val current = repo.dao.currentDraws(false).single()
            assertEquals(first.activityKey, current.activityKey)
            assertEquals("", repo.dao.meta("syncError")?.value)
            records.forEach { record ->
                assertEquals(record, repo.dao.record(record.profile, current.activityKey))
                assertTrue(runCatching { repo.start(listOf(current.rowKey), record.profile, true, false) }.isFailure)
            }
            assertNull(repo.dao.activeBatch())
        }
    }
    @Test fun unsafeRowRetainsPreviousCatalogAndRecords() = runBlocking {
        repo.sync()
        val before = repo.dao.currentDraws(false)
        val record = Record("p", before.single().activityKey, "商品", "測試店", "COMPLETE", evidence = "既有紀錄")
        repo.dao.saveRecord(record)
        source.id = null
        source.url = "https://evil.test/draw"
        runCatching { repo.sync() }
        assertEquals(before, repo.dao.currentDraws(false))
        assertEquals(record, repo.dao.record("p", record.activityKey))
        assertTrue(repo.dao.meta("syncError")!!.value.isNotBlank())
        assertNull(repo.dao.activeBatch())
    }
}

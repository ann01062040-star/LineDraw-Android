package com.linedraw.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.data.*
import com.linedraw.app.usage.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StandaloneRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: DrawDatabase
    private val requested = mutableListOf<DrawCatalog>()
    private val source = object : CatalogSource {
        override suspend fun fetch(catalog: DrawCatalog): String {
            requested += catalog
            return """<div id="page-draws"><div class="draw-store" data-draw-city="台北市"><div class="draw-store-name">測試店</div><div class="draw-start">抽選時間：2020/01/01 00:00~2099/12/31 23:59</div><div class="draw-item" data-draw-id="A" data-draw-href="https://liff.line.me/app/c/A"><div class="draw-product">UX-03 測試</div></div></div></div>"""
        }
        override suspend fun resolve(url: String) = url
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, DrawDatabase::class.java).allowMainThreadQueries().build()
        context.getSharedPreferences("standalone-test", 0).edit().clear().commit()
        requested.clear()
    }
    @After fun close() { db.close() }
    private fun consent() = UsageConsent(context.getSharedPreferences("standalone-test", 0))
    private fun repository() = Repository(db, context, source, ConsentAccessGuard(consent())) { true }
    @Test fun syncStartSubmitAndResumeNeedOnlyLocalConsent() = runBlocking {
        consent().accept()
        val repo = repository()
        repo.sync()
        val batch = repo.start(listOf(repo.dao.currentDraws(false).single().rowKey), "本機紀錄", true, false)
        repo.pause()
        val restored = repository()
        restored.resume(false)
        assertEquals("RUNNING", restored.dao.activeBatch()?.state)
        val permit = restored.batchPermit(batch.id)
        var dispatches = 0
        restored.access.dispatch(permit) { dispatches++ }
        assertEquals(1, dispatches)
        assertNotNull(restored.intent(batch.id, 0, "SUBMIT"))
        restored.finish(batch.id, 0, Participation.SUBMITTED, "未讀取", "fixture")
        assertEquals("SUBMITTED", restored.dao.record("本機紀錄", "coupon:app:A")?.status)
        assertEquals(listOf(DrawCatalog.FUNBOX), requested)
    }
    @Test fun catalogHasOnlyFunboxAndRejectsUnexpectedSavedSource() {
        assertEquals(listOf(DrawCatalog.FUNBOX), DrawCatalog.entries.toList())
        assertEquals("https://uxux11.github.io/funbox-line/", DrawCatalog.FUNBOX.dataUrl)
        assertTrue(runCatching { DrawCatalog.fromId("other") }.isFailure)
        assertTrue(runCatching { Continuation.decode("""{"catalog":"other"}""") }.isFailure)
    }
    @Test fun unacceptedConsentDoesNotFetchAnyCatalog() = runBlocking {
        assertTrue(runCatching { repository().sync() }.exceptionOrNull() is AccessDenied)
        assertTrue(requested.isEmpty())
    }
}

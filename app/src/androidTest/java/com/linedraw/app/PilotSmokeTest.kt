package com.linedraw.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.linedraw.app.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PilotSmokeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LineDrawApp
    private val repo get()=app.repository
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun setup() {runBlocking {
        Assume.assumeTrue(BuildConfig.FIVE_LINK_TEST)
        check(android.os.Build.HARDWARE.contains("ranchu"))
        ConsentFixtures.install(app)
        app.ready.await();withContext(Dispatchers.IO){repo.db.clearAllTables()};repo.sync();activateFixtureWindow(repo)
        app.getSharedPreferences("preferences",0).edit().clear().commit()
    }}
    // These cases exercise local queue bookkeeping only. Never operate LINE or alter production dates.
    private suspend fun activateFixtureWindow(repository:Repository) {
        val now=System.currentTimeMillis()
        repository.dao.upsertDraws(repository.dao.currentDraws(false).map { it.copy(startsAt=now-60_000,endsAt=now+600_000) })
    }
    @Test fun expiredOwnerActivitiesCannotStartBatch() = runBlocking {
        repo.sync()
        val expired=repo.dao.currentDraws(false).map { it.copy(endsAt=System.currentTimeMillis()-1) }
        repo.dao.upsertDraws(expired)
        assertTrue(runCatching { repo.start(expired.map { it.rowKey },"__instrumented_test__",true,false) }.isFailure)
        assertNull(repo.dao.activeBatch())
    }
    @After fun close() {scenario?.close()}
    @Test fun allOwnerLinksAreVisible() {
        assertEquals("com.linedraw.standalone.pilot",app.packageName)
        val rows=runBlocking{repo.dao.currentDraws(false)}
        assertEquals(TestCatalog.links.map{it.first},rows.map{it.url})
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("${TestCatalog.links.size} 個機會").assertIsDisplayed()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("draw:pilot:1"))
        compose.onNodeWithTag("draw:pilot:1").assertIsDisplayed()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("draw:pilot:${TestCatalog.links.size}"))
        compose.onNodeWithTag("draw:pilot:${TestCatalog.links.size}").assertIsDisplayed()
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithContentDescription("自動接續新增活動").assertDoesNotExist()
    }
    @Test fun sameTitleDoesNotMergeTheResults() = runBlocking {
        val rows=repo.dao.currentDraws(false)
        val b=repo.start(rows.map{it.rowKey},"__instrumented_test__",true,false)
        assertEquals(TestCatalog.links.size,b.total)
        repo.finish(b.id,0,Participation.COMPLETE,"未提供","資料庫測試，未操作 LINE")
        assertNotNull(repo.dao.record(b.profile,rows[0].activityKey))
        rows.drop(1).forEach{assertNull(repo.dao.record(b.profile,it.activityKey))}
        assertEquals(1,repo.dao.activeBatch()!!.currentIndex)
        repo.pause("測試結束",true)
    }
    @Test fun manualCompletionCanBeUndoneInPilot():Unit=runBlocking {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("manual:pilot:1"))
        compose.onNodeWithTag("manual:pilot:1").performClick()
        compose.onNodeWithTag("confirmManual").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("undo:pilot:1").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("MANUAL",repo.dao.record("我的紀錄",TestCatalog.draws().first().activityKey)?.status)
        compose.onNodeWithTag("undo:pilot:1").performClick()
        compose.onNodeWithTag("confirmUndoManual").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("manual:pilot:1").fetchSemanticsNodes().isNotEmpty()}
        assertNull(repo.dao.record("我的紀錄",TestCatalog.draws().first().activityKey))
    }
    @Test fun websiteRowsAndSimulationAreRejected() = runBlocking {
        val extra=TestCatalog.draws().first().copy(rowKey="outside",url="https://lin.ee/outside")
        repo.dao.upsertDraws(listOf(extra))
        assertTrue(runCatching{repo.start(listOf(extra.rowKey),"__instrumented_test__",true,false)}.isFailure)
        assertTrue(runCatching{repo.seedDemo()}.isFailure)
        repo.sync()
        assertEquals(TestCatalog.links.size,repo.dao.currentDraws(false).size)
    }
    @Test fun testLinksNeverFetchWebsiteOrAppendAndResyncKeepsRecords() = runBlocking {
        var fetches=0
        val local=Repository(repo.db,app,object:CatalogSource {
            override suspend fun fetch(catalog: DrawCatalog):String {fetches++;error("pilot must not fetch website")}
            override suspend fun resolve(url:String)=url
        },access=repo.access) {true}
        local.sync();activateFixtureWindow(local)
        val rows=local.dao.currentDraws(false)
        val b=local.start(rows.map{it.rowKey},"__instrumented_test__",true,false,autoContinue=true)
        assertEquals(TestCatalog.links.map{it.first},local.dao.items(b.id).map{it.url})
        for(position in rows.indices) local.finish(b.id,position,Participation.SUBMITTED,"未讀取","database test only")
        assertEquals("FINISHED",local.dao.latestBatch()!!.state)
        assertFalse(local.continueAfterQueue(b.id))
        local.sync()
        assertEquals(0,fetches)
        assertEquals(TestCatalog.links.size,local.dao.items(b.id).size)
        rows.forEach{assertEquals("SUBMITTED",local.dao.record(b.profile,it.activityKey)?.status)}
    }
}

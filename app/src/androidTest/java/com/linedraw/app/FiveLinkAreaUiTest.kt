package com.linedraw.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linedraw.app.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FiveLinkAreaUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LineDrawApp
    private var scenario: ActivityScenario<MainActivity>? = null
    private val testProfile = TestCatalog.profile("我的紀錄")
    @Before fun setup() = runBlocking {
        check(android.os.Build.HARDWARE.contains("ranchu"))
        Assume.assumeFalse(BuildConfig.FIVE_LINK_TEST)
        app.ready.await()
        ConsentFixtures.install(app)
        withContext(Dispatchers.IO) { app.repository.db.clearAllTables() }
        app.getSharedPreferences("preferences",0).edit().clear().putString("appearance","淺色").commit()
        val now = System.currentTimeMillis()
        app.repository.dao.upsertDraws(listOf(Draw("website-row","website-1","coupon:web:1","網站店家","台北","網站活動",
            "https://liff.line.me/web/c/1","網站抽選",now-3_600_000,now+86_400_000,0)))
        app.repository.dao.meta(Metadata("lastSync",now.toString()))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }
    @After fun close() {
        scenario?.close()
        app.getSharedPreferences("preferences",0).edit().putBoolean("fiveLinkArea",false).commit()
        runBlocking { app.repository.pause("test cleanup",true) }
    }
    private fun enter() {
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("enterFiveLinkArea"))
        compose.onNodeWithTag("enterFiveLinkArea").performClick()
        compose.onNodeWithTag("exitFiveLinkArea").assertIsDisplayed()
    }
    private suspend fun activateCurrentBatchFixture() {
        val now=System.currentTimeMillis()
        app.repository.dao.upsertDraws(TestCatalog.draws().mapIndexed { index, row ->
            // Stay clear of Compose's periodically sampled clock; a 1 ms expiry can look
            // briefly active until the next UI tick, despite already being expired in Room.
            row.copy(startsAt=now-7_200_000,endsAt=if(index<15) now-3_600_000 else now+600_000)
        })
    }
    private fun capture(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"main-test-area-$name.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LineDraw-validation")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        app.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0)
        app.contentResolver.update(uri,values,null,null)
    }
    @Test fun mainDefaultsToWebsiteAndTestAreaShowsOnlyOwnerLinks(): Unit = runBlocking {
        assertEquals("com.linedraw.standalone",app.packageName)
        compose.onNodeWithText("1 個機會").assertIsDisplayed()
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("enterFiveLinkArea"))
        capture("entry")
        compose.onNodeWithTag("enterFiveLinkArea").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("35 個機會"))
        compose.onNodeWithText("35 個機會").assertIsDisplayed()
        assertEquals(TestCatalog.links.map { it.first }, app.repository.dao.currentDraws(false).filter(TestCatalog::isTestRow).map { it.url })
        activateCurrentBatchFixture()
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("全選可抽選"))
        compose.onNodeWithText("全選可抽選").performClick()
        compose.onNodeWithText("已選 20 筆").assertIsDisplayed()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("draw:pilot:1"))
        capture("links")
        compose.onNodeWithTag("draw:website-1").assertDoesNotExist()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("draw:pilot:35"))
        compose.onNodeWithTag("draw:pilot:35").assertIsDisplayed()
        compose.onNodeWithTag("startBatch").performClick()
        compose.onNodeWithText("本次 20 筆，按清單順序逐筆處理。").assertIsDisplayed()
        compose.onNodeWithText("依選取活動的清單順序自動執行，完成後結束；不核對店家或活動名稱。").assertIsDisplayed()
        compose.onNodeWithText("返回",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("exitFiveLinkArea"))
        compose.onNodeWithTag("exitFiveLinkArea").performClick()
        compose.onNodeWithText("1 個機會").assertIsDisplayed()
        compose.onNodeWithTag("startBatch").assertDoesNotExist()
        assertEquals(1,app.repository.dao.currentDraws(false).count { !TestCatalog.isTestRow(it) })
    }
    @Test fun testRecordsAndWebsiteRecordsStayInTheirOwnArea(): Unit = runBlocking {
        app.repository.dao.saveRecord(Record("我的紀錄","coupon:web:1","網站紀錄商品","網站店家","SUBMITTED",evidence="web only"))
        app.repository.dao.saveRecord(Record(testProfile,TestCatalog.draws().first().activityKey,"測試紀錄商品","測試店家","SUBMITTED",evidence="test only"))
        enter()
        compose.onNodeWithContentDescription("紀錄 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("測試紀錄商品"))
        compose.onNodeWithText("測試紀錄商品").assertIsDisplayed()
        compose.onNodeWithText("網站紀錄商品").assertDoesNotExist()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("exitFiveLinkArea"))
        compose.onNodeWithTag("exitFiveLinkArea").performClick()
        compose.onNodeWithContentDescription("紀錄 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("網站紀錄商品"))
        compose.onNodeWithText("網站紀錄商品").assertIsDisplayed()
        compose.onNodeWithText("測試紀錄商品").assertDoesNotExist()
    }
    @Test fun activeTestBatchKeepsItsAreaAfterActivityRecreationAndLocksExit(): Unit = runBlocking {
        enter()
        activateCurrentBatchFixture()
        val batch=app.repository.start(TestCatalog.draws().drop(15).map { it.rowKey },testProfile,true,false,fiveLinks=true)
        assertTrue(batch.isFiveLinkTest())
        scenario!!.recreate()
        compose.onNodeWithTag("exitFiveLinkArea").assertIsNotEnabled()
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithContentDescription("自動接續新增活動").assertDoesNotExist()
        app.repository.pause("test stopped",true)
        // Room commits before its Flow reaches Compose; wait for the displayed state, not just the write.
        compose.waitUntil(timeoutMillis=10_000) {
            compose.onAllNodes(hasTestTag("exitFiveLinkArea") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("exitFiveLinkArea").assertIsEnabled().performClick()
        compose.onNodeWithText("1 個機會").assertIsDisplayed()
    }
}

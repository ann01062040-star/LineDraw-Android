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
class ManualCompletionUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LineDrawApp
    private var scenario:ActivityScenario<MainActivity>?=null
    private val profile="我的紀錄"
    private fun key(id:String)="coupon:manual:$id"
    @Before fun setup():Unit=runBlocking {
        check(android.os.Build.HARDWARE.contains("ranchu"));Assume.assumeFalse(BuildConfig.FIVE_LINK_TEST)
        app.ready.await();ConsentFixtures.install(app)
        withContext(Dispatchers.IO) {app.repository.db.clearAllTables()}
        app.getSharedPreferences("preferences",0).edit().clear().putString("appearance","淺色").commit()
        val now=System.currentTimeMillis()
        app.repository.dao.upsertDraws(listOf("A","B","C").mapIndexed { i,id ->
            Draw(id,id,key(id),"測試店家","台北","商品 $id","https://liff.line.me/manual/c/$id","有效測試期間",now-3_600_000,now+86_400_000,i)
        })
        app.repository.dao.saveRecord(Record(profile,key("B"),"商品 B","測試店家","SUBMITTED","未讀取","原始送出證據",123L))
        app.repository.dao.saveRecord(Record(profile,key("C"),"商品 C","測試店家","REVIEW","未提供","原始待確認證據",124L))
        app.repository.dao.meta(Metadata("lastSync",now.toString()))
        scenario=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close(){scenario?.close()}
    private fun node(tag:String):SemanticsNodeInteraction {
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun waitNode(tag:String) {compose.waitUntil(10_000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun capture() {
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"linedraw-manual-complete-0.2.4.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LineDraw-validation")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        app.contentResolver.openOutputStream(uri)!!.use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);app.contentResolver.update(uri,values,null,null)
    }
    @Test fun listMarkPersistsExcludesDrawAndCanBeUndoneFromRecords():Unit=runBlocking {
        node("manual:A").performClick()
        compose.onNodeWithText("標記為已完成？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertNull(app.repository.dao.record(profile,key("A")))
        node("manual:A").performClick();compose.onNodeWithTag("confirmManual").performClick()
        waitNode("undo:A");node("undo:A").assertIsDisplayed()
        compose.onNodeWithContentDescription("選取 測試店家 商品 A").assertIsNotEnabled()
        assertTrue(runCatching {app.repository.start(listOf("A"),profile,true,false)}.isFailure)
        capture()
        scenario!!.recreate();node("undo:A").assertIsDisplayed()
        compose.onNodeWithContentDescription("紀錄 分頁").performClick()
        node("undoRecord:${key("A")}").performClick()
        compose.onNodeWithTag("confirmUndoManual").performClick()
        compose.onNodeWithContentDescription("抽選 分頁").performClick()
        node("manual:A").assertIsDisplayed()
        assertNull(app.repository.dao.record(profile,key("A")))
        assertNull(app.repository.dao.activeBatch())
    }
    @Test fun detailsAndRecordActionsRestoreOriginalSentAndReviewEvidence():Unit=runBlocking {
        val beforeSent=app.repository.dao.record(profile,key("B"))
        val beforeReview=app.repository.dao.record(profile,key("C"))
        node("draw:B").performClick()
        compose.onNodeWithTag("detailManual").performScrollTo().performClick()
        compose.onNodeWithTag("confirmManual").performClick()
        waitNode("undo:B")
        compose.onNodeWithContentDescription("紀錄 分頁").performClick()
        node("manualRecord:${key("C")}").performClick();compose.onNodeWithTag("confirmManual").performClick()
        waitNode("undoRecord:${key("C")}")
        node("undoRecord:${key("C")}").performClick();compose.onNodeWithTag("confirmUndoManual").performClick()
        waitNode("manualRecord:${key("C")}")
        assertEquals(beforeReview,app.repository.dao.record(profile,key("C")))
        compose.onNodeWithContentDescription("抽選 分頁").performClick()
        node("undo:B").performClick();compose.onNodeWithTag("confirmUndoManual").performClick()
        waitNode("manual:B");assertEquals(beforeSent,app.repository.dao.record(profile,key("B")))
        assertTrue(runCatching {app.repository.start(listOf("B"),profile,true,false)}.isFailure)
    }
    @Test fun fiveLinkAreaManualCompletionStaysInItsOwnRecordProfile():Unit=runBlocking {
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        node("enterFiveLinkArea").performClick()
        node("manual:pilot:1").performClick();compose.onNodeWithTag("confirmManual").performClick()
        waitNode("undo:pilot:1")
        val activity=TestCatalog.draws().first().activityKey
        assertEquals("MANUAL",app.repository.dao.record(TestCatalog.profile(profile),activity)?.status)
        assertNull(app.repository.dao.record(profile,activity))
        node("exitFiveLinkArea").performClick()
        node("manual:A").assertIsDisplayed()
        assertNull(app.repository.dao.record(profile,key("A")))
    }
}

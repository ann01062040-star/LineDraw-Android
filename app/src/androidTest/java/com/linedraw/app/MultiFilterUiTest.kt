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
class MultiFilterUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LineDrawApp
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun setup(): Unit = runBlocking {
        check(android.os.Build.HARDWARE.contains("ranchu")) { "Only run on the dedicated test AVD" }
        Assume.assumeFalse(BuildConfig.FIVE_LINK_TEST)
        app.ready.await();ConsentFixtures.install(app)
        withContext(Dispatchers.IO) {app.repository.db.clearAllTables()}
        app.getSharedPreferences("preferences",0).edit().clear().putString("appearance","淺色").commit()
        val now=System.currentTimeMillis()
        fun row(id:String,city:String)=Draw(id,id,"coupon:filter:$id","$city 店家",city,"商品 $id","https://liff.line.me/filter/c/$id","測試期間",now-3_600_000,now+86_400_000,0)
        val rows=listOf(row("A","台北"),row("B","新北").copy(startsAt=now+3_600_000),row("C","高雄"),
            row("D","台北"),row("E","新北").copy(startsAt=null,endsAt=null),row("F","台北").copy(archived=true),row("G","新北"))
        app.repository.dao.upsertDraws(rows.mapIndexed { i,d -> d.copy(ordinal=i) })
        app.repository.dao.saveRecord(Record("我的紀錄","coupon:filter:D","商品 D","台北 店家","SUBMITTED",evidence="fixture only"))
        app.repository.dao.meta(Metadata("lastSync",now.toString()))
        scenario=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close(){scenario?.close()}
    private fun chip(tag:String):SemanticsNodeInteraction {
        compose.onNodeWithTag("mainList").performScrollToIndex(0)
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }
    private fun count(n:Int) {
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("抽選清單 · $n"))
        compose.onNodeWithText("抽選清單 · $n").assertIsDisplayed()
    }
    private fun selectReady() {
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("全選可抽選"))
        compose.onNodeWithText("全選可抽選").performClick()
    }
    private fun capture() {
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"linedraw-multiselect-0.2.2.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LineDraw-validation")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        app.contentResolver.openOutputStream(uri)!!.use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);app.contentResolver.update(uri,values,null,null)
    }
    @Test fun multiCityAndStatusSurviveRecreationAndOnlyReadyRowsCanBeSelected():Unit = runBlocking {
        count(6)
        chip("status:READY").performClick();count(3)
        chip("city:台北").performClick();count(1)
        chip("city:新北").performClick();count(2)
        chip("status:NOT_STARTED").performClick();count(3)
        scenario!!.recreate()
        chip("status:READY").assertIsSelected()
        chip("status:NOT_STARTED").assertIsSelected()
        chip("city:台北").assertIsSelected()
        chip("city:新北").assertIsSelected();count(3)
        capture()
        compose.onNodeWithTag("mainList").performScrollToNode(hasContentDescription("選取 新北 店家 商品 B"))
        compose.onNodeWithContentDescription("選取 新北 店家 商品 B").assertIsNotEnabled()
        selectReady()
        compose.onNodeWithTag("startBatch").performClick()
        compose.onNodeWithText("本次 2 筆，按清單順序逐筆處理。").assertIsDisplayed()
        compose.onNodeWithText("返回",useUnmergedTree=true).performClick()
        chip("city:all").performClick();count(4)
        compose.onNodeWithTag("startBatch").assertDoesNotExist()
        assertNull(app.repository.dao.activeBatch())
    }
    @Test fun archivedUnionResetAndChangedFiltersCannotLeaveHiddenRowsSelected():Unit = runBlocking {
        chip("status:RECORDED").performClick();count(1)
        chip("status:ARCHIVED").performClick();count(2)
        chip("status:all").performClick().assertIsSelected();count(6)
        selectReady();compose.onNodeWithTag("startBatch").assertIsDisplayed()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("search"))
        compose.onNodeWithTag("search").performTextInput("新北")
        count(3);compose.onNodeWithTag("startBatch").assertDoesNotExist()
        chip("status:NOT_STARTED").performClick();count(1)
        compose.onNodeWithText("全選可抽選").assertIsNotEnabled()
        chip("status:NOT_STARTED").performClick()
        chip("status:all").assertIsSelected();count(3)
        assertNull(app.repository.dao.activeBatch())
    }
}

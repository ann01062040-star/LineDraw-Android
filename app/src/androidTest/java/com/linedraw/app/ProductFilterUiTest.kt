package com.linedraw.app

import android.graphics.Bitmap
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProductFilterUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LineDrawApp
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun setup(): Unit = runBlocking {
        check(android.os.Build.HARDWARE.contains("ranchu"))
        Assume.assumeFalse(BuildConfig.FIVE_LINK_TEST)
        app.ready.await(); ConsentFixtures.install(app)
        withContext(Dispatchers.IO) { app.repository.db.clearAllTables() }
        app.getSharedPreferences("preferences",0).edit().clear().putString("appearance","淺色").commit()
        val now = System.currentTimeMillis()
        fun row(id: String, product: String, city: String="台北") = Draw(id,id,"coupon:products:$id","$city 店家",city,product,
            "https://liff.line.me/products/c/$id","測試期間",now-3_600_000,now+86_400_000,0)
        val rows = listOf(row("A","UX-03 魔導神杖"),row("B","UX-03 神杖","新北").copy(startsAt=now+3_600_000),
            row("C","CX-19 鱷魚裂甲"),row("D","UX-03 魔導神杖","高雄"),row("E","CX-19 鱷魚裂甲"),
            row("F","BX-00 暴風天馬"),row("G","BGX-01 復刻"),row("H","一般測試商品"))
        app.repository.dao.upsertDraws(rows.mapIndexed { i,d -> d.copy(ordinal=i) })
        app.repository.dao.saveRecord(Record("我的紀錄","coupon:products:E","CX-19 鱷魚裂甲","台北 店家","SUBMITTED",evidence="fixture"))
        app.repository.dao.meta(Metadata("lastSync",now.toString()))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() { scenario?.close() }
    private fun chip(tag: String): SemanticsNodeInteraction {
        // A nested horizontal chip row can remain composed above the clipped viewport.
        // Start from the top so the test actually scrolls the target back into view.
        compose.onNodeWithTag("mainList").performScrollToIndex(0)
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }
    private fun product(key: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("productOptions").performScrollToNode(hasTestTag("product:$key"))
        return compose.onNodeWithTag("product:$key")
    }
    private fun count(expected: Int) {
        try { compose.onNodeWithTag("mainList").performScrollToNode(hasText("抽選清單 · $expected")) }
        catch (error: AssertionError) {
            capture("failure-count-$expected")
            println(compose.onNodeWithTag("mainList").printToString())
            throw error
        }
        compose.onNodeWithText("抽選清單 · $expected").assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        File(app.filesDir,"product-filter").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun multipleProductsCombineWithOtherFiltersPersistAndClearHiddenSelections(): Unit = runBlocking {
        chip("chooseProducts").performClick()
        product("UX-03").performClick().assertIsOn()
        product("CX-19").performClick().assertIsOn()
        capture("picker-light")
        compose.onNodeWithTag("productDone").performClick(); count(5)
        chip("city:台北").performClick(); count(3)
        chip("status:READY").performClick(); count(2)
        chip("status:NOT_STARTED").performClick().assertIsSelected()
        chip("city:新北").performClick(); count(3)
        scenario!!.recreate()
        chip("chooseProducts").performClick()
        product("UX-03").assertIsOn(); product("CX-19").assertIsOn()
        compose.onNodeWithTag("productDone").performClick(); count(3)
        compose.onNodeWithText("全選可抽選").performClick()
        compose.onNodeWithTag("startBatch").performClick()
        compose.onNodeWithText("本次 2 筆，按清單順序逐筆處理。").assertIsDisplayed()
        compose.onNodeWithText("商品篩選：CX-19、UX-03").assertIsDisplayed()
        compose.onNodeWithText("返回",useUnmergedTree=true).performClick()
        chip("chooseProducts").performClick()
        compose.onNodeWithTag("productReset").performClick()
        compose.onNodeWithTag("productSearch").performTextInput("暴風")
        product("BX-00").performClick()
        compose.onNodeWithTag("productDone").performClick(); count(1)
        compose.onNodeWithTag("startBatch").assertDoesNotExist()
        scenario!!.close()
        app.getSharedPreferences("preferences",0).edit().putString("appearance","深色").commit()
        scenario=ActivityScenario.launch(MainActivity::class.java)
        count(1); chip("chooseProducts").performClick()
        product("BX-00").assertIsOn(); capture("picker-dark")
        compose.onNodeWithTag("productDone").performClick()
        chip("product:all").performClick(); count(8)
        assertNull(app.repository.dao.activeBatch())
    }
    @Test fun vanishedProductsDoNotResetScopeAndTestAreaKeepsSeparatePreference(): Unit = runBlocking {
        chip("chooseProducts").performClick(); product("UX-03").performClick()
        compose.onNodeWithTag("productDone").performClick(); count(3)
        val rows=app.repository.dao.currentDraws(false).filter { ProductCatalog.key(it.product)=="UX-03" }
        app.repository.dao.upsertDraws(rows.map { it.copy(archived=true) })
        compose.waitUntil(5000) { compose.onAllNodesWithText("沒有符合條件的項目").fetchSemanticsNodes().isNotEmpty() }
        count(0); chip("product:all").assertIsNotSelected()
        chip("chooseProducts").performClick(); product("UX-03").assertIsOn()
        compose.onNodeWithText("符合目前條件 0 筆").assertExists()
        compose.onNodeWithTag("productDone").performClick()
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        chip("enterFiveLinkArea").performClick()
        chip("product:all").assertIsSelected(); count(TestCatalog.links.size)
        chip("chooseProducts").performClick()
        product(ProductCatalog.key(TestCatalog.draws().first().product)).performClick()
        compose.onNodeWithTag("productDone").performClick()
        chip("exitFiveLinkArea").performClick(); count(0)
        chip("chooseProducts").performClick(); product("UX-03").assertIsOn()
        compose.onNodeWithTag("productDone").performClick()
        assertNull(app.repository.dao.activeBatch())
    }
}

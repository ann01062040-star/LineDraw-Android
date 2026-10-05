package com.linedraw.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZVisualEvidenceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as LineDrawApp
    private var scenario:ActivityScenario<MainActivity>?=null
    private fun launch(dark:Boolean=false) {
        scenario?.close()
        app.getSharedPreferences("preferences",0).edit().putBoolean("demo",false).putString("appearance",if(dark)"深色" else "淺色").putBoolean("opaque",false).commit()
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val roots=compose.onAllNodes(isRoot())
        val bitmap=roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap()
        val values=ContentValues().apply {put(MediaStore.Images.Media.DISPLAY_NAME,"final-$name");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LineDraw-validation");put(MediaStore.Images.Media.IS_PENDING,1)}
        val resolver=app.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        resolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);resolver.update(uri,values,null,null)
    }
    @After fun close() {scenario?.close()}
    @Test fun liveSyncLightDarkAndLargeText() = runBlocking {
        ConsentFixtures.install(app)
        app.ready.await()
        var synced=false
        var lastFailure:Exception?=null
        repeat(3) {
            if(!synced) try {app.repository.sync();synced=true} catch(e:Exception) {lastFailure=e;delay(1000)}
        }
        if(!synced) throw lastFailure!!
        val draws=app.repository.dao.currentDraws(false)
        assertTrue(draws.isNotEmpty())
        assertTrue(draws.all{!it.demo})
        assertNotNull(app.repository.dao.meta("sourceHash"))
        launch()
        compose.onNodeWithText("把時間，留給喜歡的事。").assertIsDisplayed()
        screenshot("01-live-light.png")
        compose.onNodeWithTag("mainList").performScrollToNode(hasText(draws.first().product))
        compose.onNodeWithText(draws.first().product).assertIsDisplayed()
        screenshot("02-live-list.png")
        compose.onNodeWithText(draws.first().product).performClick()
        compose.onNodeWithText("在 LINE 開啟").assertIsDisplayed()
        screenshot("03-detail.png")
        compose.onNodeWithText("關閉").performClick()
        launch(true)
        screenshot("04-live-dark.png")
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("深色"))
        screenshot("05-settings-dark.png")
        check(android.os.Build.HARDWARE.contains("ranchu"))
        val automation=instrumentation.uiAutomation
        fun shell(s:String){ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(s)).use{it.readBytes()}}
        try {
            shell("settings put system font_scale 2.0")
            launch()
            compose.onNodeWithContentDescription("設定 分頁").assertIsDisplayed().performClick()
            compose.onNodeWithTag("mainList").performScrollToNode(hasContentDescription("減少透明效果"))
            compose.onNodeWithContentDescription("減少透明效果").assertIsDisplayed()
            screenshot("06-font-scale-200.png")
        } finally {shell("settings put system font_scale 1.0")}
    }
}

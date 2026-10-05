package com.linedraw.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var scenario:ActivityScenario<MainActivity>
    @Before fun setup() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        kotlinx.coroutines.runBlocking { ConsentFixtures.install(context.applicationContext as LineDrawApp) }
        context.getSharedPreferences("preferences",0).edit().putBoolean("demo",true).putBoolean("autoContinue",true).putString("appearance","淺色").putBoolean("opaque",false).putBoolean("motion",false).commit()
        scenario=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() {scenario.close()}
    @Test fun drawSearchAndTabs() {
        compose.onNodeWithText("把時間，留給喜歡的事。").assertIsDisplayed()
        compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("search"))
        compose.onNodeWithTag("search").performTextInput("不存在的商品")
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("沒有符合條件的項目"))
        compose.onNodeWithText("沒有符合條件的項目").assertIsDisplayed()
        compose.onNodeWithContentDescription("紀錄 分頁").performClick()
        compose.onNodeWithText("每次抽選，都有跡可循。").assertIsDisplayed()
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithText("依你的方式。").assertIsDisplayed()
    }
    @Test fun themeAndAccessibilityPreferences() {
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasText("深色"))
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasContentDescription("減少透明效果"))
        compose.onNodeWithContentDescription("減少透明效果").performClick()
        compose.onNodeWithContentDescription("減少透明效果").assertIsOn()
        compose.onNodeWithContentDescription("抽選 分頁").performClick()
        compose.onNodeWithText("把時間，留給喜歡的事。").assertIsDisplayed()
    }
    @Test fun automaticContinuationCanBeDisabled() {
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        compose.onNodeWithTag("mainList").performScrollToNode(hasContentDescription("自動接續新增活動"))
        compose.onNodeWithContentDescription("自動接續新增活動").assertIsOn().performClick().assertIsOff()
        org.junit.Assert.assertFalse(InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("preferences",0).getBoolean("autoContinue",true))
    }
}

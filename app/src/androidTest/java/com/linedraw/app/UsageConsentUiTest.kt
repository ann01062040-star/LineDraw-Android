package com.linedraw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linedraw.app.usage.AccessDenied
import com.linedraw.app.data.Record
import com.linedraw.app.usage.UsageDeclaration
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageConsentUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LineDrawApp
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun setup() = runBlocking {
        check(android.os.Build.HARDWARE.contains("ranchu"))
        app.ready.await()
        withContext(Dispatchers.IO) { app.repository.db.clearAllTables() }
        app.getSharedPreferences("preferences", 0).edit().putBoolean("demo", !BuildConfig.FIVE_LINK_TEST)
            .putString("appearance", "淺色").commit()
        Unit
    }
    @After fun close() { scenario?.close() }
    @Test fun firstOpenShowsFullDeclarationAndAgreementSurvivesReopen() = runBlocking {
        ConsentFixtures.install(app, usageAccepted = false)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("usageDeclaration").assertIsDisplayed()
        compose.onNodeWithTag("websiteLogin").assertDoesNotExist()
        for (section in UsageDeclaration.sections) compose.onNodeWithText(section.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("consentGuide").performScrollTo().performClick()
        compose.onNodeWithTag("legalDocument").assertIsDisplayed()
        compose.onNode(hasText("LineDraw 獨立版抽選輔助", substring = true) and hasAnyAncestor(hasTestTag("legalDocument"))).assertExists()
        assertFalse(app.usageConsent.accepted.value)
        compose.onNodeWithTag("closeLegalDocument").performClick()
        compose.onNodeWithTag("consentLicense").performScrollTo().performClick()
        compose.onNodeWithTag("legalDocument").assertIsDisplayed()
        compose.onNode(hasText("PolyForm Noncommercial", substring = true) and hasAnyAncestor(hasTestTag("legalDocument"))).assertExists()
        assertFalse(app.usageConsent.accepted.value)
        compose.onNodeWithTag("closeLegalDocument").performClick()
        compose.onNodeWithTag("consentPrivacy").performScrollTo().performClick()
        compose.onNodeWithTag("legalDocument").assertIsDisplayed()
        compose.onNode(hasText("本機資料", substring = true) and hasAnyAncestor(hasTestTag("legalDocument"))).assertExists()
        compose.onNodeWithTag("closeLegalDocument").performClick()
        compose.onNodeWithTag("acceptUsage").assertTextEquals("我已了解，開始使用").performClick()
        compose.onNodeWithTag("mainList").assertIsDisplayed()
        compose.onNodeWithTag("websiteLogin").assertDoesNotExist()
        assertTrue(app.usageConsent.accepted.value)
        scenario!!.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("usageDeclaration").assertDoesNotExist()
        compose.onNodeWithTag("mainList").assertIsDisplayed()
        compose.onNodeWithTag("websiteLogin").assertDoesNotExist()
        Unit
    }
    @Test fun decliningExitsWithoutDeletingDrawRecords() = runBlocking {
        ConsentFixtures.install(app, usageAccepted = false)
        app.repository.dao.saveRecord(Record("saved-profile", "saved-key", "既有活動", "店家", "SUBMITTED", evidence = "fixture"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("declineUsage").performClick()
        compose.waitUntil(5000) { scenario!!.state == Lifecycle.State.DESTROYED }
        assertFalse(app.usageConsent.accepted.value)
        assertEquals("SUBMITTED", app.repository.dao.record("saved-profile", "saved-key")?.status)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("usageDeclaration").assertIsDisplayed()
        Unit
    }
    @Test fun withdrawnConsentBlocksDispatchBeforeAgreement() = runBlocking {
        ConsentFixtures.install(app)
        val permit = app.access.current()!!
        app.usageConsent.decline()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("usageDeclaration").assertIsDisplayed()
        compose.onNodeWithTag("mainList").assertDoesNotExist()
        assertNull(app.access.current())
        assertTrue(runCatching { app.repository.sync() }.exceptionOrNull() is AccessDenied)
        assertTrue(runCatching { app.repository.start(listOf("fake"), "p", true, false) }.exceptionOrNull() is AccessDenied)
        var dispatched = false
        assertTrue(runCatching { app.access.dispatch(permit) { dispatched = true } }.exceptionOrNull() is AccessDenied)
        assertFalse(dispatched)
        compose.onNodeWithTag("acceptUsage").performClick()
        compose.onNodeWithTag("mainList").assertIsDisplayed()
        assertNotNull(app.access.current())
    }
    @Test fun settingsDocumentsCanBeReadWithoutChangingAgreement() = runBlocking {
        ConsentFixtures.install(app)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("設定 分頁").performClick()
        for (name in listOf("GUIDE", "USAGE", "PRIVACY", "LICENSE")) {
            compose.onNodeWithTag("mainList").performScrollToNode(hasTestTag("legal:$name"))
            compose.onNodeWithTag("legal:$name").performClick()
            compose.onNodeWithTag("legalDocument").assertIsDisplayed()
            compose.onNodeWithTag("closeLegalDocument").performClick()
            assertTrue(app.usageConsent.accepted.value)
        }
    }
}

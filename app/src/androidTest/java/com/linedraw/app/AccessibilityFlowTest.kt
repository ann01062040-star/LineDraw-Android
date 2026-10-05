package com.linedraw.app

import android.app.UiAutomation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.linedraw.app.data.*
import com.linedraw.app.engine.DrawAccessibilityService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Host installs :fixture and enables service ONLY on a dedicated LineDraw AVD. */
@RunWith(AndroidJUnit4::class)
class AccessibilityFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as LineDrawApp
    private val repo get()=app.repository
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun setup() = runBlocking {
        check(android.os.Build.HARDWARE.contains("ranchu")) { "此測試只能在專用模擬器執行" }
        ConsentFixtures.install(app)
        app.ready.await()
        withContext(Dispatchers.IO) {repo.db.clearAllTables()}
        repo.seedDemo()
        app.getSharedPreferences("preferences",0).edit().putBoolean("demo",true).commit()
        scenario=ActivityScenario.launch(MainActivity::class.java)
        val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        // Instrumentation force-stops its target process; force a fresh service binding.
        // These commands are test-only, executed on the named disposable AVD by our script.
        fun shell(command:String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes()} }
        shell("pm clear com.linedraw.fixture")
        shell("settings put secure enabled_accessibility_services ''")
        delay(1000)
        shell("settings put secure enabled_accessibility_services ${app.packageName}/com.linedraw.app.engine.DrawAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        val connected=withTimeoutOrNull(10_000) {while(DrawAccessibilityService.instance==null) delay(100);true} == true
        if (!connected) {
            // Android instrumentation may retain a stale binding from its forced process restart.
            shell("settings put secure enabled_accessibility_services ''")
            delay(1000)
            shell("settings put secure enabled_accessibility_services ${app.packageName}/com.linedraw.app.engine.DrawAccessibilityService")
        }
        withTimeout(20_000) {while(DrawAccessibilityService.instance==null) delay(100)}
    }
    @After fun cleanup() { runBlocking { withContext(Dispatchers.Main) {DrawAccessibilityService.instance?.halt("測試完成",true)};delay(300) };scenario?.close() }
    @Test fun friendThenDrawAndAlreadyContinueWithoutDuplicateSubmit() = runBlocking {
        val b=repo.start(listOf("demo:friend","demo:already","demo:loss"),"__demo__",true,true)
        withContext(Dispatchers.Main){DrawAccessibilityService.instance!!.kick()}
        withTimeout(45_000) {while(repo.dao.latestBatch()?.state=="RUNNING") delay(250)}
        val finished=repo.dao.latestBatch()!!
        assertEquals("${finished.reason}; fixture=${DrawAccessibilityService.lastFixturePage}; items=${repo.dao.items(b.id)}","FINISHED",finished.state)
        assertEquals("SUBMITTED",repo.dao.record("__demo__","demo:friend")?.status)
        assertEquals("ALREADY",repo.dao.record("__demo__","demo:already")?.status)
        assertEquals("SUBMITTED",repo.dao.record("__demo__","demo:loss")?.status)
        val attempts=repo.dao.attempts().filter{it.batchId==b.id}
        assertEquals(1,attempts.count{it.action=="ADD_FRIEND" && it.disposition=="DISPATCHED"})
        assertEquals(2,attempts.count{it.action=="SUBMIT" && it.disposition=="DISPATCHED"})
        assertEquals(3,finished.currentIndex)
        assertTrue(runCatching{repo.start(listOf("demo:friend"),"__demo__",true,true)}.isFailure)
    }
    @Test fun captchaPausesBeforeAnySideEffect() = runBlocking {
        val b=repo.start(listOf("demo:captcha"),"__demo__",true,true)
        withContext(Dispatchers.Main){DrawAccessibilityService.instance!!.kick()}
        withTimeout(20_000) {while(repo.dao.latestBatch()?.state=="RUNNING") delay(250)}
        assertEquals("PAUSED",repo.dao.latestBatch()!!.state)
        assertNull(repo.dao.record("__demo__","demo:captcha"))
        assertTrue(repo.dao.attempts().filter{it.batchId==b.id}.none{it.action in setOf("SUBMIT","ADD_FRIEND","ADD_FRIEND_AND_SUBMIT")})
    }
    @Test fun webViewCombinedAndDirectButtonsSubmitAutomaticallyOnce() = runBlocking {
        val specs=listOf("web-combined","web-direct")
        val now=System.currentTimeMillis()
        repo.dao.upsertDraws(specs.mapIndexed { index,name -> Draw("demo:$name","demo:$name","demo:$name",
            "LineDraw 測試店","模擬資料","自動點擊測試 $index","linedraw-fixture://$name","模擬活動",now-60_000,now+60_000,index,demo=true) })
        val b=repo.start(specs.map{"demo:$it"},"__demo__",true,true)
        withContext(Dispatchers.Main){DrawAccessibilityService.instance!!.kick()}
        withTimeout(45_000){while(repo.dao.latestBatch()?.state=="RUNNING") delay(250)}
        assertEquals("${repo.dao.latestBatch()?.reason}; page=${DrawAccessibilityService.lastFixturePage}","FINISHED",repo.dao.latestBatch()?.state)
        assertEquals("SUBMITTED",repo.dao.record("__demo__","demo:web-combined")?.status)
        assertEquals("SUBMITTED",repo.dao.record("__demo__","demo:web-direct")?.status)
        val attempts=repo.dao.attempts().filter{it.batchId==b.id}
        assertEquals(1,attempts.count{it.action=="ADD_FRIEND_AND_SUBMIT" && it.disposition=="DISPATCHED"})
        assertEquals(1,attempts.count{it.action=="SUBMIT" && it.disposition=="DISPATCHED"})
        assertEquals(0,attempts.count{it.action=="ADD_FRIEND" && it.disposition=="DISPATCHED"})
    }
    @Test fun fiveItemQueueAdvancesImmediatelyWithoutReadingResultsOrResubmitting() = runBlocking {
        val specs=listOf("web-opaque","web-combined","web-stalled","web-direct","web-already")
        val now=System.currentTimeMillis()
        repo.dao.upsertDraws(specs.mapIndexed { index,name -> Draw("demo:$name","demo:$name","demo:$name",
            "任意清單店家","模擬資料","連續抽選 $index","linedraw-fixture://$name","模擬活動",now-60_000,now+120_000,index,demo=true) })
        val b=repo.start(specs.map{"demo:$it"},"__demo__",true,true)
        withContext(Dispatchers.Main){DrawAccessibilityService.instance!!.kick()}
        withTimeout(50_000){while(repo.dao.latestBatch()?.state=="RUNNING") delay(250)}
        val finished=repo.dao.latestBatch()!!
        assertEquals("${finished.reason}; page=${DrawAccessibilityService.lastFixturePage}","FINISHED",finished.state)
        assertEquals(5,finished.currentIndex)
        assertEquals(listOf("SUBMITTED","SUBMITTED","SUBMITTED","SUBMITTED","ALREADY"),repo.dao.items(b.id).map{it.state})
        assertEquals("SUBMITTED",repo.dao.record("__demo__","demo:web-combined")?.status)
        assertEquals("未讀取",repo.dao.record("__demo__","demo:web-opaque")?.result)
        assertEquals("SUBMITTED",repo.dao.record("__demo__","demo:web-direct")?.status)
        val attempts=repo.dao.attempts().filter{it.batchId==b.id}
        assertEquals((0..4).toList(),attempts.filter{it.action=="OPEN"}.sortedBy{it.at}.map{it.position})
        for (position in 0..3) assertEquals(1,attempts.count{it.position==position && it.action in setOf("SUBMIT","ADD_FRIEND_AND_SUBMIT") && it.disposition=="DISPATCHED"})
        assertEquals(0,attempts.count{it.position==4 && it.action in setOf("SUBMIT","ADD_FRIEND_AND_SUBMIT")})
        for (position in 0..3) {
            val click=attempts.single{it.position==position && it.action=="CLICK_RESULT"}
            val next=attempts.single{it.position==position+1 && it.action=="OPEN"}
            assertTrue("Next link waited ${next.at-click.at} ms",next.at-click.at in 0..2_000)
        }
        assertTrue(runCatching{repo.start(listOf("demo:web-opaque"),"__demo__",true,true)}.isFailure)
    }

    @Test fun receivedCouponPagesAndWinningTransitionContinueWithoutOpeningCoupons(): Unit = runBlocking {
        val specs=listOf("web-claimed-one","web-win-claimed","web-claimed-two","web-direct","web-already")
        val b=runWebQueue(specs)
        val finished=waitFinished(50_000)
        assertEquals(5,finished.currentIndex)
        assertEquals(listOf("ALREADY","SUBMITTED","ALREADY","SUBMITTED","ALREADY"),repo.dao.items(b.id).map { it.state })
        assertEquals("已領取優惠券",repo.dao.record("__demo__","demo:web-claimed-one")?.result)
        assertEquals("已領取優惠券",repo.dao.record("__demo__","demo:web-claimed-two")?.result)
        val attempts=repo.dao.attempts().filter { it.batchId==b.id }
        assertFalse(attempts.any { it.action=="PAUSE" })
        assertEquals(listOf(0,1,2,3,4),attempts.filter { it.action=="OPEN" }.sortedBy { it.at }.map { it.position })
        assertEquals(listOf(1,3),attempts.filter { it.action=="SUBMIT" && it.disposition=="DISPATCHED" }.sortedBy { it.at }.map { it.position })
        for(position in listOf(0,2)) {
            assertFalse(attempts.any { it.position==position && it.action in setOf("SUBMIT","ADD_FRIEND","ADD_FRIEND_AND_SUBMIT","CLICK_RESULT") })
        }
        assertTrue(runCatching {repo.start(listOf("demo:web-claimed-one"),"__demo__",true,true)}.isFailure)
        saveTerminalEvidence("winning-transition",b)
    }

    @Test fun supportedTerminalPagesContinueWithoutAnyResultOrCouponClicks() = runBlocking {
        val specs=listOf("web-result-loss","web-result-thanks","web-result-win","web-claimed-one","web-already","web-direct")
        val b=runWebQueue(specs)
        val finished=waitFinished()
        val fixture=saveTerminalEvidence("supported-terminal-pages",b)
        assertEquals(6,finished.currentIndex)
        assertEquals(listOf("COMPLETE","COMPLETE","COMPLETE","ALREADY","ALREADY","SUBMITTED"),repo.dao.items(b.id).map { it.state })
        assertEquals(listOf("未中獎","未中獎","中獎","已領取優惠券","未提供","未讀取"),
            specs.map { repo.dao.record("__demo__","demo:$it")?.result })
        val attempts=repo.dao.attempts().filter { it.batchId==b.id }
        assertFalse(attempts.any { it.action=="PAUSE" || it.action=="REOPEN" })
        assertEquals((0..5).toList(),attempts.filter { it.action=="OPEN" }.sortedBy { it.at }.map { it.position })
        assertEquals(listOf(5),attempts.filter { it.action in setOf("SUBMIT","ADD_FRIEND","ADD_FRIEND_AND_SUBMIT") && it.disposition=="DISPATCHED" }.map { it.position })
        assertEquals(setOf("clicks:web-direct:draw"),fixture.filterKeys { it.startsWith("clicks:") }.keys)
        assertEquals(1,fixture["clicks:web-direct:draw"])
        assertTrue(runCatching {repo.start(specs.take(5).map { "demo:$it" },"__demo__",true,true)}.isFailure)
    }

    @Test fun actualLossAndUseCouponFinishWithoutRetryOrCouponClicks() = runBlocking {
        val b=runWebQueue(listOf("web-result-loss-actual","web-result-win-use","web-direct"))
        val finished=try { waitFinished(25_000) } catch (error:Throwable) {
            saveTerminalEvidence("fixed-terminal-variants",b)
            throw error
        }
        val fixture=saveTerminalEvidence("fixed-terminal-variants",b)
        assertEquals(3,finished.currentIndex)
        assertEquals(listOf("COMPLETE","ALREADY","SUBMITTED"),repo.dao.items(b.id).map { it.state })
        val attempts=repo.dao.attempts().filter { it.batchId==b.id }
        for (position in 0..1) {
            assertEquals(1,attempts.count { it.position==position && it.action=="OPEN" })
            assertTrue(attempts.none { it.position==position && it.action in setOf("SUBMIT","ADD_FRIEND","ADD_FRIEND_AND_SUBMIT","CLICK_RESULT") })
            assertNotNull(repo.dao.record("__demo__",repo.dao.item(b.id,position)!!.activityKey))
        }
        assertFalse(attempts.any { it.action=="REOPEN" || it.action=="LOAD_FAILED" || it.action=="PAUSE" })
        assertEquals("未中獎",repo.dao.record("__demo__","demo:web-result-loss-actual")?.result)
        assertEquals("已領取優惠券",repo.dao.record("__demo__","demo:web-result-win-use")?.result)
        assertEquals(setOf("clicks:web-direct:draw"),fixture.filterKeys { it.startsWith("clicks:") }.keys)
        assertEquals(1,fixture["clicks:web-direct:draw"])
    }

    private suspend fun saveTerminalEvidence(name:String,batch:Batch):Map<String,Int> {
        val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val xml=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "run-as com.linedraw.fixture cat shared_prefs/fixture.xml")).use { it.readBytes().toString(Charsets.UTF_8) }
        val fixture=Regex("<int name=\"([^\"]+)\" value=\"([0-9]+)\"").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val items=repo.dao.items(batch.id)
        val data=org.json.JSONObject().apply {
            put("appVersion",BuildConfig.VERSION_NAME)
            put("state",repo.dao.latestBatch()?.state)
            put("index",repo.dao.latestBatch()?.currentIndex)
            put("fixtureCounters",org.json.JSONObject(fixture))
            put("items",org.json.JSONArray(items.map { item -> org.json.JSONObject().apply {
                put("position",item.position);put("activity",item.activityKey);put("state",item.state)
                put("reason",item.reason);put("updatedAt",item.updatedAt)
                val record=repo.dao.record(batch.profile,item.activityKey)
                put("recordStatus",record?.status ?: org.json.JSONObject.NULL)
                put("result",record?.result ?: org.json.JSONObject.NULL)
            } }))
            put("attempts",org.json.JSONArray(repo.dao.attempts().filter { it.batchId==batch.id }.sortedBy { it.at }.map { attempt ->
                org.json.JSONObject().apply {
                    put("position",attempt.position);put("action",attempt.action);put("detail",attempt.disposition);put("at",attempt.at)
                }
            }))
        }
        java.io.File(app.filesDir,"terminal-validation").apply { mkdirs() }.resolve("$name.json").writeText(data.toString(2))
        return fixture
    }

    @Test fun headerCouponDoesNotMaskDrawAndDisabledFooterStillFinishes() = runBlocking {
        val b=runWebQueue(listOf("web-header-use","web-result-win-use-disabled","web-direct"))
        assertEquals(3,waitFinished(25_000).currentIndex)
        val fixture=saveTerminalEvidence("terminal-button-boundaries",b)
        assertEquals(listOf("SUBMITTED","ALREADY","SUBMITTED"),repo.dao.items(b.id).map { it.state })
        assertEquals(setOf("clicks:web-header-use:draw","clicks:web-direct:draw"),fixture.filterKeys { it.startsWith("clicks:") }.keys)
        assertTrue(fixture.filterKeys { it.startsWith("clicks:") }.values.all { it==1 })
    }

    @Test fun endedDisabledButtonsSkipAndContinueWithoutRecordingDraws() = runBlocking {
        val b = runWebQueue(listOf("ended-disabled", "web-ended", "web-direct"))
        assertEquals(3, waitFinished().currentIndex)
        assertEquals(listOf("SKIPPED", "SKIPPED", "SUBMITTED"), repo.dao.items(b.id).map { it.state })
        for (name in listOf("ended-disabled", "web-ended")) assertNull(repo.dao.record("__demo__", "demo:$name"))
        val clicks = repo.dao.attempts().filter { it.batchId == b.id && it.action in setOf("SUBMIT", "ADD_FRIEND", "ADD_FRIEND_AND_SUBMIT", "CLICK_RESULT") }
        assertTrue(clicks.isNotEmpty())
        assertTrue(clicks.all { it.position == 2 })
    }

    @Test fun endedPeriodInNativeAndWebViewsContinuesToNextDraw() = runBlocking {
        val names=listOf("ended-period-text","ended-period-disabled","web-ended-period","web-ended-notice","web-direct")
        val b=runWebQueue(names)
        assertEquals(5,waitFinished().currentIndex)
        assertEquals(listOf("SKIPPED","SKIPPED","SKIPPED","SKIPPED","SUBMITTED"),repo.dao.items(b.id).map{it.state})
        val evidence=saveTerminalEvidence("ended-period-notices",b)
        assertEquals(setOf("clicks:web-direct:draw"),evidence.filterKeys{it.startsWith("clicks:")}.keys)
        assertEquals(1,evidence["clicks:web-direct:draw"])
        assertTrue(repo.dao.attempts().filter{it.batchId==b.id && it.action in setOf("SUBMIT","ADD_FRIEND","ADD_FRIEND_AND_SUBMIT")}.all{it.position==4})
    }

    @Test fun missingUsageConsentStopsPersistedBatchBeforeAnyDispatch() = runBlocking {
        val b = repo.start(listOf("demo:friend"), "__demo__", true, true)
        assertNotNull(app.access.current())
        app.usageConsent.decline()
        withContext(Dispatchers.Main) { DrawAccessibilityService.instance!!.kick() }
        withTimeout(5_000) { while (repo.dao.latestBatch()?.state == "RUNNING") delay(100) }
        assertEquals("PAUSED", repo.dao.latestBatch()?.state)
        assertNull(repo.dao.record("__demo__", "demo:friend"))
        assertTrue(repo.dao.attempts().filter { it.batchId == b.id }.none { it.disposition == "DISPATCHED" })
    }

    private suspend fun runWebQueue(specs:List<String>):Batch {
        val now=System.currentTimeMillis()
        repo.dao.upsertDraws(specs.mapIndexed { index,name -> Draw("demo:$name","demo:$name","demo:$name",
            "載入測試","模擬資料",name,"linedraw-fixture://$name","模擬活動",now-60_000,now+600_000,index,demo=true) })
        val b=repo.start(specs.map{"demo:$it"},"__demo__",true,true)
        withContext(Dispatchers.Main){DrawAccessibilityService.instance!!.kick()}
        return b
    }
    private suspend fun waitFinished(timeout:Long=45_000):Batch {
        try { withTimeout(timeout){while(repo.dao.latestBatch()?.state=="RUNNING") delay(250)} }
        catch(e:TimeoutCancellationException) {
            error("Timeout: batch=${repo.dao.latestBatch()}; page=${DrawAccessibilityService.lastFixturePage}; items=${repo.dao.latestBatch()?.let { repo.dao.items(it.id) }}; trace=${repo.dao.attempts().take(45)}")
        }
        return repo.dao.latestBatch()!!.also {
            assertEquals("${it.reason}; page=${DrawAccessibilityService.lastFixturePage}","FINISHED",it.state)
        }
    }
    @Test fun slowLoadingAndStalePreviousButtonWaitForNewDocument() = runBlocking {
        val b=runWebQueue(listOf("web-stalled","web-stale","web-slow","web-direct"))
        waitFinished()
        assertEquals(List(4){"SUBMITTED"},repo.dao.items(b.id).map{it.state})
        val attempts=repo.dao.attempts().filter{it.batchId==b.id}
        for(position in 0..3) assertEquals(1,attempts.count{it.position==position && it.action=="SUBMIT" && it.disposition=="DISPATCHED"})
        for((position,minWait) in listOf(1 to 4_800L,2 to 7_800L)) {
            val open=attempts.single{it.position==position && it.action=="OPEN"}
            val click=attempts.single{it.position==position && it.action=="CLICK_RESULT"}
            assertTrue("Clicked previous page after ${click.at-open.at}ms",click.at-open.at>=minWait)
        }
    }
    @Test fun loadingTimeoutReopensOnceThenSkipsUnsentAndContinues() = runBlocking {
        val b=runWebQueue(listOf("web-retry","web-never","web-direct"))
        waitFinished(150_000)
        assertEquals(listOf("SUBMITTED","LOAD_FAILED","SUBMITTED"),repo.dao.items(b.id).map{it.state})
        val attempts=repo.dao.attempts().filter{it.batchId==b.id}
        assertEquals(2,attempts.count{it.position==0 && it.action=="OPEN"})
        assertEquals(2,attempts.count{it.position==1 && it.action=="OPEN"})
        assertEquals(0,attempts.count{it.position==1 && it.action=="SUBMIT"})
        assertNull(repo.dao.record("__demo__","demo:web-never"))
        assertNotNull(repo.dao.record("__demo__","demo:web-direct"))
    }
    @Test fun offlineWaitResumesAndStopPreventsNetworkRecoveryRestart() = runBlocking {
        val original=repo
        val online=java.util.concurrent.atomic.AtomicBoolean(false)
        app.repository=Repository(original.db,app,access=original.access,connectivity={online.get()})
        try {
            val b=runWebQueue(listOf("web-direct"))
            delay(2_000)
            assertEquals("RUNNING",repo.dao.latestBatch()!!.state)
            assertTrue(repo.dao.attempts().none{it.batchId==b.id && it.action=="OPEN"})
            online.set(true);waitFinished()
            online.set(false)
            val stopped=runWebQueue(listOf("web-combined"))
            delay(1_000)
            withContext(Dispatchers.Main){DrawAccessibilityService.instance!!.halt("test stop",true)}
            withTimeout(5_000){while(repo.dao.latestBatch()?.state!="STOPPED") delay(100)}
            online.set(true);delay(1_000)
            assertEquals("STOPPED",repo.dao.latestBatch()!!.state)
            assertTrue(repo.dao.attempts().none{it.batchId==stopped.id && it.action=="OPEN"})
        } finally { app.repository=original }
    }

    @Test fun ambiguousItemReopensThenSkipsAndFooterInstructionsContinue() = runBlocking {
        val b=runWebQueue(listOf("web-ambiguous","web-instructions","web-result-copy","web-footer"))
        waitFinished()
        assertEquals(listOf("LOAD_FAILED","SUBMITTED","COMPLETE","SUBMITTED"),repo.dao.items(b.id).map { it.state })
        val attempts=repo.dao.attempts().filter { it.batchId==b.id }
        assertEquals(2,attempts.count { it.position==0 && it.action=="OPEN" })
        assertTrue(attempts.none { it.position==0 && it.action in setOf("SUBMIT","ADD_FRIEND") })
        assertTrue(attempts.none { it.action=="ADD_FRIEND" })
        assertNull(repo.dao.record("__demo__","demo:web-ambiguous"))
    }
    @Test fun friendOptOutSkipsAndKeepsDrawingNextItem() = runBlocking {
        val b=repo.start(listOf("demo:friend","demo:loss"),"__demo__",false,true)
        withContext(Dispatchers.Main) { DrawAccessibilityService.instance!!.kick() }
        waitFinished()
        assertEquals(listOf("SKIPPED","SUBMITTED"),repo.dao.items(b.id).map { it.state })
        assertTrue(repo.dao.attempts().none { it.batchId==b.id && it.action=="ADD_FRIEND" })
    }
    @Test fun overlayMovesAwayFromFooterAndUserCanSkipLoadingItem() = runBlocking {
        val b=runWebQueue(listOf("web-slow","web-never","web-direct"))
        delay(2000)
        withContext(Dispatchers.Main) {
            val service=DrawAccessibilityService.instance!!
            val control=DrawAccessibilityService::class.java.getDeclaredField("overlay").apply { isAccessible=true }.get(service) as android.view.View
            val wm=service.getSystemService(android.view.WindowManager::class.java)
            val params=control.layoutParams as android.view.WindowManager.LayoutParams
            params.y=wm.currentWindowMetrics.bounds.height()-control.height-100
            wm.updateViewLayout(control,params)
        }
        withTimeout(20_000) { while(repo.dao.latestBatch()?.currentIndex==0) delay(100) }
        assertEquals("SUBMITTED",repo.dao.item(b.id,0)?.state)
        withTimeout(5000) { while(repo.dao.attempts().none { it.batchId==b.id && it.position==1 && it.action=="OPEN" }) delay(100) }
        withContext(Dispatchers.Main) {
            val service=DrawAccessibilityService.instance!!
            val control=DrawAccessibilityService::class.java.getDeclaredField("overlay").apply { isAccessible=true }.get(service) as android.view.ViewGroup
            fun find(view:android.view.View):android.widget.Button? {
                if(view is android.widget.Button && view.text=="略過") return view
                if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            assertTrue(find(control)!!.performClick())
        }
        waitFinished()
        assertEquals(listOf("SUBMITTED","SKIPPED","SUBMITTED"),repo.dao.items(b.id).map { it.state })
        assertNull(repo.dao.record("__demo__","demo:web-never"))
    }
}

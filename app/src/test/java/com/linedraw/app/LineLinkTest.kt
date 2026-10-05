package com.linedraw.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.usage.DenyAccess
import com.linedraw.app.data.*
import com.linedraw.app.engine.LineLinkLauncher
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LineLinkTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:DrawDatabase
    private val target=TestCatalog.canonical(TestCatalog.links.first().second)
    @Before fun setup() {db=Room.inMemoryDatabaseBuilder(app,DrawDatabase::class.java).allowMainThreadQueries().build()}
    @After fun close(){db.close()}
    private fun repo(resolve:suspend (String)->String) = Repository(db,app,object:CatalogSource {
        override suspend fun fetch(catalog: DrawCatalog):String=error("manual open must not fetch website")
        override suspend fun resolve(url:String)=resolve(url)
    },access=TestAccess)
    @Test fun manualShortLinkIsResolvedWithoutChangingRecordsOrStartingBatch():Unit=runBlocking {
        var input=""
        val repo=repo {input=it;target}
        val d=TestCatalog.draws().first()
        repo.dao.upsertDraws(listOf(d));repo.manual(d,TestCatalog.profile("p"))
        val before=repo.dao.record(TestCatalog.profile("p"),d.activityKey)
        assertEquals(target,repo.manualOpenUrl(d,true));assertEquals(d.url,input)
        assertEquals(before,repo.dao.record(TestCatalog.profile("p"),d.activityKey))
        assertEquals(d,repo.dao.draws(listOf(d.rowKey)).single());assertNull(repo.dao.activeBatch())
    }
    @Test fun changedPilotCouponAndUnresolvedOrUntrustedDestinationsAreRejected():Unit=runBlocking {
        for(url in listOf("https://liff.line.me/app/c/changed","https://lin.ee/a","https://evil.example/a")) {
            assertTrue(url,runCatching {repo{url}.manualOpenUrl(TestCatalog.draws().first(),true)}.isFailure)
        }
    }
    @Test fun networkFailuresHaveActionableMessage():Unit=runBlocking {
        val e=runCatching {repo{throw java.io.IOException("private details")}.manualOpenUrl(TestCatalog.draws().first(),true)}.exceptionOrNull()!!
        assertTrue(e.message!!.contains("請確認網路"));assertFalse(e.message!!.contains("private details"))
    }
    @Test fun unacceptedConsentCannotResolveManualOpen():Unit=runBlocking {
        val denied=Repository(db,app,object:CatalogSource {
            override suspend fun fetch(catalog: DrawCatalog):String=error("not called")
            override suspend fun resolve(url:String):String=error("not called")
        },access=DenyAccess)
        assertTrue(runCatching{denied.manualOpenUrl(TestCatalog.draws().first(),true)}.exceptionOrNull() is com.linedraw.app.usage.AccessDenied)
    }
    @Test fun launcherDispatchesResolvedHttpsToLineAndRejectsShortUrl() {
        var sent:Intent?=null
        val ctx=object:ContextWrapper(app){override fun startActivity(intent:Intent){sent=intent}}
        LineLinkLauncher.open(ctx,target)
        assertEquals(target,sent!!.dataString);assertEquals(LineLinkLauncher.PACKAGE,sent!!.`package`)
        assertEquals(Intent.ACTION_VIEW,sent!!.action)
        assertTrue(sent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        sent=null
        assertTrue(runCatching{LineLinkLauncher.open(ctx,"https://lin.ee/WGMIH4U")}.isFailure);assertNull(sent)
    }
    @Test fun missingLineErrorDoesNotExposeAndroidIntentOrPretendSuccess() {
        val ctx=object:ContextWrapper(app){override fun startActivity(intent:Intent){throw ActivityNotFoundException("No Activity found to handle Intent $intent")}}
        val message=runCatching{LineLinkLauncher.open(ctx,target)}.exceptionOrNull()!!.message!!
        assertTrue(message.contains("找不到可用的 LINE"));assertTrue(message.contains("同一個空間"))
        assertFalse(message.contains("Intent"));assertFalse(message.contains(target))
    }
    @Test fun installedButUnsupportedLineProvidesSettingsGuidance() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName=LineLinkLauncher.PACKAGE
            applicationInfo=ApplicationInfo().apply {packageName=LineLinkLauncher.PACKAGE;enabled=true}
        })
        val ctx=object:ContextWrapper(app){override fun startActivity(intent:Intent){throw ActivityNotFoundException()}}
        assertTrue(runCatching{LineLinkLauncher.open(ctx,target)}.exceptionOrNull()!!.message!!.contains("開啟支援的連結"))
    }
    @Test fun securityRestrictionIsReportedWithoutBrowserFallback() {
        var calls=0
        val ctx=object:ContextWrapper(app){override fun startActivity(intent:Intent){calls++;throw SecurityException("restricted")}}
        assertTrue(runCatching{LineLinkLauncher.open(ctx,target)}.exceptionOrNull()!!.message!!.contains("手機限制"))
        assertEquals(1,calls)
    }
}

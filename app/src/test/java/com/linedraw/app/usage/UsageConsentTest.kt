package com.linedraw.app.usage

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.linedraw.app.usage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UsageConsentTest {
    private lateinit var prefs: SharedPreferences
    @Before fun setup() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("usage-test", 0)
        prefs.edit().clear().commit()
    }
    @Test fun explicitAgreementPersistsAcrossReopenAndDeclineClearsIt() {
        val consent = UsageConsent(prefs)
        assertFalse(consent.accepted.value)
        assertTrue(consent.accept()); assertTrue(UsageConsent(prefs).accepted.value)
        consent.decline()
        assertFalse(consent.accepted.value); assertFalse(UsageConsent(prefs).accepted.value)
    }
    @Test fun changedDeclarationRequiresNewConsent() {
        val old = UsageConsent(prefs, 1); old.accept()
        val revised = UsageConsent(prefs, 2)
        assertFalse(revised.accepted.value)
        assertTrue(revised.accept()); assertTrue(UsageConsent(prefs, 2).accepted.value)
    }
    @Test fun failedPersistenceDoesNotGrantAccess() {
        val failed = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by prefs.edit() {
                override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
                override fun commit() = false
            }
        }
        val consent = UsageConsent(failed)
        assertFalse(consent.accept()); assertFalse(consent.accepted.value)
    }
    @Test fun unacceptedConsentBlocksOperationsAndCapturedPermit() = runTest {
        val consent = UsageConsent(prefs); val guard = ConsentAccessGuard(consent)
        assertNull(guard.current())
        assertTrue(runCatching { guard.fresh() }.exceptionOrNull() is AccessDenied)
        consent.accept(); val permit = guard.fresh()
        var clicks = 0
        guard.dispatch(permit) { clicks++ }
        assertEquals(1, clicks)
        consent.decline()
        assertNull(guard.current()); assertFalse(guard.permits(permit))
        assertTrue(runCatching { guard.dispatch(permit) { clicks++ } }.exceptionOrNull() is AccessDenied)
        assertEquals(1, clicks)
    }
    @Test fun acceptingAgainDoesNotReviveCapturedPermit() = runTest {
        val consent = UsageConsent(prefs); val guard = ConsentAccessGuard(consent)
        consent.accept(); val old = guard.fresh()
        consent.decline(); consent.accept()
        assertFalse(guard.permits(old)); assertNotEquals(old, guard.fresh())
    }
    @Test fun persistedAgreementWorksLocallyAfterRestart() = runTest {
        UsageConsent(prefs).accept()
        val restarted = ConsentAccessGuard(UsageConsent(prefs))
        val permit = restarted.fresh()
        assertTrue(restarted.permits(permit))
        assertEquals("completed", restarted.dispatch(permit) { "completed" })
    }
}

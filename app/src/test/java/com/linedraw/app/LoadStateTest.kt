package com.linedraw.app

import com.linedraw.app.engine.*
import org.junit.Assert.*
import org.junit.Test

class LoadStateTest {
    @Test fun slowLoadsGetThirtySecondsAndOnlyOneReopen() {
        val load=LoadState();load.begin(100)
        for(ms in listOf(2_500L,10_000L,25_000L,30_099L)) assertEquals(LoadState.Expiry.NONE,load.expiry(ms))
        assertEquals(LoadState.Expiry.REOPEN,load.expiry(30_100))
        load.begin(30_100)
        assertEquals(2,load.attempt)
        assertEquals(LoadState.Expiry.NONE,load.expiry(60_099))
        assertEquals(LoadState.Expiry.FAILED,load.expiry(60_100))
    }
    @Test fun disconnectDoesNotSpendThePageLoadingBudget() {
        val load=LoadState();load.begin(0)
        assertTrue(load.network(false,10_000));assertTrue(load.network(false,59_000))
        assertEquals(LoadState.Expiry.NONE,load.expiry(59_000))
        assertTrue(load.network(true,60_000))
        assertEquals(LoadState.Expiry.NONE,load.expiry(79_999))
        assertEquals(LoadState.Expiry.REOPEN,load.expiry(80_000))
    }
    @Test fun persistentOfflinePausesAfterSixtySeconds() {
        val load=LoadState();assertTrue(load.network(false,0));assertTrue(load.network(false,59_999))
        assertFalse(load.network(false,60_000))
    }
    @Test fun failedResolutionCanUseOneReopenImmediately() {
        val load=LoadState();load.begin(0);load.failNow(3_000)
        assertEquals(LoadState.Expiry.REOPEN,load.expiry(3_000))
        load.begin(3_000);load.failNow(4_000)
        assertEquals(LoadState.Expiry.FAILED,load.expiry(4_000))
    }
    @Test fun precedingButtonCannotBeClickedUntilDocumentChanges() {
        val old=Page("line",setOf("same title"),setOf("抽選"),"old-document")
        val guard=PageTransitionGuard(old)
        assertFalse(guard.accept(old));assertFalse(guard.accept(old.copy()))
        assertTrue(guard.accept(old.copy(document="new-document")))
    }
    @Test fun observedLoadingTransitionCanLeadToIdenticalButtonText() {
        val old=Page("line",emptySet(),setOf("抽選"))
        val guard=PageTransitionGuard(old)
        assertTrue(guard.accept(old.copy(buttons=emptySet())))
        assertTrue(guard.accept(old))
    }
    @Test fun lateResultTextOnTheSameNodesIsNotANewDocument() {
        val old=Page("line",setOf("抽選"),setOf("抽選"),"old-document")
        val guard=PageTransitionGuard(old)
        assertFalse(guard.accept(old.copy(texts=setOf("恭喜中獎"),buttons=emptySet())))
        assertTrue(guard.accept(old.copy(document="new-document")))
    }

    @Test fun flappingNetworkCannotExtendItemBeyondNinetySeconds() {
        val load=LoadState();load.begin(0)
        for (t in listOf(10_000L,40_000L,70_000L)) {
            assertTrue(load.network(false,t));assertTrue(load.network(true,t+20_000))
        }
        assertEquals(0L,load.totalRemaining(90_000))
        assertEquals(LoadState.Expiry.FAILED,load.expiry(90_000))
    }
    @Test fun friendAndReopenBudgetsShareSameTotalDeadline() {
        val load=LoadState();load.track(0);load.begin(15_000);load.restartAfterFriend(44_000)
        load.begin(74_000);load.restartAfterFriend(89_000)
        assertEquals(1_000L,load.remaining(89_000))
        assertEquals(LoadState.Expiry.FAILED,load.expiry(90_000))
    }
    @Test fun localIssueWaitsBeforeReopenAndResetsForNextOpen() {
        val issue=LocalIssue();assertFalse(issue.retryNow(0));assertFalse(issue.retryNow(1999));assertTrue(issue.retryNow(2000))
        issue.clear();assertFalse(issue.retryNow(3000))
    }

}

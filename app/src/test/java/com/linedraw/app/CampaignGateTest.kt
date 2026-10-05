package com.linedraw.app

import com.linedraw.app.data.*
import com.linedraw.app.engine.*
import org.junit.Assert.*
import org.junit.Test

class CampaignGateTest {
    private val draw = TestCatalog.draws().first()
    private val item = BatchItem("batch",0,draw.activityKey,draw.product,draw.store,TestCatalog.canonical(TestCatalog.links.first().second),draw.startsAt,draw.endsAt)
    private fun page(label: String) = Page("line",emptySet(),setOf(label))
    private fun allowed(page: Page, item: BatchItem = this.item, pilot: Boolean = true): Boolean =
        CampaignGate.permits(page,item,"line",false,pilot)

    @Test fun pinnedCombinedButtonNeedsNoNamesOrVisibleId() {assertTrue(allowed(page("加入好友並參加抽獎")))}
    @Test fun pinnedDirectButtonNeedsNoNamesOrVisibleId() {assertTrue(allowed(page("抽選")))}
    @Test fun unrelatedLinkIsNotPermitted() {assertFalse(allowed(page("抽選"),item.copy(url="https://lin.ee/outside")))}
    @Test fun differentStoreAndActivityDoNotBlockTrustedLink() {
        assertTrue(allowed(page("抽選").copy(texts=setOf("其他店家","其他活動"))))
    }
    @Test fun trustedLinkAlreadyResultCanAdvanceWithoutResubmitting() {
        val result=page("").copy(texts=setOf("您已參加過此抽選"))
        assertTrue(allowed(result))
        assertEquals(Participation.ALREADY,(Rules.decide(result,item,true,"line") as Decision.Finish).status)
    }
    @Test fun mainCatalogTrustsResolvedBatchLinkWithoutScreenId() {assertTrue(allowed(page("抽選"),pilot=false))}
    @Test fun mainCannotActOnUnresolvedOrMismatchedLink() {assertFalse(allowed(page("抽選"),item.copy(url="https://lin.ee/unresolved"),pilot=false))}
    @Test fun wrongPackageCannotUseVisibleId() {
        assertFalse(allowed(page("抽選").copy(packageName="other",texts=setOf(item.url))))
    }
    @Test fun ownerScreenshotBottomButtonCanRunAutomatically() {
        val screenshot=Page("line",setOf("陀螺獵人 Beyblade Hunter","自動抽獎測試"),setOf("查看我的優惠券","加入好友並參加抽獎"))
        assertTrue(allowed(screenshot))
        assertEquals(Decision.Click("加入好友並參加抽獎","ADD_FRIEND_AND_SUBMIT"),Rules.decide(screenshot,item,true,"line"))
    }
    @Test fun visibleIdCannotBypassFiveLinkRestriction() {
        val outside=item.copy(url="https://lin.ee/outside")
        assertFalse(allowed(page("抽選").copy(texts=setOf(outside.url)),outside))
    }
}

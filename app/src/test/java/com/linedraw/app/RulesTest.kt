package com.linedraw.app

import com.linedraw.app.data.*
import com.linedraw.app.engine.*
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun actualLossVariantsFinishWithoutClicks() {
        for (label in listOf("可惜...沒有抽中！", "可惜… 沒有抽中 !", "可惜⋯⋯沒有抽中", "可惜，沒有抽中。", "沒有抽中", "很可惜…沒有抽中！")) {
            val result=Rules.decide(page(label),item,true,"line") as Decision.Finish
            assertEquals(Participation.COMPLETE,result.status)
            assertEquals("未中獎",result.result)
        }
    }
    @Test fun lossMentionInsideInstructionsIsNotAResult() {
        for (copy in listOf("如果可惜...沒有抽中！可以參加下次活動", "沒有抽中的人可參加下次抽選"))
            assertEquals(Decision.Click("抽選","SUBMIT"),Rules.decide(page(copy,buttons=setOf("抽選")),item,true,"line"))
    }
    @Test fun footerUseCouponFinishesWithoutReadableWinButNeverClicks() {
        for (label in listOf("使用優惠券", "使用\n優惠券")) {
            val screen=page(buttons=setOf(label)).copy(footerButtons=setOf(label))
            val result=Rules.decide(screen,item,true,"line") as Decision.Finish
            assertEquals(Participation.ALREADY,result.status)
            assertEquals("已領取優惠券",result.result)
            assertTrue(Rules.knownButtons(screen).isEmpty())
            assertTrue(Rules.decide(screen.copy(packageName="other"),item,true,"line") is Decision.Pause)
        }
    }
    @Test fun couponUseInBodyOrHeaderDoesNotHideActualDraw() {
        val draw=Decision.Click("抽選","SUBMIT")
        assertEquals(draw,Rules.decide(page("使用優惠券",buttons=setOf("抽選")),item,true,"line"))
        assertEquals(draw,Rules.decide(page(buttons=setOf("使用優惠券","抽選")),item,true,"line"))
        assertTrue(Rules.decide(page("請輸入驗證碼",buttons=setOf("使用優惠券")).copy(footerButtons=setOf("使用優惠券")),item,true,"line") is Decision.Pause)
    }
    @Test fun endedButtonSkipsWithoutClickingOrRecordingParticipation() {
        for (label in listOf("已結束","已\n結束")) {
            assertTrue(Rules.decide(page("使用時須提供個人資料",buttons=setOf(label)),item,true,"line") is Decision.Skip)
        }
    }
    @Test fun endedMentionOrWrongAppDoesNotSkip() {
        assertTrue(Rules.decide(page("上期活動已結束",buttons=setOf("抽選")),item,true,"line") is Decision.Click)
        assertTrue(Rules.decide(page(buttons=setOf("已結束"),pkg="other"),item,true,"line") is Decision.Pause)
        assertTrue(Rules.decide(page(buttons=setOf("已結束")),item.copy(submitted=true),true,"line") !is Decision.Skip)
    }
    @Test fun endedPeriodNoticeSkipsButtonsBodyAndDialogsWithoutClicking() {
        for (label in listOf("抽獎期間已結束", " 抽獎期間\n已結束！", "抽選期間已結束。", "抽籤期間已結束")) {
            for (screen in listOf(page(label),page(buttons=setOf(label)),page().copy(dialogTexts=setOf(label)))) {
                assertTrue(label,Rules.decide(screen,item,true,"line") is Decision.Skip)
                assertTrue(Rules.knownButtons(screen).isEmpty())
            }
            assertTrue(Rules.terminalButton(label))
        }
    }
    @Test fun endedPeriodNeverMatchesConditionalCopyOrOverridesAuthentication() {
        assertTrue(Rules.decide(page("如果抽獎期間已結束，請等下次",buttons=setOf("抽選")),item,true,"line") is Decision.Click)
        assertTrue(Rules.decide(page("抽獎期間已結束",pkg="other"),item,true,"line") is Decision.Pause)
        assertTrue(Rules.decide(page("抽獎期間已結束","請輸入驗證碼"),item,true,"line") is Decision.Pause)
        assertTrue(Rules.decide(page("抽獎期間已結束"),item.copy(submitted=true),true,"line") !is Decision.Skip)
    }
    private val item=BatchItem("batch",0,"a","魔導神杖","店家","https://lin.ee/a",0,Long.MAX_VALUE)
    private fun page(vararg text:String, buttons:Set<String> = emptySet(), pkg:String="line")=Page(pkg,setOf("店家","魔導神杖")+text,buttons)
    @Test fun addFriendWhenAuthorized() {assertEquals(Decision.Click("加入好友","ADD_FRIEND"),Rules.decide(page(buttons=setOf("加入好友")),item,true,"line"))}
    @Test fun friendOptOutSkips() {assertTrue(Rules.decide(page(buttons=setOf("加入好友")),item,false,"line") is Decision.Skip)}
    @Test fun friendActionCannotRepeat() {assertEquals(Decision.Wait,Rules.decide(page(buttons=setOf("加入好友")),item.copy(friendAttempted=true),true,"line"))}
    @Test fun joinedChatReopensOriginalActivity() {assertEquals(Decision.Reopen,Rules.decide(page("已加入好友"),item.copy(friendAttempted=true,stage="FRIEND"),true,"line"))}
    @Test fun submissionCannotRepeat() {assertEquals(Decision.Wait,Rules.decide(page(buttons=setOf("參加抽選")),item.copy(submitted=true),true,"line"))}
    @Test fun lossIsNotMistakenForWin() {val d=Rules.decide(page("很可惜，未中獎"),item.copy(submitted=true),true,"line") as Decision.Finish;assertEquals("未中獎",d.result)}
    @Test fun alreadyParticipatedIsSeparateOutcome() {val d=Rules.decide(page("您已參加過此抽選"),item,true,"line") as Decision.Finish;assertEquals(Participation.ALREADY,d.status)}
    @Test fun captchaOverridesResult() {assertTrue(Rules.decide(page("請輸入驗證碼","恭喜中獎"),item,true,"line") is Decision.Pause)}
    @Test fun wrongPackageNeverClicks() {assertTrue(Rules.decide(page(buttons=setOf("參加抽選"),pkg="evil"),item,true,"line") is Decision.Pause)}
    @Test fun storeNameDoesNotGateFriendAction() {assertEquals(Decision.Click("加入好友","ADD_FRIEND"),Rules.decide(Page("line",setOf("其他店","魔導神杖"),setOf("加入好友")),item,true,"line"))}
    @Test fun activityNameDoesNotGateResult() {assertEquals("中獎",(Rules.decide(Page("line",setOf("另一商品","恭喜中獎"),emptySet()),item,true,"line") as Decision.Finish).result)}
    @Test fun genericConfirmationIsNotACommand() {assertTrue(Rules.decide(page("點擊確認繼續",buttons=setOf("確認")),item,true,"line") is Decision.Pause)}
    @Test fun ambiguousButtonsRetry() {assertTrue(Rules.decide(page(buttons=setOf("參加抽選","立即抽選")),item,true,"line") is Decision.Retry)}
    @Test fun plainDrawButtonIsRecognized() {assertEquals(Decision.Click("抽選","SUBMIT"),Rules.decide(page(buttons=setOf("抽選")),item,true,"line"))}
    @Test fun combinedFriendAndDrawIsOneSubmission() {assertEquals(Decision.Click("加入好友並抽選","ADD_FRIEND_AND_SUBMIT"),Rules.decide(page(buttons=setOf("加入好友並抽選")),item,true,"line"))}
    @Test fun combinedActionRespectsFriendOptOut() {assertTrue(Rules.decide(page(buttons=setOf("加入好友並抽選")),item,false,"line") is Decision.Skip)}
    @Test fun combinedActionCannotRepeatAfterSubmission() {assertEquals(Decision.Wait,Rules.decide(page(buttons=setOf("加入好友並抽選")),item.copy(submitted=true,friendAttempted=true),true,"line"))}
    @Test fun wrappedCombinedLabelMatches() {assertEquals(Decision.Click("加入好友並抽選","ADD_FRIEND_AND_SUBMIT"),Rules.decide(page(buttons=setOf("加入好友\n並抽選")),item,true,"line"))}
    @Test fun normalizedResultPunctuationIsRecognized() {assertEquals("未中獎",(Rules.decide(page("很可惜,未中獎"),item.copy(submitted=true),true,"line") as Decision.Finish).result)}
    @Test fun actualScreenshotBottomButtonIsRecognized() {assertEquals(Decision.Click("加入好友並參加抽獎","ADD_FRIEND_AND_SUBMIT"),Rules.decide(page(buttons=setOf("查看我的優惠券","加入好友並參加抽獎")),item,true,"line"))}
    @Test fun missingStoreAndActivityNamesDoNotBlockDraw() {
        val named=item.copy(store="陀螺獵人BeybladeHunter")
        val screen=Page("line",emptySet(),setOf("抽選"))
        assertEquals(Decision.Click("抽選","SUBMIT"),Rules.decide(screen,named,true,"line"))
    }
    @Test fun winHeadingWithPunctuationAndSpacingCompletes() {
        val result=Rules.decide(page("恭喜 中獎！"),item.copy(submitted=true),true,"line") as Decision.Finish
        assertEquals("中獎",result.result)
    }
    @Test fun receivedCouponButtonFinishesEvenWhenUsageCopyMentionsPersonalData() {
        val screen=page("恭喜中獎", "使用優惠券時，店家可能請您提供個人資料",buttons=setOf("查看已領取的優惠券"))
        val result=Rules.decide(screen,item,true,"line") as Decision.Finish
        assertEquals(Participation.ALREADY,result.status)
        assertEquals("已領取優惠券",result.result)
    }
    @Test fun receivedCouponButtonWorksWithoutReadableWinningImageText() {
        val result=Rules.decide(page(buttons=setOf("查看已領取的優惠券")),item,true,"line") as Decision.Finish
        assertEquals(Participation.ALREADY,result.status)
    }
    @Test fun receivedCouponButtonToleratesLineWrapAndSpacing() {
        val result=Rules.decide(page(buttons=setOf("查看已領取的\n優惠券")),item,true,"line") as Decision.Finish
        assertEquals("已領取優惠券",result.result)
    }
    @Test fun claimingCouponInInstructionsAloneDoesNotFinish() {
        assertTrue(Rules.decide(page("查看已領取的優惠券"),item,true,"line") !is Decision.Finish)
    }
    @Test fun receivedCouponButtonInWrongAppDoesNotFinish() {
        assertTrue(Rules.decide(page(buttons=setOf("查看已領取的優惠券"),pkg="other"),item,true,"line") is Decision.Pause)
    }
    @Test fun ordinaryCouponListButtonDoesNotHideARequiredPrompt() {
        assertTrue(Rules.decide(page("請登入",buttons=setOf("查看我的優惠券")),item,true,"line") is Decision.Pause)
        assertTrue(Rules.decide(page("請輸入驗證碼",buttons=setOf("抽選")),item,true,"line") is Decision.Pause)
    }

    @Test fun couponInstructionsDoNotBlockDrawOrResult() {
        for (copy in listOf("不需登入即可抽選", "使用優惠券時可能提供個人資料", "本活動不需驗證碼", "付款時出示優惠券")) {
            assertTrue(Rules.decide(page(copy,buttons=setOf("抽選")),item,true,"line") is Decision.Click)
            assertTrue(Rules.decide(page(copy,"恭喜中獎"),item,true,"line") is Decision.Finish)
        }
    }
    @Test fun interactivePromptsStillPauseIncludingClaimedCoupon() {
        for (screen in listOf(
            page(buttons=setOf("抽選","登入")),
            page(buttons=setOf("抽選")).copy(inputs=setOf("請輸入密碼")),
            page(buttons=setOf("抽選")).copy(dialogTexts=setOf("授權存取您的個人資料")),
            page("請輸入驗證碼",buttons=setOf("查看已領取的優惠券")))) {
            assertTrue(Rules.decide(screen,item,true,"line") is Decision.Pause)
        }
    }
    @Test fun separateFriendAttemptDoesNotBlockCombinedDraw() {
        assertEquals(Decision.Click("加入好友並參加抽獎","ADD_FRIEND_AND_SUBMIT"),
            Rules.decide(page(buttons=setOf("加入好友並參加抽獎")),item.copy(friendAttempted=true,stage="FRIEND_CONFIRMED"),true,"line"))
    }
    @Test fun preferredFooterActionMustBeAnObservedButton() {
        assertEquals(Decision.Click("抽選","SUBMIT"),Rules.decide(
            page(buttons=setOf("加入好友","抽選")).copy(preferredAction="抽選"),item,true,"line"))
        assertTrue(Rules.decide(page(buttons=setOf("加入好友","抽選")).copy(preferredAction="invalid"),item,true,"line") is Decision.Retry)
    }

}

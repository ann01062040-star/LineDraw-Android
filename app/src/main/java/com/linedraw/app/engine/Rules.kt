package com.linedraw.app.engine

import com.linedraw.app.data.BatchItem
import com.linedraw.app.data.Participation

data class Page(val packageName: String, val texts: Set<String>, val buttons: Set<String>, val document: String = "",
    val inputs: Set<String> = emptySet(), val dialogTexts: Set<String> = emptySet(), val preferredAction: String? = null,
    val footerButtons: Set<String> = emptySet())
sealed interface Decision {
    data class Click(val label: String, val action: String) : Decision
    data class Finish(val status: Participation, val result: String, val evidence: String) : Decision
    data class Pause(val reason: String) : Decision
    data class Skip(val reason: String) : Decision
    data class Retry(val reason: String) : Decision
    data object Reopen : Decision
    data object Wait : Decision
}

/** The batch owns the activity identity; screen rules recognize actions and results only. */
object Rules {
    private val blockers = listOf("驗證碼", "验证码", "CAPTCHA", "登入", "登录", "解除封鎖", "個人資料", "身分證", "付款", "同意條款", "授權存取", "密碼")
    private val requiredPrompts = listOf("請輸入驗證碼", "请输入验证码", "請登入", "請先登入", "请登录", "請完成驗證", "請完成安全驗證", "請先解除封鎖", "CAPTCHA")
    private val submitLabels = listOf("參加抽選", "立即抽選", "挑戰抽獎", "立即抽獎", "參加抽獎", "抽獎", "抽選")
    private val combinedLabels = listOf("加入好友並抽選", "加入好友並抽獎", "加入好友並參加抽獎")
    fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), " ").trim()
    fun labelMatches(value: String?, label: String): Boolean = value != null &&
        normalize(value).replace(" ", "") == normalize(label).replace(" ", "")
    fun hasText(page: Page, value: String): Boolean = page.texts.any { normalize(it) == normalize(value) }
    private fun resultLabelMatches(text: String, value: String): Boolean {
        fun canonical(label: String) = normalize(label).replace(Regex("[\\s,.!。…⋯]"), "")
        return canonical(text) == canonical(value)
    }
    private fun resultText(page: Page, value: String): Boolean {
        return page.texts.any { resultLabelMatches(it, value) }
    }
    val endedNotices = listOf("抽獎期間已結束", "抽選期間已結束", "抽籤期間已結束")
    val actionLabels: List<String> get() = submitLabels + combinedLabels + "加入好友"
    fun terminalButton(value: String?): Boolean = listOf("已結束", "查看已領取的優惠券", "使用優惠券").any { labelMatches(value,it) } ||
        (value != null && endedNotices.any { resultLabelMatches(value,it) })
    fun knownButtons(page: Page): List<String> = actionLabels
        .filter { label -> page.buttons.any { labelMatches(it, label) } }
    private fun needsUser(page: Page): Boolean {
        // Instructions in the coupon body are not an interactive prompt.
        if (requiredPrompts.any { prompt -> page.texts.any { labelMatches(it.trimEnd('！','!','。',':','：'), prompt) } }) return true
        if ((page.inputs + page.dialogTexts).any { text -> blockers.any { text.contains(it, ignoreCase = true) } }) return true
        return page.buttons.any { button -> blockers.any { labelMatches(button, it) } }
    }
    fun decide(page: Page, item: BatchItem, autoFriend: Boolean, expectedPackage: String): Decision {
        if (page.packageName != expectedPackage) return Decision.Pause("已離開預期的抽選 App")
        if (needsUser(page)) return Decision.Pause("此畫面需要使用者處理")
        // 結束提示可能是不可點擊的內文或提示框，不必按確認或關閉就能開下一筆。
        val endedNotice = (page.texts + page.buttons + page.dialogTexts).any { text -> endedNotices.any { resultLabelMatches(text,it) } }
        if (!item.submitted && (endedNotice || page.buttons.any { labelMatches(it,"已結束") }))
            return Decision.Skip("活動已結束；未送出抽選，接續下一筆")
        // A received-coupon button is a terminal state, including an image-only win.
        // Finish without touching the coupon. General usage copy (e.g. 個人資料)
        // must not turn this already-handled activity into a manual-action pause.
        if (page.buttons.any { labelMatches(it,"查看已領取的優惠券") } ||
            page.footerButtons.any { labelMatches(it,"使用優惠券") })
            return Decision.Finish(Participation.ALREADY,"已領取優惠券","畫面顯示已領取優惠券；不開啟或兌換，接續下一筆")
        if (listOf("您已參加過此抽選", "已參加過抽獎", "已抽過").any { resultText(page,it) }) return Decision.Finish(Participation.ALREADY, "未提供", "畫面明確顯示已抽過")
        if (listOf("很可惜，未中獎", "未中獎", "未抽中", "銘謝惠顧", "可惜沒有抽中", "很可惜沒有抽中", "沒有抽中").any { resultText(page,it) }) return Decision.Finish(Participation.COMPLETE, "未中獎", "畫面明確顯示未中獎")
        if (listOf("恭喜中獎", "恭喜您中獎了！", "恭喜獲得優惠券").any { resultText(page,it) }) return Decision.Finish(Participation.COMPLETE, "中獎", "畫面明確顯示中獎；未執行兌換")
        if (listOf("抽選完成", "抽獎完成").any { resultText(page,it) }) return Decision.Finish(Participation.COMPLETE, "未提供", "畫面明確顯示抽選完成")
        if (item.friendAttempted && "已加入好友" in page.texts && item.stage in setOf("FRIEND", "FRIEND_SENT")) return Decision.Reopen
        if (item.submitted) return Decision.Wait
        val labels = knownButtons(page).let { all -> page.preferredAction?.takeIf { it in all }?.let(::listOf) ?: all }
        if (labels.size > 1) return Decision.Retry("操作按鈕不唯一")
        val label = labels.singleOrNull()
        if (label == "加入好友") {
            if (!autoFriend) return Decision.Skip("未開啟自動加入好友，略過此活動")
            return if (item.friendAttempted) Decision.Wait else Decision.Click(label, "ADD_FRIEND")
        }
        if (label in combinedLabels) {
            if (!autoFriend) return Decision.Skip("未開啟自動加入好友，略過此活動")
            // A separate friend action does not mean this combined draw was submitted.
            return Decision.Click(label!!, "ADD_FRIEND_AND_SUBMIT")
        }
        if (label in submitLabels) return Decision.Click(label!!, "SUBMIT")
        return Decision.Pause("未知抽選畫面，請人工核對")
    }
}

package com.linedraw.app.usage

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object UsageDeclaration {
    // 聲明變更時遞增版本，使用者須重新同意。
    const val VERSION = 3
    val paragraphs = listOf(
        "LineDraw 是免費提供、原始碼公開的抽選輔助工具，採 PolyForm Noncommercial 1.0.0 授權，限非商業使用。商業用途須另行取得授權。它並非 LINE 或活動主辦單位提供的官方 App。",
        "只有在你主動開始批次後，App 才會透過無障礙服務讀取畫面、開啟所選活動，並依你的設定加入店家好友及執行抽選。請確認目前登入的 LINE 帳號；執行期間可隨時暫停或停止。",
        "手機型號、Android／LINE 版本、網路及活動頁面變更，都可能影響操作。本工具不保證所有裝置均能正常使用、抽選必定完成或取得獎項；實際資格與結果以活動主辦單位及 LINE 畫面為準。請遵守相關活動與平台規則。",
        "抽選紀錄、設定及操作診斷保存在本機。同步清單與開啟活動時，仍會連線至來源網站及 LINE；相關服務各有其隱私政策。只有你主動使用分享功能時，診斷內容才會交給你選擇的分享對象。",
        "本專案由維護者於能力範圍內提供更新。歡迎透過 GitHub Issues 回報問題，但不保證回覆或修復時程，也不提供逐一機型設定與個別操作指導。",
        "軟體使用、修改及散布依專案授權條款辦理；擔保與責任限制依該授權及適用法律辦理，不影響依法不得排除的權利。請閱讀下方的完整授權與隱私說明，了解後再開始使用。"
    )
}

class UsageConsent(private val prefs: SharedPreferences, private val requiredVersion: Int = UsageDeclaration.VERSION) {
    private val mutableAccepted = MutableStateFlow(prefs.getInt("accepted_version", 0) == requiredVersion)
    val accepted = mutableAccepted.asStateFlow()
    private var generation = java.util.UUID.randomUUID().toString()
    fun permit(): AccessPermit? = if (accepted.value) AccessPermit(generation) else null
    fun accept(): Boolean {
        val saved = runCatching { prefs.edit().putInt("accepted_version", requiredVersion).commit() }.getOrDefault(false)
        if (saved) mutableAccepted.value = true
        return saved
    }
    fun decline() {
        generation = java.util.UUID.randomUUID().toString()
        mutableAccepted.value = false
        runCatching { prefs.edit().remove("accepted_version").commit() }
    }
    fun requireAccepted() { if (!accepted.value) throw AccessDenied() }
}

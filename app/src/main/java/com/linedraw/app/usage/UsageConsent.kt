package com.linedraw.app.usage

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object UsageDeclaration {
    // 聲明變更時遞增版本，使用者須重新同意。
    const val VERSION = 4
    const val TITLE = "使用 LineDraw 前，請先了解"
    data class Section(val title: String, val body: String)
    val sections = listOf(
        Section("免費使用，無需會員",
            "LineDraw 獨立版是免費提供的抽選輔助工具，不需要網站帳號或 VIP 資格，也不是 LINE 或活動主辦單位提供的官方 App。"),
        Section("由你決定何時開始",
            "你主動開始抽選後，App 才會透過無障礙服務讀取畫面，依選取清單開啟活動、點擊抽選，並依設定加入店家好友。執行期間可以隨時暫停或停止。"),
        Section("實際結果以活動頁面為準",
            "手機型號、Android／LINE 版本、網路及活動頁面變更，都可能影響操作。App 不保證每筆抽選都能完成，也不保證中獎；「已送出」不代表已中獎。請遵守活動及平台規則。"),
        Section("資料與隱私",
            "抽選紀錄、設定及操作診斷保存在手機內。同步清單與執行活動仍需連線至來源網站及 LINE。分享診斷前，請先檢查內容並自行選擇分享對象。"),
        Section("維護與問題回報",
            "歡迎先閱讀使用教學，再透過 GitHub Issues 回報問題。維護者會依能力提供協助，不保證所有機型相容、固定回覆或修復時程，也不提供逐一安裝設定及個別操作教學。"),
        Section("授權方式",
            "本專案採 PolyForm Noncommercial 1.0.0，原始碼可取得，限非商業使用；商業用途須另行取得授權。使用、修改、散布及責任限制，請參閱完整授權條款。")
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

package com.linedraw.app.usage

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object UsageDeclaration {
    // 聲明變更時遞增版本，使用者須重新同意。
    const val VERSION = 2
    val paragraphs = listOf(
        "此 App 為 LineDraw 獨立版抽選輔助工具，僅讀取 Funbox 公開活動清單；使用前請確認已選活動與 LINE 帳號。",
        "此App衍生之相關使用問題、操作方法等等，因Android機型、品牌眾多，恕人力有限，無法一一指導。",
        "本工具不保證各機型皆能正常使用或抽選成功，亦不提供個別操作指導。",
        "使用本App則同意上述使用聲明，不同意則請自行移除本App。"
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

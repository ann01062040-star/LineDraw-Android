package com.linedraw.app.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.linedraw.app.data.LinkPolicy

/** Launch only a resolved coupon URL. Short URLs are web redirects, not LINE activities. */
object LineLinkLauncher {
    const val PACKAGE = "jp.naver.line.android"

    fun open(context: Context, url: String) {
        require(LinkPolicy.allowed(url) && LinkPolicy.canonicalUrl(LinkPolicy.activityKey(url, "")) != null) {
            "尚未取得 LINE 活動連結，請稍後重試"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw IllegalStateException(unavailableMessage(context))
        } catch (_: SecurityException) {
            throw IllegalStateException("手機限制開啟 LINE。請確認 LINE 與 LineDraw 安裝在同一個手機使用者空間，並允許開啟 LINE 連結後重試。")
        }
    }

    private fun unavailableMessage(context: Context): String {
        val info = try { context.packageManager.getApplicationInfo(PACKAGE, 0) }
            catch (_: PackageManager.NameNotFoundException) { null }
        return when {
            info == null -> "找不到可用的 LINE。請安裝 LINE；若使用分身、工作設定檔或安全資料夾，請將 LineDraw 與 LINE 安裝在同一個空間後重試。"
            !info.enabled -> "LINE 已停用，請先在手機設定啟用 LINE，再重新開啟活動。"
            else -> "LINE 無法開啟此活動。請先更新並開啟 LINE，再到手機設定 → 應用程式 → LINE，檢查「開啟支援的連結」後重試。"
        }
    }
}

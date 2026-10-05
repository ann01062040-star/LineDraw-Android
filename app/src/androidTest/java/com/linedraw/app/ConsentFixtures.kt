package com.linedraw.app

/** 測試只設定本機使用聲明，不注入登入狀態或網路驗證。 */
object ConsentFixtures {
    suspend fun install(app: LineDrawApp, usageAccepted: Boolean = true) {
        check(android.os.Build.HARDWARE.contains("ranchu")) { "僅允許在專用模擬器執行" }
        app.ready.await()
        if (usageAccepted) check(app.usageConsent.accept()) else app.usageConsent.decline()
    }
}

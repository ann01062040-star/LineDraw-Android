package com.linedraw.app

import com.linedraw.app.usage.*

/** 僅用於單元測試；正式 App 使用本機聲明同意狀態。 */
object TestAccess : AccessGuard {
    private val permit = AccessPermit("unit-test-consent")
    override suspend fun fresh() = permit
    override fun current() = permit
    override fun permits(permit: AccessPermit) = permit == this.permit
    override fun <T> dispatch(permit: AccessPermit, action: () -> T): T {
        if (!permits(permit)) throw AccessDenied()
        return action()
    }
}

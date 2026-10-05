package com.linedraw.app.usage

/** 本機聲明同意狀態；不包含帳號、權杖、到期時間或網路驗證。 */
data class AccessPermit(val generation: String)
interface AccessGuard {
    suspend fun fresh(): AccessPermit
    fun current(): AccessPermit?
    fun permits(permit: AccessPermit): Boolean
    fun <T> dispatch(permit: AccessPermit, action: () -> T): T
}
class AccessDenied : IllegalStateException("請先同意使用聲明；批次已保留")
object DenyAccess : AccessGuard {
    override suspend fun fresh(): AccessPermit = throw AccessDenied()
    override fun current(): AccessPermit? = null
    override fun permits(permit: AccessPermit) = false
    override fun <T> dispatch(permit: AccessPermit, action: () -> T): T = throw AccessDenied()
}
class ConsentAccessGuard(private val consent: UsageConsent) : AccessGuard {
    override suspend fun fresh(): AccessPermit = current() ?: throw AccessDenied()
    override fun current(): AccessPermit? = consent.permit()
    override fun permits(permit: AccessPermit) = current() == permit
    override fun <T> dispatch(permit: AccessPermit, action: () -> T): T {
        if (!permits(permit)) throw AccessDenied()
        return action()
    }
}

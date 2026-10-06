package com.linedraw.app

import android.app.Application
import androidx.room.Room
import com.linedraw.app.data.DrawDatabase
import com.linedraw.app.data.Repository
import com.linedraw.app.usage.*
import com.linedraw.app.engine.DrawAccessibilityService
import kotlinx.coroutines.*

class LineDrawApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: Repository
    lateinit var ready: Deferred<Unit>
    lateinit var usageConsent: UsageConsent
        private set
    lateinit var access: AccessGuard
        private set

    fun acceptUsage(): Boolean = usageConsent.accept()
    fun declineUsage() {
        usageConsent.decline()
        val service = DrawAccessibilityService.instance
        if (service != null) service.halt("未確認使用須知，已停止批次", true)
        else scope.launch { ready.await(); repository.pause("未確認使用須知，已停止批次", true) }
    }
    override fun onCreate() {
        super.onCreate()
        usageConsent = UsageConsent(getSharedPreferences("usage-consent", MODE_PRIVATE))
        access = ConsentAccessGuard(usageConsent)
        val database = Room.databaseBuilder(this, DrawDatabase::class.java, "linedraw.db").build()
        repository = Repository(database, this, access = access)
        ready = scope.async { repository.recover() }
    }
}

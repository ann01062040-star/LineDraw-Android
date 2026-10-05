package com.linedraw.app.data

import com.linedraw.app.usage.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

class Repository(val db: DrawDatabase, private val context: Context,
    private val source: CatalogSource = HttpCatalogSource(), val access: AccessGuard = DenyAccess, private val connectivity: (() -> Boolean)? = null) {
    val dao = db.dao()
    private fun matchingPermit(original: AccessPermit): AccessPermit = access.current()?.takeIf {
        it.generation == original.generation
    } ?: throw AccessDenied()
    suspend fun batchPermit(batchId: String): AccessPermit {
        val permit = access.current() ?: throw AccessDenied()
        check(dao.activeBatch()?.id == batchId) { "目前批次已變更" }
        return matchingPermit(permit)
    }

    private val syncMutex = Mutex()
    private val batchMutex = Mutex()
    private val continuationMutex = Mutex()
    fun online(): Boolean {
        connectivity?.let { return it() }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    fun batchOnline(demo: Boolean): Boolean = if(demo && connectivity==null) true else online()

    suspend fun selectedCatalog(): DrawCatalog = DrawCatalog.FUNBOX

    suspend fun sync(expectedCatalog: DrawCatalog? = null): String = syncMutex.withLock {
        if (com.linedraw.app.BuildConfig.FIVE_LINK_TEST) {
            val permit = access.fresh()
            val rows = TestCatalog.draws()
            val summary = "${TestCatalog.links.size} 個測試連結 · 不讀取網站清單"
            db.withTransaction {
                matchingPermit(permit)
                dao.archive(false); dao.archive(true); dao.upsertDraws(rows)
                dao.meta(Metadata("lastSync", System.currentTimeMillis().toString()))
                dao.meta(Metadata("syncSummary", summary)); dao.meta(Metadata("syncError", ""))
                dao.meta(Metadata("sourceHash", digest(TestCatalog.links.toString())))
            }
            return@withLock summary
        }
        val catalog = selectedCatalog()
        check(expectedCatalog == null || expectedCatalog == catalog) { "清單來源已變更，請重新開始批次" }
        syncWebsite(catalog)
    }

    private suspend fun syncWebsite(catalog: DrawCatalog): String {
        val permit = access.fresh()
        try {
            require(online()) { "目前離線，顯示上次同步的清單" }
            val html = source.fetch(catalog)
            val now = System.currentTimeMillis()
            val parsed = withContext(Dispatchers.Default) { catalog.parse(html, now).map(catalog::scope) }
            // Resolve again on every sync; a short URL may point to a new campaign even when HTML is unchanged.
            val urls = coroutineScope {
                val permits = Semaphore(4)
                parsed.map { it.url }.distinct().map { url -> async {
                    permits.withPermit {
                        val resolved = try { withTimeout(15_000) { source.resolve(url) }.also { require(LinkPolicy.allowed(it)) } }
                        catch (e: TimeoutCancellationException) { null }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                        url to resolved
                    }
                } }.awaitAll().toMap()
            }
            val resolved = parsed.map { d -> urls[d.url]?.let { d.copy(activityKey=LinkPolicy.activityKey(it,d.store)) } ?: d }
            val old = dao.currentDraws(false).filter(catalog::owns)
            val newKeys = resolved.map { it.rowKey }.toSet()
            val oldByKey = old.associateBy { it.rowKey }
            val added = resolved.count { it.rowKey !in oldByKey }
            val removed = old.count { it.rowKey !in newKeys }
            val changed = resolved.count { row -> oldByKey[row.rowKey]?.let { it.copy(syncedAt=now) != row } == true }
            val unresolved = resolved.count { LinkPolicy.canonicalUrl(it.activityKey) == null }
            val summary = "${resolved.size} 筆・新增 $added・變更 $changed・封存 $removed" + if(unresolved>0) "・連結待解析 $unresolved" else ""
            db.withTransaction {
                matchingPermit(permit)
                old.map { it.rowKey }.chunked(900).forEach { dao.archiveRows(it) }
                dao.upsertDraws(resolved)
                dao.meta(Metadata(catalog.metaKey("lastSync"),now.toString()))
                dao.meta(Metadata(catalog.metaKey("sourceHash"),digest(html)))
                dao.meta(Metadata(catalog.metaKey("catalogVersion"),digest(resolved.map { "${it.rowKey}:${it.activityKey}" }.joinToString("|"))))
                dao.meta(Metadata(catalog.metaKey("syncSummary"),summary)); dao.meta(Metadata(catalog.metaKey("syncError"),""))
            }
            return summary
        } catch (e: Exception) {
            if (e is AccessDenied) throw e
            if (e is kotlinx.coroutines.CancellationException) throw e
            val reason = when (e) {
                is org.json.JSONException -> "來源資料格式異常，保留上次有效資料"
                is IllegalArgumentException, is IllegalStateException -> e.message ?: "來源格式異常"
                else -> "連線失敗，保留上次有效資料"
            }
            dao.meta(Metadata(catalog.metaKey("syncError"), reason))
            throw IllegalStateException(reason, e)
        }
    }

    /** The in-app real LINE test area has its own refresh time; website data is untouched. */
    suspend fun syncFiveLinks(): String = syncMutex.withLock {
        val permit = access.fresh()
        db.withTransaction {
            matchingPermit(permit)
            dao.upsertDraws(TestCatalog.draws())
            dao.meta(Metadata(TestCatalog.SYNC_KEY, System.currentTimeMillis().toString()))
        }
        "已載入${TestCatalog.links.size} 個測試連結"
    }

    suspend fun recover() = batchMutex.withLock {
        db.withTransaction {
            // Repair cached website schedules on upgrade without refetching hundreds of short links.
            // Keep row/activity identities, participation records, sync age and batch snapshots intact.
            if (!com.linedraw.app.BuildConfig.FIVE_LINK_TEST && dao.meta("scheduleParserVersion")?.value != "2") {
                val repaired = dao.currentDraws(false).filterNot(TestCatalog::isTestRow).mapNotNull { draw ->
                    val period = runCatching { DrawSchedule.parse(draw.timeLabel) }.getOrNull()
                    if (period?.start != null && (draw.startsAt != period.start || draw.endsAt != period.end))
                        draw.copy(startsAt=period.start, endsAt=period.end) else null
                }
                dao.upsertDraws(repaired)
                dao.meta(Metadata("scheduleParserVersion", "2"))
            }
            dao.pruneAttempts(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
            val batch = dao.activeBatch() ?: return@withTransaction
            if (batch.state == "RUNNING") {
                val item = dao.item(batch.id, batch.currentIndex)
                if (item != null && item.submitted) {
                    dao.saveItem(item.copy(state = "REVIEW", reason = "上次操作中斷，請核對 LINE 結果"))
                    dao.saveRecord(record(batch, item, Participation.REVIEW, "未提供", "程序中斷；不能確認結果"))
                }
                dao.saveBatch(batch.copy(state = "PAUSED", reason = "上次執行中斷，已暫停；不自動重送"))
            }
        }
    }

    suspend fun seedDemo() {
        access.current() ?: throw AccessDenied()
        require(com.linedraw.app.BuildConfig.DEBUG && !com.linedraw.app.BuildConfig.FIVE_LINK_TEST)
        val now = System.currentTimeMillis()
        val specs = listOf("friend" to "UX-03 魔導神杖", "already" to "BX-09 戰鬥通行證", "loss" to "CX-11 帝王威能", "captcha" to "驗證碼暫停測試")
        dao.upsertDraws(specs.mapIndexed { i, pair ->
            Draw("demo:${pair.first}", "demo:${pair.first}", "demo:${pair.first}", "LineDraw 測試店", "模擬資料", pair.second,
                "linedraw-fixture://${pair.first}", "模擬活動・不會操作 LINE", now - 3600_000, now + 86_400_000, i, demo = true)
        })
    }

    suspend fun start(keys: List<String>, profile: String, autoFriend: Boolean, demo: Boolean,
                      autoContinue: Boolean = true, cities: Set<String> = emptySet(), query: String = "",
                      fiveLinks: Boolean = com.linedraw.app.BuildConfig.FIVE_LINK_TEST,
                      statuses: Set<DrawStatus> = emptySet(), products: Set<String> = emptySet()): Batch {
        val permit = access.fresh()
        return batchMutex.withLock {
        db.withTransaction {
            matchingPermit(permit)
            check(dao.activeBatch() == null) { "先停止或完成目前批次" }
            val website = selectedCatalog()
            if (com.linedraw.app.BuildConfig.FIVE_LINK_TEST) require(!demo) { "測試清單版不使用模擬資料" }
            require(!com.linedraw.app.BuildConfig.FIVE_LINK_TEST || fiveLinks)
            require(!fiveLinks || !demo) { "抽選測試會操作 LINE，不是模擬模式" }
            if (!com.linedraw.app.BuildConfig.FIVE_LINK_TEST)
                require(TestCatalog.isTestProfile(profile) == fiveLinks) { "請從對應的清單開始抽選" }
            if (!demo) {
                check(online()) { "離線時不能開始抽選" }
                val synced = dao.meta(if (fiveLinks && !com.linedraw.app.BuildConfig.FIVE_LINK_TEST) TestCatalog.SYNC_KEY else website.metaKey("lastSync"))?.value?.toLongOrNull() ?: 0
                check(System.currentTimeMillis() - synced < 86_400_000) { "資料超過 24 小時，請先同步" }
            }
            val selected = dao.draws(keys)
            require(selected.size == keys.distinct().size && selected.isNotEmpty()) { "清單已更新，請重新選取" }
            require(selected.all { it.demo == demo && it.runnable() }) { "部分項目已不可執行" }
            require(if (fiveLinks) selected.all(TestCatalog::allowsDraw) else selected.none(TestCatalog::isTestRow)) {
                "網站清單與抽選測試不能混在同一批次"
            }
            if (!demo && !fiveLinks) require(selected.all(website::owns)) { "清單來源已變更，請重新選取活動" }
            val unique = selected.distinctBy { it.activityKey }
            require(unique.all { dao.record(profile, it.activityKey) == null }) { "部分項目已有紀錄，請重新選取" }
            val scope = CatalogFilter(cities,statuses,query,products)
            require(selected.all { scope.matches(it) }) { "篩選條件已變更，請重新選取清單" }
            val batch = Batch(UUID.randomUUID().toString(), profile, total = unique.size, autoFriend = autoFriend, demo = demo)
            dao.saveBatch(batch)
            dao.saveItems(unique.mapIndexed { i,d -> snapshot(batch,i,d) })
            val catalog = dao.currentDraws(demo).filter { TestCatalog.isTestRow(it) == fiveLinks && (demo || fiveLinks || website.owns(it)) }
            val continuation = Continuation(autoContinue && !demo && !fiveLinks,
                cities=cities.toSet(),query=query,seen=catalog.map { it.activityKey }.toSet(),
                unresolvedAtStart=catalog.filter { LinkPolicy.canonicalUrl(it.activityKey)==null }.map { it.url }.toSet(),statuses=statuses.toSet(),products=products.toSet(),catalog=website)
            dao.meta(Metadata("continuation:${batch.id}",continuation.encode()))
            matchingPermit(permit)
            batch
        }
        }
    }

    suspend fun pause(reason: String = "使用者暫停", stop: Boolean = false) = batchMutex.withLock {
        db.withTransaction {
            val b = dao.activeBatch() ?: return@withTransaction
            if (b.state == "PAUSED" && !stop) return@withTransaction
            val item = dao.item(b.id, b.currentIndex)
            if (item != null && item.submitted && item.state !in setOf("SUBMITTED", "COMPLETE", "ALREADY", "SKIPPED")) {
                dao.saveItem(item.copy(state = "REVIEW", reason = reason))
                dao.saveRecord(record(b, item, Participation.REVIEW, "未提供", "操作後暫停；結果待核對"))
            }
            dao.saveBatch(b.copy(state = if (stop) "STOPPED" else "PAUSED", reason = reason))
            trace(b.id, b.currentIndex, if (stop) "STOP" else "PAUSE", reason)
        }
    }

    suspend fun resume(skipCurrent: Boolean) {
        access.fresh()
        batchMutex.withLock {
        db.withTransaction {
            val b = dao.activeBatch() ?: error("沒有可恢復的批次")
            batchPermit(b.id)
            require(b.state == "PAUSED")
            val item = dao.item(b.id,b.currentIndex)
            if (item == null && b.currentIndex >= b.total) {
                dao.saveBatch(b.copy(state="RUNNING",reason="繼續檢查新增活動")); return@withTransaction
            }
            requireNotNull(item) { "找不到項目" }
            check(skipCurrent || (!item.submitted && item.state != "REVIEW")) { "本筆已有在途操作，請核對後略過；不重新送出" }
            if (skipCurrent) {
                if (item.state != "REVIEW") dao.saveItem(item.copy(state = "SKIPPED", reason = "使用者略過"))
                advance(b,b.currentIndex+1,"已略過目前項目")
            } else dao.saveBatch(b.copy(state = "RUNNING", reason = "繼續核對"))
        }
        }
    }

    suspend fun intent(batchId: String, position: Int, action: String): Long? = batchMutex.withLock {
        db.withTransaction {
            val b = dao.activeBatch() ?: return@withTransaction null
            if (b.id != batchId || b.state != "RUNNING" || b.currentIndex != position) return@withTransaction null
            val item = dao.item(batchId, position) ?: return@withTransaction null
            batchPermit(batchId)
            val submits = action in setOf("SUBMIT", "ADD_FRIEND_AND_SUBMIT")
            val addsFriend = action in setOf("ADD_FRIEND", "ADD_FRIEND_AND_SUBMIT")
            if ((submits && item.submitted) || (action == "ADD_FRIEND" && item.friendAttempted)) return@withTransaction null
            if (submits || addsFriend) {
                dao.saveItem(item.copy(state = "PROCESSING", stage = if (submits) "RESULT" else "FRIEND",
                    submitted = item.submitted || submits, friendAttempted = item.friendAttempted || addsFriend))
            }
            dao.addAttempt(Attempt(batchId = batchId, position = position, action = action))
        }
    }

    suspend fun finish(batchId: String, position: Int, status: Participation, result: String, evidence: String) = batchMutex.withLock {
        db.withTransaction {
            val b = dao.activeBatch() ?: return@withTransaction
            if (b.id != batchId || b.currentIndex != position || b.state != "RUNNING") return@withTransaction
            val item = dao.item(batchId, position) ?: return@withTransaction
            dao.saveRecord(record(b, item, status, result, evidence))
            dao.saveItem(item.copy(state = status.name, reason = evidence, updatedAt = System.currentTimeMillis()))
            val next = position + 1
            advance(b,next,"前往下一筆")
            trace(batchId,position,"ITEM_RESULT","status=${status.name} result=$result next=$next/${b.total}")
        }
    }

    /** Only before calling ACTION_CLICK/dispatchGesture. A dispatched or uncertain click is never withdrawn. */
    suspend fun cancelBeforeDispatch(attemptId: Long, previous: BatchItem) = batchMutex.withLock {
        db.withTransaction {
            val attempt=dao.attempt(attemptId) ?: return@withTransaction
            check(attempt.batchId==previous.batchId && attempt.position==previous.position && attempt.disposition=="INTENT")
            dao.attemptResult(attemptId,"CANCELLED_BEFORE_DISPATCH")
            val b=dao.activeBatch() ?: return@withTransaction
            if(b.id==previous.batchId && b.state=="RUNNING" && b.currentIndex==previous.position) {
                dao.saveItem(previous.copy(updatedAt=System.currentTimeMillis()))
            }
        }
    }

    suspend fun skipKnown(batchId: String, position: Int) = batchMutex.withLock {
        db.withTransaction {
            val b = dao.activeBatch() ?: return@withTransaction
            if (b.id != batchId || b.currentIndex != position || b.state != "RUNNING") return@withTransaction
            val item = dao.item(batchId, position) ?: return@withTransaction
            if (dao.record(b.profile, item.activityKey) == null) return@withTransaction
            dao.saveItem(item.copy(state = "SKIPPED", reason = "本機已有活動紀錄"))
            val next = position + 1
            advance(b,next,"略過重複活動")
        }
    }

    /** Resolve manual opens too; never hand a lin.ee redirect directly to the LINE package. */
    suspend fun manualOpenUrl(draw: Draw, fiveLinks: Boolean): String {
        val permit = access.fresh()
        require(!draw.demo && LinkPolicy.allowed(draw.url)) { "此活動不能在 LINE 開啟" }
        require(!fiveLinks || TestCatalog.allowsDraw(draw)) { "不在指定的測試活動範圍" }
        val resolved = try { withTimeout(15_000) { source.resolve(draw.url) } }
            catch (_: TimeoutCancellationException) { throw IllegalStateException("解析活動連結逾時，請確認網路後重試") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw IllegalStateException("暫時無法解析活動連結，請確認網路或重新同步後重試") }
        require(LinkPolicy.allowed(resolved)) { "活動連結不是允許的 LINE 網址" }
        val identity = LinkPolicy.activityKey(resolved, draw.store)
        require(LinkPolicy.canonicalUrl(identity) != null) { "尚未取得 LINE 活動連結，請重新同步後重試" }
        require(!fiveLinks || (TestCatalog.allowsUrl(resolved) && identity == draw.activityKey)) { "測試連結的活動已變更，請更新測試清單" }
        matchingPermit(permit)
        return resolved
    }

    suspend fun resolve(batch: Batch, item: BatchItem): BatchItem {
        if (batch.demo) return item
        if (batch.isFiveLinkTest()) require(TestCatalog.allowsItem(item)) { "不在指定的測試活動範圍" }
        val resolvedUrl = source.resolve(item.url)
        val canonical = LinkPolicy.activityKey(resolvedUrl, item.store)
        if (batch.isFiveLinkTest()) {
            require(canonical == item.activityKey && TestCatalog.allowsUrl(resolvedUrl)) { "測試短網址的活動 ID 已改變，已停止" }
        }
        return db.withTransaction {
            // Update only this URL. Another short URL with the old ID may still be the old campaign.
            dao.resolveUrl(item.url,canonical)
            val updated = item.copy(activityKey = canonical, url = resolvedUrl)
            dao.saveItem(updated)
            updated
        }
    }

    private suspend fun snapshot(b: Batch, position: Int, d: Draw) = BatchItem(b.id,position,d.activityKey,d.product,d.store,
        if(b.isFiveLinkTest()) d.url else LinkPolicy.canonicalUrl(d.activityKey) ?: d.url,d.startsAt,d.endsAt)

    private suspend fun continuation(b: Batch) = Continuation.decode(dao.meta("continuation:${b.id}")?.value)

    private suspend fun advance(b: Batch, next: Int, reason: String) {
        val checkUpdates = next >= b.total && continuation(b).enabled
        dao.saveBatch(b.copy(currentIndex=next,state=if(next>=b.total && !checkUpdates) "FINISHED" else "RUNNING",
            reason=if(checkUpdates) "本輪已送出，檢查新增活動" else if(next>=b.total) "本次批次已結束" else reason))
    }

    /** A load failure has no participation record, so a later user-started batch can retry it. */
    suspend fun skipUnsent(batchId: String, position: Int, reason: String, failed: Boolean = true) = batchMutex.withLock {
        db.withTransaction {
            val b=dao.activeBatch() ?: return@withTransaction
            if(b.id!=batchId || b.state!="RUNNING" || b.currentIndex!=position) return@withTransaction
            val item=dao.item(batchId,position) ?: return@withTransaction
            check(!item.submitted) { "已送出的活動不能當成載入失敗重試" }
            dao.saveItem(item.copy(state=if(failed) "LOAD_FAILED" else "SKIPPED",reason=reason,updatedAt=System.currentTimeMillis()))
            trace(batchId,position,if(failed) "LOAD_FAILED" else "SKIP_UNAVAILABLE",reason)
            advance(b,position+1,"前往下一筆")
        }
    }

    suspend fun unavailableReason(batch: Batch, item: BatchItem): String? {
        val catalog=continuation(batch).catalog
        val current=dao.currentDraws(batch.demo).filter { it.activityKey==item.activityKey && TestCatalog.isTestRow(it)==batch.isFiveLinkTest() &&
            (batch.demo || batch.isFiveLinkTest() || catalog.owns(it)) }
        if(current.isEmpty()) return "活動已下架或連結已更新，未送出"
        return if(current.none { it.runnable() }) "活動已截止或暫不符合可抽條件，未送出" else null
    }

    /** sync may finish after Pause/Stop; recheck ownership inside the append transaction. */
    suspend fun continueAfterQueue(batchId: String): Boolean = continuationMutex.withLock {
        val before=dao.activeBatch() ?: return@withLock false
        if(before.id!=batchId || before.state!="RUNNING" || before.currentIndex<before.total) return@withLock false
        if(before.isFiveLinkTest() || before.demo) return@withLock false
        if(!continuation(before).enabled) return@withLock false
        access.fresh()
        batchPermit(batchId)
        try { withTimeout(90_000) { sync(continuation(before).catalog) } }
        catch (e: CancellationException) { if(e !is TimeoutCancellationException) throw e
            finishContinuation(batchId,"本輪已結束；同步逾時，請稍後同步");return@withLock false }
        catch (e: AccessDenied) { throw e }
        catch (_: Exception) { finishContinuation(batchId,"本輪已結束；同步失敗，保留原清單");return@withLock false }
        batchMutex.withLock {
            db.withTransaction {
                val b=dao.activeBatch() ?: return@withTransaction false
                if(b.id!=batchId || b.state!="RUNNING" || b.currentIndex<b.total) return@withTransaction false
                batchPermit(batchId)
                val state=continuation(b)
                val candidates=dao.currentDraws(false).filter { d -> !TestCatalog.isTestRow(d) && d.runnable() && state.matches(d) &&
                    LinkPolicy.canonicalUrl(d.activityKey)!=null && d.activityKey !in state.seen && d.url !in state.unresolvedAtStart
                }.distinctBy { it.activityKey }.filter { dao.record(b.profile,it.activityKey)==null }
                if(candidates.isEmpty() || state.round>=Continuation.MAX_ROUNDS) {
                    dao.saveBatch(b.copy(state="FINISHED",reason=if(candidates.isEmpty()) "本次批次已結束，沒有新增可抽活動" else "已接續 3 輪；另有 ${candidates.size} 筆待抽"))
                    return@withTransaction false
                }
                batchPermit(batchId)
                dao.saveItems(candidates.mapIndexed { i,d -> snapshot(b,b.total+i,d) })
                dao.meta(Metadata("continuation:${b.id}",state.copy(round=state.round+1,seen=state.seen+candidates.map { it.activityKey }).encode()))
                dao.saveBatch(b.copy(total=b.total+candidates.size,reason="發現 ${candidates.size} 筆新增活動，接續第 ${state.round+1} 輪"))
                trace(b.id,b.currentIndex,"APPEND_QUEUE","round=${state.round+1} added=${candidates.size}")
                true
            }
        }
    }

    private suspend fun finishContinuation(batchId: String, reason: String) = batchMutex.withLock {
        val b=dao.activeBatch()
        if(b?.id==batchId && b.state=="RUNNING" && b.currentIndex>=b.total) dao.saveBatch(b.copy(state="FINISHED",reason=reason))
    }

    suspend fun manual(draw: Draw, profile: String) {
        val permit=access.fresh()
        batchMutex.withLock { db.withTransaction {
            matchingPermit(permit)
            if (!com.linedraw.app.BuildConfig.FIVE_LINK_TEST)
                require(TestCatalog.isTestRow(draw) == TestCatalog.isTestProfile(profile)) { "請使用對應清單的紀錄" }
            check(dao.activeBatch() == null) { "請先停止或完成目前批次，再修改紀錄" }
            val current=dao.draws(listOf(draw.rowKey)).singleOrNull()
            check(current!=null && current.activityKey==draw.activityKey) { "清單已更新，請重新開啟活動" }
            val old=dao.record(profile,draw.activityKey)
            check(old.canMarkCompletedManually()) { "已有完成紀錄" }
            dao.meta(Metadata(ManualCompletion.key(profile,draw.activityKey),ManualCompletion.encode(profile,draw.activityKey,old)))
            dao.saveRecord(Record(profile,draw.activityKey,current.product,current.store,"MANUAL",result=old?.result ?: "未提供",
                evidence="使用者手動標記已完成；不代表中獎或 LINE 結果驗證"))
            matchingPermit(permit)
        } }
    }

    suspend fun undoManual(profile: String, activityKey: String) {
        val permit=access.fresh()
        batchMutex.withLock { db.withTransaction {
            matchingPermit(permit)
            check(dao.activeBatch()==null) { "請先停止或完成目前批次，再修改紀錄" }
            val current=dao.record(profile,activityKey)
            check(current?.status=="MANUAL") { "此筆不是手動完成紀錄" }
            val key=ManualCompletion.key(profile,activityKey)
            val saved=dao.meta(key)
            val previous=if(saved!=null) ManualCompletion.decode(saved.value,profile,activityKey) else {
                // Pre-0.2.4 marks did not save their previous record. Never erase evidence of a prior submit.
                dao.lastSubmittedItem(profile,activityKey)?.let {
                    current.copy(status="REVIEW",result="未提供",evidence="已撤銷舊版手動完成；曾送出抽選，保留待確認避免重送",updatedAt=System.currentTimeMillis())
                }
            }
            if(previous==null) dao.undoManual(profile,activityKey) else dao.saveRecord(previous)
            dao.deleteMeta(key)
            matchingPermit(permit)
        } }
    }

    private fun record(b: Batch, i: BatchItem, s: Participation, result: String, evidence: String) =
        Record(b.profile, i.activityKey, i.product, i.store, s.name, result, evidence)

    /** Call with fixed state labels and counters only; never raw page text or account data. */
    suspend fun trace(batchId: String, position: Int, action: String, detail: String) {
        dao.addAttempt(Attempt(batchId = batchId, position = position, action = action, disposition = detail))
        android.util.Log.i("LineDraw", "#${position + 1} $action $detail")
    }

    suspend fun diagnostic(): String {
        val batch = dao.latestBatch()
        return buildString {
            appendLine("LineDraw ${com.linedraw.app.BuildConfig.VERSION_NAME} 診斷預覽")
            appendLine("Android API ${android.os.Build.VERSION.SDK_INT} / ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("只包含狀態與步驟，不包含聊天、帳號、網址或原始畫面。")
            appendLine("批次狀態：${batch?.state ?: "無"}；位置：${batch?.currentIndex ?: 0}/${batch?.total ?: 0}")
            appendLine("原因：${batch?.reason ?: "無"}")
            dao.attempts().forEach { appendLine("${it.at} | #${it.position + 1} | ${it.action} | ${it.disposition}") }
        }
    }
}

suspend fun resolveCoupon(input: String): String = withContext(Dispatchers.IO) {
    var url = input
    val deadline=System.nanoTime()+15_000_000_000L
    repeat(6) {
        require(LinkPolicy.allowed(url)) { "連結跳轉到未允許的網域" }
        if (URI(url).host == "liff.line.me" && Regex("^/[^/]+/c/[A-Za-z0-9_-]+$").matches(URI(url).path)) return@withContext url
        val c = URL(url).openConnection() as HttpURLConnection
        val remaining=((deadline-System.nanoTime())/1_000_000).toInt()
        check(remaining>0) { "連結解析逾時" }
        c.instanceFollowRedirects=false; c.connectTimeout=minOf(5_000,remaining); c.readTimeout=minOf(5_000,remaining)
        try {
            check(c.responseCode in 300..399) { "無法確認 LINE 優惠券活動身份" }
            val next = c.getHeaderField("Location") ?: error("缺少跳轉目的")
            url = URI(url).resolve(next).toString()
        } finally { c.disconnect() }
    }
    error("連結跳轉次數過多")
}

package com.linedraw.app.engine

import com.linedraw.app.usage.AccessDenied
import com.linedraw.app.usage.AccessPermit
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.linedraw.app.*
import com.linedraw.app.data.*
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class DrawAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var reference = WeakReference<DrawAccessibilityService>(null)
        val instance: DrawAccessibilityService? get() = reference.get()
        const val LINE_PACKAGE = LineLinkLauncher.PACKAGE
        const val FIXTURE_PACKAGE = "com.linedraw.fixture"
        @Volatile var lastFixturePage: Page? = null
            private set
    }
    private val app get() = application as LineDrawApp
    private val repo get() = app.repository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true
    private var generation = 0
    private var overlay: View? = null
    private var statusLabel: TextView? = null
    private var lastObservation = ""
    private var lastOpenKey = ""
    private var openedAt = 0L
    private var visibleItem: String? = null
    private var skipRequested: String? = null
    private var lastUnknown = ""
    private var unknownAt = 0L

    override fun onServiceConnected() {
        reference = WeakReference(this)
        // Deliberately do not resume a saved batch on service reconnection.
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* One serialized polling loop owns all dispatch. */ }
    override fun onInterrupt() { halt("無障礙服務中斷") }
    override fun onDestroy() {
        if (reference.get() === this) reference.clear()
        generation++; job?.cancel(); removeOverlay()
        app.scope.launch { app.ready.await(); repo.pause("無障礙服务已關閉") }
        scope.cancel(); super.onDestroy()
    }

    fun halt(reason: String = "使用者暫停", stop: Boolean = false) {
        generation++; job?.cancel(); job = null; removeOverlay(); lastOpenKey = ""
        app.scope.launch { app.ready.await(); repo.pause(reason, stop) }
    }

    fun kick() {
        if (job?.isActive == true) return
        val token=++generation
        lastOpenKey=""; lastObservation=""; skipRequested=null; visibleItem=null
        job=scope.launch {
            app.ready.await()
            var trackingKey=""
            var opened=false
            var seenExpected=false
            var load=LoadState()
            var transition=PageTransitionGuard(null)
            var issue=LocalIssue()
            try {
                while(isActive && token==generation) {
                    val batch=repo.dao.activeBatch() ?: break
                    if(batch.state!="RUNNING") break
                    ensureOverlay(batch)
                    try {
                    if (!awaitPermit(batch, token)) break
                    var item=repo.dao.item(batch.id,batch.currentIndex)
                    if(item==null) {
                        if(batch.currentIndex<batch.total) { repo.pause("找不到批次項目");break }
                        statusLabel?.text="LineDraw\n本輪完成，檢查新增活動"
                        if(!awaitNetwork(batch,token)) break
                        if(!repo.continueAfterQueue(batch.id)) break
                        trackingKey=""
                        continue
                    }
                    val key="${batch.id}:${item.position}"
                    val expected=if(batch.demo) FIXTURE_PACKAGE else LINE_PACKAGE
                    if(batch.isFiveLinkTest() && !TestCatalog.allowsItem(item)) { repo.pause("不在指定的測試活動範圍");break }
                    if(trackingKey!=key) {
                        if(item.submitted) {
                            repo.finish(batch.id,item.position,Participation.REVIEW,"未確認","已有在途抽選，不重送；接續下一筆")
                            continue
                        }
                        trackingKey=key; opened=false; seenExpected=false; load=LoadState(); load.track(SystemClock.elapsedRealtime()); issue=LocalIssue(); lastUnknown=""
                    }
                    visibleItem=key
                    if (skipRequested != null) {
                        val requested=skipRequested; skipRequested=null
                        if (requested==key) {
                            repo.skipUnsent(batch.id,item.position,"使用者略過此筆，未送出抽選",false)
                            continue
                        }
                    }
                    val now=SystemClock.elapsedRealtime()
                    if (load.totalRemaining(now)==0L) {
                        repo.skipUnsent(batch.id,item.position,"單筆處理超過 90 秒；未送出抽選，接續下一筆")
                        continue
                    }
                    val online=repo.batchOnline(batch.demo)
                    if(!load.network(online,now)) { repo.pause("等待網路超過 60 秒，批次已暫停");break }
                    if(!online) {
                        statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n等待網路 ${seconds(load.networkRemaining(now))} 秒・本筆 ${seconds(load.totalRemaining(now))} 秒"
                        observe(batch,item,"WAIT_NETWORK")
                        delay(300);continue
                    }
                    when(load.expiry(now)) {
                        LoadState.Expiry.REOPEN -> {
                            load.begin(now);opened=false;issue.clear()
                            repo.trace(batch.id,item.position,"REOPEN","attempt=2/2")
                        }
                        LoadState.Expiry.FAILED -> {
                            repo.skipUnsent(batch.id,item.position,"載入或操作辨識失敗：重開後仍無法處理；未送出抽選")
                            continue
                        }
                        LoadState.Expiry.NONE -> Unit
                    }
                    if(!opened) {
                        val unavailable=repo.unavailableReason(batch,item)
                        if(unavailable!=null) { repo.skipUnsent(batch.id,item.position,unavailable,false);continue }
                        if(load.attempt==0) load.begin(now)
                        statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n"+
                            if(load.attempt==1) "載入抽選頁面（最多 30 秒）" else "重新開啟 1/1（最多 30 秒）"
                        try {
                            item=withTimeout(load.remaining(SystemClock.elapsedRealtime())) { repo.resolve(batch,item) }
                        } catch(e: Exception) {
                            if(e is CancellationException && e !is TimeoutCancellationException) throw e
                            repo.trace(batch.id,item.position,"OPEN_ERROR","attempt=${load.attempt}/2")
                            load.failNow(SystemClock.elapsedRealtime());continue
                        }
                        if(repo.dao.record(batch.profile,item.activityKey)!=null) { repo.skipKnown(batch.id,item.position);continue }
                        // Bind this open to a fresh document, never to the preceding page's cached button.
                        transition=PageTransitionGuard(readPage()?.page)
                        if(token!=generation || !launchItem(batch,item,token)) break
                        opened=true;lastOpenKey=key;openedAt=SystemClock.elapsedRealtime();lastUnknown=""
                        delay(150);continue
                    }
                    statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n第 ${load.attempt}/2 次・等待 ${seconds(load.remaining(now))} 秒・本筆 ${seconds(load.totalRemaining(now))} 秒"
                    val observed=readPage()
                    if(observed==null) {
                        observe(batch,item,"WINDOW_UNAVAILABLE")
                        delay(200);continue
                    }
                    val page=observed.page
                    if(BuildConfig.DEBUG && batch.demo) lastFixturePage=page
                    val freshDocument=transition.accept(page)
                    if(page.packageName!=expected) {
                        if(page.packageName==packageNameForApp() && !seenExpected) { delay(200);continue }
                        if(lastUnknown!="OTHER_APP") {lastUnknown="OTHER_APP";unknownAt=SystemClock.elapsedRealtime()}
                        if(SystemClock.elapsedRealtime()-unknownAt>2000) {repo.pause("已離開預期的抽選 App");break}
                        delay(200);continue
                    }
                    lastUnknown=""
                    seenExpected=true
                    if(!freshDocument) { observe(batch,item,"WAIT_NEW_PAGE");delay(200);continue }
                    val decision=Rules.decide(page,item,batch.autoFriend,expected)
                    val allowed=CampaignGate.permits(page,item,expected,batch.demo,batch.isFiveLinkTest())
                    observe(batch,item,"app=EXPECTED nodes=${observed.nodes.size} buttons=${Rules.knownButtons(page).joinToString(",")} decision=${decision.javaClass.simpleName} gate=$allowed")
                    if(!allowed) {repo.pause("連結不在本次批次範圍");break}
                    fun localProblem(reason: String) {
                        statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n$reason・重讀後自動重開／略過"
                        if (issue.retryNow(SystemClock.elapsedRealtime())) load.failNow(SystemClock.elapsedRealtime())
                    }
                    when(decision) {
                        is Decision.Retry -> localProblem(decision.reason)
                        is Decision.Skip -> {
                            repo.skipUnsent(batch.id,item.position,decision.reason,false)
                            continue
                        }
                        is Decision.Pause -> {
                            if(decision.reason!="未知抽選畫面，請人工核對") {repo.pause(decision.reason);break}
                            statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n等待按鈕 ${seconds(load.remaining(now))} 秒・第 ${load.attempt}/2 次"
                        }
                        is Decision.Finish -> {
                            repo.finish(batch.id,item.position,decision.status,decision.result,decision.evidence)
                            continue
                        }
                        is Decision.Click -> {
                            val unavailable=repo.unavailableReason(batch,item)
                            if(unavailable!=null) {repo.skipUnsent(batch.id,item.position,unavailable,false);continue}
                            statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n自動${decision.label}"
                            val candidate=target(observed,decision.label)
                            if(candidate==null) {localProblem("操作按鈕不唯一或範圍異常");delay(200);continue}
                            if (moveOverlayAway(candidate)) {localProblem("正在避開浮動列");delay(100);continue}
                            val attemptId=repo.intent(batch.id,item.position,decision.action) ?: continue
                            if(token!=generation) {repo.cancelBeforeDispatch(attemptId,item);break}
                            // 先完成批次與聲明檢查，再核對最後畫面。
                            val permit = try { repo.batchPermit(batch.id) } catch (e: AccessDenied) {
                                repo.cancelBeforeDispatch(attemptId, item)
                                throw e
                            }
                            val fresh=readPage()
                            val chosen=fresh?.let { target(it,decision.label) }
                            val valid=fresh!=null && Rules.decide(fresh.page,item,batch.autoFriend,expected)==decision &&
                                CampaignGate.permits(fresh.page,item,expected,batch.demo,batch.isFiveLinkTest())
                            if(!valid || chosen==null || overlayCovers(chosen)) {
                                // No click API has been invoked. Keep waiting within this open's existing budget.
                                repo.cancelBeforeDispatch(attemptId,item)
                                observe(batch,item,"WAIT_STABLE_BUTTON")
                                delay(200);continue
                            }
                            val web=isWebTarget(chosen)
                            val outcome = try {
                                clickTarget(chosen, web, permit)
                            } catch (e: AccessDenied) {
                                repo.cancelBeforeDispatch(attemptId, item)
                                throw e
                            }
                            if (outcome==ClickOutcome.NOT_DISPATCHED) {
                                repo.cancelBeforeDispatch(attemptId,item)
                                repo.trace(batch.id,item.position,"CLICK_RESULT","action=${decision.action} outcome=NOT_DISPATCHED")
                                localProblem("點擊未派送")
                                delay(200);continue
                            }
                            repo.dao.attemptResult(attemptId,outcome.name)
                            repo.trace(batch.id,item.position,"CLICK_RESULT","action=${decision.action} method=${if(web) "GESTURE" else "ACTION_CLICK"} outcome=$outcome targets=1")
                            if (outcome==ClickOutcome.UNCERTAIN) {
                                repo.finish(batch.id,item.position,Participation.REVIEW,"未確認","點擊回報不明；不重送，接續下一筆")
                                continue
                            }
                            issue.clear()
                            if(decision.action in setOf("SUBMIT","ADD_FRIEND_AND_SUBMIT")) {
                                repo.finish(batch.id,item.position,Participation.SUBMITTED,"未讀取","抽選點擊已派送；立即前往下一筆，不等待結果")
                                continue
                            }
                            repo.dao.item(batch.id,item.position)?.let { repo.dao.saveItem(it.copy(stage="FRIEND_SENT")) }
                            load.restartAfterFriend(SystemClock.elapsedRealtime())
                        }
                        Decision.Reopen -> {
                            repo.dao.saveItem(item.copy(stage="FRIEND_CONFIRMED"))
                            transition=PageTransitionGuard(page)
                            if(!launchItem(batch,item,token)) break
                            openedAt=SystemClock.elapsedRealtime();load.restartAfterFriend(openedAt)
                        }
                        Decision.Wait -> statusLabel?.text="LineDraw ${item.position+1}/${batch.total}\n等待好友頁面 ${seconds(load.remaining(now))} 秒・第 ${load.attempt}/2 次"
                    }
                    delay(200)
                    } catch (e: AccessDenied) {
                        repo.pause(e.message ?: "請先同意使用聲明")
                        break
                    }
                }
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {repo.pause(if(e is IllegalStateException || e is IllegalArgumentException) e.message ?: "流程異常" else "開啟活動失敗；已暫停")}
            finally {
                visibleItem=null; skipRequested=null; removeOverlay()
                if(repo.dao.latestBatch()!=null) startActivity(Intent(this@DrawAccessibilityService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
    }

    private fun seconds(ms: Long) = (ms + 999) / 1000

    private suspend fun awaitPermit(batch: Batch, token: Int): Boolean {
        if (token != generation) return false
        val active = repo.dao.activeBatch()
        if (active?.id != batch.id || active.state != "RUNNING") return false
        repo.batchPermit(batch.id)
        return true
    }

    private suspend fun awaitNetwork(batch: Batch, token: Int): Boolean {
        val wait=LoadState()
        while(!repo.batchOnline(batch.demo)) {
            if(!awaitPermit(batch,token)) return false
            if(token!=generation || repo.dao.activeBatch()?.state!="RUNNING") return false
            if(!wait.network(false,SystemClock.elapsedRealtime())) {repo.pause("等待網路超過 60 秒，批次已暫停");return false}
            statusLabel?.text="LineDraw\n等待網路恢復（最多 60 秒）"
            delay(300)
        }
        return token==generation
    }

    private data class ObservedPage(val page: Page, val nodes: List<AccessibilityNodeInfo>, val bounds: Rect)
    private fun readPage(): ObservedPage? {
        val root=foregroundRoot() ?: return null
        val nodes=mutableListOf<AccessibilityNodeInfo>();collect(root,nodes)
        val texts=readableTexts(nodes)
        // LINE can disable the terminal button. Keep that label for recognition, never for clicking.
        val buttonNodes=nodes.filter {clickable(it)!=null || ((Rules.terminalButton(it.text?.toString()) || Rules.terminalButton(it.contentDescription?.toString())) &&
            (it.isClickable || it.className?.toString()?.endsWith("Button")==true))}
        val buttons=buttonNodes.flatMap {listOfNotNull(it.text?.toString(),it.contentDescription?.toString())}.toSet()
        val document="${root.windowId}:"+nodes.filter {it.className?.toString()=="android.webkit.WebView" || !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank()}.joinToString(",") {it.hashCode().toString()}
        val inputs=nodes.filter { it.isEditable || it.isPassword }.flatMap {
            listOfNotNull(it.text?.toString(),it.hintText?.toString(),it.contentDescription?.toString(),if(it.isPassword) "密碼" else null)
        }.toSet()
        val dialogTexts=nodes.filter { it.className?.toString()?.endsWith("Dialog")==true }.flatMap {
            val children=mutableListOf<AccessibilityNodeInfo>();collect(it,children);readableTexts(children)
        }.toSet()
        val bounds=Rect().also(root::getBoundsInScreen)
        val footerButtons=buttonNodes.filter { node ->
            val rect=Rect().also(node::getBoundsInScreen)
            TargetSelection.isFooter(ActionTarget("",rect.left,rect.top,rect.right,rect.bottom),bounds.top,bounds.bottom)
        }.flatMap {listOfNotNull(it.text?.toString(),it.contentDescription?.toString())}.toSet()
        val observed=ObservedPage(Page(root.packageName?.toString().orEmpty(),texts,buttons,document,inputs,dialogTexts,footerButtons=footerButtons),nodes,bounds)
        val candidates=actionTargets(observed)
        val preferred=TargetSelection.pick(candidates.map { it.second },bounds.top,bounds.bottom)?.let { candidates[it].second.label }
        return observed.copy(page=observed.page.copy(preferredAction=preferred))
    }
    private fun matches(node: AccessibilityNodeInfo,label: String)=Rules.labelMatches(node.text?.toString(),label) || Rules.labelMatches(node.contentDescription?.toString(),label)

    private fun packageNameForApp() = applicationContext.packageName
    private suspend fun observe(batch: Batch, item: BatchItem, summary: String) {
        val signature = "${batch.id}:${item.position}:$summary"
        if (lastObservation == signature) return
        lastObservation = signature
        repo.trace(batch.id, item.position, "PAGE", summary)
    }

    /** Touching our overlay must not make its own labels the page being inspected. */
    private fun foregroundRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.window?.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return active
        val applications = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.sortedByDescending { it.layer }
        return applications.firstOrNull { it.isActive || it.isFocused }?.root
    }

    private fun readableTexts(nodes: List<AccessibilityNodeInfo>): Set<String> = nodes.flatMap { node ->
        listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).flatMap { listOf(it) + it.lines() }
    }.map(Rules::normalize).filter { it.isNotBlank() }.toSet()
    private suspend fun launchItem(batch: Batch, item: BatchItem, token: Int): Boolean {
        if (batch.isFiveLinkTest()) require(!batch.demo && TestCatalog.allowsItem(item)) { "本測試批次只允許指定的測試活動" }
        val attempt = repo.intent(batch.id, item.position, "OPEN") ?: return false
        if (token != generation) return false
        lastObservation = ""
        val intent = if (batch.demo) {
            check(BuildConfig.DEBUG)
            Intent().setClassName(FIXTURE_PACKAGE, "$FIXTURE_PACKAGE.FixtureActivity")
                .setData(Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("scenario", Uri.parse(item.url).host).putExtra("product", item.product).putExtra("store", item.store)
        } else null
        val permit = repo.batchPermit(batch.id)
        repo.access.dispatch(permit) {
            check(token == generation)
            if (intent != null) startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else LineLinkLauncher.open(this@DrawAccessibilityService, item.url)
        }
        repo.dao.attemptResult(attempt, "DISPATCHED")
        repo.trace(batch.id,item.position,"OPEN_RESULT","DISPATCHED")
        return true
    }

    private fun collect(node: AccessibilityNodeInfo, output: MutableList<AccessibilityNodeInfo>) {
        var visited = 0
        fun visit(current: AccessibilityNodeInfo, depth: Int) {
            if (depth > 50 || visited >= 800) return
            visited++
            if (current.isVisibleToUser) output += current
            for (i in 0 until current.childCount) {
                if (visited >= 800) break
                current.getChild(i)?.let { visit(it, depth + 1) }
            }
        }
        visit(node, 0)
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(8) {
            if (current?.isEnabled == true && current?.isVisibleToUser == true &&
                (current?.isClickable == true || current?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK) == true)) return current
            current = current?.parent
        }
        return null
    }

    private fun isWebTarget(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(20) {
            if (current?.className?.toString() == "android.webkit.WebView") return true
            current = current?.parent
        }
        return false
    }

    private fun actionTargets(observed: ObservedPage): List<Pair<AccessibilityNodeInfo,ActionTarget>> =
        observed.nodes.flatMap { node -> Rules.actionLabels.filter { matches(node,it) }.mapNotNull { label ->
            clickable(node)?.let { target ->
                val r=Rect().also(target::getBoundsInScreen)
                target to ActionTarget(label,r.left,r.top,r.right,r.bottom)
            }
        } }.distinctBy { it.second }

    private fun target(observed: ObservedPage,label: String): AccessibilityNodeInfo? {
        val candidates=actionTargets(observed).filter { it.second.label==label }
        return TargetSelection.pick(candidates.map { it.second },observed.bounds.top,observed.bounds.bottom)?.let { candidates[it].first }
    }

    private fun overlayCovers(node: AccessibilityNodeInfo): Boolean {
        val bounds=Rect().also(node::getBoundsInScreen)
        val control=overlay?.takeIf { it.isShown } ?: return false
        val location=IntArray(2).also(control::getLocationOnScreen)
        return Rect(location[0],location[1],location[0]+control.width,location[1]+control.height)
            .contains(bounds.centerX(),bounds.centerY())
    }

    private fun moveOverlayAway(node: AccessibilityNodeInfo): Boolean {
        if(!overlayCovers(node)) return false
        val control=overlay ?: return false
        val bounds=Rect().also(node::getBoundsInScreen)
        val wm=getSystemService(WindowManager::class.java)
        val screen=wm.currentWindowMetrics.bounds
        val params=control.layoutParams as WindowManager.LayoutParams
        val margin=(16*resources.displayMetrics.density).toInt()
        params.x=0
        params.y=if(bounds.centerY()>screen.centerY()) margin else (screen.height()-control.height-margin*3).coerceAtLeast(margin)
        wm.updateViewLayout(control,params)
        return true // Re-read the page after layout; never tap using a pre-move target.
    }

    private enum class ClickOutcome { DISPATCHED, NOT_DISPATCHED, UNCERTAIN }
    /** Rejected input may be retried; accepted input with a missing/cancelled reply must never be replayed. */
    private suspend fun clickTarget(node: AccessibilityNodeInfo, webTarget: Boolean, permit: AccessPermit): ClickOutcome {
        val bounds=Rect().also(node::getBoundsInScreen)
        if(bounds.isEmpty || !node.isVisibleToUser || !node.isEnabled || overlayCovers(node)) return ClickOutcome.NOT_DISPATCHED
        if(!webTarget) return try {
            if(repo.access.dispatch(permit) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }) ClickOutcome.DISPATCHED else ClickOutcome.NOT_DISPATCHED
        } catch(e: AccessDenied) {throw e} catch(_: Exception) {ClickOutcome.UNCERTAIN}
        val path=Path().apply {moveTo(bounds.exactCenterX(),bounds.exactCenterY())}
        val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build()
        return withTimeoutOrNull(5000) {
            suspendCancellableCoroutine<ClickOutcome> { continuation ->
                try {
                    val accepted=repo.access.dispatch(permit) { dispatchGesture(gesture,object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {if(continuation.isActive) continuation.resume(ClickOutcome.DISPATCHED)}
                        override fun onCancelled(gestureDescription: GestureDescription?) {if(continuation.isActive) continuation.resume(ClickOutcome.UNCERTAIN)}
                    },null) }
                    if(!accepted && continuation.isActive) continuation.resume(ClickOutcome.NOT_DISPATCHED)
                } catch(e: AccessDenied) { continuation.resumeWith(Result.failure(e)) }
                catch(_: Exception) { if(continuation.isActive) continuation.resume(ClickOutcome.UNCERTAIN) }
            }
        } ?: ClickOutcome.UNCERTAIN
    }

    private fun ensureOverlay(batch: Batch) {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java)
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding((12*density).toInt(), (4*density).toInt(), (6*density).toInt(), (4*density).toInt())
            background = GradientDrawable().apply { setColor(Color.rgb(240,245,252)); cornerRadius = 30*density; setStroke(1, Color.WHITE) }
            elevation = 10*density
        }
        statusLabel = TextView(this).apply { text = "LineDraw  ${batch.currentIndex + 1}/${batch.total}"; setTextColor(Color.rgb(23,26,34)); textSize = 13f; contentDescription = "拖曳移動控制列" }
        layout.addView(statusLabel)
        val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        layout.addView(controls)
        controls.addView(Button(this).apply { text = "略過"; minWidth = (56*density).toInt(); setOnClickListener { skipRequested=visibleItem } })
        controls.addView(Button(this).apply { text = "暫停"; minWidth = (64*density).toInt(); setOnClickListener { halt() } })
        controls.addView(Button(this).apply { text = "停止"; minWidth = (64*density).toInt(); setOnClickListener { halt("使用者停止", true) } })
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (80*density).toInt()
            }
        var previousX = 0f; var previousY = 0f
        statusLabel?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { previousX = event.rawX; previousY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> { params.x += (event.rawX-previousX).toInt(); params.y += (event.rawY-previousY).toInt(); previousX = event.rawX; previousY = event.rawY; wm.updateViewLayout(layout, params); true }
                MotionEvent.ACTION_UP -> { statusLabel?.performClick(); true }
                else -> false
            }
        }
        wm.addView(layout, params); overlay = layout
    }
    private fun removeOverlay() { overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }; overlay = null; statusLabel = null }
}

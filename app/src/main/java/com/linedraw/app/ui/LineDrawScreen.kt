package com.linedraw.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.linedraw.app.*
import com.linedraw.app.data.*
import com.linedraw.app.engine.DrawAccessibilityService
import com.linedraw.app.engine.LineLinkLauncher
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Blue = Color(0xFF075DD8)
private val LocalOpaque = compositionLocalOf { false }
private val LocalDark = compositionLocalOf { false }
private fun date(value: Long?): String = value?.takeIf { it > 0 }?.let {
    DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei")).format(Instant.ofEpochMilli(it))
} ?: "尚未同步"

@Composable
fun LineDrawScreen(app: LineDrawApp, currentTimeMillis: () -> Long = System::currentTimeMillis) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(initialValue=currentTimeMillis(), lifecycle, currentTimeMillis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { value=currentTimeMillis(); delay(1_000) }
        }
    }
    val context = LocalContext.current
    val prefs = remember { app.getSharedPreferences("preferences", 0) }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "系統") ?: "系統") }
    var opaque by remember { mutableStateOf(prefs.getBoolean("opaque", false)) }
    var reducedMotion by remember { mutableStateOf(prefs.getBoolean("motion", false)) }
    var autoFriend by remember { mutableStateOf(prefs.getBoolean("autoFriend", true)) }
    var autoContinue by remember { mutableStateOf(prefs.getBoolean("autoContinue",true)) }
    var profile by remember { mutableStateOf(prefs.getString("profile", "我的紀錄") ?: "我的紀錄") }
    var fiveLinkArea by remember { mutableStateOf(!BuildConfig.FIVE_LINK_TEST && prefs.getBoolean("fiveLinkArea", false)) }
    val fiveLinks = BuildConfig.FIVE_LINK_TEST || fiveLinkArea
    var demo by remember { mutableStateOf(BuildConfig.DEBUG && !fiveLinks && prefs.getBoolean("demo", false)) }
    var profiles by remember { mutableStateOf(prefs.getStringSet("profiles", setOf("我的紀錄"))!!.toList().sorted()) }
    var serviceEnabled by remember { mutableStateOf(DrawAccessibilityService.instance != null) }
    val dark = appearance == "深色" || (appearance == "系統" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xFF9ABEFF), secondaryContainer = Color(0xFF253E61), onSecondaryContainer = Color(0xFFD4E4FF), background = Color(0xFF101116), surface = Color(0xFF1E2330), onSurface = Color(0xFFF5F7FC), onSurfaceVariant = Color(0xFFADB7C8))
        else lightColorScheme(primary = Blue, secondaryContainer = Color(0xFFE0EBFD), onSecondaryContainer = Blue, background = Color(0xFFF4F6FA), surface = Color.White, onSurface = Color(0xFF171A22), onSurfaceVariant = Color(0xFF596273))
    val repo = app.repository
    val draws by repo.dao.watchDraws().collectAsStateWithLifecycle(emptyList())
    val records by repo.dao.watchRecords().collectAsStateWithLifecycle(emptyList())
    val batch by repo.dao.watchBatch().collectAsStateWithLifecycle(null)
    val allItems by repo.dao.watchItems().collectAsStateWithLifecycle(emptyList())
    val metas by repo.dao.watchMetadata().collectAsStateWithLifecycle(emptyList())
    val metadata = metas.associate { it.key to it.value }
    val websiteCatalog = DrawCatalog.FUNBOX
    val activeProfile = if (demo) "__demo__" else if (fiveLinkArea) TestCatalog.profile(profile) else profile
    val profileRecords = records.filter { it.profile == activeProfile }
    val recordMap = profileRecords.associateBy { it.activityKey }
    val currentDraws = draws.filter { it.demo == demo &&
        if (fiveLinks) TestCatalog.allowsDraw(it) else !TestCatalog.isTestRow(it) && (demo || websiteCatalog.owns(it)) }
    val syncKey = if (fiveLinkArea) TestCatalog.SYNC_KEY else if (fiveLinks) "lastSync" else websiteCatalog.metaKey("lastSync")
    val readyDraws = currentDraws.filter { it.runnable(now) && it.activityKey !in recordMap }
    var tab by rememberSaveable { mutableStateOf("抽選") }
    var query by rememberSaveable { mutableStateOf("") }
    // New saveable identities avoid restoring a pre-0.2.2 single String as a List.
    var statuses by key("catalog-statuses-v2") { rememberSaveable { mutableStateOf(listOf<String>()) } }
    var cities by key("catalog-cities-v2") { rememberSaveable { mutableStateOf(listOf<String>()) } }
    val productPreferencesKey = "catalog-products-v1:" + when { demo -> "demo"; fiveLinks -> "tests"; else -> websiteCatalog.preferenceScope }
    var products by key(productPreferencesKey) { rememberSaveable {
        mutableStateOf(prefs.getStringSet(productPreferencesKey, emptySet()).orEmpty().sorted())
    } }
    val catalogFilter = CatalogFilter(cities.toSet(),statuses.map(DrawStatus::valueOf).toSet(),query,products.toSet())
    val filtered = currentDraws.filter { catalogFilter.matches(it,it.activityKey in recordMap,now) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var detail by remember { mutableStateOf<Draw?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var showManualConfirm by remember { mutableStateOf<Draw?>(null) }
    var showUndoManual by remember { mutableStateOf<Record?>(null) }
    var manualBusy by remember { mutableStateOf(false) }
    var openingLine by remember { mutableStateOf(false) }
    var showPermission by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showProducts by rememberSaveable { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val locked = batch?.state in listOf("RUNNING", "PAUSED")
    val drawListState = key(fiveLinks,websiteCatalog) { rememberLazyListState() }
    val historyListState = key(fiveLinks) { rememberLazyListState() }
    val settingsListState = key(fiveLinks) { rememberLazyListState() }
    fun selectProducts(value: Set<String>) {
        if (locked) return
        products = value.sorted(); selected = emptyList()
        prefs.edit().putStringSet(productPreferencesKey, value.toSet()).apply()
    }
    fun runTask(action: suspend () -> Unit) { scope.launch { try { app.ready.await(); action() } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "操作失敗，請重試") } } }
    suspend fun refreshCatalog() = if (fiveLinkArea) repo.syncFiveLinks() else repo.sync()
    fun sync() { if (!busy) { busy = true; runTask { try { val message = refreshCatalog(); snackbar.showSnackbar(message) } finally { busy = false } } } }
    fun switchTestArea(enter: Boolean) {
        if (busy || locked) return
        busy = true
        runTask {
            try {
                check(repo.dao.activeBatch() == null) { "請先停止或完成目前批次" }
                if (enter) repo.syncFiveLinks()
                check(repo.dao.activeBatch() == null) { "請先停止或完成目前批次" }
                fiveLinkArea = enter; demo = false
                prefs.edit().putBoolean("fiveLinkArea", enter).putBoolean("demo", false).apply()
                selected = emptyList(); query = ""; cities = emptyList(); statuses = emptyList(); tab = "抽選"
                detail = null; showConfirm = false; showProducts = false
            } finally { busy = false }
        }
    }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = DrawAccessibilityService.instance != null
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        app.ready.await()
        if (demo) repo.seedDemo()
        val initialSyncKey = if(fiveLinkArea) TestCatalog.SYNC_KEY else if(fiveLinks) "lastSync" else repo.selectedCatalog().metaKey("lastSync")
        if (fiveLinks || (!demo && (repo.dao.meta(initialSyncKey)?.value?.toLongOrNull() ?: 0) < System.currentTimeMillis() - 900_000)) {
            busy = true
            runCatching { refreshCatalog() }.onFailure { snackbar.showSnackbar(it.message ?: "同步失敗") }
            busy = false
        }
    }
    LaunchedEffect(filtered, readyDraws, profileRecords, demo, fiveLinks) { selected = selected.filter { key -> filtered.any { it.rowKey == key && it in readyDraws } } }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalDark provides dark, LocalOpaque provides opaque) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf((if (dark) Color(0xFF124575) else Color(0xFFDCEBFF)).copy(alpha = .9f), Color.Transparent), center = Offset(size.width*.1f,size.height*.1f), radius = size.width*.95f), radius=size.width*.95f, center=Offset(size.width*.1f,size.height*.1f))
                    drawCircle(Brush.radialGradient(listOf((if (dark) Color(0xFF423658) else Color(0xFFE6DDFC)).copy(alpha=.6f), Color.Transparent), center=Offset(size.width,size.height*.56f), radius=size.width*.7f), radius=size.width*.7f, center=Offset(size.width,size.height*.56f))
                }
                Scaffold(containerColor = Color.Transparent, contentColor = colors.onSurface, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                    Column(Modifier.navigationBarsPadding().padding(horizontal=16.dp).padding(bottom=8.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        if (tab == "抽選" && selected.isNotEmpty()) Glass {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("已選 ${selected.size} 筆", fontWeight=FontWeight.Bold); Text("按來源順序逐筆處理", style=MaterialTheme.typography.labelMedium, color=colors.onSurfaceVariant) }
                                Button(onClick={showConfirm=true}, enabled=!locked && !busy, modifier=Modifier.heightIn(min=48.dp).testTag("startBatch")) { Text("檢查並開始") }
                            }
                        }
                        Glass {
                            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement=Arrangement.SpaceEvenly) {
                                listOf("抽選" to "◈", "紀錄" to "◷", "設定" to "⚙").forEach { (label, symbol) ->
                                    val on = tab == label
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if(on) colors.primary.copy(alpha=.11f) else Color.Transparent)
                                        .clickable { tab=label }.padding(vertical=8.dp).semantics { contentDescription="$label 分頁"; this.selected=on }, horizontalAlignment=Alignment.CenterHorizontally) {
                                        Text(symbol, fontSize=22.sp, color=if(on) colors.primary else colors.onSurfaceVariant)
                                        Text(label, fontSize=12.sp, fontWeight=if(on) FontWeight.Bold else FontWeight.Normal, color=if(on) colors.primary else colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("mainList"), state=when(tab){"紀錄"->historyListState;"設定"->settingsListState;else->drawListState}, contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        item { Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("LINEDRAW", color=colors.primary, fontWeight=FontWeight.ExtraBold, fontSize=13.sp, letterSpacing=2.sp, modifier=Modifier.weight(1f))
                            Tag(if(demo) "模擬模式" else if(fiveLinks) "抽選測試 · $profile" else "本機 · $profile")
                        } }
                        item { Text(when(tab) { "抽選" -> "把時間，留給喜歡的事。"; "紀錄" -> "每次抽選，都有跡可循。"; else -> "依你的方式。" }, fontSize=30.sp, lineHeight=39.sp, fontWeight=FontWeight.Bold, letterSpacing=(-.8).sp) }
                        if (demo) item { Notice("目前使用模擬資料與獨立測試頁，不會操作 LINE。", "DEMO") }
                        if (fiveLinks) item { Notice("這裡會實際開啟 LINE，內含 ${TestCatalog.links.size} 個測試活動。已截止項目保留供查閱，不會加入自動抽選。開始後自動加入好友並抽選，送出後直接前往下一筆。測試紀錄與網站清單分開，不會接續網站新增活動；既有 LINE 抽選結果不會被重設。", "抽選測試區") }
                        if (fiveLinkArea) item { OutlinedButton(onClick={switchTestArea(false)}, enabled=!locked && !busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("exitFiveLinkArea")) { Text("返回網站抽選") } }
                        if (tab == "抽選") {
                            item { Glass { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) {
                                    Text("抽選總覽", fontSize=13.sp, color=colors.onSurfaceVariant)
                                    Text("${currentDraws.count { !it.archived }} 個機會", fontSize=28.sp, fontWeight=FontWeight.Bold)
                                }; TextButton(onClick=::sync, enabled=!busy, modifier=Modifier.heightIn(min=48.dp)) { Text(if(busy) "同步中…" else if(fiveLinks) "↻ 更新測試清單" else "↻ 同步") } }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                                    Metric("${currentDraws.filter { !it.archived }.map { it.store }.distinct().size}", "店家")
                                    Metric("${readyDraws.size}", "可以抽選")
                                    Metric("${profileRecords.count { it.status in setOf("SUBMITTED","COMPLETE","ALREADY","MANUAL") }}", "已有紀錄")
                                }
                                Text("${if(demo) "離線測試資料" else if(fiveLinks) "${TestCatalog.links.size} 個連結 · ${date(metadata[syncKey]?.toLongOrNull())}" else "${websiteCatalog.label} · 更新 ${date(metadata[syncKey]?.toLongOrNull())}"}", fontSize=12.sp, color=colors.onSurfaceVariant)
                            } } }

                            if (!demo && !fiveLinks && !metadata[websiteCatalog.metaKey("syncError")].isNullOrBlank()) item { Notice(metadata[websiteCatalog.metaKey("syncError")]!!, "同步未完成") }
                            if (!serviceEnabled) item { Glass { Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("準備好抽選輔助", fontWeight=FontWeight.SemiBold); Text("啟用後仍需由你開始批次", fontSize=13.sp, color=colors.onSurfaceVariant) }
                                TextButton(onClick={showPermission=true}) { Text("設定") }
                            } } }
                            if (batch != null && batch!!.profile == activeProfile) item { BatchCard(batch!!, allItems.filter { it.batchId == batch!!.id },
                                onPause={ DrawAccessibilityService.instance?.halt() ?: runTask { repo.pause() } },
                                onStop={ DrawAccessibilityService.instance?.halt("使用者停止", true) ?: runTask { repo.pause("使用者停止", true) } },
                                onResume={skip -> runTask { check(DrawAccessibilityService.instance != null) { "請先啟用抽選輔助服務" }; repo.resume(skip); DrawAccessibilityService.instance?.kick() } }) }
                            item { OutlinedTextField(query, {query=it;selected=emptyList()}, placeholder={Text("搜尋商品或店家")}, singleLine=true, modifier=Modifier.fillMaxWidth().testTag("search"), shape=RoundedCornerShape(20.dp)) }
                            item { Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("活動狀態 · 可多選", style=MaterialTheme.typography.labelLarge, color=colors.onSurfaceVariant)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected=statuses.isEmpty(), onClick={statuses=emptyList();selected=emptyList()}, label={Text("全部")}, modifier=Modifier.testTag("status:all"))
                                    DrawStatus.entries.forEach { status ->
                                        FilterChip(selected=status.name in statuses, onClick={statuses=if(status.name in statuses) statuses-status.name else statuses+status.name;selected=emptyList()},
                                            label={Text(status.label)}, modifier=Modifier.testTag("status:${status.name}"))
                                    }
                                }
                            } }
                            item { Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("地區 · 可多選", style=MaterialTheme.typography.labelLarge, color=colors.onSurfaceVariant)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected=cities.isEmpty(), onClick={cities=emptyList();selected=emptyList()}, label={Text("所有地區")}, modifier=Modifier.testTag("city:all"))
                                    currentDraws.map { it.city }.distinct().forEach { name ->
                                        FilterChip(selected=name in cities, onClick={cities=if(name in cities) cities-name else cities+name;selected=emptyList()},
                                            label={Text(name)}, modifier=Modifier.testTag("city:$name"))
                                    }
                                }
                            } }
                            item { Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("商品 · 可多選", style=MaterialTheme.typography.labelLarge, color=colors.onSurfaceVariant)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                                    FilterChip(selected=products.isEmpty(), onClick={selectProducts(emptySet())}, enabled=!locked,
                                        label={Text("全部商品")}, modifier=Modifier.testTag("product:all"))
                                    OutlinedButton(onClick={showProducts=true}, enabled=!locked, modifier=Modifier.testTag("chooseProducts")) {
                                        Text(if(products.isEmpty()) "選擇商品" else "已選 ${products.size} 種商品")
                                    }
                                }
                                if(products.isNotEmpty()) Text(products.joinToString("、", transform=ProductCatalog::label),
                                    style=MaterialTheme.typography.bodyMedium, color=colors.onSurface,
                                    modifier=Modifier.testTag("productFilterSummary"))
                            } }
                            item { Row(verticalAlignment=Alignment.CenterVertically) { Text("抽選清單 · ${filtered.size}", Modifier.weight(1f), fontWeight=FontWeight.Bold, fontSize=19.sp)
                                TextButton(onClick={selected=filtered.filter { it in readyDraws }.map { it.rowKey }}, enabled=!locked && filtered.any { it in readyDraws }) { Text("全選可抽選") } } }
                            if (filtered.isEmpty()) item { EmptyState(if(currentDraws.isEmpty()) "還沒有抽選資料" else "沒有符合條件的項目", if(currentDraws.isEmpty()) "點選同步，取得最新清單。" else "試試其他篩選條件。") }
                            filtered.groupBy { it.store }.forEach { (store, rows) ->
                                item("store:$store") { Column { Text(store, fontWeight=FontWeight.Bold, fontSize=17.sp); Text(rows.first().city, color=colors.onSurfaceVariant, fontSize=12.sp) } }
                                items(rows, key={it.rowKey}) { d -> DrawCard(d, recordMap[d.activityKey], d.rowKey in selected, !locked && d in readyDraws, now,
                                    onToggle={selected=if(d.rowKey in selected) selected-d.rowKey else selected+d.rowKey}, onDetail={detail=d},
                                    manualEnabled=!locked && !manualBusy, onManual={showManualConfirm=d}, onUndo={showUndoManual=recordMap[d.activityKey]}) }
                            }
                            if(currentDraws.isNotEmpty()) item { Text("抽選點擊送出後直接前往下一筆；「已送出」不代表中獎或未中獎。", fontSize=12.sp, color=colors.onSurfaceVariant, lineHeight=18.sp) }
                        } else if (tab == "紀錄") {
                            item { Text("${if(demo) "模擬紀錄" else if(fiveLinkArea) "抽選測試 · $profile" else profile} · ${profileRecords.size} 筆", color=colors.onSurfaceVariant) }
                            if(profileRecords.isEmpty()) item { EmptyState("從第一筆開始", "完成結果、待確認與手動標記會分開記錄。") }
                            items(profileRecords, key={it.activityKey}) { record -> CardBox {
                                Tag(runCatching { Participation.valueOf(record.status).label }.getOrDefault(record.status))
                                Text(record.product, fontSize=18.sp,fontWeight=FontWeight.Bold)
                                Text(record.store, color=colors.onSurfaceVariant)
                                Text("${record.result} · ${date(record.updatedAt)}",fontSize=13.sp)
                                Text(record.evidence,fontSize=13.sp,color=colors.onSurfaceVariant)
                                if(record.status=="MANUAL") TextButton(onClick={showUndoManual=record}, enabled=!locked && !manualBusy,
                                    modifier=Modifier.testTag("undoRecord:${record.activityKey}")) { Text("撤銷手動完成") }
                                else if(record.canMarkCompletedManually()) currentDraws.firstOrNull { it.activityKey==record.activityKey }?.let { draw ->
                                    TextButton(onClick={showManualConfirm=draw},enabled=!locked && !manualBusy,
                                        modifier=Modifier.testTag("manualRecord:${record.activityKey}")) { Text("標記已完成") }
                                }
                            } }
                        } else {
                            if (!BuildConfig.FIVE_LINK_TEST && !fiveLinkArea) item { CardBox {
                                Text("抽選測試區", fontWeight=FontWeight.Bold)
                                Text("內含 ${TestCatalog.links.size} 個測試活動，可選取仍在期間內的項目。清單與紀錄獨立，不會混入網站活動。",fontSize=13.sp,color=colors.onSurfaceVariant)
                                Button(onClick={switchTestArea(true)},enabled=!locked && !busy,
                                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("enterFiveLinkArea")) { Text("進入抽選測試") }
                                if (locked) Text("請先停止或完成目前批次，再切換測試區。",fontSize=12.sp,color=colors.onSurfaceVariant)
                            } }
                            item { SectionLabel("本機紀錄") }
                            item { CardBox {
                                Text("設定檔",fontWeight=FontWeight.Bold)
                                Text("設定檔不等於 LINE 帳號；更換 LINE 帳號時，請切換到另一份本機紀錄。",fontSize=13.sp,color=colors.onSurfaceVariant)
                                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    profiles.forEach { name -> FilterChip(selected=profile==name,onClick={if(!locked){profile=name;prefs.edit().putString("profile",name).apply();selected=emptyList()}},enabled=!locked,label={Text(name)}) }
                                }
                                TextButton(onClick={showProfile=true},enabled=!locked) { Text("＋ 新增設定檔") }
                            } }
                            item { SectionLabel("抽選輔助") }
                            item { CardBox {
                                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("無障礙服務",fontWeight=FontWeight.SemiBold); Text(if(serviceEnabled) "已啟用" else "尚未啟用",fontSize=13.sp,color=colors.onSurfaceVariant) }; TextButton(onClick={showPermission=true}) { Text("管理") } }
                                SettingToggle("自動加入店家好友", "僅處理本次所選活動的店家",autoFriend,!locked) {autoFriend=it;prefs.edit().putBoolean("autoFriend",it).apply()}
                                if(!fiveLinks) SettingToggle("自動接續新增活動", "本輪結束後同步；沿用商品、地區、活動狀態及搜尋條件，最多額外 3 輪",autoContinue,!locked) {autoContinue=it;prefs.edit().putBoolean("autoContinue",it).apply()}
                                Text("載入最多等 30 秒，再重開一次；仍失敗就略過，之後可重試。斷網最多等 60 秒。抽選送出後立即前往下一筆，不等結果。", fontSize=13.sp,color=colors.onSurfaceVariant)
                            } }
                            item { SectionLabel("LIQUID GLASS") }
                            item { CardBox {
                                Text("外觀",fontWeight=FontWeight.Bold)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("系統","淺色","深色").forEach { a -> FilterChip(selected=appearance==a,onClick={appearance=a;prefs.edit().putString("appearance",a).apply()},label={Text(a)}) } }
                                SettingToggle("減少透明效果", "提高面板實色與文字對比",opaque) {opaque=it;prefs.edit().putBoolean("opaque",it).apply()}
                                SettingToggle("減少動態效果", "介面採穩定靜態呈現",reducedMotion) {reducedMotion=it;prefs.edit().putBoolean("motion",it).apply()}
                            } }
                            item { SectionLabel("資料與診斷") }
                            item { CardBox {
                                Text("資料只保存在這台手機",fontWeight=FontWeight.Bold)
                                Text("未上傳聊天、LINE 帳號或抽選結果。操作診斷保留 30 天。",fontSize=13.sp,color=colors.onSurfaceVariant)
                                TextButton(onClick={runTask { diagnostic=repo.diagnostic() }}) { Text("預覽診斷紀錄") }
                                Text(if(fiveLinks) "來源：使用者指定的 ${TestCatalog.links.size} 個測試連結" else "來源：${websiteCatalog.label}（${websiteCatalog.url}）",fontSize=12.sp,color=colors.onSurfaceVariant)
                                Text("LineDraw ${BuildConfig.VERSION_NAME} · Android 12+",fontSize=12.sp,color=colors.onSurfaceVariant)
                            } }
                            item { SectionLabel("使用說明與授權") }
                            item { CardBox {
                                Text("免費提供・原始碼公開・限非商業使用", fontWeight=FontWeight.Bold)
                                LegalDocument.entries.forEach { document ->
                                    TextButton(onClick={legalDocument=document},modifier=Modifier.testTag("legal:${document.name}")) { Text(document.title) }
                                }
                            } }
                            if(BuildConfig.DEBUG && !fiveLinks) item { CardBox {
                                SettingToggle("模擬測試模式", "需要另外安裝 LineDraw 測試頁 APK",demo,!locked) { value -> runTask { if(value) repo.seedDemo(); demo=value; prefs.edit().putBoolean("demo",value).apply(); selected=emptyList();cities=emptyList();statuses=emptyList() } }
                            } }
                        }
                    }
                }
                legalDocument?.let { LegalDocumentDialog(it) { legalDocument = null } }
                if(showProducts) {
                    val otherFilters = catalogFilter.copy(products=emptySet())
                    val options = ProductCatalog.options(
                        currentDraws.filter { !it.archived || DrawStatus.ARCHIVED in catalogFilter.statuses },
                        currentDraws.filter { otherFilters.matches(it,it.activityKey in recordMap,now) },products.toSet())
                    ProductFilterDialog(options,products.toSet(),::selectProducts,onDismiss={showProducts=false})
                }
                detail?.let { draw -> DetailDialog(draw, recordMap[draw.activityKey], locked || manualBusy, now, openingLine,
                    onDismiss={detail=null}, onManual={showManualConfirm=draw}, onUndo={showUndoManual=recordMap[draw.activityKey]}, onOpen={if(!openingLine) {openingLine=true;runTask {try {
                        val resolvedUrl = repo.manualOpenUrl(draw, fiveLinks)
                        val permit = app.access.fresh()
                        app.access.dispatch(permit) {
                            LineLinkLauncher.open(context, resolvedUrl)
                        }
                        snackbar.showSnackbar("已開啟連結；尚未標記為完成")
                    } finally {openingLine=false} }}}) }
                if(showConfirm) AlertDialog(onDismissRequest={showConfirm=false}, title={Text("準備開始抽選")}, text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("本次 ${selected.size} 筆，按清單順序逐筆處理。")
                    if(!demo && !fiveLinks) Text("清單來源：${websiteCatalog.label}")
                    if(products.isNotEmpty()) Text("商品篩選：${products.joinToString("、", transform=ProductCatalog::label)}")
                    Text(if(autoFriend) "必要時會自動加入已選店家好友。" else "需要加入好友時會暫停。")
                    Text("保持手機解鎖並停留在抽選畫面。可以隨時使用浮動控制列暫停或停止。")
                    if(!demo && !fiveLinks && autoContinue) Text("本輪完成後同步同一來源，依開始時的商品、地區、活動狀態及搜尋條件接續新增活動，最多額外 3 輪。")
                    if(!demo) Text(if(fiveLinks) "依選取活動的清單順序自動執行，完成後結束；不核對店家或活動名稱。" else "信任本次清單連結，不核對店家、活動名稱或畫面 ID。",color=colors.onSurfaceVariant)
                }},confirmButton={Button(onClick={runTask {
                    check(DrawAccessibilityService.instance!=null) {"請先至設定啟用無障礙服務"}
                    if(!demo && System.currentTimeMillis()-(repo.dao.meta(syncKey)?.value?.toLongOrNull()?:0)>86_400_000) refreshCatalog()
                    repo.start(selected,activeProfile,autoFriend,demo,autoContinue,cities.toSet(),query,fiveLinks,catalogFilter.statuses,products.toSet());showConfirm=false;selected=emptyList();DrawAccessibilityService.instance?.kick()
                }}) {Text(if(demo) "開始模擬批次" else "開始本次抽選")}},dismissButton={TextButton(onClick={showConfirm=false}){Text("返回")}})
                showManualConfirm?.let { draw -> AlertDialog(onDismissRequest={if(!manualBusy) showManualConfirm=null},title={Text("標記為已完成？")},
                    text={Text("${draw.store}\n${draw.product}\n\n標記後不會再加入自動抽選。這是你的手動確認，不代表中獎；之後可以撤銷。")},
                    confirmButton={TextButton(enabled=!locked && !manualBusy,modifier=Modifier.testTag("confirmManual"),onClick={
                        if(!manualBusy) {manualBusy=true;runTask {try {
                            repo.manual(draw,activeProfile);showManualConfirm=null;detail=null
                            selected=selected-draw.rowKey
                        } finally {manualBusy=false} }}
                    }){Text(if(manualBusy) "儲存中…" else "標記已完成")}},
                    dismissButton={TextButton(enabled=!manualBusy,onClick={showManualConfirm=null}){Text("取消")}}) }
                showUndoManual?.let { record -> AlertDialog(onDismissRequest={if(!manualBusy) showUndoManual=null},title={Text("撤銷手動完成？")},
                    text={Text("${record.store}\n${record.product}\n\n會恢復標記前的狀態。若原本已有「已送出」或「待確認」紀錄，仍不會重新自動抽選。")},
                    confirmButton={TextButton(enabled=!locked && !manualBusy,modifier=Modifier.testTag("confirmUndoManual"),onClick={
                        if(!manualBusy) {manualBusy=true;runTask {try {
                            repo.undoManual(record.profile,record.activityKey);showUndoManual=null
                        } finally {manualBusy=false} }}
                    }){Text(if(manualBusy) "儲存中…" else "確認撤銷")}},
                    dismissButton={TextButton(enabled=!manualBusy,onClick={showUndoManual=null}){Text("取消")}}) }
                if(showPermission) AlertDialog(onDismissRequest={showPermission=false},title={Text("啟用抽選輔助")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("LineDraw 會在你開始批次後讀取 LINE 畫面，點擊加入店家好友及抽選按鈕。")
                    Text("不保存聊天內容；載入失敗會略過，登入或驗證碼會暫停。你可在 Android 設定隨時關閉。")
                    Text("APK 安裝後若服務被限制，請先在 App 資訊依系統提示允許受限制的設定，再回到此處啟用。",fontSize=13.sp)
                }},confirmButton={TextButton(onClick={showPermission=false;context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("前往 Android 設定")}},dismissButton={TextButton(onClick={showPermission=false}){Text("稍後")}})
                if(showProfile) {
                    var name by remember { mutableStateOf("") }
                    AlertDialog(onDismissRequest={showProfile=false},title={Text("新增本機設定檔")},text={OutlinedTextField(name,{name=it.take(30)},label={Text("名稱")},singleLine=true)},confirmButton={TextButton(onClick={val n=name.trim();if(n.isNotEmpty() && !n.startsWith("__") && !locked){profiles=(profiles+n).distinct();profile=n;prefs.edit().putStringSet("profiles",profiles.toSet()).putString("profile",n).apply();showProfile=false}},enabled=name.isNotBlank() && !name.trim().startsWith("__")){Text("建立")}},dismissButton={TextButton(onClick={showProfile=false}){Text("取消")}})
                }
                diagnostic?.let { content -> AlertDialog(onDismissRequest={diagnostic=null},title={Text("診斷紀錄預覽")},text={Text(content,Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),fontSize=12.sp)},confirmButton={TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,content),"匯出診斷"))}){Text("分享")}},dismissButton={Row{TextButton(onClick={runTask{repo.dao.clearAttempts();diagnostic=repo.diagnostic()}}){Text("清除診斷")};TextButton(onClick={diagnostic=null}){Text("關閉")}}}) }
            }
        }
    }
}

@Composable private fun Glass(content: @Composable () -> Unit) {
    val shape=RoundedCornerShape(28.dp)
    val dark=LocalDark.current
    val tint=if(dark) Color(0xFF263145) else Color.White
    Box(Modifier.shadow(12.dp,shape,ambientColor=Color(0xFF52769C).copy(alpha=.16f),spotColor=Color(0xFF52769C).copy(alpha=.12f)).clip(shape)
        .background(Brush.linearGradient(listOf(tint.copy(alpha=if(LocalOpaque.current) 1f else .92f),tint.copy(alpha=if(LocalOpaque.current) 1f else .64f))))
        .border(1.dp,Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark).23f else .95f),Color.White.copy(alpha=.12f))),shape)) {content()}
}
@Composable private fun CardBox(content: @Composable ColumnScope.() -> Unit) { Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)} }
@Composable private fun Metric(value:String,label:String) {Column {Text(value,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun SectionLabel(text:String) {Text(text,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun Tag(text:String) {Text(text,Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.08f)).padding(horizontal=12.dp,vertical=7.dp),color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.SemiBold)}
@Composable private fun Notice(text:String,title:String) {CardBox {Text(title,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(text,fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun EmptyState(title:String,body:String) {CardBox {Text("✧",fontSize=36.sp,color=MaterialTheme.colorScheme.primary);Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun SettingToggle(title:String,subtitle:String,value:Boolean,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) {Column(Modifier.weight(1f).padding(end=8.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(value,onCheckedChange=onChange,enabled=enabled,modifier=Modifier.semantics{contentDescription=title})}
}
@Composable private fun DrawCard(draw:Draw,record:Record?,selected:Boolean,enabled:Boolean,now:Long,onToggle:()->Unit,onDetail:()->Unit,
                                manualEnabled:Boolean,onManual:()->Unit,onUndo:()->Unit) {
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("draw:${draw.sourceId}").clickable(onClick=onDetail)) {
        Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(draw.product,fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=24.sp)
                Text(record?.let{runCatching{Participation.valueOf(it.status).label}.getOrDefault(it.status)} ?: draw.eligibilityLabel(now),color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                if(TestCatalog.isTestRow(draw)) Text("第 ${draw.ordinal+1} 筆 · ${draw.url}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(draw.startsAt!=null) "${date(draw.startsAt)} 開始" else "日期待確認",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(selected,onCheckedChange={onToggle()},enabled=enabled,modifier=Modifier.semantics{contentDescription="選取 ${draw.store} ${draw.product}"})
        }
        if(record?.status=="MANUAL") TextButton(onClick=onUndo,enabled=manualEnabled,
            modifier=Modifier.heightIn(min=48.dp).testTag("undo:${draw.sourceId}")) {Text("撤銷手動完成")}
        else if(record.canMarkCompletedManually()) TextButton(onClick=onManual,enabled=manualEnabled,
            modifier=Modifier.heightIn(min=48.dp).testTag("manual:${draw.sourceId}")) {Text("標記已完成")}
        }
    }
}
@Composable private fun BatchCard(batch:Batch,items:List<BatchItem>,onPause:()->Unit,onStop:()->Unit,onResume:(Boolean)->Unit) {
    Glass { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text(if(batch.demo) "模擬批次" else "本次任務",Modifier.weight(1f),fontWeight=FontWeight.Bold);Tag(when(batch.state){"RUNNING"->"進行中";"PAUSED"->"已暫停";"FINISHED"->"已結束";else->"已停止"})}
        val handled=items.count{it.state in setOf("SUBMITTED","COMPLETE","ALREADY","REVIEW","SKIPPED","LOAD_FAILED")}
        Text("已處理 $handled / ${batch.total}",fontSize=23.sp,fontWeight=FontWeight.Bold)
        LinearProgressIndicator(progress={handled.toFloat()/batch.total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
        Text("已送出 ${items.count{it.state=="SUBMITTED"}} · 完成 ${items.count{it.state=="COMPLETE"}} · 已抽過 ${items.count{it.state=="ALREADY"}} · 待確認 ${items.count{it.state=="REVIEW"}} · 載入失敗 ${items.count{it.state=="LOAD_FAILED"}} · 略過 ${items.count{it.state=="SKIPPED"}}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(batch.reason,fontSize=13.sp)
        val exceptions=items.filter { it.state in setOf("REVIEW","LOAD_FAILED","SKIPPED") }
        var showExceptions by remember(batch.id) { mutableStateOf(false) }
        if(exceptions.isNotEmpty()) {
            TextButton(onClick={showExceptions=!showExceptions}) { Text(if(showExceptions) "收合處理明細" else "查看略過／待確認原因（${exceptions.size}）") }
            if(showExceptions) exceptions.forEach { item ->
                Text("${item.position+1}. ${item.product}\n${item.reason}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items.firstOrNull{it.position==batch.currentIndex}?.let{Text(it.product,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(batch.state in setOf("RUNNING","PAUSED")) {
            Text("如需手動標記完成，請先停止目前批次。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(batch.state=="RUNNING") OutlinedButton(onClick=onPause){Text("暫停")}
                else { val current=items.firstOrNull{it.position==batch.currentIndex};if(current?.submitted!=true && current?.state!="REVIEW") OutlinedButton(onClick={onResume(false)}){Text("繼續")};OutlinedButton(onClick={onResume(true)}){Text("略過這筆")}}
                TextButton(onClick=onStop){Text("停止批次")}
            }
        }
    } }
}
@Composable private fun DetailDialog(draw:Draw,record:Record?,locked:Boolean,now:Long,openingLine:Boolean,onDismiss:()->Unit,onManual:()->Unit,onUndo:()->Unit,onOpen:()->Unit) {
    Dialog(onDismissRequest=onDismiss) {Surface(shape=RoundedCornerShape(28.dp)) {Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Tag(record?.let{runCatching{Participation.valueOf(it.status).label}.getOrDefault(it.status)} ?: draw.eligibilityLabel(now))
        Text(draw.product,fontSize=25.sp,fontWeight=FontWeight.Bold,lineHeight=32.sp)
        Text(draw.store,fontWeight=FontWeight.Bold)
        Text(draw.timeLabel.ifBlank{"來源未提供時間"},fontSize=14.sp)
        if(draw.endsAt==null) Text("來源未提供明確抽選截止時間，因此尚不列入自動批次。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(draw.url,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        record?.let{Text(it.evidence,fontSize=13.sp)}
        Button(onClick=onOpen,enabled=!locked && !draw.demo && !openingLine,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(openingLine) "正在開啟 LINE…" else "在 LINE 開啟")}
        if(record?.status=="MANUAL") OutlinedButton(onClick=onUndo,enabled=!locked,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("detailUndoManual")){Text("撤銷手動完成")}
        else if(record.canMarkCompletedManually()) OutlinedButton(onClick=onManual,enabled=!locked,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("detailManual")){Text("標記已完成")}
        TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)){Text("關閉")}
    } } }
}

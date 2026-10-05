package com.linedraw.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.webkit.WebView

/** Independent, offline fake provider. No LINE account and no real prize activity. */
class FixtureActivity : Activity() {
    private lateinit var column: LinearLayout
    private val prefs by lazy { getSharedPreferences("fixture", MODE_PRIVATE) }
    private var scenario = "friend"
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderIntent()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderIntent()
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    private fun renderIntent() {
        handler.removeCallbacksAndMessages(null)
        scenario = intent.getStringExtra("scenario") ?: "friend"
        val count = prefs.getInt("opens:$scenario", 0) + 1
        prefs.edit().putInt("opens:$scenario", count).apply()
        when {
            // Keep the preceding draw button visible while the next document is loading.
            scenario == "web-stale" -> handler.postDelayed({ show() }, 5_000)
            scenario == "web-slow" -> { loading(); handler.postDelayed({ show() }, 8_000) }
            scenario == "web-never" || (scenario == "web-retry" && count == 1) -> loading()
            else -> show()
        }
    }
    private fun loading() { setContentView(TextView(this).apply { text="載入中…"; gravity=Gravity.CENTER; textSize=28f }) }
    private fun show(message: String? = null) {
        if (scenario.startsWith("web-")) { showWebCoupon(); return }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,300,36,40); gravity = Gravity.CENTER_HORIZONTAL; setBackgroundColor(Color.rgb(244,246,250)) }
        setContentView(column)
        label("LINE 抽選流程測試替身", 17f)
        label("模擬畫面・不會參加真實活動", 14f)
        label(intent.getStringExtra("store") ?: "LineDraw 測試店", 24f)
        label(intent.getStringExtra("product") ?: "UX-03 魔導神杖", 24f)
        if (message != null) { label(message, 28f); return }
        when {
            scenario == "ended-period-text" -> label("抽獎期間已結束", 24f)
            scenario == "ended-period-disabled" -> column.addView(Button(this).apply { text="抽獎期間已結束"; isEnabled=false })
            scenario == "ended-disabled" -> column.addView(Button(this).apply { text = "已結束"; isEnabled = false })
            scenario == "captcha" -> label("請輸入驗證碼", 24f)
            scenario == "unknown" -> label("未知按鈕：繼續下一步", 24f)
            scenario == "already" -> label("您已參加過此抽選", 24f)
            prefs.getBoolean("submitted:$scenario", false) -> label("您已參加過此抽選", 24f)
            scenario == "friend" && !prefs.getBoolean("friend", false) -> button("加入好友") {
                prefs.edit().putBoolean("friend", true).apply(); show("已加入好友")
            }
            else -> button("參加抽選") {
                prefs.edit().putBoolean("submitted:$scenario", true).putInt("submissions", prefs.getInt("submissions",0)+1).apply()
                show(if (scenario == "loss") "很可惜，未中獎" else "恭喜中獎")
            }
        }
    }
    private fun showWebCoupon() {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        // Independent click telemetry for the offline fixture, not the automation engine.
        val pageScenario = scenario
        web.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface fun clicked(action: String) {
                if (action !in setOf("draw", "coupon", "coupon-list")) return
                val key = "clicks:$pageScenario:$action"
                prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).commit()
            }
        }, "Fixture")
        setContentView(web)
        val label = if (scenario == "web-combined") "加入好友並參加抽獎" else "抽選"
        val claimed = """<button style="width:100%;font-size:24px;padding:24px" onclick="Fixture.clicked('coupon');document.getElementById('result').textContent='ERROR: coupon was opened'">查看已領取的優惠券</button>"""
        val initial = when {
            scenario == "web-result-loss" -> "<p>很可惜，未中獎</p>"
            scenario == "web-result-thanks" -> "<p>銘謝惠顧</p>"
            scenario == "web-result-win" -> "<p>恭喜中獎</p>"
            scenario == "web-result-loss-actual" -> """<button disabled style="width:100%;font-size:24px;padding:24px">可惜...沒有抽中！</button>"""
            scenario.startsWith("web-result-win-use") -> """<button ${if(scenario.endsWith("disabled")) "disabled" else ""} style="width:100%;font-size:24px;padding:24px" onclick="Fixture.clicked('coupon');document.getElementById('result').textContent='ERROR: coupon was redeemed'">使用優惠券</button>"""
            scenario == "web-ended-period" -> """<button disabled style="width:100%;font-size:24px;padding:24px">抽獎期間已結束</button>"""
            scenario == "web-ended-notice" -> """<div role="alertdialog"><p>抽獎期間已結束！</p><button onclick="Fixture.clicked('coupon')">確認</button></div>"""
            scenario == "web-ended" -> """<button disabled style="width:100%;font-size:24px;padding:24px">已結束</button>"""
            scenario == "web-ambiguous" -> """<button style="width:45%;font-size:24px;padding:24px" onclick="document.body.textContent='ERROR: ambiguous clicked'">抽選</button><button style="width:45%;font-size:24px;padding:24px" onclick="document.body.textContent='ERROR: ambiguous clicked'">立即抽選</button>"""
            scenario == "web-result-copy" -> "<p>恭喜中獎</p><p>使用優惠券時可能提供個人資料，不需登入。</p>"
            scenario.startsWith("web-claimed") -> claimed
            scenario == "web-already" -> "您已參加過此抽選"
            else -> """<button style="width:100%;font-size:24px;padding:24px;background:#4169e1;color:white;border:0" onclick="submitDraw()">$label</button>"""
        }
        val resultScript = when (scenario) {
            // A visible image result whose words are absent from the accessibility tree.
            "web-opaque" -> """target.innerHTML='<canvas id="prize" width="360" height="120" aria-hidden="true"></canvas>';
                var ctx=document.getElementById('prize').getContext('2d');ctx.font='32px sans-serif';ctx.fillStyle='white';ctx.fillText('恭喜中獎',40,70);"""
            "web-stalled" -> """target.dataset.submitted='true';"""
            "web-combined" -> """target.textContent='恭喜中獎！';"""
            "web-win-claimed" -> """target.innerHTML='<p>恭喜中獎</p><p>優惠券說明可能提及個人資料</p><button>查看已領取的優惠券</button>';"""
            else -> """target.textContent='很可惜，未中獎';"""
        }
        web.loadDataWithBaseURL("https://fixture.invalid/", """
            <html lang="zh-Hant"><meta name="viewport" content="width=device-width,initial-scale=1">
            <body style="margin:0;padding:120px 16px 100px;font:20px sans-serif">
              <p>離線 WebView 測試，不會參加真實活動</p><h1>任意店家顯示名稱</h1><h2>任意活動顯示名稱</h2>
              <button style="padding:16px" onclick="Fixture.clicked('coupon-list')">查看我的優惠券</button>
              ${if(scenario == "web-header-use") "<button onclick=\"Fixture.clicked('coupon')\">使用優惠券</button>" else ""}
              ${if(scenario == "web-instructions") "<p>本活動不需登入或驗證碼，付款時出示優惠券，店家可能詢問個人資料。</p>" else ""}
              ${if(scenario == "web-footer") "<button onclick=\"document.body.textContent='ERROR: header clicked'\">加入好友</button>" else ""}
              ${if(scenario.startsWith("web-claimed")) "<p>優惠券使用說明：店家可能請您提供個人資料。</p>" else ""}
              <div id="result" style="position:fixed;bottom:0;left:0;right:0;background:#4169e1;color:white">
                $initial
              </div>
              <script>function submitDraw(){Fixture.clicked('draw');var target=document.getElementById('result');$resultScript}</script>
            </body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
    private fun label(value: String, size: Float) { column.addView(TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(23,26,34)); setPadding(8,22,8,22); gravity = Gravity.CENTER }) }
    private fun button(value: String, action: () -> Unit) { column.addView(Button(this).apply { text = value; textSize = 20f; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1,140)) }
}

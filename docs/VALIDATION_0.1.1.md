# 0.1.1-standalone 驗證紀錄

日期：2026-10-06。比較基底：0.1.0-standalone。此更新不涉及 VIP 版專案或其 APK 部署。

## 內容

- 維持 PolyForm Noncommercial 1.0.0 原文，新增 NOTICE；說明免費提供、原始碼公開、限非商業使用。
- 使用聲明版本從 2 改為 3，既有使用者升級後重新確認。按鈕改為「我已了解，開始使用」及「離開 App」。
- 聲明頁可閱讀授權與隱私說明；設定頁可重讀聲明、隱私與完整授權，文件都隨 APK 打包。
- README 補齊 VIP 與獨立版比較、下載 APK、Android Studio／SDK／JDK、編譯、正式簽署、USB 安裝、更新、日常抽選及常見問題。
- APK 為 versionCode 2，versionName 0.1.1-standalone，沿用 com.linedraw.standalone 與原簽署憑證。

## 驗證

- 196 項 JVM／Robolectric 單元測試通過，0 失敗、0 略過，包括舊版聲明升級後需重新確認及本機確認保存。
- Debug、Pilot 與未簽署 Release 建置成功；三種 APK 的 LICENSE、NOTICE、PRIVACY 資產逐位元比對均與倉庫文件一致。
- App 六段聲明與 docs/USAGE_DECLARATION.md 一致；文件本機連結及 Git diff 空白檢查通過。

Android 16 專用模擬器 LineDraw_Result_Headless_API36 完成 7 項 UI 測試，0 失敗、0 略過：

- 全文可捲動閱讀；未確認前可開啟完整授權與隱私文件，閱讀不會自行授予使用資格。
- 確認後直接進入清單，重開保留確認；離開 App 不刪除既有紀錄。
- 未確認時仍阻擋同步、開始及操作派送。
- 設定頁三份文件均可開啟與關閉，不會改變既有確認狀態。
- 搜尋／分頁、深色／透明偏好及自動接續開關原有流程通過。


## 發布 APK

- LineDraw-0.1.1-standalone.apk
- SHA-256：9e9722ef92db77f97eb8e6f303883518d8f6bb94d586e2248c5ea9891217162f
- 使用既有 Android debug 憑證簽署，供測試安裝；自行正式發布請依 README 使用自己的 release 金鑰。

本輪不執行真實 LINE 抽選；此次改動限於文件、聲明、授權閱讀介面及對應的打包／測試。原先的抽選引擎、來源解析與登入移除狀態未改動。GitHub 倉庫保持 private。

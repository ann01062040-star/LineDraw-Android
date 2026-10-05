# 獨立版 0.1.0 驗證紀錄

日期：2026-10-05。基底：原 Android `0.2.15-alpha`（versionCode 20）。本版本從 Android 工作目錄獨立複製，未修改原專案，也未使用原版的 VPS 發布流程。

## 變更範圍

- 唯一網站資料來源為 Funbox；移除另一網站解析器、JSON 測試快照、來源切換介面與 API URL。
- 移除整個會員登入模組、加密權杖／待完成登入資料、登入 UI、Custom Tabs 依賴、登入 App Links、會員維護工作、VIP 到期與重試流程。
- 使用聲明改為獨立工具說明。保留本機同意版本檢查及撤回後停止的保護，沒有以固定會員或假 VIP 代替驗證。
- 批次不再綁定網站會員／session；只檢查目前批次及本機聲明。保留中斷不重送、LINE 登入／驗證碼暫停、活動結束接續等規則。
- 使用 `com.linedraw.standalone`，可與 `com.linedraw.app` 並存；測試版為 `com.linedraw.standalone.pilot`。

## 驗證結果

| 檢查 | 結果 |
| --- | --- |
| JVM／Robolectric 單元測試 | 196 項通過，0 失敗／略過 |
| Debug 與 Pilot APK | 建置成功 |
| Release APK | 未簽署建置成功 |
| Debug／Pilot Android Lint | 0 error；各 62 項 warning，主要為沿用程式及依賴的建議 |
| Android 16 專用模擬器 | 13 項流程／UI 測試通過 |
| APK DEX 靜態檢查 | 無會員模組、權杖儲存類別、會員 API、移除的網站解析器／網域 |
| 合併 Manifest | 獨立 package，沒有網站登入 App Links 或 Custom Tabs 查詢 |
| 原專案檔案比對 | 複製時記錄的 Android 原始檔雜湊全部未變更 |

模擬器使用 `LineDraw_Result_Headless_API36`。測試包含：

1. 首次聲明、同意後直接進入清單、重開保留同意、拒絕退出且保留紀錄。
2. 同意被撤回時，不可同步、開始批次或派送既有操作。
3. 抽選／紀錄／設定分頁，搜尋、深色與減少透明效果、自動接續開關。
4. 正式清單與測試區隔離、獨立 package、測試紀錄隔離、執行中重建 Activity。
5. 「抽獎期間已結束」於原生內文、停用按鈕、WebView 及提示框出現時，連續跳過四筆後，第五筆只點擊一次抽選。
6. 加好友後抽選、已抽過接續；驗證碼在任何送出前暫停。

單元測試另驗證：只靠已保存的本機聲明即可同步、開始、暫停、重建 Repository、恢復及送出；撤回再同意不會讓舊操作憑證恢復；未同意時不會發出來源請求；不接受其他來源識別碼。

## APK

- 檔名：`LineDraw-0.1.0-standalone.apk`
- package：`com.linedraw.standalone`
- versionName：`0.1.0-standalone`
- versionCode：`1`
- SHA-256：`f747ba8fc19d270b7e9578e6c5a019d372952b693a4c622d9339aa1ab32a2780`

發布的 APK 使用本機 Android debug 憑證簽署，供直接安裝測試；正式自行發布請依 README 建立並保存自己的簽署金鑰。憑證私鑰不在倉庫內。

本輪未連接實體手機、未送出真實 LINE 抽選，也未執行即時 Funbox 網路測試。來源解析保留現有 HTML 快照回歸測試；模擬器結果不代表所有 LINE／手機版本的相容性。

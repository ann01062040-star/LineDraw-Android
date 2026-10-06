# 0.1.2-standalone 驗證紀錄

日期：2026-10-06。比較基底：0.1.1-standalone。

## 變更

- 使用須知改為六個分段：免費使用、主動開始、結果判讀、資料與隱私、維護範圍及授權。強調無需網站帳號／VIP，「已送出」不代表中獎。
- 須知版本由 3 升至 4，升級後須重新確認一次；按鈕維持「我已了解，開始使用」與「離開 App」。
- 首頁及須知頁標示「LineDraw 獨立版」。須知頁與設定頁新增離線使用教學，保留隱私及完整授權入口。
- README 新增獨立的無障礙啟用章節，包含系統授權、受限制設定、啟用確認、關閉方式與常見問題，並區分 App 使用確認與 Android 系統權限。
- 版本為 0.1.2-standalone，versionCode 3，沿用 com.linedraw.standalone 與既有簽章。抽選引擎僅更新使用須知相關提示文字，未改變派送或辨識邏輯。

## 驗證結果

- 196 項 JVM／Robolectric 測試通過，0 失敗、0 略過，包含舊聲明版本重新確認與本機保存。
- Debug、Pilot、未簽署 Release 建置成功。Debug／Pilot lint 各 0 Error、0 Fatal，仍有 62 項 Warning；本次未擴大處理無關警告。
- Android 16 專用模擬器 LineDraw_Result_Headless_API36 的 UsageConsentUiTest 與 AppUiTest 共 7 項通過，0 失敗、0 略過。
- 確認前可閱讀全部須知、教學、隱私與授權，閱讀不會自動同意；確認後進入清單、重開保留確認，離開 App 保留既有抽選紀錄。
- 設定頁四個閱讀入口可開啟／關閉；既有搜尋、分頁、外觀與接續開關測試通過。
- 三種 APK 中 LICENSE、NOTICE、PRIVACY、QUICK_START 資產均與倉庫文件逐位元一致；六段須知與文件一致。
- 原始碼及三種 APK 解壓內容完成提交身分字串檢查，未含不應公開的本機帳號／姓名。

## 發布 APK

- 檔案：LineDraw-0.1.2-standalone.apk
- SHA-256：26af917e55f14b3805ac8e300033ce1fea2e7639c224fe973bb8a595b1887b55
- 簽章：既有 Android debug 測試憑證，可覆蓋此倉庫先前的測試 APK。正式自行簽署請依 README 教學操作。

本輪驗證使用模擬器，未執行真實 LINE 抽選，也未逐一驗證不同品牌的系統授權選單。Android 受限制設定說明已核對 Google 官方文件，實際選單以使用者裝置為準。GitHub 倉庫保持 private。

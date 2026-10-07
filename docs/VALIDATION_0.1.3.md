# 0.1.3-standalone：Funbox 缺少來源 ID 時仍可同步

日期：2026-10-08。版本 `0.1.3-standalone`，versionCode 4；套件維持 `com.linedraw.standalone`。

## 原因與修正

本次取得的 [uxux11 公開清單](https://uxux11.github.io/funbox-line/) 有 40 間店、431 筆抽選。其中「來玩聚-楠梓家樂福」9 筆有有效 `data-draw-href`，但沒有 `data-draw-id`。舊解析器把 ID 視為必要欄位，拋出「部分抽選列缺漏身份或網址」，使整份清單無法更新。

- 保留來源提供的非空白 ID；缺少或空白 ID 時，以店家、網址、商品及期間產生穩定識別，不依賴清單順序或同步時間。
- 參加紀錄繼續使用既有活動識別規則；來源移除或補回 ID 不會清除已抽紀錄。
- 同一網址的不同商品仍保留各自列，活動去重維持原規則。
- 缺少／無效網址、重複 ID、殘缺活動資料仍拒絕同步，保留前次有效清單。
- 日期判定、批次操作、來源範圍、使用須知與資料保存方式沒有改變。隱私文件只更新適用版號。

## 驗證

- 保存只含資料欄位的 2026-10-08 清單快照；先在舊解析器重現失敗，再確認新版完整解析 431 筆，包括缺 ID 的 9 筆。
- JVM／Robolectric 共 206 項通過，0 失敗、0 略過。新增 10 項涵蓋排序與插入穩定性、來源補回 ID、共用網址、重用短網址的新期間、無效連結與重複資料。
- Room／Repository 測試確認 `SUBMITTED`、`COMPLETE`、`ALREADY`、`MANUAL`、`REVIEW` 五種既有紀錄在再次同步後保留，不能再次加入批次；壞資料不覆蓋快取。
- Android 16 專用模擬器，使用實際 Android 網路堆疊抓取公開 Funbox 頁面：1 項測試通過、解析 431 筆、列識別唯一、網址全數符合既有規則。
- 線上頁面 SHA-256：`921653f6c9e947cdc64ff5cd7a4c201e283c2b0a4ecb1232c25736b92e186983`；抓取時間 `2026-10-07T17:40:52Z`（台北 10/08 01:40）。未來來源清單可能變動，固定 431 筆只用於保存的快照測試。
- Debug、Pilot 建置成功；兩者 lint 各 0 Error／Fatal，62 項 Warning。本次未擴大處理無關警告。
- APK 內離線授權、隱私與使用教學和倉庫檔案一致；發布 APK 已檢查不含不應公開的本機帳號／姓名。

本輪沒有操作真實 LINE、送出抽選或重測各品牌手機；線上驗證範圍是抓取與解析，紀錄保留以本機 Room 測試驗證。

## 發布

- APK：`LineDraw-0.1.3-standalone.apk`
- SHA-256：`720bd98037ba593b4126ea265f71b6f69612e017e8fe183f9db25e3a9ddde714`
- 沿用本倉庫既有 APK 簽章，支援覆蓋安裝。不要先移除舊 App，避免刪除本機紀錄。
- [公開倉庫](https://github.com/beybladehunter/LineDraw-Android)；[本版下載](https://github.com/beybladehunter/LineDraw-Android/releases/tag/v0.1.3-standalone)。
- 授權維持 PolyForm Noncommercial 1.0.0；免費使用、原始碼公開、限非商業用途。

# LineDraw Android 獨立版

從 LineDraw Android `0.2.15-alpha` 分離出的獨立版本，只同步 [Funbox 公開抽選清單](https://uxux11.github.io/funbox-line/)。首次同意使用聲明後即可使用，不需要網站帳號或 VIP。

目前版本：`0.1.0-standalone`，Android 12 以上。

## 與原版的差異

| 項目 | 此獨立版 |
| --- | --- |
| 網站清單來源 | 僅 Funbox，沒有其他網站來源切換 |
| 登入與 VIP | 已移除登入頁、會員 API、權杖儲存、資格檢查、驗證重試及登入回跳連結 |
| 本機使用聲明 | 首次使用及聲明更新時確認；不同意就退出 |
| 抽選流程 | 保留同步、商品／地區／活動狀態多選、自動加入好友、批次抽選、自動接續、紀錄與手動標記 |
| 測試清單 | 保留原版的測試區與指定連結，是否有效以各活動頁為準 |
| App 名稱 | LineDraw 獨立版 |
| 安裝識別碼 | `com.linedraw.standalone` |
| 原版資料 | 使用獨立儲存空間，可並存；不自動匯入原版資料或覆蓋原版 |

本倉庫只包含 Android App、模擬測試頁及測試程式。沒有網站後端、VPS 部署工具、部署密鑰或 iOS 專案。此版本不使用原 VIP 版的 APK 部署端點。

## 安裝與開始使用

1. 在此私有倉庫的 **Releases** 下載 `LineDraw-0.1.0-standalone.apk`；需有倉庫存取權。
2. 將 APK 傳至 Android 手機，開啟檔案，依系統提示允許該來源安裝。
3. 開啟「LineDraw 獨立版」，閱讀並同意使用聲明。
4. 確認手機已安裝 LINE，且已登入你要抽選的 LINE 帳號。此 App 不會代替 LINE 登入。
5. 在 App「設定 → 無障礙服務 → 管理」啟用「LineDraw 獨立版抽選輔助」。若系統提示受限制的設定，先依手機的 App 資訊頁解除限制，再返回啟用。
6. 回到抽選頁按「同步」。用商品、地區、活動狀態及搜尋篩選，選取要抽的活動後開始批次。
7. 執行時保持手機解鎖與網路連線。浮動控制列可暫停、停止或略過目前項目。
8. 完成後可在「紀錄」查看；送出狀態不代表中獎。更換 LINE 帳號時，請切換另一份本機紀錄設定檔。

抽選頁顯示「已結束」或「抽獎期間已結束」會直接略過；已有領取／抽過的結果會記錄並接續。需要 LINE 登入、驗證碼或其他真正需要本人處理的畫面，仍會暫停。這些畫面規則與本 App 已移除的網站會員驗證不同。

## 開發環境

- [Android Studio](https://developer.android.com/studio) 與 Android SDK。
- `compileSdk 37`、`targetSdk 36`、`minSdk 31`。
- Gradle Wrapper `9.5.0`、Android Gradle Plugin `9.3.0`。
- 本倉庫 Gradle Daemon 使用 JDK 25，設定位於 `gradle/gradle-daemon-jvm.properties`。若電腦沒有此版本，Gradle 會依其中工具鏈設定下載；需要網路。
- Kotlin／Java 目標版本 17；依賴版本以 Gradle 檔案為準。

首次使用時先透過 GitHub CLI 或 Git 登入有此私有倉庫權限的帳號，再複製專案：

```bash
gh repo clone beybladehunter/LineDraw-Android
cd LineDraw-Android
```

用 Android Studio 開啟專案根目錄，依提示安裝 SDK 與同步 Gradle。Android Studio 會產生本機 `local.properties`；請勿提交此檔。

純命令列使用者可在 `local.properties` 設定 `sdk.dir` 為自己的 SDK 絕對路徑。macOS 的常見位置是 `/Users/你的帳號/Library/Android/sdk`。

## 編譯與簽署

```bash
./gradlew :app:assembleDebug
```

輸出：`app/build/outputs/apk/debug/app-debug.apk`。預設 `debug` 是完整 Funbox 獨立版，Android 工具會以本機 debug 憑證簽署，可直接安裝。

```bash
adb -s 裝置序號 install -r app/build/outputs/apk/debug/app-debug.apk
```

更新同一份安裝需要相同的簽署憑證。不同電腦各自產生的 debug 憑證通常不同；請保存自己的憑證，或使用同一發布來源的 APK。不要為了解決簽章不符而直接移除有重要紀錄的 App，移除會刪除其本機資料。

要正式簽署時，在 Android Studio 選擇 **Build → Generate Signed App Bundle or APK → APK**，建立或選取自己的 keystore，選擇 `release`。將金鑰與密碼留在專案外，勿提交到 GitHub。命令列 `:app:assembleRelease` 會產生尚未簽署的 APK，必須簽署後才能安裝。

可選的 `pilot` 建置只載入測試清單，識別碼為 `com.linedraw.standalone.pilot`：

```bash
./gradlew :app:assemblePilot
```

## 測試

單元測試與靜態檢查：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintPilot
```

模擬器流程測試使用 `fixture` App，不會抽真實 LINE 活動。先建立名稱以 `LineDraw_` 開頭的專用 Android 16 模擬器並啟動，再執行：

```bash
ANDROID_SERIAL=emulator-5554 ./scripts/test-emulator.sh
```

腳本會安裝 fixture，清除專用模擬器內的測試頁資料並重設無障礙服務，僅限可拋棄的測試模擬器。它會拒絕實體手機及非專用 AVD。預設執行聲明、主要介面、測試區、已結束接續、加入好友及驗證碼暫停等流程。

`CatalogNetworkTest` 需明確指定 `-Pandroid.testInstrumentationRunnerArguments.linedraw.liveCatalog=true` 才會讀取即時 Funbox 頁面；其餘測試使用保存的 HTML 或本機 fixture。驗證紀錄見 [docs/VALIDATION.md](docs/VALIDATION.md)。

## 原始碼導覽

- `app/src/main/java/com/linedraw/app/data/`：Funbox 解析、同步、資料庫、篩選、紀錄與佇列。
- `app/src/main/java/com/linedraw/app/engine/`：無障礙操作、畫面判斷、重試及 LINE 連結開啟。
- `app/src/main/java/com/linedraw/app/usage/`：只保留本機使用聲明同意檢查，不做網路身分驗證。
- `app/src/main/java/com/linedraw/app/ui/`：液態玻璃風格 Compose 介面。
- `fixture/`：原生／WebView 抽選流程測試頁。

## 授權

沿用 PolyForm Noncommercial 1.0.0，詳見 [LICENSE](LICENSE)。此 GitHub 倉庫保持 private，僅開放給受邀協作者。

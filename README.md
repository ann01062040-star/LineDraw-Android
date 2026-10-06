# LineDraw Android 獨立版

**免費提供、原始碼公開、限非商業使用。** 此版本只同步 [Funbox 公開抽選清單](https://uxux11.github.io/funbox-line/)，不需要陀螺獵人網站帳號或 VIP。首次閱讀並確認使用須知後，即可使用抽選輔助功能。

目前版本：**0.1.2-standalone**，支援 Android 12（API 31）以上。專案採 [PolyForm Noncommercial 1.0.0](LICENSE)，可依條款進行非商業使用、修改及散布；商業用途須另行取得授權。

本文中的「獨立版／原始碼公開版」就是原先討論的免費「開源版」。由於保留非商業限制，本專案不是 OSI 定義的開源授權專案，對外統一使用上面的三項說明。[OSI 開源定義](https://opensource.org/osd)

> **倉庫已公開。** 不需登入即可閱讀教學、取得原始碼及下載 Releases 中的 APK；提出 Issues 需登入 GitHub。授權仍為 PolyForm Noncommercial 1.0.0，限非商業使用。

## 閱讀順序

- 只想使用：從「下載 APK 與首次設定」開始，不需要 Android Studio。
- 想自己編譯：依「取得原始碼 → 安裝開發環境 → 編譯 → 簽署／安裝」操作。
- 啟用抽選輔助：直接看[無障礙輔助啟用教學](#無障礙輔助啟用教學)。
- 想比較版本：先看下面的 VIP 版差異。
- [使用須知](docs/USAGE_DECLARATION.md)、[隱私說明](docs/PRIVACY.md)、[完整授權](LICENSE)、[版權通知](NOTICE)。App 內也能離線閱讀使用須知、隱私與授權。

## VIP 版本與獨立版本的差異

比較基準為原 Android `0.2.15-alpha` 與本獨立版 `0.1.2-standalone`，不是對未來版本功能的承諾。

| 項目 | 陀螺獵人 VIP 版 | 此獨立版／原始碼公開版 |
| --- | --- | --- |
| 使用資格 | 登入陀螺獵人網站，依伺服器回傳的會員資格開放使用 | 不需網站登入或 VIP；仍依活動有效期間及本機紀錄判斷可抽項目 |
| 網站清單來源 | Funbox 與陀螺獵人官網，可切換 | 僅 Funbox |
| 會員連線 | 包含登入、權杖保存、資格驗證與登入回跳 | 這些程式已移除，不會呼叫網站會員 API |
| 同步與篩選 | 商品、地區、活動狀態可多選，另有搜尋 | 保留 |
| 自動抽選 | 加入好友、抽選送出後接續下一筆、批次結束後檢查新增項目 | 保留，可依設定控制自動加入好友及自動接續 |
| 抽過／活動結束 | 辨識已抽過、已領取、已結束等狀態並接續；異常需要本人處理時暫停 | 保留同一基底的規則，包括「抽獎期間已結束」 |
| 紀錄 | 本機紀錄設定檔、手動標記完成及撤銷 | 保留；不與 VIP 版共享或自動匯入 |
| 測試區 | 包含指定連結的實際 LINE 抽選測試區 | 保留；測試連結仍可能已抽過或過期 |
| App 名稱 | LineDraw 網站同步 | LineDraw 獨立版 |
| 安裝識別碼 | `com.linedraw.app` | `com.linedraw.standalone`，可並存 |
| APK 取得方式 | 陀螺獵人網站的會員下載流程 | 本倉庫 Releases，或自行編譯 |
| 程式授權 | 會員服務版本，不隨此倉庫釋出授權 | PolyForm Noncommercial 1.0.0；限非商業用途 |
| 維護方式 | 依 VIP 服務自身說明 | 社群問題回報，不保證修復時程或個別教學 |

兩版的主要差異是會員驗證、資料來源及發布方式；本獨立版並未刻意刪除既有批次、篩選或紀錄功能，也不宣稱 VIP 版一定比較快。之後兩個專案各自發布，某一版更新不代表另一版已同步。

本倉庫只包含 Android App、模擬測試頁與測試程式，沒有網站後端、VIP 登入模組、VPS 部署工具或 iOS 專案。

## 下載 APK 與首次設定

1. 直接開啟 [Releases](https://github.com/beybladehunter/LineDraw-Android/releases)，選擇需要的版本，不需 GitHub 帳號。
2. 在該版本的 **Assets** 下載 `LineDraw-0.1.2-standalone.apk`，不是 `Source code.zip`。後者是原始碼，不能直接安裝。
3. 把 APK 傳到 Android 手機，使用檔案管理員開啟。若詢問是否允許此來源安裝，確認檔案來自本倉庫後再允許。
4. 開啟「LineDraw 獨立版」，閱讀使用須知與授權，按 **「我已了解，開始使用」**。不想繼續可按「離開 App」。使用須知有重要更新時會再次顯示。
5. 先在 LINE App 登入你要參加抽選的帳號。本工具不需要網站登入，但仍需使用你自己的 LINE 帳號。
6. 依下方「無障礙輔助啟用教學」完成系統授權，回 App 確認顯示「已啟用」。不需要 root，也不需要連接電腦才能執行日常抽選。

## 無障礙輔助啟用教學

### 先了解你同意的內容

**確認 App 使用須知與啟用 Android 無障礙服務是兩個步驟。** 按「我已了解，開始使用」只保存本機確認紀錄，不會替你開啟系統權限。

無障礙服務可讀取畫面內容並代表你操作其他 App。LineDraw 用它辨識 LINE 活動頁、依設定加入店家好友及點擊抽選；只有你主動開始批次後才執行抽選流程。確認安裝來源可信並理解用途後，再自行開啟；啟用服務本身不會開始抽選。

### 逐步開啟

1. 開啟 **LineDraw 獨立版 → 設定** 分頁。
2. 找到 **抽選輔助 → 無障礙服務 → 管理**。
3. 閱讀 App 的「啟用抽選輔助」說明，按 **前往 Android 設定**。
4. 在系統無障礙頁找到 **LineDraw 獨立版抽選輔助**。依手機介面，它可能列在「已安裝的應用程式」「已下載的應用程式」或類似分類中；各品牌與版本名稱可能不同。
5. 進入該服務、開啟開關，閱讀系統列出的能力；同意後依畫面按「允許」或確認。若要求手機密碼或等待確認，請由本人完成。
6. 返回 LineDraw「設定」，確認「無障礙服務」顯示 **已啟用**。
7. 到「抽選」分頁同步、選取活動，再按「檢查並開始 → 開始本次抽選」。只有到這一步才會開始操作 LINE。

### 出現「受限制的設定」或開關無法啟用

側載 APK 可能受 Android 的設定限制。只有確認來源可信時，才依以下步驟開放：

1. 開啟手機 **設定 → 應用程式 → LineDraw 獨立版**；也可嘗試長按 App 圖示進入「App 資訊」。
2. 在該 App 資訊頁的「更多／⋮」選單，找 **解除受限制的設定／允許受限制的設定**，依系統畫面完成確認。
3. 再回到 LineDraw 的「管理 → 前往 Android 設定」，重新開啟 **LineDraw 獨立版抽選輔助**。
4. 返回 App 確認顯示「已啟用」。只完成「允許受限制的設定」還不等於無障礙服務已啟用。

選單名稱與可用項目會因 Android 版本、品牌及安裝方式而異；沒有看到該項目時，先回無障礙頁查看實際阻擋原因，依系統說明處理。[Google 官方：瞭解受限制的設定](https://support.google.com/android/answer/12623953?hl=zh-Hant)

### 確認、關閉與常見問題

| 狀況 | 處理方式 |
| --- | --- |
| 同時安裝 VIP 版與獨立版 | 確認啟用的是「LineDraw 獨立版抽選輔助」；另一版的服務不會代替本版。停止另一版批次，避免同時操作 LINE。 |
| 系統顯示開啟，但 App 顯示尚未啟用 | 先確認服務名稱，再回系統頁查看是否需重新開啟；返回 App 重新確認。仍有問題時記錄手機型號、Android 版本與畫面訊息回報。 |
| 重開機或更新後無法抽選 | 回到 App 檢查服務狀態；若已關閉，依上述步驟重新啟用。 |
| 只想停止這一輪 | 使用浮動控制列的「暫停」或「停止」。 |
| 不再使用自動操作 | 先停止批次，再在同一個 Android 無障礙服務頁關閉「LineDraw 獨立版抽選輔助」。 |
| 手機限制由公司或家長管理 | 依管理政策聯絡管理者處理；App 無法自行核准系統權限。 |

以上教學也可在 App 的使用須知頁按「閱讀使用教學」，或在「設定 → 使用說明與授權 → 使用教學」離線閱讀。[精簡使用教學](docs/QUICK_START.md)

## 日常使用

### 同步、選取與開始

1. 在「抽選」分頁按「同步」，等待 Funbox 活動清單載入。同步失敗時會保留上次有效資料。
2. 使用商品、地區、活動狀態多選或搜尋縮小清單。選取「尚未開始」只會顯示那些活動，不會讓它們提早成為可抽選項目。
3. 勾選項目或使用「全選可抽選」。已有本機完成紀錄、過期或尚未開始的活動不會重新當作可抽項目。
4. 在設定確認「自動加入店家好友」及「自動接續新增活動」是否符合你的需求，再開始批次。
5. 保持螢幕解鎖、網路連線，讓 LINE 留在執行畫面；避免手動切換其他 App 或同時點擊。可用浮動控制列暫停、停止或略過目前項目。

抽選操作送出後會直接開啟下一筆，不保證讀到最終中獎結果。「已送出」只代表操作已派送，是否抽中請自行查看 LINE。

### 等待、略過與暫停

- 頁面載入會等待並在需要時重開一次；仍無法完成時保留原因並接續，不會無限等待同一筆。
- 顯示「已結束」或「抽獎期間已結束」會略過，並不算成功送出抽選。
- 已抽過、已領取等明確畫面會依規則記錄並接續。
- 遇到 LINE 登入、驗證碼、帳號問題或無法安全判斷的畫面，會暫停交給本人處理。移除的是本 App 的網站會員驗證，不是跳過 LINE 自身的安全要求。
- 開啟自動接續時，本輪完成後會同步同一來源，沿用開始時的商品、地區、活動狀態及搜尋條件，最多額外接續 3 輪。這不是全天候排程工具。

### 查看紀錄、手動完成與切換帳號

- 「紀錄」分頁顯示送出、完成、已參加、待確認及手動完成等狀態，請依畫面證據判讀。
- 已在 LINE 自行處理的項目可選「標記已完成」；誤標時用「撤銷手動完成」。這只更新本機紀錄，不會取消或重設 LINE 抽選。
- 本機設定檔不等於 LINE 帳號。更換 LINE 帳號前，先停止批次，再到「設定 → 本機紀錄」新增或切換設定檔。
- 「設定 → 抽選測試區」會操作真實 LINE 活動；既有連結可能已抽過或過期，並非重置抽選資格的工具。網站活動與測試區的紀錄分開保存。
- 「設定 → 預覽診斷紀錄」可查看、清除或自行分享診斷；「使用說明與授權」可重新閱讀使用須知、隱私說明及完整授權。

## 取得原始碼

可選擇下列任一方式：

- **不熟悉 Git**：在 GitHub 倉庫頁按 **Code → Download ZIP**，解壓縮後保留整個資料夾結構。
- **GitHub CLI**：安裝並登入 GitHub CLI 後執行：

  ```bash
  gh auth login
  gh repo clone beybladehunter/LineDraw-Android
  cd LineDraw-Android
  ```

- **Git**：無需登入即可複製公開程式碼，執行：

  ```bash
  git clone https://github.com/beybladehunter/LineDraw-Android.git
  cd LineDraw-Android
  ```

公開原始碼及 APK 可直接下載；回報 Issues 或透過 GitHub 提交修改時才需要帳號。不要把 GitHub 密碼、權杖或簽署金鑰寫進專案。

## 安裝 Android Studio 與開發環境

### 1. 安裝 Android Studio

從 [Android Studio 官網](https://developer.android.com/studio) 下載對應 Windows、macOS 或 Linux 的版本，依安裝精靈完成初始設定。macOS 請依 Apple Silicon／Intel 選擇相符版本。若 IDE 明確表示不支援此專案的 AGP 版本，請更新至支援它的 Android Studio，不要先隨意降版專案依賴。[官方安裝教學](https://developer.android.com/studio/install)

### 2. 開啟專案與安裝 SDK

1. 在 Android Studio 歡迎畫面選 **Open**，指定包含 `settings.gradle.kts`、`gradlew` 的專案根目錄，不是單獨的 `app` 子目錄。
2. 在 **Tools → SDK Manager** 安裝本專案使用的 **Android API 37 平台**、Android SDK Platform-Tools 與 Build-Tools。已驗證環境使用 API 37.0 平台及 Build-Tools 36.0.0；如 Gradle 明確要求其他套件，按提示補齊。
3. 要跑模擬器才需要 Android Emulator 與對應系統映像；只編譯 APK 不必安裝模擬器。
4. 等待 Gradle Sync 完成。第一次需要連線下載 Gradle、JDK 及 Maven 依賴，時間取決於網路。

Android Studio 會建立本機 `local.properties`，內容指向 Android SDK 安裝位置；此檔不提交到 GitHub。

### 3. 確認工具鏈

| 設定 | 倉庫使用值 |
| --- | --- |
| 最低手機系統 | Android 12 / API 31 |
| compileSdk / targetSdk | 37 / 36 |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.0 |
| Gradle Daemon JDK | 25，見 `gradle/gradle-daemon-jvm.properties` |
| Kotlin／Java 編譯目標 | Java 17；Compose 外掛版本見根 Gradle 檔 |

Gradle 會依 Daemon 工具鏈設定尋找或下載 JDK 25。命令列啟動 Wrapper 仍需能找到 Java；優先使用 Android Studio 隨附 JBR 啟動，不要把「Java 編譯目標 17」誤認成「Gradle Daemon 必須用 17」。

macOS 若終端找不到 Java，可在本次終端設定：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --version
```

Windows 可在 Android Studio 的 Terminal 執行，若仍找不到 Java，將 `JAVA_HOME` 指向 Android Studio 安裝目錄下的 `jbr`，重新開啟終端。Gradle Daemon 找不到 25 時，可在 IDE 的 JDK 下載功能安裝 JDK 25；最終以 `gradlew --version` 與 Sync 訊息確認。

## 編譯可安裝的測試 APK

預設 **debug 就是完整的 Funbox 獨立版**；不要誤選只有指定測試清單的 `pilot`。

macOS／Linux，在專案根目錄執行：

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

看到 **BUILD SUCCESSFUL** 後，輸出位於：

```text
app/build/outputs/apk/debug/app-debug.apk
```

此 APK 已以本機 Android debug 憑證簽署，可直接安裝。若使用 Android Studio 圖形介面，將 Build Variant 設為 `debug`，透過 Build APK 選項建置，或直接用 **Run → Run 'app'** 安裝至所選裝置。

其他建置：

| 用途 | Gradle 任務 | 輸出 | 識別碼 |
| --- | --- | --- | --- |
| 完整測試版 | `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | `com.linedraw.standalone` |
| 指定連結測試版 | `:app:assemblePilot` | `app/build/outputs/apk/pilot/app-pilot.apk` | `com.linedraw.standalone.pilot` |
| 未簽署 Release | `:app:assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` | `com.linedraw.standalone` |

Release 尚未簽署時不能直接安裝；下一節說明簽署。

## 簽署自己的 Release APK

1. 在 Android Studio 選 **Build → Generate Signed App Bundle or APK**，選 **APK**。
2. 選擇 `app` 模組；第一次可按 **Create new** 建立自己的 keystore，已有金鑰則使用原檔。
3. 將 keystore 放在專案外，保存檔案、alias、密碼及備份。不要上傳到 GitHub 或寄到 Issues。
4. 選擇 **release** variant，依精靈完成建置。
5. 在完成通知按 **Locate** 找到已簽署 APK；精靈的輸出位置以你當次選擇為準。

後續更新同一份安裝必須使用相同的簽署憑證，並提高 `app/build.gradle.kts` 的 `versionCode`。`versionName` 是給使用者看的版本名稱。正式使用時應持續保管自己的 release 金鑰。[Android 官方簽署說明](https://developer.android.com/studio/publish/app-signing)

本倉庫目前 Releases 提供以維護者本機 debug 憑證簽署的測試 APK。你在另一台電腦自行編譯的 debug APK 通常使用不同憑證，不能保證能覆蓋該安裝；不要為了處理簽章不符直接移除有重要紀錄的 App。App 目前沒有完整資料匯出／還原功能，移除或清除資料會遺失紀錄。

## 將 APK 安裝到手機

可直接傳送 APK 至手機安裝，或使用 USB：

1. 在手機「關於手機」開啟開發者選項，進入開發者選項啟用 **USB 偵錯**。
2. 接上可傳輸資料的 USB 線，保持手機解鎖，確認手機上的「允許 USB 偵錯」提示。部分品牌另要求允許「透過 USB 安裝」。
3. 在 Android Studio 選擇該裝置後按 Run；或在命令列執行：

   ```bash
   adb devices -l
   adb -s 你的裝置序號 install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. 出現 `Success` 後，在手機開啟「LineDraw 獨立版」，依前述首次設定開啟無障礙服務。

若找不到 `adb`，使用 SDK Manager 顯示的 SDK 路徑下 `platform-tools/adb`（Windows 為 `adb.exe`），或將該資料夾加入 PATH。[ADB 官方說明](https://developer.android.com/tools/adb)

## 更新原始碼與安裝新版

使用 Git 且沒有待保存修改時，在根目錄執行 `git pull --ff-only`，重新 Sync、編譯，再以相同簽章覆蓋安裝。若自己改過程式，先提交或保存修改，不要用重設指令丟掉它們。

VIP 版與獨立版可並存，但資料及無障礙服務各自獨立。測試其中一版時，請停止另一版的批次，避免同時操作同一個 LINE 畫面。

## 常見問題

| 狀況 | 處理方式 |
| --- | --- |
| GitHub 404／沒有 Releases | 確認網址為 `beybladehunter/LineDraw-Android`，並由倉庫的 Releases 入口選擇有效版本；公開下載不需要受邀帳號 |
| `SDK location not found` | 用 Android Studio 開啟根目錄，確認 SDK Manager 位置及 `local.properties` |
| 找不到 API 37／平台套件 | 在 SDK Manager 補齊 API 37 平台，重新 Sync |
| 找不到 Java／JDK 版本不符 | 檢查 `JAVA_HOME`、IDE JDK 與 Daemon JDK 25 設定 |
| Gradle 下載失敗 | 檢查網路、代理及憑證；修正後重試，不必先刪除整個專案 |
| `unauthorized`／`offline` | 解鎖手機、重新插拔並確認 USB 偵錯提示 |
| `INSTALL_FAILED_USER_RESTRICTED` | 檢查手機是否允許 USB 安裝，並確認手機上的安裝提示 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 已安裝版本與新 APK 簽章不同；確認原簽章，不要直接移除有重要資料的 App |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 新 APK 的 versionCode 比已安裝版低，使用較新版本；避免強制降版 |
| 無法啟用無障礙 | 檢查受限制的設定，以及是否開啟了正確的獨立版服務 |
| 同步失敗／清單空白 | 檢查網路與 Funbox 頁面，再查看同步錯誤；保留診斷供回報 |
| 執行中暫停 | 先看浮動列或 App 原因，處理 LINE 登入／驗證等必要步驟後再繼續 |
| 抽選頁顯示已抽過 | 換 APK、本機設定檔或重裝都不會重置 LINE 端抽選資格 |

## 測試與驗證

單元測試及靜態檢查：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintPilot
```

Windows 將 `./gradlew` 換成 `.\gradlew.bat`。測試輸出可在 `app/build/reports/` 查看。

模擬器流程測試需建立名稱以 `LineDraw_` 開頭的專用 Android 16 AVD，啟動後在 macOS／Linux 執行：

```bash
ANDROID_SERIAL=emulator-5554 ./scripts/test-emulator.sh
```

將序號改成 `adb devices` 顯示的那一台。腳本會安裝 `fixture`，清除其測試資料並重設無障礙服務，只能用在可拋棄的專用模擬器。它拒絕實體手機與非專用 AVD，使用模擬活動，不會送出真實 LINE 抽選。

即時網頁測試需明確指定 instrumentation 參數 `linedraw.liveCatalog=true`；其他測試使用本機 fixture 或保存的 HTML。初版驗證見 [0.1.0 紀錄](docs/VALIDATION.md)，0.1.1 更新見 [驗證紀錄](docs/VALIDATION_0.1.1.md)，本次更新見 [0.1.2 紀錄](docs/VALIDATION_0.1.2.md)。模擬器測試不代表每款真機皆已驗證。

## 回報問題與維護範圍

先閱讀教學及常見問題。仍無法解決時，請在 [GitHub Issues](https://github.com/beybladehunter/LineDraw-Android/issues) 提供 App 版本、手機型號、Android／LINE 版本、重現步驟、預期與實際結果，必要時附上自行檢查過的診斷。

維護者於能力範圍內回覆，不承諾固定支援時段、修復期限、所有機型相容性或個別安裝教學。請勿在回報中公開帳密、權杖、驗證碼或私人聊天。

## 授權與散布

- 專案程式採 **PolyForm Noncommercial 1.0.0**。非商業用途的使用、修改與散布依 [LICENSE](LICENSE) 辦理；「免費下載」不等於允許商業利用。
- 商業用途須另行取得授權，請向 GitHub 帳號 `beybladehunter` 聯繫說明預定用途。是否收費不是判定商業用途的唯一條件；不確定時先取得確認。
- 散布時保留授權條款或其網址，以及 [NOTICE](NOTICE) 中的 Required Notice；不要把修改版冒充維護者發布的版本。
- 第三方套件、工具、名稱、商標及活動內容各有其權利與授權，不因本專案使用 PolyForm 而改變。
- 本授權只適用於此倉庫所提供且有權授權的程式，不自動涵蓋陀螺獵人網站、VIP 版或其他未提供於此的專案。
- 使用告知與原始碼授權分開：App 的聲明用於說明自動操作、資料及維護範圍，不代替 LICENSE 全文。

## 原始碼導覽

- `app/src/main/java/com/linedraw/app/data/`：Funbox 解析、同步、資料庫、篩選、紀錄及佇列。
- `app/src/main/java/com/linedraw/app/engine/`：無障礙操作、畫面判斷、等待與連結開啟。
- `app/src/main/java/com/linedraw/app/usage/`：本機聲明確認，不做網站會員驗證。
- `app/src/main/java/com/linedraw/app/ui/`：Compose 液態玻璃介面與授權閱讀視窗。
- `fixture/`：原生／WebView 測試頁。

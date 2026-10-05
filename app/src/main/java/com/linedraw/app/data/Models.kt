package com.linedraw.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.net.URI

const val SOURCE_URL = "https://uxux11.github.io/funbox-line/"
fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

enum class Eligibility(val label: String) { READY("可抽選"), NOT_STARTED("尚未開始"), EXPIRED("已截止"), UNKNOWN("時間未確認"), ARCHIVED("已封存") }
enum class Participation(val label: String) { SUBMITTED("已送出"), COMPLETE("已完成"), ALREADY("已抽過"), MANUAL("已完成（手動）"), REVIEW("待確認") }

@Entity(tableName = "draws")
data class Draw(
    @PrimaryKey val rowKey: String,
    val sourceId: String,
    val activityKey: String,
    val store: String,
    val city: String,
    val product: String,
    val url: String,
    val timeLabel: String,
    val startsAt: Long?,
    val endsAt: Long?,
    val ordinal: Int,
    val archived: Boolean = false,
    val demo: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
) {
    fun eligibility(now: Long = System.currentTimeMillis()): Eligibility = when {
        archived -> Eligibility.ARCHIVED
        startsAt != null && now < startsAt -> Eligibility.NOT_STARTED
        endsAt != null && now >= endsAt -> Eligibility.EXPIRED
        startsAt == null || endsAt == null -> Eligibility.UNKNOWN
        else -> Eligibility.READY
    }
}

@Entity(tableName = "records", primaryKeys = ["profile", "activityKey"])
data class Record(
    val profile: String, val activityKey: String, val product: String, val store: String,
    val status: String, val result: String = "未提供", val evidence: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "batches")
data class Batch(
    @PrimaryKey val id: String,
    val profile: String,
    val state: String = "RUNNING",
    val currentIndex: Int = 0,
    val total: Int,
    val autoFriend: Boolean,
    val demo: Boolean,
    val reason: String = "準備開始",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_items", primaryKeys = ["batchId", "position"])
data class BatchItem(
    val batchId: String, val position: Int,
    val activityKey: String, val product: String, val store: String, val url: String,
    val startsAt: Long?, val endsAt: Long?,
    val state: String = "PENDING", val stage: String = "OPEN",
    val submitted: Boolean = false, val friendAttempted: Boolean = false,
    val reason: String = "等待處理", val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "attempts")
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String, val position: Int, val action: String,
    val disposition: String = "INTENT", val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "metadata")
data class Metadata(@PrimaryKey val key: String, val value: String)

object LinkPolicy {
    fun canonicalUrl(key: String): String? {
        val parts = key.split(':')
        return if (parts.size == 3 && parts[0] == "coupon" && parts.drop(1).all { Regex("[A-Za-z0-9_-]+").matches(it) })
            "https://liff.line.me/${parts[1]}/c/${parts[2]}" else null
    }
    fun allowed(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) &&
            u.host in setOf("lin.ee", "liff.line.me", "line.me") && !u.rawPath.isNullOrBlank() && u.rawPath != "/"
    }.getOrDefault(false)

    fun activityKey(url: String, store: String, period: String = ""): String {
        require(allowed(url)) { "抽選連結不在允許的 LINE 網域" }
        val u = URI(url)
        val match = Regex("^/([^/]+)/c/([A-Za-z0-9_-]+)$").matchEntire(u.path)
        // Only a documented, fully identified coupon can merge across distinct URLs.
        return if (u.host == "liff.line.me" && match != null) "coupon:${match.groupValues[1]}:${match.groupValues[2]}"
        else "url:" + digest(store + "\n" + url.substringBefore('#') + "\n" + period)
    }
}

package com.linedraw.app.engine

/** Monotonic-clock budget: 30 seconds/open, one reopen, 60 seconds/network outage. */
class LoadState(private val loadMs: Long = 30_000, private val networkMs: Long = 60_000, private val totalMs: Long = 90_000) {
    enum class Expiry { NONE, REOPEN, FAILED }
    var attempt = 0; private set
    private var deadline = Long.MAX_VALUE
    private var offlineAt: Long? = null
    private var startedAt: Long? = null
    fun track(now: Long) { if (startedAt == null) startedAt = now }
    fun totalRemaining(now: Long): Long = (totalMs - (now - (startedAt ?: now))).coerceAtLeast(0)
    fun networkRemaining(now: Long): Long = (networkMs - (now - (offlineAt ?: now))).coerceAtLeast(0)
    fun begin(now: Long) { track(now); attempt++; deadline=now+loadMs }
    fun restartAfterFriend(now: Long) { deadline=now+loadMs }
    fun failNow(now: Long) { deadline=now }
    fun remaining(now: Long) = minOf((deadline-now).coerceAtLeast(0),totalRemaining(now)).coerceAtLeast(1)
    fun expiry(now: Long): Expiry = if (totalRemaining(now) == 0L) Expiry.FAILED
        else if (now < deadline || offlineAt != null) Expiry.NONE else if (attempt < 2) Expiry.REOPEN else Expiry.FAILED
    fun network(online: Boolean, now: Long): Boolean {
        track(now)
        if (!online) { if (offlineAt == null) offlineAt=now; return now-offlineAt!! < networkMs }
        offlineAt?.let { if (deadline != Long.MAX_VALUE) deadline += now-it }
        offlineAt=null
        return true
    }
}

/** Give a transient local problem two seconds to settle, then use the existing reopen budget. */
class LocalIssue(private val waitMs: Long = 2_000) {
    private var since: Long? = null
    fun retryNow(now: Long): Boolean { if (since == null) since = now; return now - since!! >= waitMs }
    fun clear() { since = null }
}

/** Never reuse an unchanged preceding page as the newly opened activity. No name matching. */
class PageTransitionGuard(private val previous: Page?) {
    private var changed = previous == null
    fun accept(page: Page): Boolean {
        // A late result-text update on the old nodes is not evidence of a new document.
        if (page.packageName != previous?.packageName || page.document != previous.document ||
            (page.texts.isEmpty() && page.buttons.isEmpty())) changed=true
        return changed
    }
}

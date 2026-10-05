package com.linedraw.app.engine

import com.linedraw.app.data.BatchItem
import com.linedraw.app.data.TestCatalog
import com.linedraw.app.data.LinkPolicy
import java.net.URI

/** Pilot links are trusted batch inputs; no store, title or on-screen ID confirmation. */
object CampaignGate {
    fun visible(url: String, texts: Set<String>): Boolean {
        val id = runCatching { URI(url).path.substringAfterLast('/') }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false
        return texts.any { it == url || it == id || it.contains("/c/$id") }
    }

    fun permits(page: Page, item: BatchItem, expectedPackage: String,
                demo: Boolean, pilot: Boolean): Boolean {
        if (page.packageName != expectedPackage) return false
        if (pilot) return TestCatalog.allowsItem(item)
        return demo || LinkPolicy.canonicalUrl(item.activityKey) == item.url
    }
}

package com.linedraw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.room.Room
import com.linedraw.app.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CatalogNetworkTest {
    @Test fun fetchFunboxWithAndroidNetworkStack()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("linedraw.liveCatalog") == "true")
        check(android.os.Build.HARDWARE.contains("ranchu"))
        val result=JSONArray()
        val source=HttpCatalogSource()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val output=File(context.filesDir,"catalog-selector").apply{mkdirs()}
        for(catalog in DrawCatalog.entries) {
            val html=source.fetch(catalog)
            output.resolve("${catalog.id}.html").writeText(html)
            println("CATALOG ${catalog.id} bytes=${html.toByteArray().size}")
            val rows=catalog.parse(html)
            assertTrue(rows.isNotEmpty());assertEquals(rows.size,rows.map {it.rowKey}.distinct().size)
            assertTrue(rows.all {LinkPolicy.allowed(it.url)})
            result.put(JSONObject().put("source",catalog.id).put("url",catalog.dataUrl).put("rows",rows.size)
                .put("sha256",digest(html)).put("ready",rows.count {it.runnable()})
                .put("fetchedAt",java.time.Instant.now().toString()))
        }
        output.resolve("live-sources.json").writeText(result.toString(2))
    }

}

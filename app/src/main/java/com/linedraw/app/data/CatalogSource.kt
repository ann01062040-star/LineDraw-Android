package com.linedraw.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

interface CatalogSource {
    suspend fun fetch(catalog: DrawCatalog = DrawCatalog.FUNBOX): String
    suspend fun resolve(url: String): String
}

class HttpCatalogSource : CatalogSource {
    override suspend fun fetch(catalog: DrawCatalog): String = withContext(Dispatchers.IO) {
                val conn = URL(catalog.dataUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 15_000; conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "LineDraw/0.1 (Android; public draw catalog)")
                conn.setRequestProperty("Accept", "text/html")
                try {
                    check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                    val bytes = conn.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 2_000_000) { "來源頁面過大" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 2_000_000) { "來源頁面過大" }
                    bytes.toString(Charsets.UTF_8)
                } finally { conn.disconnect() }
    }
    override suspend fun resolve(url: String): String = resolveCoupon(url)
}

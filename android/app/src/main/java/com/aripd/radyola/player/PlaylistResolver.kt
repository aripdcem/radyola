package com.aripd.radyola.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * `.pls` / `.m3u` çalma listelerini gerçek akış adresine çözer.
 *
 * ExoPlayer bu iki eski formatı doğrudan açamaz; listedeki ilk akış adresini
 * çıkarıp onu çalarız. `.m3u8` (HLS) dokunulmadan geçer — ExoPlayer onu destekler.
 */
object PlaylistResolver {

    private const val TAG = "PlaylistResolver"
    private const val MAX_BYTES = 64 * 1024

    /** URL'nin çözülmesi gerekip gerekmediğini söyler. */
    fun needsResolving(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".pls") || path.endsWith(".m3u")
    }

    /**
     * Çalma listesini indirip ilk akış adresini döndürür.
     * Çözülemezse orijinal URL geri verilir; böylece çağıran taraf her zaman çalabilir.
     */
    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        if (!needsResolving(url)) return@withContext url
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Radyola-Android/1.0")
            }
            val body = try {
                connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(MAX_BYTES)
                    val read = reader.read(buffer)
                    if (read <= 0) "" else String(buffer, 0, read)
                }
            } finally {
                connection.disconnect()
            }
            firstStreamUrl(body) ?: url
        } catch (e: Exception) {
            Log.w(TAG, "Çalma listesi çözülemedi: $url", e)
            url
        }
    }

    /**
     * Çalma listesi gövdesinden ilk akış adresini çıkarır.
     *
     * PLS: `File1=http://...` satırı. M3U: yorum olmayan ilk satır.
     */
    internal fun firstStreamUrl(body: String): String? {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val plsEntry = lines.firstOrNull { it.startsWith("File", ignoreCase = true) && it.contains('=') }
        if (plsEntry != null) {
            val value = plsEntry.substringAfter('=').trim()
            if (value.startsWith("http")) return value
        }
        return lines.firstOrNull { !it.startsWith("#") && it.startsWith("http") }
    }
}

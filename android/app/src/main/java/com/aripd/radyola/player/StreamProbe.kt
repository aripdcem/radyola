package com.aripd.radyola.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bir adresten gerçekten yayın alınıp alınamadığını sınar.
 *
 * Elle eklenen istasyonlar için: yazım hatası kaydedilirse kullanıcı kanalın
 * neden çalmadığını anlayamıyor. Kaydetmeden önce ilk baytları çekiyoruz.
 */
object StreamProbe {

    private const val TAG = "StreamProbe"
    private const val MIN_BYTES = 512

    suspend fun canPlay(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Radyola-Android/1.0")
                setRequestProperty("Range", "bytes=0-4095")
            }
            try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                    return@withContext false
                }
                // Icecast'in durum sayfası da 200 dönüyor; belge türü yayın değildir.
                val type = connection.contentType.orEmpty()
                if (type.startsWith("text/html") || type.startsWith("text/xml") ||
                    type.startsWith("application/json") || type.startsWith("application/xml")
                ) {
                    return@withContext false
                }
                // HLS manifesti küçük bir metin; bayt sayısı yerine içeriğine bakılır.
                if (type.contains("mpegurl", ignoreCase = true) ||
                    url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
                ) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext body.trimStart().startsWith("#EXTM3U")
                }
                val buffer = ByteArray(4096)
                var total = 0
                connection.inputStream.use { stream ->
                    while (total < MIN_BYTES) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        total += read
                    }
                }
                total >= MIN_BYTES
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Yayın sınanamadı: $url", e)
            false
        }
    }
}

package com.aripd.radyola.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * İstasyon listesini ortak JSON kaynağından çeker, diske önbelleğe alır.
 *
 * Kaynak tüm platformlarda (macOS / Linux / Windows / web) ortaktır;
 * şema `data/README.md` içinde tanımlı.
 */
class StationRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "stations.json")

    /**
     * İstasyon listesini döndürür.
     *
     * Sıra: ağ → disk önbelleği → gömülü varsayılanlar. Ağdan başarılı çekim
     * önbelleği tazeler; böylece uçak modunda da son bilinen liste açılır.
     */
    suspend fun loadStations(): List<RadioStation> = withContext(Dispatchers.IO) {
        fetchWithRetry()?.also { writeCache(it) }
            ?: readCache()
            ?: FALLBACK_STATIONS
    }

    /** Yalnızca diskteki önbelleği okur — açılışta anında liste göstermek için. */
    suspend fun cachedStations(): List<RadioStation>? = withContext(Dispatchers.IO) {
        readCache()
    }

    /**
     * Ağ denemesini kısa bir beklemeyle bir kez tekrarlar.
     *
     * Telefon uykudan yeni uyandığında Wi-Fi birkaç saniye hazır olmuyor; tek
     * denemede yedek listeye düşmek yerine bir şans daha veriyoruz.
     */
    private suspend fun fetchWithRetry(): List<RadioStation>? {
        fetchFromNetwork()?.let { return it }
        delay(RETRY_DELAY_MS)
        return fetchFromNetwork()
    }

    private fun fetchFromNetwork(): List<RadioStation>? {
        return try {
            val connection = (URL(STATIONS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Radyola-Android/1.0")
                setRequestProperty("Accept", "application/json")
            }
            val body = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Beklenmeyen yanıt: HTTP ${connection.responseCode}")
                    return null
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            parseStations(body).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "İstasyon listesi çekilemedi, önbelleğe düşülüyor", e)
            null
        }
    }

    private fun readCache(): List<RadioStation>? = try {
        if (!cacheFile.exists()) null
        else parseStations(cacheFile.readText()).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "Önbellek okunamadı", e)
        null
    }

    /**
     * Önbelleği kaynakla aynı şemada yazar — böylece [readCache] ile
     * [fetchFromNetwork] aynı ayrıştırıcıyı paylaşır.
     */
    private fun writeCache(stations: List<RadioStation>) {
        try {
            val array = JSONArray()
            stations.forEach { station ->
                array.put(
                    JSONObject()
                        .put(FIELD_TITLE, station.name)
                        .put(FIELD_URL, station.url)
                        .put(FIELD_WEBSITE, station.website)
                        .put(FIELD_LOCATION, station.location)
                        .put(FIELD_COUNTRY_CODE, station.countryCode)
                        .put(FIELD_GENRE, station.genre)
                )
            }
            cacheFile.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Önbellek yazılamadı", e)
        }
    }

    companion object {
        private const val TAG = "StationRepository"
        private const val RETRY_DELAY_MS = 1_500L

        /** Kuratörlü istasyon listesi — tüm platformlarda ortak. */
        const val STATIONS_URL = "https://radyola.aripd.com/data/stations.json"
    }
}

private const val FIELD_TITLE = "title"
private const val FIELD_URL = "url"
private const val FIELD_WEBSITE = "website"
private const val FIELD_LOCATION = "location"
private const val FIELD_COUNTRY_CODE = "countryCode"
private const val FIELD_GENRE = "genre"

/**
 * JSON dizisini istasyon listesine çevirir.
 *
 * Adı veya çalınabilir bir adresi olmayan kayıtlar atlanır — tek bozuk satır
 * yüzünden listenin tamamını kaybetmemek için ayrıştırma kayıt bazında toleranslı.
 */
internal fun parseStations(json: String): List<RadioStation> {
    val array = JSONArray(json)
    val stations = ArrayList<RadioStation>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val name = item.optString(FIELD_TITLE).trim()
        val url = item.optString(FIELD_URL).trim()
        if (name.isEmpty() || !url.startsWith("http")) continue
        stations.add(
            RadioStation(
                name = name,
                url = url,
                website = item.optString(FIELD_WEBSITE).trim(),
                location = item.optString(FIELD_LOCATION).trim(),
                countryCode = item.optString(FIELD_COUNTRY_CODE).trim(),
                genre = item.optString(FIELD_GENRE).trim()
            )
        )
    }
    return stations
}

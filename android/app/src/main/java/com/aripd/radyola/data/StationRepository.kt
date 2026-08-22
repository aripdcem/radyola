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
 * İstasyon listesini Google Sheets'ten çeker, diske önbelleğe alır.
 *
 * Diğer platformlarla (macOS / Linux / Windows / web) aynı kaynağı kullanır.
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

    /** Yalnızca diskteki önbelleği okur — açılışta anında liste göstermek için. */
    suspend fun cachedStations(): List<RadioStation>? = withContext(Dispatchers.IO) {
        readCache()
    }

    private fun fetchFromNetwork(): List<RadioStation>? {
        return try {
            val connection = (URL(STATIONS_CSV_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Radyola-Android/1.0")
            }
            val csv = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            parseCsv(csv).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "İstasyon listesi çekilemedi, önbelleğe düşülüyor", e)
            null
        }
    }

    private fun readCache(): List<RadioStation>? = try {
        if (!cacheFile.exists()) null else {
            val array = JSONArray(cacheFile.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                RadioStation(
                    name = o.getString("name"),
                    url = o.getString("url"),
                    website = o.optString("website"),
                    location = o.optString("location"),
                    genre = o.optString("genre")
                )
            }.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Önbellek okunamadı", e)
        null
    }

    private fun writeCache(stations: List<RadioStation>) {
        try {
            val array = JSONArray()
            stations.forEach { s ->
                array.put(
                    JSONObject()
                        .put("name", s.name)
                        .put("url", s.url)
                        .put("website", s.website)
                        .put("location", s.location)
                        .put("genre", s.genre)
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

        /** Google Sheets CSV export URL'si — tüm platformlarda ortak. */
        const val STATIONS_CSV_URL =
            "https://docs.google.com/spreadsheets/d/" +
                "1WetccPDwGuUAqNQzUTVNCKy1k48MDM1bvLnDlfdRhis/export?format=csv"
    }
}

/**
 * CSV metnini istasyon listesine çevirir.
 *
 * Başlık satırı ayrı işaretlenmediğinden, URL'si `http` ile başlamayan satırlar
 * atlanır — bu hem başlığı hem de bozuk satırları eler.
 */
internal fun parseCsv(csv: String): List<RadioStation> =
    splitCsvRows(csv).mapNotNull { row ->
        if (row.size < 3) return@mapNotNull null
        val name = row[1].trim()
        val url = row[2].trim()
        if (name.isEmpty() || !url.startsWith("http")) return@mapNotNull null
        RadioStation(
            name = name,
            url = url,
            website = row.getOrElse(3) { "" }.trim(),
            location = row.getOrElse(4) { "" }.trim(),
            genre = row.getOrElse(5) { "" }.trim()
        )
    }

/**
 * CSV'yi satır ve alanlara böler.
 *
 * Tırnak içindeki virgül ve satır sonlarını korur, `""` kaçışını tek tırnağa çevirir.
 */
private fun splitCsvRows(csv: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var fields = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0

    fun endField() {
        fields.add(field.toString())
        field.setLength(0)
    }

    fun endRow() {
        endField()
        if (fields.any { it.isNotBlank() }) rows.add(fields)
        fields = mutableListOf()
    }

    while (i < csv.length) {
        val c = csv[i]
        when {
            inQuotes && c == '"' && i + 1 < csv.length && csv[i + 1] == '"' -> {
                field.append('"'); i++
            }
            c == '"' -> inQuotes = !inQuotes
            !inQuotes && c == ',' -> endField()
            !inQuotes && (c == '\n' || c == '\r') -> {
                if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                endRow()
            }
            else -> field.append(c)
        }
        i++
    }
    endRow()
    return rows
}

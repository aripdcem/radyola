package com.aripd.radyola.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Kullanıcının kendi istasyon listesi.
 *
 * Kuratörlü liste (`data/stations.json`) yalnızca **tohum**: ilk açılışta bir
 * kez kopyalanır, sonra üzerine yazılmaz. Böylece liste tamamen kullanıcıya
 * ait olur — ekleyebilir, çıkarabilir — ve uzaktaki dosya tazelendiğinde
 * kullanıcının seçimleri kaybolmaz.
 *
 * Kayıtların tamamı saklanır, yalnız kimlikleri değil: Keşfet'ten eklenen bir
 * istasyonu göstermek için 3.400 kayıtlık dizini yüklemek gerekmesin.
 */
class UserListStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    /** Listedeki istasyonların kimlikleri — Keşfet'te "ekli mi" kontrolü için. */
    val ids: Set<String>
        get() = _stations.value.mapTo(HashSet()) { it.id }

    /** Diskteki listeyi belleğe alır. Dosya yoksa liste boş kalır (tohumlanmamış). */
    suspend fun load(): List<RadioStation> = withContext(Dispatchers.IO) {
        val loaded = try {
            if (file.exists()) parseStations(file.readText()) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Liste okunamadı", e)
            emptyList()
        }
        _stations.value = loaded
        loaded
    }

    /** Liste daha önce tohumlandı mı? Dosyanın varlığı yeterli işaret. */
    suspend fun isSeeded(): Boolean = withContext(Dispatchers.IO) { file.exists() }

    /**
     * İlk açılış tohumu. Yalnız daha önce tohumlanmamışsa yazar —
     * kullanıcının listesini kazara ezmemek için.
     */
    suspend fun seed(stations: List<RadioStation>): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) return@withContext false
        persist(stations)
        true
    }

    /** İstasyonu listeye ekler. Zaten varsa dokunmaz. */
    suspend fun add(station: RadioStation) = withContext(Dispatchers.IO) {
        val current = _stations.value
        if (current.any { it.id == station.id }) return@withContext
        persist(current + station)
    }

    suspend fun remove(station: RadioStation) = withContext(Dispatchers.IO) {
        persist(_stations.value.filterNot { it.id == station.id })
    }

    /** Listede varsa çıkarır, yoksa ekler — yıldız düğmesinin karşılığı. */
    suspend fun toggle(station: RadioStation) = withContext(Dispatchers.IO) {
        val current = _stations.value
        if (current.any { it.id == station.id }) remove(station) else add(station)
    }

    /**
     * Kuratörlü listede olup kullanıcıda olmayanları döndürür.
     *
     * Tohum bir kez atıldığı için sonraki eklemeleriniz kullanıcıya ulaşmıyor;
     * ayarlardaki "yeni kanallara bak" bunu elle kapatır. Sessizce eklemiyoruz:
     * kullanıcının bilerek çıkardığı bir istasyon geri gelmemeli.
     */
    fun missingFrom(curated: List<RadioStation>): List<RadioStation> {
        val mine = ids
        return curated.filterNot { it.id in mine }
    }

    private fun persist(stations: List<RadioStation>) {
        try {
            val array = JSONArray()
            stations.forEach { s ->
                array.put(
                    JSONObject()
                        .put("title", s.name)
                        .put("url", s.url)
                        .put("website", s.website)
                        .put("location", s.location)
                        .put("countryCode", s.countryCode)
                        .put("genre", s.genre)
                        .put("votes", s.votes)
                )
            }
            file.writeText(array.toString())
            _stations.value = stations
        } catch (e: Exception) {
            Log.w(TAG, "Liste yazılamadı", e)
        }
    }

    private companion object {
        const val TAG = "UserListStore"
        const val FILE_NAME = "mylist.json"
    }
}

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
     *
     * Karşılaştırma kimlikle değil **adla** yapılır: kimlik URL'yi içeriyor ve
     * kuratörlü listede ölü yayınların adresi düzeltiliyor. Kimlikle bakılsa
     * adresi düzeltilen istasyon "eksik" sanılıp ikinci kez eklenirdi — biri
     * ölü, biri sağlam iki kopya. Adres farkları [applyUrlUpdates]'in işi.
     */
    fun missingFrom(curated: List<RadioStation>): List<RadioStation> =
        missingByName(curated, _stations.value)

    /**
     * Kuratörlü listedeki adres düzeltmelerini kullanıcı listesine uygular.
     *
     * Üyelik ve sıra kullanıcının malı, akış adresi verinin: kuratörlü listede
     * bir istasyonun URL'si değiştiyse (ölü yayın düzeltmesi) kullanıcıdaki
     * aynı adlı kaydın adresi de güncellenir — kayıt olduğu yerde kalır.
     *
     * Dönen liste eski → yeni kimlik eşlemesi; çağıran taraf "son istasyon"
     * gibi kimliğe bağlı kayıtları taşıyabilsin diye.
     */
    suspend fun applyUrlUpdates(curated: List<RadioStation>): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val updates = curatedUrlUpdates(curated, _stations.value)
            if (updates.isEmpty()) return@withContext emptyList()
            val renames = ArrayList<Pair<String, String>>(updates.size)
            val next = _stations.value.map { station ->
                val newUrl = updates[station.id] ?: return@map station
                station.copy(url = newUrl).also { renames.add(station.id to it.id) }
            }
            persist(next)
            renames
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

/**
 * Kuratörlü listede olup kullanıcı listesinde **adı** geçmeyen istasyonlar.
 *
 * Saf fonksiyon — birim testinde Android bağımlılığı olmadan sınanır.
 */
internal fun missingByName(
    curated: List<RadioStation>,
    mine: List<RadioStation>
): List<RadioStation> {
    val names = mine.mapTo(HashSet()) { it.name }
    return curated.filterNot { it.name in names }
}

/**
 * Kullanıcı listesindeki hangi kayıtların adresi kuratörlü listede değişmiş?
 *
 * Dönen eşleme: kullanıcıdaki kaydın kimliği → kuratörlü listedeki yeni adres.
 * Ad eşleşmesi esas alınır; kuratörlü listede adlar benzersizdir
 * (aynı ad iki kez geçerse son kayıt kazanır).
 */
internal fun curatedUrlUpdates(
    curated: List<RadioStation>,
    mine: List<RadioStation>
): Map<String, String> {
    val byName = curated.associateBy { it.name }
    return buildMap {
        mine.forEach { station ->
            val fresh = byName[station.name] ?: return@forEach
            if (fresh.url != station.url) put(station.id, fresh.url)
        }
    }
}

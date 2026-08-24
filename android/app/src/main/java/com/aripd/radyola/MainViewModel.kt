package com.aripd.radyola

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.aripd.radyola.data.AppSettings
import com.aripd.radyola.data.RadioStation
import com.aripd.radyola.data.SettingsStore
import com.aripd.radyola.data.StationRepository
import com.aripd.radyola.data.Countries
import com.aripd.radyola.data.ListMode
import com.aripd.radyola.data.StationSource
import com.aripd.radyola.data.UserListStore
import com.aripd.radyola.player.PlaylistResolver
import com.aripd.radyola.player.StreamProbe
import com.aripd.radyola.player.RadyolaPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uygulamanın tüm ekran durumu. */
data class UiState(
    val mode: ListMode = ListMode.MY_LIST,
    val directoryLoading: Boolean = false,
    /** Elle eklenen adres doğrulanırken true. */
    val verifyingStation: Boolean = false,
    /** Kullanıcı listesindeki kimlikler — Keşfet'te "ekli mi" göstergesi. */
    val myListIds: Set<String> = emptySet(),
    val stations: List<RadioStation> = emptyList(),
    val visible: List<RadioStation> = emptyList(),
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val query: String = "",
    val selectedCountry: String? = null,
    val selectedGenre: String? = null,
    val isLoading: Boolean = true,
    val current: RadioStation? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val nowPlayingTrack: String? = null,
    val errorMessage: String? = null,
    val settings: AppSettings = AppSettings(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSec: Int = 0
) {
    val hasFilters: Boolean
        get() = query.isNotBlank() || selectedCountry != null || selectedGenre != null

    val isDiscovering: Boolean
        get() = mode == ListMode.DISCOVER
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StationRepository(application)
    private val settingsStore = SettingsStore(application)
    private val userList = UserListStore(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private val resolvedUrls = mutableMapOf<String, String>()
    private var sleepTimerJob: Job? = null
    private var pendingAutoplayId: String? = null

    // Keşfet dizini bir kez çekilip bellekte tutulur; mod geçişi ağa çıkmasın.
    private var directory: List<RadioStation>? = null

    // Oynatıcıya verilen sıra. Keşfet'te görünen liste 3.400 kayıt olabiliyor;
    // hepsini MediaItem'a çevirmek yerine seçilen istasyonun çevresinden bir
    // pencere alıyoruz — ileri/geri tuşları için fazlasıyla yeterli.
    private var queue: List<RadioStation> = emptyList()

    init {
        connectToPlayer()
        observeSettings()
        bootstrapMyList()
    }

    // ── oynatıcı bağlantısı ──────────────────────────────────

    private fun connectToPlayer() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, RadyolaPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val mediaController = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = mediaController
                mediaController.repeatMode = Player.REPEAT_MODE_ALL
                mediaController.addListener(playerListener)
                syncPlayerState()
                querySleepTimer(mediaController)
                pendingAutoplayId?.let { id ->
                    pendingAutoplayId = null
                    stationById(id)?.let { play(it) }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlayerState()

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Canlı yayınlarda ICY başlığı çalan parçayı taşır. Yayın henüz kendi
            // başlığını göndermediyse burası MediaItem'daki istasyon adını taşır —
            // onu "çalan parça" diye göstermemek için çalan öğenin adıyla karşılaştırıyoruz.
            val track = mediaMetadata.title?.toString()?.trim()
            val playingName = controller?.currentMediaItem?.mediaId?.let { id -> stationById(id)?.name }
            _uiState.update {
                it.copy(nowPlayingTrack = track?.takeIf { t -> t.isNotEmpty() && t != playingName })
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    errorMessage = "Yayın açılamadı: ${it.current?.name ?: ""}".trim(),
                    isBuffering = false
                )
            }
        }
    }

    private fun syncPlayerState() {
        val player = controller ?: return
        val currentId = player.currentMediaItem?.mediaId
        _uiState.update { state ->
            state.copy(
                current = currentId?.let { id -> state.stations.firstOrNull { it.id == id } } ?: state.current,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING
            )
        }
    }

    // ── veri yükleme ─────────────────────────────────────────

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                applyFilters()
            }
        }
    }

    /** Ekrandaki listeyi tazeler — hangi mod açıksa onu yeniden yükler. */
    fun loadStations() {
        when (_uiState.value.mode) {
            ListMode.MY_LIST -> viewModelScope.launch { publishMyList() }
            ListMode.DISCOVER -> loadDirectory(forceRefresh = true)
        }
    }

    /**
     * İlk açılış: kullanıcı listesi yoksa kuratörlü listeden tohumlanır.
     *
     * Tohum bir kez atılır. Ağ yoksa gömülü yedek kullanılır — kullanıcıyı
     * boş bir ekranla karşılamamak için; sonraki açılışta gerçek tohum gelmez,
     * ama ayarlardaki "yeni kanallara bak" farkı kapatır.
     */
    private fun bootstrapMyList() {
        viewModelScope.launch {
            userList.load()
            if (!userList.isSeeded()) {
                _uiState.update { it.copy(isLoading = true) }
                userList.seed(repository.load(StationSource.CURATED))
                userList.load()
            }
            publishMyList()
            _uiState.update { it.copy(isLoading = false) }
            maybeAutoplay()
            preResolvePlaylists(userList.stations.value)
            migrateLegacyFavorites()
        }
    }

    /**
     * Eski favori kümesini kullanıcı listesine taşır.
     *
     * Favoriler yalnız kimlik saklıyordu. Kuratörlü olanlar zaten tohumda var;
     * asıl risk Keşfet'ten yıldızlananlar — meta verileri hiçbir yerde yok, o
     * yüzden dizin bir kez çekilip eşleştiriliyor. Taşıma bitince küme silinir,
     * böylece bir daha çalışmaz.
     */
    private suspend fun migrateLegacyFavorites() {
        val legacy = settingsStore.settings.first().favorites
        if (legacy.isEmpty()) return
        val missing = legacy - userList.ids
        if (missing.isNotEmpty()) {
            val pool = directory ?: repository.load(StationSource.DIRECTORY).also { directory = it }
            pool.filter { it.id in missing }.forEach { userList.add(it) }
            userList.load()
            publishMyList()
        }
        settingsStore.clearFavorites()
    }

    private fun publishMyList() {
        publishStations(ListMode.MY_LIST, userList.stations.value)
        _uiState.update { it.copy(myListIds = userList.ids) }
    }

    private fun loadDirectory(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                directory?.let { publishStations(ListMode.DISCOVER, it); return@launch }
            }
            _uiState.update { it.copy(directoryLoading = true) }
            repository.cached(StationSource.DIRECTORY)?.let {
                publishStations(ListMode.DISCOVER, it)
            }
            val stations = repository.load(StationSource.DIRECTORY)
            directory = stations
            publishStations(ListMode.DISCOVER, stations)
            _uiState.update { it.copy(directoryLoading = false) }
        }
    }

    /** Listem ↔ Keşfet geçişi. */
    fun setMode(mode: ListMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update {
            it.copy(mode = mode, query = "", selectedCountry = null, selectedGenre = null)
        }
        when (mode) {
            ListMode.MY_LIST -> viewModelScope.launch { publishMyList() }
            ListMode.DISCOVER -> loadDirectory()
        }
    }

    /**
     * Elle girilen istasyonu doğrulayıp listeye ekler.
     *
     * Adres kaydedilmeden önce gerçekten çalınabiliyor mu diye deneniyor:
     * yazım hatası aksi hâlde sessiz bir başarısızlığa dönüşüyor, kullanıcı
     * da kanalın neden çalmadığını anlayamıyor. `.pls`/`.m3u` ise çözülüp
     * çözülen adres saklanıyor.
     */
    fun addManualStation(
        name: String,
        url: String,
        city: String,
        countryCode: String,
        genre: String,
        onResult: (Result<RadioStation>) -> Unit
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedUrl = url.trim()
            if (trimmedName.isEmpty()) {
                onResult(Result.failure(IllegalArgumentException("İstasyon adı boş olamaz")))
                return@launch
            }
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                onResult(Result.failure(IllegalArgumentException("Adres http:// veya https:// ile başlamalı")))
                return@launch
            }

            _uiState.update { it.copy(verifyingStation = true) }
            val resolved = if (PlaylistResolver.needsResolving(trimmedUrl)) {
                PlaylistResolver.resolve(trimmedUrl)
            } else {
                trimmedUrl
            }
            val reachable = StreamProbe.canPlay(resolved)
            _uiState.update { it.copy(verifyingStation = false) }

            if (!reachable) {
                onResult(Result.failure(IllegalStateException("Bu adresten yayın alınamadı")))
                return@launch
            }

            // Konum "Şehir, Ülke" biçiminde kurulur; ülke adı seçilen koddan
            // gelir, böylece ülke şeridi elle yazımla parçalanmaz.
            val country = Countries.nameOf(countryCode)
            val station = RadioStation(
                name = trimmedName,
                url = trimmedUrl,
                location = listOf(city.trim(), country).filter { it.isNotEmpty() }.joinToString(", "),
                countryCode = countryCode,
                genre = genre.trim()
            )
            userList.add(station)
            userList.load()
            _uiState.update { it.copy(myListIds = userList.ids) }
            if (_uiState.value.mode == ListMode.MY_LIST) publishMyList()
            onResult(Result.success(station))
        }
    }

    /**
     * Yıldız düğmesi: Keşfet'te "listeme ekle", Listem'de "çıkar".
     *
     * Ayrı bir favori kümesi tutmuyoruz — liste zaten kullanıcının seçtikleri.
     */
    fun toggleInMyList(station: RadioStation) {
        viewModelScope.launch {
            userList.toggle(station)
            _uiState.update { it.copy(myListIds = userList.ids) }
            if (_uiState.value.mode == ListMode.MY_LIST) publishMyList()
        }
    }

    /**
     * Listeyi ekrana bağlar: ülke ve tür şeritlerini kurar, filtreleri uygular.
     */
    private fun publishStations(mode: ListMode, stations: List<RadioStation>) {
        val countries = stations.map { it.country }.distinct().sorted()

        // Kullanıcı listesinde birkaç tür var, hepsi çipe sığar. Dizinde 438 tür
        // var; en sık geçenler dışını göstermek şeridi kullanılmaz hâle getirir.
        val genreCounts = stations
            .flatMap { it.genre.split("/") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
        val genres = genreCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_GENRE_CHIPS)
            .map { it.key }
            .sorted()

        _uiState.update {
            if (it.mode != mode) it // kullanıcı beklerken mod değiştirdi
            else it.copy(stations = stations, countries = countries, genres = genres)
        }
        applyFilters()
    }

    /** Son çalınan istasyonu, ayar açıksa uygulama açılışında başlatır. */
    private fun maybeAutoplay() {
        val state = _uiState.value
        if (!state.settings.autoplayOnStart || state.current != null) return
        val station = userList.stations.value.firstOrNull { it.id == state.settings.lastStationId }
            ?: return
        if (controller == null) pendingAutoplayId = station.id else play(station)
    }

    /**
     * `.pls` / `.m3u` adreslerini arka planda çözer.
     *
     * Bildirimden "sonraki istasyon" tuşuna basıldığında sıradaki öğe hazır olsun diye
     * çalmadan önce toplu çözülür.
     */
    private fun preResolvePlaylists(stations: List<RadioStation>) {
        viewModelScope.launch {
            val pending = stations.filter {
                PlaylistResolver.needsResolving(it.url) && it.url !in resolvedUrls
            }
            if (pending.isEmpty()) return@launch
            pending.map { station ->
                async { station.url to PlaylistResolver.resolve(station.url) }
            }.awaitAll().forEach { (raw, resolved) -> resolvedUrls[raw] = resolved }
            refreshQueueUrls()
        }
    }

    // ── filtreleme ───────────────────────────────────────────

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        applyFilters()
    }

    fun selectCountry(country: String?) {
        _uiState.update { it.copy(selectedCountry = country) }
        applyFilters()
    }

    fun selectGenre(genre: String?) {
        _uiState.update { it.copy(selectedGenre = genre) }
        applyFilters()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(query = "", selectedCountry = null, selectedGenre = null)
        }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val query = state.query.trim()
            val filtered = state.stations.filter { station ->
                (state.selectedCountry == null || station.country == state.selectedCountry) &&
                    (state.selectedGenre == null || station.genre.contains(state.selectedGenre, true)) &&
                    (query.isEmpty() || station.searchText.contains(query, ignoreCase = true))
            }
            // Kuratörlü listenin sırası elle verilmiş, korunur. Dizinde 3.400 kayıt
            // rastgele sırada; oy en anlamlı sıralama ölçütü.
            val visible = if (state.isDiscovering) filtered.sortedByDescending { it.votes } else filtered
            state.copy(visible = visible)
        }
    }

    // ── oynatma ──────────────────────────────────────────────

    fun play(station: RadioStation) {
        val player = controller ?: run {
            pendingAutoplayId = station.id
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(current = station, errorMessage = null, nowPlayingTrack = null) }

            // Dokunulan istasyon çalma listesiyse önce çöz — beklemeden çalmak hataya düşer.
            if (PlaylistResolver.needsResolving(station.url) && station.url !in resolvedUrls) {
                _uiState.update { it.copy(isBuffering = true) }
                resolvedUrls[station.url] = PlaylistResolver.resolve(station.url)
            }

            // Sıra = ekranda görünen listeden bir pencere; bildirimdeki ileri/geri
            // de bu sırayı izler.
            queue = queueWindowAround(station)
            val index = queue.indexOfFirst { it.id == station.id }.coerceAtLeast(0)

            player.setMediaItems(queue.map(::toMediaItem), index, 0L)
            player.prepare()
            player.play()

            settingsStore.setLastStation(station.id)
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            // Canlı yayında duraklatma bağlantıyı düşürür; baştan hazırlayıp devam ediyoruz.
            player.prepare()
            player.play()
        } else {
            _uiState.value.current?.let { play(it) }
        }
    }

    fun skipToNext() = skip(1)

    fun skipToPrevious() = skip(-1)

    private fun skip(direction: Int) {
        controller ?: return
        val visible = _uiState.value.visible
        if (visible.isEmpty()) return
        val currentIndex = visible.indexOfFirst { it.id == _uiState.value.current?.id }
        val nextIndex = ((currentIndex + direction) + visible.size) % visible.size
        play(visible[nextIndex])
    }

    /**
     * Oynatıcıya verilecek sırayı [station] çevresinden keser.
     *
     * Liste [MAX_QUEUE_SIZE] altındaysa olduğu gibi kullanılır; büyükse
     * istasyonun iki yanından eşit pay alınır.
     */
    private fun queueWindowAround(station: RadioStation): List<RadioStation> {
        val visible = _uiState.value.visible.ifEmpty { return listOf(station) }
        if (visible.size <= MAX_QUEUE_SIZE) return visible
        val index = visible.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        val half = MAX_QUEUE_SIZE / 2
        val start = (index - half).coerceIn(0, visible.size - MAX_QUEUE_SIZE)
        return visible.subList(start, start + MAX_QUEUE_SIZE)
    }

    fun stop() {
        controller?.run {
            pause()
            clearMediaItems()
        }
        queue = emptyList()
        _uiState.update { it.copy(current = null, isPlaying = false, nowPlayingTrack = null) }
    }

    /** Çözülen adresler geldiğinde sıradaki öğeleri güncel URL'lerle tazeler. */
    private fun refreshQueueUrls() {
        val player = controller ?: return
        if (player.mediaItemCount == 0 || queue.isEmpty()) return
        val currentId = player.currentMediaItem?.mediaId ?: return
        val index = queue.indexOfFirst { it.id == currentId }
        if (index < 0) return
        // Çalan öğeye dokunmadan sıradakilerin adreslerini yenile.
        if (index + 1 <= queue.lastIndex) {
            player.replaceMediaItems(
                index + 1,
                player.mediaItemCount,
                queue.subList(index + 1, queue.size).map(::toMediaItem)
            )
        }
        if (index > 0) {
            player.replaceMediaItems(0, index, queue.subList(0, index).map(::toMediaItem))
        }
    }

    private fun toMediaItem(station: RadioStation): MediaItem =
        MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(resolvedUrls[station.url] ?: station.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(listOfNotNull(
                        station.location.takeIf { it.isNotEmpty() },
                        station.genre.takeIf { it.isNotEmpty() }
                    ).joinToString(" · "))
                    .setStation(station.name)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
            .build()

    // ── favoriler & ayarlar ──────────────────────────────────

    fun setRememberStation(value: Boolean) {
        viewModelScope.launch { settingsStore.setRememberStation(value) }
    }

    fun setAutoplayOnStart(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoplayOnStart(value) }
    }

    /**
     * Kuratörlü listede olup kullanıcıda olmayanları ekler.
     *
     * Tohum bir kez atıldığı için sonradan eklenen kanallar kullanıcıya
     * ulaşmıyor. Bunu sessizce yapmıyoruz — kullanıcının bilerek çıkardığı
     * bir istasyon geri gelmemeli, bu yüzden yalnız istendiğinde çalışır.
     */
    fun addNewCuratedStations(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val curated = repository.load(StationSource.CURATED)
            val missing = userList.missingFrom(curated)
            missing.forEach { userList.add(it) }
            if (missing.isNotEmpty()) {
                userList.load()
                _uiState.update { it.copy(myListIds = userList.ids) }
                if (_uiState.value.mode == ListMode.MY_LIST) publishMyList()
            }
            onResult(missing.size)
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    // ── uyku zamanlayıcı ─────────────────────────────────────

    /**
     * [minutes] dakika sonra çalmayı durdurur; 0 zamanlayıcıyı iptal eder.
     *
     * Asıl sayaç serviste çalışır (bkz. [RadyolaPlaybackService]): burada
     * tutulsaydı kullanıcı uygulamayı son kullanılanlardan kaydırdığında
     * ViewModel'le birlikte ölür, yayın sabaha kadar çalardı — zamanlayıcının
     * tam da önlemesi gereken senaryo. Buradaki iş yalnız ekrandaki geri sayım.
     */
    fun setSleepTimer(minutes: Int) {
        val c = controller ?: return
        c.sendCustomCommand(
            SessionCommand(RadyolaPlaybackService.CMD_SLEEP_SET, Bundle.EMPTY),
            bundleOf(RadyolaPlaybackService.KEY_SLEEP_MINUTES to minutes)
        )
        startSleepDisplay(minutes, minutes * 60)
    }

    /**
     * Servisteki zamanlayıcıyı sorup ekrandaki geri sayımı ona bağlar.
     *
     * Uygulama kapatılıp yeniden açıldığında zamanlayıcı serviste sürüyor
     * olabilir; ayarlar bunu bilmezse "Kapalı" gösterir ve kullanıcı yanlışlıkla
     * ikinci kez kurar.
     */
    private fun querySleepTimer(c: MediaController) {
        val future = c.sendCustomCommand(
            SessionCommand(RadyolaPlaybackService.CMD_SLEEP_QUERY, Bundle.EMPTY),
            Bundle.EMPTY
        )
        future.addListener(
            {
                val result = runCatching { future.get() }.getOrNull() ?: return@addListener
                startSleepDisplay(
                    result.extras.getInt(RadyolaPlaybackService.KEY_SLEEP_MINUTES, 0),
                    result.extras.getInt(RadyolaPlaybackService.KEY_SLEEP_REMAINING_SEC, 0)
                )
            },
            ContextCompat.getMainExecutor(getApplication())
        )
    }

    /** Ekrandaki geri sayım — yalnız görüntü; süre dolunca durduran taraf servis. */
    private fun startSleepDisplay(minutes: Int, remainingSec: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0 || remainingSec <= 0) {
            _uiState.update { it.copy(sleepTimerMinutes = 0, sleepTimerRemainingSec = 0) }
            return
        }
        _uiState.update { it.copy(sleepTimerMinutes = minutes, sleepTimerRemainingSec = remainingSec) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = remainingSec
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            _uiState.update { it.copy(sleepTimerMinutes = 0, sleepTimerRemainingSec = 0) }
        }
    }

    private fun stationById(id: String): RadioStation? =
        _uiState.value.stations.firstOrNull { it.id == id }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}

/** Tür çipi şeridinde gösterilecek en fazla tür sayısı. */
private const val MAX_GENRE_CHIPS = 24

/** Oynatıcıya verilen sıranın üst sınırı. */
private const val MAX_QUEUE_SIZE = 100

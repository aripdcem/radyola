package com.aripd.radyola

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aripd.radyola.data.AppSettings
import com.aripd.radyola.data.RadioStation
import com.aripd.radyola.data.SettingsStore
import com.aripd.radyola.data.StationRepository
import com.aripd.radyola.player.PlaylistResolver
import com.aripd.radyola.player.RadyolaPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uygulamanın tüm ekran durumu. */
data class UiState(
    val stations: List<RadioStation> = emptyList(),
    val visible: List<RadioStation> = emptyList(),
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val query: String = "",
    val selectedCountry: String? = null,
    val selectedGenre: String? = null,
    val favoritesOnly: Boolean = false,
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
        get() = query.isNotBlank() || selectedCountry != null || selectedGenre != null || favoritesOnly
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StationRepository(application)
    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private val resolvedUrls = mutableMapOf<String, String>()
    private var sleepTimerJob: Job? = null
    private var pendingAutoplayId: String? = null

    init {
        connectToPlayer()
        observeSettings()
        loadStations()
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

    fun loadStations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Önce önbellek: liste anında görünsün, ağ yavaşsa da ekran boş kalmasın.
            repository.cachedStations()?.let { cached -> publishStations(cached) }

            val stations = repository.loadStations()
            publishStations(stations)
            _uiState.update { it.copy(isLoading = false) }

            maybeAutoplay()
            preResolvePlaylists(stations)
        }
    }

    private fun publishStations(stations: List<RadioStation>) {
        val countries = stations.map { it.country }.distinct().sorted()
        val genres = stations
            .flatMap { it.genre.split("/") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        _uiState.update { it.copy(stations = stations, countries = countries, genres = genres) }
        applyFilters()
    }

    /** Son çalınan istasyonu, ayar açıksa uygulama açılışında başlatır. */
    private fun maybeAutoplay() {
        val state = _uiState.value
        if (!state.settings.autoplayOnStart || state.current != null) return
        val station = stationById(state.settings.lastStationId) ?: return
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

    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        applyFilters()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(query = "", selectedCountry = null, selectedGenre = null, favoritesOnly = false)
        }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val query = state.query.trim()
            val visible = state.stations.filter { station ->
                (state.selectedCountry == null || station.country == state.selectedCountry) &&
                    (state.selectedGenre == null || station.genre.contains(state.selectedGenre, true)) &&
                    (!state.favoritesOnly || station.id in state.settings.favorites) &&
                    (query.isEmpty() || station.matches(query))
            }
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

            // Sıra = ekranda görünen liste; bildirimdeki ileri/geri de bu sırayı izler.
            val queue = _uiState.value.visible.ifEmpty { listOf(station) }
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
        val player = controller ?: return
        val queue = _uiState.value.visible
        if (queue.isEmpty()) return
        val currentIndex = queue.indexOfFirst { it.id == _uiState.value.current?.id }
        val nextIndex = ((currentIndex + direction) + queue.size) % queue.size
        play(queue[nextIndex])
    }

    fun stop() {
        controller?.run {
            pause()
            clearMediaItems()
        }
        _uiState.update { it.copy(current = null, isPlaying = false, nowPlayingTrack = null) }
    }

    /** Çözülen adresler geldiğinde sıradaki öğeleri güncel URL'lerle tazeler. */
    private fun refreshQueueUrls() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val queue = _uiState.value.visible
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

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch { settingsStore.toggleFavorite(station.id) }
    }

    fun setRememberStation(value: Boolean) {
        viewModelScope.launch { settingsStore.setRememberStation(value) }
    }

    fun setAutoplayOnStart(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoplayOnStart(value) }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    // ── uyku zamanlayıcı ─────────────────────────────────────

    /** [minutes] dakika sonra çalmayı durdurur; 0 zamanlayıcıyı iptal eder. */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerMinutes = 0, sleepTimerRemainingSec = 0) }
            return
        }
        _uiState.update { it.copy(sleepTimerMinutes = minutes, sleepTimerRemainingSec = minutes * 60) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            controller?.pause()
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

/** Ad, konum ve tür alanlarında büyük/küçük harf duyarsız arama. */
private fun RadioStation.matches(query: String): Boolean {
    val needle = query.lowercase()
    return name.lowercase().contains(needle) ||
        location.lowercase().contains(needle) ||
        genre.lowercase().contains(needle)
}

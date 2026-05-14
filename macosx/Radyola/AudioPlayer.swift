import AVFoundation
import MediaPlayer

// ──────────────────────────────────────────────
// AVPlayer Tabanlı Ses Oynatıcı
// ──────────────────────────────────────────────

/// AVPlayer tabanlı internet radyo akışı oynatıcı.
///
/// AVPlayer, Apple'ın yüksek seviyeli medya oynatma framework'üdür.
/// MP3, AAC, HLS (m3u8) ve diğer tüm formatları native olarak destekler.
@Observable
final class AudioPlayer {
    private var player: AVPlayer?
    private var timeObserver: Any?

    private(set) var currentStation: RadioStation?
    private(set) var isPlaying: Bool = false
    private(set) var isPaused: Bool = false

    private let settings = SettingsManager.shared

    var state: String {
        if isPlaying { return "playing" }
        if isPaused { return "paused" }
        return "stopped"
    }

    init() {
        setupRemoteCommands()
    }

    // ── Genel API ──

    /// Belirtilen istasyonu çalmaya başlar.
    func play(_ station: RadioStation) {
        if currentStation == station && isPlaying { return }

        stop()
        currentStation = station

        guard let url = URL(string: station.url) else { return }

        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        player = AVPlayer(playerItem: item)
        player?.volume = Float(settings.volume) / 100.0
        player?.play()

        isPlaying = true
        isPaused = false

        updateNowPlayingInfo()

        // Son istasyonu kaydet
        if settings.rememberStation {
            settings.lastStation = station.title
        }
    }

    /// Çalmayı duraklatır.
    func pause() {
        player?.pause()
        isPlaying = false
        isPaused = true
        updateNowPlayingInfo()
    }

    /// Duraklatılmış çalmayı sürdürür.
    func resume() {
        player?.play()
        isPlaying = true
        isPaused = false
        updateNowPlayingInfo()
    }

    /// Çalmayı durdurur ve kaynakları serbest bırakır.
    func stop() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        player = nil
        currentStation = nil
        isPlaying = false
        isPaused = false
        clearNowPlayingInfo()
    }

    /// Aynı istasyonsa play/pause toggle, farklıysa yeni istasyonu çal.
    func toggle(_ station: RadioStation) {
        if currentStation == station {
            if isPlaying {
                pause()
            } else {
                resume()
            }
        } else {
            play(station)
        }
    }

    /// Ses seviyesini ayarlar (0-100).
    func setVolume(_ percent: Int) {
        let clamped = max(0, min(100, percent))
        player?.volume = Float(clamped) / 100.0
        settings.volume = clamped
    }

    /// Mevcut ses seviyesi (0-100).
    var volume: Int {
        settings.volume
    }

    // ── Now Playing (Kontrol Merkezi / Media Keys) ──

    /// Media key entegrasyonu — macOS Kontrol Merkezi'nde gösterim.
    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            if self?.currentStation != nil {
                self?.resume()
                return .success
            }
            return .commandFailed
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self, let station = self.currentStation else { return .commandFailed }
            self.toggle(station)
            return .success
        }

        center.stopCommand.isEnabled = true
        center.stopCommand.addTarget { [weak self] _ in
            self?.stop()
            return .success
        }
    }

    /// Now Playing bilgilerini günceller (Kontrol Merkezi).
    private func updateNowPlayingInfo() {
        guard let station = currentStation else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: station.title,
            MPMediaItemPropertyArtist: "İnternet Radyo",
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
        ]
        if !station.genre.isEmpty {
            info[MPMediaItemPropertyGenre] = station.genre
        }
        if !station.location.isEmpty {
            info[MPMediaItemPropertyAlbumTitle] = "\(station.flag) \(station.location)"
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    /// Now Playing bilgilerini temizler.
    private func clearNowPlayingInfo() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}

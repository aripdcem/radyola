import SwiftUI

// ──────────────────────────────────────────────
// Radyola — macOS Menü Çubuğu Radyo Çalar
// ──────────────────────────────────────────────

/// macOS 14+ (Sonoma) menü çubuğu internet radyo uygulaması.
///
/// SwiftUI MenuBarExtra + AVFoundation tabanlı.
/// Google Sheets'ten dinamik istasyon listesi çeker.
@main
struct RadyolaApp: App {
    @State private var stations: [RadioStation] = []
    @State private var player = AudioPlayer()
    @State private var isLoading = true

    private let settings = SettingsManager.shared

    var body: some Scene {
        MenuBarExtra {
            if isLoading {
                Text("İstasyonlar yükleniyor...")
                    .foregroundStyle(.secondary)
            } else {
                StationMenuBuilder(
                    stations: stations,
                    player: player,
                    onSkip: { direction in skip(direction) }
                )
            }
        } label: {
            menuBarLabel
        }
    }

    /// Menü çubuğu ikonu ve etiketi.
    @ViewBuilder
    private var menuBarLabel: some View {
        if let station = player.currentStation {
            let icon = player.isPlaying ? "radio.fill" : "radio"
            Label("\(station.title)", systemImage: icon)
        } else {
            Label("Radyola", systemImage: "radio")
        }
    }

    /// Uygulama başladığında istasyonları yükle ve autoplay uygula.
    init() {
        Task {
            let fetched = await fetchStations()
            await MainActor.run {
                stations = fetched
                isLoading = false
                applyStartupSettings()
            }
        }
    }

    /// Sonraki/önceki istasyona geçiş.
    private func skip(_ direction: Int) {
        guard !stations.isEmpty else { return }
        guard let current = player.currentStation,
              let idx = stations.firstIndex(of: current) else {
            player.play(stations[0])
            return
        }
        var newIdx = idx + direction
        if newIdx < 0 { newIdx = stations.count - 1 }
        if newIdx >= stations.count { newIdx = 0 }
        player.play(stations[newIdx])
    }

    /// Başlangıç ayarlarını uygula (autoplay, son istasyon).
    private func applyStartupSettings() {
        let lastName = settings.lastStation
        guard !lastName.isEmpty, settings.rememberStation else { return }

        if let station = stations.first(where: { $0.title == lastName }) {
            if settings.autoplayOnStart {
                player.play(station)
            }
        }
    }
}

import SwiftUI

// ──────────────────────────────────────────────
// Menü Çubuğu Menüsü Oluşturucu
// ──────────────────────────────────────────────

/// Radyola menü çubuğu menüsünü oluşturur.
///
/// Menü yapısı:
/// ```
///  ── Radyola ──────────────
///  🎵 Çalıyor — Açık Radyo
///  ─────────────────────────
///  🇹🇷 Türkiye ▸
///     🇹🇷  Açık Radyo  [Eclectic]  (İstanbul)
///     🇹🇷  ITU Radio Jazz  [Jazz / Blues]  (İstanbul)
///  🇧🇪 Belgium ▸
///     ...
///  ─────────────────────────
///  ▶ Çal / ⏸ Duraklat
///  ⏹ Durdur
///  🔊 Ses: %75 ▸
///  ─────────────────────────
///  ⚙ Ayarlar ▸
///  ❌ Çıkış
/// ```
struct StationMenuBuilder: View {
    let stations: [RadioStation]
    let player: AudioPlayer
    let onSkip: (Int) -> Void

    private let settings = SettingsManager.shared

    var body: some View {
        // ── Now Playing Header ──
        if let station = player.currentStation {
            let stateText = player.isPaused ? "⏸ Duraklatıldı" : "🎵 Çalıyor"
            Text("\(stateText) — \(station.title)")
                .font(.headline)
            if !station.genre.isEmpty {
                Text("\(station.flag) \(station.location)  ·  \(station.genre)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Divider()
        }

        // ── Ülke Alt Menüleri ──
        let groups = groupStationsByCountry(stations)
        ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
            let countryFlag = group.stations.first?.flag ?? "📻"
            let hasActive = group.stations.contains { $0 == player.currentStation }
            let label = hasActive
                ? "\(countryFlag) \(group.country) ✦"
                : "\(countryFlag) \(group.country) (\(group.stations.count))"

            Menu(label) {
                ForEach(group.stations) { station in
                    Button {
                        player.toggle(station)
                    } label: {
                        if station == player.currentStation {
                            Label(station.menuLabel, systemImage: player.isPlaying ? "speaker.wave.2.fill" : "pause.fill")
                        } else {
                            Text(station.menuLabel)
                        }
                    }
                }
            }
        }

        Divider()

        // ── Oynatma Kontrolleri ──
        Button {
            onSkip(-1)
        } label: {
            Label("Önceki İstasyon", systemImage: "backward.fill")
        }
        .keyboardShortcut("[", modifiers: [.command])
        .disabled(player.currentStation == nil)

        Button {
            if let station = player.currentStation {
                player.toggle(station)
            }
        } label: {
            if player.isPlaying {
                Label("Duraklat", systemImage: "pause.fill")
            } else if player.isPaused {
                Label("Devam", systemImage: "play.fill")
            } else {
                Label("Çal", systemImage: "play.fill")
            }
        }
        .keyboardShortcut(" ", modifiers: [])
        .disabled(player.currentStation == nil)

        Button {
            onSkip(1)
        } label: {
            Label("Sonraki İstasyon", systemImage: "forward.fill")
        }
        .keyboardShortcut("]", modifiers: [.command])
        .disabled(player.currentStation == nil)

        Button {
            player.stop()
        } label: {
            Label("Durdur", systemImage: "stop.fill")
        }
        .keyboardShortcut(".", modifiers: [.command])
        .disabled(player.currentStation == nil)

        Divider()

        // ── Ses Seviyesi ──
        Menu("🔊 Ses: %\(player.volume)") {
            ForEach([(0, "🔇 Sessiz"), (25, "🔈 %25"), (50, "🔉 %50"), (75, "🔊 %75"), (100, "🔊 %100")], id: \.0) { percent, label in
                Button {
                    player.setVolume(percent)
                } label: {
                    if player.volume == percent {
                        Label(label, systemImage: "checkmark")
                    } else {
                        Text(label)
                    }
                }
            }
        }

        Divider()

        // ── Ayarlar ──
        Menu("⚙ Ayarlar") {
            Button {
                settings.autoplayOnStart.toggle()
            } label: {
                Label("Başlangıçta otomatik çal",
                      systemImage: settings.autoplayOnStart ? "checkmark.square.fill" : "square")
            }

            Button {
                settings.rememberStation.toggle()
            } label: {
                Label("Son istasyonu hatırla",
                      systemImage: settings.rememberStation ? "checkmark.square.fill" : "square")
            }
        }

        Divider()

        // ── Çıkış ──
        Button {
            NSApplication.shared.terminate(nil)
        } label: {
            Label("Çıkış", systemImage: "xmark.circle")
        }
        .keyboardShortcut("q", modifiers: [.command])
    }
}

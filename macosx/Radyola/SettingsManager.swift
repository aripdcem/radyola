import Foundation

// ──────────────────────────────────────────────
// Ayarlar (Settings) Yönetimi
// ──────────────────────────────────────────────

/// UserDefaults tabanlı ayar yöneticisi.
@Observable
final class SettingsManager {
    static let shared = SettingsManager()

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let autoplayOnStart = "autoplayOnStart"
        static let rememberStation = "rememberStation"
        static let lastStation = "lastStation"
        static let volume = "volume"
    }

    /// Başlangıçta son istasyonu otomatik çal
    var autoplayOnStart: Bool {
        get { defaults.bool(forKey: Keys.autoplayOnStart) }
        set { defaults.set(newValue, forKey: Keys.autoplayOnStart) }
    }

    /// Son çalınan istasyonu hatırla
    var rememberStation: Bool {
        get { defaults.object(forKey: Keys.rememberStation) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Keys.rememberStation) }
    }

    /// Son çalınan istasyonun adı
    var lastStation: String {
        get { defaults.string(forKey: Keys.lastStation) ?? "" }
        set { defaults.set(newValue, forKey: Keys.lastStation) }
    }

    /// Ses seviyesi (0-100)
    var volume: Int {
        get {
            let val = defaults.object(forKey: Keys.volume) as? Int
            return val ?? 100
        }
        set {
            let clamped = max(0, min(100, newValue))
            defaults.set(clamped, forKey: Keys.volume)
        }
    }

    private init() {}
}

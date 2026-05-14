import Foundation

// ──────────────────────────────────────────────
// Veri Modeli
// ──────────────────────────────────────────────

/// Google Sheets CSV export URL — kanal listesi buradan çekilir
let stationsCSVURL =
    "https://docs.google.com/spreadsheets/d/"
    + "1WetccPDwGuUAqNQzUTVNCKy1k48MDM1bvLnDlfdRhis/export?format=csv"

/// Ülke adından bayrak emoji'sine eşleme
private let countryFlags: [String: String] = [
    "türkiye": "🇹🇷", "turkey": "🇹🇷",
    "belgium": "🇧🇪",
    "united kingdom": "🇬🇧", "uk": "🇬🇧",
    "greece": "🇬🇷",
    "russia": "🇷🇺",
    "spain": "🇪🇸",
    "united states": "🇺🇸", "usa": "🇺🇸",
    "france": "🇫🇷",
    "germany": "🇩🇪",
    "netherlands": "🇳🇱",
    "italy": "🇮🇹",
    "japan": "🇯🇵",
    "portugal": "🇵🇹",
    "ireland": "🇮🇪",
    "canada": "🇨🇦",
    "australia": "🇦🇺",
    "austria": "🇦🇹",
    "switzerland": "🇨🇭",
    "sweden": "🇸🇪",
    "norway": "🇳🇴",
    "denmark": "🇩🇰",
    "finland": "🇫🇮",
    "poland": "🇵🇱",
    "czech republic": "🇨🇿",
    "hungary": "🇭🇺",
    "romania": "🇷🇴",
    "bulgaria": "🇧🇬",
    "croatia": "🇭🇷",
    "serbia": "🇷🇸",
    "brazil": "🇧🇷",
    "argentina": "🇦🇷",
    "mexico": "🇲🇽",
    "india": "🇮🇳",
    "china": "🇨🇳",
    "south korea": "🇰🇷",
]

/// Bir radyo istasyonunu temsil eder.
struct RadioStation: Identifiable, Equatable {
    let id: UUID
    let title: String
    let url: String
    let location: String
    let genre: String

    init(title: String, url: String, location: String = "", genre: String = "") {
        self.id = UUID()
        self.title = title
        self.url = url
        self.location = location
        self.genre = genre
    }

    /// Konum string'inden ülke bayrağı emoji'si çıkarır.
    var flag: String {
        guard !location.isEmpty else { return "📻" }
        let parts = location.split(separator: ",")
        let country = parts.last?.trimmingCharacters(in: .whitespaces).lowercased() ?? ""
        return countryFlags[country] ?? "📻"
    }

    /// Konum string'inden şehir adını çıkarır.
    var city: String {
        guard !location.isEmpty else { return "" }
        let parts = location.split(separator: ",")
        return parts.first.map { String($0).trimmingCharacters(in: .whitespaces) } ?? ""
    }

    /// Konum string'inden ülke adını çıkarır.
    var country: String {
        guard !location.isEmpty else { return "Diğer" }
        let parts = location.split(separator: ",")
        return parts.last.map { String($0).trimmingCharacters(in: .whitespaces) } ?? "Diğer"
    }

    /// Menü etiketi: "🇹🇷  Açık Radyo  [Eclectic]  (İstanbul)"
    var menuLabel: String {
        var label = "\(flag)  \(title)"
        if !genre.isEmpty { label += "  [\(genre)]" }
        if !city.isEmpty { label += "  (\(city))" }
        return label
    }

    static func == (lhs: RadioStation, rhs: RadioStation) -> Bool {
        lhs.title == rhs.title && lhs.url == rhs.url
    }
}

// ──────────────────────────────────────────────
// Google Sheets CSV Parse
// ──────────────────────────────────────────────

/// Google Sheets'ten CSV olarak kanal listesini çeker ve parse eder.
func fetchStations() async -> [RadioStation] {
    guard let url = URL(string: stationsCSVURL) else {
        return fallbackStations()
    }

    do {
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue("Radyola/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: request)

        guard let csv = String(data: data, encoding: .utf8) else {
            return fallbackStations()
        }

        let stations = parseCSV(csv)
        return stations.isEmpty ? fallbackStations() : stations
    } catch {
        print("radyola: Google Sheets'e bağlanılamadı (\(error)) — fallback kullanılıyor")
        return fallbackStations()
    }
}

/// CSV string'ini RadioStation dizisine parse eder.
private func parseCSV(_ csv: String) -> [RadioStation] {
    var stations: [RadioStation] = []

    for line in csv.components(separatedBy: .newlines) {
        let row = parseCSVLine(line)
        guard row.count >= 3 else { continue }

        let title = row[1].trimmingCharacters(in: .whitespaces)
        let url = row[2].trimmingCharacters(in: .whitespaces)
        let location = row.count > 4 ? row[4].trimmingCharacters(in: .whitespaces) : ""
        let genre = row.count > 5 ? row[5].trimmingCharacters(in: .whitespaces) : ""

        guard !title.isEmpty, !url.isEmpty else { continue }
        stations.append(RadioStation(title: title, url: url, location: location, genre: genre))
    }

    return stations
}

/// CSV satırını alanlara ayırır (tırnak içindeki virgülleri korur).
private func parseCSVLine(_ line: String) -> [String] {
    var fields: [String] = []
    var current = ""
    var inQuotes = false

    for char in line {
        if char == "\"" {
            inQuotes.toggle()
        } else if char == "," && !inQuotes {
            fields.append(current)
            current = ""
        } else {
            current.append(char)
        }
    }
    fields.append(current)
    return fields
}

/// İnternet bağlantısı yoksa kullanılacak varsayılan istasyonlar.
func fallbackStations() -> [RadioStation] {
    [
        RadioStation(title: "Açık Radyo", url: "https://stream.34bit.net/ar.mp3",
                     location: "İstanbul, Türkiye", genre: "Eclectic"),
        RadioStation(title: "VRT Klara", url: "http://icecast-servers.vrtcdn.be/klara-high.mp3",
                     location: "Brussels, Belgium", genre: "Classical"),
        RadioStation(title: "BBC Radio 1", url: "http://lsn.lv/bbcradio.m3u8?station=bbc_radio_one&bitrate=96000",
                     location: "London, United Kingdom", genre: "Pop / Dance"),
        RadioStation(title: "Radio Panik", url: "https://streaming.domainepublic.net/radiopanik.mp3",
                     location: "Brussels, Belgium", genre: "Alternative"),
    ]
}

/// İstasyonları ülkeye göre gruplar. Sıralama: orijinal CSV sırası korunur.
func groupStationsByCountry(_ stations: [RadioStation]) -> [(country: String, stations: [RadioStation])] {
    var groups: [(String, [RadioStation])] = []
    var seen: [String: Int] = [:]

    for station in stations {
        let country = station.country
        if let idx = seen[country] {
            groups[idx].1.append(station)
        } else {
            seen[country] = groups.count
            groups.append((country, [station]))
        }
    }
    return groups
}

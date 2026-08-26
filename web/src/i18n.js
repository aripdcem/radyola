/**
 * İki dil, tek tablo. Seçim sırası: <aripd-radyola lang="tr|en"> özniteliği
 * (gömen sayfa karar verebilsin), yoksa tarayıcı dili. Sözcük dizilişi dile
 * göre değiştiği için ad taşıyan etiketler fonksiyon (playLabel, favLabel).
 *
 * Testler iki dilin anahtar kümelerinin bire bir aynı olduğunu doğrular —
 * yeni metin eklerken iki tabloya da eklemeyi unutmayı yapısal yakalar.
 */
export const LANGS = {
  en: {
    tagline: "Curated internet radio stations from around the world",
    tabCurated: "Curated",
    tabDiscover: "Discover",
    tabFavorites: "Favorites",
    searchCurated: "Search stations...",
    searchDirectory: "Search thousands of stations...",
    searchFavorites: "Search favorites...",
    searchAria: "Search stations",
    clearSearch: "Clear search",
    stationList: "Station list",
    all: "All",
    allGenres: "All Genres",
    noStations: "No stations found",
    noFavorites: "No favorites yet — tap the ♥ on any station to keep it here",
    loadFailed: "Failed to load stations.",
    retry: "Try again",
    selectStation: "Select a station",
    errGeneric: "Stream unavailable — try another station",
    errHttp: "HTTP-only stream — blocked by the browser",
    errHlsLoad: "Could not load HLS support — check your connection",
    errHlsUnsupported: "This browser cannot play HLS streams",
    httpTitle: "HTTP-only stream — may not play in the browser",
    website: "Visit website",
    favorite: "Favorite",
    prevStation: "Previous station",
    nextStation: "Next station",
    playPause: "Play/Pause",
    volume: "Volume",
    share: "Share station",
    linkCopied: "Link copied",
    playLabel: (name) => `Play ${name}`,
    favLabel: (name) => `Favorite ${name}`,
  },
  tr: {
    tagline: "Dünyanın dört bir yanından seçme internet radyoları",
    tabCurated: "Seçki",
    tabDiscover: "Keşfet",
    tabFavorites: "Favoriler",
    searchCurated: "İstasyon ara...",
    searchDirectory: "Binlerce istasyonda ara...",
    searchFavorites: "Favorilerde ara...",
    searchAria: "İstasyon ara",
    clearSearch: "Aramayı temizle",
    stationList: "İstasyon listesi",
    all: "Tümü",
    allGenres: "Tüm Türler",
    noStations: "İstasyon bulunamadı",
    noFavorites: "Henüz favori yok — istasyonların ♥ simgesine dokun",
    loadFailed: "İstasyonlar yüklenemedi.",
    retry: "Tekrar dene",
    selectStation: "Bir istasyon seç",
    errGeneric: "Yayın açılamadı — başka bir istasyon dene",
    errHttp: "Yalnız HTTP yayını — tarayıcı engelledi",
    errHlsLoad: "HLS desteği yüklenemedi — bağlantını kontrol et",
    errHlsUnsupported: "Bu tarayıcı HLS akışı çalamıyor",
    httpTitle: "Yalnız HTTP yayını — tarayıcıda çalmayabilir",
    website: "Siteye git",
    favorite: "Favori",
    prevStation: "Önceki istasyon",
    nextStation: "Sonraki istasyon",
    playPause: "Çal/Duraklat",
    volume: "Ses düzeyi",
    share: "İstasyonu paylaş",
    linkCopied: "Bağlantı kopyalandı",
    playLabel: (name) => `${name} çal`,
    favLabel: (name) => `${name} favorilere`,
  },
};

/** Öznitelik > tarayıcı dili > İngilizce. Saf: değerler dışarıdan gelir. */
export function pickLang(attrLang, navLang) {
  const attr = (attrLang || "").toLowerCase();
  if (attr.startsWith("tr")) return "tr";
  if (attr.startsWith("en")) return "en";
  return (navLang || "en").toLowerCase().startsWith("tr") ? "tr" : "en";
}

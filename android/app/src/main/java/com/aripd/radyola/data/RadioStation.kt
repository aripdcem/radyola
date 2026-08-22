package com.aripd.radyola.data

/**
 * Bir radyo istasyonunu temsil eder.
 *
 * Alanlar Google Sheets tablosundaki sütunlarla birebir eşleşir:
 * `[0] sıra, [1] ad, [2] akış URL'si, [3] web sitesi, [4] konum, [5] tür`
 */
data class RadioStation(
    val name: String,
    val url: String,
    val website: String = "",
    val location: String = "",
    val genre: String = ""
) {
    /** Konum string'inin son parçası ülke kabul edilir: "İstanbul, Türkiye" → "Türkiye" */
    val country: String
        get() = location.substringAfterLast(',', "").trim()
            .ifEmpty { location.trim() }
            .ifEmpty { "Diğer" }

    /** Konum string'inin ilk parçası şehir kabul edilir: "İstanbul, Türkiye" → "İstanbul" */
    val city: String
        get() = if (location.contains(',')) location.substringBefore(',').trim() else ""

    /** Ülkeye karşılık gelen bayrak emoji'si; eşleşme yoksa radyo emoji'si. */
    val flag: String
        get() = COUNTRY_FLAGS[country.lowercase()] ?: "📻"

    /** İstasyonu benzersiz kılan anahtar — favori ve "son istasyon" kaydında kullanılır. */
    val id: String
        get() = "$name|$url"
}

private val COUNTRY_FLAGS = mapOf(
    "türkiye" to "🇹🇷", "turkiye" to "🇹🇷", "turkey" to "🇹🇷",
    "belgium" to "🇧🇪", "belçika" to "🇧🇪",
    "united kingdom" to "🇬🇧", "uk" to "🇬🇧", "england" to "🇬🇧",
    "greece" to "🇬🇷", "yunanistan" to "🇬🇷",
    "russia" to "🇷🇺", "rusya" to "🇷🇺",
    "spain" to "🇪🇸", "united states" to "🇺🇸", "usa" to "🇺🇸",
    "france" to "🇫🇷", "germany" to "🇩🇪", "netherlands" to "🇳🇱",
    "italy" to "🇮🇹", "japan" to "🇯🇵", "portugal" to "🇵🇹",
    "ireland" to "🇮🇪", "canada" to "🇨🇦", "australia" to "🇦🇺",
    "austria" to "🇦🇹", "switzerland" to "🇨🇭", "sweden" to "🇸🇪",
    "norway" to "🇳🇴", "denmark" to "🇩🇰", "finland" to "🇫🇮",
    "poland" to "🇵🇱", "czech republic" to "🇨🇿", "hungary" to "🇭🇺",
    "romania" to "🇷🇴", "bulgaria" to "🇧🇬", "croatia" to "🇭🇷",
    "serbia" to "🇷🇸", "brazil" to "🇧🇷", "argentina" to "🇦🇷",
    "mexico" to "🇲🇽", "india" to "🇮🇳", "china" to "🇨🇳",
    "south korea" to "🇰🇷"
)

/** İnternet bağlantısı yoksa ve önbellek boşsa kullanılacak varsayılan istasyonlar. */
val FALLBACK_STATIONS = listOf(
    RadioStation(
        name = "Açık Radyo",
        url = "https://stream.34bit.net/ar.mp3",
        location = "İstanbul, Türkiye",
        genre = "Eclectic"
    ),
    RadioStation(
        name = "VRT Klara",
        url = "http://icecast-servers.vrtcdn.be/klara-high.mp3",
        location = "Brussels, Belgium",
        genre = "Classical"
    ),
    RadioStation(
        name = "BBC Radio 1",
        url = "http://lsn.lv/bbcradio.m3u8?station=bbc_radio_one&bitrate=96000",
        location = "London, United Kingdom",
        genre = "Pop / Dance"
    ),
    RadioStation(
        name = "Radio Panik",
        url = "https://streaming.domainepublic.net/radiopanik.mp3",
        location = "Brussels, Belgium",
        genre = "Alternative"
    )
)

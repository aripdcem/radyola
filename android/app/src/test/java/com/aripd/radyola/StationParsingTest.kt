package com.aripd.radyola

import com.aripd.radyola.data.RadioStation
import com.aripd.radyola.data.parseStations
import com.aripd.radyola.player.PlaylistResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationParsingTest {

    @Test
    fun `tüm alanlar okunur`() {
        val json = """
            [{
              "date": "2/1/2023",
              "title": "Açık Radyo",
              "url": "https://stream.34bit.net/ar.mp3",
              "website": "https://acikradyo.com.tr",
              "location": "İstanbul, Türkiye",
              "countryCode": "TR",
              "genre": "Eclectic"
            }]
        """.trimIndent()

        val stations = parseStations(json)

        assertEquals(1, stations.size)
        with(stations[0]) {
            assertEquals("Açık Radyo", name)
            assertEquals("https://stream.34bit.net/ar.mp3", url)
            assertEquals("https://acikradyo.com.tr", website)
            assertEquals("İstanbul, Türkiye", location)
            assertEquals("TR", countryCode)
            assertEquals("Eclectic", genre)
        }
    }

    @Test
    fun `çalınamayacak kayıtlar atlanır, sağlamlar korunur`() {
        val json = """
            [
              {"title": "Adressiz", "url": "", "location": "Paris, France"},
              {"title": "", "url": "https://example.com/a.mp3"},
              {"title": "Göreli adres", "url": "/stream.mp3"},
              {"title": "Geçerli", "url": "https://example.com/b.mp3", "location": "Roma, Italy"}
            ]
        """.trimIndent()

        val stations = parseStations(json)

        assertEquals(1, stations.size)
        assertEquals("Geçerli", stations[0].name)
    }

    @Test
    fun `eksik isteğe bağlı alanlar boş kabul edilir`() {
        val stations = parseStations("""[{"title": "Sade", "url": "https://example.com/s.mp3"}]""")

        assertEquals(1, stations.size)
        with(stations[0]) {
            assertEquals("", website)
            assertEquals("", location)
            assertEquals("", countryCode)
            assertEquals("", genre)
        }
    }

    @Test
    fun `boş dizi boş liste döndürür`() {
        assertTrue(parseStations("[]").isEmpty())
    }

    @Test
    fun `dizin kaydının oy alanı okunur`() {
        val json = """
            [{"title": "X", "url": "https://e.com/x.mp3", "countryCode": "TR",
              "genre": "Folk", "votes": 15646}]
        """.trimIndent()

        assertEquals(15646, parseStations(json).single().votes)
    }

    @Test
    fun `kuratörlü kayıtta oy sıfır kabul edilir`() {
        val json = """[{"title": "X", "url": "https://e.com/x.mp3"}]"""

        assertEquals(0, parseStations(json).single().votes)
    }

    @Test
    fun `arama metni ad konum ve türü kapsar`() {
        val station = RadioStation(
            name = "Açık Radyo", url = "http://x",
            location = "İstanbul, Türkiye", genre = "Eclectic"
        )

        assertTrue(station.searchText.contains("açık", ignoreCase = true))
        assertTrue(station.searchText.contains("türkiye", ignoreCase = true))
        assertTrue(station.searchText.contains("eclectic", ignoreCase = true))
    }

    @Test
    fun `bilinmeyen alanlar yok sayılır`() {
        // directory.json kalite alanları taşır; kuratörlü liste taşımaz.
        // Aynı ayrıştırıcı ikisini de okuyabilmeli.
        val json = """
            [{"title": "X", "url": "https://e.com/x.mp3", "votes": 9001,
              "bitrate": 128, "codec": "MP3", "hls": false, "lastCheckOk": true}]
        """.trimIndent()

        assertEquals("X", parseStations(json).single().name)
    }

    @Test
    fun `bayrak ISO kodundan türetilir`() {
        assertEquals("🇹🇷", RadioStation("x", "http://x", countryCode = "TR").flag)
        assertEquals("🇧🇪", RadioStation("x", "http://x", countryCode = "be").flag)
        assertEquals("🇦🇺", RadioStation("x", "http://x", countryCode = "AU").flag)
    }

    @Test
    fun `kod yoksa ülke adı tablosuna düşülür`() {
        val station = RadioStation("x", "http://x", location = "Brussels, Belgium")

        assertEquals("🇧🇪", station.flag)
    }

    @Test
    fun `geçersiz kod bayrak üretmez`() {
        assertEquals("📻", RadioStation("x", "http://x", countryCode = "XYZ").flag)
        assertEquals("📻", RadioStation("x", "http://x", countryCode = "12").flag)
        assertEquals("📻", RadioStation("x", "http://x").flag)
    }

    @Test
    fun `konumdan şehir ve ülke çıkarılır`() {
        val station = RadioStation("x", "http://x", location = "İstanbul, Türkiye")

        assertEquals("İstanbul", station.city)
        assertEquals("Türkiye", station.country)
    }

    @Test
    fun `konumsuz istasyon Diğer ülkesine düşer`() {
        assertEquals("Diğer", RadioStation("x", "http://x").country)
    }

    @Test
    fun `pls ve m3u çözülür m3u8 dokunulmaz`() {
        assertTrue(PlaylistResolver.needsResolving("http://160.75.86.29:8088/listen.pls?sid=3"))
        assertTrue(PlaylistResolver.needsResolving("http://example.com/stream.m3u"))
        assertFalse(PlaylistResolver.needsResolving("http://lsn.lv/bbcradio.m3u8?station=bbc_radio_one"))
        assertFalse(PlaylistResolver.needsResolving("https://stream.34bit.net/ar.mp3"))
    }

    @Test
    fun `pls gövdesinden ilk akış adresi alınır`() {
        val body = """
            [playlist]
            NumberOfEntries=1
            File1=http://160.75.86.29:8088/itucazmp3
            Title1=ITU Radio Jazz/Blues
            Length1=-1
            Version=2
        """.trimIndent()

        assertEquals("http://160.75.86.29:8088/itucazmp3", PlaylistResolver.firstStreamUrl(body))
    }

    @Test
    fun `m3u gövdesinde yorum satırları atlanır`() {
        val body = "#EXTM3U\n#EXTINF:-1,Radyo\nhttp://example.com/live.aac\n"

        assertEquals("http://example.com/live.aac", PlaylistResolver.firstStreamUrl(body))
    }

    @Test
    fun `akış içermeyen gövde null döner`() {
        assertNull(PlaylistResolver.firstStreamUrl("[playlist]\nNumberOfEntries=0\n"))
    }
}

package com.aripd.radyola

import com.aripd.radyola.data.RadioStation
import com.aripd.radyola.data.parseCsv
import com.aripd.radyola.player.PlaylistResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationParsingTest {

    @Test
    fun `tırnak içindeki virgül alanı bölmez`() {
        val csv = """2/1/2023,Açık Radyo,https://stream.34bit.net/ar.mp3,,"İstanbul, Türkiye",Eclectic"""
        val stations = parseCsv(csv)

        assertEquals(1, stations.size)
        assertEquals("Açık Radyo", stations[0].name)
        assertEquals("İstanbul, Türkiye", stations[0].location)
        assertEquals("Eclectic", stations[0].genre)
    }

    @Test
    fun `akış adresi olmayan satırlar atlanır`() {
        val csv = """
            Tarih,Ad,URL,Web,Konum,Tür
            2/1/2023,Geçerli,https://example.com/stream.mp3,,"Roma, Italy",Jazz
            2/1/2023,Adressiz,,,"Paris, France",Pop
        """.trimIndent()

        val stations = parseCsv(csv)

        assertEquals(1, stations.size)
        assertEquals("Geçerli", stations[0].name)
    }

    @Test
    fun `eksik sondaki sütunlar boş kabul edilir`() {
        val csv = "2/1/2023,Sade,https://example.com/s.mp3"
        val stations = parseCsv(csv)

        assertEquals(1, stations.size)
        assertEquals("", stations[0].location)
        assertEquals("", stations[0].genre)
    }

    @Test
    fun `konumdan şehir ülke ve bayrak çıkarılır`() {
        val station = RadioStation(name = "Test", url = "http://x", location = "İstanbul, Türkiye")

        assertEquals("İstanbul", station.city)
        assertEquals("Türkiye", station.country)
        assertEquals("🇹🇷", station.flag)
    }

    @Test
    fun `konumsuz istasyon Diğer ülkesine düşer`() {
        val station = RadioStation(name = "Test", url = "http://x")

        assertEquals("Diğer", station.country)
        assertEquals("📻", station.flag)
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

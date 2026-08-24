package com.aripd.radyola

import com.aripd.radyola.data.RadioStation
import com.aripd.radyola.data.curatedUrlUpdates
import com.aripd.radyola.data.missingByName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Yeni kanallara bak" birleştirme kuralları.
 *
 * Kimlik `ad|url` olduğu için kuratörlü listedeki bir adres düzeltmesi kimliği
 * değiştirir. Fark kimlikle alınsaydı düzeltilen istasyon "eksik" sanılıp
 * ikinci kez eklenirdi — biri ölü, biri sağlam iki kopya. Depo geçmişinde bu
 * düzeltmeler gerçekten var (ör. "fix(data): ölü üç yayının adresini güncelle").
 */
class UserListMergeTest {

    private fun station(name: String, url: String) = RadioStation(name = name, url = url)

    @Test
    fun `adresi düzeltilen istasyon eksik sayılmaz`() {
        val mine = listOf(station("Açık Radyo", "https://old.example/ar.mp3"))
        val curated = listOf(station("Açık Radyo", "https://new.example/ar.mp3"))

        assertTrue(missingByName(curated, mine).isEmpty())
    }

    @Test
    fun `gerçekten yeni kanal eksik listesine girer`() {
        val mine = listOf(station("Açık Radyo", "https://x/ar.mp3"))
        val curated = listOf(
            station("Açık Radyo", "https://x/ar.mp3"),
            station("LoungeFM", "https://x/lounge.mp3")
        )

        assertEquals(listOf("LoungeFM"), missingByName(curated, mine).map { it.name })
    }

    @Test
    fun `kullanıcının çıkardığı kanal istenirse geri gelir`() {
        // "Yeni kanallara bak" bilinçli bir eylem; çıkarılan kanalın orada
        // yeniden görünmesi bekleniyor — sessiz senkronda görünmemesi asıl kural.
        val curated = listOf(station("Açık Radyo", "https://x/ar.mp3"))

        assertEquals(1, missingByName(curated, emptyList()).size)
    }

    @Test
    fun `adres farkı güncelleme olarak raporlanır`() {
        val mine = listOf(station("Açık Radyo", "https://old.example/ar.mp3"))
        val curated = listOf(station("Açık Radyo", "https://new.example/ar.mp3"))

        val updates = curatedUrlUpdates(curated, mine)

        assertEquals(mapOf(mine[0].id to "https://new.example/ar.mp3"), updates)
    }

    @Test
    fun `aynı adres güncelleme üretmez`() {
        val mine = listOf(station("Açık Radyo", "https://x/ar.mp3"))
        val curated = listOf(station("Açık Radyo", "https://x/ar.mp3"))

        assertTrue(curatedUrlUpdates(curated, mine).isEmpty())
    }

    @Test
    fun `kuratörlü listede olmayan kullanıcı kanalına dokunulmaz`() {
        // Keşfet'ten ya da elle eklenen kanallar kuratörlü listede yok;
        // adresleri kullanıcının bildiği gibi kalmalı.
        val mine = listOf(station("Yerel FM", "https://yerel.example/live"))
        val curated = listOf(station("Açık Radyo", "https://x/ar.mp3"))

        assertTrue(curatedUrlUpdates(curated, mine).isEmpty())
    }

    @Test
    fun `birden çok düzeltme aynı geçişte uygulanır`() {
        val mine = listOf(
            station("A", "https://a-old.example/s"),
            station("B", "https://b.example/s"),
            station("C", "https://c-old.example/s")
        )
        val curated = listOf(
            station("A", "https://a-new.example/s"),
            station("B", "https://b.example/s"),
            station("C", "https://c-new.example/s")
        )

        val updates = curatedUrlUpdates(curated, mine)

        assertEquals(2, updates.size)
        assertEquals("https://a-new.example/s", updates[mine[0].id])
        assertEquals("https://c-new.example/s", updates[mine[2].id])
    }
}

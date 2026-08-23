package com.aripd.radyola

import com.aripd.radyola.data.parseStations
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Ayrıştırıcıyı depodaki gerçek `data/stations.json` dosyasına karşı çalıştırır.
 *
 * Birim testleri veri şemasını uydurma örneklerle sınıyor; bu test ise
 * kaynağın kendisi değiştiğinde (alan adı, biçim) haber vermesi için var.
 */
class RealDataContractTest {

    private val dataFile = File("../../data/stations.json")
    private val directoryFile = File("../../data/directory.json")

    @Test
    fun `kuratörlü liste ayrıştırılabiliyor`() {
        assumeTrue("data/stations.json bulunamadı — modül tek başına derleniyor", dataFile.exists())

        val stations = parseStations(dataFile.readText())

        assertTrue("En az bir istasyon okunmalı", stations.isNotEmpty())
        assertTrue(
            "Her istasyonun adı ve çalınabilir adresi olmalı",
            stations.all { it.name.isNotBlank() && it.url.startsWith("http") }
        )
    }

    @Test
    fun `her istasyon bayrak üretebiliyor`() {
        assumeTrue(dataFile.exists())

        val stations = parseStations(dataFile.readText())
        val flagless = stations.filter { it.flag == "📻" }

        assertTrue(
            "Bayrağı çözülemeyen istasyonlar: ${flagless.map { "${it.name} (${it.countryCode}/${it.country})" }}",
            flagless.isEmpty()
        )
    }

    @Test
    fun `Keşfet dizini ayrıştırılabiliyor`() {
        assumeTrue("data/directory.json bulunamadı", directoryFile.exists())

        val stations = parseStations(directoryFile.readText())

        assertTrue("Dizin binlerce istasyon taşımalı", stations.size > 1000)
        assertTrue(
            "Her istasyonun ISO ülke kodu olmalı",
            stations.all { it.countryCode.length == 2 }
        )
        assertTrue(
            "Oy alanı taşınmalı — Keşfet sıralaması buna dayanıyor",
            stations.count { it.votes > 0 } > stations.size / 2
        )
    }

    @Test
    fun `dizin tür etiketleri fasete uygun`() {
        assumeTrue(directoryFile.exists())

        val genres = parseStations(directoryFile.readText())
            .flatMap { it.genre.split("/") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val distinct = genres.distinct()
        assertTrue("Tür sayısı filtre için makul olmalı, bulunan: ${distinct.size}", distinct.size < 600)

        // Crawler temizliğinin bıraktığı gürültü türleri geri gelirse haber versin.
        val noise = distinct.filter { it.length <= 2 || it.length > 25 || it.matches(Regex("[\\d.,\\s]+")) }
        assertTrue("Gürültü etiketleri: $noise", noise.isEmpty())
    }
}
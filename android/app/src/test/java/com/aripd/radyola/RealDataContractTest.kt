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
}

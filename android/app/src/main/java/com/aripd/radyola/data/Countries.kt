package com.aripd.radyola.data

/**
 * Elle kanal eklerken seçilebilen ülkeler.
 *
 * Serbest metin bırakılsa kullanıcı "istanbul turkiye" / "İstanbul, Türkiye" /
 * "Istanbul/TR" yazar ve ülke şeridi parçalanır — crawler'da temizlediğimiz
 * etiket gürültüsünün aynısını kendi elimizle üretmiş oluruz. Ayrıca bayrak
 * ISO kodundan türetildiği için kodun kesin olması gerekiyor.
 */
object Countries {

    /** Görünen ad → ISO 3166-1 alpha-2. Kuratörlü listedeki adlandırmayı izler. */
    val byName: Map<String, String> = linkedMapOf(
        "Türkiye" to "TR",
        "Almanya" to "DE",
        "Amerika Birleşik Devletleri" to "US",
        "Avustralya" to "AU",
        "Avusturya" to "AT",
        "Belçika" to "BE",
        "Birleşik Krallık" to "GB",
        "Fransa" to "FR",
        "Hollanda" to "NL",
        "İspanya" to "ES",
        "İsveç" to "SE",
        "İsviçre" to "CH",
        "İtalya" to "IT",
        "İrlanda" to "IE",
        "Japonya" to "JP",
        "Kanada" to "CA",
        "Norveç" to "NO",
        "Polonya" to "PL",
        "Portekiz" to "PT",
        "Rusya" to "RU",
        "Yunanistan" to "GR",
        "Diğer" to ""
    )

    val names: List<String> = byName.keys.toList()

    fun codeOf(displayName: String): String = byName[displayName] ?: ""

    /**
     * Koddan, kuratörlü listedeki konum yazımıyla uyumlu ülke adı.
     *
     * `data/stations.json` konumları İngilizce yazıyor ("Brussels, Belgium");
     * ülke şeridinin tek bir yazımda kalması için burada da onu izliyoruz.
     */
    fun nameOf(code: String): String = ENGLISH_NAMES[code].orEmpty()

    private val ENGLISH_NAMES = mapOf(
        "TR" to "Türkiye", "DE" to "Germany", "US" to "United States",
        "AU" to "Australia", "AT" to "Austria", "BE" to "Belgium",
        "GB" to "United Kingdom", "FR" to "France", "NL" to "Netherlands",
        "ES" to "Spain", "SE" to "Sweden", "CH" to "Switzerland",
        "IT" to "Italy", "IE" to "Ireland", "JP" to "Japan",
        "CA" to "Canada", "NO" to "Norway", "PL" to "Poland",
        "PT" to "Portugal", "RU" to "Russia", "GR" to "Greece"
    )
}

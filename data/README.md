# Radyola — Veri Dosyaları

Tüm platformlar istasyon listesini buradan çeker. İki dosya var ve **ikisi
farklı şekilde bakılır** — karıştırılırsa elle seçilmiş istasyonlar kaybolur.

## `stations.json` — kuratörlü liste (~35 istasyon)

Her platformun açılışta yüklediği varsayılan liste. **Elle bakılır**, crawler
bu dosyaya dokunmaz.

Menü tabanlı uygulamalar (macOS/Linux/Windows tepsi menüsü) yalnızca bunu
kullanır: 3 binlik bir listeyi alt menülere sığdırmak mümkün değil.

Yeni istasyon eklemek için dosyayı doğrudan düzenleyin.

## `directory.json` — Keşfet dizini (~3.400 istasyon)

`crawler/crawler.js` üretir, radio-browser.info kaynaklıdır. Arama yeteneği
olan platformlar (web, Android) isteğe bağlı olarak çeker.

Yeniden üretmek için:

```bash
node crawler/crawler.js --all --min-votes 1000
```

## Yayın denetimi

Kuratörlü liste zamanla çürüyor: adresler taşınıyor, sunucular kapanıyor.
Kimse bakmadığı için bu fark edilmiyordu — son ölçümde 35 kanalın 6'sı ölüydü.

```bash
node crawler/check-streams.js                  # data/stations.json
node crawler/check-streams.js --directory      # data/directory.json (yavaş)
node crawler/check-streams.js --json rapor.json --fail-on-dead
```

CI'da haftalık çalışması için GitLab arayüzünden bir zamanlama kurun:
**Build → Pipeline schedules → New schedule**. Ölü yayın bulunursa iş kırmızıya
döner; bildirim mekanizması budur. `STREAM_CHECK_SOFT=1` değişkeniyle
yumuşatılabilir.

### Beklenen kapalılık

Bir istasyona `expectDown` alanı eklenirse denetleyici onu ölü sayar ama işi
kırmızıya çevirmez:

```json
{
  "title": "Radyo Boğaziçi",
  "url": "http://nova.radyobogazici.net:7008/listen",
  "expectDown": "Yaz tatili — Eylül 2026'da tekrar bak"
}
```

Amaç alarmı anlamlı tutmak: her hafta kırmızı gelen bir denetim, gerçek bir
bozulma olduğunda fark edilmez. Muafiyet kalıcı değil — yayın geri döndüğünde
denetleyici **"muaf tutulmuştu ama çalışıyor"** diye haber verir, işaret
kaldırılsın diye. Alanı diğer platformlar yok sayar.

### Yanlış alarm vermemek için

Sürekli hatalı uyaran bir denetleyici görmezden gelinir. Dört tuzağı ölçüp
kapattık:

| Tuzak | Nasıl çözüldü |
|---|---|
| Anlık kesinti | Ölü ilan etmeden önce 3 deneme, aralarında 4 sn |
| 200 dönüp veri göndermeyen sunucu | En az 512 bayt gerçekten akmalı |
| HLS manifesti 200-300 bayt | `.m3u8` bayt sayarak değil, manifest çözümlenerek denetlenir: master ise ilk varyanta inilip segment (`#EXTINF`) aranır |
| Icecast durum sayfası | Yanlış mount adresi 200 dönüp `text/xml` gönderiyor; içerik türü belge ise ses sayılmaz |
| Icecast/Shoutcast standart dışı başlık | `insecureHTTPParser` — Node'un katı ayrıştırıcısı çalışan yayınları `HPE_CR_EXPECTED` ile ölü gösteriyordu |

Denetleyici kendini oynatıcılarla **aynı** `User-Agent` ile tanıtır
(`Radyola/1.0`). Kendine özel bir kimlik kullanmak yanıltıcı sonuç veriyordu:
ITU'nun Shoutcast sunucusu denetleyiciye özel kimliği 403 ile reddederken
oynatıcı kimliğine sorunsuz yanıt veriyor. Soru "sunucu bizi kabul ediyor mu"
değil, "kullanıcı bu yayını çalabiliyor mu".

## Şema

Her iki dosya da aynı ortak alanları taşır:

| Alan | Örnek | Not |
|---|---|---|
| `date` | `2026-08-22` | Eklenme/tazelenme tarihi |
| `title` | `Açık Radyo` | |
| `url` | `https://stream.34bit.net/ar.mp3` | Akış adresi |
| `website` | `https://acikradyo.com.tr` | Boş olabilir |
| `location` | `İstanbul, Türkiye` | `Şehir, Ülke` veya yalnız ülke |
| `countryCode` | `TR` | ISO 3166-1 alpha-2 |
| `genre` | `Eclectic` | En fazla 3 tür, ` / ` ile ayrılmış |

`directory.json` ayrıca kalite alanları taşır: `votes`, `bitrate`, `codec`,
`hls`, `lastCheckOk`. Sıralama ve bozuk yayınları eleme için kullanılır.

### Tür etiketleri nasıl temizleniyor

radio-browser etiketleri serbest metin: aynı tür beş ayrı yazımla geliyor,
yanına frekans (`107.7 fm`), şehir adı ve yayıncı markası karışıyor. Ham hâlde
1.365 farklı etiket çıkıyor ve 833'ü tek bir istasyonda geçiyor — filtre olarak
kullanılamaz. Crawler dört aşamada toparlıyor:

1. **Normalize** — büyük/küçük harf, aksan, noktalama ve ayraç farkları silinir.
   `80's` / `#80s` / `80s` aynı etiket olur; `R&b/urban` iki etikete bölünür.
2. **Eş anlamlı eşleme** — diller arası birleştirme: `Müzik`/`Музыка`/`音乐` → `Music`,
   `Haber`/`Новости`/`Nachrichten` → `News`, `Türkü` → `Folk`.
3. **Gürültü eleme** — frekanslar, salt rakamlar, iki karakterden kısa ve 25
   karakterden uzun etiketler, akış adresleri, yayıncı adları. Etiketin bütün
   kelimeleri istasyonun kendi konumunda geçiyorsa yer adıdır, atılır.
4. **Eşik** — `--min-tag-count` (varsayılan 3) altında kalan etiketler ve tek bir
   yayıncıya sıkışmış etiketler elenir. İkincisi marka/yer adlarını yakalar:
   `181.FM` ağının 34 kanalı şehirleri `Waynesboro`'yu tür sanıyordu.

Sonuç: **438 tür**, istasyonların %72'sinde tür bilgisi var. Kuyrukta hâlâ birkaç
şehir/marka adı var (`Cdmx`, `Acir`); onları tamamen ayıklamak yer adı sözlüğü
gerektirir.

### `countryCode` neden var

Bayrak emoji'si ülke **adından** değil kodundan türetilmeli. radio-browser'ın
ham adları uzun ve resmî (`The United Kingdom Of Great Britain And Northern
Ireland`); ad eşleştiren sözlükler 160 ülkede tutmuyor. İki harfli kod ise her
dilde iki satırda emoji'ye çevrilir:

```kotlin
// Kotlin — 'TR' → 🇹🇷
val flag = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')) }
    .joinToString("") { String(it) }
```

```python
# Python — 'TR' → 🇹🇷
flag = "".join(chr(0x1F1E6 + ord(c) - ord("A")) for c in code.upper())
```

```js
// JavaScript — 'TR' → 🇹🇷
const flag = [...code.toUpperCase()].map(c =>
  String.fromCodePoint(0x1F1E6 + c.charCodeAt(0) - 65)).join("");
```

```swift
// Swift — 'TR' → 🇹🇷
let flag = code.uppercased().unicodeScalars
    .compactMap { UnicodeScalar(0x1F1E6 + $0.value - 65).map(String.init) }
    .joined()
```

# Radyola — Web

`<aripd-radyola>` — bağımlılıksız, gömülebilir bir Web Component. Shadow DOM
kullanır, sayfanın stilini etkilemez ve ondan etkilenmez.

## Özellikler

- **Üç liste** — *Curated* (elle bakılan ~35 istasyon, açılışta yüklenir),
  *Discover* (~3.400 istasyonluk dizin, ilk geçişte çekilir) ve *Favorites*
  (kalp ile seçilenler; tam kayıt `localStorage`'da tutulur, dizin inmeden
  çalışır). İlk ikisi diğer platformlarla ortak JSON kaynağından gelir
  ([`data/`](../data/)).
- **HLS desteği** — `.m3u8` akışları Safari'de doğal, Chrome/Firefox'ta
  [hls.js](https://github.com/video-dev/hls.js) ile çalar. Kitaplık ayrı bir
  parça olarak derlenir ve ilk HLS istasyonuna kadar ağdan istenmez.
- **Karışık içerik onarımı** — dizindeki ~1.900 istasyon yalnız `http://`
  yayınlıyor; HTTPS sayfada tarayıcı bunları engeller. Çalarken şema `https`'e
  yükseltilir (sunucu TLS konuşuyorsa kurtulur), satırda **HTTP** rozeti
  gösterilir, olmadı ise hata mesajı asıl nedeni söyler.
- Bulanık arama (Fuse.js), ülke ve tür şeritleri, ISO kodundan türetilen
  bayraklar, dizin kayıtlarında codec/bitrate rozetleri
- **TR/EN arayüz** — tarayıcı diline göre otomatik; gömen sayfa
  `<aripd-radyola lang="tr">` (ya da `en`) ile sabitleyebilir. Metinlerin
  tamamı tek tabloda (`LANGS`).
- **Derin bağlantı + paylaş** — çalan istasyon adres çubuğuna `#s=…` olarak
  yazılır (geçmişi şişirmeden, `replaceState`). Bu bağlantıyla gelen
  ziyaretçide istasyon çubukta duraklatılmış hazır bekler; kuratörlü listede
  yoksa dizin arka planda çekilip oradan bulunur. Paylaş düğmesi yerel
  paylaşım menüsünü açar, yoksa bağlantıyı panoya kopyalar.
- Aramada temizle (×) düğmesi; veri yüklenemezse "Tekrar dene" düğmesi
- Alt oynatıcı çubuğu: çal/duraklat, önceki/sonraki, ses seviyesi (kalıcı)
- **Klavye** — `/` aramaya odaklanır, `Esc` temizler; satırlar Tab ile
  gezilir, Enter/Space çalar; `prefers-reduced-motion` süslemeleri durdurur
- **PWA** — telefona/masaüstüne uygulama olarak kurulabilir
  (`manifest.webmanifest` + `icons/`). `sw.js` kabuğu çevrimdışı açar ve
  istasyon listelerini önbellekten anında verir: arka planda ETag ile koşullu
  GET atılır, veri değişmemişse sunucu 304 döner ve 1,2 MB'lık dizin yeniden
  inmez. Ses akışlarına ve başka kökenlere hiç karışmaz. SW kaydı ana
  sayfanın işi (`index.html`) — bileşeni başka siteye gömen, SW almaz.
  Dağıtımda `__BUILD__` commit özetiyle damgalanır (`web.yml`); her dağıtım
  SW'yi yeniler, eski önbellekler `activate`'te silinir.

## Kullanım

```html
<aripd-radyola></aripd-radyola>
<script type="module" src="./dist/js/index.js"></script>
```

`radyola-player.css` bileşenin yanında bulunmalı — shadow DOM'a `./radyola-player.css`
yolundan yükleniyor.

## Derleme

```bash
npm ci
npm run build     # → dist/js/index.js (~44 KB) + hls-*.js (ayrı parça, istenince yüklenir)
npm run start     # yerel sunucu (python3 -m http.server)
```

## Ölçek notları

Discover dizini 3.400 kayıt taşıyor; şu üçü olmadan sayfa donuyordu:

- **Arama indeksi liste başına bir kez kurulur.** Önceden her tuş vuruşunda
  `new Fuse(...)` çağrılıyordu; 3.400 kayıtta yeniden indeksleme hissediliyordu.
- **Satırlar sayfa sayfa basılır** (`PAGE_SIZE`, varsayılan 60). Gerisi listenin
  sonundaki gözcü görünür oldukça `IntersectionObserver` ile eklenir.
- **Filtre şeritleri tek satır, yatay kaydırmalı.** Sarmalarsa 160 ülke çipi
  istasyon listesini ekranın çok altına itiyor.

Ayrıca arama `SEARCH_DEBOUNCE` kadar geciktirilir ve tür şeridi en sık geçen
`MAX_GENRE_PILLS` türle sınırlıdır — dizinde 438 tür var.

## Bayraklar

Bayrak emoji'si ülke adından değil ISO 3166-1 kodundan türetilir (`flagOf`).
Not: **Windows** regional indicator bayraklarını çizmez, iki harfi kutu içinde
gösterir. Bilgi kaybı yok ama görünüm platforma göre değişir.

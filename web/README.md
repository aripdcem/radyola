# Radyola — Web

`<aripd-radyola>` — bağımlılıksız, gömülebilir bir Web Component. Shadow DOM
kullanır, sayfanın stilini etkilemez ve ondan etkilenmez.

## Özellikler

- **İki liste** — *Curated* (elle bakılan ~35 istasyon, açılışta yüklenir) ve
  *Discover* (~3.400 istasyonluk dizin, ilk geçişte çekilir). İkisi de diğer
  platformlarla ortak JSON kaynağından gelir ([`data/`](../data/)).
- Bulanık arama (Fuse.js), ülke ve tür şeritleri, ISO kodundan türetilen bayraklar
- Alt oynatıcı çubuğu: çal/duraklat, önceki/sonraki, ses seviyesi

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
npm run build     # → dist/js/index.js  (~35 KB)
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

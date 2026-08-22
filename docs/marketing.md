# Radyola — Pazarlama & Rekabet Avantajları

---

## Neden Radyola?

İnternet radyo pazarında TuneIn, Radio Garden, myTuner gibi büyük oyuncular var. Radyola'nın bu kalabalık pazarda öne çıkmasını sağlayan somut avantajları aşağıda detaylandırılmıştır.

---

## 1. Küratörlü İstasyon Listesi

**Ne demek:** İstasyonlar bir algoritma veya otomatik tarama ile değil, editöryal bir seçimle elle ekleniyor.

**Neden önemli:**
- TuneIn'de 100.000+ istasyon var — kullanıcı seçim yorgunluğu yaşar
- Radyola'da her istasyon bilinçli bir tercihle listeye girmiş, kalite garantisi var
- Spotify'ın küratörlü playlistlerinin başarısı bu modelin işe yaradığını kanıtlıyor
- Türkiye, Belçika, İngiltere, Yunanistan gibi spesifik bir kültürel zevk yansıtıyor — bu bir **niş kimlik**, dezavantaj değil

**Pazarlama mesajı:**
> *"Binlerce istasyonda kaybolma. Dinlemeye değer olanları biz seçtik."*

---

## 2. Sıfıra Yakın Altyapı Maliyeti

**Ne demek:** Radyola hiçbir ses dosyası barındırmaz, radyo akışlarını yönlendirir. Hosting GitLab Pages (ücretsiz), istasyon verisi depodaki bir JSON dosyası (ücretsiz).

**Neden önemli:**
- Sunucu maliyeti yok → kâra geçiş eşiği çok düşük
- Ölçekleme sorunu yok — 100 kullanıcı ile 100.000 kullanıcı aynı maliyeti taşır
- Rakipler sunucu, CDN, veritabanı maliyetlerini karşılamak zorunda

**Pazarlama mesajı (B2B/yatırımcı):**
> *"Sıfır sunucu maliyeti ile sonsuz ölçeklenebilirlik."*

---

## 3. Çoklu Platform — Tek Kod Tabanı Yaklaşımı

**Ne demek:** Web, Android, Linux, macOS ve Windows için native uygulamalar,
tek bir repository'den ve tek bir veri kaynağından yönetiliyor.

| Platform | Teknoloji | Dağıtım |
|---|---|---|
| 🌐 Web | Vanilla JS, Web Component | GitLab Pages |
| 🤖 Android | Kotlin, Jetpack Compose, Media3 | APK |
| 🐧 Linux | Python, GTK4, GStreamer | .deb paketi |
| 🍎 macOS | Swift, SwiftUI, AVFoundation | Xcode |
| 🪟 Windows | Python, pystray, pygame | .exe (PyInstaller) |

**Neden önemli:**
- Rakiplerin çoğu ya sadece web ya sadece mobil — Radyola beşinde de native
- Masaüstünde system tray, mobilde kilit ekranı kontrolleri → arka planda çalışır
- Her platformda o platformun native araçları (GTK4, SwiftUI, Win32 tray, Media3)

**Pazarlama mesajı:**
> *"Tarayıcını aç ya da tray'den dinle. Her platformda, her zaman."*

---

## 4. Gömülebilir Web Component Mimarisi

**Ne demek:** Web uygulaması `<aripd-radyola>` adlı bir Web Component olarak tasarlanmış. Shadow DOM kullanıyor, herhangi bir siteye tek satır HTML ile gömülebilir.

```html
<aripd-radyola></aripd-radyola>
<script type="module" src="radyola.js"></script>
```

**Neden önemli:**
- Hiçbir framework bağımlılığı yok (React, Vue, Angular gerekmez)
- Herhangi bir web sitesine çakışma riski olmadan entegre edilebilir
- B2B widget satışı için hazır altyapı (bkz. [monetization.md](./monetization.md))
- Rakiplerin hiçbiri bu kadar kolay gömülebilir bir çözüm sunmuyor

**Pazarlama mesajı (B2B):**
> *"Tek satır HTML ile sitenize canlı radyo ekleyin."*

---

## 5. Hafiflik & Performans

**Ne demek:** Web uygulaması framework kullanmıyor, esbuild ile paketleniyor. Tek bağımlılık: Fuse.js (arama kütüphanesi, ~6KB gzip).

| Metrik | Radyola | Tipik Rakip |
|---|---|---|
| JS bundle boyutu | ~15 KB (gzip) | 200–500 KB |
| Framework | Yok (Vanilla JS) | React/Angular |
| İlk yükleme süresi | < 1 saniye | 3–5 saniye |
| Bağımlılık sayısı | 1 (Fuse.js) | 50–200+ |

**Neden önemli:**
- Yavaş bağlantılarda bile anında açılır
- Düşük güçlü cihazlarda (eski telefon, Raspberry Pi) sorunsuz çalışır
- SEO ve Core Web Vitals puanları doğal olarak yüksek

**Pazarlama mesajı:**
> *"15 KB. Sıfır framework. Anında açılır."*

---

## 6. Dinamik İstasyon Yönetimi

**Ne demek:** İstasyon listesi uygulamaların içine gömülü değil; iki JSON
dosyasından geliyor ve CI bunları statik olarak yayınlıyor:

- `data/stations.json` — elle bakılan ~35 istasyonluk küratörlü liste
- `data/directory.json` — radio-browser.info'dan derlenen 3.400 istasyonluk
  Keşfet dizini (`crawler/crawler.js` üretir)

Yeni istasyon eklemek = dosyaya bir kayıt eklemek. Uygulama güncellemesi
gerekmez; kullanıcı listeyi yenilediğinde görür.

**Neden önemli:**
- Rakiplerin çoğu istasyon güncellemesi için uygulama güncellemesi gerektirir
- İki katmanlı yapı hem küratörlük iddiasını hem de kapsamı aynı anda taşıyor
- Dizin kalite sinyalleri taşıyor (`votes`, `bitrate`, `codec`, `lastCheckOk`) —
  sıralama ve bozuk yayınları eleme için altyapı hazır
- Topluluk katkısına açık: küratörlü liste bir merge request ile büyüyebilir

**Not:** Güncelleme artık bir git commit'i gerektiriyor (eskiden bir tablo
satırıydı). Teknik olmayan katkıcılar için istasyon öneri formu bu boşluğu
kapatır — [`todo.md`](todo.md) içinde açık madde.

**Pazarlama mesajı:**
> *"Yeni istasyonlar anında. Güncelleme beklemeye gerek yok."*

---

## 7. Açık Kaynak & Şeffaflık

**Ne demek:** MIT lisansı ile tamamen açık kaynak. Herkes kodu inceleyebilir, fork edebilir, katkıda bulunabilir.

**Neden önemli:**
- Güven oluşturur — gizli veri toplama, gizli izleme yok
- Topluluk katkılarıyla organik büyüme potansiyeli
- Geliştirici ekosisteminde görünürlük (GitHub/GitLab stars)
- Kurumsal müşteriler kodu denetleyebilir

**Pazarlama mesajı:**
> *"Açık kaynak. Şeffaf. Seninle birlikte büyüyor."*

---

## Rakip Karşılaştırması

| Özellik | Radyola | TuneIn | Radio Garden | myTuner |
|---|---|---|---|---|
| İstasyon seçimi | Küratörlü | Otomatik (100K+) | Otomatik | Otomatik |
| Web uygulaması | ✅ | ✅ | ✅ | ✅ |
| Masaüstü native | ✅ (3 platform) | ❌ | ❌ | ❌ |
| System tray | ✅ | ❌ | ❌ | ❌ |
| Gömülebilir widget | ✅ | ❌ | ❌ | ❌ |
| Açık kaynak | ✅ | ❌ | ❌ | ❌ |
| JS bundle boyutu | ~15 KB | ~400 KB | ~300 KB | ~250 KB |
| Sunucu maliyeti | $0 | Yüksek | Yüksek | Yüksek |
| Reklam | Yok (şimdilik) | Agresif | Orta | Agresif |

---

## Hedef Kitle Segmentleri

### 1. Bireysel Dinleyiciler
- Kaliteli, seçilmiş istasyon arayanlar
- Reklam bombardımanından kaçınanlar
- Masaüstünde arka planda radyo dinleyenler

### 2. Radyo İstasyonları (B2B)
- Web sitelerine gömülebilir player arayanlar
- Dinleyici analitiği isteyenler
- Düşük maliyetli dijital dağıtım arayanlar

### 3. Geliştiriciler
- Açık kaynak projelere katkı vermek isteyenler
- Kendi radyo uygulamasını kurmak isteyenler
- Web Component mimarisini referans almak isteyenler

---

## Özet: Tek Cümlelik Konumlandırma

> **Radyola, elle seçilmiş radyo istasyonlarını her platformda, sıfır maliyet ve maksimum performansla dinleten açık kaynak bir internet radyo çalar.**

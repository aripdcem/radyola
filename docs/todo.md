# Radyola — Yapılacaklar

Monetizasyon ve pazarlama dokümanlarında tespit edilen zayıf yönler ve bunları gidermek için atılması gereken adımlar.

---

## Altyapı Eksikleri

- [ ] **Kullanıcı hesap sistemi** — Favori, senkronizasyon ve premium model için gerekli (Firebase Auth veya Supabase)
- [ ] **Analitik altyapısı** — Kullanıcı davranışını ölçmek için (Plausible veya Umami, self-host)
- [ ] **Ödeme altyapısı** — Premium model için (Stripe, Paddle veya Gumroad)

## Büyüme & Görünürlük

- [ ] **SEO optimizasyonu** — Open Graph meta etiketleri, sitemap.xml, yapılandırılmış veri
- [ ] **PWA desteği** — Service worker, manifest.json, offline erişim, ana ekrana ekleme
- [ ] **Sosyal medya varlığı** — Twitter/X, Product Hunt lansmanı
- [ ] **Bağış butonu** — Web uygulamasına Ko-fi veya Buy Me a Coffee entegrasyonu

## Gelir Kanalları

- [ ] **Reklam entegrasyonu** — AdSense veya Carbon Ads (Shadow DOM dışına yerleştirme gerekir)
- [ ] **İstasyon sponsorluğu** — "Öne çıkan istasyon" paketi hazırla, radyo istasyonlarına ulaş
- [ ] **Widget paketleme** — Web Component'i npm paketi olarak yayınla, dokümantasyon hazırla

## Ürün Geliştirme

- [ ] **Favori istasyonlar** — localStorage veya bulut tabanlı favori listesi
- [ ] **Uyku zamanlayıcı (Sleep Timer)** — Belirli süre sonra otomatik durdurma
- [ ] **Equalizer** — Web Audio API ile bas/tiz ayarı
- [ ] **Özel temalar** — Açık/koyu mod geçişi, özelleştirilebilir renkler
- [x] **Mobil uygulama (Android)** — Kotlin + Compose + Media3 native uygulama ([`android/`](../android/)); favoriler, uyku zamanlayıcı ve ~3.400 istasyonluk Keşfet dizini dahil
- [ ] **Mobil uygulama (iOS)** — macOS sürümündeki AVFoundation katmanı paylaşılabilir

## Topluluk

- [ ] **Katkı rehberi (CONTRIBUTING.md)** — Açık kaynak katkılarını kolaylaştır
- [ ] **İstasyon öneri formu** — Kullanıcıların istasyon önerebileceği basit bir form/issue template

# Radyola — Android

Jetpack Compose + Media3 (ExoPlayer) tabanlı internet radyo çalar.

## Özellikler

- **Dinamik istasyon listesi** — diğer platformlarla ortak Google Sheets kaynağı,
  ağ yoksa disk önbelleği, o da yoksa gömülü varsayılan liste
- **Arka planda çalma** — `MediaSessionService` ile ön plan servisi; bildirim,
  kilit ekranı kontrolleri ve kulaklık medya tuşları
- **Arama ve filtreler** — ad / konum / tür üzerinde arama, ülke ve tür seçicileri
- **Favoriler** — DataStore'da kalıcı
- **Uyku zamanlayıcı** — 15 / 30 / 45 / 60 / 90 dakika
- **Ayarlar** — son istasyonu hatırla, açılışta otomatik çal
- **Ses odağı** — arama gelince duraklar, kulaklık çıkınca susar
- Açık/koyu tema, Android 13+ tek renk (monochrome) uygulama simgesi

## Gereksinimler

| | |
|---|---|
| Min. Android | 7.0 (API 24) |
| Hedef / derleme SDK | 34 |
| JDK | 17 |
| Android SDK | Platform 34, Build Tools 34.0.0 |

Android Studio ile `android/` klasörünü açmanız yeterli. Komut satırı için
`local.properties` içine SDK yolunu yazın:

```properties
sdk.dir=/home/kullanici/Android/Sdk
```

## Derleme

```bash
cd android

# Hata ayıklama (debug) APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Yayın (release) APK — R8 ile küçültülmüş, ~3 MB
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# Birim testleri
./gradlew testDebugUnitTest
```

> Yayın yapılandırması, yan yükleme (sideload) kolay olsun diye debug anahtarıyla
> imzalanır. Google Play'e yükleyecekseniz `app/build.gradle.kts` içinde kendi
> `signingConfig` tanımınızı kullanın.

## Cihaza Kurma

```bash
# USB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Kablosuz hata ayıklama (Geliştirici seçenekleri → Kablosuz hata ayıklama)
adb connect 192.168.1.x:PORT
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Kablosuz hata ayıklama portu her açılışta değişir; cihazdaki ekranda yazan
güncel `IP:PORT` değerini kullanın.

## Mimari

```
com.aripd.radyola
├── MainActivity.kt              Compose giriş noktası, bildirim izni
├── MainViewModel.kt             Tüm ekran durumu, MediaController köprüsü
├── data/
│   ├── RadioStation.kt          Model, ülke/şehir/bayrak türetmeleri
│   ├── StationRepository.kt     Google Sheets CSV çekme + ayrıştırma + önbellek
│   └── SettingsStore.kt         DataStore: ayarlar ve favoriler
├── player/
│   ├── RadyolaPlaybackService.kt  MediaSessionService (ön plan servisi)
│   └── PlaylistResolver.kt        .pls / .m3u → gerçek akış adresi
└── ui/                          Compose ekranları ve tema
```

### Akış biçimleri

| Biçim | Ele alınışı |
|---|---|
| `.mp3`, `.aac` (Icecast/Shoutcast) | ExoPlayer doğrudan çalar |
| `.m3u8` (HLS) | `media3-exoplayer-hls` |
| `.pls`, `.m3u` | `PlaylistResolver` ilk akış adresini çıkarır |

`.pls` adresleri liste yüklendikten sonra arka planda toplu çözülür; böylece
bildirimdeki **sonraki istasyon** tuşu da beklemeden çalışır.

## Kaldırma

```bash
adb uninstall com.aripd.radyola
```

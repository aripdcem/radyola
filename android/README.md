# Radyola — Android

Jetpack Compose + Media3 (ExoPlayer) tabanlı internet radyo çalar.

## Özellikler

- **Listem** — kullanıcının kendi listesi. İlk açılışta kuratörlü listeden
  (`data/stations.json`) bir kez **tohumlanır**, sonra tamamen kullanıcıya
  aittir: kanal eklenir, çıkarılır, üzerine yazılmaz
- **Keşfet** — ~3.400 istasyonluk dizin, ilk geçişte çekilir
- **Elle kanal ekleme** — dizinde olmayan yerel/niş yayınlar için. Adres
  kaydedilmeden önce denenir; ülke ve tür seçim listesinden gelir
- **Arka planda çalma** — `MediaSessionService` ile ön plan servisi; bildirim,
  kilit ekranı kontrolleri ve kulaklık medya tuşları
- **Arama ve filtreler** — ad / konum / tür üzerinde arama, ülke ve tür seçicileri.
  Keşfet'te liste oya göre sıralanır ve tür çipleri en sık 24 türle sınırlanır
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
│   ├── StationRepository.kt     Uzak listeler (tohum + Keşfet), çekme + önbellek
│   ├── UserListStore.kt         Listem — yerel, kullanıcıya ait
│   ├── Countries.kt             Elle eklemede ülke seçimi
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

`.pls` adresleri kuratörlü liste yüklendikten sonra arka planda toplu çözülür;
böylece bildirimdeki **sonraki istasyon** tuşu da beklemeden çalışır. Keşfet
dizininde 3.400 kayıt olduğu için orada çözüm çalma anında, tek tek yapılır.

### Neden tohum, neden canlı liste değil

`data/stations.json` beş platformun ortak dosyası. Android onu canlı çekseydi
kullanıcının eklediği kanallar her yenilemede kaybolur, çıkardıkları geri
gelirdi. Tohum bir kez atılır; bunun bedeli, sonradan eklenen kuratörlü
kanalların kendiliğinden gelmemesi. Ayarlardaki **"Yeni kanallara bak"** bu
farkı elle kapatır — sessizce eklemiyoruz, çünkü kullanıcının bilerek
çıkardığı bir istasyon geri gelmemeli.

### Elle eklenen kanal doğrulanır

Yazım hatası aksi hâlde sessiz bir başarısızlığa dönüşüyor: kanal listeye
giriyor, çalmıyor, kullanıcı nedenini anlayamıyor. `StreamProbe` kaydetmeden
önce ilk baytları çekiyor; `.pls`/`.m3u` çözülüyor, HLS manifest üzerinden
doğrulanıyor, Icecast'in durum sayfası (belge içerik türü) yayın sayılmıyor.

Ülke ve tür serbest metin değil seçim listesi. Serbest bırakılsa kullanıcı
"istanbul turkiye" / "İstanbul, Türkiye" yazar ve ülke şeridi parçalanır —
crawler'da temizlediğimiz etiket gürültüsünün aynısı. Tür alanı yine de
yazmaya açık, çünkü kullanıcının aklındaki tür listede olmayabilir.

### Keşfet modunda sıra

Oynatıcıya görünen listenin tamamı değil, seçilen istasyonun çevresinden
**100 kayıtlık bir pencere** verilir. 3.400 `MediaItem` üretmek gereksiz;
bildirimdeki ileri/geri için bu pencere fazlasıyla yeterli, uygulama içi
ileri/geri ise görünen listenin tamamında dolaşır.

## Kaldırma

```bash
adb uninstall com.aripd.radyola
```

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
- **Uyku zamanlayıcı** — 15 / 30 / 45 / 60 / 90 dakika. Sayaç oynatma
  servisinde çalışır: uygulama son kullanılanlardan kaydırılsa da yayın
  vakti gelince durur
- **Ayarlar** — son istasyonu hatırla, açılışta otomatik çal
- **Ses odağı** — arama gelince duraklar, kulaklık çıkınca susar
- **Kopan yayını toparlama** — ağ hatasında bir kez sessizce yeniden
  bağlanır; ancak art arda ikinci hatada kullanıcıya söyler
- Açık/koyu tema, Android 13+ tek renk (monochrome) uygulama simgesi

## Gereksinimler

| | |
|---|---|
| Min. Android | 7.0 (API 24) |
| Hedef / derleme SDK | 35 |
| JDK | 17 |
| Android SDK | Platform 35, Build Tools 35.0.0 |

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

## İmzalama

Anahtar deposu verilmişse onunla, verilmemişse debug anahtarıyla imzalanır.
Yol ve parolalar ortam değişkeninden ya da Gradle özelliğinden okunur:

| Ortam değişkeni | Gradle özelliği | Varsayılan |
|---|---|---|
| `RADYOLA_KEYSTORE_FILE` | `radyola.keystoreFile` | — (yoksa debug anahtarı) |
| `RADYOLA_KEYSTORE_PASSWORD` | `radyola.keystorePassword` | — |
| `RADYOLA_KEY_ALIAS` | `radyola.keyAlias` | `radyola` |
| `RADYOLA_KEY_PASSWORD` | `radyola.keyPassword` | store parolası |
| `RADYOLA_VERSION_CODE` | `radyola.versionCode` | `1` |
| `RADYOLA_VERSION_NAME` | `radyola.versionName` | `1.0.0` |

Kendi anahtarınızı üretin:

```bash
keytool -genkeypair -v -keystore radyola-release.jks \
    -alias radyola -keyalg RSA -keysize 2048 -validity 10000
```

Yerelde imzalı derleme:

```bash
RADYOLA_KEYSTORE_FILE=$PWD/radyola-release.jks \
RADYOLA_KEYSTORE_PASSWORD=... \
./gradlew assembleRelease
```

> **Anahtar deposunu depoya koymayın.** CI'da GitHub secret olarak tutulur:
> `base64 -w0 radyola-release.jks` çıktısını `ANDROID_KEYSTORE_BASE64`
> secret'ına yazın (bkz. [`.github/workflows/release.yml`](../.github/workflows/release.yml)).

> **Neden önemli:** debug anahtarı her makinede — ve CI'da her koşuda — yeniden
> üretilir. Debug anahtarıyla imzalanmış iki APK farklı imza taşır; cihaz
> ikincisini güncelleme saymaz, kullanıcı önce uygulamayı kaldırmak zorunda
> kalır. Sürüm yayınlıyorsanız kendi anahtarınızı tanımlayın.

## CI

Her itmede [`android.yml`](../.github/workflows/android.yml) testleri koşar ve
APK üretir; Actions koşusunun **Artifacts** bölümünden inilir. `v*` etiketi
itildiğinde [`release.yml`](../.github/workflows/release.yml) imzalı APK'yı
GitHub Release'e ekler.

CI, `versionCode`'u commit sayısından türetir (`git rev-list --count HEAD`):
monoton artar, CI koşu sayacından bağımsızdır ve aynı commit her zaman aynı
numarayı verir.

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

Aynı düğme **adres düzeltmelerini** de uygular: kuratörlü listede bir yayının
URL'si değiştiyse (ölü yayın düzeltmesi) kullanıcıdaki aynı adlı kaydın adresi
yerinde güncellenir. Karşılaştırma bu yüzden kimlikle (`ad|url`) değil adla
yapılır; kimlikle yapılsa adresi düzeltilen istasyon "eksik" sanılıp ikinci
kez eklenirdi — biri ölü, biri sağlam iki kopya.

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

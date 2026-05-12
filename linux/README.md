# Radyola — Linux Native İnternet Radyo Çalar

**Radyola**, macOS [Radyola](../macosx/) uygulamasının Linux native karşılığıdır. GTK4, Libadwaita ve GStreamer kullanarak Debian 13 (Trixie) üzerinde çalışan bir internet radyo çalar uygulamasıdır.

## Ekran Görünümü

```
┌──────────────────────────────────┐
│  Radyola                    ⓘ   │
│  🎵 Çalıyor — Açık Radyo        │
│─────────────────────────────────│
│  ⏹  🇹🇷 Açık Radyo         🔊   │
│  ▶  🇷🇺 Sputnik Türkiye         │
│  ▶  🇹🇷 ITU Radio Jazz/Blues    │
│  ▶  🇹🇷 ITU Radio Classical     │
│  ▶  🇧🇪 MUSIQ3                  │
│  ▶  🇧🇪 VRT Klara               │
│  ...                             │
└──────────────────────────────────┘
```

## Özellikler

- 🎵 **18 internet radyo istasyonu** (Türkiye, Belçika, İngiltere, Yunanistan)
- ▶️ **Play/Pause/Stop** kontrolleri
- 🎨 **Libadwaita** ile modern GNOME görünümü (koyu/açık tema uyumlu)
- 📡 **GStreamer** ile tüm codec desteği (MP3, AAC, HLS/m3u8, PLS)
- ⚡ **Tek dosya** mimarisi — kurulumu ve bakımı kolay
- 🚨 **Hata yönetimi** — bağlantı sorunlarında kullanıcı bildirimi
- ℹ️ **Hakkında diyaloğu** — uygulama bilgileri

## Gereksinimler

### Sistem

| Paket | Minimum Sürüm | Açıklama |
|---|---|---|
| `python3` | 3.11+ | Python yorumlayıcı |
| `python3-gi` | 3.42+ | PyGObject (GObject Introspection bağlayıcıları) |
| `gir1.2-gtk-4.0` | 4.8+ | GTK4 GObject Introspection |
| `gir1.2-adw-1` | 1.2+ | Libadwaita GObject Introspection |
| `gir1.2-gstreamer-1.0` | 1.20+ | GStreamer GObject Introspection |
| `gir1.2-gst-plugins-base-1.0` | 1.20+ | GStreamer Plugins Base |
| `gstreamer1.0-plugins-good` | 1.20+ | MP3, AAC codec'leri |
| `gstreamer1.0-plugins-bad` | 1.20+ | HLS desteği (m3u8) |
| `gstreamer1.0-plugins-ugly` | 1.20+ | Ek codec'ler |

### Kurulum (Debian 13)

Gerekli paketler zaten çoğu Debian 13 GNOME kurulumunda mevcuttur. Eksik olanları yüklemek için:

```bash
sudo apt install python3-gi gir1.2-gtk-4.0 gir1.2-adw-1 \
    gir1.2-gstreamer-1.0 gir1.2-gst-plugins-base-1.0 \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
    gstreamer1.0-plugins-ugly
```

## Kullanım

### Doğrudan Çalıştırma

```bash
python3 radyola.py
```

### Çalıştırılabilir Yapma

```bash
chmod +x radyola.py
./radyola.py
```

### Masaüstü Entegrasyonu

Uygulamayı GNOME uygulama menüsüne eklemek için:

```bash
# Dosyaları kopyala
sudo mkdir -p /opt/radyola
sudo cp radyola.py radyola.css /opt/radyola/
sudo cp radyola.desktop /usr/share/applications/
```

## Mimari

### Dosya Yapısı

```
linux/
├── radyola.py        # Ana uygulama (tek dosya)
├── radyola.css       # GTK4 özel stiller
├── radyola.desktop   # Freedesktop masaüstü girişi
└── README.md         # Bu dosya
```

### Sınıf Diyagramı

```
┌─────────────────┐    ┌──────────────────┐
│   RadyolaApp    │───▸│  RadyolaWindow   │
│  (Adw.App)      │    │  (Adw.AppWindow) │
└─────────────────┘    └──────┬───────────┘
                              │
                    ┌─────────┼──────────┐
                    ▼         ▼          ▼
            ┌────────────┐ ┌──────┐ ┌────────────────┐
            │ StationRow │ │ UI   │ │ GStreamerPlayer │
            │ (ListBox)  │ │ Ctrl │ │ (playbin)      │
            └─────┬──────┘ └──────┘ └────────────────┘
                  │
                  ▼
            ┌──────────────┐
            │ RadioStation │
            │ (dataclass)  │
            └──────────────┘
```

### Bileşenler

| Sınıf | Görev |
|---|---|
| `RadioStation` | İstasyon veri modeli (isim, URL, ülke, tür) |
| `GStreamerPlayer` | GStreamer `playbin` ile ses akışı yönetimi |
| `StationRow` | Tekil istasyon satırı widget'ı |
| `RadyolaWindow` | Ana pencere: header bar + kontroller + liste |
| `RadyolaApp` | Uygulama yaşam döngüsü yönetimi |

### Ses Akışı

GStreamer `playbin` elemanı kullanılır. Bu, GStreamer'ın en yüksek seviyeli oynatma bileşenidir ve URL'den otomatik olarak:
1. Protokolü algılar (HTTP, HTTPS, HLS)
2. Codec'i belirler (MP3, AAC, Vorbis vb.)
3. Çıkış cihazını seçer (PulseAudio/PipeWire)

## Radyo İstasyonları

| # | İstasyon | Ülke | Tür |
|---|---|---|---|
| 1 | Açık Radyo | 🇹🇷 | Kültür |
| 2 | Sputnik Türkiye | 🇷🇺 | Haber |
| 3 | ITU Radio Jazz/Blues | 🇹🇷 | Jazz/Blues |
| 4 | ITU Radio Classical | 🇹🇷 | Klasik |
| 5 | MUSIQ3 | 🇧🇪 | Klasik |
| 6 | VRT Klara | 🇧🇪 | Klasik |
| 7 | Viva Brabant Wallon | 🇧🇪 | Pop |
| 8 | ITU Radio Rock | 🇹🇷 | Rock |
| 9 | BBC Radio 1 | 🇬🇧 | Pop |
| 10 | BBC World Service News | 🇬🇧 | Haber |
| 11 | Radyo TRT Haber | 🇹🇷 | Haber |
| 12 | NTV Radyo | 🇹🇷 | Haber |
| 13 | HABERTÜRK Radyo | 🇹🇷 | Haber |
| 14 | Radio Panik | 🇧🇪 | Alternatif |
| 15 | Radyo Bozcaada | 🇹🇷 | Pop |
| 16 | Radyo Gökçeada | 🇹🇷 | Pop |
| 17 | Radyo Boğaziçi | 🇹🇷 | Pop |
| 18 | Μινόρε Καλλονής | 🇬🇷 | Yerel |

## macOS Versiyonuyla Karşılaştırma

| Özellik | macOS (Radyola) | Linux (Radyola) |
|---|---|---|
| **Framework** | SwiftUI | GTK4 + Libadwaita |
| **Ses** | AVFoundation (AVPlayer) | GStreamer (playbin) |
| **UI Modeli** | MenuBarExtra (menü çubuğu) | Pencereli uygulama |
| **Dil** | Swift | Python 3 |
| **Mimari** | Çoklu dosya | Tek dosya |
| **Hata Yönetimi** | ❌ | ✅ |
| **Koyu Tema** | macOS otomatik | Libadwaita otomatik |
| **Volume Kontrolü** | ❌ | ❌ (sistem seviyesinde) |
| **İstasyon Sayısı** | 18 | 18 (aynı) |
| **Tür Bilgisi** | ❌ | ✅ |
| **Ülke Bayrağı** | ❌ | ✅ |

## Geliştirme Durumu & Notlar

> [!NOTE]
> Bu sürüm, macOS Radyola projesinin Linux native karşılığı olarak geliştirilmiştir. Temel radyo çalma işlevselliği çalışır durumdadır ve macOS versiyonuna kıyasla ek özellikler içerir.

### ✅ Tamamlanan Özellikler
- GTK4 + Libadwaita ile modern GNOME native pencere
- Radyo istasyonu listesi (18 istasyon, ülke bayrakları ve tür bilgisiyle)
- Play/Pause/Stop kontrolleri
- GStreamer `playbin` ile ses akışı oynatma (tüm codec desteği)
- Aktif istasyon görsel vurgulama (CSS animasyonları)
- Hata yönetimi (bağlantı sorunlarında `Adw.AlertDialog`)
- Koyu/açık tema otomatik uyumu (Libadwaita)
- Hakkında diyaloğu
- Freedesktop `.desktop` dosyası (masaüstü entegrasyonu)

### 🚧 Gelecekte Eklenebilecek Özellikler
- Volume (ses seviyesi) kontrolü (şu an sistem seviyesinde)
- System tray / indicator desteği (DE bağımlı)
- İstasyon ekleme/silme/düzenleme UI'sı
- İstasyon favorilere ekleme
- "Now Playing" metadata gösterimi (ICY/Shoutcast stream bilgisi)
- Klavye kısayolları (play/pause/stop/next)
- İstasyon arama/filtreleme

### ⚠️ Bilinen Kısıtlamalar
- Tüm istasyonlar hardcoded — harici yapılandırma dosyası kullanılmıyor
- Bazı PLS/m3u8 formatındaki stream URL'leri doğrudan çalışmayabilir (GStreamer plugin desteğine bağlı)
- System tray entegrasyonu yok (GNOME bunu kaldırdığı için pencereli uygulama tercih edildi)

## Lisans

MIT

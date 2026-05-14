# Radyola

Çoklu platform internet radyo çalar uygulaması.

## Platformlar

| Platform | Teknoloji | Ses Motoru | Tray/Menü |
|---|---|---|---|
| 🍎 macOS | Swift, SwiftUI | AVFoundation | MenuBarExtra |
| 🐧 Linux | Python, GTK4, Libadwaita | GStreamer | D-Bus StatusNotifierItem |
| 🪟 Windows | Python, pystray, Pillow | pygame (SDL2) | Win32 System Tray |

---

## 🍎 macOS

SwiftUI + AVFoundation tabanlı menü çubuğu radyo çalar.

- **Konum:** [`macosx/`](macosx/)
- **Gereksinim:** macOS 13.0+ (Ventura), Xcode 15+

### Kurulum & Çalıştırma

```bash
# Xcode ile aç ve çalıştır
open macosx/Radyola.xcodeproj
# Xcode → Product → Run (⌘R)
```

### Kaldırma

Uygulamayı `/Applications` klasöründen çöp kutusuna sürükleyin.

---

## 🐧 Linux

GTK4 + Libadwaita + GStreamer tabanlı native radyo çalar.

- **Konum:** [`linux/`](linux/)
- **Gereksinim:** Debian 13+ (Trixie), Python 3.11+

### Gerekli Paketler

```bash
sudo apt install python3-gi python3-dbus gir1.2-gtk-4.0 gir1.2-adw-1 \
    gir1.2-gstreamer-1.0 gir1.2-gst-plugins-base-1.0 \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
    gstreamer1.0-plugins-ugly

# İsteğe bağlı: GNOME'da system tray ikonu için
sudo apt install gnome-shell-extension-appindicator
```

### Doğrudan Çalıştırma (Kurulum Gerekmez)

```bash
python3 linux/radyola.py
```

### DEB Paketi ile Kurulum

```bash
cd linux/
./build-deb.sh
# → radyola_1.0.0_all.deb oluşturulur

sudo apt install ./radyola_1.0.0_all.deb
```

> `apt install ./` kullanmak bağımlılıkları otomatik çözer (python3-gi, gstreamer vb.).

### Çalıştırma (Kurulum Sonrası)

```bash
radyola
# veya GNOME uygulama menüsünden "Radyola" arayın
```

### Kaldırma

```bash
sudo apt remove radyola
```

---

## 🪟 Windows

pystray + pygame tabanlı system tray radyo çalar.

- **Konum:** [`windows/`](windows/)
- **Gereksinim:** Windows 10+, Python 3.11+

### Gerekli Paketler

```cmd
pip install pystray Pillow pygame
```

### Doğrudan Çalıştırma (Kurulum Gerekmez)

```cmd
python windows\radyola.py
```

### EXE Oluşturma

```cmd
cd windows
build.bat
REM → dist\Radyola.exe oluşturulur
```

Veya manuel:

```cmd
pip install pyinstaller
pyinstaller --onefile --windowed --name Radyola --hidden-import=pystray._win32 radyola.py
```

### Çalıştırma (EXE)

```cmd
dist\Radyola.exe
REM Çift tıklayarak da çalıştırılabilir
```

### Kaldırma

`Radyola.exe` dosyasını silin. Ayar dosyasını da temizlemek için:

```cmd
rmdir /s /q "%APPDATA%\Radyola"
```

---

## Radyo İstasyonları

Tüm platformlar aynı Google Sheets kaynağından dinamik istasyon listesi çeker:

🇹🇷 Açık Radyo · ITU Radio (Jazz/Blues, Classical, Rock) · TRT Haber · NTV Radyo · HABERTÜRK · Bozcaada · Gökçeada · Boğaziçi  
🇧🇪 MUSIQ3 · VRT Klara · Viva Brabant Wallon · Radio Panik  
🇬🇧 BBC Radio 1 · BBC World Service  
🇷🇺 Sputnik Türkiye  
🇬🇷 Μινόρε Καλλονής

## Lisans

MIT
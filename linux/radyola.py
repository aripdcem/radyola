#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Radyola — Linux Native Internet Radyo Çalar

macOS MacTile uygulamasının GTK4/Libadwaita/GStreamer tabanlı
Linux native karşılığı. Debian 13 (Trixie) için optimize edilmiştir.

Gereksinimler:
    - Python 3.11+
    - GTK 4, Libadwaita 1, GStreamer 1.0
    - python3-gi, gir1.2-gtk-4.0, gir1.2-adw-1
    - gir1.2-gstreamer-1.0, gir1.2-gst-plugins-base-1.0

Kullanım:
    python3 radyola.py

Lisans: MIT
"""

import sys
import os
from dataclasses import dataclass, field
from typing import Optional
from pathlib import Path

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
gi.require_version("Gst", "1.0")

from gi.repository import Gtk, Adw, Gst, GLib, Gio, Pango  # noqa: E402


# ──────────────────────────────────────────────
# Veri Modeli
# ──────────────────────────────────────────────


@dataclass
class RadioStation:
    """Bir radyo istasyonunu temsil eder."""

    title: str
    url: str
    country: str = ""
    genre: str = ""

    @property
    def flag(self) -> str:
        """Ülke bayrağı emoji'si döndürür."""
        flags = {
            "TR": "🇹🇷",
            "BE": "🇧🇪",
            "GB": "🇬🇧",
            "GR": "🇬🇷",
            "RU": "🇷🇺",
        }
        return flags.get(self.country, "📻")


# macOS MacTile projesindeki 18 istasyonun tamamı
STATIONS: list[RadioStation] = [
    RadioStation("Açık Radyo", "https://stream.34bit.net/ar.mp3", "TR", "Kültür"),
    RadioStation(
        "Sputnik Türkiye",
        "https://nfw.ria.ru/flv/audio.aspx?ID=98318704&type=mp3",
        "RU",
        "Haber",
    ),
    RadioStation(
        "ITU Radio Jazz/Blues",
        "http://160.75.86.29:8088/listen.pls?sid=3",
        "TR",
        "Jazz/Blues",
    ),
    RadioStation(
        "ITU Radio Classical",
        "http://160.75.86.29:8088/listen.pls?sid=5",
        "TR",
        "Klasik",
    ),
    RadioStation(
        "MUSIQ3",
        "https://redbeemedia.streamabc.net/redbm-musiq3-aac-256-1558698",
        "BE",
        "Klasik",
    ),
    RadioStation(
        "VRT Klara",
        "http://icecast-servers.vrtcdn.be/klara-high.mp3",
        "BE",
        "Klasik",
    ),
    RadioStation(
        "Viva Brabant Wallon",
        "https://radio.rtbf.be/viva-bw/aac-128",
        "BE",
        "Pop",
    ),
    RadioStation(
        "ITU Radio Rock",
        "http://160.75.86.29:8088/listen.pls?sid=1",
        "TR",
        "Rock",
    ),
    RadioStation(
        "BBC Radio 1",
        "http://open.live.bbc.co.uk/mediaselector/5/select/version/2.0/mediaset/http-icy-mp3-a/vpid/bbc_radio_one/format/pls.pls",
        "GB",
        "Pop",
    ),
    RadioStation(
        "BBC World Service News",
        "http://open.live.bbc.co.uk/mediaselector/5/select/mediaset/http-icy-mp3-a/format/pls/proto/http/vpid/bbc_world_service.pls",
        "GB",
        "Haber",
    ),
    RadioStation(
        "Radyo TRT Haber",
        "https://nmicenotrt.mediatriple.net/trt_haber.aac",
        "TR",
        "Haber",
    ),
    RadioStation(
        "NTV Radyo",
        "https://dygedge.radyotvonline.net/ntvradyo/playlist.m3u8",
        "TR",
        "Haber",
    ),
    RadioStation(
        "HABERTÜRK Radyo",
        "https://ciner-live.ercdn.net/haberturkradyo/haberturkradyo_1.m3u8",
        "TR",
        "Haber",
    ),
    RadioStation(
        "Radio Panik",
        "https://streaming.domainepublic.net/radiopanik.mp3",
        "BE",
        "Alternatif",
    ),
    RadioStation(
        "Radyo Bozcaada",
        "http://radyobozcaada.canliyayinda.com:4000/stream",
        "TR",
        "Pop",
    ),
    RadioStation(
        "Radyo Gökçeada",
        "https://radyogok.80.yayin.com.tr/stream",
        "TR",
        "Pop",
    ),
    RadioStation(
        "Radyo Boğaziçi",
        "http://nova.radyobogazici.net:7008/listen",
        "TR",
        "Pop",
    ),
    RadioStation(
        "Μινόρε Καλλονής",
        "https://i4.streams.ovh:2200/ssl/minore?mp=/stream",
        "GR",
        "Yerel",
    ),
]


# ──────────────────────────────────────────────
# GStreamer Ses Oynatıcı
# ──────────────────────────────────────────────


class GStreamerPlayer:
    """GStreamer playbin tabanlı internet radyo akışı oynatıcı.

    playbin, GStreamer'ın yüksek seviyeli ses/video oynatma elemanıdır.
    URL'den otomatik olarak codec ve format algılayarak çalma yapabilir.
    MP3, AAC, HLS (m3u8), PLS ve diğer tüm formatları destekler.
    """

    def __init__(self):
        Gst.init(None)
        self._playbin = Gst.ElementFactory.make("playbin", "radyola-player")
        if not self._playbin:
            raise RuntimeError("GStreamer playbin oluşturulamadı")

        self._current_station: Optional[RadioStation] = None
        self._on_error: Optional[callable] = None
        self._on_state_changed: Optional[callable] = None

        # Bus mesajlarını dinle (hata, durum değişikliği vb.)
        bus = self._playbin.get_bus()
        bus.add_signal_watch()
        bus.connect("message::error", self._on_bus_error)
        bus.connect("message::state-changed", self._on_bus_state_changed)
        bus.connect("message::eos", self._on_bus_eos)

    # ── Genel API ──

    def play(self, station: RadioStation) -> None:
        """Belirtilen istasyonu çalmaya başlar."""
        if self._current_station == station and self.is_playing:
            return

        self.stop()
        self._current_station = station
        self._playbin.set_property("uri", station.url)
        self._playbin.set_state(Gst.State.PLAYING)

    def pause(self) -> None:
        """Çalmayı duraklatır."""
        if self._current_station:
            self._playbin.set_state(Gst.State.PAUSED)

    def resume(self) -> None:
        """Duraklatılmış çalmayı sürdürür."""
        if self._current_station:
            self._playbin.set_state(Gst.State.PLAYING)

    def stop(self) -> None:
        """Çalmayı durdurur ve kaynakları serbest bırakır."""
        self._playbin.set_state(Gst.State.NULL)
        self._current_station = None

    def toggle(self, station: RadioStation) -> None:
        """Aynı istasyonsa play/pause toggle, farklıysa yeni istasyonu çal."""
        if self._current_station == station:
            if self.is_playing:
                self.pause()
            else:
                self.resume()
        else:
            self.play(station)

    # ── Durum Sorguları ──

    @property
    def is_playing(self) -> bool:
        """Şu an çalıyor mu?"""
        _, state, _ = self._playbin.get_state(Gst.CLOCK_TIME_NONE)
        return state == Gst.State.PLAYING

    @property
    def is_paused(self) -> bool:
        """Duraklatılmış mı?"""
        _, state, _ = self._playbin.get_state(Gst.CLOCK_TIME_NONE)
        return state == Gst.State.PAUSED

    @property
    def current_station(self) -> Optional[RadioStation]:
        """Şu an çalan/duraklatılan istasyon."""
        return self._current_station

    @property
    def state(self) -> str:
        """Durum string'i: 'playing', 'paused', 'stopped'."""
        if self.is_playing:
            return "playing"
        if self.is_paused:
            return "paused"
        return "stopped"

    # ── Callback'ler ──

    def on_error(self, callback: callable) -> None:
        """Hata callback'i ayarlar. callback(error_message: str)"""
        self._on_error = callback

    def on_state_changed(self, callback: callable) -> None:
        """Durum değişikliği callback'i. callback(state: str)"""
        self._on_state_changed = callback

    # ── Bus Mesaj İşleyicileri ──

    def _on_bus_error(self, bus, message):
        err, debug = message.parse_error()
        error_msg = f"{err.message}"
        if self._on_error:
            GLib.idle_add(self._on_error, error_msg)
        self.stop()

    def _on_bus_state_changed(self, bus, message):
        if message.src != self._playbin:
            return
        _, new_state, _ = message.parse_state_changed()
        if self._on_state_changed:
            state_map = {
                Gst.State.PLAYING: "playing",
                Gst.State.PAUSED: "paused",
                Gst.State.NULL: "stopped",
                Gst.State.READY: "ready",
            }
            state_str = state_map.get(new_state, "unknown")
            GLib.idle_add(self._on_state_changed, state_str)

    def _on_bus_eos(self, bus, message):
        """Akış sona erdiğinde."""
        self.stop()
        if self._on_state_changed:
            GLib.idle_add(self._on_state_changed, "stopped")

    def cleanup(self):
        """Kaynakları temizler. Uygulama kapanışında çağrılmalı."""
        self.stop()


# ──────────────────────────────────────────────
# İstasyon Satırı Widget'ı
# ──────────────────────────────────────────────


class StationRow(Gtk.ListBoxRow):
    """Tek bir radyo istasyonunu gösteren liste satırı.

    Libadwaita ActionRow kullanarak modern GNOME görünümü sağlar.
    İstasyon çalarken görsel geri bildirim verir (ikon + CSS sınıfı).
    """

    def __init__(self, station: RadioStation):
        super().__init__()
        self.station = station
        self._is_active = False
        self._is_paused = False

        # Ana kutu
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        box.set_margin_top(8)
        box.set_margin_bottom(8)
        box.set_margin_start(12)
        box.set_margin_end(12)

        # Play/Pause ikonu
        self._icon = Gtk.Image.new_from_icon_name("media-playback-start-symbolic")
        self._icon.set_pixel_size(24)
        self._icon.add_css_class("station-icon")
        box.append(self._icon)

        # İstasyon bilgileri
        info_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        info_box.set_hexpand(True)

        # Başlık satırı (bayrak + isim)
        title_label = Gtk.Label(label=f"{station.flag}  {station.title}")
        title_label.set_xalign(0)
        title_label.set_ellipsize(Pango.EllipsizeMode.END)
        title_label.add_css_class("station-title")
        info_box.append(title_label)

        # Alt başlık (tür)
        if station.genre:
            genre_label = Gtk.Label(label=station.genre)
            genre_label.set_xalign(0)
            genre_label.add_css_class("station-genre")
            genre_label.add_css_class("dim-label")
            info_box.append(genre_label)

        box.append(info_box)

        # Durum göstergesi (sağ taraf)
        self._status_icon = Gtk.Image.new_from_icon_name("audio-volume-muted-symbolic")
        self._status_icon.set_pixel_size(16)
        self._status_icon.set_opacity(0)
        self._status_icon.add_css_class("status-icon")
        box.append(self._status_icon)

        self.set_child(box)
        self.add_css_class("station-row")

    def set_active(self, active: bool, paused: bool = False) -> None:
        """İstasyonun aktif (çalıyor/duraklatılmış) durumunu ayarlar."""
        self._is_active = active
        self._is_paused = paused

        if active and not paused:
            self._icon.set_from_icon_name("media-playback-pause-symbolic")
            self._status_icon.set_from_icon_name("audio-volume-high-symbolic")
            self._status_icon.set_opacity(1)
            self.add_css_class("playing")
            self.remove_css_class("paused")
        elif active and paused:
            self._icon.set_from_icon_name("media-playback-start-symbolic")
            self._status_icon.set_from_icon_name("media-playback-pause-symbolic")
            self._status_icon.set_opacity(0.6)
            self.remove_css_class("playing")
            self.add_css_class("paused")
        else:
            self._icon.set_from_icon_name("media-playback-start-symbolic")
            self._status_icon.set_opacity(0)
            self.remove_css_class("playing")
            self.remove_css_class("paused")


# ──────────────────────────────────────────────
# Ana Pencere
# ──────────────────────────────────────────────


class RadyolaWindow(Adw.ApplicationWindow):
    """Radyola ana penceresi.

    Libadwaita ApplicationWindow tabanlı, HeaderBar + istasyon listesi +
    kontrol çubuğu içeren compact bir pencere.
    """

    def __init__(self, app: Adw.Application):
        super().__init__(application=app, title="Radyola")
        self.set_default_size(420, 580)
        self.set_resizable(True)

        # Oynatıcı
        self._player = GStreamerPlayer()
        self._player.on_error(self._on_player_error)
        self._player.on_state_changed(self._on_player_state_changed)

        # İstasyon satır referansları
        self._station_rows: dict[str, StationRow] = {}

        self._build_ui()
        self._load_css()

    def _build_ui(self) -> None:
        """UI bileşenlerini oluşturur."""

        # Ana dikey kutu
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)

        # ── Header Bar ──
        header = Adw.HeaderBar()
        header.add_css_class("flat")

        title_widget = Adw.WindowTitle(title="Radyola", subtitle="İnternet Radyo Çalar")
        header.set_title_widget(title_widget)
        self._title_widget = title_widget

        # Hakkında butonu
        about_btn = Gtk.Button(icon_name="help-about-symbolic")
        about_btn.set_tooltip_text("Hakkında")
        about_btn.connect("clicked", self._on_about_clicked)
        header.pack_end(about_btn)

        main_box.append(header)

        # ── Kontrol Çubuğu ──
        control_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        control_box.set_margin_start(16)
        control_box.set_margin_end(16)
        control_box.set_margin_top(8)
        control_box.set_margin_bottom(8)
        control_box.set_halign(Gtk.Align.CENTER)

        # Durdur butonu
        self._stop_btn = Gtk.Button(icon_name="media-playback-stop-symbolic")
        self._stop_btn.set_tooltip_text("Durdur")
        self._stop_btn.add_css_class("circular")
        self._stop_btn.add_css_class("control-button")
        self._stop_btn.set_sensitive(False)
        self._stop_btn.connect("clicked", self._on_stop_clicked)
        control_box.append(self._stop_btn)

        # Şu an çalan etiketi
        self._now_playing_label = Gtk.Label(label="Bir istasyon seçin")
        self._now_playing_label.set_ellipsize(Pango.EllipsizeMode.END)
        self._now_playing_label.set_hexpand(True)
        self._now_playing_label.add_css_class("now-playing-label")
        control_box.append(self._now_playing_label)

        main_box.append(control_box)

        # Ayırıcı
        main_box.append(Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL))

        # ── İstasyon Listesi ──
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_vexpand(True)
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)

        self._listbox = Gtk.ListBox()
        self._listbox.set_selection_mode(Gtk.SelectionMode.NONE)
        self._listbox.add_css_class("boxed-list")
        self._listbox.set_margin_start(12)
        self._listbox.set_margin_end(12)
        self._listbox.set_margin_top(8)
        self._listbox.set_margin_bottom(12)
        self._listbox.connect("row-activated", self._on_row_activated)

        # İstasyonları ekle
        for station in STATIONS:
            row = StationRow(station)
            self._listbox.append(row)
            self._station_rows[station.title] = row

        scrolled.set_child(self._listbox)
        main_box.append(scrolled)

        self.set_content(main_box)

    def _load_css(self) -> None:
        """Özel CSS stillerini yükler."""
        css_path = Path(__file__).parent / "radyola.css"
        if css_path.exists():
            provider = Gtk.CssProvider()
            provider.load_from_path(str(css_path))
            Gtk.StyleContext.add_provider_for_display(
                self.get_display(),
                provider,
                Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION,
            )

    # ── Olay İşleyicileri ──

    def _on_row_activated(self, listbox: Gtk.ListBox, row: StationRow) -> None:
        """İstasyona tıklandığında çal/duraklat toggle yapar."""
        station = row.station
        self._player.toggle(station)
        self._update_ui()

    def _on_stop_clicked(self, button: Gtk.Button) -> None:
        """Durdur butonuna basıldığında."""
        self._player.stop()
        self._update_ui()

    def _on_about_clicked(self, button: Gtk.Button) -> None:
        """Hakkında diyaloğunu gösterir."""
        about = Adw.AboutDialog(
            application_name="Radyola",
            application_icon="audio-x-generic",
            developer_name="aripd",
            version="1.0.0",
            comments="macOS MacTile projesinin Linux native karşılığı.\n"
            "GTK4 + Libadwaita + GStreamer tabanlı internet radyo çalar.",
            website="https://github.com/aripd/playground",
            license_type=Gtk.License.MIT_X11,
            developers=["aripd"],
        )
        about.present(self)

    def _on_player_error(self, error_msg: str) -> None:
        """Oynatıcı hatası durumunda kullanıcıyı bilgilendirir."""
        dialog = Adw.AlertDialog(
            heading="Bağlantı Hatası",
            body=f"Radyo akışına bağlanılamadı:\n{error_msg}",
        )
        dialog.add_response("ok", "Tamam")
        dialog.present(self)
        self._update_ui()

    def _on_player_state_changed(self, state: str) -> None:
        """Oynatıcı durumu değiştiğinde UI'ı günceller."""
        self._update_ui()

    # ── UI Güncelleme ──

    def _update_ui(self) -> None:
        """Tüm UI bileşenlerini oynatıcı durumuna göre günceller."""
        current = self._player.current_station
        is_playing = self._player.is_playing
        is_paused = self._player.is_paused

        # İstasyon satırlarını güncelle
        for title, row in self._station_rows.items():
            if current and current.title == title:
                row.set_active(True, paused=is_paused)
            else:
                row.set_active(False)

        # Kontrol çubuğunu güncelle
        if current:
            state_text = "⏸ Duraklatıldı" if is_paused else "🎵 Çalıyor"
            self._now_playing_label.set_label(f"{current.flag} {current.title}")
            self._title_widget.set_subtitle(f"{state_text} — {current.title}")
            self._stop_btn.set_sensitive(True)
        else:
            self._now_playing_label.set_label("Bir istasyon seçin")
            self._title_widget.set_subtitle("İnternet Radyo Çalar")
            self._stop_btn.set_sensitive(False)

    def cleanup(self) -> None:
        """Uygulama kapanışında kaynakları temizler."""
        self._player.cleanup()


# ──────────────────────────────────────────────
# Uygulama
# ──────────────────────────────────────────────


class RadyolaApp(Adw.Application):
    """Radyola GTK4/Libadwaita uygulaması."""

    def __init__(self):
        super().__init__(
            application_id="com.aripd.radyola",
            flags=Gio.ApplicationFlags.DEFAULT_FLAGS,
        )
        self._window: Optional[RadyolaWindow] = None

    def do_activate(self) -> None:
        """Uygulama aktifleştiğinde pencereyi oluşturur veya öne getirir."""
        if not self._window:
            self._window = RadyolaWindow(self)
        self._window.present()

    def do_shutdown(self) -> None:
        """Uygulama kapanışında temizlik yapar."""
        if self._window:
            self._window.cleanup()
        Adw.Application.do_shutdown(self)


# ──────────────────────────────────────────────
# Giriş Noktası
# ──────────────────────────────────────────────

def main() -> int:
    """Uygulamayı başlatır ve çıkış kodunu döndürür."""
    app = RadyolaApp()
    return app.run(sys.argv)


if __name__ == "__main__":
    sys.exit(main())

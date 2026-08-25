import Fuse from "fuse.js";

/**
 * İki liste, aynı şema (bkz. data/README.md).
 *
 * Ayrı tutulmalarının sebebi: elle seçilmiş 35 istasyon 3.400'ün içinde
 * kaybolur. Kuratörlü liste açılışta yüklenir, dizin yalnız istenirse çekilir.
 */
// Adresler denenme sırasıyla: özel alan adı DNS taşımalarında kesintiye
// düşebiliyor; GitHub Pages adresi her koşulda çalışır (özel alan adı
// bağlanınca GitHub oraya yönlendirir).
const DATA_HOSTS = [
  "https://radyola.aripd.com/data",
  "https://aripdcem.github.io/radyola/data",
];

const SOURCES = {
  curated: {
    urls: DATA_HOSTS.map((h) => `${h}/stations.json`),
    label: "Curated",
  },
  directory: {
    urls: DATA_HOSTS.map((h) => `${h}/directory.json`),
    label: "Discover",
  },
};

/** Dizinde ilk anda basılan satır sayısı; gerisi kaydırdıkça eklenir. */
const PAGE_SIZE = 60;

/** Tür şeridinde gösterilecek en fazla tür — dizinde 438 tür var. */
const MAX_GENRE_PILLS = 24;

/** Arama, tuş vuruşu başına değil bu gecikmeyle çalışır (ms). */
const SEARCH_DEBOUNCE = 150;

/* ── helpers ─────────────────────────────────────────────── */
function el(tag, cls, attrs) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (attrs) Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
  return e;
}

/**
 * ISO 3166-1 alpha-2 kodunu bayrak emoji'sine çevirir: "TR" → 🇹🇷
 * Her harf Unicode regional indicator karşılığına kaydırılır.
 */
function flagOf(code) {
  if (!code || code.length !== 2 || !/^[a-z]{2}$/i.test(code)) return "";
  return [...code.toUpperCase()]
    .map((c) => String.fromCodePoint(0x1f1e6 + c.charCodeAt(0) - 65))
    .join("");
}

/** İstasyonu benzersiz kılan anahtar — iki liste arasında da çakışmaz. */
function stationKey(s) {
  return `${s.name}|${s.url}`;
}

/**
 * Web sitesi bağlantısı olarak kullanılabilir mi?
 *
 * Discover verisi radio-browser.info'dan geliyor — herkesin düzenleyebildiği
 * bir veritabanı. `javascript:` gibi şemaları eleyip yalnız http(s) kabul
 * ediyoruz; adres ayrıca HTML'e gömülmez, DOM üzerinden atanır (bkz. _appendRows).
 */
function safeWebsite(url) {
  return /^https?:\/\//i.test(url) ? url : "";
}

/* ── CSS (loaded from external file into shadow DOM) ────── */
const CSS_PATH = "./radyola-player.css";

function createStyleLink() {
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = CSS_PATH;
  return link;
}

/* ── SVG icons ─────────────────────────── */
const SVG = {
  play: `<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>`,
  pause: `<svg viewBox="0 0 24 24"><path d="M6 4h4v16H6zm8 0h4v16h-4z"/></svg>`,
  search: `<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`,
  radio: `<svg viewBox="0 0 384 512"><path d="M73 39c-14.8-9.1-33.4-9.4-48.5-.9S0 62.6 0 80v352c0 17.4 9.4 33.4 24.5 41.9s33.7 8.1 48.5-.9L361 297c14.3-8.7 23-24.2 23-41s-8.7-32.2-23-41L73 39z"/></svg>`,
  extLink: `<svg viewBox="0 0 24 24"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>`,
  vol: `<svg viewBox="0 0 24 24"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07" fill="none" stroke="currentColor" stroke-width="2"/></svg>`,
  prev: `<svg viewBox="0 0 24 24"><path d="M19 20L9 12l10-8v16zM5 4v16"/></svg>`,
  next: `<svg viewBox="0 0 24 24"><path d="M5 4l10 8-10 8V4zM19 4v16"/></svg>`,
};

/* ══════════════════════════════════════════════════════════ */
/*  Web Component                                            */
/* ══════════════════════════════════════════════════════════ */
class AripdRadyola extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._source = "curated";
    this._lists = {};        // kaynak → istasyonlar (bir kez çekilir, bellekte kalır)
    this._fuse = null;       // arama indeksi; liste başına bir kez kurulur
    this._stations = [];
    this._filtered = [];
    this._rendered = 0;      // dizinde satırlar kaydırdıkça ekleniyor
    this._activeFilter = null;
    this._activeGenre = null;
    this._currentKey = null;
    this._isPlaying = false;
    this._searchTimer = null;
  }

  connectedCallback() {
    this._render();
    this._fetchData();
  }

  /* ── render skeleton ─────────────────────────── */
  _render() {
    const style = createStyleLink();

    const root = el("div", "radyola-root");
    root.innerHTML = `
      <div class="ambient-bg"><div class="orb"></div><div class="orb"></div><div class="orb"></div></div>
      <div class="app">
        <header class="header">
          <div class="logo">${SVG.radio}<span>Radyola</span></div>
          <p>Curated internet radio stations from around the world</p>
        </header>
        <div class="source-toggle" id="sourceToggle" role="tablist" aria-label="Station list">
          <button class="source-btn active" data-source="curated" role="tab" aria-selected="true">Curated</button>
          <button class="source-btn" data-source="directory" role="tab" aria-selected="false">Discover</button>
        </div>
        <div class="search-wrap">
          ${SVG.search}
          <input type="text" placeholder="Search stations..." id="searchInput" aria-label="Search stations">
        </div>
        <div class="locations" id="locationsBar"></div>
        <div class="locations" id="genresBar"></div>
        <div class="stations" id="stationList">
          <div class="loading"><div class="spinner"></div></div>
        </div>
      </div>
      <div class="player-bar" id="playerBar">
        <div class="player-inner">
          <div class="player-art">
            <div class="pulse-ring"></div>
            ${SVG.radio}
          </div>
          <div class="player-info">
            <div class="player-name" id="pName">Select a station</div>
            <div class="player-loc" id="pLoc">—</div>
          </div>
          <div class="player-controls">
            <button class="ctrl-btn" id="btnPrev" aria-label="Previous station">${SVG.prev}</button>
            <button class="ctrl-btn play-btn" id="btnPlay" aria-label="Play/Pause">${SVG.play}</button>
            <button class="ctrl-btn" id="btnNext" aria-label="Next station">${SVG.next}</button>
            <div class="vol-wrap">
              ${SVG.vol}
              <input type="range" class="vol-slider" id="volSlider" min="0" max="1" step="0.01" value="0.8" aria-label="Volume">
            </div>
          </div>
        </div>
      </div>
    `;

    this.shadowRoot.appendChild(style);
    this.shadowRoot.appendChild(root);

    /* cache refs */
    this._audio = document.createElement("audio");
    // Ölü yayına tıklayan kullanıcı aksi hâlde hiçbir şey görmüyor: play()
    // sözü sessizce reddediliyor, çubuk "çalıyor" gibi kalıyordu.
    this._audio.addEventListener("error", () => this._onStreamError());
    this._audio.addEventListener("stalled", () => {
      if (this._isPlaying) this._elBar.classList.add("is-buffering");
    });
    this._audio.addEventListener("playing", () => {
      this._elBar.classList.remove("is-buffering");
      this._setMediaSessionState("playing");
    });
    this._setupMediaSession();
    this._elList = this.shadowRoot.getElementById("stationList");
    this._elLocs = this.shadowRoot.getElementById("locationsBar");
    this._elGenres = this.shadowRoot.getElementById("genresBar");
    this._elSearch = this.shadowRoot.getElementById("searchInput");
    this._elToggle = this.shadowRoot.getElementById("sourceToggle");
    this._elBar = this.shadowRoot.getElementById("playerBar");
    this._elPName = this.shadowRoot.getElementById("pName");
    this._elPLoc = this.shadowRoot.getElementById("pLoc");
    this._btnPlay = this.shadowRoot.getElementById("btnPlay");
    this._btnPrev = this.shadowRoot.getElementById("btnPrev");
    this._btnNext = this.shadowRoot.getElementById("btnNext");
    this._volSlider = this.shadowRoot.getElementById("volSlider");

    /* events */
    this._elSearch.addEventListener("input", () => this._onSearch());
    this._elToggle.addEventListener("click", (e) => {
      const btn = e.target.closest(".source-btn");
      if (btn) this._setSource(btn.dataset.source);
    });
    this._btnPlay.addEventListener("click", () => this._togglePlay());
    this._btnPrev.addEventListener("click", () => this._skip(-1));
    this._btnNext.addEventListener("click", () => this._skip(1));
    this._volSlider.addEventListener("input", (e) => {
      this._audio.volume = parseFloat(e.target.value);
    });
    this._audio.volume = 0.8;
  }

  /* ── data ─────────────────────────── */
  /** Adresleri sırayla dener; ilk başarılı JSON yanıtı kazanır. */
  async _fetchFirstReachable(urls) {
    let lastError;
    for (const url of urls) {
      try {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
      } catch (err) {
        lastError = err;
      }
    }
    throw lastError;
  }

  async _fetchData(source = this._source) {
    // Daha önce çekilmişse ağa çıkma: kaynak geçişi anında olsun.
    if (this._lists[source]) {
      this._useList(source);
      return;
    }

    this._elList.innerHTML = `<div class="loading"><div class="spinner"></div></div>`;
    try {
      const json = await this._fetchFirstReachable(SOURCES[source].urls);

      const stations = json
        .map((r) => ({
          name: r.title || "",
          url: r.url || "",
          website: r.website || "",
          location: r.location || "",
          flag: flagOf(r.countryCode),
          genre: r.genre || "",
          votes: r.votes || 0,
        }))
        .filter((s) => s.name && s.url.startsWith("http"));

      // Kuratörlü listenin sırası elle verilmiş, korunur. Dizin rastgele
      // sırada geliyor; oy en anlamlı sıralama ölçütü.
      if (source === "directory") stations.sort((a, b) => b.votes - a.votes);

      stations.forEach((s) => {
        s.id = stationKey(s);
        s.search = `${s.name} ${s.location} ${s.genre}`;
      });

      this._lists[source] = stations;
      if (this._source === source) this._useList(source);
    } catch (err) {
      if (this._source !== source) return;
      this._elList.innerHTML = `<div class="empty-state">Failed to load stations. Please try again.</div>`;
    }
  }

  /** Çekilmiş bir listeyi ekrana bağlar: filtre şeritlerini ve arama indeksini kurar. */
  _useList(source) {
    this._stations = this._lists[source];
    // Fuse indeksi her tuş vuruşunda değil, liste başına bir kez kurulur —
    // 3.400 kayıtta yeniden indekslemek aramayı hissedilir şekilde yavaşlatıyordu.
    this._fuse = new Fuse(this._stations, {
      keys: ["name", "location", "genre"],
      threshold: 0.35,
      ignoreLocation: true,
    });
    this._buildLocations();
    this._buildGenres();
    this._applyFilters();
  }

  /** Curated ↔ Discover geçişi. */
  _setSource(source) {
    if (this._source === source) return;
    this._source = source;
    this._activeFilter = null;
    this._activeGenre = null;
    this._elSearch.value = "";
    this._elToggle.querySelectorAll(".source-btn").forEach((b) => {
      const active = b.dataset.source === source;
      b.classList.toggle("active", active);
      b.setAttribute("aria-selected", String(active));
    });
    this._elSearch.placeholder =
      source === "directory" ? "Search thousands of stations..." : "Search stations...";
    this._fetchData(source);
  }

  /* ── location filter pills ─────────────────────────── */
  _buildLocations() {
    this._elLocs.innerHTML = "";
    const map = {};
    this._stations.forEach((s) => {
      const country = s.location.split(",").pop().trim();
      if (country) map[country] = (map[country] || 0) + 1;
    });

    // "All" pill
    const allPill = el("button", "loc-pill active");
    allPill.textContent = "All";
    allPill.addEventListener("click", () => {
      this._activeFilter = null;
      this._applyFilters();
      this._elLocs.querySelectorAll(".loc-pill").forEach((p) => p.classList.remove("active"));
      allPill.classList.add("active");
    });
    this._elLocs.appendChild(allPill);

    Object.keys(map).sort().forEach((country) => {
      const pill = el("button", "loc-pill");
      pill.textContent = country;
      pill.addEventListener("click", () => {
        this._activeFilter = country;
        this._applyFilters();
        this._elLocs.querySelectorAll(".loc-pill").forEach((p) => p.classList.remove("active"));
        pill.classList.add("active");
      });
      this._elLocs.appendChild(pill);
    });
  }

  /* ── genre filter pills ─────────────────────────── */
  _buildGenres() {
    this._elGenres.innerHTML = "";
    const counts = {};
    this._stations.forEach((s) => {
      if (s.genre) {
        s.genre.split("/").forEach((g) => {
          const gt = g.trim();
          if (gt) counts[gt] = (counts[gt] || 0) + 1;
        });
      }
    });

    // Dizinde 438 tür var; hepsini şeride basmak onu kullanılmaz hâle getirir.
    const map = {};
    Object.entries(counts)
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .slice(0, MAX_GENRE_PILLS)
      .forEach(([g, n]) => (map[g] = n));

    const allPill = el("button", "loc-pill active");
    allPill.textContent = "All Genres";
    allPill.addEventListener("click", () => {
      this._activeGenre = null;
      this._applyFilters();
      this._elGenres.querySelectorAll(".loc-pill").forEach((p) => p.classList.remove("active"));
      allPill.classList.add("active");
    });
    this._elGenres.appendChild(allPill);

    Object.keys(map).sort().forEach((genre) => {
      const pill = el("button", "loc-pill");
      pill.textContent = genre;
      pill.addEventListener("click", () => {
        this._activeGenre = genre;
        this._applyFilters();
        this._elGenres.querySelectorAll(".loc-pill").forEach((p) => p.classList.remove("active"));
        pill.classList.add("active");
      });
      this._elGenres.appendChild(pill);
    });
  }

  _applyFilters() {
    const query = this._elSearch.value.trim();

    // Arama önce: hazır indeks üzerinden çalışıp sonucu daraltıyoruz. Böylece
    // her tuş vuruşunda 3.400 kayıt yeniden indekslenmiyor.
    let list = query.length > 0
      ? this._fuse.search(query).map((r) => r.item)
      : this._stations;

    if (this._activeFilter) {
      list = list.filter((s) => s.location.includes(this._activeFilter));
    }
    if (this._activeGenre) {
      list = list.filter((s) => s.genre && s.genre.includes(this._activeGenre));
    }

    this._filtered = list;
    this._renderList();
  }

  _onSearch() {
    // Dizinde her harfte arama çalıştırmak gereksiz iş; kullanıcı durunca çalışsın.
    clearTimeout(this._searchTimer);
    this._searchTimer = setTimeout(() => this._applyFilters(), SEARCH_DEBOUNCE);
  }

  /* ── render station list ─────────────────────────── */
  /**
   * Listeyi baştan çizer.
   *
   * Dizinde 3.400 kayıt var; hepsini DOM'a basmak sayfayı dondurur. İlk sayfa
   * çizilir, gerisi listenin sonundaki gözcü görünür oldukça eklenir.
   */
  _renderList() {
    this._elList.innerHTML = "";
    this._rendered = 0;

    if (this._filtered.length === 0) {
      this._elList.innerHTML = `<div class="empty-state">No stations found</div>`;
      return;
    }

    this._renderMore();
  }

  _renderMore() {
    const slice = this._filtered.slice(this._rendered, this._rendered + PAGE_SIZE);
    this._rendered += slice.length;
    this._appendRows(slice);

    this._sentinel?.remove();
    this._sentinel = null;
    if (this._rendered >= this._filtered.length) return;

    // Gözcü görününce bir sayfa daha ekle.
    this._sentinel = el("div", "list-sentinel");
    this._elList.appendChild(this._sentinel);
    this._observer ??= new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) this._renderMore();
    });
    this._observer.disconnect();
    this._observer.observe(this._sentinel);
  }

  _appendRows(stations) {
    stations.forEach((s) => {
      const row = el("div", "station" + (s.id === this._currentKey ? " playing" : ""));
      row.dataset.id = s.id;

      row.innerHTML = `
        <div class="station-icon">
          <svg class="play-ico" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
          <div class="eq-bars"><div class="eq-bar"></div><div class="eq-bar"></div><div class="eq-bar"></div><div class="eq-bar"></div></div>
        </div>
        <div class="station-info">
          <div class="station-name">${s.flag ? `<span class="station-flag">${s.flag}</span>` : ""}${this._esc(s.name)}</div>
          <div class="station-meta">
            <span class="station-loc">${this._esc(s.location)}</span>
            ${s.genre ? `<span class="station-genre">${this._esc(s.genre)}</span>` : ""}
          </div>
        </div>
      `;

      // Website bağlantısı HTML string'ine gömülmez: _esc tırnak kaçırmıyor,
      // veri kaynağı da topluluk düzenlemesine açık — href'ten öznitelik
      // enjeksiyonu mümkün olurdu. setAttribute bu sınıf hatayı yapısal kapatır.
      const site = safeWebsite(s.website);
      if (site) {
        const ext = el("a", "station-ext", {
          href: site, target: "_blank", rel: "noopener", title: "Visit website",
        });
        ext.innerHTML = SVG.extLink;
        row.appendChild(ext);
      }

      row.addEventListener("click", (e) => {
        if (e.target.closest(".station-ext")) return;
        this._playStation(s.id);
      });
      this._elList.appendChild(row);
    });
  }

  /* ── playback ─────────────────────────── */
  _playStation(id) {
    const s = this._stations.find((st) => st.id === id);
    if (!s) return;

    this._currentKey = id;
    this._audio.pause();
    this._audio.src = s.url;
    this._audio.load();
    this._audio.play().catch(() => {});
    this._isPlaying = true;

    this._elPName.textContent = s.name;
    this._elPLoc.textContent = s.genre ? `${s.location}  ·  ${s.genre}` : s.location;
    this._elPLoc.classList.remove("error");
    document.title = `${s.name} — Radyola`;
    this._btnPlay.innerHTML = SVG.pause;
    this._elBar.classList.add("visible", "is-playing");

    this._updateMediaSessionMetadata(s);
    this._highlightPlaying();
  }

  /** Akış açılamadı: çubuğu duraklat ve nedenini söyle. */
  _onStreamError() {
    if (!this._currentKey || !this._audio.error) return;
    this._isPlaying = false;
    this._btnPlay.innerHTML = SVG.play;
    this._elBar.classList.remove("is-playing", "is-buffering");
    this._elPLoc.textContent = "Stream unavailable — try another station";
    this._elPLoc.classList.add("error");
    this._setMediaSessionState("paused");
    this._highlightPlaying();
  }

  /* ── Media Session API ─────────────────────────── */
  /**
   * Tarayıcının medya arayüzüne bağlanır: klavye medya tuşları, kulaklık
   * tuşları ve telefonda kilit ekranı/bildirim kontrolleri.
   */
  _setupMediaSession() {
    if (!("mediaSession" in navigator)) return;
    const on = (action, handler) => {
      // Tarayıcı tanımadığı eylemde fırlatır; desteklenenler yeter.
      try { navigator.mediaSession.setActionHandler(action, handler); } catch { /* yok say */ }
    };
    on("play", () => this._togglePlay());
    on("pause", () => this._togglePlay());
    on("previoustrack", () => this._skip(-1));
    on("nexttrack", () => this._skip(1));
  }

  _updateMediaSessionMetadata(s) {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: s.name,
      artist: s.genre ? `${s.location} · ${s.genre}` : s.location,
      album: "Radyola",
    });
    this._setMediaSessionState("playing");
  }

  _setMediaSessionState(state) {
    if ("mediaSession" in navigator) navigator.mediaSession.playbackState = state;
  }

  /**
   * Çalan satırı işaretler.
   *
   * Listeyi baştan çizmiyoruz: dizinde kullanıcı yüzlerce satır kaydırmış
   * olabilir, yeniden çizim onu başa atardı.
   */
  _highlightPlaying() {
    this._elList.querySelectorAll(".station").forEach((row) => {
      row.classList.toggle("playing", row.dataset.id === this._currentKey);
    });
  }

  _togglePlay() {
    if (!this._currentKey) return;
    if (this._audio.paused) {
      this._audio.play().catch(() => {});
      this._isPlaying = true;
      this._btnPlay.innerHTML = SVG.pause;
      this._elBar.classList.add("is-playing");
      this._setMediaSessionState("playing");
    } else {
      this._audio.pause();
      this._isPlaying = false;
      this._btnPlay.innerHTML = SVG.play;
      this._elBar.classList.remove("is-playing");
      this._setMediaSessionState("paused");
    }
    this._highlightPlaying();
  }

  _skip(dir) {
    if (this._filtered.length === 0) return;
    const curFilterIdx = this._filtered.findIndex((s) => s.id === this._currentKey);
    let next = curFilterIdx + dir;
    if (next < 0) next = this._filtered.length - 1;
    if (next >= this._filtered.length) next = 0;
    this._playStation(this._filtered[next].id);
  }

  _esc(str) {
    const d = document.createElement("div");
    d.textContent = str;
    return d.innerHTML;
  }

  disconnectedCallback() {
    this._audio.pause();
    this._audio.src = "";
    clearTimeout(this._searchTimer);
    this._observer?.disconnect();
  }
}

customElements.define("aripd-radyola", AripdRadyola);

/* ── Auto dark mode (host page) ─────────────────────────── */
;(function () {
  const h = document.querySelector("html");
  if (h.getAttribute("data-bs-theme") === "auto") {
    function updateTheme() {
      h.setAttribute("data-bs-theme",
        window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
      );
    }
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", updateTheme);
    updateTheme();
  }
})();
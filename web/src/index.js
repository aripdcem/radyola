import Fuse from "fuse.js";

const SHEET_URL =
  "https://sheets.googleapis.com/v4/spreadsheets/1WetccPDwGuUAqNQzUTVNCKy1k48MDM1bvLnDlfdRhis/values/Sheet1?alt=json&key=AIzaSyD9GqS9UoqRh3Pl2kZVbTZ9GGpp1OgWJRY";

/* ── helpers ─────────────────────────────────────────────── */
function el(tag, cls, attrs) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (attrs) Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
  return e;
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
    this._stations = [];
    this._filtered = [];
    this._activeFilter = null;
    this._currentIdx = -1;
    this._isPlaying = false;
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
        <div class="search-wrap">
          ${SVG.search}
          <input type="text" placeholder="Search stations..." id="searchInput" aria-label="Search stations">
        </div>
        <div class="locations" id="locationsBar"></div>
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
    this._elList = this.shadowRoot.getElementById("stationList");
    this._elLocs = this.shadowRoot.getElementById("locationsBar");
    this._elSearch = this.shadowRoot.getElementById("searchInput");
    this._elBar = this.shadowRoot.getElementById("playerBar");
    this._elPName = this.shadowRoot.getElementById("pName");
    this._elPLoc = this.shadowRoot.getElementById("pLoc");
    this._btnPlay = this.shadowRoot.getElementById("btnPlay");
    this._btnPrev = this.shadowRoot.getElementById("btnPrev");
    this._btnNext = this.shadowRoot.getElementById("btnNext");
    this._volSlider = this.shadowRoot.getElementById("volSlider");

    /* events */
    this._elSearch.addEventListener("input", () => this._onSearch());
    this._btnPlay.addEventListener("click", () => this._togglePlay());
    this._btnPrev.addEventListener("click", () => this._skip(-1));
    this._btnNext.addEventListener("click", () => this._skip(1));
    this._volSlider.addEventListener("input", (e) => {
      this._audio.volume = parseFloat(e.target.value);
    });
    this._audio.volume = 0.8;
  }

  /* ── data ─────────────────────────── */
  async _fetchData() {
    try {
      const res = await fetch(SHEET_URL);
      const json = await res.json();
      this._stations = json.values.map((r, i) => ({
        id: i,
        name: r[1],
        url: r[2],
        website: r[3] || "",
        location: r[4] || "",
      }));
      this._filtered = [...this._stations];
      this._buildLocations();
      this._renderList();
    } catch (err) {
      this._elList.innerHTML = `<div class="empty-state">Failed to load stations. Please try again.</div>`;
    }
  }

  /* ── location filter pills ─────────────────────────── */
  _buildLocations() {
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

  _applyFilters() {
    const query = this._elSearch.value.trim();
    let list = [...this._stations];

    if (this._activeFilter) {
      list = list.filter((s) => s.location.includes(this._activeFilter));
    }

    if (query.length > 0) {
      const fuse = new Fuse(list, { keys: ["name", "location"], threshold: 0.35 });
      list = fuse.search(query).map((r) => r.item);
    }

    this._filtered = list;
    this._renderList();
  }

  _onSearch() {
    this._applyFilters();
  }

  /* ── render station list ─────────────────────────── */
  _renderList() {
    this._elList.innerHTML = "";

    if (this._filtered.length === 0) {
      this._elList.innerHTML = `<div class="empty-state">No stations found</div>`;
      return;
    }

    this._filtered.forEach((s) => {
      const row = el("div", "station" + (s.id === this._currentIdx ? " playing" : ""));
      row.dataset.id = s.id;

      row.innerHTML = `
        <div class="station-icon">
          <svg class="play-ico" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
          <div class="eq-bars"><div class="eq-bar"></div><div class="eq-bar"></div><div class="eq-bar"></div><div class="eq-bar"></div></div>
        </div>
        <div class="station-info">
          <div class="station-name">${this._esc(s.name)}</div>
          <div class="station-loc">${this._esc(s.location)}</div>
        </div>
        ${s.website ? `<a class="station-ext" href="${this._esc(s.website)}" target="_blank" rel="noopener" title="Visit website">${SVG.extLink}</a>` : ""}
      `;

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

    this._currentIdx = id;
    this._audio.pause();
    this._audio.src = s.url;
    this._audio.load();
    this._audio.play().catch(() => {});
    this._isPlaying = true;

    this._elPName.textContent = s.name;
    this._elPLoc.textContent = s.location;
    document.title = `${s.name} — Radyola`;
    this._btnPlay.innerHTML = SVG.pause;
    this._elBar.classList.add("visible", "is-playing");

    this._renderList();
  }

  _togglePlay() {
    if (this._currentIdx < 0) return;
    if (this._audio.paused) {
      this._audio.play().catch(() => {});
      this._isPlaying = true;
      this._btnPlay.innerHTML = SVG.pause;
      this._elBar.classList.add("is-playing");
    } else {
      this._audio.pause();
      this._isPlaying = false;
      this._btnPlay.innerHTML = SVG.play;
      this._elBar.classList.remove("is-playing");
    }
    this._renderList();
  }

  _skip(dir) {
    if (this._filtered.length === 0) return;
    const curFilterIdx = this._filtered.findIndex((s) => s.id === this._currentIdx);
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
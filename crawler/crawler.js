/**
 * Radyola — radio-browser.info Crawler
 *
 * radio-browser.info API'sinden radyo istasyonu verilerini çeker ve
 * Radyola projesinin kullandığı Google Sheets CSV formatına uygun dosya üretir.
 *
 * Kullanım:
 *   node crawler.js                         → Tüm ülkeler (varsayılan liste)
 *   node crawler.js --countries TR,BE,GB    → Belirli ülke kodları
 *   node crawler.js --all                   → Tüm dünya
 *   node crawler.js --min-votes 10          → Minimum oy sayısı filtresi
 *   node crawler.js --working-only          → Sadece çalışan istasyonlar
 */

const https = require("https");
const http = require("http");
const fs = require("fs");
const path = require("path");

/* ── Configuration ──────────────────────────────────────────── */

const API_BASE = "https://de1.api.radio-browser.info";

// Radyola projesinde varsayılan ülkeler
const DEFAULT_COUNTRIES = ["TR", "BE", "GB", "RU", "GR", "DE", "FR", "NL", "US", "JP"];

const COUNTRY_NAME_MAP = {
  TR: "Türkiye",
  BE: "Belgium",
  GB: "United Kingdom",
  RU: "Russia",
  GR: "Greece",
  DE: "Germany",
  FR: "France",
  NL: "Netherlands",
  US: "United States",
  JP: "Japan",
  IT: "Italy",
  ES: "Spain",
  BR: "Brazil",
  CA: "Canada",
  AU: "Australia",
  SE: "Sweden",
  NO: "Norway",
  DK: "Denmark",
  FI: "Finland",
  AT: "Austria",
  CH: "Switzerland",
  PT: "Portugal",
  PL: "Poland",
  CZ: "Czechia",
  IE: "Ireland",
  AR: "Argentina",
  MX: "Mexico",
  IN: "India",
  KR: "South Korea",
  ZA: "South Africa",
};

/* ── CLI Argument Parsing ──────────────────────────────────── */

function parseArgs() {
  const args = process.argv.slice(2);
  const config = {
    countries: DEFAULT_COUNTRIES,
    all: false,
    minVotes: 0,
    workingOnly: true,
    limit: 0, // 0 = no limit
  };

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--countries":
        config.countries = args[++i].split(",").map((c) => c.trim().toUpperCase());
        break;
      case "--all":
        config.all = true;
        break;
      case "--min-votes":
        config.minVotes = parseInt(args[++i], 10) || 0;
        break;
      case "--working-only":
        config.workingOnly = true;
        break;
      case "--include-broken":
        config.workingOnly = false;
        break;
      case "--limit":
        config.limit = parseInt(args[++i], 10) || 0;
        break;
      case "--help":
        console.log(`
Radyola Crawler - radio-browser.info'dan radyo istasyonları çeker

Kullanım:
  node crawler.js [seçenekler]

Seçenekler:
  --countries TR,BE,GB   Belirli ülke kodları (virgülle ayrılmış)
  --all                  Tüm dünya radyoları
  --min-votes N          Minimum oy sayısı (varsayılan: 0)
  --working-only         Sadece çalışan istasyonlar (varsayılan)
  --include-broken       Bozuk istasyonları da dahil et
  --limit N              Ülke başına maksimum istasyon sayısı
  --help                 Bu yardım mesajını gösterir
`);
        process.exit(0);
    }
  }

  return config;
}

/* ── HTTP Fetch Helper ─────────────────────────────────────── */

function fetchJSON(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith("https") ? https : http;
    const req = client.get(
      url,
      {
        headers: {
          "User-Agent": "Radyola-Crawler/1.0 (https://gitlab.com/aripd/radyola)",
          Accept: "application/json",
        },
      },
      (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} for ${url}`));
          res.resume();
          return;
        }
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => {
          try {
            resolve(JSON.parse(data));
          } catch (e) {
            reject(new Error(`JSON parse error: ${e.message}`));
          }
        });
      }
    );
    req.on("error", reject);
    req.setTimeout(30000, () => {
      req.destroy();
      reject(new Error(`Timeout for ${url}`));
    });
  });
}

/* ── Delay helper ──────────────────────────────────────────── */

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/* ── Fetch all countries ───────────────────────────────────── */

async function fetchAllCountryCodes() {
  console.log("📡 Tüm ülke kodları çekiliyor...");
  const countries = await fetchJSON(`${API_BASE}/json/countries`);
  return countries
    .filter((c) => c.stationcount > 0)
    .map((c) => c.iso_3166_1)
    .filter(Boolean);
}

/* ── Fetch stations for a country ──────────────────────────── */

async function fetchStationsByCountry(countryCode, config) {
  const params = new URLSearchParams({
    order: "clickcount",
    reverse: "true",
    hidebroken: config.workingOnly ? "true" : "false",
  });

  if (config.limit > 0) {
    params.set("limit", config.limit.toString());
  }

  const url = `${API_BASE}/json/stations/bycountrycodeexact/${countryCode}?${params}`;
  const stations = await fetchJSON(url);

  // Filter by minimum votes
  return stations.filter((s) => s.votes >= config.minVotes);
}

/* ── Transform to Radyola format ───────────────────────────── */

function transformStation(station) {
  // Build location: "City, Country" or just "Country"
  const city = station.state || "";
  const country = station.country || COUNTRY_NAME_MAP[station.countrycode] || station.countrycode;
  const location = city ? `${city}, ${country}` : country;

  // Tags → genre (capitalize, clean up)
  const genre = station.tags
    ? station.tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean)
        .map((t) => t.charAt(0).toUpperCase() + t.slice(1))
        .slice(0, 3) // Max 3 genres
        .join(" / ")
    : "";

  return {
    // Radyola CSV format fields
    date: new Date().toISOString().split("T")[0],
    title: station.name.trim(),
    url: station.url_resolved || station.url,
    website: station.homepage || "",
    location: location,
    genre: genre,

    // Extended fields (JSON only)
    _meta: {
      stationuuid: station.stationuuid,
      countrycode: station.countrycode,
      language: station.language || "",
      codec: station.codec || "",
      bitrate: station.bitrate || 0,
      votes: station.votes || 0,
      clickcount: station.clickcount || 0,
      favicon: station.favicon || "",
      geo_lat: station.geo_lat,
      geo_long: station.geo_long,
      hls: station.hls === 1,
      lastcheckok: station.lastcheckok === 1,
      ssl_error: station.ssl_error === 1,
    },
  };
}

/* ── CSV Export ─────────────────────────────────────────────── */

function escapeCSV(str) {
  if (!str) return "";
  if (str.includes(",") || str.includes('"') || str.includes("\n")) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

function toCSV(stations) {
  const header = "date,title,url,website,location,genre";
  const rows = stations.map(
    (s) =>
      [s.date, s.title, s.url, s.website, s.location, s.genre].map(escapeCSV).join(",")
  );
  return [header, ...rows].join("\n");
}

/* ── Main ──────────────────────────────────────────────────── */

async function main() {
  const config = parseArgs();
  const outputDir = path.join(__dirname, "output");

  // Create output directory
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  console.log("╔══════════════════════════════════════════╗");
  console.log("║   📻 Radyola — Radio Browser Crawler     ║");
  console.log("╚══════════════════════════════════════════╝");
  console.log();

  // Determine which countries to crawl
  let countries = config.countries;
  if (config.all) {
    countries = await fetchAllCountryCodes();
    console.log(`🌍 ${countries.length} ülke bulundu\n`);
  }

  const allStations = [];
  const stats = { total: 0, countries: 0, withGenre: 0, withCoords: 0 };

  for (let i = 0; i < countries.length; i++) {
    const code = countries[i];
    const countryName = COUNTRY_NAME_MAP[code] || code;

    process.stdout.write(
      `[${i + 1}/${countries.length}] 🔍 ${countryName} (${code})...`
    );

    try {
      const stations = await fetchStationsByCountry(code, config);
      const transformed = stations.map(transformStation);
      allStations.push(...transformed);

      const withGenre = transformed.filter((s) => s.genre).length;
      const withCoords = transformed.filter(
        (s) => s._meta.geo_lat && s._meta.geo_long
      ).length;

      stats.total += transformed.length;
      stats.countries++;
      stats.withGenre += withGenre;
      stats.withCoords += withCoords;

      console.log(
        ` ✅ ${transformed.length} istasyon (${withGenre} genre, ${withCoords} koordinat)`
      );
    } catch (err) {
      console.log(` ❌ Hata: ${err.message}`);
    }

    // Be polite to the API
    if (i < countries.length - 1) {
      await delay(500);
    }
  }

  console.log();
  console.log("═══════════════════════════════════════════");
  console.log(`📊 Toplam: ${stats.total} istasyon, ${stats.countries} ülke`);
  console.log(`🏷️  Genre bilgisi olan: ${stats.withGenre}`);
  console.log(`📍 Koordinatı olan: ${stats.withCoords}`);
  console.log("═══════════════════════════════════════════");

  // Remove duplicates by stream URL
  const uniqueMap = new Map();
  allStations.forEach((s) => {
    const key = s.url.toLowerCase();
    if (!uniqueMap.has(key) || (uniqueMap.get(key).genre === "" && s.genre !== "")) {
      uniqueMap.set(key, s);
    }
  });
  const uniqueStations = [...uniqueMap.values()];
  console.log(`🔄 Tekrar edenler kaldırıldı: ${allStations.length} → ${uniqueStations.length}`);

  // Sort by country then by clickcount (desc)
  uniqueStations.sort((a, b) => {
    if (a.location < b.location) return -1;
    if (a.location > b.location) return 1;
    return (b._meta.clickcount || 0) - (a._meta.clickcount || 0);
  });

  // ── Write CSV (Radyola Google Sheets format) ──
  const csvData = toCSV(uniqueStations);
  const csvPath = path.join(outputDir, "stations.csv");
  fs.writeFileSync(csvPath, "\uFEFF" + csvData, "utf8"); // BOM for Excel compatibility
  console.log(`\n📄 CSV kaydedildi: ${csvPath}`);

  // ── Write full JSON (with all metadata) ──
  const jsonData = JSON.stringify(uniqueStations, null, 2);
  const jsonPath = path.join(outputDir, "stations.json");
  fs.writeFileSync(jsonPath, jsonData, "utf8");
  console.log(`📄 JSON kaydedildi: ${jsonPath}`);

  // ── Write compact JSON (without _meta, for direct use) ──
  const compactStations = uniqueStations.map(({ _meta, ...rest }) => rest);
  const compactPath = path.join(outputDir, "stations-compact.json");
  fs.writeFileSync(compactPath, JSON.stringify(compactStations, null, 2), "utf8");
  console.log(`📄 Compact JSON kaydedildi: ${compactPath}`);

  // ── Write per-country files ──
  const byCountry = {};
  uniqueStations.forEach((s) => {
    const code = s._meta.countrycode;
    if (!byCountry[code]) byCountry[code] = [];
    byCountry[code].push(s);
  });

  const countriesDir = path.join(outputDir, "countries");
  if (!fs.existsSync(countriesDir)) {
    fs.mkdirSync(countriesDir, { recursive: true });
  }

  for (const [code, stations] of Object.entries(byCountry)) {
    const countryCSV = toCSV(stations);
    fs.writeFileSync(path.join(countriesDir, `${code}.csv`), "\uFEFF" + countryCSV, "utf8");
    fs.writeFileSync(
      path.join(countriesDir, `${code}.json`),
      JSON.stringify(stations, null, 2),
      "utf8"
    );
  }
  console.log(`📁 Ülke bazlı dosyalar: ${countriesDir}`);

  // ── Summary report ──
  console.log("\n╔══════════════════════════════════════════╗");
  console.log("║   ✅ Crawl tamamlandı!                    ║");
  console.log("╚══════════════════════════════════════════╝");
  console.log("\nÜlke bazlı dağılım:");
  Object.entries(byCountry)
    .sort((a, b) => b[1].length - a[1].length)
    .forEach(([code, stations]) => {
      const name = COUNTRY_NAME_MAP[code] || code;
      const bar = "█".repeat(Math.ceil(stations.length / 20));
      console.log(`  ${code} ${name.padEnd(20)} ${String(stations.length).padStart(5)} ${bar}`);
    });
}

main().catch((err) => {
  console.error("\n❌ Fatal error:", err.message);
  process.exit(1);
});

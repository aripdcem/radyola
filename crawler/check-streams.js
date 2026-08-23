/**
 * Radyola — Yayın Denetleyici
 *
 * data/stations.json içindeki her akış adresini deneyip ölenleri raporlar.
 * Kuratörlü liste zamanla çürüyor ve bunu kimse fark etmiyor; Android'in
 * tohum modelinde bu liste yeni kullanıcının başlangıç noktası olduğu için
 * temiz kalması gerekiyor.
 *
 * Kullanım:
 *   node check-streams.js                    → data/stations.json
 *   node check-streams.js --directory        → data/directory.json (yavaş)
 *   node check-streams.js --file yol.json
 *   node check-streams.js --json rapor.json  → makine okunur çıktı
 *   node check-streams.js --fail-on-dead     → ölü varsa çıkış kodu 1 (CI)
 */

const https = require("https");
const http = require("http");
const fs = require("fs");
const path = require("path");

/* ── Configuration ──────────────────────────────────────────── */

const DEFAULTS = {
  concurrency: 8,
  timeoutMs: 12_000,
  // Bir yayını ölü ilan etmeden önceki deneme sayısı. Canlı yayın sunucuları
  // anlık olarak bağlantı reddedebiliyor; tek denemeyle karar vermek düzenli
  // yanlış alarm üretir ve rapor güvenilirliğini yok eder.
  attempts: 3,
  retryDelayMs: 4_000,
  // Yayının gerçekten veri gönderdiğini görmek için beklenen en az bayt.
  // Bazı sunucular 200 dönüp hiç veri göndermiyor.
  minBytes: 512,
  // Oynatıcıların kullandığı kimliğin aynısı. Denetleyiciye özel bir kimlik
  // kullanmak yanıltıcı sonuç veriyor: ITU'nun Shoutcast sunucusu
  // "Radyola-HealthCheck/1.0 (https://...)" adresini 403 ile reddederken
  // "Radyola/1.0" ile sorunsuz yanıt veriyor. Soru "sunucu bizi kabul ediyor
  // mu" değil, "kullanıcı bu yayını çalabiliyor mu".
  userAgent: "Radyola/1.0",
};

/* ── CLI ────────────────────────────────────────────────────── */

function parseArgs() {
  const args = process.argv.slice(2);
  const config = {
    ...DEFAULTS,
    file: path.join(__dirname, "..", "data", "stations.json"),
    jsonOut: null,
    failOnDead: false,
  };

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--directory":
        config.file = path.join(__dirname, "..", "data", "directory.json");
        break;
      case "--file":
        config.file = path.resolve(args[++i]);
        break;
      case "--json":
        config.jsonOut = path.resolve(args[++i]);
        break;
      case "--concurrency":
        config.concurrency = parseInt(args[++i], 10) || DEFAULTS.concurrency;
        break;
      case "--attempts":
        config.attempts = parseInt(args[++i], 10) || DEFAULTS.attempts;
        break;
      case "--timeout":
        config.timeoutMs = (parseInt(args[++i], 10) || 12) * 1000;
        break;
      case "--fail-on-dead":
        config.failOnDead = true;
        break;
      case "--help":
        console.log(`
Radyola Yayın Denetleyici

  node check-streams.js [seçenekler]

  --directory        data/directory.json'ı denetle (3.400 kayıt, yavaş)
  --file YOL         Belirli bir JSON dosyası
  --json YOL         Raporu JSON olarak yaz
  --concurrency N    Eşzamanlı istek (varsayılan: ${DEFAULTS.concurrency})
  --attempts N       Ölü saymadan önceki deneme (varsayılan: ${DEFAULTS.attempts})
  --timeout N        Saniye cinsinden zaman aşımı (varsayılan: 12)
  --fail-on-dead     Ölü yayın varsa çıkış kodu 1 döndür
  --help             Bu mesaj
`);
        process.exit(0);
    }
  }
  return config;
}

/* ── Playlist resolution ────────────────────────────────────── */

/** `.pls` / `.m3u` çalma listesi mi? `.m3u8` (HLS) doğrudan çalınabilir, değil. */
function isPlaylist(url) {
  const p = url.split("?")[0].toLowerCase();
  return p.endsWith(".pls") || p.endsWith(".m3u");
}

/**
 * HLS mi? Manifest küçük bir metin dosyası olduğu için bayt sayarak
 * denetlenemez — ayrı yoldan doğrulanır.
 */
function isHls(url, contentType = "") {
  if (/^application\/(vnd\.apple\.mpegurl|x-mpegurl)/i.test(contentType)) return true;
  if (/^audio\/(mpegurl|x-mpegurl)/i.test(contentType)) return true;
  return url.split("?")[0].toLowerCase().endsWith(".m3u8");
}

/** Çalma listesi gövdesinden ilk akış adresini çıkarır. */
function firstStreamUrl(body) {
  const lines = body
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  const pls = lines.find((l) => /^file\d*\s*=/i.test(l));
  if (pls) {
    const value = pls.slice(pls.indexOf("=") + 1).trim();
    if (value.startsWith("http")) return value;
  }
  return lines.find((l) => !l.startsWith("#") && l.startsWith("http")) || null;
}

/* ── Single request ─────────────────────────────────────────── */

/**
 * Adresi açıp ilk baytların gelip gelmediğine bakar.
 *
 * Yalnız durum koduna bakmak yetmiyor: 200 dönüp hiç veri göndermeyen
 * sunucular var. [minBytes] kadar veri akmazsa yayın ölü sayılır.
 */
function probe(url, config, redirectsLeft = 5) {
  return new Promise((resolve) => {
    let target;
    try {
      target = new URL(url);
    } catch {
      resolve({ ok: false, reason: "invalid-url" });
      return;
    }
    if (target.protocol !== "http:" && target.protocol !== "https:") {
      resolve({ ok: false, reason: `unsupported-scheme:${target.protocol}` });
      return;
    }

    const client = target.protocol === "https:" ? https : http;
    let settled = false;
    let received = 0;

    const done = (result) => {
      if (settled) return;
      settled = true;
      try {
        req.destroy();
      } catch {}
      resolve(result);
    };

    const req = client.get(
      url,
      // Aralık isteği: tüm yayını indirmeden ilk baytları görmek için.
      requestOptions(config, { Range: "bytes=0-4095", "Icy-MetaData": "0" }),
      (res) => {
        const status = res.statusCode;

        if (status >= 300 && status < 400 && res.headers.location) {
          res.resume();
          if (redirectsLeft <= 0) {
            done({ ok: false, reason: "too-many-redirects" });
            return;
          }
          const next = new URL(res.headers.location, url).toString();
          probe(next, config, redirectsLeft - 1).then(done);
          return;
        }

        if (status !== 200 && status !== 206) {
          res.resume();
          done({ ok: false, reason: `http-${status}` });
          return;
        }

        const contentType = res.headers["content-type"] || "";
        res.on("data", (chunk) => {
          received += chunk.length;
          if (received >= config.minBytes) {
            done({ ok: true, status, contentType, bytes: received });
          }
        });
        res.on("end", () => {
          if (received >= config.minBytes) {
            done({ ok: true, status, contentType, bytes: received });
          } else {
            done({ ok: false, reason: `no-data(${received}b)`, status, contentType });
          }
        });
        res.on("error", (e) => done({ ok: false, reason: `stream:${e.code || e.message}` }));
      }
    );

    req.on("timeout", () => done({ ok: false, reason: "timeout" }));
    req.on("error", (e) => done({ ok: false, reason: e.code || e.message }));
  });
}

/* ── Station check ──────────────────────────────────────────── */

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * HLS akışını manifest üzerinden doğrular.
 *
 * Bayt saymak burada işe yaramaz: master playlist 200-300 bayt bir metin.
 * Master ise ilk varyanta inip segment (`#EXTINF`) olup olmadığına bakıyoruz —
 * segment listesi varsa yayın gerçekten üretiliyor demektir.
 */
async function checkHls(url, config, depth = 0) {
  const fetched = await fetchText(url, config);
  if (!fetched.ok) return { ok: false, reason: `hls:${fetched.reason}` };
  const body = fetched.body;
  if (!body.trimStart().startsWith("#EXTM3U")) {
    return { ok: false, reason: "hls-invalid-manifest" };
  }
  if (/#EXTINF/i.test(body)) {
    return { ok: true, kind: "hls", bytes: body.length };
  }
  if (/#EXT-X-STREAM-INF/i.test(body) && depth < 2) {
    const variant = body
      .split(/\r?\n/)
      .map((l) => l.trim())
      .find((l) => l && !l.startsWith("#"));
    if (!variant) return { ok: false, reason: "hls-no-variant" };
    return checkHls(new URL(variant, url).toString(), config, depth + 1);
  }
  return { ok: false, reason: "hls-no-segments" };
}

/** Bir adresi tek seferde denetler; çalma listesiyse önce çözer. */
async function checkOnce(originalUrl, config) {
  let url = originalUrl;
  let resolvedFrom = null;

  if (isPlaylist(url)) {
    const fetched = await fetchText(url, config);
    if (!fetched.ok) return { ok: false, reason: `playlist:${fetched.reason}`, url };
    const first = firstStreamUrl(fetched.body);
    if (!first) return { ok: false, reason: "playlist-unresolved", url };
    resolvedFrom = url;
    url = first;
  }

  const result = isHls(url)
    ? await checkHls(url, config)
    : await probe(url, config);
  return { ...result, url, resolvedFrom };
}

/**
 * Bir istasyonu denetler.
 *
 * Başarısızlıkta [attempts] kez tekrar dener — çalma listesi çözümü dahil.
 * Canlı yayın sunucuları anlık olarak reddedebiliyor; tek denemeyle karar
 * vermek düzenli yanlış alarm üretir ve raporu güvenilmez kılar.
 */
async function checkStation(station, config) {
  let last = null;
  for (let attempt = 1; attempt <= config.attempts; attempt++) {
    last = await checkOnce(station.url, config);
    if (last.ok) return { station, attempt, ...last };
    if (attempt < config.attempts) await delay(config.retryDelayMs);
  }
  return { station, ...last, ok: false };
}

/**
 * Metin gövdesi indirir (çalma listesi, HLS manifesti).
 *
 * Hata sebebini yutmaz — `playlist-fetch-failed` gibi anlamsız bir rapor
 * yerine gerçek sebebi (`ECONNRESET`, `http-403`) yukarı taşır.
 */
function fetchText(url, config, redirectsLeft = 5) {
  return new Promise((resolve) => {
    let target;
    try {
      target = new URL(url);
    } catch {
      resolve({ ok: false, reason: "invalid-url" });
      return;
    }
    const client = target.protocol === "https:" ? https : http;
    const req = client.get(url, requestOptions(config), (res) => {
      const status = res.statusCode;

      if (status >= 300 && status < 400 && res.headers.location) {
        res.resume();
        if (redirectsLeft <= 0) {
          resolve({ ok: false, reason: "too-many-redirects" });
          return;
        }
        fetchText(new URL(res.headers.location, url).toString(), config, redirectsLeft - 1)
          .then(resolve);
        return;
      }
      if (status !== 200 && status !== 206) {
        res.resume();
        resolve({ ok: false, reason: `http-${status}` });
        return;
      }

      let data = "";
      res.on("data", (c) => {
        data += c;
        if (data.length > 256 * 1024) req.destroy();
      });
      res.on("end", () => resolve({ ok: true, body: data, contentType: res.headers["content-type"] || "" }));
      res.on("error", (e) => resolve({ ok: false, reason: e.code || e.message }));
    });
    req.on("timeout", () => {
      req.destroy();
      resolve({ ok: false, reason: "timeout" });
    });
    req.on("error", (e) => resolve({ ok: false, reason: e.code || e.message }));
  });
}

/**
 * Ortak istek seçenekleri.
 *
 * `insecureHTTPParser`: Icecast ve Shoutcast sunucularının çoğu standart dışı
 * başlık gönderiyor; Node'un katı ayrıştırıcısı bunları HPE_CR_EXPECTED ile
 * reddedip çalışan yayınları ölü gösteriyordu.
 */
function requestOptions(config, extraHeaders = {}) {
  return {
    headers: { "User-Agent": config.userAgent, ...extraHeaders },
    timeout: config.timeoutMs,
    insecureHTTPParser: true,
  };
}

/* ── Concurrency ────────────────────────────────────────────── */

async function mapWithLimit(items, limit, worker, onProgress) {
  const results = new Array(items.length);
  let next = 0;
  let finished = 0;

  async function run() {
    while (true) {
      const i = next++;
      if (i >= items.length) return;
      results[i] = await worker(items[i], i);
      onProgress?.(++finished, items.length, results[i]);
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, run));
  return results;
}

/* ── Main ───────────────────────────────────────────────────── */

async function main() {
  const config = parseArgs();

  if (!fs.existsSync(config.file)) {
    console.error(`❌ Dosya bulunamadı: ${config.file}`);
    process.exit(2);
  }

  const stations = JSON.parse(fs.readFileSync(config.file, "utf8"));
  console.log("╔══════════════════════════════════════════╗");
  console.log("║   📻 Radyola — Yayın Denetleyici          ║");
  console.log("╚══════════════════════════════════════════╝");
  console.log(`\n📄 ${path.relative(process.cwd(), config.file)} — ${stations.length} istasyon`);
  console.log(
    `⚙️  eşzamanlı: ${config.concurrency}, deneme: ${config.attempts}, zaman aşımı: ${
      config.timeoutMs / 1000
    }sn\n`
  );

  const started = process.hrtime.bigint();
  const results = await mapWithLimit(
    stations,
    config.concurrency,
    (s) => checkStation(s, config),
    (done, total, result) => {
      const mark = result.ok ? "✅" : "❌";
      const name = (result.station.title || "?").slice(0, 38).padEnd(38);
      const detail = result.ok ? "" : ` ${result.reason}`;
      console.log(`[${String(done).padStart(4)}/${total}] ${mark} ${name}${detail}`);
    }
  );
  const elapsedSec = Number(process.hrtime.bigint() - started) / 1e9;

  const dead = results.filter((r) => !r.ok);
  const alive = results.length - dead.length;

  console.log("\n═══════════════════════════════════════════");
  console.log(`✅ Çalışan: ${alive}/${results.length} (%${Math.round((100 * alive) / results.length)})`);
  console.log(`❌ Ölü:     ${dead.length}`);
  console.log(`⏱  Süre:    ${elapsedSec.toFixed(1)} sn`);
  console.log("═══════════════════════════════════════════");

  if (dead.length > 0) {
    console.log("\nÖlü yayınlar:\n");
    dead.forEach((r) => {
      console.log(`  ${r.station.title}`);
      console.log(`    sebep : ${r.reason}`);
      console.log(`    adres : ${r.station.url}`);
      if (r.resolvedFrom) console.log(`    çözülen: ${r.url}`);
      console.log();
    });
  }

  if (config.jsonOut) {
    const report = {
      file: path.relative(path.join(__dirname, ".."), config.file),
      total: results.length,
      alive,
      dead: dead.map((r) => ({
        title: r.station.title,
        url: r.station.url,
        resolvedUrl: r.resolvedFrom ? r.url : undefined,
        reason: r.reason,
      })),
    };
    fs.writeFileSync(config.jsonOut, JSON.stringify(report, null, 2) + "\n", "utf8");
    console.log(`📄 Rapor yazıldı: ${config.jsonOut}`);
  }

  if (config.failOnDead && dead.length > 0) process.exit(1);
}

main().catch((err) => {
  console.error("\n❌ Fatal error:", err.message);
  process.exit(2);
});

/*
 * Radyola service worker.
 *
 * Üç işi var, fazlası yok:
 *  1. Kabuk (HTML/CSS/JS/simgeler) çevrimdışı da açılır — ağ varken her
 *     istekte ağ öncelikli, yani dağıtımlar beklemeden ulaşır.
 *  2. İstasyon listeleri (data/*.json) önbellekten ANINDA döner, arka planda
 *     ETag ile koşullu GET atılır: veri değişmemişse sunucu 304 der ve
 *     1,2 MB'lık dizin yeniden inmez.
 *  3. Ses akışlarına ve diğer kökenlere hiç karışmaz — akış sonsuzdur,
 *     önbelleğe alınamaz; veri yedek adresleri tarayıcının olağan yolundan
 *     gider.
 *
 * VERSION dağıtımda commit kısa özetiyle damgalanır (web.yml). Her dağıtım
 * sw.js'in baytlarını değiştirir → tarayıcı yeni SW'yi kurar → install taze
 * kabuğu indirir, activate eski önbellekleri siler.
 */
const VERSION = "__BUILD__";
const SHELL_CACHE = `radyola-shell-${VERSION}`;
const DATA_CACHE = `radyola-data-${VERSION}`;

/* Adı sabit kabuk dosyaları. dist/js/hls-*.js içerik adresli (hash'li), adı
   önceden bilinemez — ilk kullanımında çalışma anında önbelleğe girer. */
const SHELL = [
  "./",
  "./index.html",
  "./radyola-player.css",
  "./radyola.svg",
  "./manifest.webmanifest",
  "./dist/js/index.js",
  "./fonts/InterVariable.woff2",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((k) => k !== SHELL_CACHE && k !== DATA_CACHE)
            .map((k) => caches.delete(k))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  const url = new URL(req.url);
  // Başka köken: akışlar, Google Fonts, mutlak veri adresleri — dokunma.
  if (url.origin !== self.location.origin) return;

  if (url.pathname.includes("/data/") && url.pathname.endsWith(".json")) {
    event.respondWith(dataResponse(event));
    return;
  }
  if (req.mode === "navigate") {
    event.respondWith(networkFirst(req, "./index.html"));
    return;
  }
  if (url.pathname.includes("/dist/js/hls-")) {
    // Adında hash var: içerik değişirse adı da değişir — süresiz önbellek.
    event.respondWith(cacheFirst(req));
    return;
  }
  event.respondWith(networkFirst(req));
});

/* Önbellekten hemen dön, arka planda koşullu GET ile tazele (SWR). */
async function dataResponse(event) {
  const cache = await caches.open(DATA_CACHE);
  const cached = await cache.match(event.request);
  const refresh = refreshData(cache, event.request, cached);
  if (cached) {
    // respondWith çözüldükten sonra da arka plan isteği yaşasın.
    event.waitUntil(refresh.catch(() => {}));
    return cached;
  }
  return refresh;
}

async function refreshData(cache, req, cached) {
  const headers = new Headers();
  const etag = cached && cached.headers.get("ETag");
  if (etag) headers.set("If-None-Match", etag);
  const res = await fetch(req.url, { headers });
  if (res.status === 304) return cached;
  if (res.ok) await cache.put(req, res.clone());
  return res;
}

/* Ağ öncelikli: taze sürüm her zaman kazanır, ağ yoksa önbellek devreye girer. */
async function networkFirst(req, fallbackPath) {
  const cache = await caches.open(SHELL_CACHE);
  try {
    const res = await fetch(req);
    if (res.ok) await cache.put(req, res.clone());
    return res;
  } catch (err) {
    const cached =
      (await cache.match(req)) ||
      (fallbackPath ? await cache.match(fallbackPath) : undefined);
    if (cached) return cached;
    throw err;
  }
}

async function cacheFirst(req) {
  const cache = await caches.open(SHELL_CACHE);
  const cached = await cache.match(req);
  if (cached) return cached;
  const res = await fetch(req);
  if (res.ok) await cache.put(req, res.clone());
  return res;
}

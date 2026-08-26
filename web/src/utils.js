/**
 * Saf yardımcılar — DOM'a dokunmazlar, birim testleri bunları hedefler
 * (bkz. ../test/). Tarayıcı durumu isteyen ikisi (isInsecure, playableUrl)
 * protokolü parametre olarak alır; uygulama içinde varsayılan location'dan
 * gelir, testte elle verilir.
 */

/**
 * ISO 3166-1 alpha-2 kodunu bayrak emoji'sine çevirir: "TR" → 🇹🇷
 * Her harf Unicode regional indicator karşılığına kaydırılır.
 */
export function flagOf(code) {
  if (!code || code.length !== 2 || !/^[a-z]{2}$/i.test(code)) return "";
  return [...code.toUpperCase()]
    .map((c) => String.fromCodePoint(0x1f1e6 + c.charCodeAt(0) - 65))
    .join("");
}

/** İstasyonu benzersiz kılan anahtar — iki liste arasında da çakışmaz. */
export function stationKey(s) {
  return `${s.name}|${s.url}`;
}

/**
 * Web sitesi bağlantısı olarak kullanılabilir mi?
 *
 * Discover verisi radio-browser.info'dan geliyor — herkesin düzenleyebildiği
 * bir veritabanı. `javascript:` gibi şemaları eleyip yalnız http(s) kabul
 * ediyoruz; adres ayrıca HTML'e gömülmez, DOM üzerinden atanır (bkz. _appendRows).
 */
export function safeWebsite(url) {
  return /^https?:\/\//i.test(url) ? url : "";
}

/** HTTPS sayfada engellenecek (yalnız http:// yayınlayan) akış mı? */
export function isInsecure(url, protocol = location.protocol) {
  return protocol === "https:" && url.startsWith("http://");
}

/**
 * Sayfa HTTPS'ten sunulurken http:// akışı tarayıcı engeller (karışık içerik) —
 * dizindeki 3.400 istasyonun ~1.900'ü böyle. Şemayı https'e çevirip deniyoruz:
 * sunucu TLS konuşuyorsa çalar; konuşmuyorsa hata yoluna düşer ve kullanıcıya
 * asıl neden söylenir (bkz. audio error dinleyicisi).
 */
export function playableUrl(url, protocol = location.protocol) {
  return isInsecure(url, protocol) ? "https://" + url.slice("http://".length) : url;
}

/** HLS akışı mı? Dizin verisi işaretliyor; kuratörlü listede uzantıya bakılır. */
export function isHlsStream(s) {
  return s.hls || /\.m3u8(\?|$)/i.test(s.url);
}

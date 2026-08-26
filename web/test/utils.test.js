import { describe, expect, it } from "vitest";

import {
  flagOf,
  isHlsStream,
  isInsecure,
  playableUrl,
  safeWebsite,
  stationKey,
} from "../src/utils.js";

describe("flagOf", () => {
  it("ISO kodunu bayrak emojisine çevirir", () => {
    expect(flagOf("TR")).toBe("🇹🇷");
    expect(flagOf("de")).toBe("🇩🇪");
  });

  it("geçersiz girdide boş döner", () => {
    expect(flagOf("")).toBe("");
    expect(flagOf(null)).toBe("");
    expect(flagOf(undefined)).toBe("");
    expect(flagOf("T")).toBe("");
    expect(flagOf("TUR")).toBe("");
    expect(flagOf("1A")).toBe("");
  });
});

describe("stationKey", () => {
  it("ad ve adresi birleştirir — iki liste arasında da benzersiz", () => {
    expect(stationKey({ name: "Açık Radyo", url: "https://x/stream" }))
      .toBe("Açık Radyo|https://x/stream");
  });
});

describe("safeWebsite", () => {
  it("yalnız http(s) kabul eder", () => {
    expect(safeWebsite("https://example.com")).toBe("https://example.com");
    expect(safeWebsite("http://example.com")).toBe("http://example.com");
    expect(safeWebsite("HTTPS://EXAMPLE.COM")).toBe("HTTPS://EXAMPLE.COM");
  });

  it("betik ve diğer şemaları eler (veri topluluk düzenlemesine açık)", () => {
    expect(safeWebsite("javascript:alert(1)")).toBe("");
    expect(safeWebsite("ftp://example.com")).toBe("");
    expect(safeWebsite("//example.com")).toBe("");
    expect(safeWebsite("")).toBe("");
  });
});

describe("isInsecure / playableUrl (karışık içerik)", () => {
  it("HTTPS sayfada http:// akışı güvensizdir ve https'e yükseltilir", () => {
    expect(isInsecure("http://s/y", "https:")).toBe(true);
    expect(playableUrl("http://s/y", "https:")).toBe("https://s/y");
  });

  it("HTTP sayfada (yerel geliştirme) dokunulmaz", () => {
    expect(isInsecure("http://s/y", "http:")).toBe(false);
    expect(playableUrl("http://s/y", "http:")).toBe("http://s/y");
  });

  it("https akışa hiçbir koşulda dokunulmaz", () => {
    expect(isInsecure("https://s/y", "https:")).toBe(false);
    expect(playableUrl("https://s/y", "https:")).toBe("https://s/y");
  });
});

describe("isHlsStream", () => {
  it("dizin bayrağını tanır", () => {
    expect(isHlsStream({ hls: true, url: "https://s/x" })).toBe(true);
  });

  it("uzantıdan tanır — sorgu dizisiyle de", () => {
    expect(isHlsStream({ url: "https://s/master.m3u8" })).toBe(true);
    expect(isHlsStream({ url: "https://s/master.M3U8?token=1" })).toBe(true);
  });

  it("düz akışları HLS saymaz", () => {
    expect(isHlsStream({ url: "https://s/stream.mp3" })).toBe(false);
    expect(isHlsStream({ url: "https://s/m3u8/degil" })).toBe(false);
  });
});

import { describe, expect, it } from "vitest";

import { LANGS, pickLang } from "../src/i18n.js";

describe("pickLang", () => {
  it("öznitelik tarayıcı dilini geçersiz kılar", () => {
    expect(pickLang("tr", "en-US")).toBe("tr");
    expect(pickLang("en", "tr-TR")).toBe("en");
    expect(pickLang("en-GB", "tr-TR")).toBe("en");
  });

  it("öznitelik yoksa tarayıcı dili seçer", () => {
    expect(pickLang(null, "tr-TR")).toBe("tr");
    expect(pickLang("", "tr")).toBe("tr");
    expect(pickLang(null, "en-US")).toBe("en");
  });

  it("bilinmeyen değerlerde İngilizce'ye düşer", () => {
    expect(pickLang("fr", "de-DE")).toBe("en");
    expect(pickLang(null, null)).toBe("en");
    expect(pickLang(null, undefined)).toBe("en");
  });
});

describe("LANGS", () => {
  it("iki dilin anahtar kümeleri bire bir aynı", () => {
    expect(Object.keys(LANGS.tr).sort()).toEqual(Object.keys(LANGS.en).sort());
  });

  it("hiçbir metin boş değil", () => {
    for (const lang of Object.values(LANGS)) {
      for (const [key, value] of Object.entries(lang)) {
        if (typeof value === "string") {
          expect(value.length, key).toBeGreaterThan(0);
        }
      }
    }
  });

  it("ad taşıyan etiketler adı içerir (sözcük dizilişi dile göre değişir)", () => {
    expect(LANGS.en.playLabel("X FM")).toBe("Play X FM");
    expect(LANGS.tr.playLabel("X FM")).toBe("X FM çal");
    expect(LANGS.en.favLabel("X FM")).toContain("X FM");
    expect(LANGS.tr.favLabel("X FM")).toContain("X FM");
  });
});

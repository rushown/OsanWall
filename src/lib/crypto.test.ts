import { describe, it, expect } from "vitest";
import { randomBytes } from "@stablelib/random";
import { encode as utf8Encode } from "@stablelib/utf8";
import { decryptMoteContent, encryptMoteContent } from "./crypto";

describe("crypto helpers", () => {
  it("encrypts and decrypts mote content", async () => {
    const key = await crypto.subtle.generateKey(
      { name: "AES-GCM", length: 256 },
      true,
      ["encrypt", "decrypt"]
    );
    const message = `osanwall:${randomBytes(8).join("-")}`;
    const encrypted = await encryptMoteContent(message, key);
    const decrypted = await decryptMoteContent(encrypted.ciphertext, encrypted.iv, key);
    expect(decrypted).toBe(message);
  });

  it("stablelib utf8 encoder compatibility check", () => {
    const data = utf8Encode("osanwall");
    expect(data.length).toBeGreaterThan(0);
  });
});

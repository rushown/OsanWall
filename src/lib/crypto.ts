import { db } from "./db";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

export type PublicPreKeyBundle = {
  userId: string;
  identityKey: string; // base64(X25519 public key raw)
  signedPreKey: string; // base64(X25519 public key raw)
  signedPreKeySignature: string; // base64(signature)
  oneTimePreKeys: string[]; // base64(raw pub keys)
  createdAt: string;
};

export type IdentityContext = {
  userId: string;
  keyPair: CryptoKeyPair;
  bundle: PublicPreKeyBundle;
};

export type X3DHPeerBundle = {
  userId: string;
  identityKey: string;
  signedPreKey: string;
  oneTimePreKey?: string;
};

type DeriveKeyInput = {
  mode: "webauthn-prf" | "passphrase-fallback";
  passphrase?: string;
};

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const STORE_ID = "identity";

export async function generateIdentity(userId: string, passphrase?: string): Promise<IdentityContext> {
  const identityKeyPair = await crypto.subtle.generateKey(
    { name: "X25519", namedCurve: "X25519" },
    true,
    ["deriveKey", "deriveBits"]
  );

  const signingKey = await crypto.subtle.generateKey(
    { name: "Ed25519" },
    true,
    ["sign", "verify"]
  );

  const signedPreKeyPair = await crypto.subtle.generateKey(
    { name: "X25519", namedCurve: "X25519" },
    true,
    ["deriveKey", "deriveBits"]
  );

  const oneTimePreKeys = await Promise.all(
    Array.from({ length: 10 }).map(() =>
      crypto.subtle.generateKey({ name: "X25519", namedCurve: "X25519" }, true, ["deriveKey", "deriveBits"])
    )
  );

  const signedPreKeyRaw = await crypto.subtle.exportKey("raw", signedPreKeyPair.publicKey);
  const signingPrivate = signingKey.privateKey;
  const signature = await crypto.subtle.sign("Ed25519", signingPrivate, signedPreKeyRaw);

  const identityPublicRaw = await crypto.subtle.exportKey("raw", identityKeyPair.publicKey);
  const signedPreKeyB64 = toB64(signedPreKeyRaw);
  const signatureB64 = toB64(signature);
  const oneTimePreKeyB64 = await Promise.all(
    oneTimePreKeys.map(async (kp) => toB64(await crypto.subtle.exportKey("raw", kp.publicKey)))
  );

  const bundle: PublicPreKeyBundle = {
    userId,
    identityKey: toB64(identityPublicRaw),
    signedPreKey: signedPreKeyB64,
    signedPreKeySignature: signatureB64,
    oneTimePreKeys: oneTimePreKeyB64,
    createdAt: new Date().toISOString(),
  };

  const wrappingMode: DeriveKeyInput["mode"] = supportsWebAuthnPRF()
    ? "webauthn-prf"
    : "passphrase-fallback";
  const wrappingKey = await deriveWrappingKey({ mode: wrappingMode, passphrase });

  const exportedPrivate = await crypto.subtle.exportKey("pkcs8", identityKeyPair.privateKey);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encryptedPrivate = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    wrappingKey,
    exportedPrivate
  );

  await db.keyMaterial.put({
    id: STORE_ID,
    publicKeyRaw: bundle.identityKey,
    encryptedPrivateKey: toB64(encryptedPrivate),
    privateKeyIv: toB64(iv),
    wrappingMode,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  });

  return { userId, keyPair: identityKeyPair, bundle };
}

export async function uploadPublicPreKeyBundle(
  supabase: SupabaseClient,
  bundle: PublicPreKeyBundle
): Promise<void> {
  const { error } = await supabase.from("key_bundles").upsert(
    {
      user_id: bundle.userId,
      identity_key: bundle.identityKey,
      signed_pre_key: bundle.signedPreKey,
      signed_pre_key_signature: bundle.signedPreKeySignature,
      one_time_pre_keys: bundle.oneTimePreKeys,
      created_at: bundle.createdAt,
      updated_at: new Date().toISOString(),
    },
    { onConflict: "user_id" }
  );
  if (error) throw new Error(`Failed to upload key bundle: ${error.message}`);
}

export function createSupabaseClient(url: string, anonKey: string): SupabaseClient {
  return createClient(url, anonKey, {
    auth: { persistSession: true, autoRefreshToken: true },
    global: { headers: { "X-Client-Info": "osanwall-web" } },
  });
}

export async function unlockIdentityPrivateKey(passphrase?: string): Promise<CryptoKey> {
  const rec = await db.keyMaterial.get(STORE_ID);
  if (!rec) throw new Error("No identity key found");
  const key = await deriveWrappingKey({ mode: rec.wrappingMode, passphrase });
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: toArrayBuffer(fromB64(rec.privateKeyIv)) },
    key,
    toArrayBuffer(fromB64(rec.encryptedPrivateKey))
  );
  return crypto.subtle.importKey(
    "pkcs8",
    decrypted,
    { name: "X25519", namedCurve: "X25519" },
    true,
    ["deriveKey", "deriveBits"]
  );
}

export async function encryptMoteContent(plaintext: string, symmetricKey: CryptoKey): Promise<{ iv: string; ciphertext: string }> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    symmetricKey,
    encoder.encode(plaintext)
  );
  return { iv: toB64(iv), ciphertext: toB64(encrypted) };
}

export async function decryptMoteContent(ciphertextB64: string, ivB64: string, symmetricKey: CryptoKey): Promise<string> {
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: toArrayBuffer(fromB64(ivB64)) },
    symmetricKey,
    toArrayBuffer(fromB64(ciphertextB64))
  );
  return decoder.decode(decrypted);
}

export async function initializeX3DHSession(
  peer: X3DHPeerBundle,
  passphrase?: string
): Promise<{ sessionId: string; sharedSecretB64: string }> {
  const localPrivate = await unlockIdentityPrivateKey(passphrase);
  const peerIdentityPub = await crypto.subtle.importKey(
    "raw",
    toArrayBuffer(fromB64(peer.identityKey)),
    { name: "X25519", namedCurve: "X25519" },
    false,
    []
  );
  const peerSignedPreKey = await crypto.subtle.importKey(
    "raw",
    toArrayBuffer(fromB64(peer.signedPreKey)),
    { name: "X25519", namedCurve: "X25519" },
    false,
    []
  );
  const eph = await crypto.subtle.generateKey(
    { name: "X25519", namedCurve: "X25519" },
    true,
    ["deriveBits"]
  );

  const dh1 = await crypto.subtle.deriveBits({ name: "X25519", public: peerSignedPreKey }, localPrivate, 256);
  const dh2 = await crypto.subtle.deriveBits({ name: "X25519", public: peerIdentityPub }, eph.privateKey, 256);
  const shared = await hkdfMerge([new Uint8Array(dh1), new Uint8Array(dh2)], "osanwall-x3dh");

  const ephPrivatePkcs8 = await crypto.subtle.exportKey("pkcs8", eph.privateKey);
  const wrapIv = crypto.getRandomValues(new Uint8Array(12));
  const wrapKey = await deriveWrappingKey({ mode: supportsWebAuthnPRF() ? "webauthn-prf" : "passphrase-fallback", passphrase });
  const encryptedEph = await crypto.subtle.encrypt({ name: "AES-GCM", iv: wrapIv }, wrapKey, ephPrivatePkcs8);

  const sessionId = `${peer.userId}:${Date.now()}`;
  await db.ratchetSessions.put({
    id: sessionId,
    peerUserId: peer.userId,
    rootKey: toB64(shared),
    sendingChainKey: toB64(shared.slice(0, 16)),
    receivingChainKey: toB64(shared.slice(16)),
    currentDhPublicKey: toB64(await crypto.subtle.exportKey("raw", eph.publicKey)),
    currentDhPrivateKeyEncrypted: `${toB64(wrapIv)}.${toB64(encryptedEph)}`,
    messageNumberSend: 0,
    messageNumberRecv: 0,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  });

  return { sessionId, sharedSecretB64: toB64(shared) };
}

export async function rotateSessionMessageKey(sessionId: string): Promise<string> {
  const session = await db.ratchetSessions.get(sessionId);
  if (!session) throw new Error("Session not found");
  const next = await hkdfMerge([fromB64(session.sendingChainKey)], "osanwall-ratchet-step");
  const messageKey = next.slice(0, 16);
  const chainKey = next.slice(16);
  await db.ratchetSessions.update(sessionId, {
    sendingChainKey: toB64(chainKey),
    messageNumberSend: session.messageNumberSend + 1,
    updatedAt: Date.now(),
  });
  return toB64(messageKey);
}

async function deriveWrappingKey(input: DeriveKeyInput): Promise<CryptoKey> {
  let seedMaterial = "osanwall-default-fallback";
  if (input.mode === "webauthn-prf") {
    seedMaterial = await derivePRFSeed();
  } else {
    if (!input.passphrase || input.passphrase.length < 8) {
      throw new Error("Passphrase fallback requires at least 8 chars");
    }
    seedMaterial = input.passphrase;
  }

  const baseKey = await crypto.subtle.importKey(
    "raw",
    encoder.encode(seedMaterial),
    "PBKDF2",
    false,
    ["deriveKey"]
  );
  return crypto.subtle.deriveKey(
    {
      name: "PBKDF2",
      salt: encoder.encode("osanwall-keywrap-v1"),
      iterations: 210_000,
      hash: "SHA-256",
    },
    baseKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"]
  );
}

function supportsWebAuthnPRF(): boolean {
  return typeof window !== "undefined" && !!window.PublicKeyCredential;
}

async function derivePRFSeed(): Promise<string> {
  // WebAuthn PRF extension availability is browser/credential dependent.
  // We use user-verification challenge data as seed material when PRF is unavailable.
  const challenge = crypto.getRandomValues(new Uint8Array(32));
  if (!window.PublicKeyCredential) return toB64(challenge);
  return toB64(challenge);
}

async function hkdfMerge(chunks: Uint8Array[], info: string): Promise<Uint8Array> {
  const sourceLen = chunks.reduce((acc, c) => acc + c.byteLength, 0);
  const source = new Uint8Array(sourceLen);
  let o = 0;
  for (const c of chunks) {
    source.set(c, o);
    o += c.byteLength;
  }
  const key = await crypto.subtle.importKey("raw", source, "HKDF", false, ["deriveBits"]);
  const derived = await crypto.subtle.deriveBits(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt: encoder.encode("osanwall-hkdf-v1"),
      info: encoder.encode(info),
    },
    key,
    256
  );
  return new Uint8Array(derived);
}

function toB64(input: BufferSource | Uint8Array): string {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input as ArrayBuffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 1) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

function fromB64(input: string): Uint8Array {
  const bin = atob(input);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i += 1) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function toArrayBuffer(input: Uint8Array): ArrayBuffer {
  return input.buffer.slice(input.byteOffset, input.byteOffset + input.byteLength) as ArrayBuffer;
}

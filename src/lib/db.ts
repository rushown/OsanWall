import Dexie, { Table } from "dexie";

export type MoteRecord = {
  id: string;
  authorId: string;
  encryptedPayload: string;
  iv: string;
  keyVersion: number;
  x: number;
  y: number;
  lamport: number;
  clientId: string;
  status: "pending" | "synced" | "conflicted";
  createdAt: number;
  updatedAt: number;
  expiresAt?: number;
};

export type KeyMaterialRecord = {
  id: "identity";
  publicKeyRaw: string;
  encryptedPrivateKey: string;
  privateKeyIv: string;
  wrappingMode: "webauthn-prf" | "passphrase-fallback";
  createdAt: number;
  updatedAt: number;
};

export type SyncQueueRecord = {
  id?: number;
  opType: "create_mote" | "update_mote" | "react_mote" | "delete_mote";
  entityId: string;
  payload: string;
  retryCount: number;
  nextRetryAt: number;
  createdAt: number;
  lastError?: string;
};

export type AppSettingsRecord = {
  key: string;
  value: string;
  updatedAt: number;
};

export type RatchetSessionRecord = {
  id: string;
  peerUserId: string;
  rootKey: string;
  sendingChainKey: string;
  receivingChainKey: string;
  currentDhPublicKey: string;
  currentDhPrivateKeyEncrypted: string;
  messageNumberSend: number;
  messageNumberRecv: number;
  createdAt: number;
  updatedAt: number;
};

export type KeyBundleCacheRecord = {
  userId: string;
  identityKey: string;
  signedPreKey: string;
  signature: string;
  oneTimePreKey: string;
  fetchedAt: number;
};

export class OsanwallDB extends Dexie {
  motes!: Table<MoteRecord, string>;
  syncQueue!: Table<SyncQueueRecord, number>;
  keyMaterial!: Table<KeyMaterialRecord, "identity">;
  appSettings!: Table<AppSettingsRecord, string>;
  ratchetSessions!: Table<RatchetSessionRecord, string>;
  keyBundleCache!: Table<KeyBundleCacheRecord, string>;

  constructor() {
    super("osanwall");
    this.version(1).stores({
      motes: "id, authorId, status, updatedAt, expiresAt, lamport, [x+y]",
      syncQueue: "++id, opType, entityId, nextRetryAt, retryCount",
      keyMaterial: "id, updatedAt",
      appSettings: "key, updatedAt",
    });

    this.version(2).stores({
      motes: "id, authorId, status, updatedAt, expiresAt, lamport, [x+y], clientId",
      syncQueue: "++id, opType, entityId, nextRetryAt, retryCount",
      keyMaterial: "id, updatedAt",
      appSettings: "key, updatedAt",
    }).upgrade(async (tx) => {
      await tx.table("motes").toCollection().modify((mote: Partial<MoteRecord>) => {
        if (!mote.clientId) mote.clientId = "legacy";
      });
    });

    this.version(3).stores({
      motes: "id, authorId, status, updatedAt, expiresAt, lamport, [x+y], clientId",
      syncQueue: "++id, opType, entityId, nextRetryAt, retryCount",
      keyMaterial: "id, updatedAt",
      appSettings: "key, updatedAt",
      ratchetSessions: "id, peerUserId, updatedAt",
      keyBundleCache: "userId, fetchedAt",
    });
  }
}

export const db = new OsanwallDB();

export async function enforceStorageBudget(maxUsageRatio = 0.8): Promise<{ evicted: number; ratio: number }> {
  if (!("storage" in navigator) || !navigator.storage.estimate) {
    return { evicted: 0, ratio: 0 };
  }
  const estimate = await navigator.storage.estimate();
  const usage = estimate.usage ?? 0;
  const quota = estimate.quota ?? 1;
  const ratio = usage / quota;
  if (ratio <= maxUsageRatio) return { evicted: 0, ratio };

  const now = Date.now();
  const stale = await db.motes.where("expiresAt").below(now).toArray();
  let evicted = 0;
  for (const mote of stale) {
    await db.motes.delete(mote.id);
    evicted += 1;
  }
  return { evicted, ratio };
}

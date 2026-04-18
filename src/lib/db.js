import Dexie from "dexie";
export class OsanwallDB extends Dexie {
    motes;
    syncQueue;
    keyMaterial;
    appSettings;
    ratchetSessions;
    keyBundleCache;
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
            await tx.table("motes").toCollection().modify((mote) => {
                if (!mote.clientId)
                    mote.clientId = "legacy";
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
export async function enforceStorageBudget(maxUsageRatio = 0.8) {
    if (!("storage" in navigator) || !navigator.storage.estimate) {
        return { evicted: 0, ratio: 0 };
    }
    const estimate = await navigator.storage.estimate();
    const usage = estimate.usage ?? 0;
    const quota = estimate.quota ?? 1;
    const ratio = usage / quota;
    if (ratio <= maxUsageRatio)
        return { evicted: 0, ratio };
    const now = Date.now();
    const stale = await db.motes.where("expiresAt").below(now).toArray();
    let evicted = 0;
    for (const mote of stale) {
        await db.motes.delete(mote.id);
        evicted += 1;
    }
    return { evicted, ratio };
}

import { db } from "./db";
export class SyncEngine {
    transport;
    clientId;
    constructor(transport, clientId) {
        this.transport = transport;
        this.clientId = clientId;
    }
    async enqueue(item) {
        return db.syncQueue.add({
            ...item,
            retryCount: 0,
            nextRetryAt: Date.now(),
            createdAt: Date.now(),
        });
    }
    async processQueue() {
        const now = Date.now();
        const ready = await db.syncQueue.where("nextRetryAt").belowOrEqual(now).toArray();
        let sent = 0;
        let failed = 0;
        for (const item of ready) {
            try {
                await this.transport.push(item);
                if (item.id)
                    await db.syncQueue.delete(item.id);
                sent += 1;
            }
            catch (err) {
                failed += 1;
                const retryCount = item.retryCount + 1;
                const backoffMs = Math.min(60_000, 1_000 * 2 ** retryCount);
                if (item.id) {
                    await db.syncQueue.update(item.id, {
                        retryCount,
                        nextRetryAt: Date.now() + backoffMs,
                        lastError: err instanceof Error ? err.message : "unknown error",
                    });
                }
            }
        }
        return { sent, failed };
    }
    async mergeRemoteState(remoteMotes) {
        let inserted = 0;
        let updated = 0;
        let ignored = 0;
        for (const remote of remoteMotes) {
            const local = await db.motes.get(remote.id);
            if (!local) {
                await db.motes.put({
                    ...remote,
                    authorId: "unknown",
                    keyVersion: 1,
                    createdAt: remote.updatedAt,
                    expiresAt: undefined,
                });
                inserted += 1;
                continue;
            }
            const winner = compareLww(local, remote);
            if (winner === "remote") {
                await db.motes.update(remote.id, {
                    encryptedPayload: remote.encryptedPayload,
                    iv: remote.iv,
                    x: remote.x,
                    y: remote.y,
                    lamport: remote.lamport,
                    clientId: remote.clientId,
                    status: remote.status,
                    updatedAt: remote.updatedAt,
                });
                updated += 1;
            }
            else {
                ignored += 1;
            }
        }
        return { inserted, updated, ignored };
    }
    async sync(vectorClock) {
        await this.processQueue();
        const diff = await this.transport.pullDiff(vectorClock);
        await this.mergeRemoteState(diff.motes);
        return diff.vector;
    }
    nextLamport(current) {
        return Math.max(current, Date.now()) + 1;
    }
    getClientId() {
        return this.clientId;
    }
}
function compareLww(local, remote) {
    if (remote.lamport > local.lamport)
        return "remote";
    if (remote.lamport < local.lamport)
        return "local";
    const localHash = stableIdHash(local.clientId);
    const remoteHash = stableIdHash(remote.clientId);
    return remoteHash > localHash ? "remote" : "local";
}
function stableIdHash(input) {
    let hash = 2166136261;
    for (let i = 0; i < input.length; i += 1) {
        hash ^= input.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0).toString(16).padStart(8, "0");
}

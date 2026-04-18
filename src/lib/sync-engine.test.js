import { beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "./db";
import { SyncEngine } from "./sync-engine";
describe("SyncEngine", () => {
    beforeEach(async () => {
        await db.delete();
        await db.open();
    });
    it("retries failed queue items with backoff", async () => {
        const push = vi.fn()
            .mockRejectedValueOnce(new Error("network down"))
            .mockResolvedValueOnce(undefined);
        const engine = new SyncEngine({
            push,
            pullDiff: async () => ({ motes: [], vector: {} }),
        }, "client-a");
        await engine.enqueue({
            opType: "create_mote",
            entityId: "m1",
            payload: "{}",
        });
        const first = await engine.processQueue();
        expect(first.failed).toBe(1);
        const item = await db.syncQueue.toCollection().first();
        expect(item?.retryCount).toBe(1);
        if (item?.id) {
            await db.syncQueue.update(item.id, { nextRetryAt: Date.now() - 1 });
        }
        const second = await engine.processQueue();
        expect(second.sent).toBe(1);
        expect(await db.syncQueue.count()).toBe(0);
    });
    it("uses deterministic LWW merge", async () => {
        const engine = new SyncEngine({
            push: async () => { },
            pullDiff: async () => ({ motes: [], vector: {} }),
        }, "client-a");
        await db.motes.put({
            id: "mote-1",
            authorId: "u1",
            encryptedPayload: "local",
            iv: "iv",
            keyVersion: 1,
            x: 0,
            y: 0,
            lamport: 10,
            clientId: "a",
            status: "synced",
            createdAt: Date.now(),
            updatedAt: Date.now(),
        });
        const result = await engine.mergeRemoteState([
            {
                id: "mote-1",
                encryptedPayload: "remote",
                iv: "iv",
                x: 1,
                y: 1,
                lamport: 11,
                clientId: "b",
                status: "synced",
                updatedAt: Date.now(),
            },
        ]);
        expect(result.updated).toBe(1);
        const mote = await db.motes.get("mote-1");
        expect(mote?.encryptedPayload).toBe("remote");
    });
});

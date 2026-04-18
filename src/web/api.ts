import type { SyncQueueRecord } from "../lib/db";
import type { RemoteMote } from "../lib/sync-engine";

const API_BASE = (import.meta.env.VITE_OSANWALL_API_BASE as string | undefined) ?? "http://localhost:8787";

export async function pushQueueItem(item: SyncQueueRecord, jwt: string): Promise<void> {
  const payload = JSON.parse(item.payload);
  if (item.opType === "create_mote") {
    const res = await fetch(`${API_BASE}/api/motes`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${jwt}`,
      },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(`create_mote failed: ${res.status}`);
    return;
  }
  if (item.opType === "react_mote") {
    const res = await fetch(`${API_BASE}/api/motes/interact`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${jwt}`,
      },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(`react_mote failed: ${res.status}`);
  }
}

export async function fetchViewportMotes(centerX: number, centerY: number): Promise<RemoteMote[]> {
  const res = await fetch(`${API_BASE}/api/motes?x=${centerX}&y=${centerY}&w=1200&h=900&limit=200`);
  if (!res.ok) return [];
  const body = await res.json();
  return (body.motes ?? []).map((m: any) => ({
    id: m.id,
    encryptedPayload: String(m.text ?? ""),
    iv: "",
    x: Number(m.x ?? 0),
    y: Number(m.y ?? 0),
    lamport: Number(m.updatedAt ?? Date.now()),
    clientId: String(m.authorId ?? "remote"),
    status: "synced",
    updatedAt: Number(m.updatedAt ?? Date.now()),
  }));
}

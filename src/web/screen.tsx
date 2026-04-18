import { Canvas } from "@react-three/fiber";
import { motion } from "framer-motion";
import { useEffect, useMemo, useRef, useState } from "react";
import { db, enforceStorageBudget, type MoteRecord } from "../lib/db";
import { SyncEngine } from "../lib/sync-engine";
import { fetchViewportMotes, pushQueueItem } from "./api";
import { loadSettings, saveSettings } from "./settings";
import { useUiState } from "./state";

const SESSION_USER_ID = "user_demo_01";
const SESSION_JWT =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyX2RlbW9fMDEiLCJleHAiOjQxMDI0NDQ4MDB9.y0N2mDMqYO5AAq4hlSxHDhI4O8XK6hVtfI5A4kRk2oA";

export function OsanwallCanvasApp() {
  const { viewport, setViewport, isSyncing, setSyncing } = useUiState();
  const [motes, setMotes] = useState<MoteRecord[]>([]);
  const [draft, setDraft] = useState("");
  const [storageMessage, setStorageMessage] = useState("");
  const [settings, setSettings] = useState(loadSettings);
  const dragRef = useRef<{ x: number; y: number } | null>(null);

  const syncEngine = useMemo(
    () =>
      new SyncEngine(
        {
          push: (item) => pushQueueItem(item, SESSION_JWT),
          pullDiff: async () => ({
            motes: await fetchViewportMotes(viewport.x, viewport.y),
            vector: {},
          }),
        },
        SESSION_USER_ID
      ),
    [viewport.x, viewport.y]
  );

  useEffect(() => {
    saveSettings(settings);
  }, [settings]);

  useEffect(() => {
    let mounted = true;
    db.motes.orderBy("updatedAt").reverse().limit(200).toArray().then((rows) => {
      if (mounted) setMotes(rows);
    });
    db.appSettings.get("draft_mote").then((r) => {
      if (r?.value) setDraft(r.value);
    });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    const id = setInterval(async () => {
      setSyncing(true);
      await syncEngine.processQueue();
      const remote = await fetchViewportMotes(viewport.x, viewport.y);
      await syncEngine.mergeRemoteState(remote);
      const next = await db.motes.orderBy("updatedAt").reverse().limit(200).toArray();
      setMotes(next);
      setSyncing(false);
    }, 2500);
    return () => clearInterval(id);
  }, [syncEngine, setSyncing, viewport.x, viewport.y]);

  useEffect(() => {
    const id = setTimeout(async () => {
      await db.appSettings.put({
        key: "draft_mote",
        value: draft,
        updatedAt: Date.now(),
      });
    }, 2000);
    return () => clearTimeout(id);
  }, [draft]);

  useEffect(() => {
    const id = setInterval(async () => {
      const { evicted, ratio } = await enforceStorageBudget();
      if (evicted > 0) {
        setStorageMessage(`Storage saver evicted ${evicted} stale motes (usage ${(ratio * 100).toFixed(1)}%).`);
      }
    }, 15000);
    return () => clearInterval(id);
  }, []);

  const createMote = async () => {
    const text = draft.trim();
    if (!text) return;
    const id = crypto.randomUUID();
    const now = Date.now();
    const mote: MoteRecord = {
      id,
      authorId: SESSION_USER_ID,
      encryptedPayload: text,
      iv: "",
      keyVersion: 1,
      x: viewport.x,
      y: viewport.y,
      lamport: now,
      clientId: SESSION_USER_ID,
      status: "pending",
      createdAt: now,
      updatedAt: now,
      expiresAt: now + 1000 * 60 * 60 * 24 * 14,
    };
    await db.motes.put(mote);
    await syncEngine.enqueue({
      opType: "create_mote",
      entityId: id,
      payload: JSON.stringify({
        id,
        authorId: SESSION_USER_ID,
        text,
        x: mote.x,
        y: mote.y,
        vibe: "default",
      }),
    });
    setDraft("");
    setMotes(await db.motes.orderBy("updatedAt").reverse().limit(200).toArray());
  };

  return (
    <div className="appRoot">
      <div
        className="canvasWrap"
        onWheel={(e) => {
          const scale = Math.min(2.5, Math.max(0.45, viewport.scale + (e.deltaY > 0 ? -0.05 : 0.05)));
          setViewport({ scale });
        }}
        onPointerDown={(e) => {
          dragRef.current = { x: e.clientX, y: e.clientY };
        }}
        onPointerMove={(e) => {
          if (!dragRef.current) return;
          const dx = (e.clientX - dragRef.current.x) / viewport.scale;
          const dy = (e.clientY - dragRef.current.y) / viewport.scale;
          dragRef.current = { x: e.clientX, y: e.clientY };
          setViewport({ x: viewport.x - dx, y: viewport.y + dy });
        }}
        onPointerUp={() => {
          dragRef.current = null;
        }}
      >
        <Canvas camera={{ position: [0, 0, 10], fov: 55 }}>
          <ambientLight intensity={0.5} />
          <pointLight position={[8, 10, 10]} intensity={1.1} />
          <mesh position={[0, 0, -2]}>
            <planeGeometry args={[40, 30, 10, 10]} />
            <meshStandardMaterial color="#101522" wireframe />
          </mesh>
        </Canvas>
        <div className="overlay">
          {motes.map((m) => (
            <motion.div
              key={m.id}
              className={`mote ${m.status}`}
              initial={{ opacity: 0, scale: 0.92 }}
              animate={{ opacity: 1, scale: 1 }}
              style={{
                left: `${(m.x - viewport.x) * viewport.scale + window.innerWidth / 2}px`,
                top: `${(viewport.y - m.y) * viewport.scale + window.innerHeight / 2}px`,
              }}
            >
              <div className="moteText">{m.encryptedPayload}</div>
              <div className="moteMeta">{m.status}</div>
            </motion.div>
          ))}
        </div>
      </div>

      <div className="composer">
        <div className="headline">Osanwall Canvas</div>
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Drop a new mote at current viewport..."
        />
        <button onClick={createMote}>Drop Mote</button>
        <label className="toggleRow">
          <input
            type="checkbox"
            checked={settings.muted}
            onChange={(e) => setSettings({ ...settings, muted: e.target.checked })}
          />
          Mute notifications
        </label>
        <div className="metaLine">Sync: {isSyncing ? "syncing" : "idle"} · viewport ({viewport.x.toFixed(0)}, {viewport.y.toFixed(0)})</div>
        {storageMessage ? <div className="storageBanner">{storageMessage}</div> : null}
      </div>
    </div>
  );
}

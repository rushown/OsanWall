import { create } from "zustand";
const initialViewport = (() => {
    const raw = sessionStorage.getItem("osanwall.viewport");
    if (!raw)
        return { x: 0, y: 0, scale: 1 };
    try {
        const parsed = JSON.parse(raw);
        return {
            x: Number.isFinite(parsed.x) ? parsed.x : 0,
            y: Number.isFinite(parsed.y) ? parsed.y : 0,
            scale: Number.isFinite(parsed.scale) ? parsed.scale : 1,
        };
    }
    catch {
        return { x: 0, y: 0, scale: 1 };
    }
})();
export const useUiState = create((set, get) => ({
    viewport: initialViewport,
    setViewport: (next) => {
        const merged = { ...get().viewport, ...next };
        sessionStorage.setItem("osanwall.viewport", JSON.stringify(merged));
        set({ viewport: merged });
    },
    isSyncing: false,
    setSyncing: (v) => set({ isSyncing: v }),
}));

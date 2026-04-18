import { create } from "zustand";

type Viewport = {
  x: number;
  y: number;
  scale: number;
};

type UiState = {
  viewport: Viewport;
  setViewport: (next: Partial<Viewport>) => void;
  isSyncing: boolean;
  setSyncing: (v: boolean) => void;
};

const initialViewport = (() => {
  const raw = sessionStorage.getItem("osanwall.viewport");
  if (!raw) return { x: 0, y: 0, scale: 1 };
  try {
    const parsed = JSON.parse(raw) as Viewport;
    return {
      x: Number.isFinite(parsed.x) ? parsed.x : 0,
      y: Number.isFinite(parsed.y) ? parsed.y : 0,
      scale: Number.isFinite(parsed.scale) ? parsed.scale : 1,
    };
  } catch {
    return { x: 0, y: 0, scale: 1 };
  }
})();

export const useUiState = create<UiState>((set, get) => ({
  viewport: initialViewport,
  setViewport: (next) => {
    const merged = { ...get().viewport, ...next };
    sessionStorage.setItem("osanwall.viewport", JSON.stringify(merged));
    set({ viewport: merged });
  },
  isSyncing: false,
  setSyncing: (v) => set({ isSyncing: v }),
}));

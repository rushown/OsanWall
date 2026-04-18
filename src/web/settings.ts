import { z } from "zod";

const settingsSchema = z.object({
  theme: z.enum(["dark", "light"]).default("dark"),
  fontScale: z.number().min(0.8).max(1.6).default(1),
  haptics: z.boolean().default(true),
  muted: z.boolean().default(false),
});

export type AppSettings = z.infer<typeof settingsSchema>;

const SETTINGS_KEY = "osanwall.settings.v1";

export function loadSettings(): AppSettings {
  const raw = localStorage.getItem(SETTINGS_KEY);
  if (!raw) return settingsSchema.parse({});
  try {
    return settingsSchema.parse(JSON.parse(raw));
  } catch {
    return settingsSchema.parse({});
  }
}

export function saveSettings(next: AppSettings): void {
  const parsed = settingsSchema.parse(next);
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(parsed));
}

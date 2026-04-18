#!/usr/bin/env node
const base = process.env.OSANWALL_API_BASE ?? "http://localhost:8787";
const concurrency = Number(process.env.CONCURRENCY ?? 20);
const durationMs = Number(process.env.DURATION_MS ?? 30000);

let sent = 0;
let failed = 0;
let totalLatency = 0;

async function hit() {
  const start = Date.now();
  try {
    const res = await fetch(`${base}/health`, { headers: { "X-Trace-Id": crypto.randomUUID() } });
    if (!res.ok) failed += 1;
  } catch {
    failed += 1;
  } finally {
    sent += 1;
    totalLatency += Date.now() - start;
  }
}

const endAt = Date.now() + durationMs;
const workers = Array.from({ length: concurrency }).map(async () => {
  while (Date.now() < endAt) {
    await hit();
    await new Promise((r) => setTimeout(r, 25 + Math.random() * 50));
  }
});

await Promise.all(workers);
console.log(
  JSON.stringify({
    sent,
    failed,
    errorRate: sent === 0 ? 0 : failed / sent,
    avgLatencyMs: sent === 0 ? 0 : Math.round(totalLatency / sent),
  })
);

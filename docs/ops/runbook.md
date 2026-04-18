# Osanwall Incident Runbook

## 1. Triage
- Confirm blast radius: auth-only, write-only, read-only, or global outage.
- Pull latest trace IDs from Worker logs (`mote.create`, `mote.interact`, `mote.sweep`).
- Check active deploy in GitHub Actions and Cloudflare dashboard.

## 2. Immediate Mitigation
- If write path is failing, switch app to read-only mode via feature flag.
- If queue processing is unstable, pause sweep jobs and reduce background sync interval.
- If high 5xx, rollback Worker to last successful deploy revision.

## 3. Data Integrity Checks
- Verify KV key counts for `mote:*` and `cell:*` prefixes are stable.
- Run consistency check: each `mote:*` must map to exactly one spatial cell.
- Validate no spike in `conflicted` local records.

## 4. Recovery
- Re-enable writes in staged rollout (5% -> 25% -> 100%).
- Monitor p95, error rate, and sync success each stage for 15 minutes.

## 5. Postmortem
- Document root cause, user impact, and recovery timeline.
- Add failing scenario as automated regression test.
- Update SLO and alert thresholds if needed.

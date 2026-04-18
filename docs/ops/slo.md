# Osanwall SLO Targets

## API
- Availability: 99.9% monthly for `/health` and `/api/motes*`.
- Latency: p95 < 250ms for read APIs, p95 < 350ms for write APIs.
- Error budget: 43m 49s per month.

## Sync
- Local write acknowledgment: < 100ms from user action.
- Queue drain success: >= 99% within 60 seconds after connectivity restored.

## Canvas
- Frame stability: <= 1 dropped frame per 10s interaction on target device class.
- Initial interactive paint target: <= 2.5s on mid-tier mobile network profile.

## Alerts
- Trigger on 5m rolling error rate > 2%.
- Trigger on p95 write latency > 500ms for 10 minutes.
- Trigger on sync queue failure ratio > 5% for 10 minutes.

# Event Contracts — JSON Schema

Formal schemas (Draft 2020-12) for events published and consumed by the `subscriptions` service.
Human-readable overview and state machine: see `/docs/events.md`.

## Layout

- `envelope.v1.json` — common envelope (eventId, eventType, schemaVersion, aggregate*, occurredAt, payload)
- `published/*.json` — payload schemas for events emitted by this service
- `consumed/*.json`  — payload schemas for events consumed from `billing`

Each payload schema is referenced from the envelope via `$ref` in integration tests (to be added).

## Conventions

- Filename pattern: `<event-type>.v<version>.json`.
- `schemaVersion` inside the envelope and the schema filename must match.
- New required fields require a major bump (new `vN` file); optional additions stay in-place.

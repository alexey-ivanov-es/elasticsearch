# REST Handler Codegen — Task List

Tasks are taken from the full project plan. Update the status when a task is completed.

**Status key:** `Not done` | `Done`

---

## Phase 1: Simple GET/DELETE Without Body

| Task | Description | Status |
|------|-------------|--------|
| 1.1 | Create the Gradle Plugin Project | Done |
| 1.2 | Define the Data Model | Done |
| 1.3 | Implement Schema Parser | Done |
| 1.4 | Add `@server_transport_action` to PoC Endpoints in the Spec | Done |
| 1.5 | Implement TransportActionResolver | Done |
| 1.6 | Implement TypeMapper | Not done |
| 1.7 | Implement ListenerResolver | Not done |
| 1.8 | Implement HandlerCodeEmitter | Not done |
| 1.9 | Add `fromRestRequest()` to PoC ActionRequests | Not done |
| 1.10 | Generate and Swap | Not done |

---

## Phase 2: PUT/POST With Pass-Through Body

| Task | Description | Status |
|------|-------------|--------|
| 2.1 | Extend Generator Scope Filter | Not done |
| 2.2 | Add `fromRestRequest()` to PoC ActionRequests (Phase 2) | Not done |
| 2.3 | Add `@server_transport_action` to Phase 2 Endpoints | Not done |
| 2.4 | Generate and Swap (Phase 2) | Not done |

---

## Phase 3: Generate fromXContent From Spec

| Task | Description | Status |
|------|-------------|--------|
| 3a | Flat bodies with primitive fields (FromXContentEmitter) | Not done |
| 3b | Nested objects and known types | Not done |
| 3c | Generate fromXContent for shared type definitions | Not done |

---

## Phase 4: Complex and Special Cases

| Task | Description | Status |
|------|-------------|--------|
| 4a | Cancellable endpoints | Not done |
| 4b | Custom response handling (e.g. cat APIs) | Not done |
| 4c | Multi-action endpoints (skip / manual handler) | Not done |
| 4d | Generate toXContent for responses (deferred) | Not done |

---

## Other

| Item | Description | Status |
|------|-------------|--------|
| Validation / drift detection | Tool to compare hand-written handlers vs spec (Section 13) | Not done |
| Generator unit tests | SchemaParser, TransportActionResolver, TypeMapper, HandlerCodeEmitter, ListenerResolver, FromXContentEmitter (Section 15) | Not done |

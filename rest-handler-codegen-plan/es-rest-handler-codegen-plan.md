# Elasticsearch REST Handler Code Generation — Implementation Plan

## 1. Project Summary

### Problem

Every REST handler in Elasticsearch is hand-written Java extending `BaseRestHandler`. Each manually implements `routes()`, `getName()`, `supportedQueryParameters()`, `prepareRequest()`, etc. This is repetitive, error-prone, and drifts out of sync with the `elasticsearch-specification` — the canonical API contract used by all client libraries, docs, and OpenAPI specs.

### Solution

Build a Java code generator (Gradle plugin) that reads the compiled specification (`schema.json`) and generates complete Java REST handler classes, ultimately including XContent request body parsing.

### Repository Context

- **`elasticsearch-specification`** (https://github.com/elastic/elasticsearch-specification) — external repo containing TypeScript API definitions that compile to `output/schema/schema.json`. This is the upstream source of truth. The repository is available as a sibling directory to the elasticsearch repo (e.g. at `../elasticsearch-specification` when working from the elasticsearch repo root; both typically live under the same parent, e.g. `.../src/elasticsearch` and `.../src/elasticsearch-specification`).
- **`elasticsearch`** (https://github.com/elastic/elasticsearch) — the server repo where the generator plugin, vendored spec files, and generated handlers live.

### Vendored Spec Files

The compiled spec files required by the generator are **copied into the elasticsearch repository** at a dedicated location:

```
rest-api-spec/
  src/main/resources/
    rest-api-spec/         ← existing REST API spec JSONs
    schema/                ← NEW: vendored from elasticsearch-specification
      schema.json          ← the compiled spec (full or filtered)
```

This approach:
- Eliminates cross-repo build dependencies — the generator reads a local file, not an external artifact.
- Makes the spec version explicit and reviewable — updating the vendored spec is a deliberate PR.
- Allows adding `@server_transport_action` annotations upstream and only pulling them into the elasticsearch repo when ready.
- Each phase vendors only the endpoints it needs — the spec can be filtered/subset.

The vendored `schema.json` is updated by periodically pulling from `elasticsearch-specification` after running `make generate`. This can be a manual step or automated via CI.

### Phased Approach

| Phase | Scope | Key Outcome |
|-------|-------|-------------|
| 1 | GET/DELETE endpoints without body | End-to-end generator pipeline proven |
| 2 | PUT/POST with pass-through body | Body-bearing endpoints covered |
| 3 | Generate `fromXContent` parsing from spec | Eliminate hand-written body parsing |
| 4 | Complex/special cases | Cancellable, cat APIs, multi-action |

---

## 2. Spec Schema Reference

The generator's primary input is `schema.json`, the compiled output of the elasticsearch-specification. Understanding its structure is essential.

### Top-Level Structure

`schema.json` has two top-level arrays: `types` (all type definitions — requests, responses, enums, interfaces) and `endpoints` (all REST endpoints with routes and type references).

### Endpoint Object

Each endpoint contains: `name` (e.g. `"indices.delete"`), `description`, `urls` (array of `{path, methods}` objects), `stability`, `request` (a `{name, namespace}` reference into the types array), `response` (same), `requestBodyRequired` (boolean), and `availability` (object with `stack` and `serverless` sub-objects containing `since`, `stability`, `visibility` fields).

### Request Type Object

Each request type (identified by `kind: "request"`) contains: `name`, `namespace`, `inherits` (parent type reference), `path` (array of path parameter properties), `query` (array of query parameter properties), and `body`.

Each property in `path` and `query` has: `name` (the parameter name as it appears in the URL/query string, using snake_case), `required` (boolean), `type` (a type descriptor), `serverDefault` (optional default value), and `description`.

### Type Descriptors

Type descriptors have a `kind` field that determines their structure:

- `instance_of` — has a `type` field with `{name, namespace}` pointing to a named type (e.g. `Duration`, `IndexName`, `boolean`).
- `array_of` — has a `value` field containing another type descriptor for the element type.
- `dictionary_of` — has `key` and `value` fields, both type descriptors.
- `union_of` — has `items` field, an array of type descriptors.
- `literal_value` — a constant value.

### Body Kinds

The `body` field on request types has a `kind`:

- `no_body` — endpoint has no request body (GET/DELETE typically).
- `value` — the entire body is a single value (e.g. the `index` API where the body IS the document). Has a `value` type descriptor.
- `properties` — the body is a JSON object with named fields. Has a `properties` array, where each element has `name`, `required`, `type` (a type descriptor), `serverDefault`, and `description`.

### Interface/Class Type Definitions

Non-request types (kind: `interface` or similar) have: `name`, `namespace`, `properties` (array of property objects with same structure as body properties), `inherits` (optional parent type), and sometimes `generics`.

### Enum Type Definitions

Enum types have `kind: "enum"`, `name`, `namespace`, and `members` (array of `{name, description}` objects representing the enum values).

---

## 3. Current Server-Side Patterns

### BaseRestHandler Contract

Every REST handler extends `BaseRestHandler` and overrides these methods:

- **`routes()`** → returns `List<Route>` where each Route is a pair of HTTP method and path string (e.g. `new Route(DELETE, "/{index}")`). Deprecated routes use `Route.builder(method, path).deprecated(message, version).build()`.
- **`getName()`** → returns a unique string identifier for the handler (e.g. `"delete_index_action"`), used for usage tracking.
- **`supportedQueryParameters()`** → returns `Set<String>` of query parameter names this handler accepts. Strict checking rejects unknown parameters.
- **`prepareRequest(RestRequest, NodeClient)`** → extracts parameters from the RestRequest, builds an ActionRequest, and returns a `RestChannelConsumer` lambda that dispatches the request via `client.execute(TYPE, actionRequest, listener)`.

### Parameter Extraction Patterns

Path parameters: `request.param("name")` returns `String`.
Query parameters use typed accessors: `request.paramAsBoolean("name", default)`, `request.paramAsInt("name", default)`, `request.paramAsLong("name", default)`, `request.paramAsTime("name", default)` (returns `TimeValue`), `request.paramAsSize("name", default)` (returns `ByteSizeValue`), or plain `request.param("name")` for strings.

Multi-valued params like `index` use `Strings.splitStringByCommaToArray(request.param("index"))`.

`IndicesOptions.fromRequest(request, defaults)` handles `expand_wildcards`, `ignore_unavailable`, `allow_no_indices` as a group.

### Response Listener Selection

The listener wrapping the response is determined by the ActionResponse class hierarchy:

- **Default**: `RestToXContentListener<>(channel)` — for responses implementing `ToXContentObject`. Returns HTTP 200.
- **Status-bearing**: `RestStatusToXContentListener<>(channel, r -> r.status())` — for responses implementing `StatusToXContentObject` (document CRUD: index, update, delete). HTTP status comes from the response itself.
- **Chunked**: `RestRefCountedChunkedToXContentListener<>(channel)` — for responses implementing `ChunkedToXContentObject` (search). Streams the response.
- **Nodes responses**: `RestActions.NodesResponseRestListener<>(channel)` — for responses extending `BaseNodesResponse` (_nodes/* APIs). Wraps in `{_nodes: {}, cluster_name: ..., nodes: {}}` envelope.

The generator should inspect the ActionResponse class (derivable from the transport action's type parameters) and pick the listener automatically. Priority order: ChunkedToXContentObject > BaseNodesResponse > StatusToXContentObject > ToXContentObject (default).

### ServerlessScope Annotation

Derived from the spec's `availability.serverless` field:
- `visibility: "public"` → annotate handler class with `@ServerlessScope(Scope.PUBLIC)`
- `visibility: "internal"` → `@ServerlessScope(Scope.INTERNAL)`
- No serverless availability → no annotation

### Transport Action Dispatch

Generated handlers **always** use `client.execute(TransportXxxAction.TYPE, actionRequest, listener)`. The `TYPE` is a static `ActionType` constant on the transport action class. The generator needs the fully qualified transport action class name to reference this constant. This choice is intentional for consistency and to derive the action type from the transport action at generation time. Some existing hand-written handlers use the client facade (e.g. `client.admin().indices().delete(...)`); when those are replaced by generated handlers, dispatch will switch to the `client.execute(..., TYPE, ...)` pattern.

---

## 4. Spec Extension: Server Transport Action

### Required Annotation

The TypeScript spec needs a new JSDoc tag on request definitions linking to the server-side transport action:

```typescript
/**
 * @rest_spec_name indices.delete
 * @availability stack since=1.0.0 stability=stable
 * @server_transport_action org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction
 */
```

This annotation is added to the spec **from the very start**, including for the PoC. The spec compiler must be extended to parse this tag and include it in `schema.json` as a new field on the endpoint object:

```json
{
  "name": "indices.delete",
  "serverTransportAction": "org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction",
  ...
}
```

### Spec Compiler Changes

The `elasticsearch-specification` compiler needs a small change:
1. Parse the `@server_transport_action` JSDoc tag from the TypeScript definitions.
2. Include the value as a `serverTransportAction` string field on the endpoint object in `schema.json`.
3. The field is optional — endpoints without it are skipped by the generator.

For the PoC, add this tag to the 3 phase-1 endpoints and the 3 phase-2 endpoints in the TypeScript spec. After regenerating `schema.json` in the `elasticsearch-specification` repo, copy the result to the vendored location in the `elasticsearch` repo (`rest-api-spec/src/main/resources/schema/schema.json`).

### Deriving ActionRequest and ActionResponse via Classpath Inspection

From the transport action class, the generator resolves the ActionRequest and ActionResponse types **by inspecting the Java class hierarchy at Gradle task execution time**. The generator has access to the server module's classpath during the build.

The algorithm:
1. Load the transport action class (e.g., `TransportDeleteIndexAction`) from the compile classpath.
2. Walk its superclass chain until finding a parameterized type that extends `TransportAction<Request, Response>` (or a subclass like `TransportMasterNodeAction<Request, Response>`).
3. Extract the two type parameters: the first is the `ActionRequest` class, the second is the `ActionResponse` class.

For example: `TransportDeleteIndexAction extends TransportMasterNodeAction<DeleteIndexRequest, AcknowledgedResponse>` → `DeleteIndexRequest` and `AcknowledgedResponse`.

This is implemented in a `TransportActionResolver` class that uses Java reflection on the loaded classes. The Gradle task must be configured so the server module's compile classpath is available to the generator task.

---

## 5. Type Mapping Table

### Spec Type → REST Parameter Extraction

| Spec Type (name) | Java Type | Extraction Pattern |
|---|---|---|
| `boolean` (_builtins) | `boolean` | `request.paramAsBoolean("name", defaultValue)` |
| `integer` (_builtins) | `int` | `request.paramAsInt("name", defaultValue)` |
| `long` (_builtins) | `long` | `request.paramAsLong("name", defaultValue)` |
| `float` (_builtins) | `float` | `Float.parseFloat(request.param("name"))` |
| `double` (_builtins) | `double` | `Double.parseDouble(request.param("name"))` |
| `string` (_builtins) | `String` | `request.param("name")` |
| `Duration` (_types) | `TimeValue` | `request.paramAsTime("name", defaultValue)` |
| `ByteSize` (_types) | `ByteSizeValue` | `request.paramAsSize("name", defaultValue)` |
| `IndexName` / `Name` / `Id` / `Routing` | `String` | `request.param("name")` |
| `Indices` / `Names` / `Ids` | `String[]` | `Strings.splitStringByCommaToArray(request.param("name"))` |
| `ExpandWildcards` | handled as group | `IndicesOptions.fromRequest(request, defaults)` — this is special: `expand_wildcards`, `ignore_unavailable`, `allow_no_indices` are typically handled together via `IndicesOptions` rather than individually |
| `WaitForActiveShards` | `ActiveShardCount` | `ActiveShardCount.parseString(request.param("name"))` |
| `VersionType` | `VersionType` | `VersionType.fromString(request.param("name"))` |
| `Refresh` | `String` | `request.param("refresh")` |
| `Scroll` | `Scroll` | `new Scroll(request.paramAsTime("scroll", ...))` |
| Any enum type | String or enum | `request.param("name")` then parse/validate |

### Spec Type → XContent Body Parsing (Phase 3)

| Spec Type Kind | Parser Pattern |
|---|---|
| `string` | `parser.text()` |
| `boolean` | `parser.booleanValue()` |
| `integer` | `parser.intValue()` |
| `long` | `parser.longValue()` |
| `float` | `parser.floatValue()` |
| `double` | `parser.doubleValue()` |
| `Duration` | `TimeValue.parseTimeValue(parser.text(), "fieldName")` |
| Named object with existing `fromXContent` | `TypeName.fromXContent(parser)` |
| `array_of` | `List` — loop: `while (parser.nextToken() != END_ARRAY) { ... parse element ... }` |
| `dictionary_of` | `Map` — loop: `while (parser.nextToken() != END_OBJECT) { String key = parser.currentName(); parser.nextToken(); ... parse value ... }` |
| `union_of` | Token-based dispatch: check `parser.currentToken()` — `START_OBJECT` vs `VALUE_STRING` vs `VALUE_NUMBER` etc. |
| enum | `EnumType.fromString(parser.text())` |

---

## 6. Naming Conventions

### Handler Class Name

Pattern: `Rest` + CamelCase(fullEndpointName) + `Action`

The full endpoint name (including namespace) is converted from `dot.separated.snake_case` to `CamelCase`, then wrapped with the `Rest` prefix and `Action` suffix. This is a simple, consistent rule with no special casing.

Examples:
- `indices.delete` → `RestIndicesDeleteAction`
- `indices.get` → `RestIndicesGetAction`
- `cluster.health` → `RestClusterHealthAction`
- `ingest.put_pipeline` → `RestIngestPutPipelineAction`
- `search` (top-level) → `RestSearchAction`
- `indices.put_mapping` → `RestIndicesPutMappingAction`

**Note**: these derived names may NOT match existing handler class names (e.g., the current `RestDeleteIndexAction` would become `RestIndicesDeleteAction`). This is intentional — the generated handlers use a consistent naming scheme. The old hand-written handler is deleted when the generated one replaces it, so name continuity is not required.

### Handler Name String (getName())

`indices.delete` → `"indices_delete_action"` (replace dots with underscores, append `_action`).

### Package Name

Derive from the endpoint namespace, mapping to the existing package structure:
- `indices.*` → `org.elasticsearch.rest.action.admin.indices`
- `cluster.*` → `org.elasticsearch.rest.action.admin.cluster`
- `ingest.*` → `org.elasticsearch.rest.action.ingest`
- Top-level (no namespace) → `org.elasticsearch.rest.action`
- `cat.*` → `org.elasticsearch.rest.action.cat`
- `nodes.*` → `org.elasticsearch.rest.action.admin.cluster.node` (verify)

**Important**: verify package derivation against the actual codebase. Since generated handler class names differ from existing ones, package placement can follow the consistent namespace-based scheme without needing to match legacy locations.

---

## 7. Generator Architecture

### Location

The generator lives in the `elasticsearch` repo as a Gradle build tool:

```
build-tools-internal/
  rest-handler-generator/
    build.gradle
    src/main/java/org/elasticsearch/gradle/resthandler/
      RestHandlerGeneratorPlugin.java    — Gradle plugin entry point
      RestHandlerGeneratorTask.java      — Task that orchestrates generation
      model/                              — Data model records for schema.json
        Schema.java
        Endpoint.java
        TypeDefinition.java
        Property.java
        TypeDescriptor.java
        Body.java
        Availability.java
        UrlPattern.java
      SchemaParser.java                  — Parses schema.json into model objects
      TransportActionResolver.java       — Resolves ActionRequest/Response from transport action class via reflection
      HandlerCodeEmitter.java            — Generates RestHandler Java source files
      TypeMapper.java                    — Maps spec types to Java types and extraction code
      ListenerResolver.java             — Determines which response listener to use (via ActionResponse class inspection)
      FromXContentEmitter.java           — (Phase 3) Generates XContent parsing code
```

### Dependencies

- **Jackson** (`com.fasterxml.jackson.core:jackson-databind`) for parsing `schema.json`
- **JavaPoet** (`com.squareup:javapoet`) for type-safe Java code generation. JavaPoet generates `JavaFile` objects that can be written to disk. It handles imports, formatting, and type references correctly.
- **Gradle API** for the plugin/task infrastructure
- The generator task requires the **server module's compile classpath** as an input, so it can load transport action classes for reflection-based resolution of ActionRequest/ActionResponse types and listener selection.

### Spec Input Location

The generator reads `schema.json` from the vendored location within the elasticsearch repo:

```
rest-api-spec/src/main/resources/schema/schema.json
```

The Gradle task takes this file path as an `@InputFile`. When the vendored spec is updated (by copying a freshly compiled `schema.json` from `elasticsearch-specification`), the task re-runs and regenerates handlers.

### Generated Output Location

Generated sources go into a build directory (e.g. `server/build/generated/sources/rest-handlers/`) and are added to the source set. The Gradle plugin registers the generation task and wires the output directory as a source directory.

### Plugin Application

The generator plugin is applied **only to the `:server` project** for now. The long-term intention is to apply it to every module that registers REST handlers (e.g. x-pack and other plugins).

### Coexistence with Hand-Written Handlers

During migration, generated and hand-written handlers coexist. The hand-written handler is deleted only after the generated one is verified to produce identical behavior (verified by existing YAML REST tests passing). The generator only processes endpoints that have a `serverTransportAction` field in schema.json — endpoints without it are skipped.

---

## 8. The `fromRestRequest()` Pattern

### Concept

Each ActionRequest class gains a static factory method that knows how to populate itself from a `RestRequest`. This method is **not generated** in phases 1-2 — it is hand-written by relocating existing code from the handler's `prepareRequest()` into the ActionRequest class. In phase 3, the body-parsing portion of this method is generated.

### Example Shape

For `DeleteIndexRequest`, the existing handler code:

```java
// Currently in RestDeleteIndexAction.prepareRequest():
DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(
    Strings.splitStringByCommaToArray(request.param("index")));
deleteIndexRequest.ackTimeout(RestUtils.getAckTimeout(request));
deleteIndexRequest.masterNodeTimeout(RestUtils.getMasterNodeTimeout(request));
deleteIndexRequest.indicesOptions(
    IndicesOptions.fromRequest(request, deleteIndexRequest.indicesOptions()));
```

Gets relocated to:

```java
// New method on DeleteIndexRequest itself (using RestUtils for ack_timeout / master_timeout):
public static DeleteIndexRequest fromRestRequest(RestRequest request) {
    DeleteIndexRequest r = new DeleteIndexRequest(
        Strings.splitStringByCommaToArray(request.param("index")));
    r.ackTimeout(RestUtils.getAckTimeout(request));
    r.masterNodeTimeout(RestUtils.getMasterNodeTimeout(request));
    r.indicesOptions(IndicesOptions.fromRequest(request, r.indicesOptions()));
    return r;
}
```

Parameter names and helpers (e.g. `RestUtils.getAckTimeout`, `RestUtils.getMasterNodeTimeout`) follow the spec's query parameter names and existing RestUtils where applicable.

### Why This Pattern

- The generated handler only needs to call `ActionRequest.fromRestRequest(request)` — one line.
- The method is independently testable.
- It's a pure refactoring of existing code — no new logic.
- In phase 3, only the XContent body parsing within this method needs to be generated.

### Migration Steps Per Endpoint

1. Add `fromRestRequest()` to the ActionRequest class, copying logic from the existing handler.
2. Modify the existing hand-written handler to call `fromRestRequest()` instead of inline code.
3. Run YAML REST tests to verify identical behavior.
4. Now the endpoint is ready for the generator — the generated handler will call the same `fromRestRequest()`.
5. Delete the hand-written handler, replace with generated one.
6. Run YAML REST tests again.

---

## 9. Phase 1: Simple GET/DELETE Without Body

### Goal

Prove the full generator pipeline end-to-end: read schema.json → emit compilable Java handlers → integrate into the Gradle build → pass existing YAML REST tests.

### Scope

Endpoints that use only GET, DELETE, or HEAD methods, have NO request body (`body.kind == "no_body"`), and have a `serverTransportAction` field present in the schema.

### PoC Endpoints

1. **`indices.delete`** — `DELETE /{index}`. One required path param (`index`), standard query params (`timeout`, `master_timeout`, `expand_wildcards`, `ignore_unavailable`, `allow_no_indices`). Response: `AcknowledgedResponse`. Listener: `RestToXContentListener`. This is the simplest possible endpoint.

2. **`indices.get`** — `GET /{index}`, `HEAD /{index}`. Path param, query params (`flat_settings`, `include_defaults`, `local`, `features`). Response: `GetIndexResponse`. Listener: `RestToXContentListener`. Tests HEAD method support and multiple routes for same endpoint.

3. **`cluster.health`** — `GET /_cluster/health`, `GET /_cluster/health/{index}`. Optional path param (two URL patterns — one with `{index}`, one without), many query params (`level`, `local`, `timeout`, `wait_for_active_shards`, `wait_for_nodes`, `wait_for_events`, `wait_for_status`, `wait_for_no_relocating_shards`, `wait_for_no_initializing_shards`). Response: `ClusterHealthResponse`. Tests optional path parameters and larger parameter sets.

### Implementation Tasks

#### Task 1.1: Create the Gradle Plugin Project

- Create directory structure under `build-tools-internal/rest-handler-generator/`.
- Set up `build.gradle` with dependencies on Jackson and JavaPoet.
- Implement `RestHandlerGeneratorPlugin` that registers a `RestHandlerGeneratorTask`.
- The task takes inputs: path to the vendored `schema.json` (at `rest-api-spec/src/main/resources/schema/schema.json`), the server module's compile classpath, and output directory.
- Wire the output directory into the server module's source sets.

#### Task 1.2: Define the Data Model

- Create Java records (or classes) mirroring the schema.json structure: `Schema`, `Endpoint`, `UrlPattern`, `TypeDefinition`, `Property`, `TypeDescriptor`, `Body`, `Availability`, `AvailabilityDetail`, `TypeReference`.
- These are internal to the generator — they don't need to be part of the Elasticsearch server runtime.

#### Task 1.3: Implement Schema Parser

- Parse `schema.json` using Jackson's `ObjectMapper` into the data model.
- Build a type lookup map: `namespace.name` → `TypeDefinition` for resolving type references.
- Resolve each endpoint's request and response types by looking up their `{name, namespace}` reference in the type map.

#### Task 1.4: Add `@server_transport_action` to PoC Endpoints in the Spec

- In the `elasticsearch-specification` repo, add the `@server_transport_action` JSDoc tag to the TypeScript request definitions for the 3 PoC endpoints.
- Extend the spec compiler to parse this tag and include `serverTransportAction` as a field on the endpoint object in `schema.json`.
- Regenerate `schema.json` and verify the field appears correctly.
- Copy the regenerated `schema.json` to the vendored location in the `elasticsearch` repo: `rest-api-spec/src/main/resources/schema/schema.json`.

#### Task 1.5: Implement TransportActionResolver

- Create `TransportActionResolver` class that takes a fully-qualified transport action class name and the server compile classpath.
- Using a `URLClassLoader` over the compile classpath, load the transport action class.
- Walk the superclass chain using `getGenericSuperclass()` and `ParameterizedType.getActualTypeArguments()` to find the `ActionRequest` and `ActionResponse` type parameters.
- Return a record containing: the transport action class, the ActionRequest class, and the ActionResponse class.
- Handle edge cases: the transport action may have multiple levels of inheritance (e.g. `TransportDeleteIndexAction extends TransportMasterNodeAction<DeleteIndexRequest, AcknowledgedResponse>` where `TransportMasterNodeAction extends TransportMasterNodeReadAction` etc.). Walk until you find concrete type arguments, not type variables.

#### Task 1.6: Implement TypeMapper

- Build the spec-type → Java-type mapping table from section 5.
- For each property, given its type descriptor, return: the Java type name, the extraction code pattern (how to read it from `RestRequest`), and whether it needs special handling (like `IndicesOptions` group).
- Handle the `IndicesOptions` special case: when a request has `expand_wildcards`, `ignore_unavailable`, or `allow_no_indices` params, they should be handled via `IndicesOptions.fromRequest()` rather than individually.

#### Task 1.7: Implement ListenerResolver

- Given the `ActionResponse` class (resolved by `TransportActionResolver` in task 1.5), determine which response listener to use.
- Use reflection to inspect the ActionResponse class hierarchy. Check which interfaces it implements, in priority order:
  1. `ChunkedToXContentObject` → `RestRefCountedChunkedToXContentListener`
  2. `BaseNodesResponse` (check superclass chain) → `RestActions.NodesResponseRestListener`
  3. `StatusToXContentObject` → `RestStatusToXContentListener`
  4. Default → `RestToXContentListener`
- This runs at generation time using the same classpath available to the TransportActionResolver.

#### Task 1.8: Implement HandlerCodeEmitter

- Using JavaPoet, generate a complete Java class for each endpoint.
- Generated class extends `BaseRestHandler`.
- Generate these methods: `routes()`, `getName()`, `supportedQueryParameters()`, `prepareRequest()`.
- `prepareRequest()` calls `ActionRequest.fromRestRequest(request)` and dispatches via `client.execute(TYPE, actionRequest, new Listener<>(channel))`.
- Add `@ServerlessScope` annotation based on availability.
- Add a `@Generated` annotation or file header comment marking the file as generated.

#### Task 1.9: Add `fromRestRequest()` to PoC ActionRequests

- For each of the 3 PoC endpoints, add a `public static XxxRequest fromRestRequest(RestRequest request)` method to the existing ActionRequest class.
- The body of this method is copied from the existing handler's `prepareRequest()` — it's a refactoring, not new logic.
- Temporarily modify the existing hand-written handler to call `fromRestRequest()` to verify the refactoring is correct.
- Run YAML REST tests for these endpoints.

#### Task 1.10: Generate and Swap

- Run the generator for the 3 PoC endpoints.
- Compare generated handler code with existing hand-written code to verify they're functionally equivalent.
- Replace hand-written handlers with generated ones.
- Run the full YAML REST test suite to verify no regressions.

### Success Criteria

- Generator Gradle task runs successfully as part of the build.
- Generated handlers compile without manual edits.
- All existing YAML REST tests for `indices.delete`, `indices.get`, and `cluster.health` pass with the generated handlers.
- Generated code is readable and follows Elasticsearch code conventions.

---

## 10. Phase 2: PUT/POST With Pass-Through Body

### Goal

Handle endpoints that have a request body, where the ActionRequest already knows how to parse the body via its existing `.source()` or similar method. The generator doesn't need to understand the body structure — it just detects that a body exists and passes it through.

### Scope

Endpoints that use PUT or POST, have `requestBodyRequired: true` (or optional body), and whose ActionRequest has an existing method to consume the body from XContent (typically `.source(XContentParser, ...)` or `fromXContent(XContentParser)`).

### What Changes From Phase 1

The generated `prepareRequest()` is identical — it still just calls `ActionRequest.fromRestRequest(request)`. The difference is that the hand-written `fromRestRequest()` method on the ActionRequest now includes body parsing:

```java
public static CreateIndexRequest fromRestRequest(RestRequest request) {
    CreateIndexRequest r = new CreateIndexRequest(request.param("index"));
    // query params...
    r.timeout(request.paramAsTime("timeout", r.timeout()));
    r.masterNodeTimeout(request.paramAsTime("master_timeout", r.masterNodeTimeout()));
    // body pass-through
    if (request.hasContent()) {
        r.source(request.contentOrSourceParamParser(), LoggingDeprecationHandler.INSTANCE);
    }
    return r;
}
```

The generator itself only needs to know that the endpoint has a body (from the spec's `requestBodyRequired` field) so it doesn't reject PUT/POST endpoints. The actual body parsing is still in the hand-written `fromRestRequest()`.

### PoC Endpoints

1. **`indices.create`** — `PUT /{index}`. Path param, query params (`timeout`, `master_timeout`, `wait_for_active_shards`), body with `settings`, `mappings`, `aliases`. `CreateIndexRequest` already has a `.source()` method that parses the entire body.

2. **`indices.put_mapping`** — `PUT /{index}/_mapping`, `PUT /_mapping`. Body is the mapping definition. `PutMappingRequest` has `.source()`.

3. **`ingest.put_pipeline`** — `PUT /_ingest/pipeline/{id}`. Different domain (ingest, not indices). Body is the pipeline configuration. `PutPipelineRequest` takes the body as `BytesReference`.

### Implementation Tasks

#### Task 2.1: Extend Generator Scope Filter

- Modify the `isSupported()` filter in the generator task to also accept endpoints with `body.kind == "properties"` or `body.kind == "value"`, not just `"no_body"`.
- No changes needed to `HandlerCodeEmitter` — the generated handler code is the same (calls `fromRestRequest()`).

#### Task 2.2: Add `fromRestRequest()` to PoC ActionRequests

- For each of the 3 PoC endpoints, add `fromRestRequest()` to the ActionRequest class.
- Include body parsing using the existing `.source()` / `fromXContent()` methods.
- Refactor existing handlers to call it, verify with tests.

#### Task 2.3: Add `@server_transport_action` to Phase 2 Endpoints

- Add the `@server_transport_action` tag to the 3 phase-2 endpoints in the TypeScript spec.
- Regenerate `schema.json` and copy to the vendored location in the `elasticsearch` repo.

#### Task 2.4: Generate and Swap

- Generate handlers, compare, swap, run tests.

### Success Criteria

- All existing YAML REST tests for `indices.create`, `indices.put_mapping`, and `ingest.put_pipeline` pass with generated handlers.
- Generator correctly handles POST/PUT methods and body-bearing endpoints.

---

## 11. Phase 3: Generate `fromXContent` From the Spec

### Goal

This is the high-value phase. Generate XContent body parsing code directly from the spec's body type definitions, eliminating hand-written parsing logic. After this phase, adding a new endpoint requires only adding its spec entry (with `@server_transport_action` annotation) and vendoring the updated `schema.json` — zero hand-written REST/parsing code.

### Sub-Phase 3a: Flat Bodies With Primitive Fields

#### Scope

Endpoints whose body contains only simple/primitive fields: strings, numbers, booleans, enums. No nested objects, no complex types. The generator produces a `fromXContent(XContentParser)` method that parses the body into an ActionRequest.

#### What Gets Generated

For an endpoint with body properties like `{ max_num_segments: integer, only_expunge_deletes: boolean, flush: boolean }`, generate:

```java
// Generated parsing code pattern (conceptual)
public static ForceMergeRequest bodyFromXContent(XContentParser parser) throws IOException {
    ForceMergeRequest request = new ForceMergeRequest();
    ensureExpectedToken(Token.START_OBJECT, parser.nextToken(), parser);
    while (parser.nextToken() != Token.END_OBJECT) {
        String fieldName = parser.currentName();
        parser.nextToken();
        switch (fieldName) {
            case "max_num_segments" -> request.maxNumSegments(parser.intValue());
            case "only_expunge_deletes" -> request.onlyExpungeDeletes(parser.booleanValue());
            case "flush" -> request.flush(parser.booleanValue());
            default -> parser.skipChildren();
        }
    }
    return request;
}
```

#### Generator Changes

- New `FromXContentEmitter` class that takes a body definition (list of properties with type descriptors) and generates XContent parsing code.
- For each property, use the XContent type mapping table (section 5) to determine the parser call.
- Generate a switch statement over field names.
- Unknown fields: call `parser.skipChildren()` for forward compatibility.
- Integrate with `fromRestRequest()`: the generated body parser is called within `fromRestRequest()` when the request has content.

#### Key Decision: Where Does the Generated fromXContent Live?

Options:
1. **On the ActionRequest class itself** — generate a static `fromXContent()` method that the hand-written `fromRestRequest()` calls. This means modifying ActionRequest source files, which is awkward for generated code.
2. **In the generated handler** — inline the parsing logic directly in `prepareRequest()`. Simpler but makes the generated handler longer.
3. **In a separate generated helper class** — e.g., `IndicesCreateRequestParser.java` that has a `parse(XContentParser, ActionRequest)` method. Clean separation.

Recommendation: **option 3** — generate a separate parser helper class per endpoint. The `fromRestRequest()` delegates body parsing to this generated helper. This keeps generated and hand-written code in separate files.

```java
// Hand-written fromRestRequest() calls generated parser:
public static CreateIndexRequest fromRestRequest(RestRequest request) {
    CreateIndexRequest r = new CreateIndexRequest(request.param("index"));
    // query params...
    if (request.hasContent()) {
        CreateIndexRequestParser.parseBody(request.contentOrSourceParamParser(), r);
    }
    return r;
}
```

Eventually (when the full `fromRestRequest()` is generated), the parser helper is inlined into the generated handler.

#### Setter Name Resolution

The generated parser needs to know which setter to call on the ActionRequest for each body field. The spec field name is `snake_case` (e.g. `max_num_segments`), the Java setter is `camelCase` (e.g. `maxNumSegments()`).

Strategy:
1. Convert spec field name from `snake_case` to `camelCase` — this is the default setter name guess.
2. If the convention doesn't match for a specific field, the override can be expressed as a new spec annotation on the property (e.g. `@server_setter_name maxNumSegments`) or handled as a special case in the generator's type mapper.
3. For the PoC, verify each mapping manually.

#### PoC Endpoints

Finding truly flat-body endpoints in Elasticsearch is tricky — most have at least some nesting. Good candidates:

1. **`indices.forcemerge`** — `POST /{index}/_forcemerge`. Body fields (if any) are simple. Actually, forcemerge is mostly query-param driven. Let me pick better:

1. **`indices.split`** — `PUT /{index}/_split/{target}`. Body has `settings` and `aliases` — but these are complex. Not ideal.

Better candidates for truly flat/primitive bodies:

1. **`clear_scroll`** — `DELETE /_search/scroll`. Body has `scroll_id: string | string[]`. Simple.
2. **`indices.update_aliases`** — the `actions` array contains simple objects with known structure.

Actually, for phase 3a it may be most practical to pick endpoints where the body IS flat even if the endpoint itself is simple. Alternatively, pick an endpoint with a few primitive body fields and one or two well-known complex fields that already have `fromXContent()`.

Revised PoC for 3a:

1. **`indices.clone`** — `PUT /{index}/_clone/{target}`. Body: `settings` (IndexSettings — has existing fromXContent), `aliases` (Map of Alias — has existing fromXContent). Mix of complex types with existing parsers.
2. **`indices.shrink`** — same body structure as clone.

These endpoints test that the generator can emit a parser that delegates to existing `fromXContent()` for complex sub-types.

### Sub-Phase 3b: Nested Objects and Known Types

#### Scope

Extend the generator to handle body fields that are nested objects, arrays, maps, and well-known Elasticsearch types. The generator recursively follows the spec's type definitions to generate parsing code.

#### What Changes

The `FromXContentEmitter` now handles these type descriptor kinds:

- **`instance_of` pointing to a named type**: check if that type has an existing `fromXContent()` in the codebase. If yes, generate a delegation call: `TypeName.fromXContent(parser)`. If no, recursively generate parsing code for that type's properties.
- **`array_of`**: generate a list-parsing loop.
- **`dictionary_of`**: generate a map-parsing loop.
- **`union_of`**: generate token-based dispatch (inspect current token to determine which branch).

#### Handling Types With Existing fromXContent

Many core Elasticsearch types already have `fromXContent()` methods: `QueryBuilder`, `AggregationBuilder`, `SortBuilder`, `TypeMapping`, `IndexSettings`, `Alias`, etc.

The generator needs a registry of types that have existing parsers. This can be:
- A static list in the generator config, or
- A convention: if the Java class has a `public static XxxType fromXContent(XContentParser)` method, use it.

For the PoC, use a static list. Include at minimum: `IndexSettings`, `TypeMapping`, `Alias`, `QueryBuilder` (via `AbstractQueryBuilder.parseTopLevelQuery`), `Script`, `FieldSort`/`SortBuilder`.

#### Stop Condition for Recursion

Do NOT attempt to generate parsers for deeply recursive or highly polymorphic types like `QueryBuilder`, `AggregationBuilder`, `Property` (mapping), `FieldType`. These have complex parsing logic with named registries and builder patterns. Always delegate to existing parsers for these.

The generator should have an explicit "known complex types" list that always delegates.

#### PoC Endpoints

1. **`indices.create`** — body has `settings: IndexSettings` (delegate to existing), `mappings: TypeMapping` (delegate), `aliases: Record<IndexName, Alias>` (generate map loop, delegate Alias parsing). This is the key test: generating the top-level body parser that dispatches to existing sub-parsers.

2. **`indices.put_alias`** — `PUT /{index}/_alias/{name}`. Body has `filter` (QueryBuilder — delegate), `routing` (string), `index_routing` (string), `search_routing` (string), `is_write_index` (boolean). Mix of primitives and one complex delegated type.

3. **`reindex`** — `POST /_reindex`. Body has `source` (object with `index`, `query`, `size`, `slice`, `remote`, `sort`, `_source` fields) and `dest` (object with `index`, `routing`, `op_type`, `pipeline`, `version_type` fields). Tests nested object parsing where the sub-objects need generated parsers themselves.

### Sub-Phase 3c: Generate fromXContent for Shared Type Definitions

#### Scope

Instead of only generating parsers for endpoint bodies, also generate `fromXContent()` for mid-level type definitions in the spec that are used across multiple endpoints. For example, `Alias`, `SlicedScroll`, `ReindexSource`, `ReindexDestination`.

#### What Changes

The generator processes not just endpoint body definitions but also standalone type definitions from the `types` array in schema.json. It generates a parser class for each type that doesn't already have one in the codebase.

#### Approach

- Walk the type graph from each endpoint's body fields.
- For each referenced type, check if it's in the "has existing parser" registry.
- If not, generate a parser for it, recursively resolving its own field types.
- Stop at the "known complex types" boundary.

#### PoC

Select 5-10 mid-complexity types that are:
- Used by multiple endpoints
- Not already having `fromXContent()` (or having one that could be replaced)
- Not deeply recursive

Good candidates will be apparent after implementing 3a and 3b — they'll be the types where the generator had to stop and delegate to hand-written code.

### Phase 3 Success Criteria

- The generator produces compilable `fromXContent` parsing code for endpoint bodies.
- Generated parsers correctly parse all fields defined in the spec.
- Generated parsers delegate to existing `fromXContent()` for complex sub-types.
- YAML REST tests pass with generated parsers replacing hand-written ones.
- Adding a new simple endpoint requires only: spec definition with `@server_transport_action` + vendored `schema.json` update. No hand-written Java parsing code.

---

## 12. Phase 4: Complex and Special Cases

### Phase 4a: Cancellable Endpoints

Some long-running endpoints wrap the `NodeClient` in `RestCancellableNodeClient` so the request can be cancelled when the HTTP connection is closed. The generator needs to support this.

**Trigger**: a new spec annotation on the endpoint (e.g. `@cancellable true`) or derived from endpoint characteristics (long-running operations).

**Code change**: instead of `client.execute(TYPE, request, listener)`, generate:

```java
RestCancellableNodeClient cancelClient = new RestCancellableNodeClient(client, request.getHttpChannel());
cancelClient.execute(TYPE, actionRequest, listener);
```

**PoC**: `_search`, `_cluster/health`

### Phase 4b: Custom Response Handling

Endpoints that don't use standard listeners: cat APIs (return `text/plain` with custom table formatting), endpoints with custom status code logic, endpoints that add Location headers.

**Approach**: generate an abstract base class with all the boilerplate (routes, name, params), leaving a `buildResponse()` hook for the developer:

```java
// Generated abstract class
public abstract class AbstractRestCatIndicesAction extends BaseRestHandler {
    @Override public List<Route> routes() { /* generated */ }
    @Override public String getName() { /* generated */ }
    @Override public Set<String> supportedQueryParameters() { /* generated */ }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        CatIndicesRequest actionRequest = CatIndicesRequest.fromRestRequest(request);
        return buildResponse(actionRequest, request, client);
    }

    protected abstract RestChannelConsumer buildResponse(
        CatIndicesRequest request, RestRequest restRequest, NodeClient client);
}
```

Developer extends this and implements only `buildResponse()`.

**PoC**: `cat.indices`, `cat.health`

### Phase 4c: Multi-Action Endpoints

Handlers that dispatch to different transport actions based on request parameters. Example: `RestIndexAction` has `CreateHandler` and `AutoIdHandler` sub-classes with different route sets and validation logic.

**Approach**: the generator skips these endpoints. They remain fully hand-written. Endpoints without a `serverTransportAction` in the spec, or endpoints explicitly marked with a `@server_manual_handler` annotation, are not generated.

**Examples**: `index`, `bulk`, `msearch`

### Phase 4d: Generate `toXContent` for Responses

The inverse of phase 3 — generate serialization code for ActionResponse types. The spec defines response body shapes, so you can generate `toXContent()` methods.

This is a separate large effort and lower priority than request parsing. Defer to a future project.

---

## 13. Validation and Drift Detection

### Immediate Value (Can Be Built Before Full Generation)

Even before generating handlers, build a **validation tool** that compares existing hand-written handlers against the spec and reports drift:

- **Missing parameters**: spec defines a query param that the handler's `supportedQueryParameters()` doesn't include.
- **Extra parameters**: handler accepts a param not in the spec.
- **Wrong routes**: handler's `routes()` don't match spec's `urls`.
- **Missing endpoints**: spec defines an endpoint with no corresponding handler.
- **Deprecated route mismatches**: handler has deprecated routes not reflected in spec, or vice versa.

This tool runs as a CI check and provides immediate value before any handler is replaced with a generated one.

### Post-Generation Validation

After generating handlers, validate that:
- All generated files compile without errors.
- All YAML REST tests pass.
- Generated handler behavior is equivalent to the replaced hand-written handler (verified by test coverage).

---

## 14. Migration Strategy

### Per-Endpoint Migration Checklist

1. Add `@server_transport_action` tag to the endpoint's request definition in the TypeScript spec. Regenerate `schema.json` and copy to the vendored location in the elasticsearch repo.
2. Add `fromRestRequest()` to the ActionRequest class (refactor from existing handler).
3. Run YAML REST tests to verify refactoring.
4. Enable generation for this endpoint.
5. Compare generated handler with hand-written handler (manual review or automated diff).
6. Delete hand-written handler.
7. Run full YAML REST test suite.

### Ordering

Start with the simplest, most stable endpoints. Avoid endpoints that are:
- Actively being modified in other PRs
- Having known issues or TODOs in their handler code
- Plugin endpoints (start with server module only)

### Rollback

If a generated handler causes issues, re-enable the hand-written handler by removing the `@server_transport_action` tag from the spec (so the generator skips it) and restoring the hand-written class.

---

## 15. Testing Strategy

### Unit Tests for the Generator

- Test `SchemaParser` with sample schema.json fragments.
- Test `TransportActionResolver` resolves correct ActionRequest/ActionResponse from known transport action classes.
- Test `TypeMapper` with each spec type → Java type mapping.
- Test `HandlerCodeEmitter` produces expected Java source for sample endpoints.
- Test `FromXContentEmitter` produces correct parsing code for various body shapes (primitives, nested objects, arrays, maps).
- Test `ListenerResolver` picks the right listener for each ActionResponse class hierarchy.

### Integration Tests

- Run the generator against the full schema.json for configured endpoints.
- Compile the generated code.
- Run YAML REST tests.

### Regression Tests

- The existing YAML REST test suite IS the regression test. If generated handlers pass all tests that the hand-written handlers passed, the generation is correct.
- Add a CI step that generates handlers and runs tests as part of the build.

---

## 16. File Summary

### New Files to Create

```
rest-api-spec/src/main/resources/
  schema/
    schema.json                          ← vendored from elasticsearch-specification

build-tools-internal/rest-handler-generator/
  build.gradle
  src/main/java/org/elasticsearch/gradle/resthandler/
    RestHandlerGeneratorPlugin.java
    RestHandlerGeneratorTask.java
    SchemaParser.java
    TransportActionResolver.java
    HandlerCodeEmitter.java
    TypeMapper.java
    ListenerResolver.java
    FromXContentEmitter.java           (Phase 3)
    model/
      Schema.java
      Endpoint.java
      TypeDefinition.java
      Property.java
      TypeDescriptor.java
      Body.java
      Availability.java
      AvailabilityDetail.java
      UrlPattern.java
      TypeReference.java
  src/test/java/org/elasticsearch/gradle/resthandler/
    SchemaParserTests.java
    TransportActionResolverTests.java
    TypeMapperTests.java
    HandlerCodeEmitterTests.java
    FromXContentEmitterTests.java      (Phase 3)
```

### Files to Modify

- **`elasticsearch-specification`** (upstream): add `@server_transport_action` JSDoc tags to PoC endpoint request definitions; extend spec compiler to parse the tag and include it in `schema.json`.
- **`rest-api-spec/src/main/resources/schema/schema.json`**: updated by copying regenerated `schema.json` from the spec repo after each phase.
- ActionRequest classes for PoC endpoints: add `fromRestRequest()` static factory method.
- Server module `build.gradle`: apply the generator plugin, add generated sources to source sets, pass compile classpath to generator task.
- Existing hand-written handlers: delete after successful migration (one at a time).

### External Dependency

- `elasticsearch-specification` repo: the TypeScript definitions and spec compiler live here. Changes to add `@server_transport_action` tags and compiler support are made upstream, then `schema.json` is regenerated and copied into the elasticsearch repo's vendored location.

---

## 17. Open Questions and Decisions

### Resolved

- **JavaPoet vs. template engine?** → JavaPoet. Handles imports, formatting, type references correctly.
- **Generated sources in source tree vs. build directory?** → Build directory. Truly generated, never committed.
- **When to modify the spec?** → From the start. The `@server_transport_action` annotation is added to the spec for PoC endpoints.
- **How to resolve ActionRequest/ActionResponse?** → Classpath inspection via reflection at Gradle task execution time. No manual mapping files.

### Open

1. **IndicesOptions handling**: `expand_wildcards`, `ignore_unavailable`, `allow_no_indices` are typically handled together via `IndicesOptions.fromRequest()`. The generator needs to detect when a request has these params and generate the grouped call rather than individual extraction. Decision: hardcode this pattern recognition for the IndicesOptions param group.

2. **Default values**: the spec has `serverDefault` for some parameters. Should the generator use these as defaults in `request.paramAsXxx("name", default)` calls? Yes, when available. Fall back to Java-side defaults when not specified.

3. **Backward compatibility**: generated handlers must support `RestApiVersion.V_7` deprecated routes where applicable. The spec has deprecation information that can drive this.

4. **Spec compiler ownership**: the spec compiler lives in `elasticsearch-specification` and is maintained by the clients team. Coordinate the `@server_transport_action` annotation addition with them. It should be non-breaking — endpoints without the tag are unaffected.

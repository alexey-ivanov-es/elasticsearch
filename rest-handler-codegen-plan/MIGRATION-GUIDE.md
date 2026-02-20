# Migrating an endpoint from hand-written to generated REST handler

Step-by-step instructions for migrating a single REST endpoint from its hand-written handler to the code-generated handler. Use this guide together with the [project plan](es-rest-handler-codegen-plan.md) and [CHANGED-FILES.md](CHANGED-FILES.md).

---

## Prerequisites

- The endpoint is in scope for the current phase (e.g. GET/DELETE/HEAD without body for Phase 1).
- You have the `elasticsearch-specification` tree available (in-repo or sibling) and can regenerate `schema.json`.
- The vendored schema lives at `rest-api-spec/src/main/resources/schema/schema.json`. **Never edit `schema.json` by hand** — change the TypeScript spec and regenerate (see [README](README.md) and `elasticsearch-specification/README.md`).

---

## 1. Add server-side annotations to the spec

In the **TypeScript spec** (under `elasticsearch-specification/specification/`), open the request definition for the endpoint (e.g. `indices/get/IndicesGetRequest.ts`, `indices/delete/IndicesDeleteRequest.ts`).

### 1.1 Required: transport action

Add the JSDoc tag that links the endpoint to the server transport action:

```ts
/**
 * ...
 * @server_transport_action org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction
 */
```

Use the **fully qualified class name** of the `Transport*Action` class that handles this API.

### 1.2 Optional: capabilities

If the hand-written handler overrides `supportedCapabilities()` and returns a non-empty set, add:

```ts
 * @server_capabilities search_query_rules,search_phase_took
```

Use a comma-separated list of capability strings. The generator will emit `supportedCapabilities()` returning `Set.of(...)`. If the endpoint does not advertise capabilities, omit this tag.

### 1.3 Optional: allow system index access

If the hand-written handler overrides `allowSystemIndexAccessByDefault()` to return `true`, add:

```ts
 * @server_allow_system_index_access true
```

Omit this tag (or use `false`) for endpoints that do not allow system index access by default.

### 1.4 Optional: can trip circuit breaker

If the hand-written handler overrides `canTripCircuitBreaker()` to return `false` (so the request is not counted against the in-flight circuit breaker), add:

```ts
 * @server_can_trip_circuit_breaker false
```

Omit this tag (or use `true`) for endpoints that use the default (`true`). The generator only emits an override when the value is `false`.

### 1.5 Optional: response params (field-level)

Some query parameters are **not** consumed when building the `ActionRequest`; they are only used when serializing the response (e.g. `flat_settings`, `include_defaults`). For such parameters:

- In the **TypeScript spec**, add the tag **on the query field** (not on the request):

  ```ts
  query_parameters: {
    /**
     * If true, returns settings in flat format.
     * @server_default false
     * @server_response_param
     */
    flat_settings?: boolean
  }
  ```

- The spec compiler will add that parameter name to the endpoint’s `responseParams` in `schema.json`. The generator will:
  - Put it in `responseParams()` (and **not** in `supportedQueryParameters()`).
  - Emit `allSupportedParameters()` as the union of supported query params and response params so `BaseRestHandler`’s “supported equals consumed” check passes.

- In the **ActionRequest’s `fromRestRequest()`**, do **not** read or use that parameter; the response layer will read it when rendering the response.

### 1.6 HTTP methods (including HEAD for “exists” APIs)

The generator emits one `Route(method, path)` per (method, path) pair from the spec’s `urls`. If the same path is used for both “get” and “exists” (e.g. GET returns body, HEAD returns only status), **both methods must be listed** in the spec:

```ts
urls: [
  {
    path: '/{index}'
    methods: ['GET', 'HEAD']
  }
]
```

Example: the **indices.exists** API is implemented as **HEAD /{index}**. The **indices.get** endpoint shares that path and therefore must declare both `GET` and `HEAD`. If only `GET` is listed, the generated handler will not register HEAD and clients will get **405 Method Not Allowed** for indices.exists.

After editing the spec, **regenerate** `schema.json` (see `elasticsearch-specification/README.md`) and **copy** it to `rest-api-spec/src/main/resources/schema/schema.json`.

---

## 2. Implement `fromRestRequest()` on the ActionRequest

Move the parameter-reading logic from the hand-written handler’s `prepareRequest()` into a **static** method on the ActionRequest:

```java
public static GetIndexRequest fromRestRequest(RestRequest request) {
    GetIndexRequest r = new GetIndexRequest(RestUtils.getMasterNodeTimeout(request));
    r.indices(Strings.splitStringByCommaToArray(request.param("index")));
    r.indicesOptions(IndicesOptions.fromRequest(request, r.indicesOptions()));
    // ... other params
    return r;
}
```

### 2.1 Consuming every supported parameter

`BaseRestHandler` requires that the set of **consumed** parameters equals the set of **supported** parameters. A parameter is “consumed” only when you read it from the request (e.g. `request.param("name")`, `request.paramAsBoolean("name", default)`). **Do not use `request.hasParam("name")`** to decide whether to read a param — `hasParam()` does **not** consume the parameter, so the handler will fail the “supported == consumed” assertion.

- For every query parameter that appears in the endpoint’s **supported** set (i.e. in the spec’s query parameters and not in `responseParams`), ensure `fromRestRequest()` **reads** it at least once (even if only to ignore or log).
- For **optional** params you can call `request.param("name")` and then branch on null/empty; that still consumes the param.
- For the deprecated **`local`** parameter used by some cluster/indices APIs: call **`RestUtils.consumeDeprecatedLocalParameter(request)`** inside `fromRestRequest()`. That helper calls `request.param("local")` so the param is consumed, and logs a deprecation warning only when the param is present. Do not read `local` elsewhere if you use this helper.

Do **not** read parameters that are **response params** (see 1.4); they are not part of the request build and are handled during response serialization.

### 2.2 Response params

Parameters marked with `@server_response_param` in the spec must **not** be read or set on the ActionRequest in `fromRestRequest()`. They are exposed to the response layer only. The generated handler’s `responseParams()` and `allSupportedParameters()` already account for them.

### 2.3 Response type and `RestStatusProvider` (when the handler uses `::status`)

If the hand-written handler passes a **status method reference** to the listener (e.g. `new RestToXContentListener<>(channel, XxxResponse::status)`), the HTTP status is taken from the response. For the generated handler to do the same, the **ActionResponse** class must implement **`RestStatusProvider`** (`org.elasticsearch.rest.action.RestStatusProvider`).

- **Interface**: `RestStatusProvider` declares a single method `RestStatus status();`.
- **ActionResponse**: Add `implements RestStatusProvider` to the response class and ensure it has a `RestStatus status()` method (add `@Override` if the method already exists).
- The code generator’s `ListenerResolver` checks for this interface; when present, it emits `RestToXContentListener<>(channel, ResponseType::status)` so the REST status comes from the response.

If you omit this, the generated handler will use `RestToXContentListener<>(channel)` and the response will always be HTTP 200.

### 2.4 Verify the refactor

Temporarily change the **existing** hand-written handler to call `XxxRequest.fromRestRequest(request)` instead of inlining the logic. Run the relevant YAML REST tests to confirm behavior is unchanged.

---

## 3. Generate and swap

1. Run the generator so it picks up the new endpoint (it only generates for endpoints that have `serverTransportAction` in the vendored schema):

   ```bash
   ./gradlew :server:generateRestHandlers
   ```

2. Confirm the generated handler appears under `server/build/generated/sources/rest-handlers/` (e.g. `RestIndicesGetAction.java`) and that:
   - `routes()` includes all methods/paths (e.g. GET and HEAD if you added both).
   - `supportedQueryParameters()` matches the spec’s query params (excluding response params).
   - If you use response params, `responseParams()` and `allSupportedParameters()` are present.

3. In **`ActionModule.initRestHandlers()`**:
   - **Remove** the registration line for the old hand-written handler (e.g. `registerHandler.accept(new RestGetIndicesAction());`).
   - The **generated** handlers are already registered via `GeneratedRestHandlerRegistry.registerHandlers(registerHandler)` (added once in Phase 1); no change needed there.

4. **Delete** the hand-written handler class from the server source tree (e.g. `RestGetIndicesAction.java`). The generated class with the naming convention (e.g. `RestIndicesGetAction`) replaces it in the build.

5. Compile and run the YAML REST tests for this endpoint:

   ```bash
   ./gradlew :server:compileRestHandlersJava
   ./gradlew ":rest-api-spec:yamlRestTest" --tests "org.elasticsearch.test.rest.ClientYamlTestSuiteIT.test" -Dtests.yaml=<path/to/test>
   ```

---

## 4. Checklist summary

- [ ] **Spec**: `@server_transport_action` added (required).
- [ ] **Spec**: `@server_capabilities` added if the handler advertises capabilities.
- [ ] **Spec**: `@server_allow_system_index_access true` added if the handler allows system index access by default.
- [ ] **Spec**: `@server_can_trip_circuit_breaker false` added if the handler overrides `canTripCircuitBreaker()` to return `false`.
- [ ] **Spec**: For params used only in response serialization, add `@server_response_param` on the **field** in the request’s query parameters.
- [ ] **Spec**: If the endpoint serves both GET and HEAD (e.g. indices.get / indices.exists), set `methods: ['GET', 'HEAD']` in `urls`.
- [ ] **Schema**: Regenerated from spec and copied to `rest-api-spec/src/main/resources/schema/schema.json`.
- [ ] **ActionRequest**: `fromRestRequest(RestRequest)` implemented; every **supported** (non–response) param is **consumed** (read) — no `hasParam()`-only checks; use `RestUtils.consumeDeprecatedLocalParameter(request)` for `local` where applicable; response params are not read.
- [ ] **ActionResponse**: If the hand-written handler uses a listener with `::status` (e.g. `RestToXContentListener<>(channel, XxxResponse::status)`), the response class implements `RestStatusProvider` so the generator emits the same pattern.
- [ ] **Handler**: Hand-written handler removed from source; its registration removed from `ActionModule.initRestHandlers()`.
- [ ] **Tests**: YAML REST tests for the endpoint pass with the generated handler.

---

## References

- **Project plan**: [es-rest-handler-codegen-plan.md](es-rest-handler-codegen-plan.md) — schema layout, `BaseRestHandler` contract, response params, capabilities, allowSystemIndexAccess, canTripCircuitBreaker, naming, generator architecture, response listener selection (`RestStatusProvider`).
- **Schema regeneration**: [README](README.md), `elasticsearch-specification/README.md`.
- **Changed files**: [CHANGED-FILES.md](CHANGED-FILES.md) — list of files touched by this project (update when you change/remove handlers or spec).

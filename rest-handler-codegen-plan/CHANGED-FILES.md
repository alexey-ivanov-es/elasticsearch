# REST Handler Codegen — Changed Files

List of files created, modified, or removed for this project. **Update this file for every change that gets committed.**

---

## New files

- `rest-handler-codegen-plan/` — project plan folder
- `rest-handler-codegen-plan/es-rest-handler-codegen-plan.md` — full implementation plan (moved from repository root)
- `rest-handler-codegen-plan/TASKS.md` — task list with status
- `rest-handler-codegen-plan/CHANGED-FILES.md` — this file
- `rest-handler-codegen-plan/README.md` — instructions and pointers
- `elasticsearch-specification/` — vendored from [elasticsearch-specification](https://github.com/elastic/elasticsearch-specification) for editing in this repo:
  - `elasticsearch-specification/specification/` — TypeScript API definitions (full tree)
  - `elasticsearch-specification/compiler/` — Node/TS compiler that produces schema.json (excl. node_modules; `compiler-wasm-lib` dependency removed so npm install works without Rust)
  - `elasticsearch-specification/README.md` — how to generate schema.json and copy to rest-api-spec
  - `elasticsearch-specification/.nvmrc` — Node 24
  - `elasticsearch-specification/.gitignore` — output/, compiler/node_modules
- **Task 1.1 (REST handler generator plugin):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorPlugin.java` — plugin that creates `restHandlers` source set, registers `generateRestHandlers` task (depends on compileJava), wires output to restHandlers, jar and test classpaths
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorTask.java` — task with inputs (schema file, server main classes dirs, server compile classpath), URLClassLoader for resolution, emits handler classes + GeneratedRestHandlerRegistry
  - `rest-api-spec/src/main/resources/schema/schema.json` — minimal placeholder (empty endpoints/types); **Task 1.4:** replaced with full schema including serverTransportAction for PoC endpoints
- **Task 1.2 (Data model):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/Schema.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/Endpoint.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/UrlPattern.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/TypeDefinition.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/TypeReference.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/Property.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/TypeDescriptor.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/Body.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/Availability.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/AvailabilityDetail.java`
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/model/EnumMember.java`
- **Task 1.3 (Schema parser):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/SchemaParser.java` — parses schema.json with Jackson, builds type lookup map
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/ParsedSchema.java` — parsed schema plus typeByRef map and resolve helpers
  - `build-tools-internal/src/test/java/org/elasticsearch/gradle/resthandler/SchemaParserTests.java` — unit tests for SchemaParser
- **Task 1.5 (TransportActionResolver):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/TransportActionResolver.java` — resolve ActionRequest/ActionResponse and action type reference (TYPE or INSTANCE) from transport action class via reflection; overload with ClassLoader
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/ResolvedTransportAction.java` — record holding transport action class, request class, response class, actionTypeReferenceClass, actionTypeReferenceField
  - `build-tools-internal/src/test/java/org/elasticsearch/gradle/resthandler/TransportActionResolverTests.java` — unit tests for TransportActionResolver
- **Task 1.6 (TypeMapper):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/ParameterMapping.java` — record for Java type name, extraction code, and special handling (e.g. IndicesOptions)
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/TypeMapper.java` — spec-type to Java type and RestRequest extraction code; IndicesOptions group handling
  - `build-tools-internal/src/test/java/org/elasticsearch/gradle/resthandler/TypeMapperTests.java` — unit tests for TypeMapper
- **Task 1.7 (ListenerResolver):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestListenerType.java` — enum of supported listeners (CHUNKED, NODES, STATUS, DEFAULT) with package/class and getClassName() for JavaPoet; replaces ListenerKind
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/ListenerResolver.java` — resolve listener from ActionResponse class via classloader-by-name checks (ChunkedToXContentObject, BaseNodesResponse, StatusToXContentObject, default)
  - `build-tools-internal/src/test/java/org/elasticsearch/gradle/resthandler/ListenerResolverTests.java` — unit tests for ListenerResolver (plain class → DEFAULT; optional server-on-classpath tests for SearchResponse→CHUNKED, NodesInfoResponse→NODES, AcknowledgedResponse→DEFAULT)
- **Task 1.8 (HandlerCodeEmitter):**
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/HandlerCodeEmitter.java` — JavaPoet-based emitter: routes(), getName(), supportedQueryParameters(), prepareRequest(); package from transport action (org.elasticsearch.rest.action + suffix after "action."); RestHandler.Route, BaseRestHandler.RestChannelConsumer; action type TYPE/INSTANCE; emitRegistry(Consumer<RestHandler>) for GeneratedRestHandlerRegistry
  - `build-tools-internal/src/test/java/org/elasticsearch/gradle/resthandler/HandlerCodeEmitterTests.java` — unit tests for emitted handler structure, query params, ServerlessScope, multiple routes, chunked listener, invalid listener FQN
- **Task 1.9 (fromRestRequest for PoC):**
  - `server/.../action/admin/indices/delete/DeleteIndexRequest.java` — add fromRestRequest(RestRequest); handler prepareRequest unchanged, now delegates to it
  - `server/.../action/admin/indices/get/GetIndexRequest.java` — add fromRestRequest(RestRequest); RestGetIndicesAction.prepareRequest delegates to it
  - `server/.../action/admin/cluster/health/ClusterHealthRequest.java` — add fromRestRequest(RestRequest); RestClusterHealthAction.prepareRequest delegates to it, fromRequest() kept as delegating wrapper for tests

---

## Modified files

- `elasticsearch-specification/compiler/package.json` — removed `compiler-wasm-lib` dependency so install works without upstream compiler-rs
- **Task 1.1:** `gradle/build.versions.toml` — added javapoet; `build-tools-internal/build.gradle` — added restHandlerGenerator plugin registration and deps (javapoet, jackson.databind); `server/build.gradle` — apply plugin `elasticsearch.rest-handler-generator`
- **Task 1.3:** `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorTask.java` — call SchemaParser.parse(), log parsed counts
- **Task 1.8:** `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorTask.java` — for each endpoint with serverTransportAction: resolve transport action and listener, emit handler via HandlerCodeEmitter, write JavaFile to output directory; skip endpoints that fail resolution with warn log
- **Task 1.9:** `server/.../rest/action/admin/indices/RestDeleteIndexAction.java`, `RestGetIndicesAction.java`, `server/.../rest/action/admin/cluster/RestClusterHealthAction.java` — prepareRequest() refactored to call XxxRequest.fromRestRequest(request); prepareRequest not removed (per adjustment)
- **Task 1.10:** `server/.../action/ActionModule.java` — registerGeneratedHandlers(registerHandler) via reflection; remove hand-written registration for RestClusterHealthAction, RestGetIndicesAction, RestDeleteIndexAction. `server/.../rest/action/admin/cluster/RestClusterHealthActionTests.java` — use ClusterHealthRequest.fromRestRequest and import generated RestClusterHealthAction from org.elasticsearch.rest.action.admin.cluster.health.
- **Task 1.4:** `elasticsearch-specification/compiler/src/model/metamodel.ts` — add serverTransportAction to Endpoint; `elasticsearch-specification/compiler/src/model/utils.ts` — parse @server_transport_action and set endpoint.serverTransportAction; `elasticsearch-specification/specification/indices/delete/IndicesDeleteRequest.ts`, `indices/get/IndicesGetRequest.ts`, `cluster/health/ClusterHealthRequest.ts` — add @server_transport_action JSDoc; `rest-api-spec/src/main/resources/schema/schema.json` — regenerated from compiler and copied from elasticsearch-specification/output/schema/schema.json. Parser compatibility with full schema: `build-tools-internal/.../SchemaParser.java` — disable FAIL_ON_UNKNOWN_PROPERTIES; `model/TypeDefinition.java` — name as TypeReference (nested {name, namespace}); `model/TypeDescriptor.java` — value as Object (string or nested descriptor); `model/SchemaParserTests.java` — test schema uses nested type name

---

## Removed / moved files

- **Task 1.10:** `server/.../rest/action/admin/indices/RestDeleteIndexAction.java`, `RestGetIndicesAction.java`, `server/.../rest/action/admin/cluster/RestClusterHealthAction.java` — deleted; replaced by generated RestIndicesDeleteAction, RestIndicesGetAction, RestClusterHealthAction in restHandlers source set.
- `es-rest-handler-codegen-plan.md` — moved to `rest-handler-codegen-plan/es-rest-handler-codegen-plan.md`
- `build-tools-internal/.../ListenerKind.java` — removed; replaced by RestListenerType (enum with package/class for emitter)
- `build-tools-internal/.../ResolvedListener.java` — removed; ListenerResolver now returns RestListenerType directly

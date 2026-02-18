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
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorPlugin.java` — plugin that registers `generateRestHandlers` task and wires output into server main source set
  - `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorTask.java` — task with inputs (schema file, server compile classpath) and output directory
  - `rest-api-spec/src/main/resources/schema/schema.json` — minimal placeholder (empty endpoints/types) so the task has a valid input; replaced by vendored spec in Task 1.4
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

---

## Modified files

- `elasticsearch-specification/compiler/package.json` — removed `compiler-wasm-lib` dependency so install works without upstream compiler-rs
- **Task 1.1:** `gradle/build.versions.toml` — added javapoet; `build-tools-internal/build.gradle` — added restHandlerGenerator plugin registration and deps (javapoet, jackson.databind); `server/build.gradle` — apply plugin `elasticsearch.rest-handler-generator`
- **Task 1.3:** `build-tools-internal/src/main/java/org/elasticsearch/gradle/resthandler/RestHandlerGeneratorTask.java` — call SchemaParser.parse(), log parsed counts

---

## Removed / moved files

- `es-rest-handler-codegen-plan.md` — moved to `rest-handler-codegen-plan/es-rest-handler-codegen-plan.md`

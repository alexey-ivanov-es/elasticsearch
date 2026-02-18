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

---

## Modified files

- `elasticsearch-specification/compiler/package.json` — removed `compiler-wasm-lib` dependency so install works without upstream compiler-rs

---

## Removed / moved files

- `es-rest-handler-codegen-plan.md` — moved to `rest-handler-codegen-plan/es-rest-handler-codegen-plan.md`

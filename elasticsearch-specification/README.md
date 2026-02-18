# Elasticsearch API specification (vendored)

This directory contains a copy of the API specification and compiler from the [elasticsearch-specification](https://github.com/elastic/elasticsearch-specification) repository so they can be edited in this repo (e.g. to add `@server_transport_action` and extend the compiler to emit `serverTransportAction` in `schema.json`).

## Layout

- **`specification/`** — TypeScript API definitions (requests, responses, types). Edit these to add JSDoc tags such as `@server_transport_action`.
- **`compiler/`** — Node/TypeScript compiler that reads `specification/` and writes a single `schema.json` (types + endpoints).

## Generating schema.json

1. **Node**: Use Node 24 (see `.nvmrc` in the upstream repo or run `nvm use` if you have it).

2. **Install compiler dependencies** (from this repo root):
   ```bash
   cd elasticsearch-specification/compiler && npm install
   ```

3. **Run the compiler** (from this repo root):
   ```bash
   cd elasticsearch-specification/compiler && npm run generate-schema -- --spec ../specification --output ../output && cd ../..
   ```

4. **Use the generated schema**: Copy `elasticsearch-specification/output/schema/schema.json` to `rest-api-spec/src/main/resources/schema/schema.json` so the REST handler generator (and the rest of the build) use it.

## Note on OpenAPI transform

The upstream compiler depends on `compiler-wasm-lib` (Rust/WASM) for the `transform-to-openapi` script. That dependency is not included in this vendor copy, so `npm run transform-to-openapi` will not work here. Generating `schema.json` (for the REST handler codegen) does not require it.

## Upstream

Source: https://github.com/elastic/elasticsearch-specification  
Vendored for the REST handler codegen project; see `rest-handler-codegen-plan/` in this repo.

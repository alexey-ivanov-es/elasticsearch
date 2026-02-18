/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import org.elasticsearch.gradle.resthandler.model.Endpoint;
import org.elasticsearch.gradle.resthandler.model.TypeDefinition;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link SchemaParser} with sample schema.json fragments.
 */
public class SchemaParserTests {

    /**
     * Verifies that SchemaParser can parse the real schema.json format used by the generator.
     * The test resource mirrors the vendored schema at rest-api-spec/src/main/resources/schema/schema.json.
     */
    @Test
    public void parseRealSchemaFromResource() throws Exception {
        String resource = "org/elasticsearch/gradle/resthandler/schema.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull("Missing test resource: " + resource, in);
            Path schema = Files.createTempFile("schema", ".json");
            try {
                Files.write(schema, in.readAllBytes());
                ParsedSchema parsed = SchemaParser.parse(schema);
                assertNotNull(parsed.schema());
                assertNotNull(parsed.schema().types());
                assertNotNull(parsed.schema().endpoints());
                assertNotNull(parsed.typeByRef());
                // Current vendored schema is empty; type map and lists are empty
                assertEquals(0, parsed.schema().types().size());
                assertEquals(0, parsed.schema().endpoints().size());
                assertEquals(0, parsed.typeByRef().size());
            } finally {
                Files.deleteIfExists(schema);
            }
        }
    }

    @Test
    public void parseEmptySchema() throws Exception {
        Path schema = Files.createTempFile("schema", ".json");
        try {
            Files.writeString(
                schema,
                "{\"endpoints\":[],\"types\":[]}"
            );
            ParsedSchema parsed = SchemaParser.parse(schema);
            assertNotNull(parsed.schema());
            assertNotNull(parsed.schema().types());
            assertEquals(0, parsed.schema().types().size());
            assertNotNull(parsed.schema().endpoints());
            assertEquals(0, parsed.schema().endpoints().size());
            assertNotNull(parsed.typeByRef());
            assertEquals(0, parsed.typeByRef().size());
        } finally {
            Files.deleteIfExists(schema);
        }
    }

    @Test
    public void parseSchemaWithTypeAndEndpointResolvesRequestResponse() throws Exception {
        Path schema = Files.createTempFile("schema", ".json");
        try {
            Files.writeString(
                schema,
                """
                {
                  "types": [
                    {
                      "name": {"name": "DeleteIndexRequest", "namespace": "indices"},
                      "kind": "request",
                      "path": [{"name": "index", "required": true, "type": {"kind": "instance_of", "type": {"name": "Indices", "namespace": "_types"}}}],
                      "query": [],
                      "body": {"kind": "no_body"}
                    },
                    {
                      "name": {"name": "AcknowledgedResponse", "namespace": "_types"},
                      "kind": "interface",
                      "properties": []
                    }
                  ],
                  "endpoints": [
                    {
                      "name": "indices.delete",
                      "urls": [{"path": "/{index}", "methods": ["DELETE"]}],
                      "request": {"name": "DeleteIndexRequest", "namespace": "indices"},
                      "response": {"name": "AcknowledgedResponse", "namespace": "_types"},
                      "requestBodyRequired": false
                    }
                  ]
                }
                """
            );
            ParsedSchema parsed = SchemaParser.parse(schema);
            assertEquals(2, parsed.schema().types().size());
            assertEquals(1, parsed.schema().endpoints().size());
            assertEquals(2, parsed.typeByRef().size());

            TypeDefinition deleteReq = parsed.typeByRef().get("indices.DeleteIndexRequest");
            assertNotNull(deleteReq);
            assertNotNull(deleteReq.name());
            assertEquals("DeleteIndexRequest", deleteReq.name().name());
            assertEquals("indices", deleteReq.name().namespace());
            assertEquals("request", deleteReq.kind());

            TypeDefinition ackResp = parsed.typeByRef().get("_types.AcknowledgedResponse");
            assertNotNull(ackResp);
            assertNotNull(ackResp.name());
            assertEquals("AcknowledgedResponse", ackResp.name().name());

            Endpoint endpoint = parsed.schema().endpoints().get(0);
            assertEquals(deleteReq, parsed.getRequestType(endpoint));
            assertEquals(ackResp, parsed.getResponseType(endpoint));
        } finally {
            Files.deleteIfExists(schema);
        }
    }

    @Test
    public void typeKeyHandlesNullNamespace() {
        assertEquals(".Name", ParsedSchema.typeKey(null, "Name"));
        assertEquals("ns.Type", ParsedSchema.typeKey("ns", "Type"));
    }
}

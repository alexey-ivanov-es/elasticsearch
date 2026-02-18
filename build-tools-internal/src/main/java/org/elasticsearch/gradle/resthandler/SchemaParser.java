/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.elasticsearch.gradle.resthandler.model.Schema;
import org.elasticsearch.gradle.resthandler.model.TypeDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses schema.json into the data model and builds a type lookup map so that
 * each endpoint's request and response types can be resolved by {@code namespace.name}.
 */
public final class SchemaParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SchemaParser() {}

    /**
     * Parse the schema file and build the type map. The map keys are {@code namespace.name}.
     *
     * @param schemaPath path to schema.json
     * @return parsed schema and type lookup map
     */
    public static ParsedSchema parse(Path schemaPath) {
        Schema schema = readSchema(schemaPath);
        Map<String, TypeDefinition> typeByRef = buildTypeMap(schema);
        return new ParsedSchema(schema, typeByRef);
    }

    private static Schema readSchema(Path schemaPath) {
        try {
            return OBJECT_MAPPER.readValue(Files.newInputStream(schemaPath), Schema.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse schema at " + schemaPath, e);
        }
    }

    private static Map<String, TypeDefinition> buildTypeMap(Schema schema) {
        List<TypeDefinition> types = schema.types();
        Map<String, TypeDefinition> typeByRef = new HashMap<>(types == null ? 0 : types.size());
        if (types != null) {
            for (TypeDefinition t : types) {
                String key = ParsedSchema.typeKey(t.namespace(), t.name());
                typeByRef.put(key, t);
            }
        }
        return Map.copyOf(typeByRef);
    }
}

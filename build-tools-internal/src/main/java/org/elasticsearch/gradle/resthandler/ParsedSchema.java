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
import org.elasticsearch.gradle.resthandler.model.Schema;
import org.elasticsearch.gradle.resthandler.model.TypeDefinition;
import org.elasticsearch.gradle.resthandler.model.TypeReference;

import java.util.Map;

/**
 * Result of parsing schema.json: the raw schema plus a type lookup map for resolving
 * request/response type references. Keys are {@code namespace.name}.
 */
public record ParsedSchema(Schema schema, Map<String, TypeDefinition> typeByRef) {

    /**
     * Resolve an endpoint's request type by looking up its request type reference in the type map.
     */
    public TypeDefinition getRequestType(Endpoint endpoint) {
        return lookup(endpoint.request());
    }

    /**
     * Resolve an endpoint's response type by looking up its response type reference in the type map.
     */
    public TypeDefinition getResponseType(Endpoint endpoint) {
        return lookup(endpoint.response());
    }

    /**
     * Look up a type definition by its reference. Returns null if the reference is null or not found.
     */
    public TypeDefinition lookup(TypeReference ref) {
        if (ref == null) {
            return null;
        }
        return typeByRef.get(typeKey(ref.namespace(), ref.name()));
    }

    /**
     * Build the type map key for a given namespace and name. Used for lookups and when building the map.
     */
    public static String typeKey(String namespace, String name) {
        return (namespace != null ? namespace : "") + "." + (name != null ? name : "");
    }
}

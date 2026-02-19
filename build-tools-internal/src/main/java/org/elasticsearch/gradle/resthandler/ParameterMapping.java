/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

/**
 * Result of mapping a spec property (path/query param) to Java: type name,
 * extraction code fragment for {@code RestRequest}, and any special handling
 * (e.g. IndicesOptions group).
 */
public record ParameterMapping(String javaTypeName, String extractionCode, SpecialHandling specialHandling) {

    /**
     * How this parameter is extracted. {@link #INDICES_OPTIONS} means the param
     * is one of {@code expand_wildcards}, {@code ignore_unavailable},
     * {@code allow_no_indices} and should be handled via
     * {@code IndicesOptions.fromRequest(request, defaults)} as a group, not individually.
     */
    public enum SpecialHandling {
        NONE,
        INDICES_OPTIONS
    }

    /**
     * Create a mapping with no special handling.
     */
    public static ParameterMapping of(String javaTypeName, String extractionCode) {
        return new ParameterMapping(javaTypeName, extractionCode, SpecialHandling.NONE);
    }

    /**
     * Create a mapping for a parameter that is part of the IndicesOptions group.
     * The emitter should generate a single {@code IndicesOptions.fromRequest(...)} call.
     */
    public static ParameterMapping indicesOptions() {
        return new ParameterMapping("IndicesOptions", "", SpecialHandling.INDICES_OPTIONS);
    }
}

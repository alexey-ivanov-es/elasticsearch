/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.elasticsearch.gradle.resthandler.model.Property;
import org.elasticsearch.gradle.resthandler.model.TypeDescriptor;
import org.elasticsearch.gradle.resthandler.model.TypeReference;

import java.util.List;
import java.util.Set;

/**
 * Maps spec type descriptors to Java types and RestRequest parameter extraction
 * code. Implements the spec-type → Java mapping table (plan section 5) and the
 * IndicesOptions special case.
 */
public final class TypeMapper {

    private static final Set<String> INDICES_OPTIONS_PARAM_NAMES = Set.of(
        "expand_wildcards",
        "ignore_unavailable",
        "allow_no_indices"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().disable(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
    );

    private TypeMapper() {}

    /**
     * Map a query or path property to a Java type and extraction code. For
     * params that are part of the IndicesOptions group
     * ({@code expand_wildcards}, {@code ignore_unavailable}, {@code allow_no_indices}),
     * returns {@link ParameterMapping.SpecialHandling#INDICES_OPTIONS}; the emitter
     * should generate a single {@code IndicesOptions.fromRequest(request, defaults)}
     * call instead of per-param extraction.
     *
     * @param property       the path or query property
     * @param allQueryParams all query properties of the request (unused for now; for future use)
     * @return the mapping, or null if the type is not supported for parameter extraction
     */
    public static ParameterMapping mapProperty(Property property, List<Property> allQueryParams) {
        if (property == null || property.type() == null) {
            return null;
        }
        if (property.name() != null && INDICES_OPTIONS_PARAM_NAMES.contains(property.name())) {
            return ParameterMapping.indicesOptions();
        }
        return mapTypeDescriptor(property.type(), property.name(), property.serverDefault());
    }

    /**
     * Map a type descriptor to Java type and extraction code. Used for path and
     * query parameters. Does not apply IndicesOptions grouping (use
     * {@link #mapProperty(Property, List)} for that).
     *
     * @param type        the spec type descriptor
     * @param paramName   the parameter name (for the generated extraction expression)
     * @param serverDefault optional default from the spec, may be null
     * @return the mapping, or null if the type is not supported
     */
    public static ParameterMapping mapTypeDescriptor(TypeDescriptor type, String paramName, Object serverDefault) {
        if (type == null) {
            return null;
        }
        switch (type.kind() == null ? "" : type.kind()) {
            case "instance_of" -> {
                return mapInstanceOf(type.type(), paramName, serverDefault);
            }
            case "array_of" -> {
                TypeDescriptor elementType = resolveValueTypeDescriptor(type.value());
                if (elementType == null) {
                    return null;
                }
                ParameterMapping elementMapping = mapTypeDescriptor(elementType, paramName, serverDefault);
                if (elementMapping == null) {
                    return null;
                }
                if ("String".equals(elementMapping.javaTypeName())) {
                    return ParameterMapping.of(
                        "String[]",
                        "Strings.splitStringByCommaToArray(request.param(\"" + paramName + "\"))"
                    );
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static ParameterMapping mapInstanceOf(TypeReference typeRef, String paramName, Object serverDefault) {
        if (typeRef == null) {
            return null;
        }
        String namespace = typeRef.namespace() != null ? typeRef.namespace() : "";
        String name = typeRef.name() != null ? typeRef.name() : "";

        if ("_builtins".equals(namespace)) {
            return mapBuiltin(name, paramName, serverDefault);
        }
        if ("_types".equals(namespace)) {
            return mapTypes(name, paramName, serverDefault);
        }
        return null;
    }

    private static ParameterMapping mapBuiltin(String name, String paramName, Object serverDefault) {
        String defaultStr = defaultLiteral(serverDefault);
        return switch (name) {
            case "boolean" -> ParameterMapping.of(
                "boolean",
                "request.paramAsBoolean(\"" + paramName + "\", " + defaultStr + ")"
            );
            case "integer" -> ParameterMapping.of(
                "int",
                "request.paramAsInt(\"" + paramName + "\", " + defaultStr + ")"
            );
            case "long" -> ParameterMapping.of(
                "long",
                "request.paramAsLong(\"" + paramName + "\", " + defaultStr + ")"
            );
            case "float" -> ParameterMapping.of(
                "float",
                "Float.parseFloat(request.param(\"" + paramName + "\"))"
            );
            case "double" -> ParameterMapping.of(
                "double",
                "Double.parseDouble(request.param(\"" + paramName + "\"))"
            );
            case "string" -> ParameterMapping.of(
                "String",
                "request.param(\"" + paramName + "\")"
            );
            default -> null;
        };
    }

    private static ParameterMapping mapTypes(String name, String paramName, Object serverDefault) {
        String defaultStr = defaultLiteral(serverDefault);
        return switch (name) {
            case "Duration" -> ParameterMapping.of(
                "TimeValue",
                "request.paramAsTime(\"" + paramName + "\", " + defaultStr + ")"
            );
            case "ByteSize" -> ParameterMapping.of(
                "ByteSizeValue",
                "request.paramAsSize(\"" + paramName + "\", " + defaultStr + ")"
            );
            case "IndexName", "Name", "Id", "Routing" -> ParameterMapping.of(
                "String",
                "request.param(\"" + paramName + "\")"
            );
            case "Indices", "Names", "Ids" -> ParameterMapping.of(
                "String[]",
                "Strings.splitStringByCommaToArray(request.param(\"" + paramName + "\"))"
            );
            case "ExpandWildcards" -> ParameterMapping.of(
                "ExpandWildcards",
                "ExpandWildcards.fromString(request.param(\"" + paramName + "\"))"
            );
            case "WaitForActiveShards" -> ParameterMapping.of(
                "ActiveShardCount",
                "ActiveShardCount.parseString(request.param(\"" + paramName + "\"))"
            );
            case "VersionType" -> ParameterMapping.of(
                "VersionType",
                "VersionType.fromString(request.param(\"" + paramName + "\"))"
            );
            case "Refresh" -> ParameterMapping.of(
                "String",
                "request.param(\"" + paramName + "\")"
            );
            case "Scroll" -> ParameterMapping.of(
                "Scroll",
                "new Scroll(request.paramAsTime(\"" + paramName + "\", " + defaultStr + "))"
            );
            default -> ParameterMapping.of(
                "String",
                "request.param(\"" + paramName + "\")"
            );
        };
    }

    private static String defaultLiteral(Object serverDefault) {
        if (serverDefault == null) {
            return "null";
        }
        if (serverDefault instanceof Boolean b) {
            return String.valueOf(b);
        }
        if (serverDefault instanceof Number n) {
            return n.toString();
        }
        if (serverDefault instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return "null";
    }

    /**
     * Resolve {@code value} from an array_of type descriptor to a TypeDescriptor.
     * Jackson deserializes nested objects as Map, so we convert when needed.
     */
    private static TypeDescriptor resolveValueTypeDescriptor(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof TypeDescriptor td) {
            return td;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return OBJECT_MAPPER.convertValue(map, TypeDescriptor.class);
        }
        return null;
    }
}

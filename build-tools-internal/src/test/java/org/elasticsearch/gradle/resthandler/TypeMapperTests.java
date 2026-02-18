/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import org.elasticsearch.gradle.resthandler.model.Property;
import org.elasticsearch.gradle.resthandler.model.TypeDescriptor;
import org.elasticsearch.gradle.resthandler.model.TypeReference;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link TypeMapper}.
 */
public class TypeMapperTests {

    @Test
    public void mapPropertyNullPropertyReturnsNull() {
        assertNull(TypeMapper.mapProperty(null, List.of()));
    }

    @Test
    public void mapPropertyNullTypeReturnsNull() {
        Property p = new Property("foo", false, null, null, null);
        assertNull(TypeMapper.mapProperty(p, List.of()));
    }

    @Test
    public void mapBuiltinBoolean() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("boolean", "_builtins"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "flag", null);
        assertNotNull(m);
        assertEquals("boolean", m.javaTypeName());
        assertEquals("request.paramAsBoolean(\"flag\", null)", m.extractionCode());
        assertEquals(ParameterMapping.SpecialHandling.NONE, m.specialHandling());
    }

    @Test
    public void mapBuiltinBooleanWithDefault() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("boolean", "_builtins"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "flag", true);
        assertNotNull(m);
        assertEquals("request.paramAsBoolean(\"flag\", true)", m.extractionCode());
    }

    @Test
    public void mapBuiltinInteger() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("integer", "_builtins"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "size", 10);
        assertNotNull(m);
        assertEquals("int", m.javaTypeName());
        assertEquals("request.paramAsInt(\"size\", 10)", m.extractionCode());
    }

    @Test
    public void mapBuiltinLong() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("long", "_builtins"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "version", null);
        assertNotNull(m);
        assertEquals("long", m.javaTypeName());
        assertEquals("request.paramAsLong(\"version\", null)", m.extractionCode());
    }

    @Test
    public void mapBuiltinString() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("string", "_builtins"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "q", null);
        assertNotNull(m);
        assertEquals("String", m.javaTypeName());
        assertEquals("request.param(\"q\")", m.extractionCode());
    }

    @Test
    public void mapTypesDuration() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("Duration", "_types"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "timeout", "30s");
        assertNotNull(m);
        assertEquals("TimeValue", m.javaTypeName());
        assertEquals("request.paramAsTime(\"timeout\", \"30s\")", m.extractionCode());
    }

    @Test
    public void mapTypesIndexName() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("IndexName", "_types"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "index", null);
        assertNotNull(m);
        assertEquals("String", m.javaTypeName());
        assertEquals("request.param(\"index\")", m.extractionCode());
    }

    @Test
    public void mapTypesIndicesAsArray() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("Indices", "_types"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "index", null);
        assertNotNull(m);
        assertEquals("String[]", m.javaTypeName());
        assertEquals("Strings.splitStringByCommaToArray(request.param(\"index\"))", m.extractionCode());
    }

    @Test
    public void mapPropertyExpandWildcardsReturnsIndicesOptions() {
        Property p = new Property(
            "expand_wildcards",
            false,
            new TypeDescriptor("instance_of", new TypeReference("ExpandWildcards", "_types"), null, null, null),
            null,
            null
        );
        ParameterMapping m = TypeMapper.mapProperty(p, List.of());
        assertNotNull(m);
        assertEquals(ParameterMapping.SpecialHandling.INDICES_OPTIONS, m.specialHandling());
        assertEquals("IndicesOptions", m.javaTypeName());
    }

    @Test
    public void mapPropertyIgnoreUnavailableReturnsIndicesOptions() {
        Property p = new Property(
            "ignore_unavailable",
            false,
            new TypeDescriptor("instance_of", new TypeReference("boolean", "_builtins"), null, null, null),
            null,
            null
        );
        ParameterMapping m = TypeMapper.mapProperty(p, List.of());
        assertNotNull(m);
        assertEquals(ParameterMapping.SpecialHandling.INDICES_OPTIONS, m.specialHandling());
    }

    @Test
    public void mapPropertyAllowNoIndicesReturnsIndicesOptions() {
        Property p = new Property(
            "allow_no_indices",
            false,
            new TypeDescriptor("instance_of", new TypeReference("boolean", "_builtins"), null, null, null),
            null,
            null
        );
        ParameterMapping m = TypeMapper.mapProperty(p, List.of());
        assertNotNull(m);
        assertEquals(ParameterMapping.SpecialHandling.INDICES_OPTIONS, m.specialHandling());
    }

    @Test
    public void mapPropertyNonIndicesOptionsParamUsesTypeMapping() {
        Property p = new Property(
            "timeout",
            false,
            new TypeDescriptor("instance_of", new TypeReference("Duration", "_types"), null, null, null),
            "30s",
            null
        );
        ParameterMapping m = TypeMapper.mapProperty(p, List.of());
        assertNotNull(m);
        assertEquals(ParameterMapping.SpecialHandling.NONE, m.specialHandling());
        assertEquals("TimeValue", m.javaTypeName());
        assertEquals("request.paramAsTime(\"timeout\", \"30s\")", m.extractionCode());
    }

    @Test
    public void mapTypesVersionType() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("VersionType", "_types"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "version_type", null);
        assertNotNull(m);
        assertEquals("VersionType", m.javaTypeName());
        assertEquals("VersionType.fromString(request.param(\"version_type\"))", m.extractionCode());
    }

    @Test
    public void mapTypesWaitForActiveShards() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("WaitForActiveShards", "_types"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "wait_for_active_shards", null);
        assertNotNull(m);
        assertEquals("ActiveShardCount", m.javaTypeName());
        assertEquals("ActiveShardCount.parseString(request.param(\"wait_for_active_shards\"))", m.extractionCode());
    }

    @Test
    public void mapTypesByteSize() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("ByteSize", "_types"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "size", null);
        assertNotNull(m);
        assertEquals("ByteSizeValue", m.javaTypeName());
        assertEquals("request.paramAsSize(\"size\", null)", m.extractionCode());
    }

    @Test
    public void mapArrayOfString() {
        TypeDescriptor elementType = new TypeDescriptor("instance_of", new TypeReference("string", "_builtins"), null, null, null);
        TypeDescriptor arrayType = new TypeDescriptor("array_of", null, elementType, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(arrayType, "index", null);
        assertNotNull(m);
        assertEquals("String[]", m.javaTypeName());
        assertEquals("Strings.splitStringByCommaToArray(request.param(\"index\"))", m.extractionCode());
    }

    @Test
    public void mapInstanceOfUnknownBuiltinReturnsNull() {
        TypeDescriptor type = new TypeDescriptor("instance_of", new TypeReference("unknown", "_builtins"), null, null, null);
        ParameterMapping m = TypeMapper.mapTypeDescriptor(type, "x", null);
        assertNull(m);
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A type definition from the schema {@code types} array. Structure depends on {@code kind}:
 * <ul>
 *   <li>{@code request} — path/query params and body; has path, query, body, inherits</li>
 *   <li>{@code interface} or similar — has properties, optional inherits, optional generics</li>
 *   <li>{@code enum} — has members (array of enum values)</li>
 * </ul>
 * Unused fields are null.
 */
public record TypeDefinition(
    @JsonProperty("name") String name,
    @JsonProperty("namespace") String namespace,
    @JsonProperty("kind") String kind,
    @JsonProperty("inherits") TypeReference inherits,
    @JsonProperty("path") List<Property> path,
    @JsonProperty("query") List<Property> query,
    @JsonProperty("body") Body body,
    @JsonProperty("properties") List<Property> properties,
    @JsonProperty("generics") List<?> generics,
    @JsonProperty("members") List<EnumMember> members
) {}

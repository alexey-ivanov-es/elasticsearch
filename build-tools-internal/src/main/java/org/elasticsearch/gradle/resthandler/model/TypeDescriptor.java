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
 * Type descriptor from the spec. The {@code kind} field determines which other fields are present.
 * <ul>
 *   <li>{@code instance_of} — {@code type}: reference to a named type</li>
 *   <li>{@code array_of} — {@code value}: element type descriptor (use {@link #value()})</li>
 *   <li>{@code dictionary_of} — {@code key}, {@code value}: key and value type descriptors</li>
 *   <li>{@code union_of} — {@code items}: list of alternative type descriptors</li>
 *   <li>{@code literal_value} — {@code value}: constant (use {@link #literalValue()})</li>
 * </ul>
 */
public record TypeDescriptor(
    @JsonProperty("kind") String kind,
    @JsonProperty("type") TypeReference type,
    @JsonProperty("value") TypeDescriptor value,
    @JsonProperty("key") TypeDescriptor key,
    @JsonProperty("items") List<TypeDescriptor> items,
    @JsonProperty("literalValue") Object literalValue
) {}

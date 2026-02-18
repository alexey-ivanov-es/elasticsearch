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
 * Request body definition. The {@code kind} field determines which other fields are present.
 * <ul>
 *   <li>{@code no_body} — no request body (typical for GET/DELETE)</li>
 *   <li>{@code value} — body is a single value; {@code value} is its type descriptor</li>
 *   <li>{@code properties} — body is a JSON object; {@code properties} lists its fields</li>
 * </ul>
 */
public record Body(
    @JsonProperty("kind") String kind,
    @JsonProperty("value") TypeDescriptor value,
    @JsonProperty("properties") List<Property> properties
) {}

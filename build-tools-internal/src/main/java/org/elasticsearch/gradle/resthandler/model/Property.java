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

/**
 * A named property on a type: path/query parameter or body field.
 * Has name, required flag, type descriptor, optional server default, and description.
 */
public record Property(
    @JsonProperty("name") String name,
    @JsonProperty("required") boolean required,
    @JsonProperty("type") TypeDescriptor type,
    @JsonProperty("serverDefault") Object serverDefault,
    @JsonProperty("description") String description
) {}

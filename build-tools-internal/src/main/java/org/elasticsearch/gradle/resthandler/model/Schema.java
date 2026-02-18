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
 * Top-level schema structure: the compiled API specification.
 * Contains all type definitions and all REST endpoints.
 */
public record Schema(@JsonProperty("types") List<TypeDefinition> types, @JsonProperty("endpoints") List<Endpoint> endpoints) {}

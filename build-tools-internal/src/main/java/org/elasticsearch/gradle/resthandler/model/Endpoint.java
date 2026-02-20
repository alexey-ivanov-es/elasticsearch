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
 * A REST endpoint from the schema: name, URLs, request/response type references,
 * body requirement, availability, and optional server-side extension fields.
 */
public record Endpoint(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("urls") List<UrlPattern> urls,
    @JsonProperty("stability") String stability,
    @JsonProperty("request") TypeReference request,
    @JsonProperty("response") TypeReference response,
    @JsonProperty("requestBodyRequired") boolean requestBodyRequired,
    @JsonProperty("availability") Availability availability,
    @JsonProperty("serverTransportAction") String serverTransportAction,
    @JsonProperty("capabilities") List<String> capabilities,
    @JsonProperty("allowSystemIndexAccess") Boolean allowSystemIndexAccess,
    @JsonProperty("responseParams") List<String> responseParams
) {}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action;

import org.elasticsearch.rest.RestStatus;

/**
 * Implemented by action response types that supply a REST status for the HTTP response.
 * When the code generator produces a REST handler for an endpoint whose response type
 * implements this interface, it uses {@code RestToXContentListener<>(channel, ResponseType::status)}
 * so that the returned status is used for the response.
 */
public interface RestStatusProvider {

    /**
     * The REST status for the HTTP response (e.g. 200 OK, 201 CREATED, 404 NOT_FOUND).
     */
    RestStatus status();
}

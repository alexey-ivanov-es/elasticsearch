/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.core.Releasable;

/**
 * Marker interface for {@link ActionRequest}s whose body/source is a
 * {@link Releasable} that must be released when the response is sent.
 * When a generated REST handler's request type implements this interface,
 * the generator wraps the response listener with
 * {@code ActionListener.withRef(listener, request.getSourceForRelease())}
 * so that the source is released on completion.
 */
public interface ReleasableSourceRequest {

    /**
     * Returns the releasable source to release when the request completes
     * (success or failure), or {@code null} if there is nothing to release.
     */
    Releasable getSourceForRelease();
}

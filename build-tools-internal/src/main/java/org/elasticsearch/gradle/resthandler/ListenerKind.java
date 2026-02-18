/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

/**
 * Kind of REST response listener to use when generating a handler, based on
 * the ActionResponse type (chunked, nodes envelope, status-bearing, or default ToXContent).
 */
public enum ListenerKind {
    /** Chunked streaming response; use RestRefCountedChunkedToXContentListener. */
    CHUNKED,
    /** Nodes API response; use RestActions.NodesResponseRestListener. */
    NODES,
    /** Status-bearing response (e.g. document CRUD); use RestStatusToXContentListener. */
    STATUS,
    /** Default ToXContent response; use RestToXContentListener. */
    DEFAULT
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import com.squareup.javapoet.ClassName;

/**
 * Supported REST response listener types for generated handlers. Each constant
 * carries the package and class name so the emitter can build a JavaPoet
 * {@link ClassName} without parsing strings.
 */
public enum RestListenerType {

    /** Chunked streaming; {@code RestRefCountedChunkedToXContentListener}. */
    CHUNKED("org.elasticsearch.rest.action", "RestRefCountedChunkedToXContentListener", null),

    /** Nodes API envelope; {@code RestActions.NodesResponseRestListener}. */
    NODES("org.elasticsearch.rest.action", "RestActions", "NodesResponseRestListener"),

    /** Status-bearing (e.g. document CRUD); {@code RestStatusToXContentListener}. */
    STATUS("org.elasticsearch.rest.action", "RestStatusToXContentListener", null),

    /** Default ToXContent; {@code RestToXContentListener}. */
    DEFAULT("org.elasticsearch.rest.action", "RestToXContentListener", null);

    private final String packageName;
    private final String primaryClass;
    private final String innerClass;

    RestListenerType(String packageName, String primaryClass, String innerClass) {
        this.packageName = packageName;
        this.primaryClass = primaryClass;
        this.innerClass = innerClass;
    }

    /**
     * Return the JavaPoet class name for this listener (top-level or inner class).
     */
    public ClassName getClassName() {
        if (innerClass == null) {
            return ClassName.get(packageName, primaryClass);
        }
        return ClassName.get(packageName, primaryClass).nestedClass(innerClass);
    }

    /**
     * True for status-bearing listeners that require a method reference (e.g. {@code Response::status}).
     */
    public boolean needsStatusMethodReference() {
        return this == STATUS;
    }
}

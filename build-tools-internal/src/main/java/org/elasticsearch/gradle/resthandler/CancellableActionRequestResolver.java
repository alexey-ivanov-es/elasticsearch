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
 * Resolves whether an ActionRequest type should use {@code RestCancellableNodeClient}
 * when dispatching from a generated REST handler. Uses the request class's ClassLoader
 * to load the marker interface by name so that build-tools-internal does not depend
 * on the server module.
 */
public final class CancellableActionRequestResolver {

    private static final String CANCELLABLE_ACTION_REQUEST = "org.elasticsearch.rest.action.CancellableActionRequest";

    private CancellableActionRequestResolver() {}

    /**
     * Return true if the given request class implements {@code CancellableActionRequest},
     * so the generated handler should wrap the client in {@code RestCancellableNodeClient}.
     */
    public static boolean isCancellable(Class<?> requestClass) {
        ClassLoader loader = requestClass.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        try {
            Class<?> marker = loader.loadClass(CANCELLABLE_ACTION_REQUEST);
            return marker.isAssignableFrom(requestClass);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

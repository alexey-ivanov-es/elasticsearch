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
 * Resolves whether an ActionRequest type implements {@code ReleasableSourceRequest},
 * so the generated REST handler should wrap the response listener with
 * {@code ActionListener.withRef(listener, request.getSourceForRelease())}.
 * Uses the request class's ClassLoader to load the interface by name so that
 * build-tools-internal does not depend on the server module.
 */
public final class ReleasableSourceRequestResolver {

    private static final String RELEASABLE_SOURCE_REQUEST = "org.elasticsearch.rest.action.ReleasableSourceRequest";

    private ReleasableSourceRequestResolver() {}

    /**
     * Return true if the given request class implements {@code ReleasableSourceRequest},
     * so the generated handler should wrap the listener with {@code ActionListener.withRef}.
     */
    public static boolean hasReleasableSource(Class<?> requestClass) {
        ClassLoader loader = requestClass.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        try {
            Class<?> marker = loader.loadClass(RELEASABLE_SOURCE_REQUEST);
            return marker.isAssignableFrom(requestClass);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

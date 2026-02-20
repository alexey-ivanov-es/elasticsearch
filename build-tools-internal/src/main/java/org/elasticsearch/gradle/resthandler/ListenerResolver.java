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
 * Resolves which REST response listener type to use for a given ActionResponse
 * class by inspecting its type hierarchy. Uses the response class's ClassLoader
 * to load server types by name so that build-tools-internal does not depend on
 * the server module.
 * <p>
 * Priority order: ChunkedToXContentObject → BaseNodesResponse →
 * RestStatusProvider → default (RestToXContentListener).
 */
public final class ListenerResolver {

    private static final String CHUNKED_TO_XCONTENT_OBJECT = "org.elasticsearch.common.xcontent.ChunkedToXContentObject";
    private static final String BASE_NODES_RESPONSE = "org.elasticsearch.action.support.nodes.BaseNodesResponse";
    private static final String REST_STATUS_PROVIDER = "org.elasticsearch.rest.action.RestStatusProvider";

    private ListenerResolver() {}

    /**
     * Resolve the listener type for the given ActionResponse class.
     *
     * @param responseClass the ActionResponse class (e.g. from {@link ResolvedTransportAction#responseClass()})
     * @return the listener type (package and class name are on the enum)
     */
    public static RestListenerType resolve(Class<?> responseClass) {
        ClassLoader loader = responseClass.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }

        if (isAssignableFrom(responseClass, CHUNKED_TO_XCONTENT_OBJECT, loader)) {
            return RestListenerType.CHUNKED;
        }
        if (isAssignableFrom(responseClass, BASE_NODES_RESPONSE, loader)) {
            return RestListenerType.NODES;
        }
        if (isAssignableFrom(responseClass, REST_STATUS_PROVIDER, loader)) {
            return RestListenerType.STATUS;
        }
        return RestListenerType.DEFAULT;
    }

    private static boolean isAssignableFrom(Class<?> responseClass, String typeName, ClassLoader loader) {
        try {
            Class<?> type = loader.loadClass(typeName);
            return type.isAssignableFrom(responseClass);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

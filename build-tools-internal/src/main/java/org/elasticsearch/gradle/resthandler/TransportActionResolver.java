/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Resolves the ActionRequest and ActionResponse types for a transport action class
 * by walking its generic superclass chain until {@code TransportAction<Request, Response>}
 * (or a subclass) is found and type arguments are concrete.
 */
public final class TransportActionResolver {

    private static final String TRANSPORT_ACTION_CLASS = "org.elasticsearch.action.support.TransportAction";

    private TransportActionResolver() {}

    /**
     * Resolve the transport action class and its request/response types using the given classpath.
     *
     * @param transportActionClassName fully-qualified name of the transport action class
     * @param classpath                classpath (e.g. server compile classpath) as files
     * @return the transport action class and its request and response classes
     * @throws IllegalArgumentException if the class cannot be loaded or type parameters cannot be resolved
     */
    public static ResolvedTransportAction resolve(String transportActionClassName, Iterable<File> classpath) {
        URL[] urls = StreamSupport.stream(classpath.spliterator(), false)
            .filter(File::exists)
            .map(TransportActionResolver::toUrl)
            .toArray(URL[]::new);
        URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        try {
            Class<?> actionClass = loader.loadClass(transportActionClassName);
            Class<?> transportActionBase = loader.loadClass(TRANSPORT_ACTION_CLASS);
            Map<TypeVariable<?>, Type> bindings = new HashMap<>();
            Class<?> current = actionClass;
            while (current != null && transportActionBase.isAssignableFrom(current)) {
                Type genericSuper = current.getGenericSuperclass();
                if (genericSuper instanceof ParameterizedType pt) {
                    Class<?> raw = (Class<?>) pt.getRawType();
                    if (transportActionBase.isAssignableFrom(raw)) {
                        Type[] typeParams = raw.getTypeParameters();
                        Type[] actualArgs = pt.getActualTypeArguments();
                        for (int i = 0; i < Math.min(typeParams.length, actualArgs.length); i++) {
                            if (typeParams[i] instanceof TypeVariable<?> tv) {
                                bindings.put(tv, actualArgs[i]);
                            }
                        }
                        if (typeParams.length >= 2 && actualArgs.length >= 2) {
                            Class<?> requestClass = resolveToClass(actualArgs[0], bindings, loader);
                            Class<?> responseClass = resolveToClass(actualArgs[1], bindings, loader);
                            if (requestClass != null && responseClass != null) {
                                try {
                                    loader.close();
                                } catch (IOException ignored) {
                                }
                                return new ResolvedTransportAction(actionClass, requestClass, responseClass);
                            }
                        }
                    }
                    current = raw;
                } else {
                    current = current.getSuperclass();
                }
            }
            try {
                loader.close();
            } catch (IOException ignored) {
            }
        } catch (ClassNotFoundException e) {
            try {
                loader.close();
            } catch (IOException ignored) {
            }
            throw new IllegalArgumentException("Could not load class: " + transportActionClassName + ", or " + TRANSPORT_ACTION_CLASS, e);
        }
        try {
            loader.close();
        } catch (IOException ignored) {
        }
        throw new IllegalArgumentException(
            "Could not resolve Request/Response type parameters for " + transportActionClassName
        );
    }

    private static Class<?> resolveToClass(Type type, Map<TypeVariable<?>, Type> bindings, ClassLoader loader) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof TypeVariable<?> tv) {
            Type bound = bindings.get(tv);
            if (bound != null) {
                return resolveToClass(bound, bindings, loader);
            }
        }
        if (type instanceof ParameterizedType pt) {
            return resolveToClass(pt.getRawType(), bindings, loader);
        }
        return null;
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Invalid classpath entry: " + file, e);
        }
    }
}

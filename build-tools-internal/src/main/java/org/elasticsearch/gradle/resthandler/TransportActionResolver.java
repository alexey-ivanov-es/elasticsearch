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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
     * Resolve the transport action class and its request/response types using the given classloader.
     * The caller is responsible for closing the classloader when done.
     *
     * @param transportActionClassName fully-qualified name of the transport action class
     * @param classLoader              classloader that can load the transport action and its dependencies
     * @return the transport action class and its request and response classes
     * @throws IllegalArgumentException if the class cannot be loaded or type parameters cannot be resolved
     */
    public static ResolvedTransportAction resolve(String transportActionClassName, ClassLoader classLoader) {
        try {
            Class<?> actionClass = classLoader.loadClass(transportActionClassName);
            Class<?> transportActionBase = classLoader.loadClass(TRANSPORT_ACTION_CLASS);
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
                            Class<?> requestClass = resolveToClass(actualArgs[0], bindings, classLoader);
                            Class<?> responseClass = resolveToClass(actualArgs[1], bindings, classLoader);
                            if (requestClass != null && responseClass != null) {
                                Object[] actionTypeRef = resolveActionTypeReference(actionClass, classLoader);
                                return new ResolvedTransportAction(
                                    actionClass,
                                    requestClass,
                                    responseClass,
                                    (Class<?>) actionTypeRef[0],
                                    (String) actionTypeRef[1]
                                );
                            }
                        }
                    }
                    current = raw;
                } else {
                    current = current.getSuperclass();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not load class: " + transportActionClassName + ", or " + TRANSPORT_ACTION_CLASS, e);
        }
        throw new IllegalArgumentException("Could not resolve Request/Response type parameters for " + transportActionClassName);
    }

    /**
     * Resolve the transport action class and its request/response types using the given classpath.
     * Creates and closes a URLClassLoader internally.
     *
     * @param transportActionClassName fully-qualified name of the transport action class
     * @param classpath                classpath (e.g. server compile classpath) as files
     * @return the transport action class and its request and response classes
     * @throws IllegalArgumentException if the class cannot be loaded, type parameters cannot be resolved,
     *                                   or closing the classloader fails
     */
    public static ResolvedTransportAction resolve(String transportActionClassName, Iterable<File> classpath) {
        URL[] urls = StreamSupport.stream(classpath.spliterator(), false)
            .filter(File::exists)
            .map(TransportActionResolver::toUrl)
            .toArray(URL[]::new);
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            return resolve(transportActionClassName, loader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to close classloader", e);
        }
    }

    /**
     * Resolve the class and field to use for client.execute(ActionType, request, listener).
     * Returns either (transportActionClass, "TYPE") or (derived Action class, "INSTANCE").
     */
    private static Object[] resolveActionTypeReference(Class<?> transportActionClass, ClassLoader classLoader)
        throws ClassNotFoundException {
        try {
            Field typeField = transportActionClass.getField("TYPE");
            if (typeField != null && Modifier.isStatic(typeField.getModifiers()) && Modifier.isPublic(typeField.getModifiers())) {
                return new Object[] { transportActionClass, "TYPE" };
            }
        } catch (NoSuchFieldException ignored) {
            // fall through to INSTANCE
        }
        String pkg = transportActionClass.getPackageName();
        String simple = transportActionClass.getSimpleName();
        if (simple.startsWith("Transport") && simple.length() > "Transport".length()) {
            String actionSimple = simple.substring("Transport".length());
            Class<?> actionClass = classLoader.loadClass(pkg + "." + actionSimple);
            return new Object[] { actionClass, "INSTANCE" };
        }
        throw new IllegalArgumentException("Could not resolve action type reference for " + transportActionClass.getName());
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

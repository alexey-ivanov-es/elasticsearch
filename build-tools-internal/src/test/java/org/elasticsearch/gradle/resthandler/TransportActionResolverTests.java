/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

/**
 * Unit tests for {@link TransportActionResolver}.
 */
public class TransportActionResolverTests {

    @Test
    public void resolveThrowsWhenClasspathEmpty() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> TransportActionResolver.resolve(
                "org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction",
                Collections.emptyList()
            )
        );
        assertNotNull(e.getCause());
    }

    @Test
    public void resolveThrowsWhenClassNotFound() {
        List<File> classpath = List.of(new File("/nonexistent"));
        assumeTrue("Only run when /nonexistent does not exist", !classpath.get(0).exists());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransportActionResolver.resolve("org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction", classpath)
        );
    }

    /**
     * Resolves TransportDeleteIndexAction when the server is on the classpath (e.g. testRuntimeOnly project(':server')).
     * Skips if the action class is not loadable.
     */
    @Test
    public void resolveTransportDeleteIndexActionWhenServerOnClasspath() {
        ClassLoader cl = getClass().getClassLoader();
        assumeTrue("Need URLClassLoader to get classpath", cl instanceof URLClassLoader);
        URL[] urls = ((URLClassLoader) cl).getURLs();
        List<File> classpath = java.util.Arrays.stream(urls)
            .map(u -> {
                try {
                    return new File(u.toURI());
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(f -> f != null && f.exists())
            .toList();
        assumeTrue("Need non-empty classpath", !classpath.isEmpty());
        try {
            cl.loadClass("org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction");
        } catch (ClassNotFoundException e) {
            assumeTrue("Server not on classpath, skip integration check", false);
        }
        ResolvedTransportAction resolved = TransportActionResolver.resolve(
            "org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction",
            classpath
        );
        assertNotNull(resolved);
        assertEquals("org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction", resolved.transportActionClass().getName());
        assertEquals("org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest", resolved.requestClass().getName());
        assertEquals("org.elasticsearch.action.support.master.AcknowledgedResponse", resolved.responseClass().getName());
        assertEquals("org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction", resolved.actionTypeReferenceClass().getName());
        assertEquals("TYPE", resolved.actionTypeReferenceField());
    }
}

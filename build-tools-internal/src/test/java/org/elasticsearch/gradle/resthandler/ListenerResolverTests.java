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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Unit tests for {@link ListenerResolver}.
 */
public class ListenerResolverTests {

    /**
     * A plain class that does not implement any server response marker type;
     * resolver should return DEFAULT.
     */
    public static final class PlainResponse {}

    @Test
    public void resolvePlainClassReturnsDefault() {
        ResolvedListener resolved = ListenerResolver.resolve(PlainResponse.class);
        assertNotNull(resolved);
        assertEquals(ListenerKind.DEFAULT, resolved.kind());
        assertEquals("org.elasticsearch.rest.action.RestToXContentListener", resolved.listenerClassName());
    }

    @Test
    public void resolveReturnsExpectedListenerClassNames() {
        ResolvedListener resolved = ListenerResolver.resolve(PlainResponse.class);
        assertEquals("org.elasticsearch.rest.action.RestToXContentListener", resolved.listenerClassName());
    }

    /**
     * When server is on the classpath, ChunkedToXContentObject responses (e.g. SearchResponse)
     * resolve to CHUNKED and RestRefCountedChunkedToXContentListener.
     */
    @Test
    public void resolveSearchResponseWhenServerOnClasspath() {
        ClassLoader cl = getClass().getClassLoader();
        try {
            Class<?> searchResponse = cl.loadClass("org.elasticsearch.action.search.SearchResponse");
            ResolvedListener resolved = ListenerResolver.resolve(searchResponse);
            assertNotNull(resolved);
            assertEquals(ListenerKind.CHUNKED, resolved.kind());
            assertEquals("org.elasticsearch.rest.action.RestRefCountedChunkedToXContentListener", resolved.listenerClassName());
        } catch (ClassNotFoundException e) {
            assumeTrue("Server not on classpath, skip", false);
        }
    }

    /**
     * When server is on the classpath, BaseNodesResponse subclasses (e.g. NodesInfoResponse)
     * resolve to NODES and RestActions.NodesResponseRestListener.
     */
    @Test
    public void resolveNodesInfoResponseWhenServerOnClasspath() {
        ClassLoader cl = getClass().getClassLoader();
        try {
            Class<?> nodesInfoResponse = cl.loadClass("org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse");
            ResolvedListener resolved = ListenerResolver.resolve(nodesInfoResponse);
            assertNotNull(resolved);
            assertEquals(ListenerKind.NODES, resolved.kind());
            assertEquals("org.elasticsearch.rest.action.RestActions.NodesResponseRestListener", resolved.listenerClassName());
        } catch (ClassNotFoundException e) {
            assumeTrue("Server not on classpath, skip", false);
        }
    }

    /**
     * When server is on the classpath, a response that is neither chunked nor nodes
     * (e.g. AcknowledgedResponse) resolves to DEFAULT.
     */
    @Test
    public void resolveAcknowledgedResponseWhenServerOnClasspath() {
        ClassLoader cl = getClass().getClassLoader();
        try {
            Class<?> ackResponse = cl.loadClass("org.elasticsearch.action.support.master.AcknowledgedResponse");
            ResolvedListener resolved = ListenerResolver.resolve(ackResponse);
            assertNotNull(resolved);
            assertEquals(ListenerKind.DEFAULT, resolved.kind());
            assertEquals("org.elasticsearch.rest.action.RestToXContentListener", resolved.listenerClassName());
        } catch (ClassNotFoundException e) {
            assumeTrue("Server not on classpath, skip", false);
        }
    }
}

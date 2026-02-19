/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import org.elasticsearch.gradle.resthandler.model.Availability;
import org.elasticsearch.gradle.resthandler.model.AvailabilityDetail;
import org.elasticsearch.gradle.resthandler.model.Endpoint;
import org.elasticsearch.gradle.resthandler.model.Property;
import org.elasticsearch.gradle.resthandler.model.TypeDefinition;
import org.elasticsearch.gradle.resthandler.model.TypeReference;
import org.elasticsearch.gradle.resthandler.model.UrlPattern;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link HandlerCodeEmitter}.
 */
public class HandlerCodeEmitterTests {

    /**
     * Dummy classes used as stand-ins for transport action, request, and response
     * so we can test emission without the server classpath.
     */
    public static final class FakeTransportAction {}

    public static final class FakeRequest {}

    public static final class FakeResponse {}

    private static Endpoint endpoint(
        String name,
        List<UrlPattern> urls,
        Availability availability
    ) {
        return new Endpoint(
            name,
            "description",
            urls,
            "stable",
            new TypeReference("DeleteIndexRequest", "indices"),
            new TypeReference("AcknowledgedResponse", "indices"),
            false,
            availability,
            "org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction"
        );
    }

    @Test
    public void emitProducesNonEmptyJavaFile() {
        Endpoint endpoint = endpoint(
            "indices.delete",
            List.of(new UrlPattern("/{index}", List.of("DELETE"))),
            null
        );
        ResolvedTransportAction resolved = new ResolvedTransportAction(
            FakeTransportAction.class,
            FakeRequest.class,
            FakeResponse.class,
            FakeTransportAction.class,
            "TYPE"
        );
        RestListenerType listenerType = RestListenerType.DEFAULT;

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType);

        assertNotNull(javaFile);
        String source = javaFile.toString();
        assertTrue("Generated source should contain handler class name", source.contains("RestIndicesDeleteAction"));
        assertTrue("Generated source should extend BaseRestHandler", source.contains("BaseRestHandler"));
        assertTrue("Generated source should contain getName return value", source.contains("indices_delete_action"));
        assertTrue("Generated source should contain routes", source.contains("routes()"));
        assertTrue("Generated source should contain prepareRequest", source.contains("prepareRequest"));
        assertTrue("Generated source should call fromRestRequest", source.contains("fromRestRequest(request)"));
        assertTrue("Generated source should call client.execute", source.contains("client.execute("));
        // no query params for this endpoint, so supportedQueryParameters() is not overridden
        assertTrue("Generated source should have file comment", source.contains("DO NOT EDIT"));
    }

    @Test
    public void emitWithQueryParamsIncludesThemInSupportedQueryParameters() {
        Endpoint endpoint = endpoint(
            "cluster.health",
            List.of(new UrlPattern("/_cluster/health", List.of("GET"))),
            null
        );
        TypeDefinition requestType = new TypeDefinition(
            new TypeReference("ClusterHealthRequest", "cluster"),
            "request",
            null,
            List.of(),
            List.of(
                new Property("timeout", false, null, null, null),
                new Property("local", false, null, null, null)
            ),
            null,
            null,
            null,
            null
        );
        ResolvedTransportAction resolved = new ResolvedTransportAction(
            FakeTransportAction.class,
            FakeRequest.class,
            FakeResponse.class,
            FakeTransportAction.class,
            "TYPE"
        );
        RestListenerType listenerType = RestListenerType.DEFAULT;

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, requestType, resolved, listenerType);

        String source = javaFile.toString();
        assertTrue("Should include timeout in supportedQueryParameters", source.contains("timeout"));
        assertTrue("Should include local in supportedQueryParameters", source.contains("local"));
        assertTrue("Should return Set.of(...)", source.contains("Set.of("));
    }

    @Test
    public void emitWithServerlessVisibilityAddsServerlessScopeAnnotation() {
        Availability availability = new Availability(
            new AvailabilityDetail("1.0.0", "stable", null),
            new AvailabilityDetail("1.0.0", "stable", "public")
        );
        Endpoint endpoint = endpoint(
            "indices.delete",
            List.of(new UrlPattern("/{index}", List.of("DELETE"))),
            availability
        );
        ResolvedTransportAction resolved = new ResolvedTransportAction(
            FakeTransportAction.class,
            FakeRequest.class,
            FakeResponse.class,
            FakeTransportAction.class,
            "TYPE"
        );
        RestListenerType listenerType = RestListenerType.DEFAULT;

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType);

        String source = javaFile.toString();
        assertTrue("Should add @ServerlessScope when visibility is public", source.contains("ServerlessScope"));
        assertTrue("Should reference Scope.PUBLIC", source.contains("PUBLIC"));
    }

    @Test
    public void emitWithMultipleRoutesGeneratesListOfRoutes() {
        Endpoint endpoint = endpoint(
            "indices.get",
            List.of(
                new UrlPattern("/{index}", List.of("GET", "HEAD"))
            ),
            null
        );
        ResolvedTransportAction resolved = new ResolvedTransportAction(
            FakeTransportAction.class,
            FakeRequest.class,
            FakeResponse.class,
            FakeTransportAction.class,
            "TYPE"
        );
        RestListenerType listenerType = RestListenerType.DEFAULT;

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType);

        String source = javaFile.toString();
        assertTrue("Should contain Route", source.contains("Route"));
        assertTrue("Should contain path", source.contains("/{index}"));
    }

    @Test
    public void emitWithChunkedListenerGeneratesChunkedListenerInstantiation() {
        Endpoint endpoint = endpoint(
            "search",
            List.of(new UrlPattern("/_search", List.of("POST"))),
            null
        );
        ResolvedTransportAction resolved = new ResolvedTransportAction(
            FakeTransportAction.class,
            FakeRequest.class,
            FakeResponse.class,
            FakeTransportAction.class,
            "TYPE"
        );
        RestListenerType listenerType = RestListenerType.CHUNKED;

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType);

        String source = javaFile.toString();
        assertTrue("Should reference chunked listener", source.contains("RestRefCountedChunkedToXContentListener"));
    }

}

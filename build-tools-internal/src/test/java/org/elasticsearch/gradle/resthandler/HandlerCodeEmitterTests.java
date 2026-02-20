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

import static org.junit.Assert.assertFalse;
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
        return endpoint(name, urls, availability, null, null, null, null);
    }

    private static Endpoint endpoint(
        String name,
        List<UrlPattern> urls,
        Availability availability,
        List<String> capabilities,
        Boolean allowSystemIndexAccess,
        Boolean canTripCircuitBreaker,
        List<String> responseParams
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
            "org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction",
            capabilities,
            allowSystemIndexAccess,
            canTripCircuitBreaker,
            responseParams
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, requestType, resolved, listenerType, false);

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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

        String source = javaFile.toString();
        assertTrue("Should reference chunked listener", source.contains("RestRefCountedChunkedToXContentListener"));
    }

    @Test
    public void emitWithResponseParamsExcludesThemFromSupportedQueryParametersAndAddsResponseParamsOverride() {
        Endpoint endpoint = endpoint(
            "cluster.get_settings",
            List.of(new UrlPattern("/_cluster/settings", List.of("GET"))),
            null,
            null,
            null,
            null,
            List.of("flat_settings", "include_defaults")
        );
        TypeDefinition requestType = new TypeDefinition(
            new TypeReference("ClusterGetSettingsRequest", "cluster"),
            "request",
            null,
            List.of(),
            List.of(
                new Property("flat_settings", false, null, null, null),
                new Property("include_defaults", false, null, null, null),
                new Property("master_timeout", false, null, null, null),
                new Property("timeout", false, null, null, null)
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, requestType, resolved, listenerType, false);

        String source = javaFile.toString();
        assertTrue("Should override responseParams()", source.contains("responseParams()"));
        assertTrue("Should include flat_settings in response params", source.contains("flat_settings"));
        assertTrue("Should include include_defaults in response params", source.contains("include_defaults"));
        assertTrue("supportedQueryParameters should include master_timeout", source.contains("master_timeout"));
        assertTrue("supportedQueryParameters should include timeout", source.contains("timeout"));
        // Response params must not appear in the SUPPORTED_QUERY_PARAMETERS Set.of(...) — they appear in RESPONSE_PARAMS
        int supportedOf = source.indexOf("SUPPORTED_QUERY_PARAMETERS");
        int responseParamsOf = source.indexOf("RESPONSE_PARAMS");
        assertTrue("RESPONSE_PARAMS field should be present", responseParamsOf >= 0);
        String afterSupported = source.substring(supportedOf, responseParamsOf);
        assertTrue(
            "flat_settings and include_defaults should not be in SUPPORTED_QUERY_PARAMETERS",
            afterSupported.contains("master_timeout") && afterSupported.contains("timeout")
                && !afterSupported.contains("flat_settings") && !afterSupported.contains("include_defaults")
        );
    }

    @Test
    public void emitWithCapabilitiesAddsSupportedCapabilitiesOverride() {
        Endpoint endpoint = endpoint(
            "search",
            List.of(new UrlPattern("/_search", List.of("POST"))),
            null,
            List.of("search_query_rules", "search_phase_took"),
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

        String source = javaFile.toString();
        assertTrue("Should override supportedCapabilities()", source.contains("supportedCapabilities()"));
        assertTrue("Should include search_query_rules", source.contains("search_query_rules"));
        assertTrue("Should include search_phase_took", source.contains("search_phase_took"));
    }

    @Test
    public void emitWithAllowSystemIndexAccessAddsOverride() {
        Endpoint endpoint = endpoint(
            "cluster.health",
            List.of(new UrlPattern("/_cluster/health", List.of("GET"))),
            null,
            null,
            true,
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

        String source = javaFile.toString();
        assertTrue("Should override allowSystemIndexAccessByDefault()", source.contains("allowSystemIndexAccessByDefault()"));
        assertTrue("Should return true", source.contains("return true"));
    }

    @Test
    public void emitWithCanTripCircuitBreakerFalseProducesOverride() {
        Endpoint endpoint = endpoint(
            "indices.delete",
            List.of(new UrlPattern("/{index}", List.of("DELETE"))),
            null,
            null,
            null,
            false,
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

        String source = javaFile.toString();
        assertTrue("Should override canTripCircuitBreaker()", source.contains("canTripCircuitBreaker()"));
        assertTrue("Should return false", source.contains("return false"));
    }

    @Test
    public void emitWithoutOptionalOverridesProducesNoResponseParamsOrCapabilitiesOrAllowSystemIndexAccess() {
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, false);

        String source = javaFile.toString();
        assertTrue("Should contain prepareRequest", source.contains("prepareRequest"));
        assertFalse("Should not override responseParams (no RESPONSE_PARAMS field)", source.contains("RESPONSE_PARAMS"));
        assertFalse("Should not override supportedCapabilities (no CAPABILITIES field)", source.contains("CAPABILITIES"));
        assertFalse(
            "Should not override allowSystemIndexAccessByDefault",
            source.contains("allowSystemIndexAccessByDefault()")
        );
        assertFalse(
            "Should not override canTripCircuitBreaker",
            source.contains("canTripCircuitBreaker()")
        );
    }

    @Test
    public void emitWithRestCancellableClientWrapsClient() {
        Endpoint endpoint = endpoint(
            "cluster.health",
            List.of(new UrlPattern("/_cluster/health", List.of("GET"))),
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

        com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, null, resolved, listenerType, true);

        String source = javaFile.toString();
        assertTrue(
            "Should wrap client in RestCancellableNodeClient when useRestCancellableClient is true",
            source.contains("RestCancellableNodeClient")
        );
        assertTrue(
            "Should pass request.getHttpChannel() to RestCancellableNodeClient",
            source.contains("request.getHttpChannel()")
        );
    }

}

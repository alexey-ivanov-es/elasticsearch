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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import org.elasticsearch.gradle.resthandler.model.Availability;
import org.elasticsearch.gradle.resthandler.model.Endpoint;
import org.elasticsearch.gradle.resthandler.model.TypeDefinition;
import org.elasticsearch.gradle.resthandler.model.UrlPattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

/**
 * Emits Java source for REST handler classes that extend {@code BaseRestHandler}.
 * Generates {@code routes()}, {@code getName()}, {@code supportedQueryParameters()},
 * and {@code prepareRequest()} calling {@code ActionRequest.fromRestRequest(request)}
 * and dispatching via {@code client.execute(TYPE, actionRequest, listener)}.
 */
public final class HandlerCodeEmitter {

    private static final String BASE_PACKAGE = "org.elasticsearch.rest.action";
    private static final String ADMIN_PACKAGE_PREFIX = BASE_PACKAGE + ".admin.";

    private HandlerCodeEmitter() {}

    /**
     * Generate a Java source file for the given endpoint and resolved action/listener.
     *
     * @param endpoint         the API endpoint from the spec
     * @param requestType      the request type definition (for query param names); may be null
     * @param resolvedAction   the resolved transport action and request/response classes
     * @param resolvedListener the resolved response listener type
     * @return a JavaFile ready to write to disk
     */
    public static JavaFile emit(
        Endpoint endpoint,
        TypeDefinition requestType,
        ResolvedTransportAction resolvedAction,
        ResolvedListener resolvedListener
    ) {
        String packageName = packageForEndpoint(endpoint);
        String handlerClassName = handlerClassName(endpoint.name());
        String handlerNameString = handlerNameString(endpoint.name());

        ClassName baseRestHandler = ClassName.get("org.elasticsearch.rest", "BaseRestHandler");
        ClassName restRequest = ClassName.get("org.elasticsearch.rest", "RestRequest");
        ClassName nodeClient = ClassName.get("org.elasticsearch.client.internal.node", "NodeClient");
        ClassName restChannelConsumer = ClassName.get("org.elasticsearch.rest", "RestChannelConsumer");
        ClassName route = ClassName.get("org.elasticsearch.rest", "Route");
        ClassName restRequestMethod = ClassName.get("org.elasticsearch.rest", "RestRequest", "Method");

        ClassName requestClass = classNameFromFqn(resolvedAction.requestClass().getName());
        ClassName transportActionClass = classNameFromFqn(resolvedAction.transportActionClass().getName());
        ClassName listenerClass = listenerClassNameFromFqn(resolvedListener.listenerClassName());
        ClassName responseClass = classNameFromFqn(resolvedAction.responseClass().getName());

        MethodSpec routesMethod = buildRoutesMethod(endpoint, route, restRequestMethod);
        MethodSpec getNameMethod = MethodSpec.methodBuilder("getName")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return $S", handlerNameString)
            .build();

        Set<String> queryParamNames = queryParamNames(requestType);
        MethodSpec supportedQueryParametersMethod = buildSupportedQueryParametersMethod(queryParamNames);

        CodeBlock listenerNew = buildListenerInstantiation(resolvedListener, listenerClass, responseClass);
        MethodSpec prepareRequestMethod = MethodSpec.methodBuilder("prepareRequest")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(restChannelConsumer)
            .addParameter(restRequest, "request")
            .addParameter(nodeClient, "client")
            .addException(IOException.class)
            .addStatement(
                "$T actionRequest = $T.fromRestRequest(request)",
                requestClass,
                requestClass
            )
            .addStatement(
                "return channel -> client.execute($T.TYPE, actionRequest, $L)",
                transportActionClass,
                listenerNew
            )
            .build();

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(handlerClassName)
            .addModifiers(Modifier.PUBLIC)
            .superclass(baseRestHandler)
            .addMethod(routesMethod)
            .addMethod(getNameMethod)
            .addMethod(supportedQueryParametersMethod)
            .addMethod(prepareRequestMethod);

        addServerlessScope(endpoint, typeBuilder);

        typeBuilder.addJavadoc("Generated REST handler for endpoint $S. Do not edit.\n", endpoint.name());

        return JavaFile.builder(packageName, typeBuilder.build())
            .addFileComment("DO NOT EDIT - Generated by rest-handler-codegen")
            .build();
    }

    private static MethodSpec buildSupportedQueryParametersMethod(Set<String> queryParamNames) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("supportedQueryParameters")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class)));
        if (queryParamNames.isEmpty()) {
            builder.addStatement("return $T.of()", Set.class);
        } else {
            String placeholders = queryParamNames.stream().map(x -> "$S").collect(Collectors.joining(", "));
            Object[] args = new Object[queryParamNames.size() + 1];
            args[0] = Set.class;
            int i = 1;
            for (String n : queryParamNames) {
                args[i++] = n;
            }
            builder.addStatement("return $T.of(" + placeholders + ")", args);
        }
        return builder.build();
    }

    private static MethodSpec buildRoutesMethod(Endpoint endpoint, ClassName route, ClassName restRequestMethod) {
        List<CodeBlock> routeArgs = new ArrayList<>();
        List<UrlPattern> urls = endpoint.urls() != null ? endpoint.urls() : List.of();
        for (UrlPattern urlPattern : urls) {
            List<String> methods = urlPattern.methods() != null ? urlPattern.methods() : List.of();
            for (String method : methods) {
                String methodConstant = methodConstant(method);
                routeArgs.add(CodeBlock.of("new $T($T.$L, $S)", route, restRequestMethod, methodConstant, urlPattern.path()));
            }
        }
        MethodSpec.Builder builder = MethodSpec.methodBuilder("routes")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), route));
        if (routeArgs.isEmpty()) {
            builder.addStatement("return $T.of()", List.class);
        } else {
            builder.addStatement("return $T.of($L)", List.class, CodeBlock.join(routeArgs, ", "));
        }
        return builder.build();
    }

    private static String methodConstant(String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> "GET";
            case "POST" -> "POST";
            case "PUT" -> "PUT";
            case "DELETE" -> "DELETE";
            case "HEAD" -> "HEAD";
            default -> "GET";
        };
    }

    private static CodeBlock buildListenerInstantiation(
        ResolvedListener resolvedListener,
        ClassName listenerClass,
        ClassName responseClass
    ) {
        return switch (resolvedListener.kind()) {
            case CHUNKED, NODES, DEFAULT -> CodeBlock.of("new $T<>(channel)", listenerClass);
            case STATUS -> CodeBlock.of("new $T<>(channel, $T::status)", listenerClass, responseClass);
        };
    }

    private static void addServerlessScope(Endpoint endpoint, TypeSpec.Builder typeBuilder) {
        Availability availability = endpoint.availability();
        if (availability == null || availability.serverless() == null) {
            return;
        }
        String visibility = availability.serverless().visibility();
        if (visibility == null) {
            return;
        }
        ClassName scope = ClassName.get("org.elasticsearch.rest", "Scope");
        ClassName serverlessScope = ClassName.get("org.elasticsearch.rest", "ServerlessScope");
        String scopeConstant = "public".equalsIgnoreCase(visibility) ? "PUBLIC" : "INTERNAL";
        typeBuilder.addAnnotation(
            com.squareup.javapoet.AnnotationSpec.builder(serverlessScope)
                .addMember("value", "$T.$L", scope, scopeConstant)
                .build()
        );
    }

    private static Set<String> queryParamNames(TypeDefinition requestType) {
        if (requestType == null || requestType.query() == null) {
            return Set.of();
        }
        return requestType.query().stream()
            .map(p -> p.name())
            .filter(n -> n != null)
            .collect(Collectors.toSet());
    }

    private static String packageForEndpoint(Endpoint endpoint) {
        String name = endpoint.name();
        if (name == null) {
            return BASE_PACKAGE;
        }
        int dot = name.indexOf('.');
        String namespace = dot > 0 ? name.substring(0, dot) : "";
        return switch (namespace) {
            case "indices" -> ADMIN_PACKAGE_PREFIX + "indices";
            case "cluster" -> ADMIN_PACKAGE_PREFIX + "cluster";
            case "ingest" -> BASE_PACKAGE + ".ingest";
            case "cat" -> BASE_PACKAGE + ".cat";
            case "nodes" -> ADMIN_PACKAGE_PREFIX + "cluster.node";
            default -> namespace.isEmpty() ? BASE_PACKAGE : ADMIN_PACKAGE_PREFIX + namespace;
        };
    }

    private static String handlerClassName(String endpointName) {
        if (endpointName == null || endpointName.isEmpty()) {
            return "RestAction";
        }
        String camel = toCamelCase(endpointName);
        return "Rest" + camel + "Action";
    }

    private static String handlerNameString(String endpointName) {
        if (endpointName == null) {
            return "action";
        }
        return endpointName.replace('.', '_') + "_action";
    }

    private static String toCamelCase(String dotSeparated) {
        StringBuilder sb = new StringBuilder();
        for (String segment : dotSeparated.split("[._]")) {
            if (segment.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(segment.charAt(0)));
            if (segment.length() > 1) {
                sb.append(segment.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static ClassName classNameFromFqn(String fqn) {
        return ClassName.bestGuess(fqn);
    }

    private static ClassName listenerClassNameFromFqn(String fqn) {
        String[] parts = fqn.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid listener class name: " + fqn);
        }
        if (parts.length == 5) {
            return ClassName.get(String.join(".", parts[0], parts[1], parts[2], parts[3]), parts[4]);
        }
        if (parts.length >= 6) {
            String packageName = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            ClassName outer = ClassName.get(packageName, parts[4]);
            return outer.nestedClass(parts[5]);
        }
        return ClassName.bestGuess(fqn);
    }
}

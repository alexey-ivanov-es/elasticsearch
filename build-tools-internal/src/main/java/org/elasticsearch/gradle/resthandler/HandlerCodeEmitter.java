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
import com.squareup.javapoet.FieldSpec;
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
import java.util.TreeSet;
import java.util.function.Consumer;
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
    private static final String ACTION_PACKAGE_PREFIX = "org.elasticsearch.action.";
    private static final String SUPPORTED_QUERY_PARAMETERS_FIELD = "SUPPORTED_QUERY_PARAMETERS";
    private static final String RESPONSE_PARAMS_FIELD = "RESPONSE_PARAMS";
    private static final String CAPABILITIES_FIELD = "CAPABILITIES";

    private HandlerCodeEmitter() {}

    /**
     * Package for generated REST handlers: {@code org.elasticsearch.rest.action} + everything in the
     * transport action package after {@code action.}.
     */
    public static String packageForTransportAction(String transportActionFqn) {
        if (transportActionFqn == null || !transportActionFqn.contains(".")) {
            return BASE_PACKAGE;
        }
        int lastDot = transportActionFqn.lastIndexOf('.');
        String transportPackage = transportActionFqn.substring(0, lastDot);
        if (!transportPackage.startsWith(ACTION_PACKAGE_PREFIX)) {
            return BASE_PACKAGE;
        }
        String suffix = transportPackage.substring(ACTION_PACKAGE_PREFIX.length());
        return suffix.isEmpty() ? BASE_PACKAGE : BASE_PACKAGE + "." + suffix;
    }

    /**
     * Handler class name from endpoint name: Rest + CamelCase(endpointName) + Action.
     */
    public static String handlerClassNameForEndpoint(String endpointName) {
        return handlerClassName(endpointName);
    }

    /**
     * Generate a Java source file for the given endpoint and resolved action/listener.
     *
     * @param endpoint         the API endpoint from the spec
     * @param requestType      the request type definition (for query param names); may be null
     * @param resolvedAction   the resolved transport action and request/response classes
     * @param listenerType the resolved response listener type
     * @return a JavaFile ready to write to disk
     */
    public static JavaFile emit(
        Endpoint endpoint,
        TypeDefinition requestType,
        ResolvedTransportAction resolvedAction,
        RestListenerType listenerType
    ) {
        String packageName = packageForTransportAction(resolvedAction.transportActionClass().getName());
        String handlerClassName = handlerClassName(endpoint.name());
        String handlerNameString = handlerNameString(endpoint.name());

        ClassName baseRestHandler = ClassName.get("org.elasticsearch.rest", "BaseRestHandler");
        ClassName restRequest = ClassName.get("org.elasticsearch.rest", "RestRequest");
        ClassName nodeClient = ClassName.get("org.elasticsearch.client.internal.node", "NodeClient");
        ClassName restChannelConsumer = ClassName.get("org.elasticsearch.rest", "BaseRestHandler", "RestChannelConsumer");
        ClassName route = ClassName.get("org.elasticsearch.rest", "RestHandler", "Route");
        ClassName restRequestMethod = ClassName.get("org.elasticsearch.rest", "RestRequest", "Method");

        ClassName requestClass = classNameFromFqn(resolvedAction.requestClass().getName());
        ClassName actionTypeClass = classNameFromFqn(resolvedAction.actionTypeReferenceClass().getName());
        ClassName listenerClass = listenerType.getClassName();
        ClassName responseClass = classNameFromFqn(resolvedAction.responseClass().getName());

        MethodSpec routesMethod = buildRoutesMethod(endpoint, route, restRequestMethod);
        MethodSpec getNameMethod = MethodSpec.methodBuilder("getName")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return $S", handlerNameString)
            .build();

        Set<String> supportedParamNames = supportedQueryParamNames(endpoint, requestType);
        boolean hasSupportedParams = supportedParamNames.isEmpty() == false;
        List<String> responseParamsList = endpoint.responseParams() != null ? endpoint.responseParams() : List.of();
        boolean hasResponseParams = responseParamsList.isEmpty() == false;
        List<String> capabilitiesList = endpoint.capabilities() != null ? endpoint.capabilities() : List.of();
        boolean hasCapabilities = capabilitiesList.isEmpty() == false;
        boolean allowSystemIndexAccess = Boolean.TRUE.equals(endpoint.allowSystemIndexAccess());

        CodeBlock listenerNew = buildListenerInstantiation(listenerType, listenerClass, responseClass);
        MethodSpec prepareRequestMethod = MethodSpec.methodBuilder("prepareRequest")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(restChannelConsumer)
            .addParameter(restRequest, "request")
            .addParameter(nodeClient, "client")
            .addException(IOException.class)
            .addStatement("$T actionRequest = $T.fromRestRequest(request)", requestClass, requestClass)
            .addStatement(
                "return channel -> client.execute($T.$L, actionRequest, $L)",
                actionTypeClass,
                resolvedAction.actionTypeReferenceField(),
                listenerNew
            )
            .build();

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(handlerClassName)
            .addModifiers(Modifier.PUBLIC)
            .superclass(baseRestHandler)
            .addMethod(routesMethod)
            .addMethod(getNameMethod);
        if (hasSupportedParams) {
            typeBuilder.addField(buildSupportedQueryParametersField(supportedParamNames));
            typeBuilder.addMethod(buildSupportedQueryParametersMethod());
        }
        if (hasResponseParams) {
            typeBuilder.addField(buildResponseParamsField(responseParamsList));
            typeBuilder.addMethod(buildResponseParamsMethod());
        }
        if (hasCapabilities) {
            typeBuilder.addField(buildCapabilitiesField(capabilitiesList));
            typeBuilder.addMethod(buildSupportedCapabilitiesMethod());
        }
        if (allowSystemIndexAccess) {
            typeBuilder.addMethod(buildAllowSystemIndexAccessByDefaultMethod());
        }
        typeBuilder.addMethod(prepareRequestMethod);

        addServerlessScope(endpoint, typeBuilder);

        typeBuilder.addJavadoc("Generated REST handler for endpoint $S. Do not edit.\n", endpoint.name());

        return JavaFile.builder(packageName, typeBuilder.build()).addFileComment("DO NOT EDIT - Generated by rest-handler-codegen").build();
    }

    /**
     * Build a static final Set field so that supportedQueryParameters() returns the same instance
     * every time, as required by BaseRestHandler's assertion. Only used when the endpoint has
     * query parameters.
     */
    private static FieldSpec buildSupportedQueryParametersField(Set<String> queryParamNames) {
        ParameterizedTypeName setOfString = ParameterizedTypeName.get(
            ClassName.get(Set.class),
            ClassName.get(String.class)
        );
        String placeholders = queryParamNames.stream().map(x -> "$S").collect(Collectors.joining(", "));
        Object[] args = new Object[queryParamNames.size() + 1];
        args[0] = Set.class;
        int i = 1;
        for (String n : queryParamNames) {
            args[i++] = n;
        }
        CodeBlock initializer = CodeBlock.of("$T.of(" + placeholders + ")", args);
        return FieldSpec.builder(setOfString, SUPPORTED_QUERY_PARAMETERS_FIELD)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(initializer)
            .build();
    }

    private static MethodSpec buildSupportedQueryParametersMethod() {
        return MethodSpec.methodBuilder("supportedQueryParameters")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class)))
            .addStatement("return $L", SUPPORTED_QUERY_PARAMETERS_FIELD)
            .build();
    }

    private static FieldSpec buildResponseParamsField(List<String> responseParamNames) {
        ParameterizedTypeName setOfString = ParameterizedTypeName.get(
            ClassName.get(Set.class),
            ClassName.get(String.class)
        );
        String placeholders = responseParamNames.stream().map(x -> "$S").collect(Collectors.joining(", "));
        Object[] args = new Object[responseParamNames.size() + 1];
        args[0] = Set.class;
        int i = 1;
        for (String n : responseParamNames) {
            args[i++] = n;
        }
        CodeBlock initializer = CodeBlock.of("$T.of(" + placeholders + ")", args);
        return FieldSpec.builder(setOfString, RESPONSE_PARAMS_FIELD)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(initializer)
            .build();
    }

    private static MethodSpec buildResponseParamsMethod() {
        return MethodSpec.methodBuilder("responseParams")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class)))
            .addStatement("return $L", RESPONSE_PARAMS_FIELD)
            .build();
    }

    private static FieldSpec buildCapabilitiesField(List<String> capabilityStrings) {
        ParameterizedTypeName setOfString = ParameterizedTypeName.get(
            ClassName.get(Set.class),
            ClassName.get(String.class)
        );
        String placeholders = capabilityStrings.stream().map(x -> "$S").collect(Collectors.joining(", "));
        Object[] args = new Object[capabilityStrings.size() + 1];
        args[0] = Set.class;
        int i = 1;
        for (String c : capabilityStrings) {
            args[i++] = c;
        }
        CodeBlock initializer = CodeBlock.of("$T.of(" + placeholders + ")", args);
        return FieldSpec.builder(setOfString, CAPABILITIES_FIELD)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(initializer)
            .build();
    }

    private static MethodSpec buildSupportedCapabilitiesMethod() {
        return MethodSpec.methodBuilder("supportedCapabilities")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class)))
            .addStatement("return $L", CAPABILITIES_FIELD)
            .build();
    }

    private static MethodSpec buildAllowSystemIndexAccessByDefaultMethod() {
        return MethodSpec.methodBuilder("allowSystemIndexAccessByDefault")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addStatement("return true")
            .build();
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

    private static CodeBlock buildListenerInstantiation(RestListenerType listenerType, ClassName listenerClass, ClassName responseClass) {
        if (listenerType.needsStatusMethodReference()) {
            return CodeBlock.of("new $T<>(channel, $T::status)", listenerClass, responseClass);
        }
        return CodeBlock.of("new $T<>(channel)", listenerClass);
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
            com.squareup.javapoet.AnnotationSpec.builder(serverlessScope).addMember("value", "$T.$L", scope, scopeConstant).build()
        );
    }

    /**
     * Parameter names for {@code supportedQueryParameters()}: all consumed params minus
     * {@code responseParams}. Response params are declared in {@code responseParams()} only
     * and must not be duplicated in {@code supportedQueryParameters()}.
     */
    private static Set<String> supportedQueryParamNames(Endpoint endpoint, TypeDefinition requestType) {
        Set<String> names = new TreeSet<>(allSupportedParamNames(requestType));
        List<String> responseParamsList = endpoint.responseParams() != null ? endpoint.responseParams() : List.of();
        names.removeAll(responseParamsList);
        return names;
    }

    /**
     * All parameter names the handler must declare as supported so that consumed params match
     * (BaseRestHandler assertion). Includes: query params from spec, path params, and when the
     * request has an "index" path param, the IndicesOptions query params consumed by
     * IndicesOptions.fromRequest() (allow_no_indices, ignore_unavailable, ignore_throttled).
     */
    private static Set<String> allSupportedParamNames(TypeDefinition requestType) {
        Set<String> names = new TreeSet<>();
        if (requestType == null) {
            return names;
        }
        if (requestType.query() != null) {
            requestType.query().stream().map(p -> p.name()).filter(n -> n != null).forEach(names::add);
        }
        boolean hasIndexPath = false;
        if (requestType.path() != null) {
            for (var p : requestType.path()) {
                if (p.name() != null) {
                    names.add(p.name());
                    if ("index".equals(p.name())) {
                        hasIndexPath = true;
                    }
                }
            }
        }
        if (hasIndexPath) {
            names.add("allow_no_indices");
            names.add("ignore_throttled");
            names.add("ignore_unavailable");
        }
        return names;
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

    /**
     * Generate {@code GeneratedRestHandlerRegistry}: a class with static
     * {@code registerHandlers(Consumer<RestHandler>)} that registers every generated handler.
     *
     * @param handlerClasses list of generated handler class names (package + simple name)
     * @return a JavaFile for the registry class in package {@value #BASE_PACKAGE}
     */
    public static JavaFile emitRegistry(List<ClassName> handlerClasses) {
        ClassName restHandler = ClassName.get("org.elasticsearch.rest", "RestHandler");
        ClassName consumer = ClassName.get(Consumer.class);
        ParameterizedTypeName consumerOfRestHandler = ParameterizedTypeName.get(consumer, restHandler);

        MethodSpec.Builder registerBody = MethodSpec.methodBuilder("registerHandlers")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(consumerOfRestHandler, "registerHandler")
            .returns(void.class);
        for (ClassName handlerClass : handlerClasses) {
            registerBody.addStatement("registerHandler.accept(new $T())", handlerClass);
        }

        TypeSpec typeSpec = TypeSpec.classBuilder("GeneratedRestHandlerRegistry")
            .addModifiers(Modifier.PUBLIC)
            .addMethod(registerBody.build())
            .addJavadoc("Registry of generated REST handlers. Do not edit.\n")
            .build();

        return JavaFile.builder(BASE_PACKAGE, typeSpec).addFileComment("DO NOT EDIT - Generated by rest-handler-codegen").build();
    }
}

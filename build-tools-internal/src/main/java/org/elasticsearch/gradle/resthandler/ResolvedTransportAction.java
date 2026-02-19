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
 * Result of resolving a transport action class: the action class plus the
 * ActionRequest and ActionResponse types from its generic superclass chain,
 * and the class/field to use for {@code client.execute(..., request, listener)}
 * (either {@code TransportXxxAction.TYPE} or {@code XxxAction.INSTANCE}).
 */
public record ResolvedTransportAction(
    Class<?> transportActionClass,
    Class<?> requestClass,
    Class<?> responseClass,
    Class<?> actionTypeReferenceClass,
    String actionTypeReferenceField
) {}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action.admin.indices;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.Scope;
import org.elasticsearch.rest.ServerlessScope;
import org.elasticsearch.rest.action.RestToXContentListener;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.rest.RestRequest.Method.PUT;

@ServerlessScope(Scope.PUBLIC)
public class RestCreateIndexAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return List.of(new Route(PUT, "/{index}"));
    }

    @Override
    public String getName() {
        return "create_index_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final CreateIndexRequest createIndexRequest = CreateIndexRequest.fromRestRequest(request);
        return channel -> client.admin().indices().create(createIndexRequest, new RestToXContentListener<>(channel));
    }

    @Override
    public Set<String> supportedCapabilities() {
        return CreateIndexCapabilities.CAPABILITIES;
    }
}

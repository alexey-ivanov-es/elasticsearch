/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action.ingest;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.action.ingest.PutPipelineTransportAction;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.bytes.ReleasableBytesReference;
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
public class RestPutPipelineAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return List.of(new Route(PUT, "/_ingest/pipeline/{id}"));
    }

    @Override
    public String getName() {
        return "ingest_put_pipeline_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        final PutPipelineRequest request = PutPipelineRequest.fromRestRequest(restRequest);
        ReleasableBytesReference content = (ReleasableBytesReference) request.getSource();
        return channel -> client.execute(
            PutPipelineTransportAction.TYPE,
            request,
            ActionListener.withRef(new RestToXContentListener<>(channel), content)
        );
    }

    @Override
    public Set<String> supportedCapabilities() {
        // pipeline_tracking info: `{created,modified}_date` system properties defined within pipeline definition.
        return Set.of("pipeline_tracking_info", "field_access_pattern.flexible");
    }
}

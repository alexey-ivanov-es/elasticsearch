/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.admin.cluster.tasks;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.master.MasterNodeReadRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.rest.RestRequest;

import java.io.IOException;

import static org.elasticsearch.rest.RestUtils.getMasterNodeTimeout;

public class PendingClusterTasksRequest extends MasterNodeReadRequest<PendingClusterTasksRequest> {

    public PendingClusterTasksRequest(TimeValue masterNodeTimeout) {
        super(masterNodeTimeout);
    }

    public PendingClusterTasksRequest(StreamInput in) throws IOException {
        super(in);
    }

    /**
     * Create a request from a REST request. Used by the REST handler and by generated handlers.
     */
    public static PendingClusterTasksRequest fromRestRequest(RestRequest request) {
        PendingClusterTasksRequest r = new PendingClusterTasksRequest(getMasterNodeTimeout(request));
        r.local(request.paramAsBoolean("local", r.local()));
        return r;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

}

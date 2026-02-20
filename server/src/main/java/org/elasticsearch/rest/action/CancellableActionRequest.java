/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.tasks.CancellableTask;

/**
 * Marker interface for {@link ActionRequest}s that support cancellation when the REST
 * client disconnects. When a generated REST handler's request type implements this
 * interface, the generator wraps the {@code NodeClient} in {@link RestCancellableNodeClient}
 * so that closing the HTTP channel cancels the in-flight request.
 * <p>
 * Implementations must ensure that {@link ActionRequest#createTask(long, String, String, org.elasticsearch.tasks.TaskId, java.util.Map) createTask}
 * returns a {@link CancellableTask} so that cancellation can be propagated.
 */
public interface CancellableActionRequest {}

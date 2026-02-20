/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action.admin.cluster;

import org.elasticsearch.action.ClusterStatsLevel;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.Priority;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.FakeRestRequest;

import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.rest.RestUtils.REST_MASTER_TIMEOUT_PARAM;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.object.HasToString.hasToString;

public class RestClusterHealthActionTests extends ESTestCase {

    public void testFromRequest() {
        Map<String, String> params = new HashMap<>();
        String index = "index";
        boolean local = randomBoolean();
        var masterTimeout = randomTimeValue();
        var timeout = randomTimeValue();
        ClusterHealthStatus waitForStatus = randomFrom(ClusterHealthStatus.values());
        boolean waitForNoRelocatingShards = randomBoolean();
        boolean waitForNoInitializingShards = randomBoolean();
        int waitForActiveShards = randomIntBetween(1, 3);
        String waitForNodes = "node";
        Priority waitForEvents = randomFrom(Priority.values());

        params.put("index", index);
        params.put("local", String.valueOf(local));
        params.put(REST_MASTER_TIMEOUT_PARAM, masterTimeout.getStringRep());
        params.put("timeout", timeout.getStringRep());
        params.put("wait_for_status", waitForStatus.name());
        if (waitForNoRelocatingShards || randomBoolean()) {
            params.put("wait_for_no_relocating_shards", String.valueOf(waitForNoRelocatingShards));
        }
        if (waitForNoInitializingShards || randomBoolean()) {
            params.put("wait_for_no_initializing_shards", String.valueOf(waitForNoInitializingShards));
        }
        params.put("wait_for_active_shards", String.valueOf(waitForActiveShards));
        params.put("wait_for_nodes", waitForNodes);
        params.put("wait_for_events", waitForEvents.name());

        FakeRestRequest restRequest = buildRestRequest(params);
        ClusterHealthRequest clusterHealthRequest = ClusterHealthRequest.fromRestRequest(restRequest);
        assertThat(clusterHealthRequest.indices().length, equalTo(1));
        assertThat(clusterHealthRequest.indices()[0], equalTo(index));
        assertThat(clusterHealthRequest.local(), equalTo(local));
        assertThat(clusterHealthRequest.masterNodeTimeout(), equalTo(masterTimeout));
        assertThat(clusterHealthRequest.timeout(), equalTo(timeout));
        assertThat(clusterHealthRequest.waitForStatus(), equalTo(waitForStatus));
        assertThat(clusterHealthRequest.waitForNoRelocatingShards(), equalTo(waitForNoRelocatingShards));
        assertThat(clusterHealthRequest.waitForNoInitializingShards(), equalTo(waitForNoInitializingShards));
        assertThat(clusterHealthRequest.waitForActiveShards(), equalTo(ActiveShardCount.parseString(String.valueOf(waitForActiveShards))));
        assertThat(clusterHealthRequest.waitForNodes(), equalTo(waitForNodes));
        assertThat(clusterHealthRequest.waitForEvents(), equalTo(waitForEvents));

    }

    /**
     * Level is a response param; fromRestRequest does not read or validate it.
     * Valid levels are validated when the response is serialized (ClusterStatsLevel.of).
     */
    public void testFromRestRequestWithLevelParam() {
        final HashMap<String, String> params = new HashMap<>();
        params.put("level", ClusterStatsLevel.CLUSTER.getLevel());
        RestRequest request = buildRestRequest(params);
        ClusterHealthRequest req = ClusterHealthRequest.fromRestRequest(request);
        assertNotNull(req);
    }

    /**
     * Invalid level is rejected by ClusterStatsLevel.of() when the response is serialized.
     */
    public void testInvalidLevelRejectedByClusterStatsLevel() {
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> ClusterStatsLevel.of("invalid")
        );
        assertThat(e, hasToString(containsString("level parameter must be one of [cluster] or [indices] or [shards] but was [invalid]")));
    }

    private FakeRestRequest buildRestRequest(Map<String, String> params) {
        return new FakeRestRequest.Builder(xContentRegistry()).withMethod(RestRequest.Method.GET)
            .withPath("/_cluster/health")
            .withParams(params)
            .build();
    }
}

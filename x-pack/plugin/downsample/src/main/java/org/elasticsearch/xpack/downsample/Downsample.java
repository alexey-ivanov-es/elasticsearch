/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.downsample;

import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.features.NodeFeature;
import org.elasticsearch.plugin.Extension;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.PersistentTaskPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.threadpool.FixedExecutorBuilderSpec;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Downsample extends Plugin implements ActionPlugin, PersistentTaskPlugin {

    public static final String DOWNSAMPLE_TASK_THREAD_POOL_NAME = "downsample_indexing";
    private static final int DOWNSAMPLE_TASK_THREAD_POOL_QUEUE_SIZE = 256;
    public static final String DOWNSAMPLE_MIN_NUMBER_OF_REPLICAS_NAME = "downsample.min_number_of_replicas";

    @Extension
    public static final FixedExecutorBuilderSpec DOWNSAMPLE_EXECUTOR = new FixedExecutorBuilderSpec(
        DOWNSAMPLE_TASK_THREAD_POOL_NAME,
        settings -> ThreadPool.oneEighthAllocatedProcessors(EsExecutors.allocatedProcessors(settings)),
        DOWNSAMPLE_TASK_THREAD_POOL_QUEUE_SIZE,
        "xpack.downsample.thread_pool"
    );

    /*@Override
    public Collection<ActionHandler> getActions() {
        return List.of(
            new ActionHandler(DownsampleAction.INSTANCE, TransportDownsampleAction.class),
            new ActionHandler(
                DownsampleShardPersistentTaskExecutor.DelegatingAction.INSTANCE,
                DownsampleShardPersistentTaskExecutor.DelegatingAction.TA.class
            )
        );
    }*/

    @Override
    public List<RestHandler> getRestHandlers(
        Settings settings,
        NamedWriteableRegistry namedWriteableRegistry,
        RestController restController,
        ClusterSettings clusterSettings,
        IndexScopedSettings indexScopedSettings,
        SettingsFilter settingsFilter,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<DiscoveryNodes> nodesInCluster,
        Predicate<NodeFeature> clusterSupportsFeature
    ) {
        return List.of(new RestDownsampleAction());
    }
}

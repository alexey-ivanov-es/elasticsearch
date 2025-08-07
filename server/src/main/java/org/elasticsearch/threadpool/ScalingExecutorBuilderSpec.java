/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.threadpool;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.plugin.Extensible;

import java.util.function.ToIntFunction;

@Extensible
public record ScalingExecutorBuilderSpec(
    String name,
    int core,
    ToIntFunction<Settings> maxFunction,
    TimeValue keepAlive,
    boolean rejectAfterShutdown,
    String prefix,
    EsExecutors.TaskTrackingConfig trackingConfig
) implements ThreadPoolSpec<ScalingExecutorBuilder.ScalingExecutorSettings> {
    public ScalingExecutorBuilderSpec(String name, int core, int max, TimeValue keepAlive, boolean rejectAfterShutdown) {
        this(name, core, ignore -> max, keepAlive, rejectAfterShutdown);
    }

    public ScalingExecutorBuilderSpec(String name, int core, int max, TimeValue keepAlive, boolean rejectAfterShutdown, String prefix) {
        this(name, core, ignore -> max, keepAlive, rejectAfterShutdown, prefix, EsExecutors.TaskTrackingConfig.DO_NOT_TRACK);
    }

    public ScalingExecutorBuilderSpec(String name, int core, ToIntFunction<Settings> maxFunction, TimeValue keepAlive, boolean rejectAfterShutdown) {
        this(name, core, maxFunction, keepAlive, rejectAfterShutdown, "thread_pool." + name);
    }

    public ScalingExecutorBuilderSpec(String name, int core, ToIntFunction<Settings> maxFunction, TimeValue keepAlive, boolean rejectAfterShutdown, String prefix) {
        this(name, core, maxFunction, keepAlive, rejectAfterShutdown, prefix, EsExecutors.TaskTrackingConfig.DO_NOT_TRACK);
    }

    @Override
    public ScalingExecutorBuilder toBuilder(Settings settings) {
        int max = maxFunction.applyAsInt(settings);
        return new ScalingExecutorBuilder(name, core, max, keepAlive, rejectAfterShutdown, prefix, trackingConfig);
    }
}

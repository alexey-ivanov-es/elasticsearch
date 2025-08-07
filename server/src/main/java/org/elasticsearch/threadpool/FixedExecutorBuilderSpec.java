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
import org.elasticsearch.plugin.Extensible;

import java.util.function.ToIntFunction;

@Extensible
public record FixedExecutorBuilderSpec(
    String name,
    ToIntFunction<Settings> sizeFunction,
    int queueSize,
    String prefix,
    EsExecutors.TaskTrackingConfig taskTrackingConfig
) implements ThreadPoolSpec<FixedExecutorBuilder.FixedExecutorSettings> {
    public FixedExecutorBuilderSpec(String name, int size, int queueSize, String prefix) {
        this(name, ignore -> size, queueSize, prefix);
    }

    public FixedExecutorBuilderSpec(String name, ToIntFunction<Settings> sizeFunction, int queueSize, String prefix) {
        this(name, sizeFunction, queueSize, prefix, EsExecutors.TaskTrackingConfig.DO_NOT_TRACK);
    }

    @Override
    public FixedExecutorBuilder toBuilder(Settings settings) {
        int size = sizeFunction().applyAsInt(settings);
        return new FixedExecutorBuilder(settings, name, size, queueSize, prefix, taskTrackingConfig);
    }
}

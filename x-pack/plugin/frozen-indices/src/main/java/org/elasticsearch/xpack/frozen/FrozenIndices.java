/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.frozen;

import org.elasticsearch.action.AbstractActionRequest;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.xpack.core.action.XPackUsageFeatureAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FrozenIndices extends Plugin implements ActionPlugin {

    @Override
    public Collection<ActionHandler> getActions() {
        List<ActionHandler> actions = new ArrayList<>();
        actions.add(new ActionHandler(XPackUsageFeatureAction.FROZEN_INDICES, FrozenIndicesUsageTransportAction.class));
        return actions;
    }
}

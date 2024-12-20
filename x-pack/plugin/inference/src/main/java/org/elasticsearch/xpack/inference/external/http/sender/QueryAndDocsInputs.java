/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import java.util.List;
import java.util.Objects;

public class QueryAndDocsInputs extends InferenceInputs {

    public static QueryAndDocsInputs of(InferenceInputs inferenceInputs) {
        if (inferenceInputs instanceof QueryAndDocsInputs == false) {
            throw createUnsupportedTypeException(inferenceInputs, QueryAndDocsInputs.class);
        }

        return (QueryAndDocsInputs) inferenceInputs;
    }

    private final String query;
    private final List<String> chunks;

    public QueryAndDocsInputs(String query, List<String> chunks) {
        this(query, chunks, false);
    }

    public QueryAndDocsInputs(String query, List<String> chunks, boolean stream) {
        super(stream);
        this.query = Objects.requireNonNull(query);
        this.chunks = Objects.requireNonNull(chunks);
    }

    public String getQuery() {
        return query;
    }

    public List<String> getChunks() {
        return chunks;
    }

    public int inputSize() {
        return chunks.size();
    }
}

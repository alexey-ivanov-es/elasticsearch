/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.ingest;

import org.elasticsearch.action.support.master.AcknowledgedRequest;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.bytes.ReleasableBytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestUtils;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public class PutPipelineRequest extends AcknowledgedRequest<PutPipelineRequest> implements ToXContentObject {

    /**
     * Creates a new put-pipeline request from a REST request. Parses path and query parameters
     * and body (pipeline definition). The returned request's source may be a {@link ReleasableBytesReference};
     * the caller is responsible for releasing it (e.g. via {@code ActionListener.withRef(listener, request.getSource())}
     * when the source is releasable).
     */
    public static PutPipelineRequest fromRestRequest(RestRequest restRequest) {
        Integer ifVersion = null;
        if (restRequest.hasParam("if_version")) {
            String versionString = restRequest.param("if_version");
            try {
                ifVersion = Integer.parseInt(versionString);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "invalid value [%s] specified for [if_version]. must be an integer value", versionString)
                );
            }
        }
        Tuple<XContentType, ReleasableBytesReference> sourceTuple = restRequest.contentOrSourceParam();
        return new PutPipelineRequest(
            RestUtils.getMasterNodeTimeout(restRequest),
            RestUtils.getAckTimeout(restRequest),
            restRequest.param("id"),
            sourceTuple.v2(),
            sourceTuple.v1(),
            ifVersion
        );
    }

    private final String id;
    private final BytesReference source;
    private final XContentType xContentType;
    private final Integer version;

    /**
     * Create a new pipeline request with the id and source along with the content type of the source
     */
    public PutPipelineRequest(
        TimeValue masterNodeTimeout,
        TimeValue ackTimeout,
        String id,
        BytesReference source,
        XContentType xContentType,
        Integer version
    ) {
        super(masterNodeTimeout, ackTimeout);
        this.id = Objects.requireNonNull(id);
        this.source = Objects.requireNonNull(source);
        this.xContentType = Objects.requireNonNull(xContentType);
        this.version = version;
    }

    public PutPipelineRequest(
        TimeValue masterNodeTimeout,
        TimeValue ackTimeout,
        String id,
        BytesReference source,
        XContentType xContentType
    ) {
        this(masterNodeTimeout, ackTimeout, id, source, xContentType, null);
    }

    public PutPipelineRequest(StreamInput in) throws IOException {
        super(in);
        id = in.readString();
        source = in.readBytesReference();
        xContentType = in.readEnum(XContentType.class);
        version = in.readOptionalInt();
    }

    public String getId() {
        return id;
    }

    public BytesReference getSource() {
        return source;
    }

    public XContentType getXContentType() {
        return xContentType;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(id);
        out.writeBytesReference(source);
        XContentHelper.writeTo(out, xContentType);
        out.writeOptionalInt(version);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (source != null) {
            builder.rawValue(source.streamInput(), xContentType);
        } else {
            builder.startObject().endObject();
        }
        return builder;
    }
}

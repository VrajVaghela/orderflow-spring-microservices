package com.orderflow.order.infrastructure.grpc;

import com.orderflow.common.Correlation;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.UUID;

public final class CorrelationClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> KEY =
            Metadata.Key.of(Correlation.HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String correlationId = Correlation.current().orElseGet(() -> UUID.randomUUID().toString());
                headers.put(KEY, correlationId);
                super.start(responseListener, headers);
            }
        };
    }
}

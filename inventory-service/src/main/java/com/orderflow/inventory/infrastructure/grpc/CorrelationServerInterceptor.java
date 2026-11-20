package com.orderflow.inventory.infrastructure.grpc;

import com.orderflow.common.Correlation;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
public class CorrelationServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> KEY =
            Metadata.Key.of(Correlation.HEADER, Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> CORRELATION = Context.key(Correlation.HEADER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String correlationId = Correlation.resolve(headers.get(KEY));
        Context context = Context.current().withValue(CORRELATION, correlationId);
        ServerCall.Listener<ReqT> delegate = Contexts.interceptCall(context, call, headers, next);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                run(() -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                run(super::onHalfClose);
            }

            @Override
            public void onCancel() {
                run(super::onCancel);
            }

            @Override
            public void onComplete() {
                run(super::onComplete);
            }

            @Override
            public void onReady() {
                run(super::onReady);
            }

            private void run(Runnable action) {
                Correlation.set(correlationId);
                try {
                    action.run();
                } finally {
                    Correlation.clear();
                }
            }
        };
    }
}

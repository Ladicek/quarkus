package io.quarkus.apicurio.registry.avro;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.client.impl.ErrorHandler;
import io.apicurio.registry.utils.IoUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

public class ResponseHandler<T> implements Handler<AsyncResult<HttpResponse<Buffer>>> {

    final CompletableFuture<T> resultHolder;
    final TypeReference<T> targetType;
    private static final ObjectMapper mapper = new ObjectMapper();

    public ResponseHandler(CompletableFuture<T> resultHolder, TypeReference<T> targetType) {
        this.resultHolder = resultHolder;
        this.targetType = targetType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(AsyncResult<HttpResponse<Buffer>> event) {

        try {
            if (isFailure(event.result().statusCode())) {
                resultHolder.completeExceptionally(ErrorHandler
                        .handleErrorResponse(IoUtil.toStream(event.result().body().getBytes()), event.result().statusCode()));
            } else if (event.succeeded()) {
                final HttpResponse<Buffer> result = event.result();
                final String typeName = targetType.getType().getTypeName();
                if (typeName.contains("InputStream")) {
                    resultHolder.complete((T) IoUtil.toStream(result.body().getBytes()));
                } else if (typeName.contains("Void")) {
                    //Intended null return
                    resultHolder.complete(null);
                } else {
                    resultHolder.complete(mapper.readValue(result.body().getBytes(), targetType));
                }
            } else {
                resultHolder.completeExceptionally(event.cause());
            }
        } catch (IOException e) {
            resultHolder.completeExceptionally(e);
        }
    }

    private static boolean isFailure(int statusCode) {
        return statusCode / 100 != 2;
    }
}

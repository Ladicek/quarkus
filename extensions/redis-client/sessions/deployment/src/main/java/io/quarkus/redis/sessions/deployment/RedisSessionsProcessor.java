package io.quarkus.redis.sessions.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.redis.deployment.client.RequestedRedisClientBuildItem;
import io.quarkus.redis.runtime.client.config.RedisConfig;
import io.quarkus.redis.sessions.runtime.RedisSessionsRecorder;
import io.quarkus.vertx.http.sessions.deployment.spi.SessionStoreRequestBuildItem;
import io.quarkus.vertx.http.sessions.deployment.spi.SessionStoreResponseBuildItem;
import io.quarkus.vertx.http.sessions.spi.SessionStoreKind;

public class RedisSessionsProcessor {
    private static final String FEATURE = "redis-sessions";

    @BuildStep
    public FeatureBuildItem featureBuildItem() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void redisSessions(SessionStoreRequestBuildItem request,
            BuildProducer<SessionStoreResponseBuildItem> response,
            BuildProducer<RequestedRedisClientBuildItem> redisRequest,
            RedisSessionsRecorder recorder) {
        if (request.is(SessionStoreKind.REDIS)) {
            response.produce(new SessionStoreResponseBuildItem(recorder.create()));
            redisRequest.produce(new RequestedRedisClientBuildItem(
                    request.clientName(RedisConfig.DEFAULT_CLIENT_NAME)));
        }
    }
}

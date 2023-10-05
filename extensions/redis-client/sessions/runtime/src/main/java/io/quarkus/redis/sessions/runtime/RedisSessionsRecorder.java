package io.quarkus.redis.sessions.runtime;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Map;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;

import io.quarkus.arc.Arc;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.runtime.client.config.RedisConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.sessions.spi.SessionStoreProvider;
import io.vertx.core.Vertx;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.sstore.redis.RedisSessionStore;
import io.vertx.redis.client.Redis;

@Recorder
public class RedisSessionsRecorder {
    public SessionStoreProvider create() {
        return new SessionStoreProvider() {
            @Override
            public SessionStore create(Vertx vertx, Map<String, Object> config) {
                String clientName = (String) config.get("clientName");
                Duration retryTimeout = (Duration) config.get("retryTimeout");
                Annotation qualifier = clientName != null
                        ? RedisClientName.Literal.of(clientName)
                        : Default.Literal.INSTANCE;
                Instance<Redis> bean = Arc.container().select(Redis.class, qualifier);
                if (bean.isResolvable()) {
                    Redis client = bean.get();
                    return RedisSessionStore.create(vertx, retryTimeout.toMillis(), client);
                }
                throw new IllegalStateException("Unknown Redis client: "
                        + (clientName != null ? clientName : RedisConfig.DEFAULT_CLIENT_NAME));
            }
        };
    }
}

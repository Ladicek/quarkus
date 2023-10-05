package io.quarkus.infinispan.sessions.runtime;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Map;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;

import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkus.arc.Arc;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.infinispan.client.runtime.InfinispanClientUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.sessions.spi.SessionStoreProvider;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.sstore.infinispan.InfinispanSessionStore;

@Recorder
public class InfinispanSessionsRecorder {
    public SessionStoreProvider create() {
        return new SessionStoreProvider() {
            @Override
            public SessionStore create(Vertx vertx, Map<String, Object> config) {
                String clientName = (String) config.get("clientName");
                String cacheName = (String) config.get("cacheName");
                Duration retryTimeout = (Duration) config.get("retryTimeout");
                Annotation qualifier = clientName != null
                        ? InfinispanClientName.Literal.of(clientName)
                        : Default.Literal.INSTANCE;
                Instance<RemoteCacheManager> bean = Arc.container().select(RemoteCacheManager.class, qualifier);
                if (bean.isResolvable()) {
                    RemoteCacheManager client = bean.get();
                    JsonObject options = new JsonObject()
                            .put("cacheName", cacheName)
                            .put("retryTimeout", retryTimeout.toMillis());
                    return InfinispanSessionStore.create(vertx, options, client);
                }
                throw new IllegalStateException("Unknown Infinispan client: "
                        + (clientName != null ? clientName : InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME));
            }
        };
    }
}

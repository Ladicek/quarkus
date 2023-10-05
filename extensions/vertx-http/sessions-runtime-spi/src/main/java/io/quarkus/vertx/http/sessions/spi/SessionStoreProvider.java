package io.quarkus.vertx.http.sessions.spi;

import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.ext.web.sstore.SessionStore;

public interface SessionStoreProvider {
    SessionStore create(Vertx vertx, Map<String, Object> config);
}

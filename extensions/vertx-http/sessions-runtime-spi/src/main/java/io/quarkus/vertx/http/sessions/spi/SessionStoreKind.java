package io.quarkus.vertx.http.sessions.spi;

public enum SessionStoreKind {
    NONE,
    REDIS,
    INFINISPAN,
}

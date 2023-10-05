package io.quarkus.vertx.http.sessions.deployment.spi;

import java.util.Objects;
import java.util.Optional;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.vertx.http.sessions.spi.SessionStoreKind;

public final class SessionStoreRequestBuildItem extends SimpleBuildItem {
    private final SessionStoreKind kind;
    private final Optional<String> clientName; // may be empty for the unnamed client

    public SessionStoreRequestBuildItem(SessionStoreKind kind, Optional<String> clientName) {
        this.kind = Objects.requireNonNull(kind);
        this.clientName = Objects.requireNonNull(clientName);
    }

    public boolean is(SessionStoreKind kind) {
        return this.kind == kind;
    }

    public String clientName(String defaultName) {
        return clientName.orElse(defaultName);
    }
}

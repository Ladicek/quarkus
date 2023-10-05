package io.quarkus.vertx.http.sessions.deployment.spi;

import java.util.Objects;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.vertx.http.sessions.spi.SessionStoreProvider;

public final class SessionStoreResponseBuildItem extends SimpleBuildItem {
    private final SessionStoreProvider provider;

    public SessionStoreResponseBuildItem(SessionStoreProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public SessionStoreProvider getProvider() {
        return provider;
    }
}

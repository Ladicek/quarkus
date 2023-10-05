package io.quarkus.vertx.http.runtime;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration of Vert.x Web sessions stored in remote Infinispan cache.
 */
@ConfigGroup
public class SessionsInfinispanConfig {
    /**
     * Name of the Infinispan cache used to store session data. If it does not exist, it is created
     * automatically from Infinispan's default template {@code DIST_SYNC}.
     */
    @ConfigItem(defaultValue = "quarkus.sessions")
    public String cacheName;

    /**
     * Maximum time to retry when retrieving session data from the Infinispan cache.
     * The Vert.x session handler retries when the session data are not found, because
     * distributing data across an Infinispan cluster may take time.
     */
    @ConfigItem(defaultValue = "5s")
    public Duration retryTimeout;
}

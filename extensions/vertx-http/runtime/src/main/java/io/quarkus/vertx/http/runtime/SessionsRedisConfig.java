package io.quarkus.vertx.http.runtime;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration of Vert.x Web sessions stored in Redis.
 */
@ConfigGroup
public class SessionsRedisConfig {
    /**
     * Maximum time to retry when retrieving session data from the Redis server.
     * The Vert.x session handler retries when the session data are not found, because
     * distributing data across a potential Redis cluster may take some time.
     */
    @ConfigItem(defaultValue = "2s")
    public Duration retryTimeout;
}

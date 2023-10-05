package io.quarkus.vertx.http.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration of Vert.x Web sessions stored in Redis.
 */
@ConfigGroup
public class SessionsRedisBuildTimeConfig {
    /**
     * Name of the Redis client configured in the Quarkus Redis extension configuration.
     * If not set, uses the default (unnamed) Redis client.
     */
    @ConfigItem
    public Optional<String> clientName;
}

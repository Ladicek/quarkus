package io.quarkus.vertx.http.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration of Vert.x Web sessions stored in remote Infinispan cache.
 */
@ConfigGroup
public class SessionsInfinispanBuildTimeConfig {
    /**
     * Name of the Infinispan client configured in the Quarkus Infinispan Client extension configuration.
     * If not set, uses the default (unnamed) Infinispan client.
     * <p>
     * Note that the Infinispan client must be configured to so that the user has necessary permissions
     * on the Infinispan server. The required minimum is the Infinispan {@code deployer} role.
     */
    @ConfigItem
    public Optional<String> clientName;
}

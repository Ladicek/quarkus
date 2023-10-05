package io.quarkus.vertx.http.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration of Vert.x Web sessions.
 */
@ConfigGroup
public class SessionsBuildTimeConfig {
    /**
     * Whether Vert.x Web support for sessions is enabled (the {@code SessionHandler} is added to the router)
     * and if so, which session store is used. For the {@code redis} and {@code infinispan} modes, the corresponding
     * Quarkus extension must be present and a connection to the data store must be configured there.
     */
    @ConfigItem(defaultValue = "disabled")
    public SessionsMode mode;

    /**
     * Configuration of sessions stored in Redis.
     */
    public SessionsRedisBuildTimeConfig redis;

    /**
     * Configuration of sessions stored in remote Infinispan cache.
     */
    public SessionsInfinispanBuildTimeConfig infinispan;

    public enum SessionsMode {
        /**
         * Support for Vert.x Web sessions is disabled.
         */
        DISABLED,
        /**
         * Support for Vert.x Web sessions is enabled and sessions are stored in memory.
         * <p>
         * In this mode, if an application is deployed in multiple replicas fronted with a load balancer,
         * it is necessary to enable sticky sessions (also known as session affinity) on the load balancer.
         * Still, losing a replica means losing all sessions stored on that replica.
         * <p>
         * In a multi-replica deployment, it is recommended to use an external session store (Redis or Infinispan).
         * Alternatively, if Vert.x clustering is enabled ({@code quarkus.vertx.cluster}), in-memory sessions
         * may be clustered ({@code quarkus.http.sessions.in-memory.clustered}), which also makes sticky sessions
         * not necessary and prevents session data loss (depending on the Vert.x cluster manager configuration).
         */
        IN_MEMORY,
        /**
         * Support for Vert.x Web sessions is enabled and sessions are stored in a remote Redis server.
         * The Quarkus Redis Sessions extension must be present and a Redis connection must be configured.
         */
        REDIS,
        /**
         * Support for Vert.x Web sessions is enabled and sessions are stored in a remote Infinispan cache.
         * The Quarkus Infinispan Sessions extension must be present and an Infinispan connection must be configured.
         */
        INFINISPAN,
    }
}

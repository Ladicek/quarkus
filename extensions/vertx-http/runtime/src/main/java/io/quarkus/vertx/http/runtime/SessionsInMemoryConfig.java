package io.quarkus.vertx.http.runtime;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration of Vert.x Web sessions stored in memory.
 */
@ConfigGroup
public class SessionsInMemoryConfig {
    /**
     * Name of the Vert.x local map or cluster-wide map to store the session data.
     */
    @ConfigItem(defaultValue = "quarkus.sessions")
    public String mapName;

    /**
     * Whether in-memory sessions are clustered.
     * <p>
     * Ignored when Vert.x clustering is not enabled.
     */
    @ConfigItem(defaultValue = "false")
    public boolean clustered;

    /**
     * Maximum time to retry when retrieving session data from the cluster-wide map.
     * The Vert.x session handler retries when the session data are not found, because
     * distributing data across the cluster may take time.
     * <p>
     * Ignored when in-memory sessions are not clustered.
     */
    @ConfigItem(defaultValue = "5s")
    public Duration retryTimeout;
}

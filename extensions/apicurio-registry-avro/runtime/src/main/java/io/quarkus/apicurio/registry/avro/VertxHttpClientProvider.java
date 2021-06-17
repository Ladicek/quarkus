package io.quarkus.apicurio.registry.avro;

import java.util.Map;

import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.rest.client.spi.RegistryHttpClient;
import io.apicurio.registry.rest.client.spi.RegistryHttpClientProvider;
import io.vertx.core.Vertx;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class VertxHttpClientProvider implements RegistryHttpClientProvider {

    private final Vertx vertx;

    public VertxHttpClientProvider(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public RegistryHttpClient create(String endpoint, Map<String, Object> configs, Auth auth) {
        return new VertxHttpClient(vertx, endpoint, configs, auth);
    }
}

package io.quarkus.apicurio.registry.avro;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Vertx;

@Recorder
public class ApicurioRegistryClient {
    public void setup(RuntimeValue<Vertx> vertx) {
        // currently, we have our own copy of the RegistryHttpClient SPI
        // implementation, because the implementation in Apicurio Registry
        // is compiled against Vert.x 3.9 (because Apicurio Registry is based
        // on Quarkus 1.13)
        //
        // when Apicurio Registry is updated to Quarkus 2 and hence Vert.x 4,
        // we should remove our implementation of the SPI and just use the one
        // from Apicurio Registry
        RegistryClientFactory.setProvider(new VertxHttpClientProvider(vertx.getValue()));
    }
}

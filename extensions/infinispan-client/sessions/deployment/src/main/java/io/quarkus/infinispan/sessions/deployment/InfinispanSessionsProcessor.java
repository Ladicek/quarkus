package io.quarkus.infinispan.sessions.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;
import io.quarkus.infinispan.client.runtime.InfinispanClientUtil;
import io.quarkus.infinispan.sessions.runtime.InfinispanSessionsRecorder;
import io.quarkus.vertx.http.sessions.deployment.spi.SessionStoreRequestBuildItem;
import io.quarkus.vertx.http.sessions.deployment.spi.SessionStoreResponseBuildItem;
import io.quarkus.vertx.http.sessions.spi.SessionStoreKind;

public class InfinispanSessionsProcessor {
    private static final String FEATURE = "infinispan-sessions";

    @BuildStep
    public FeatureBuildItem featureBuildItem() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void infinispanSessions(SessionStoreRequestBuildItem request,
            BuildProducer<SessionStoreResponseBuildItem> response,
            BuildProducer<InfinispanClientNameBuildItem> infinispanRequest,
            InfinispanSessionsRecorder recorder) {
        if (request.is(SessionStoreKind.INFINISPAN)) {
            response.produce(new SessionStoreResponseBuildItem(recorder.create()));
            infinispanRequest.produce(new InfinispanClientNameBuildItem(
                    request.clientName(InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME)));
        }
    }
}

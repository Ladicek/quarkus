package io.quarkus.vertx.runtime;

import io.quarkus.arc.Invoker;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.RecordableConstructor;
import io.quarkus.vertx.ConsumeEvent;

public class EventConsumerInfo {
    public final ConsumeEvent annotation;
    public final boolean blockingAnnotation;
    public final boolean runOnVirtualThreadAnnotation;
    public final boolean splitHeadersBodyParams;
    public final RuntimeValue<Invoker<Object, Object>> invoker;

    @RecordableConstructor
    public EventConsumerInfo(ConsumeEvent annotation, boolean blockingAnnotation, boolean runOnVirtualThreadAnnotation,
            boolean splitHeadersBodyParams, RuntimeValue<Invoker<Object, Object>> invoker) {
        this.annotation = annotation;
        this.blockingAnnotation = blockingAnnotation;
        this.runOnVirtualThreadAnnotation = runOnVirtualThreadAnnotation;
        this.splitHeadersBodyParams = splitHeadersBodyParams;
        this.invoker = invoker;
    }
}

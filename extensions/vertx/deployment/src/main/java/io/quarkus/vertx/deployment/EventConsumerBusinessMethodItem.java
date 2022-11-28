package io.quarkus.vertx.deployment;

import org.jboss.jandex.AnnotationInstance;

import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.builder.item.MultiBuildItem;

public final class EventConsumerBusinessMethodItem extends MultiBuildItem {

    private final BeanInfo bean;
    private final AnnotationInstance consumeEvent;
    private final boolean blockingAnnotation;
    private final boolean runOnVirtualThreadAnnotation;
    private final boolean splitHeadersBodyParams;
    private final InvokerInfo invoker;

    public EventConsumerBusinessMethodItem(BeanInfo bean, AnnotationInstance consumeEvent, boolean blockingAnnotation,
            boolean runOnVirtualThreadAnnotation, boolean splitHeadersBodyParams, InvokerInfo invoker) {
        this.bean = bean;
        this.consumeEvent = consumeEvent;
        this.blockingAnnotation = blockingAnnotation;
        this.runOnVirtualThreadAnnotation = runOnVirtualThreadAnnotation;
        this.splitHeadersBodyParams = splitHeadersBodyParams;
        this.invoker = invoker;
    }

    public BeanInfo getBean() {
        return bean;
    }

    public AnnotationInstance getConsumeEvent() {
        return consumeEvent;
    }

    public boolean isBlockingAnnotation() {
        return blockingAnnotation;
    }

    public boolean isRunOnVirtualThreadAnnotation() {
        return runOnVirtualThreadAnnotation;
    }

    public boolean isSplitHeadersBodyParams() {
        return splitHeadersBodyParams;
    }

    public InvokerInfo getInvoker() {
        return invoker;
    }

}

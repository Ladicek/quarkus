package io.quarkus.vertx.deployment;

import static io.quarkus.vertx.deployment.VertxConstants.CONSUME_EVENT;
import static io.quarkus.vertx.deployment.VertxConstants.MESSAGE;
import static io.quarkus.vertx.deployment.VertxConstants.MUTINY_MESSAGE;
import static io.quarkus.vertx.deployment.VertxConstants.MUTINY_MESSAGE_HEADERS;
import static io.quarkus.vertx.deployment.VertxConstants.UNI;
import static io.quarkus.vertx.deployment.VertxConstants.isMessage;
import static io.quarkus.vertx.deployment.VertxConstants.isMessageHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.jboss.logging.Logger;

import io.quarkus.arc.Invoker;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem;
import io.quarkus.arc.deployment.CurrentContextFactoryBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem.BeanClassAnnotationExclusion;
import io.quarkus.arc.processor.AnnotationStore;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AnnotationProxyBuildItem;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.vertx.ConsumeEvent;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.runtime.EventConsumerInfo;
import io.quarkus.vertx.runtime.VertxEventBusConsumerRecorder;
import io.quarkus.vertx.runtime.VertxProducer;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;

class VertxProcessor {

    private static final Logger LOGGER = Logger.getLogger(VertxProcessor.class.getName());

    @BuildStep
    void featureAndCapability(BuildProducer<FeatureBuildItem> feature, BuildProducer<CapabilityBuildItem> capability) {
        feature.produce(new FeatureBuildItem(Feature.VERTX));
    }

    @BuildStep
    AdditionalBeanBuildItem registerBean() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClass(ConsumeEvent.class)
                .addBeanClass(VertxProducer.class)
                .build();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    VertxBuildItem build(CoreVertxBuildItem vertx, VertxEventBusConsumerRecorder recorder,
            List<EventConsumerBusinessMethodItem> messageConsumerBusinessMethods,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            AnnotationProxyBuildItem annotationProxy, LaunchModeBuildItem launchMode, ShutdownContextBuildItem shutdown,
            BuildProducer<ServiceStartBuildItem> serviceStart,
            List<MessageCodecBuildItem> codecs, RecorderContext recorderContext) {
        List<EventConsumerInfo> messageConsumerConfigurations = new ArrayList<>();
        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClass, true);
        for (EventConsumerBusinessMethodItem businessMethod : messageConsumerBusinessMethods) {
            ConsumeEvent annotation = annotationProxy.builder(businessMethod.getConsumeEvent(), ConsumeEvent.class)
                    .withDefaultValue("value", businessMethod.getBean().getBeanClass().toString())
                    .build(classOutput);

            RuntimeValue<Invoker<Object, Object>> invoker = recorderContext.staticInstance(
                    businessMethod.getInvoker().getClassName(), Invoker.class);

            messageConsumerConfigurations.add(new EventConsumerInfo(annotation, businessMethod.isBlockingAnnotation(),
                    businessMethod.isRunOnVirtualThreadAnnotation(), businessMethod.isSplitHeadersBodyParams(),
                    invoker));
        }

        Map<Class<?>, Class<?>> codecByClass = new HashMap<>();
        for (MessageCodecBuildItem messageCodecItem : codecs) {
            codecByClass.put(recorderContext.classProxy(messageCodecItem.getType()),
                    recorderContext.classProxy(messageCodecItem.getCodec()));
        }

        recorder.configureVertx(vertx.getVertx(), messageConsumerConfigurations,
                launchMode.getLaunchMode(),
                shutdown, codecByClass);
        serviceStart.produce(new ServiceStartBuildItem("vertx"));
        return new VertxBuildItem(recorder.forceStart(vertx.getVertx()));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void currentContextFactory(BuildProducer<CurrentContextFactoryBuildItem> currentContextFactory,
            VertxBuildConfig buildConfig, VertxEventBusConsumerRecorder recorder) {
        if (buildConfig.customizeArcContext()) {
            currentContextFactory.produce(new CurrentContextFactoryBuildItem(recorder.currentContextFactory()));
        }
    }

    @BuildStep
    public UnremovableBeanBuildItem unremovableBeans() {
        return new UnremovableBeanBuildItem(new BeanClassAnnotationExclusion(CONSUME_EVENT));
    }

    @BuildStep
    void collectEventConsumers(
            BeanRegistrationPhaseBuildItem beanRegistrationPhase,
            BuildProducer<EventConsumerBusinessMethodItem> messageConsumerBusinessMethods,
            BuildProducer<BeanConfiguratorBuildItem> errors) {
        // We need to collect all business methods annotated with @ConsumeEvent first
        AnnotationStore annotationStore = beanRegistrationPhase.getContext().get(BuildExtension.Key.ANNOTATION_STORE);
        for (BeanInfo bean : beanRegistrationPhase.getContext().beans().classBeans()) {
            for (MethodInfo method : bean.getTarget().get().asClass().methods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                AnnotationInstance consumeEvent = annotationStore.getAnnotation(method, CONSUME_EVENT);
                if (consumeEvent != null) {
                    // Validate method params and return type
                    List<Type> params = method.parameterTypes();
                    if (params.size() == 2) {
                        if (!isMessageHeaders(params.get(0).name())) {
                            // If there are two parameters, the first must be message headers.
                            throw new IllegalStateException(String.format(
                                    "An event consumer business method with two parameters must have MultiMap as the first parameter: %s [method: %s, bean:%s]",
                                    params, method, bean));
                        } else if (isMessage(params.get(1).name())) {
                            throw new IllegalStateException(String.format(
                                    "An event consumer business method with two parameters must not accept io.vertx.core.eventbus.Message or io.vertx.mutiny.core.eventbus.Message: %s [method: %s, bean:%s]",
                                    params, method, bean));
                        }
                    } else if (params.size() != 1) {
                        throw new IllegalStateException(String.format(
                                "An event consumer business method must accept exactly one parameter: %s [method: %s, bean:%s]",
                                params, method, bean));
                    }
                    if (method.returnType().kind() != Kind.VOID && VertxConstants.isMessage(params.get(0).name())) {
                        throw new IllegalStateException(String.format(
                                "An event consumer business method that accepts io.vertx.core.eventbus.Message or io.vertx.mutiny.core.eventbus.Message must return void [method: %s, bean:%s]",
                                method, bean));
                    }
                    if (method.hasAnnotation(RunOnVirtualThread.class) && consumeEvent.value("ordered") != null
                            && consumeEvent.value("ordered").asBoolean()) {
                        throw new IllegalStateException(String.format(
                                "An event consumer business method that cannot use @RunOnVirtualThread and set the ordered attribute to true [method: %s, bean:%s]",
                                method, bean));
                    }

                    InvokerBuilder builder = bean.createInvoker(method)
                            .setInstanceLookup();

                    if (method.parametersCount() == 1 && method.parameterType(0).name().equals(MESSAGE)) {
                        // io.vertx.core.eventbus.Message
                        // no transformation required
                    } else if (method.parametersCount() == 1 && method.parameterType(0).name().equals(MUTINY_MESSAGE)) {
                        // io.vertx.mutiny.core.eventbus.Message
                        builder.setArgumentTransformer(0, io.vertx.mutiny.core.eventbus.Message.class, "newInstance");
                    } else if (method.parametersCount() == 1) {
                        // parameter is payload
                        builder.setArgumentTransformer(0, io.vertx.core.eventbus.Message.class, "body");
                    } else if (method.parametersCount() == 2 && method.parameterType(0).name().equals(MUTINY_MESSAGE_HEADERS)) {
                        // if the method expects Mutiny MultiMap, wrap the Vert.x MultiMap
                        builder.setArgumentTransformer(0, io.vertx.mutiny.core.MultiMap.class, "newInstance");
                    }

                    if (method.returnType().name().equals(UNI)) {
                        builder.setReturnValueTransformer(Uni.class, "subscribeAsCompletionStage");
                    }

                    InvokerInfo invoker = builder.build();

                    messageConsumerBusinessMethods.produce(new EventConsumerBusinessMethodItem(bean, consumeEvent,
                            method.hasAnnotation(Blocking.class), method.hasAnnotation(RunOnVirtualThread.class),
                            params.size() == 2, invoker));
                    LOGGER.debugf("Found event consumer business method %s declared on %s", method, bean);
                }
            }
        }
    }

    @BuildStep
    AutoAddScopeBuildItem autoAddScope() {
        // Add @Singleton to a class with no scope annotation but with a method annotated with @ConsumeEvent
        return AutoAddScopeBuildItem.builder().containsAnnotations(CONSUME_EVENT).defaultScope(BuiltinScope.SINGLETON)
                .reason("Found event consumer business methods").build();
    }

    @BuildStep
    void registerVerticleClasses(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        // Mutiny Verticles
        for (ClassInfo ci : indexBuildItem.getIndex()
                .getAllKnownSubclasses(DotName.createSimple(io.smallrye.mutiny.vertx.core.AbstractVerticle.class.getName()))) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(ci.toString()).build());
        }
    }

    @BuildStep
    void faultToleranceIntegration(Capabilities capabilities, BuildProducer<ServiceProviderBuildItem> serviceProvider) {
        if (capabilities.isPresent(Capability.SMALLRYE_FAULT_TOLERANCE)) {
            serviceProvider.produce(new ServiceProviderBuildItem(
                    "io.smallrye.faulttolerance.core.event.loop.EventLoop",
                    "io.smallrye.faulttolerance.vertx.VertxEventLoop"));
        }
    }
}

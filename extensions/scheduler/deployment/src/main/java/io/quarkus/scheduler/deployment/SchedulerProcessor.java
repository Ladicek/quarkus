package io.quarkus.scheduler.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static org.jboss.jandex.AnnotationTarget.Kind.METHOD;
import static org.jboss.jandex.AnnotationValue.createArrayValue;
import static org.jboss.jandex.AnnotationValue.createBooleanValue;
import static org.jboss.jandex.AnnotationValue.createStringValue;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.jboss.logging.Logger;

import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

import io.quarkus.arc.Invoker;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem.BeanClassAnnotationExclusion;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.arc.processor.BeanDeploymentValidator;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AnnotationProxyBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.common.runtime.MutableScheduledMethod;
import io.quarkus.scheduler.common.runtime.SchedulerContext;
import io.quarkus.scheduler.common.runtime.util.SchedulerUtils;
import io.quarkus.scheduler.kotlin.runtime.CoroutineInvoker;
import io.quarkus.scheduler.runtime.SchedulerConfig;
import io.quarkus.scheduler.runtime.SchedulerRecorder;
import io.quarkus.scheduler.runtime.SimpleScheduler;
import io.smallrye.mutiny.Uni;

public class SchedulerProcessor {

    private static final Logger LOGGER = Logger.getLogger(SchedulerProcessor.class);

    static final Type SCHEDULED_EXECUTION_TYPE = Type.create(DotName.createSimple(ScheduledExecution.class.getName()),
            Kind.CLASS);

    static final String INVOKER_SUFFIX = "_ScheduledInvoker";
    static final String NESTED_SEPARATOR = "$_";

    @BuildStep
    void beans(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (capabilities.isMissing(Capability.QUARTZ)) {
            additionalBeans.produce(new AdditionalBeanBuildItem(SimpleScheduler.class, Scheduled.ApplicationNotRunning.class));
        }
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(Scheduled.class, Scheduled.Schedules.class).build());
    }

    @BuildStep
    AutoAddScopeBuildItem autoAddScope() {
        // We add @Singleton to any bean class that has no scope annotation and declares at least one method annotated with @Scheduled
        return AutoAddScopeBuildItem.builder()
                .anyMethodMatches(m -> m.hasAnnotation(SchedulerDotNames.SCHEDULED_NAME)
                        || m.hasAnnotation(SchedulerDotNames.SCHEDULES_NAME))
                .defaultScope(BuiltinScope.SINGLETON)
                .reason("Found scheduled business methods").build();
    }

    @BuildStep
    void collectScheduledMethods(BeanArchiveIndexBuildItem beanArchives, BeanDiscoveryFinishedBuildItem beanDiscovery,
            TransformedAnnotationsBuildItem transformedAnnotations,
            BuildProducer<ScheduledBusinessMethodItem> scheduledBusinessMethods) {

        for (BeanInfo bean : beanDiscovery.beanStream().classBeans()) {
            ClassInfo beanClass = bean.getTarget().get().asClass();

            for (MethodInfo method : beanClass.methods()) {
                List<AnnotationInstance> schedules = null;
                AnnotationInstance scheduledAnnotation = transformedAnnotations.getAnnotation(method,
                        SchedulerDotNames.SCHEDULED_NAME);
                if (scheduledAnnotation != null) {
                    schedules = List.of(scheduledAnnotation);
                } else {
                    AnnotationInstance schedulesAnnotation = transformedAnnotations.getAnnotation(method,
                            SchedulerDotNames.SCHEDULES_NAME);
                    if (schedulesAnnotation != null) {
                        schedules = new ArrayList<>();
                        for (AnnotationInstance scheduledInstance : schedulesAnnotation.value().asNestedArray()) {
                            // We need to set the target of the containing instance
                            schedules.add(AnnotationInstance.create(scheduledInstance.name(), schedulesAnnotation.target(),
                                    scheduledInstance.values()));
                        }
                    }
                }
                if (schedules != null) {
                    scheduledBusinessMethods.produce(new ScheduledBusinessMethodItem(bean, method, schedules,
                            transformedAnnotations.hasAnnotation(method, SchedulerDotNames.NON_BLOCKING)));
                    LOGGER.debugf("Found scheduled business method %s declared on %s", method, bean);
                }
            }
        }
    }

    @BuildStep
    void validateScheduledBusinessMethods(SchedulerConfig config, List<ScheduledBusinessMethodItem> scheduledMethods,
            ValidationPhaseBuildItem validationPhase, BuildProducer<ValidationErrorBuildItem> validationErrors) {
        List<Throwable> errors = new ArrayList<>();
        Map<String, AnnotationInstance> encounteredIdentities = new HashMap<>();
        Set<String> methodDescriptions = new HashSet<>();

        for (ScheduledBusinessMethodItem scheduledMethod : scheduledMethods) {
            if (!methodDescriptions.add(scheduledMethod.getMethodDescription())) {
                errors.add(new IllegalStateException("Multiple @Scheduled methods of the same name declared on the same class: "
                        + scheduledMethod.getMethodDescription()));
                continue;
            }
            MethodInfo method = scheduledMethod.getMethod();
            if (Modifier.isAbstract(method.flags())) {
                errors.add(new IllegalStateException("@Scheduled method must not be abstract: "
                        + scheduledMethod.getMethodDescription()));
                continue;
            }
            if (Modifier.isPrivate(method.flags())) {
                errors.add(new IllegalStateException("@Scheduled method must not be private: "
                        + scheduledMethod.getMethodDescription()));
                continue;
            }

            boolean isSuspendMethod = KotlinUtil.isSuspendMethod(method);

            // Validate method params and return type
            List<Type> params = method.parameterTypes();
            int maxParamSize = isSuspendMethod ? 2 : 1;
            if (params.size() > maxParamSize
                    || (params.size() == maxParamSize && !params.get(0).equals(SCHEDULED_EXECUTION_TYPE))) {
                errors.add(new IllegalStateException(String.format(
                        "Invalid scheduled business method parameters %s [method: %s, bean: %s]", params,
                        method, scheduledMethod.getBean())));
            }
            if (!isValidReturnType(method)) {
                if (isSuspendMethod) {
                    errors.add(new IllegalStateException(
                            String.format(
                                    "Suspending scheduled business method must return Unit [method: %s, bean: %s]",
                                    method, scheduledMethod.getBean())));
                } else {
                    errors.add(new IllegalStateException(
                            String.format(
                                    "Scheduled business method must return void, CompletionStage<Void> or Uni<Void> [method: %s, bean: %s]",
                                    method, scheduledMethod.getBean())));
                }
            }
            // Validate cron() and every() expressions
            CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(config.cronType));
            for (AnnotationInstance scheduled : scheduledMethod.getSchedules()) {
                Throwable error = validateScheduled(parser, scheduled, encounteredIdentities, validationPhase.getContext());
                if (error != null) {
                    errors.add(error);
                }
            }
        }

        if (!errors.isEmpty()) {
            validationErrors.produce(new ValidationErrorBuildItem(errors));
        }
    }

    private boolean isValidReturnType(MethodInfo method) {
        Type returnType = method.returnType();
        if (returnType.kind() == Kind.VOID) {
            return true;
        }
        if (SchedulerDotNames.COMPLETION_STAGE.equals(returnType.name())
                && returnType.asParameterizedType().arguments().get(0).name().equals(SchedulerDotNames.VOID)) {
            return true;
        }
        if (SchedulerDotNames.UNI.equals(returnType.name())
                && returnType.asParameterizedType().arguments().get(0).name().equals(SchedulerDotNames.VOID)) {
            return true;
        }
        if (KotlinUtil.isSuspendMethod(method)
                && SchedulerDotNames.VOID.equals(KotlinUtil.determineReturnTypeOfSuspendMethod(method).name())) {
            return true;
        }
        return false;
    }

    @BuildStep
    public List<UnremovableBeanBuildItem> unremovableBeans() {
        // Beans annotated with @Scheduled should never be removed
        return List.of(new UnremovableBeanBuildItem(new BeanClassAnnotationExclusion(SchedulerDotNames.SCHEDULED_NAME)),
                new UnremovableBeanBuildItem(new BeanClassAnnotationExclusion(SchedulerDotNames.SCHEDULES_NAME)));
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public FeatureBuildItem build(SchedulerConfig config, BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            SchedulerRecorder recorder, List<ScheduledBusinessMethodItem> scheduledMethods,
            BuildProducer<GeneratedClassBuildItem> generatedClasses, AnnotationProxyBuildItem annotationProxy,
            RecorderContext recorderContext) {

        List<MutableScheduledMethod> scheduledMetadata = new ArrayList<>();
        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses, new Function<String, String>() {
            @Override
            public String apply(String name) {
                // org/acme/Foo_ScheduledInvoker_run_0000 -> org.acme.Foo
                int idx = name.indexOf(INVOKER_SUFFIX);
                if (idx != -1) {
                    name = name.substring(0, idx);
                }
                if (name.contains(NESTED_SEPARATOR)) {
                    name = name.replace(NESTED_SEPARATOR, "$");
                }
                return name;
            }
        });

        for (ScheduledBusinessMethodItem scheduledMethod : scheduledMethods) {
            MutableScheduledMethod metadata = new MutableScheduledMethod();
            BeanInfo bean = scheduledMethod.getBean();
            MethodInfo method = scheduledMethod.getMethod();

            InvokerBuilder invokerBuilder = bean.createInvoker(method);
            Type returnType = method.returnType();
            if (returnType.kind() == Kind.VOID) {
                invokerBuilder.setReturnValueTransformer(CompletableFuture.class, "completedStage")
                        .setExceptionTransformer(CompletableFuture.class, "failedStage");
            } else if (SchedulerDotNames.COMPLETION_STAGE.equals(returnType.name())) {
                invokerBuilder.setExceptionTransformer(CompletableFuture.class, "failedStage");
            } else if (SchedulerDotNames.UNI.equals(returnType.name())) {
                invokerBuilder.setReturnValueTransformer(Uni.class, "subscribeAsCompletionStage")
                        .setExceptionTransformer(CompletableFuture.class, "failedStage");
            } else if (KotlinUtil.isSuspendMethod(method)) {
                invokerBuilder.setInvocationWrapper(CoroutineInvoker.class, "inNewCoroutine");
            }

            if (!Modifier.isStatic(method.flags())) {
                invokerBuilder.setInstanceLookup();
            }
            InvokerInfo invoker = invokerBuilder.build();

            metadata.setInvokerClassName(invoker.getClassName());
            metadata.setDeclaringClassName(method.declaringClass().toString());
            metadata.setMethodName(method.name());
            List<Scheduled> schedules = new ArrayList<>();
            for (AnnotationInstance scheduled : scheduledMethod.getSchedules()) {
                schedules.add(annotationProxy.builder(scheduled, Scheduled.class).build(classOutput));
            }
            metadata.setSchedules(schedules);
            metadata.setInvoker(recorderContext.staticInstance(invoker.getClassName(), Invoker.class));
            metadata.setNonBlocking(scheduledMethod.isNonBlocking());
            metadata.setScheduledExecutionArgument(method.parametersCount() > 0
                    && method.parameterType(0).equals(SCHEDULED_EXECUTION_TYPE));
            scheduledMetadata.add(metadata);
        }

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(SchedulerContext.class).setRuntimeInit()
                .supplier(recorder.createContext(config, scheduledMetadata))
                .done());

        return new FeatureBuildItem(Feature.SCHEDULER);
    }

    @BuildStep
    public void metrics(SchedulerConfig config,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformer) {

        if (config.metricsEnabled && metricsCapability.isPresent()) {
            DotName micrometerTimed = DotName.createSimple("io.micrometer.core.annotation.Timed");
            DotName mpTimed = DotName.createSimple("org.eclipse.microprofile.metrics.annotation.Timed");

            annotationsTransformer.produce(new AnnotationsTransformerBuildItem(AnnotationsTransformer.builder()
                    .appliesTo(METHOD)
                    .whenContainsAny(List.of(SchedulerDotNames.SCHEDULED_NAME, SchedulerDotNames.SCHEDULES_NAME))
                    .whenContainsNone(List.of(micrometerTimed,
                            mpTimed, DotName.createSimple("org.eclipse.microprofile.metrics.annotation.SimplyTimed")))
                    .transform(context -> {
                        // Transform a @Scheduled method that has no metrics timed annotation
                        MethodInfo scheduledMethod = context.getTarget().asMethod();
                        if (metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER)) {
                            // Micrometer
                            context.transform()
                                    .add(micrometerTimed, createStringValue("value", "scheduled.methods"))
                                    .add(micrometerTimed, createStringValue("value", "scheduled.methods.running"),
                                            createBooleanValue("longTask", true))
                                    .done();
                            LOGGER.debugf("Added Micrometer @Timed to a @Scheduled method %s#%s()",
                                    scheduledMethod.declaringClass().name(),
                                    scheduledMethod.name());
                        } else if (metricsCapability.get().metricsSupported(MetricsFactory.MP_METRICS)) {
                            // MP metrics
                            context.transform()
                                    .add(mpTimed,
                                            createArrayValue("tags",
                                                    new AnnotationValue[] { createStringValue("scheduled", "scheduled=true") }))
                                    .done();
                            LOGGER.debugf("Added MP Metrics @Timed to a @Scheduled method %s#%s()",
                                    scheduledMethod.declaringClass().name(),
                                    scheduledMethod.name());
                        }
                    })));
        }
    }

    @BuildStep
    public void tracing(SchedulerConfig config,
            Capabilities capabilities, BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformer) {

        if (config.tracingEnabled && capabilities.isPresent(Capability.OPENTELEMETRY_TRACER)) {
            DotName withSpan = DotName.createSimple("io.opentelemetry.instrumentation.annotations.WithSpan");
            DotName legacyWithSpan = DotName.createSimple("io.opentelemetry.extension.annotations.WithSpan");

            annotationsTransformer.produce(new AnnotationsTransformerBuildItem(AnnotationsTransformer.builder()
                    .appliesTo(METHOD)
                    .whenContainsAny(List.of(SchedulerDotNames.SCHEDULED_NAME, SchedulerDotNames.SCHEDULES_NAME))
                    .whenContainsNone(List.of(withSpan, legacyWithSpan))
                    .transform(context -> {
                        MethodInfo scheduledMethod = context.getTarget().asMethod();
                        context.transform()
                                .add(withSpan)
                                .done();
                        LOGGER.debugf("Added OpenTelemetry @WithSpan to a @Scheduled method %s#%s()",
                                scheduledMethod.declaringClass().name(),
                                scheduledMethod.name());
                    })));
        }
    }

    private Throwable validateScheduled(CronParser parser, AnnotationInstance schedule,
            Map<String, AnnotationInstance> encounteredIdentities,
            BeanDeploymentValidator.ValidationContext validationContext) {
        MethodInfo method = schedule.target().asMethod();
        AnnotationValue cronValue = schedule.value("cron");
        AnnotationValue everyValue = schedule.value("every");
        if (cronValue != null && !cronValue.asString().trim().isEmpty()) {
            String cron = cronValue.asString().trim();
            if (!SchedulerUtils.isConfigValue(cron)) {
                try {
                    parser.parse(cron).validate();
                } catch (IllegalArgumentException e) {
                    return new IllegalStateException("Invalid cron() expression on: " + schedule, e);
                }
                if (everyValue != null && !everyValue.asString().trim().isEmpty()) {
                    LOGGER.warnf(
                            "%s declared on %s#%s() defines both cron() and every() - the cron expression takes precedence",
                            schedule, method.declaringClass().name(), method.name());
                }
            }
            // Validate the time zone ID
            AnnotationValue timeZoneValue = schedule.value("timeZone");
            if (timeZoneValue != null) {
                String timeZone = timeZoneValue.asString();
                if (!SchedulerUtils.isConfigValue(timeZone) && !timeZone.equals(Scheduled.DEFAULT_TIMEZONE)) {
                    try {
                        ZoneId.of(timeZone);
                    } catch (Exception e) {
                        return new IllegalStateException("Invalid timeZone() on " + schedule, e);
                    }
                }
            }

        } else {
            if (everyValue != null && !everyValue.asString().trim().isEmpty()) {
                String every = everyValue.asString().trim();
                if (!SchedulerUtils.isConfigValue(every)) {
                    if (Character.isDigit(every.charAt(0))) {
                        every = "PT" + every;
                    }
                    try {
                        Duration.parse(every);
                    } catch (Exception e) {
                        return new IllegalStateException("Invalid every() expression on: " + schedule, e);
                    }
                }
            } else {
                return new IllegalStateException("@Scheduled must declare either cron() or every(): " + schedule);
            }
        }
        AnnotationValue delay = schedule.value("delay");
        AnnotationValue delayedValue = schedule.value("delayed");
        if (delay == null || delay.asLong() <= 0) {
            if (delayedValue != null && !delayedValue.asString().trim().isEmpty()) {
                String delayed = delayedValue.asString().trim();
                if (!SchedulerUtils.isConfigValue(delayed)) {
                    if (Character.isDigit(delayed.charAt(0))) {
                        delayed = "PT" + delayed;
                    }
                    try {
                        Duration.parse(delayed);
                    } catch (Exception e) {
                        return new IllegalStateException("Invalid delayed() expression on: " + schedule, e);
                    }
                }

            }
        } else {
            if (delayedValue != null && !delayedValue.asString().trim().isEmpty()) {
                LOGGER.warnf(
                        "%s declared on %s#%s() defines both delay() and delayed() - the delayed() value is ignored",
                        schedule, method.declaringClass().name(), method.name());
            }
        }

        AnnotationValue identityValue = schedule.value("identity");
        if (identityValue != null) {
            String identity = SchedulerUtils.lookUpPropertyValue(identityValue.asString());
            AnnotationInstance previousInstanceWithSameIdentity = encounteredIdentities.get(identity);
            if (previousInstanceWithSameIdentity != null) {
                String message = String.format("The identity: \"%s\" on: %s is not unique and it has already bean used by : %s",
                        identity, schedule, previousInstanceWithSameIdentity);
                return new IllegalStateException(message);
            } else {
                encounteredIdentities.put(identity, schedule);
            }
        }

        AnnotationValue skipExecutionIfValue = schedule.value("skipExecutionIf");
        if (skipExecutionIfValue != null) {
            DotName skipPredicate = skipExecutionIfValue.asClass().name();
            if (!SchedulerDotNames.SKIP_NEVER_NAME.equals(skipPredicate)
                    && validationContext.beans().withBeanType(skipPredicate).collect().size() != 1) {
                String message = String.format("There must be exactly one bean that matches the skip predicate: \"%s\" on: %s",
                        skipPredicate, schedule);
                return new IllegalStateException(message);
            }
        }

        return null;
    }

    @BuildStep
    UnremovableBeanBuildItem unremoveableSkipPredicates() {
        return new UnremovableBeanBuildItem(new UnremovableBeanBuildItem.BeanTypeExclusion(SchedulerDotNames.SKIP_PREDICATE));
    }

    @BuildStep
    void produceCoroutineScope(BuildProducer<AdditionalBeanBuildItem> buildItemBuildProducer) {
        if (!QuarkusClassLoader.isClassPresentAtRuntime("kotlinx.coroutines.CoroutineScope")) {
            return;
        }

        buildItemBuildProducer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass("io.quarkus.scheduler.kotlin.runtime.ApplicationCoroutineScope")
                .setUnremovable().build());
    }

}

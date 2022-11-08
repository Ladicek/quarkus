package io.quarkus.resteasy.reactive.kotlin.deployment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.resteasy.reactive.common.processor.EndpointIndexer;
import org.jboss.resteasy.reactive.server.core.parameters.NullParamExtractor;
import org.jboss.resteasy.reactive.server.core.parameters.ParameterExtractor;
import org.jboss.resteasy.reactive.server.handlers.PublisherResponseHandler;
import org.jboss.resteasy.reactive.server.model.FixedHandlersChainCustomizer;
import org.jboss.resteasy.reactive.server.model.HandlerChainCustomizer;
import org.jboss.resteasy.reactive.server.processor.scanning.MethodScanner;
import org.jboss.resteasy.reactive.server.runtime.kotlin.CoroutineMethodProcessor;
import org.jboss.resteasy.reactive.server.runtime.kotlin.FlowToPublisherHandler;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.resteasy.reactive.server.spi.MethodScannerBuildItem;

public class KotlinCoroutineIntegrationProcessor {

    static final DotName CONTINUATION = DotName.createSimple("kotlin.coroutines.Continuation");
    static final DotName FLOW = DotName.createSimple("kotlinx.coroutines.flow.Flow");
    public static final String NAME = KotlinCoroutineIntegrationProcessor.class.getName();
    private static final DotName BLOCKING_ANNOTATION = DotName.createSimple("io.smallrye.common.annotation.Blocking");

    @BuildStep
    void produceCoroutineScope(BuildProducer<AdditionalBeanBuildItem> buildItemBuildProducer) {
        buildItemBuildProducer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        "org.jboss.resteasy.reactive.server.runtime.kotlin.CoroutineInvocationHandlerFactory",
                        "org.jboss.resteasy.reactive.server.runtime.kotlin.ApplicationCoroutineScope")
                .setUnremovable().build());
    }

    @BuildStep
    MethodScannerBuildItem scanner() {
        return new MethodScannerBuildItem(new MethodScanner() {
            @SuppressWarnings("unchecked")
            @Override
            public List<HandlerChainCustomizer> scan(MethodInfo method, ClassInfo actualEndpointClass,
                    Map<String, Object> methodContext) {
                if (methodContext.containsKey(NAME)) { //method is suspendable, we need to handle the invocation differently

                    ensureNotBlocking(method);

                    CoroutineMethodProcessor processor = new CoroutineMethodProcessor();
                    if (methodContext.containsKey(EndpointIndexer.METHOD_CONTEXT_CUSTOM_RETURN_TYPE_KEY)) {
                        Type methodReturnType = (Type) methodContext.get(EndpointIndexer.METHOD_CONTEXT_CUSTOM_RETURN_TYPE_KEY);
                        if (methodReturnType != null) {
                            if (methodReturnType.name().equals(FLOW)) {
                                return List.of(processor, flowCustomizer());
                            }
                        }
                    }
                    return Collections.singletonList(processor);
                }
                return Collections.emptyList();
            }

            private void ensureNotBlocking(MethodInfo method) {
                if (method.annotation(BLOCKING_ANNOTATION) != null) {
                    String format = String.format("Suspendable @Blocking methods are not supported yet: %s.%s",
                            method.declaringClass().name(), method.name());
                    throw new IllegalStateException(format);
                }
            }

            @Override
            public ParameterExtractor handleCustomParameter(Type paramType, Map<DotName, AnnotationInstance> annotations,
                    boolean field, Map<String, Object> methodContext) {
                //look for methods that take a Continuation, these are suspendable and need to be handled differently
                if (paramType.name().equals(CONTINUATION)) {
                    methodContext.put(NAME, true);
                    if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                        Type firstGenericType = paramType.asParameterizedType().arguments().get(0);
                        if (firstGenericType.kind() == Type.Kind.WILDCARD_TYPE) {
                            methodContext.put(EndpointIndexer.METHOD_CONTEXT_CUSTOM_RETURN_TYPE_KEY,
                                    firstGenericType.asWildcardType().superBound());
                        }

                    }
                    return new NullParamExtractor();
                }
                return null;
            }

            @Override
            public boolean isMethodSignatureAsync(MethodInfo info) {
                for (var param : info.parameterTypes()) {
                    if (param.name().equals(CONTINUATION)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @BuildStep
    public MethodScannerBuildItem flowSupport() {
        return new MethodScannerBuildItem(new MethodScanner() {
            @Override
            public List<HandlerChainCustomizer> scan(MethodInfo method, ClassInfo actualEndpointClass,
                    Map<String, Object> methodContext) {
                DotName returnTypeName = method.returnType().name();
                if (returnTypeName.equals(FLOW)) {
                    return Collections.singletonList(flowCustomizer());
                }
                return Collections.emptyList();
            }

            @Override
            public boolean isMethodSignatureAsync(MethodInfo info) {
                return info.returnType().name().equals(FLOW);
            }
        });
    }

    private static HandlerChainCustomizer flowCustomizer() {
        return new FixedHandlersChainCustomizer(
                List.of(new FlowToPublisherHandler(), new PublisherResponseHandler()),
                HandlerChainCustomizer.Phase.AFTER_METHOD_INVOKE);
    }
}

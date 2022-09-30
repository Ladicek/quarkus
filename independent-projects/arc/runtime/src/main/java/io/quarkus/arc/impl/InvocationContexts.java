package io.quarkus.arc.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.interceptor.InvocationContext;

import io.quarkus.arc.MethodMetadata;

public final class InvocationContexts {

    private InvocationContexts() {
    }

    /**
     *
     * @param target
     * @param method
     * @param methodMetadata
     * @param aroundInvokeForward
     * @param args
     * @param chain
     * @param interceptorBindings
     * @return the return value
     * @throws Exception
     */
    public static Object performAroundInvoke(Object target, Method method, MethodMetadata methodMetadata,
            Function<InvocationContext, Object> aroundInvokeForward, Object[] args,
            List<InterceptorInvocation> chain,
            Set<Annotation> interceptorBindings) throws Exception {
        return AroundInvokeInvocationContext.perform(target, method, methodMetadata, aroundInvokeForward, args, chain,
                interceptorBindings);
    }

    /**
     *
     * @param target
     * @param chain
     * @param interceptorBindings
     * @return a new invocation context
     */
    public static InvocationContext postConstruct(Object target, List<InterceptorInvocation> chain,
            Set<Annotation> interceptorBindings) {
        return new LifecycleCallbackInvocationContext(target, null, null, interceptorBindings, chain);
    }

    /**
     *
     * @param target
     * @param chain
     * @param interceptorBindings
     * @return a new invocation context
     */
    public static InvocationContext preDestroy(Object target, List<InterceptorInvocation> chain,
            Set<Annotation> interceptorBindings) {
        return new LifecycleCallbackInvocationContext(target, null, null, interceptorBindings, chain);
    }

    /**
     *
     * @param constructor
     * @param methodMetadata
     * @param chain
     * @param aroundConstructForward
     * @param interceptorBindings
     * @return a new {@link javax.interceptor.AroundConstruct} invocation context
     */
    public static InvocationContext aroundConstruct(Constructor<?> constructor, MethodMetadata methodMetadata,
            List<InterceptorInvocation> chain,
            Supplier<Object> aroundConstructForward,
            Set<Annotation> interceptorBindings) {
        return new AroundConstructInvocationContext(constructor, methodMetadata, interceptorBindings, chain,
                aroundConstructForward);
    }

}

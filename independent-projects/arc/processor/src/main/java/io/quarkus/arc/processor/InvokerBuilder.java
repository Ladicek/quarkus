package io.quarkus.arc.processor;

import java.util.function.Consumer;

import jakarta.enterprise.invoke.Transformer;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;

public class InvokerBuilder {
    final ClassInfo beanClass;
    final MethodInfo method;
    private final Consumer<InvokerInfo> afterBuilt;

    boolean instanceLookup;
    boolean[] argumentLookups;

    InvocationTransformer instanceTransfomer;
    InvocationTransformer[] argumentTransformers;
    InvocationTransformer returnValueTransformer;
    InvocationTransformer exceptionTransformer;

    InvocationTransformer invocationWrapper;

    InvokerBuilder(ClassInfo beanClass, MethodInfo method, Consumer<InvokerInfo> afterBuilt) {
        this.beanClass = beanClass;
        this.method = method;
        this.afterBuilt = afterBuilt;

        this.argumentTransformers = new InvocationTransformer[method.parametersCount()];
        this.argumentLookups = new boolean[method.parametersCount()];
    }

    public InvokerBuilder setInstanceLookup() {
        instanceLookup = true;
        return this;
    }

    public InvokerBuilder setArgumentLookup(int position) {
        if (position >= argumentLookups.length) {
            throw new IllegalArgumentException();
        }

        argumentLookups[position] = true;
        return this;
    }

    public InvokerBuilder setInstanceTransformer(Class<?> clazz, String methodName) {
        if (instanceTransfomer != null) {
            throw new IllegalStateException();
        }

        this.instanceTransfomer = new InvocationTransformer(InvocationTransformerKind.INSTANCE, clazz, methodName);
        return this;
    }

    public InvokerBuilder setArgumentTransformer(int position, Class<?> clazz, String methodName) {
        if (position >= argumentLookups.length) {
            throw new IllegalArgumentException();
        }
        if (argumentTransformers[position] != null) {
            throw new IllegalStateException();
        }

        this.argumentTransformers[position] = new InvocationTransformer(InvocationTransformerKind.ARGUMENT, clazz, methodName);
        return this;
    }

    public InvokerBuilder setReturnValueTransformer(Class<?> clazz, String methodName) {
        if (returnValueTransformer != null) {
            throw new IllegalStateException();
        }

        this.returnValueTransformer = new InvocationTransformer(InvocationTransformerKind.RETURN_VALUE, clazz, methodName);
        return this;
    }

    public InvokerBuilder setReturnValueTransformer(Class<? extends Transformer<?, ?>> transformer) {
        if (returnValueTransformer != null) {
            throw new IllegalStateException();
        }

        this.returnValueTransformer = new InvocationTransformer(InvocationTransformerKind.RETURN_VALUE, transformer, null);
        return this;
    }

    public InvokerBuilder setExceptionTransformer(Class<?> clazz, String methodName) {
        if (exceptionTransformer != null) {
            throw new IllegalStateException();
        }

        this.exceptionTransformer = new InvocationTransformer(InvocationTransformerKind.EXCEPTION, clazz, methodName);
        return this;
    }

    public InvokerBuilder setInvocationWrapper(Class<?> clazz, String methodName) {
        if (invocationWrapper != null) {
            throw new IllegalStateException();
        }

        this.invocationWrapper = new InvocationTransformer(InvocationTransformerKind.WRAPPER, clazz, methodName);
        return this;
    }

    public InvokerInfo build() {
        InvokerInfo result = new InvokerInfo(this);
        if (afterBuilt != null) {
            afterBuilt.accept(result);
        }
        return result;
    }
}

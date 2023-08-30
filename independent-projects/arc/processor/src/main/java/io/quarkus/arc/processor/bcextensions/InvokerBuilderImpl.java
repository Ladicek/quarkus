package io.quarkus.arc.processor.bcextensions;

import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.invoke.InvokerBuilder;
import jakarta.enterprise.invoke.Transformer;

class InvokerBuilderImpl implements InvokerBuilder<InvokerInfo> {
    private final io.quarkus.arc.processor.InvokerBuilder arcInvokerBuilder;

    InvokerBuilderImpl(io.quarkus.arc.processor.InvokerBuilder arcInvokerBuilder) {
        this.arcInvokerBuilder = arcInvokerBuilder;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setInstanceLookup() {
        arcInvokerBuilder.setInstanceLookup();
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setArgumentLookup(int position) {
        arcInvokerBuilder.setArgumentLookup(position);
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setInstanceTransformer(Class<?> clazz, String methodName) {
        arcInvokerBuilder.setInstanceTransformer(clazz, methodName);
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setArgumentTransformer(int position, Class<?> clazz, String methodName) {
        arcInvokerBuilder.setArgumentTransformer(position, clazz, methodName);
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setReturnValueTransformer(Class<?> clazz, String methodName) {
        arcInvokerBuilder.setReturnValueTransformer(clazz, methodName);
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setReturnValueTransformer(Class<? extends Transformer<?, ?>> transformer) {
        arcInvokerBuilder.setReturnValueTransformer(transformer);
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setExceptionTransformer(Class<?> clazz, String methodName) {
        arcInvokerBuilder.setExceptionTransformer(clazz, methodName);
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> setInvocationWrapper(Class<?> clazz, String methodName) {
        arcInvokerBuilder.setInvocationWrapper(clazz, methodName);
        return this;
    }

    @Override
    public InvokerInfo build() {
        return new InvokerInfoImpl(arcInvokerBuilder.build());
    }
}

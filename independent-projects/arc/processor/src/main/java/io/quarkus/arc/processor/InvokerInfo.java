package io.quarkus.arc.processor;

import java.util.Arrays;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

public class InvokerInfo {
    final ClassInfo beanClass;
    final MethodInfo method;

    final InvocationTransformer invocationWrapper;

    final InvocationTransformer instanceTransfomer;
    final InvocationTransformer[] argumentTransformers;
    final InvocationTransformer returnValueTransformer;
    final InvocationTransformer exceptionTransformer;

    final boolean instanceLookup;
    final boolean[] argumentLookups;

    final String className;
    final String wrapperClassName;

    InvokerInfo(InvokerBuilder builder) {
        assert builder.argumentTransformers.length == builder.method.parametersCount();
        assert builder.argumentLookups.length == builder.method.parametersCount();

        this.beanClass = builder.beanClass;
        this.method = builder.method;

        this.invocationWrapper = builder.invocationWrapper;

        this.instanceTransfomer = builder.instanceTransfomer;
        this.argumentTransformers = builder.argumentTransformers;
        this.returnValueTransformer = builder.returnValueTransformer;
        this.exceptionTransformer = builder.exceptionTransformer;

        this.instanceLookup = builder.instanceLookup;
        this.argumentLookups = builder.argumentLookups;

        String hash = methodHash(builder);
        this.className = builder.method.declaringClass().name() + "_" + builder.method.name() + "_Invoker_" + hash;
        this.wrapperClassName = invocationWrapper != null
                ? builder.method.declaringClass().name() + "_" + builder.method.name() + "_InvokerWrapper_" + hash
                : null;
    }

    public String getClassName() {
        return wrapperClassName != null ? wrapperClassName : className;
    }

    @Override
    public String toString() {
        return beanClass.name() + "#" + method.name();
    }

    private static String methodHash(InvokerBuilder builder) {
        StringBuilder str = new StringBuilder();
        str.append(builder.beanClass.name());
        str.append(builder.method.declaringClass().name());
        str.append(builder.method.name());
        str.append(builder.method.returnType().name());
        for (Type parameterType : builder.method.parameterTypes()) {
            str.append(parameterType.name());
        }
        str.append(builder.instanceTransfomer);
        str.append(Arrays.toString(builder.argumentTransformers));
        str.append(builder.returnValueTransformer);
        str.append(builder.exceptionTransformer);
        str.append(builder.instanceLookup);
        str.append(Arrays.toString(builder.argumentLookups));
        return Hashes.sha1(str.toString());
    }
}

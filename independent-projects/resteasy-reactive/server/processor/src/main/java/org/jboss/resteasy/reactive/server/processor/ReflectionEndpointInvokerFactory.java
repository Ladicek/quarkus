package org.jboss.resteasy.reactive.server.processor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.resteasy.reactive.common.model.ResourceMethod;

import io.quarkus.arc.Invoker;

public class ReflectionEndpointInvokerFactory implements EndpointInvokerFactory {

    @Override
    public Supplier<Invoker<Object, Object>> create(ResourceMethod method, ClassInfo currentClass,
            MethodInfo currentMethod) {
        return new Supplier<Invoker<Object, Object>>() {
            @Override
            public Invoker<Object, Object> get() {

                try {
                    Class<?> clazz = Class.forName(currentMethod.declaringClass().name().toString(), false,
                            Thread.currentThread().getContextClassLoader());
                    Method meth = clazz.getDeclaredMethod(currentMethod.name(), toParamArray(currentMethod.parameterTypes()));
                    return new Invoker<Object, Object>() {
                        @Override
                        public Object invoke(Object instance, Object[] parameters) {
                            try {
                                return meth.invoke(instance, parameters);
                            } catch (InvocationTargetException e) {
                                if (e.getCause() instanceof Exception) {
                                    throw sneakyThrow(e.getCause());
                                }
                                throw sneakyThrow(e);
                            } catch (IllegalAccessException e) {
                                throw sneakyThrow(e);
                            }
                        }
                    };
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }

            private Class<?>[] toParamArray(List<Type> parameters) {
                Class<?>[] ret = new Class[parameters.size()];
                for (int i = 0; i < ret.length; ++i) {
                    ret[i] = toParam(parameters.get(i));
                }
                return ret;
            }

            private Class<?> toParam(Type type) {
                if (type.kind() == Type.Kind.PRIMITIVE) {
                    PrimitiveType prim = type.asPrimitiveType();
                    switch (prim.primitive()) {
                        case INT:
                            return int.class;
                        case BYTE:
                            return byte.class;
                        case CHAR:
                            return char.class;
                        case LONG:
                            return long.class;
                        case FLOAT:
                            return float.class;
                        case SHORT:
                            return short.class;
                        case DOUBLE:
                            return double.class;
                        case BOOLEAN:
                            return boolean.class;
                        default:
                            throw new RuntimeException("Unknown type " + prim.primitive());
                    }
                } else {
                    try {
                        return Class.forName(type.name().toString(), false, Thread.currentThread().getContextClassLoader());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}

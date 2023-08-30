package io.quarkus.arc;

public interface Invoker<T, R> extends jakarta.enterprise.invoke.Invoker<T, R> {
    @Override
    R invoke(T instance, Object[] arguments);
}

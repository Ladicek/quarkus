package io.quarkus.arc;

public interface Invoker<T, R> {
    // could use varargs, but this will usually be used in generated code,
    // so it doesn't matter (and makes writing generic code easier)
    R invoke(T instance, Object[] arguments);
}

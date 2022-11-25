package io.quarkus.runtime;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a proxyable object that can be returned from a bytecode recorder,
 * and passed between recorders.
 *
 */
public class RuntimeValue<T> implements Supplier<T> {

    private final T value;

    public RuntimeValue(T value) {
        Objects.requireNonNull(value);
        this.value = value;
    }

    public RuntimeValue() {
        this.value = null;
    }

    public T getValue() {
        if (value == null) {
            throw new IllegalStateException("Cannot call getValue() at deployment time");
        }
        return value;
    }

    @Override
    public T get() {
        return getValue();
    }
}

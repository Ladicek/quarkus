package io.quarkus.arc.processor;

enum InvocationTransformerKind {
    WRAPPER,
    INSTANCE,
    ARGUMENT,
    RETURN_VALUE,
    EXCEPTION,
}

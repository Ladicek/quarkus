package io.quarkus.arc.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import io.quarkus.arc.InjectableBean;

public abstract class AbstractInjectableBean<T> implements InjectableBean<T> {
    private final Set<Type> types;
    private final Set<Annotation> qualifiers;

    protected AbstractInjectableBean(Set<Type> types) {
        this.types = types;
        this.qualifiers = Qualifiers.DEFAULT_QUALIFIERS;
    }

    protected AbstractInjectableBean(Set<Type> types, Set<Annotation> qualifiers) {
        this.types = types;
        this.qualifiers = qualifiers;
    }

    @Override
    public Set<Type> getTypes() {
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }
}

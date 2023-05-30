package io.quarkus.arc.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import jakarta.enterprise.context.spi.CreationalContext;

/**
 * Common class for all built-in beans.
 *
 */
public abstract class BuiltInBean<T> extends AbstractInjectableBean<T> {

    protected BuiltInBean(Set<Type> types) {
        super(types);
    }

    protected BuiltInBean(Set<Type> types, Set<Annotation> qualifiers) {
        super(types, qualifiers);
    }

    @Override
    public String getIdentifier() {
        return "builtin_bean_" + this.getClass().getSimpleName();
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        return get(creationalContext);
    }

    @Override
    public Kind getKind() {
        return Kind.BUILTIN;
    }

    @Override
    public String toString() {
        return Beans.toString(this);
    }

}

package io.quarkus.arc.impl;

import static io.quarkus.arc.impl.CreationalContextImpl.unwrap;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Decorated;
import jakarta.enterprise.inject.spi.Bean;

import io.quarkus.arc.InjectableReferenceProvider;

/**
 * {@link Decorated} {@link Bean} metadata provider.
 */
public class DecoratedBeanMetadataProvider implements InjectableReferenceProvider<Contextual<?>> {

    @Override
    public Contextual<?> get(CreationalContext<Contextual<?>> creationalContext) {
        // First attempt to obtain the creational context of the decorator bean and then the creational context of the decorated bean
        CreationalContextImpl<?> parent = unwrap(creationalContext).getParent();
        if (parent != null) {
            parent = parent.getParent();
            return parent != null ? parent.getContextual() : null;
        }
        return null;
    }

}

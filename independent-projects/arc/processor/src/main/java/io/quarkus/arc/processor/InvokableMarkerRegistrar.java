package io.quarkus.arc.processor;

import java.util.Set;

import org.jboss.jandex.DotName;

/**
 * Makes it possible to turn an annotation into an invokable marker without adding an
 * {@link jakarta.enterprise.invoke.Invokable} annotation to it.
 */
public interface InvokableMarkerRegistrar extends BuildExtension {
    /**
     * Returns a set of additional invokable marker annotation types.
     */
    Set<DotName> getAdditionalInvokableMarkers();
}

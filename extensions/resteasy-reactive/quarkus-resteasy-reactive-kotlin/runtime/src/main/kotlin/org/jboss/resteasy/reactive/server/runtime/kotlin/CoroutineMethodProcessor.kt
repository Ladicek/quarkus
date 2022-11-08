package org.jboss.resteasy.reactive.server.runtime.kotlin

import io.quarkus.arc.Invoker
import jakarta.enterprise.inject.spi.CDI
import org.jboss.resteasy.reactive.server.model.HandlerChainCustomizer
import org.jboss.resteasy.reactive.server.spi.ServerRestHandler

/** Intercepts method invocations to force a different InvocationHandler. */
open class CoroutineMethodProcessor @Deprecated("Used only in synthetic code") constructor() :
    HandlerChainCustomizer {

    // not pretty, but this seems to be a limitation of the current method scanning process
    // the HandlerChainCustomizer is called in a build step, but also at runtime to actually create
    // the invocation handler
    // classes like SecurityContextOverrideHandler also use this approach
    private val handlerFactory by lazy {
        CDI.current().select(CoroutineInvocationHandlerFactory::class.java).get()
    }

    override fun alternateInvocationHandler(invoker: Invoker<Any?, Any?>): ServerRestHandler {
        return handlerFactory.createHandler(invoker)
    }
}

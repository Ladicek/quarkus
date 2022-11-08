package org.jboss.resteasy.reactive.server.runtime.kotlin

import io.quarkus.arc.Invoker
import io.quarkus.arc.Unremovable
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.server.spi.ServerRestHandler

/** Factory for the [CoroutineInvocationHandler] that is already part of the CDI container */
@Singleton
@Unremovable
class CoroutineInvocationHandlerFactory(
    private val applicationCoroutineScope: ApplicationCoroutineScope
) {
    fun createHandler(invoker: Invoker<Any?, Any?>): ServerRestHandler {
        return CoroutineInvocationHandler(invoker, applicationCoroutineScope)
    }
}

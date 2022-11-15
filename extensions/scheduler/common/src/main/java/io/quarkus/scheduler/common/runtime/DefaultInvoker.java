package io.quarkus.scheduler.common.runtime;

import java.util.concurrent.CompletionStage;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.ManagedContext;
import io.quarkus.scheduler.ScheduledExecution;

public class DefaultInvoker implements ScheduledInvoker {
    private final Invoker<Object, CompletionStage<Void>> methodInvoker;
    private final boolean isNonBlocking;
    private final boolean hasScheduledExecutionArgument;

    public DefaultInvoker(Invoker<Object, CompletionStage<Void>> methodInvoker, boolean isNonBlocking,
            boolean hasScheduledExecutionArgument) {
        this.methodInvoker = methodInvoker;
        this.isNonBlocking = isNonBlocking;
        this.hasScheduledExecutionArgument = hasScheduledExecutionArgument;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) throws Exception {
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            return invokeBean(execution);
        } else {
            // 1. Activate the context
            // 2. Capture the state (which is basically a shared Map instance)
            // 3. Destroy the context correctly when the returned stage completes
            requestContext.activate();
            final ContextState state = requestContext.getState();
            try {
                return invokeBean(execution).whenComplete((v, t) -> {
                    requestContext.destroy(state);
                });
            } catch (RuntimeException e) {
                // Just terminate the context and rethrow the exception if something goes really wrong
                requestContext.terminate();
                throw e;
            } finally {
                // Always deactivate the context
                requestContext.deactivate();
            }
        }
    }

    protected CompletionStage<Void> invokeBean(ScheduledExecution execution) {
        Object[] arguments = hasScheduledExecutionArgument ? new Object[] { execution } : new Object[] {};
        return methodInvoker.invoke(null, arguments);
    }

    @Override
    public boolean isBlocking() {
        return !isNonBlocking;
    }
}

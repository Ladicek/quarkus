package io.quarkus.scheduler.common.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import io.quarkus.arc.Invoker;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.scheduler.Scheduled;

public final class ImmutableScheduledMethod implements ScheduledMethod {

    private final String invokerClassName;
    private final String declaringClassName;
    private final String methodName;
    private final List<Scheduled> schedules;

    private final RuntimeValue<Invoker<Object, CompletionStage<Void>>> invoker;
    private final boolean nonBlocking;
    private final boolean scheduledExecutionArgument;

    public ImmutableScheduledMethod(String invokerClassName, String declaringClassName, String methodName,
            List<Scheduled> schedules, RuntimeValue<Invoker<Object, CompletionStage<Void>>> invoker,
            boolean nonBlocking, boolean scheduledExecutionArgument) {
        this.invokerClassName = Objects.requireNonNull(invokerClassName);
        this.declaringClassName = Objects.requireNonNull(declaringClassName);
        this.methodName = Objects.requireNonNull(methodName);
        this.schedules = List.copyOf(schedules);
        this.invoker = Objects.requireNonNull(invoker);
        this.nonBlocking = nonBlocking;
        this.scheduledExecutionArgument = scheduledExecutionArgument;
    }

    public String getInvokerClassName() {
        return invokerClassName;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<Scheduled> getSchedules() {
        return schedules;
    }

    @Override
    public RuntimeValue<Invoker<Object, CompletionStage<Void>>> getInvoker() {
        return invoker;
    }

    @Override
    public boolean isNonBlocking() {
        return nonBlocking;
    }

    @Override
    public boolean isScheduledExecutionArgument() {
        return scheduledExecutionArgument;
    }
}

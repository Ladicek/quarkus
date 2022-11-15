package io.quarkus.scheduler.common.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.quarkus.arc.Invoker;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.scheduler.Scheduled;

// This class is mutable so that it can be serialized in a recorder method
public class MutableScheduledMethod implements ScheduledMethod {

    private String invokerClassName;
    private String declaringClassName;
    private String methodName;
    private List<Scheduled> schedules;

    private RuntimeValue<Invoker<Object, CompletionStage<Void>>> invoker;
    private boolean nonBlocking;
    private boolean scheduledExecutionArgument;

    public String getInvokerClassName() {
        return invokerClassName;
    }

    public void setInvokerClassName(String invokerClassName) {
        this.invokerClassName = invokerClassName;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public void setDeclaringClassName(String declaringClassName) {
        this.declaringClassName = declaringClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<Scheduled> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<Scheduled> schedules) {
        this.schedules = schedules;
    }

    public RuntimeValue<Invoker<Object, CompletionStage<Void>>> getInvoker() {
        return invoker;
    }

    public void setInvoker(RuntimeValue<Invoker<Object, CompletionStage<Void>>> invoker) {
        this.invoker = invoker;
    }

    public boolean isNonBlocking() {
        return nonBlocking;
    }

    public void setNonBlocking(boolean nonBlocking) {
        this.nonBlocking = nonBlocking;
    }

    public boolean isScheduledExecutionArgument() {
        return scheduledExecutionArgument;
    }

    public void setScheduledExecutionArgument(boolean scheduledExecutionArgument) {
        this.scheduledExecutionArgument = scheduledExecutionArgument;
    }
}

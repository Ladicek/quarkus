package io.quarkus.scheduler.common.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.quarkus.arc.Invoker;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.scheduler.Scheduled;

/**
 * Scheduled method metadata.
 *
 */
public interface ScheduledMethod {

    String getInvokerClassName();

    String getDeclaringClassName();

    String getMethodName();

    List<Scheduled> getSchedules();

    default String getMethodDescription() {
        return getDeclaringClassName() + "#" + getMethodName();
    }

    RuntimeValue<Invoker<Object, CompletionStage<Void>>> getInvoker();

    boolean isNonBlocking();

    boolean isScheduledExecutionArgument();

}
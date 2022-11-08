package org.jboss.resteasy.reactive.server.processor;

import java.util.function.Supplier;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.resteasy.reactive.common.model.ResourceMethod;

import io.quarkus.arc.Invoker;

public interface EndpointInvokerFactory {

    Supplier<Invoker<Object, Object>> create(ResourceMethod method, ClassInfo currentClass, MethodInfo currentMethod);
}

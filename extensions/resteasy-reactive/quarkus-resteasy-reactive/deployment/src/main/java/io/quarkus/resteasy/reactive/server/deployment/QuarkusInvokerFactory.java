package io.quarkus.resteasy.reactive.server.deployment;

import java.util.Set;
import java.util.function.Supplier;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.resteasy.reactive.common.model.ResourceMethod;
import org.jboss.resteasy.reactive.server.processor.EndpointInvokerFactory;

import io.quarkus.arc.Invoker;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanResolver;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.deployment.recording.RecorderContext;

public class QuarkusInvokerFactory implements EndpointInvokerFactory {

    final RecorderContext recorderContext;
    final BeanResolver beanResolver;

    public QuarkusInvokerFactory(RecorderContext recorderContext, BeanResolver beanResolver) {
        this.recorderContext = recorderContext;
        this.beanResolver = beanResolver;
    }

    @Override
    public Supplier<Invoker<Object, Object>> create(ResourceMethod method, ClassInfo currentClassInfo, MethodInfo info) {
        Set<BeanInfo> candidates = beanResolver.resolveBeans(Type.create(currentClassInfo.name(), Type.Kind.CLASS));
        BeanInfo bean = beanResolver.resolveAmbiguity(candidates);
        InvokerInfo invoker = bean.createInvoker(info).build();
        return recorderContext.staticInstance(invoker.getClassName(), Invoker.class);
    }
}

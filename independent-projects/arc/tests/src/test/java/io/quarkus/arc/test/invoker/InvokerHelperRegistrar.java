package io.quarkus.arc.test.invoker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanRegistrar;
import io.quarkus.arc.processor.InvokerInfo;

public class InvokerHelperRegistrar implements BeanRegistrar {
    private final Class<?> beanClass;
    private final BiConsumer<BeanInfo, BiConsumer<String, InvokerInfo>> action;

    public InvokerHelperRegistrar(Class<?> beanClass, BiConsumer<BeanInfo, BiConsumer<String, InvokerInfo>> action) {
        this.beanClass = beanClass;
        this.action = action;
    }

    @Override
    public void register(RegistrationContext context) {
        Map<String, InvokerInfo> map = new LinkedHashMap<>();
        BeanInfo bean = context.beans().withBeanClass(beanClass).firstResult().orElseThrow();
        action.accept(bean, map::put);
        context.configure(InvokerHelper.class)
                .types(InvokerHelper.class)
                .creator(InvokerHelperCreator.class)
                .param("names", map.keySet().toArray(String[]::new))
                .param("invokers", map.values().toArray(InvokerInfo[]::new))
                .done();
    }
}

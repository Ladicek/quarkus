package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invokable;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class InvokableOnClassTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept(invokableMethod.name(), bean.createInvoker(invokableMethod).build());
                }
            }))
            .build();

    @Test
    public void test() {
        InvokerHelper helper = Arc.container().instance(InvokerHelper.class).get();

        InstanceHandle<MyService> service = Arc.container().instance(MyService.class);

        Invoker<MyService, String> hello = helper.getInvoker("hello");
        assertEquals("foobar0[]", hello.invoke(service.get(), new Object[] { 0, List.of() }));

        Invoker<MyService, Set<String>> doSomething = helper.getInvoker("doSomething");
        assertEquals(Set.of("_", "quux"), doSomething.invoke(service.get(), new Object[] { "_" }));
    }

    @Singleton
    @Invokable
    static class MyService {
        public String hello(int param1, List<String> param2) {
            return "foobar" + param1 + param2;
        }

        public Set<String> doSomething(String param) {
            return Set.of("quux", param);
        }
    }
}

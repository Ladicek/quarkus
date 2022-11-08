package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Invokable;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class InstanceLookupDependentTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept(invokableMethod.name(), bean.createInvoker(invokableMethod).setInstanceLookup().build());
                }
            }))
            .build();

    @Test
    public void test() {
        InvokerHelper helper = Arc.container().instance(InvokerHelper.class).get();

        Invoker<MyService, String> invoker = helper.getInvoker("hello");
        assertEquals("foobar0", invoker.invoke(null, new Object[0]));
        assertEquals("foobar1", invoker.invoke(null, new Object[0]));
        assertEquals("foobar2", invoker.invoke(null, new Object[0]));
        assertEquals(3, MyService.DESTROYED);
    }

    @Dependent
    static class MyService {
        private static int COUNTER = 0;

        static int DESTROYED = 0;

        private int id;

        @PostConstruct
        public void init() {
            this.id = COUNTER++;
        }

        @PreDestroy
        public void destroy() {
            DESTROYED++;
        }

        @Invokable
        public String hello() {
            return "foobar" + id;
        }
    }
}

package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.invoke.Invokable;
import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class ArgumentLookupTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class, MyDependency.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept(invokableMethod.name(), bean.createInvoker(invokableMethod)
                            .setArgumentLookup(0)
                            .build());
                }
            }))
            .build();

    @Test
    public void test() {
        InvokerHelper helper = Arc.container().instance(InvokerHelper.class).get();

        InstanceHandle<MyService> service = Arc.container().instance(MyService.class);

        Invoker<MyService, String> invoker = helper.getInvoker("hello");
        assertEquals("foobar0", invoker.invoke(service.get(), new Object[0]));
        assertEquals("foobar0", invoker.invoke(service.get(), new Object[0]));
        assertEquals("foobar0", invoker.invoke(service.get(), new Object[0]));
        assertEquals(0, MyDependency.DESTROYED);
    }

    @Singleton
    static class MyService {
        @Invokable
        public String hello(MyDependency dependency) {
            return "foobar" + dependency.getId();
        }
    }

    @Singleton
    static class MyDependency {
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

        public int getId() {
            return id;
        }
    }
}

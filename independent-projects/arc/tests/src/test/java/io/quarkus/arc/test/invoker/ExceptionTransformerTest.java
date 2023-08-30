package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.enterprise.invoke.Invokable;
import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class ExceptionTransformerTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept(invokableMethod.name(), bean.createInvoker(invokableMethod)
                            .setExceptionTransformer(ExceptionTransformer.class, "change")
                            .build());
                }
            }))
            .build();

    static class ExceptionTransformer {
        static String change(Throwable exception) {
            if (exception instanceof IllegalArgumentException) {
                return "hello";
            } else if (exception instanceof IllegalStateException) {
                return "doSomething";
            } else {
                throw new AssertionError();
            }
        }
    }

    @Test
    public void test() {
        InvokerHelper helper = Arc.container().instance(InvokerHelper.class).get();

        InstanceHandle<MyService> service = Arc.container().instance(MyService.class);

        Invoker<MyService, String> hello = helper.getInvoker("hello");
        assertEquals("hello", hello.invoke(service.get(), new Object[0]));

        Invoker<MyService, String> doSomething = helper.getInvoker("doSomething");
        assertEquals("doSomething", doSomething.invoke(service.get(), new Object[0]));
    }

    @Singleton
    static class MyService {
        @Invokable
        public String hello() {
            throw new IllegalArgumentException();
        }

        @Invokable
        public String doSomething() {
            throw new IllegalStateException();
        }
    }
}

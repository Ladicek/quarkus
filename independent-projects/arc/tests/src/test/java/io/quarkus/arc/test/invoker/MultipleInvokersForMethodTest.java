package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invokable;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class MultipleInvokersForMethodTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept("change1", bean.createInvoker(invokableMethod)
                            .setReturnValueTransformer(Change1.class, "change")
                            .build());
                    invokers.accept("change2", bean.createInvoker(invokableMethod)
                            .setReturnValueTransformer(Change2.class, "change")
                            .build());
                    invokers.accept("change3", bean.createInvoker(invokableMethod)
                            .setReturnValueTransformer(Change3.class, "change")
                            .build());
                }
            }))
            .build();

    static class Change1 {
        static String change(String result) {
            return result + "1";
        }
    }

    static class Change2 {
        static String change(String result) {
            return result + "2";
        }
    }

    static class Change3 {
        static String change(String result) {
            return result + "3";
        }
    }

    @Test
    public void test() {
        InvokerHelper helper = Arc.container().instance(InvokerHelper.class).get();

        InstanceHandle<MyService> service = Arc.container().instance(MyService.class);

        Invoker<MyService, String> invoker1 = helper.getInvoker("change1");
        assertEquals("foobar1", invoker1.invoke(service.get(), new Object[0]));

        Invoker<MyService, String> invoker2 = helper.getInvoker("change2");
        assertEquals("foobar2", invoker2.invoke(service.get(), new Object[0]));

        Invoker<MyService, String> invoker3 = helper.getInvoker("change3");
        assertEquals("foobar3", invoker3.invoke(service.get(), new Object[0]));
    }

    @Singleton
    static class MyService {
        @Invokable
        public String hello() {
            return "foobar";
        }
    }
}

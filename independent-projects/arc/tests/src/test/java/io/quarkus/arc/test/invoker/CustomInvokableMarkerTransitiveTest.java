package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import jakarta.enterprise.invoke.Invokable;
import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class CustomInvokableMarkerTransitiveTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyIndirectInvokable.class, MyInvokable.class, MyService.class)
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
    }

    @Invokable
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface MyIndirectInvokable {
    }

    @MyIndirectInvokable
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface MyInvokable {
    }

    @Singleton
    static class MyService {
        @MyInvokable
        public String hello(int param1, List<String> param2) {
            return "foobar" + param1 + param2;
        }
    }
}

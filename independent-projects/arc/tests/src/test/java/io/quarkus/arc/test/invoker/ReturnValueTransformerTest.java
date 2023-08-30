package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.invoke.Invokable;
import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.test.ArcTestContainer;

public class ReturnValueTransformerTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept(invokableMethod.name(), bean.createInvoker(invokableMethod)
                            .setReturnValueTransformer(ReturnValueTransformer.class, "change")
                            .build());
                }
            }))
            .build();

    static class ReturnValueTransformer {
        static String change(String result) {
            return result.toUpperCase(Locale.ROOT);
        }

        static Set<String> change(Set<String> result) {
            return result.stream().map(it -> it.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        }
    }

    @Test
    public void test() {
        InvokerHelper helper = Arc.container().instance(InvokerHelper.class).get();

        InstanceHandle<MyService> service = Arc.container().instance(MyService.class);

        Invoker<MyService, String> hello = helper.getInvoker("hello");
        assertEquals("FOOBAR0[]", hello.invoke(service.get(), new Object[] { 0, List.of() }));

        Invoker<MyService, Set<String>> doSomething = helper.getInvoker("doSomething");
        assertEquals(Set.of("_", "QUUX"), doSomething.invoke(service.get(), new Object[] { "_" }));
    }

    @Singleton
    static class MyService {
        @Invokable
        public String hello(int param1, List<String> param2) {
            return "foobar" + param1 + param2;
        }

        @Invokable
        public Set<String> doSomething(String param) {
            return Set.of("quux", param);
        }
    }
}

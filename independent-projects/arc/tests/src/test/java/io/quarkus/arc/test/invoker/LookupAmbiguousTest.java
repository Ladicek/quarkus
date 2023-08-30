package io.quarkus.arc.test.invoker;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.invoke.Invokable;
import jakarta.inject.Singleton;

import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.test.ArcTestContainer;

public class LookupAmbiguousTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyService.class, MyDependency1.class, MyDependency2.class)
            .beanRegistrars(new InvokerHelperRegistrar(MyService.class, (bean, invokers) -> {
                for (MethodInfo invokableMethod : bean.getInvokableMethods()) {
                    invokers.accept(invokableMethod.name(), bean.createInvoker(invokableMethod)
                            .setArgumentLookup(0)
                            .build());
                }
            }))
            .shouldFail()
            .build();

    @Test
    public void trigger() {
        Throwable error = container.getFailure();
        assertNotNull(error);
        assertInstanceOf(DeploymentException.class, error);
        assertTrue(error.getMessage().contains("Ambiguous dependencies"));
    }

    @Singleton
    static class MyService {
        @Invokable
        public String hello(MyDependency dependency) {
            return "foobar" + dependency.getId();
        }
    }

    interface MyDependency {
        int getId();
    }

    @Singleton
    static class MyDependency1 implements MyDependency {
        @Override
        public int getId() {
            return 1;
        }
    }

    @Singleton
    static class MyDependency2 implements MyDependency {
        @Override
        public int getId() {
            return 2;
        }
    }
}

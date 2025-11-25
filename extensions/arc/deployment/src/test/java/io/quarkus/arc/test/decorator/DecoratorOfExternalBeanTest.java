package io.quarkus.arc.test.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.test.supplement.ConsumerOfSomeBeanInExternalLibrary;
import io.quarkus.arc.test.supplement.SomeBeanInExternalLibrary;
import io.quarkus.arc.test.supplement.SomeEventInExternalLibrary;
import io.quarkus.arc.test.supplement.SomeInterfaceInExternalLibrary;
import io.quarkus.arc.test.supplement.SomeProducedDependencyInExternalLibrary;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

public class DecoratorOfExternalBeanTest {
    // the test includes an _application_ decorator (in the Runtime CL) that applies
    // to a bean that is _outside_ of the application (in the Base Runtime CL)

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(MyDecorator.class))
            // we need a non-application archive, so cannot use `withAdditionalDependency()`
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-arc-test-supplement", Version.getVersion())));

    @Inject
    SomeBeanInExternalLibrary bean;

    @Inject
    ConsumerOfSomeBeanInExternalLibrary consumer;

    @Inject
    Event<SomeEventInExternalLibrary> event;

    @Inject
    Instance<SomeProducedDependencyInExternalLibrary> instance;

    @Test
    public void test() {
        assertEquals("Delegated: Hello", bean.hello());

        assertFalse(SomeBeanInExternalLibrary.pinged);
        assertFalse(SomeBeanInExternalLibrary.observed);
        assertFalse(SomeBeanInExternalLibrary.produced);
        assertFalse(SomeBeanInExternalLibrary.disposed);

        assertEquals("pong", consumer.ping());

        assertTrue(SomeBeanInExternalLibrary.pinged);
        assertFalse(SomeBeanInExternalLibrary.observed);
        assertFalse(SomeBeanInExternalLibrary.produced);
        assertFalse(SomeBeanInExternalLibrary.disposed);

        event.fire(new SomeEventInExternalLibrary());

        assertTrue(SomeBeanInExternalLibrary.pinged);
        assertTrue(SomeBeanInExternalLibrary.observed);
        assertFalse(SomeBeanInExternalLibrary.produced);
        assertFalse(SomeBeanInExternalLibrary.disposed);

        SomeProducedDependencyInExternalLibrary dependency = instance.get();

        assertTrue(SomeBeanInExternalLibrary.pinged);
        assertTrue(SomeBeanInExternalLibrary.observed);
        assertTrue(SomeBeanInExternalLibrary.produced);
        assertFalse(SomeBeanInExternalLibrary.disposed);

        assertEquals("Produced: Hello", dependency.hello());

        assertTrue(SomeBeanInExternalLibrary.pinged);
        assertTrue(SomeBeanInExternalLibrary.observed);
        assertTrue(SomeBeanInExternalLibrary.produced);
        assertFalse(SomeBeanInExternalLibrary.disposed);

        instance.destroy(dependency);

        assertTrue(SomeBeanInExternalLibrary.pinged);
        assertTrue(SomeBeanInExternalLibrary.observed);
        assertTrue(SomeBeanInExternalLibrary.produced);
        assertTrue(SomeBeanInExternalLibrary.disposed);
    }

    @Test
    public void testNonAppArchive() {
        assertTrue(SomeBeanInExternalLibrary.class.getClassLoader().getName()
                .contains("Quarkus Base Runtime ClassLoader"));
        assertTrue(ConsumerOfSomeBeanInExternalLibrary.class.getClassLoader().getName()
                .contains("Quarkus Base Runtime ClassLoader"));
        assertTrue(SomeEventInExternalLibrary.class.getClassLoader().getName()
                .contains("Quarkus Base Runtime ClassLoader"));
        assertTrue(SomeProducedDependencyInExternalLibrary.class.getClassLoader().getName()
                .contains("Quarkus Base Runtime ClassLoader"));
    }

    @Decorator
    public static class MyDecorator implements SomeInterfaceInExternalLibrary {
        @Inject
        @Delegate
        SomeInterfaceInExternalLibrary delegate;

        @Override
        public String hello() {
            return "Delegated: " + delegate.hello();
        }
    }
}

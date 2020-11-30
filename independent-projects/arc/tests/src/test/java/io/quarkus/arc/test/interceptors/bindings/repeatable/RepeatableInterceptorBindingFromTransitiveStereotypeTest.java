package io.quarkus.arc.test.interceptors.bindings.repeatable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.arc.Arc;
import io.quarkus.arc.test.ArcTestContainer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.inject.Stereotype;
import javax.inject.Singleton;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.InvocationContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests using a repeating interceptor binding declared on a stereotype,
 * which comes transitively through another stereotype.
 */
public class RepeatableInterceptorBindingFromTransitiveStereotypeTest {
    @RegisterExtension
    public ArcTestContainer container = new ArcTestContainer(MyBinding.class, MyBinding.List.class, MyStereotype.class,
            MyOtherStereotype.class, MyBean.class, IncrementingInterceptor.class);

    @Test
    @Disabled("ArC doesn't have transitive stereotypes yet")
    public void test() {
        MyBean bean = Arc.container().instance(MyBean.class).get();

        assertEquals(10, bean.foo());
        assertEquals(21, bean.foobar());
        assertEquals(30, bean.foobaz());
        assertEquals(41, bean.foobarbaz());
        assertEquals(51, bean.nonannotated());
    }

    @Target({ ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR })
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(MyBinding.List.class)
    @InterceptorBinding
    @interface MyBinding {
        String value();

        @Target({ ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR })
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            MyBinding[] value();
        }
    }

    @MyBinding("foo")
    @MyBinding("bar")
    @Stereotype
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface MyStereotype {
    }

    @MyStereotype
    @Stereotype
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface MyOtherStereotype {
    }

    @Singleton
    @MyOtherStereotype
    static class MyBean {
        @MyBinding("foo")
        public int foo() {
            return 10;
        }

        @MyBinding("foo")
        @MyBinding("bar")
        public int foobar() {
            return 20;
        }

        @MyBinding("foo")
        @MyBinding("baz")
        public int foobaz() {
            return 30;
        }

        @MyBinding("foo")
        @MyBinding("bar")
        @MyBinding("baz")
        public int foobarbaz() {
            return 40;
        }

        public int nonannotated() {
            return 50;
        }
    }

    @Interceptor
    @MyBinding("foo")
    @MyBinding("bar")
    static class IncrementingInterceptor {
        @AroundInvoke
        public Object intercept(InvocationContext ctx) throws Exception {
            return ((Integer) ctx.proceed()) + 1;
        }
    }
}

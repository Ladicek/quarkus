package io.quarkus.arc.test.interceptors.bindings.repeatable;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.arc.Arc;
import io.quarkus.arc.test.ArcTestContainer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
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
 * Tests usage of inherited stereotype containing a repeating interceptor binding.
 */
public class InheritedStereotypeWithRepeatableInterceptorBindingTest {
    @RegisterExtension
    public ArcTestContainer container = new ArcTestContainer(MyBinding.class, MyBinding.List.class, MyStereotype.class,
            MyBeanDefiningAnnotation.class, MySuperclass.class, MyBean.class, IncrementingInterceptor.class);

    @Test
    @Disabled("ArC doesn't have stereotype inheritance yet")
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
    @Singleton
    @Stereotype
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @interface MyStereotype {
    }

    @Stereotype
    @Target({ TYPE, METHOD, FIELD })
    @Retention(RUNTIME)
    @interface MyBeanDefiningAnnotation {
    }

    @MyStereotype
    static class MySuperclass {
    }

    @MyBeanDefiningAnnotation
    static class MyBean extends MySuperclass {
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

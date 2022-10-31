package io.quarkus.arc.test.interceptors.primitive;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.test.ArcTestContainer;

public class PrimitiveInterceptorTest {
    @RegisterExtension
    public ArcTestContainer container = new ArcTestContainer(MyBean.class, Primitive.class, PrimitiveInterceptor.class);

    @Test
    public void testInterception() {
        ArcContainer arc = Arc.container();
        MyBean bean = arc.instance(MyBean.class).get();
        assertEquals("intercepted: foobar", bean.doSomething(42));
    }

    @Singleton
    static class MyBean {
        @Primitive
        String doSomething(int param) {
            return "foobar";
        }
    }

    @Target({ TYPE, METHOD })
    @Retention(RUNTIME)
    @Documented
    @InterceptorBinding
    public @interface Primitive {
    }

    @Primitive
    @Interceptor
    @Priority(1)
    public static class PrimitiveInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext ctx) throws Exception {
            return "intercepted: " + ctx.proceed();
        }
    }
}

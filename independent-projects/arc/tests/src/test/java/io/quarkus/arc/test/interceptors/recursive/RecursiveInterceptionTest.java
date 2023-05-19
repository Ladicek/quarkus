package io.quarkus.arc.test.interceptors.recursive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.test.ArcTestContainer;

public class RecursiveInterceptionTest {
    @RegisterExtension
    public ArcTestContainer container = new ArcTestContainer.Builder()
            .beanClasses(MyBean.class, MyInterceptorBinding.class, MyInterceptor.class)
            .strictCompatibility(true) // prevent recursive interception
            .build();

    @Test
    public void test() {
        MyBean bean = Arc.container().instance(MyBean.class).get();
        assertEquals("intercepted: foobar", bean.doSomething());
    }

    @MyInterceptorBinding
    @Singleton
    static class MyBean {
        String doSomething() {
            return "foobar";
        }
    }

    @Target({ ElementType.TYPE_USE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @InterceptorBinding
    @Inherited
    @interface MyInterceptorBinding {
    }

    @MyInterceptorBinding
    @Interceptor
    @Priority(1)
    static class MyInterceptor {
        @AroundInvoke
        Object intercept(InvocationContext ctx) {
            MyBean bean = (MyBean) ctx.getTarget();
            return "intercepted: " + bean.doSomething();
        }
    }
}

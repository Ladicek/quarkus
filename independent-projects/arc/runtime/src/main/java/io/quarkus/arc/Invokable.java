package io.quarkus.arc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Invokable {
    // implemented:
    // - usage of @Invokable
    // - usage of annotation directly or transitively meta-annotated @Invokable
    //
    // not implemented:
    // - inheriting an invokable marker (or @Invokable, if that becomes @Inherited)
}

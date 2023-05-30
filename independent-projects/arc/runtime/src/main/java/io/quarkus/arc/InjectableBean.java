package io.quarkus.arc;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

/**
 * Quarkus representation of an injectable bean.
 * This interface extends the standard CDI {@link Bean} interface.
 *
 * @author Martin Kouba
 *
 * @param <T>
 */
public interface InjectableBean<T> extends Bean<T>, InjectableReferenceProvider<T> {

    /**
     * The identifier is generated by the container and is unique for a specific deployment.
     *
     * @return the identifier for this bean
     */
    String getIdentifier();

    /**
     *
     * @return the kind of the bean
     * @see Kind
     */
    default Kind getKind() {
        return Kind.CLASS;
    }

    /**
     *
     * @return the scope
     */
    @Override
    default Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    @Override
    default void destroy(T instance, CreationalContext<T> creationalContext) {
        creationalContext.release();
    }

    /**
     *
     * @return the declaring bean if the bean is a producer method/field, or {@code null}
     */
    default InjectableBean<?> getDeclaringBean() {
        return null;
    }

    @Override
    default String getName() {
        return null;
    }

    @Override
    default Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    /**
     * By default, this method always returns an empty set, because obtaining the set
     * of injection points of a bean at application runtime is rarely useful.
     * <p>
     * In the {@linkplain ArcContainer#strictCompatibility() strict mode}, this method
     * works as described by the CDI specification. Feedback on usefulness of this
     * method is welcome!
     */
    @Override
    default Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    // Deprecated method which can be safely removed once we use CDI 4.0+
    @Deprecated
    default boolean isNullable() {
        return false;
    }

    @Override
    default boolean isAlternative() {
        return false;
    }

    /**
     *
     * @return the priority if the bean is an alternative, or {@code null}
     */
    default Integer getAlternativePriority() {
        return isAlternative() ? getPriority() : null;
    }

    /**
     * @return whether or not the bean is a default bean
     */
    default boolean isDefaultBean() {
        return false;
    }

    /**
     * Suppressed beans cannot be obtained by programmatic lookup via {@link Instance}.
     *
     * @return {@code true} if the bean should be suppressed
     */
    default boolean isSuppressed() {
        return false;
    }

    /**
     * A bean may have a priority assigned.
     * <p>
     * Class-based beans can specify the priority declaratively via {@link jakarta.annotation.Priority} and
     * {@link io.quarkus.arc.Priority}. If no priority annotation is used then a bean has the priority of value 0.
     * <p>
     * This priority is used to sort the resolved beans when performing programmatic lookup via
     * {@link Instance} or when injecting a list of beans by means of the {@link All} qualifier.
     *
     * @return the priority
     * @see Priority
     */
    default int getPriority() {
        return 0;
    }

    /**
     * The return value depends on the {@link #getKind()}.
     *
     * <ul>
     * <li>For managed beans, interceptors, decorators and built-in beans, the bean class is returned.</li>
     * <li>For a producer method, the class of the return type is returned.</li>
     * <li>For a producer field, the class of the field is returned.</li>
     * <li>For a synthetic bean, the implementation class defined by the registrar is returned.
     * </ul>
     *
     * @return the implementation class, or null in case of a producer of a primitive type or an array
     * @see Kind
     */
    default Class<?> getImplementationClass() {
        return getBeanClass();
    }

    enum Kind {

        CLASS,
        PRODUCER_FIELD,
        PRODUCER_METHOD,
        SYNTHETIC,
        INTERCEPTOR,
        DECORATOR,
        BUILTIN,
        ;

        public static Kind from(String value) {
            for (Kind kind : values()) {
                if (kind.toString().equals(value)) {
                    return kind;
                }
            }
            return null;
        }

    }

}

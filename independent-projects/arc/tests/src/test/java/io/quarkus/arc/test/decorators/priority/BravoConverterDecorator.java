package io.quarkus.arc.test.decorators.priority;

import io.quarkus.arc.test.decorators.priority.MultipleDecoratorsTest.Converter;
import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;

@Priority(2)
@Decorator
class BravoConverterDecorator implements Converter<String> {

    @Inject
    @Delegate
    Converter<String> delegate;

    @Override
    public String convert(String value) {
        return new StringBuilder(delegate.convert(value)).reverse().toString();
    }

}

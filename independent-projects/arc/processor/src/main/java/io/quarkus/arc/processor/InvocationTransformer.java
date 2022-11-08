package io.quarkus.arc.processor;

final class InvocationTransformer {
    final InvocationTransformerKind kind;
    final Class<?> clazz;
    final String method;

    InvocationTransformer(InvocationTransformerKind kind, Class<?> clazz, String method) {
        this.kind = kind;
        this.clazz = clazz;
        this.method = method;
    }

    @Override
    public String toString() {
        String kind = "";
        switch (this.kind) {
            case WRAPPER:
                kind = "invocation wrapper ";
                break;
            case INSTANCE:
                kind = "target instance transformer ";
                break;
            case ARGUMENT:
                kind = "argument transformer ";
                break;
            case RETURN_VALUE:
                kind = "return value transformer ";
                break;
            case EXCEPTION:
                kind = "exception transformer ";
        }
        return kind + clazz.getName() + "#" + method;
    }

    public boolean isInputTransformer() {
        return kind == InvocationTransformerKind.INSTANCE || kind == InvocationTransformerKind.ARGUMENT;
    }

    public boolean isOutputTransformer() {
        return kind == InvocationTransformerKind.RETURN_VALUE || kind == InvocationTransformerKind.EXCEPTION;
    }
}

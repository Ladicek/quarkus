package io.quarkus.arc.impl;

import java.util.ArrayDeque;

// this is used to prevent recursive interception
// due to high cost and little usefulness, it is only used in the strict mode
public class CurrentInterception {
    private static final ThreadLocal<ArrayDeque<InterceptedMethodMetadata>> STACK = new ThreadLocal<>() {
        @Override
        protected ArrayDeque<InterceptedMethodMetadata> initialValue() {
            return new ArrayDeque<>();
        }
    };

    public static boolean mayIntercept(InterceptedMethodMetadata interception) {
        ArrayDeque<InterceptedMethodMetadata> stack = STACK.get();
        for (InterceptedMethodMetadata item : stack) {
            if (item.method.equals(interception.method)) {
                return false;
            }
        }
        return true;
    }

    public static Object performAroundInvoke(Object target, Object[] args, InterceptedMethodMetadata metadata)
            throws Exception {
        try {
            STACK.get().push(metadata);
            return AroundInvokeInvocationContext.perform(target, args, metadata);
        } finally {
            STACK.get().pop();
        }
    }
}

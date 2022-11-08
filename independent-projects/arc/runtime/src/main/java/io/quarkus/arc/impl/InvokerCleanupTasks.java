package io.quarkus.arc.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.quarkus.arc.InstanceHandle;

// TODO thread safety?
public class InvokerCleanupTasks implements Consumer<Runnable> {
    private final List<Runnable> finishTasks = new ArrayList<>();
    private final List<InstanceHandle<?>> finishInstanceHandles = new ArrayList<>();

    @Override
    public void accept(Runnable task) {
        if (task != null) {
            finishTasks.add(task);
        }
    }

    public void addBean(InstanceHandle<?> instanceHandle) {
        finishInstanceHandles.add(instanceHandle);
    }

    // must not throw
    public void finish() {
        for (Runnable task : finishTasks) {
            try {
                task.run();
            } catch (Exception e) {
                // TODO log?
            }
        }
        finishTasks.clear();

        for (InstanceHandle<?> instanceHandle : finishInstanceHandles) {
            try {
                instanceHandle.destroy();
            } catch (Exception e) {
                // TODO log?
            }
        }
        finishInstanceHandles.clear();
    }
}

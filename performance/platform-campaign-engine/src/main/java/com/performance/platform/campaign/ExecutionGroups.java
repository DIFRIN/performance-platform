package com.performance.platform.campaign;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ExecutionGroups {

    private final Map<String, Group> groups = new HashMap<>();

    boolean acquire(String name, AtomicBoolean cancelled) throws InterruptedException {
        Group group;
        synchronized (groups) {
            group = groups.computeIfAbsent(name, ignored -> new Group());
            group.users++;
        }
        boolean acquired = false;
        try {
            while (!cancelled.get()) {
                if (group.permit.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                    acquired = true;
                    return true;
                }
            }
            return false;
        } finally {
            if (!acquired) {
                dereference(name, group);
            }
        }
    }

    void release(String name) {
        synchronized (groups) {
            Group group = groups.get(name);
            group.permit.release();
            dereference(name, group);
        }
    }

    private void dereference(String name, Group group) {
        synchronized (groups) {
            if (--group.users == 0) {
                groups.remove(name, group);
            }
        }
    }

    private static final class Group {
        private final Semaphore permit = new Semaphore(1, true);
        private int users;
    }
}

package com.github.kdgaming0.skyrecipes.core.util;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One running computation and at most one pending input. Submit and completion callbacks
 * run on the owner thread; the worker sees only the captured input. Superseded results
 * are discarded before publication, including results already waiting on the owner queue.
 */
public final class LatestTaskRunner<I, O> {
    private final Executor worker;
    private final Executor owner;
    private final Function<I, O> compute;
    private final Consumer<O> publish;
    private final Consumer<Exception> onFailure;
    private long generation;
    private boolean running;
    private boolean pending;
    private I next;

    public LatestTaskRunner(Executor worker, Executor owner, Function<I, O> compute,
                            Consumer<O> publish, Consumer<Exception> onFailure) {
        this.worker = worker;
        this.owner = owner;
        this.compute = compute;
        this.publish = publish;
        this.onFailure = onFailure;
    }

    public void submit(I input) {
        generation++;
        next = input;
        pending = true;
        if (!running) start();
    }

    private void start() {
        I input = next;
        next = null;
        pending = false;
        running = true;
        long ticket = generation;
        try {
            worker.execute(() -> {
                O result;
                try {
                    result = compute.apply(input);
                } catch (Exception failure) {
                    owner.execute(() -> finish(ticket, null, failure));
                    return;
                }
                owner.execute(() -> finish(ticket, result, null));
            });
        } catch (RuntimeException failure) {
            finish(ticket, null, failure);
        }
    }

    private void finish(long ticket, O result, Exception failure) {
        // Keep running true through publication, so reentrant submissions only replace next.
        try {
            if (failure != null) onFailure.accept(failure);
            else if (ticket == generation) publish.accept(result);
        } finally {
            running = false;
            if (pending) start();
        }
    }
}

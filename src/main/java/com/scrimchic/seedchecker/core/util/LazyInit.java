package com.scrimchic.seedchecker.core.util;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Something expensive, built at most once, by whichever thread first needs it, without anyone
 * else ever waiting for it.
 *
 * <p>Made for the vanilla structure data that exact jigsaw validation needs: nearly a second of
 * data pack loading that must not happen on world join, must not happen twice when three workers
 * ask at once, must not block the render thread, and must not be retried every frame once it has
 * failed. Every one of those is a property of this class, so each is tested here rather than
 * re-argued at the call site.
 *
 * <p>The thread that wins the race does the loading itself, inline. Everybody else sees
 * {@link State#INITIALIZING} and carries on with something else; there is deliberately no way to
 * wait.
 */
public final class LazyInit<T> {

    public enum State {
        UNINITIALIZED,
        INITIALIZING,
        READY,

        /** Loading threw, or the value was closed. Terminal: nothing is loaded again. */
        FAILED
    }

    /** Builds the value. Runs on the thread that won the race. */
    public interface Loader<T> {
        T load() throws Exception;
    }

    /** Releases a value that will no longer be used. */
    public interface Closer<T> {
        void close(T value) throws Exception;
    }

    private final AtomicReference<State> state = new AtomicReference<State>(State.UNINITIALIZED);
    private final Closer<T> closer;

    private volatile T value;
    private volatile Throwable failure;

    public LazyInit(Closer<T> closer) {
        if (closer == null) {
            throw new IllegalArgumentException("closer is required");
        }
        this.closer = closer;
    }

    public State state() {
        return state.get();
    }

    /** @return the value when {@link State#READY}, otherwise {@code null}. Never blocks. */
    public T getIfReady() {
        T current = value;
        return state.get() == State.READY ? current : null;
    }

    /** @return why loading failed, or {@code null}. */
    public Throwable failure() {
        return failure;
    }

    /**
     * Loads the value on this thread if nobody has started to, and otherwise returns at once.
     *
     * @return the state after the call: {@code READY} or {@code FAILED} for the thread that loaded,
     *         whatever another thread's load has reached for everybody else
     */
    public State initializeIfNeeded(Loader<T> loader) {
        if (!state.compareAndSet(State.UNINITIALIZED, State.INITIALIZING)) {
            return state.get();
        }
        T loaded;
        try {
            loaded = loader.load();
        } catch (Throwable thrown) {
            failure = thrown;
            state.set(State.FAILED);
            return State.FAILED;
        }
        value = loaded;
        if (!state.compareAndSet(State.INITIALIZING, State.READY)) {
            // Closed while loading. Nobody will ever read this value, so it is released here, by
            // the only thread that holds it.
            value = null;
            closeQuietly(loaded);
            return state.get();
        }
        return State.READY;
    }

    /**
     * Releases the value and refuses to load again. Safe from any thread, at any state.
     *
     * <p>A load still in progress is closed by its own thread when it finishes.
     */
    public void close() {
        State previous = state.getAndSet(State.FAILED);
        if (failure == null) {
            failure = new IllegalStateException("closed");
        }
        if (previous == State.READY) {
            T current = value;
            value = null;
            if (current != null) {
                closeQuietly(current);
            }
        }
    }

    private void closeQuietly(T released) {
        try {
            closer.close(released);
        } catch (Throwable ignored) {
            // Shutting down; there is nobody left to tell.
        }
    }
}

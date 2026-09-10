package dev.soundceiling.app;

import java.util.function.Supplier;

/** Serializes only final state/actuator effects, never capture read/open/close. */
final class EngineSessionGate {
    private long generation;
    private boolean active;

    synchronized long start() {
        generation = generation == Long.MAX_VALUE ? 1L : generation + 1L;
        active = true;
        return generation;
    }

    synchronized long current() { return active ? generation : 0L; }

    synchronized boolean isCurrent(long token) {
        return active && token != 0L && token == generation;
    }

    synchronized boolean runIfCurrent(long token, Runnable effect) {
        if (!isCurrent(token)) return false;
        effect.run();
        return true;
    }

    synchronized <T> T callIfCurrent(long token, Supplier<T> effect, T rejected) {
        return isCurrent(token) ? effect.get() : rejected;
    }

    synchronized boolean stopIfCurrent(long token, Runnable stoppedEffect) {
        if (!isCurrent(token)) return false;
        active = false;
        stoppedEffect.run();
        return true;
    }
}

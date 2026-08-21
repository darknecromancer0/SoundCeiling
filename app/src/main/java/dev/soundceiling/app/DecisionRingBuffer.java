package dev.soundceiling.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class DecisionRingBuffer {
    private final int capacity;
    private final ArrayDeque<String> lines;

    DecisionRingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.lines = new ArrayDeque<>(this.capacity);
    }

    synchronized void add(String line) {
        if (line == null || line.isEmpty()) return;
        while (lines.size() >= capacity) lines.removeFirst();
        lines.addLast(line);
    }

    synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }

    synchronized void clear() {
        lines.clear();
    }
}

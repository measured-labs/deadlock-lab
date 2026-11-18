package com.experiment.deadlocklab.global.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 데드락 실험 메트릭 수집기
 */
public class MetricsCollector {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> timers = new ConcurrentHashMap<>();
    private final Map<String, Long> timerStarts = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void increment(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(delta);
    }

    public long getCount(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    public void startTimer(String name) {
        timerStarts.put(name, System.nanoTime());
    }

    public long stopTimer(String name) {
        Long startTime = timerStarts.remove(name);
        if (startTime == null) return 0;
        long elapsed = System.nanoTime() - startTime;
        timers.put(name, elapsed);
        return elapsed;
    }

    public long getTimerMillis(String name) {
        return timers.getOrDefault(name, 0L) / 1_000_000;
    }

    public void reset() {
        counters.clear();
        timers.clear();
        timerStarts.clear();
    }

    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══════════════════════════════════════\n");
        sb.append("        DEADLOCK LAB METRICS\n");
        sb.append("═══════════════════════════════════════\n");

        if (!counters.isEmpty()) {
            sb.append("\n[Counters]\n");
            counters.forEach((name, value) ->
                    sb.append(String.format("  %-35s : %d%n", name, value.get())));
        }

        if (!timers.isEmpty()) {
            sb.append("\n[Timers]\n");
            timers.forEach((name, nanos) ->
                    sb.append(String.format("  %-35s : %d ms%n", name, nanos / 1_000_000)));
        }

        sb.append("\n═══════════════════════════════════════\n");
        return sb.toString();
    }

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }
}

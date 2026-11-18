package com.experiment.deadlocklab.detection.domain;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 데드락 탐지 결과
 */
public class DetectionResult {

    private final boolean deadlockDetected;
    private final List<Thread> involvedThreads;
    private final LocalDateTime detectedAt;
    private final String description;

    private DetectionResult(boolean deadlockDetected, List<Thread> involvedThreads, String description) {
        this.deadlockDetected = deadlockDetected;
        this.involvedThreads = involvedThreads;
        this.detectedAt = LocalDateTime.now();
        this.description = description;
    }

    public static DetectionResult deadlockDetected(List<Thread> cycle) {
        String desc = "Deadlock detected! Cycle: " +
                cycle.stream().map(Thread::getName).toList();
        return new DetectionResult(true, cycle, desc);
    }

    public static DetectionResult noDeadlock() {
        return new DetectionResult(false, Collections.emptyList(), "No deadlock detected");
    }

    public static DetectionResult timeout() {
        return new DetectionResult(false, Collections.emptyList(), "Detection timeout");
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public List<Thread> getInvolvedThreads() {
        return involvedThreads;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("DetectionResult{deadlock=%s, threads=%d, desc='%s'}",
                deadlockDetected, involvedThreads.size(), description);
    }
}

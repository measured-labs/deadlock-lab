package com.experiment.deadlocklab.scenario.domain;

import com.experiment.deadlocklab.detection.domain.DetectionResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 시나리오 실행 결과
 */
public class ScenarioResult {

    private final String scenarioName;
    private final boolean deadlockOccurred;
    private final SolutionType solutionApplied;
    private final Duration executionTime;
    private final LocalDateTime timestamp;
    private final DetectionResult detectionResult;
    private final String description;

    private ScenarioResult(String scenarioName, boolean deadlockOccurred,
                           SolutionType solutionApplied, Duration executionTime,
                           DetectionResult detectionResult, String description) {
        this.scenarioName = scenarioName;
        this.deadlockOccurred = deadlockOccurred;
        this.solutionApplied = solutionApplied;
        this.executionTime = executionTime;
        this.timestamp = LocalDateTime.now();
        this.detectionResult = detectionResult;
        this.description = description;
    }

    public static ScenarioResult deadlock(String scenarioName, Duration executionTime,
                                           DetectionResult detectionResult) {
        return new ScenarioResult(scenarioName, true, SolutionType.NONE,
                executionTime, detectionResult, "Deadlock occurred");
    }

    public static ScenarioResult success(String scenarioName, SolutionType solution,
                                          Duration executionTime) {
        return new ScenarioResult(scenarioName, false, solution,
                executionTime, DetectionResult.noDeadlock(), "Completed successfully");
    }

    public static ScenarioResult timeout(String scenarioName, Duration executionTime) {
        return new ScenarioResult(scenarioName, true, SolutionType.NONE,
                executionTime, DetectionResult.timeout(), "Execution timeout (likely deadlock)");
    }

    // Getters
    public String getScenarioName() {
        return scenarioName;
    }

    public boolean isDeadlockOccurred() {
        return deadlockOccurred;
    }

    public SolutionType getSolutionApplied() {
        return solutionApplied;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public DetectionResult getDetectionResult() {
        return detectionResult;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("ScenarioResult{scenario=%s, deadlock=%s, solution=%s, time=%dms}",
                scenarioName, deadlockOccurred, solutionApplied, executionTime.toMillis());
    }
}

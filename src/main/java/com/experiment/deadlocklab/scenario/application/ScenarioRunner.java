package com.experiment.deadlocklab.scenario.application;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.scenario.scenarios.*;
import com.experiment.deadlocklab.solution.application.SolutionApplier;

import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오 실행기
 */
public class ScenarioRunner {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final SolutionApplier solutionApplier;
    private final ThreadVisualizer visualizer;
    private final MetricsCollector metrics;
    private final ConsoleLogger logger;

    public ScenarioRunner(ResourceManager resourceManager,
                          DeadlockDetector detector,
                          SolutionApplier solutionApplier,
                          ThreadVisualizer visualizer,
                          MetricsCollector metrics) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.solutionApplier = solutionApplier;
        this.visualizer = visualizer;
        this.metrics = metrics;
        this.logger = ConsoleLogger.of("Runner");
    }

    /**
     * 단일 시나리오 실행
     */
    public List<ScenarioResult> run(DeadlockScenario scenario, boolean withSolution)
            throws InterruptedException {

        List<ScenarioResult> results = new ArrayList<>();

        if (withSolution) {
            results.add(scenario.runWithSolution());
        } else {
            results.add(scenario.runWithoutSolution());
        }

        return results;
    }

    /**
     * 시나리오 비교 실행 (미적용 vs 적용)
     */
    public List<ScenarioResult> runComparison(DeadlockScenario scenario)
            throws InterruptedException {

        List<ScenarioResult> results = new ArrayList<>();

        // 1. 해결책 미적용
        logger.info("Running WITHOUT solution...");
        results.add(scenario.runWithoutSolution());

        Thread.sleep(1000);

        // 2. 해결책 적용
        logger.info("Running WITH solution...");
        results.add(scenario.runWithSolution());

        return results;
    }

    /**
     * 모든 시나리오 실행
     */
    public List<ScenarioResult> runAll() throws InterruptedException {
        List<ScenarioResult> results = new ArrayList<>();

        // 시나리오 목록
        List<DeadlockScenario> scenarios = List.of(
                new ClassicDeadlockScenario(resourceManager, detector, visualizer),
                new DatabaseDeadlockScenario(resourceManager, detector, visualizer),
                new PhilosopherScenario(resourceManager, detector, visualizer),
                new NestedLockScenario(resourceManager, detector, visualizer)
        );

        for (DeadlockScenario scenario : scenarios) {
            logger.info("===== " + scenario.getName() + " =====");

            // 미적용
            results.add(scenario.runWithoutSolution());
            Thread.sleep(500);

            // 적용
            results.add(scenario.runWithSolution());
            Thread.sleep(1000);

            // 초기화
            resourceManager.clear();
            detector.clear();
        }

        return results;
    }

    /**
     * 결과 요약 출력
     */
    public void printSummary(List<ScenarioResult> results) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SCENARIO RESULTS SUMMARY                   ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");

        for (ScenarioResult result : results) {
            String status = result.isDeadlockOccurred() ? "❌ DEADLOCK" : "✅ SUCCESS";
            String solution = result.getSolutionApplied().getName();
            String time = result.getExecutionTime().toMillis() + "ms";

            System.out.printf("║  %-25s | %-12s | %-15s | %6s ║%n",
                    truncate(result.getScenarioName(), 25),
                    status,
                    truncate(solution, 15),
                    time);
        }

        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String truncate(String s, int length) {
        if (s.length() <= length) return s;
        return s.substring(0, length - 2) + "..";
    }
}

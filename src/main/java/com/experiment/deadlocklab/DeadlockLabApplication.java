package com.experiment.deadlocklab;

import com.experiment.deadlocklab.benchmark.DeadlockBenchmark;
import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.application.ScenarioRunner;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.scenario.scenarios.*;
import com.experiment.deadlocklab.solution.application.SolutionApplier;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Deadlock Lab - 메인 애플리케이션
 * 
 * 데드락을 직접 재현하고 해결하며 배우는 프로젝트
 */
@SpringBootApplication
public class DeadlockLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeadlockLabApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner() {
        return args -> {
            printBanner();

            // 컴포넌트 초기화
            ResourceManager resourceManager = new ResourceManager();
            DeadlockDetector detector = new DeadlockDetector();
            SolutionApplier solutionApplier = new SolutionApplier();
            ThreadVisualizer visualizer = new ThreadVisualizer();
            MetricsCollector metrics = new MetricsCollector();

            ScenarioRunner runner = new ScenarioRunner(
                    resourceManager, detector, solutionApplier, visualizer, metrics);

            // 시나리오 선택
            String scenario = args.length > 0 ? args[0].toLowerCase() : "all";
            boolean withSolution = args.length > 1 && args[1].equals("--with-solution");

            switch (scenario) {
                case "classic", "c" -> {
                    ClassicDeadlockScenario classic = new ClassicDeadlockScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, classic, withSolution);
                }
                case "database", "db" -> {
                    DatabaseDeadlockScenario database = new DatabaseDeadlockScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, database, withSolution);
                }
                case "philosopher", "p" -> {
                    PhilosopherScenario philosopher = new PhilosopherScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, philosopher, withSolution);
                }
                case "nested", "n" -> {
                    NestedLockScenario nested = new NestedLockScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, nested, withSolution);
                }
                case "benchmark", "b" -> {
                    DeadlockBenchmark benchmark = new DeadlockBenchmark(resourceManager, metrics);
                    benchmark.runDeadlockRateBenchmark(20);
                    benchmark.runSolutionComparisonBenchmark(1000);
                }
                case "all" -> {
                    List<ScenarioResult> results = runner.runAll();
                    runner.printSummary(results);
                }
                default -> {
                    printUsage();
                }
            }

            // 메트릭 리포트
            System.out.println(metrics.generateReport());

            printFinalMessage();
        };
    }

    private void runScenario(ScenarioRunner runner, DeadlockScenario scenario, boolean withSolution)
            throws InterruptedException {

        if (withSolution) {
            scenario.runWithSolution();
        } else {
            // 비교 실행 (미적용 -> 적용)
            List<ScenarioResult> results = runner.runComparison(scenario);
            runner.printSummary(results);
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("  ____                 _ _            _      _          _     ");
        System.out.println(" |  _ \\  ___  __ _  __| | | ___   ___| | __ | |    __ _| |__  ");
        System.out.println(" | | | |/ _ \\/ _` |/ _` | |/ _ \\ / __| |/ / | |   / _` | '_ \\ ");
        System.out.println(" | |_| |  __/ (_| | (_| | | (_) | (__|   <  | |__| (_| | |_) |");
        System.out.println(" |____/ \\___|\\__,_|\\__,_|_|\\___/ \\___|_|\\_\\ |_____\\__,_|_.__/ ");
        System.out.println();
        System.out.println("  Deadlock Detection and Resolution Lab");
        System.out.println("  Experience Deadlocks, Don't Just Read About Them");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void printUsage() {
        System.out.println("Usage: ./gradlew bootRun --args='<scenario> [--with-solution]'");
        System.out.println();
        System.out.println("Available scenarios:");
        System.out.println("  classic    (c) - Classic AB-BA deadlock");
        System.out.println("  database  (db) - Database row lock deadlock");
        System.out.println("  philosopher(p) - Dining philosophers problem");
        System.out.println("  nested     (n) - Nested synchronized deadlock");
        System.out.println("  benchmark  (b) - Run performance benchmarks");
        System.out.println("  all            - Run all scenarios (default)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --with-solution  Run only with solution applied");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./gradlew bootRun --args='classic'");
        System.out.println("  ./gradlew bootRun --args='philosopher --with-solution'");
        System.out.println();
    }

    private void printFinalMessage() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    DEADLOCK LAB COMPLETED                     ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                               ║");
        System.out.println("║  Key Lessons:                                                 ║");
        System.out.println("║                                                               ║");
        System.out.println("║  1. Lock Ordering     - Always acquire locks in same order    ║");
        System.out.println("║  2. TryLock + Timeout - Never wait forever                    ║");
        System.out.println("║  3. Lock Scope        - Hold locks for shortest time          ║");
        System.out.println("║  4. Detection         - Wait-For Graph cycle = deadlock       ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Prevention > Detection > Recovery                            ║");
        System.out.println("║                                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}

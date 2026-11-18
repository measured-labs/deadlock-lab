package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;
import com.experiment.deadlocklab.solution.strategies.TryLockSolution;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * 데이터베이스 행 락 데드락 시나리오
 * 
 * Transaction 1: UPDATE row1, UPDATE row2
 * Transaction 2: UPDATE row2, UPDATE row1
 * -> DEADLOCK!
 */
public class DatabaseDeadlockScenario implements DeadlockScenario {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public DatabaseDeadlockScenario(ResourceManager resourceManager,
                                     DeadlockDetector detector,
                                     ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("Database");
    }

    @Override
    public String getName() {
        return "Database Row Lock Deadlock";
    }

    @Override
    public String getDescription() {
        return "Two transactions update rows in opposite order";
    }

    @Override
    public int getRequiredResourceCount() {
        return 2;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.TRY_LOCK_TIMEOUT;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");

        // 행 락 시뮬레이션
        LockableResource row1001 = resourceManager.createResource("1001", "Row-1001");
        LockableResource row1002 = resourceManager.createResource("1002", "Row-1002");

        logger.info("Database rows: accounts.1001, accounts.1002");
        logger.info("TX-1: UPDATE 1001, then UPDATE 1002");
        logger.info("TX-2: UPDATE 1002, then UPDATE 1001");
        logger.warn("Expected: DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Transaction 1
        executor.submit(() -> {
            Thread.currentThread().setName("TX-1");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");
                logger.info("UPDATE accounts SET balance = balance - 100 WHERE id = 1001");
                row1001.lock();
                logger.success("Row 1001 locked ✓");

                Thread.sleep(100);

                logger.info("UPDATE accounts SET balance = balance + 100 WHERE id = 1002");
                detector.recordWaiting(Thread.currentThread(), row1002);
                row1002.lock();  // 블로킹
                logger.success("Row 1002 locked ✓");

                logger.info("COMMIT");

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                if (row1002.isHeldByCurrentThread()) row1002.unlock();
                if (row1001.isHeldByCurrentThread()) row1001.unlock();
                endLatch.countDown();
            }
        });

        // Transaction 2
        executor.submit(() -> {
            Thread.currentThread().setName("TX-2");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");
                logger.info("UPDATE accounts SET balance = balance - 200 WHERE id = 1002");
                row1002.lock();
                logger.success("Row 1002 locked ✓");

                Thread.sleep(100);

                logger.info("UPDATE accounts SET balance = balance + 200 WHERE id = 1001");
                detector.recordWaiting(Thread.currentThread(), row1001);
                row1001.lock();  // 블로킹
                logger.success("Row 1001 locked ✓");

                logger.info("COMMIT");

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                if (row1001.isHeldByCurrentThread()) row1001.unlock();
                if (row1002.isHeldByCurrentThread()) row1002.unlock();
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        // 데드락 대기
        boolean completed = endLatch.await(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            logger.deadlock("DEADLOCK DETECTED - Transactions stuck!");
            visualizer.printWaitForGraph(detector.getWaitForGraph());
        }

        executor.shutdownNow();
        resourceManager.clear();

        return completed
                ? ScenarioResult.success(getName(), SolutionType.NONE, Duration.ofMillis(elapsed))
                : ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (TryLock + Timeout)");

        LockableResource row1001 = resourceManager.createResource("1001", "Row-1001");
        LockableResource row1002 = resourceManager.createResource("1002", "Row-1002");

        TryLockSolution solution = new TryLockSolution(500, 3);

        logger.info("Database rows: accounts.1001, accounts.1002");
        logger.info("Solution: TryLock with 500ms timeout, 3 retries");
        logger.success("Expected: NO DEADLOCK (timeout & retry)");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Transaction 1
        executor.submit(() -> {
            Thread.currentThread().setName("TX-1");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");

                if (solution.acquireLocks(List.of(row1001, row1002))) {
                    try {
                        logger.success("All rows locked ✓");
                        Thread.sleep(100);
                        logger.info("COMMIT");
                    } finally {
                        solution.releaseLocks(List.of(row1001, row1002));
                    }
                } else {
                    logger.error("Failed to acquire locks - ROLLBACK");
                }

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                endLatch.countDown();
            }
        });

        // Transaction 2
        executor.submit(() -> {
            Thread.currentThread().setName("TX-2");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");

                if (solution.acquireLocks(List.of(row1002, row1001))) {
                    try {
                        logger.success("All rows locked ✓");
                        Thread.sleep(100);
                        logger.info("COMMIT");
                    } finally {
                        solution.releaseLocks(List.of(row1002, row1001));
                    }
                } else {
                    logger.error("Failed to acquire locks - ROLLBACK");
                }

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        resourceManager.clear();

        if (completed) {
            logger.success("All transactions completed!");
            return ScenarioResult.success(getName(), SolutionType.TRY_LOCK_TIMEOUT, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           DATABASE ROW LOCK DEADLOCK SCENARIO                 ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}

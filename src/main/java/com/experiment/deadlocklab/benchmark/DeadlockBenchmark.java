package com.experiment.deadlocklab.benchmark;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.solution.strategies.LockOrderingSolution;
import com.experiment.deadlocklab.solution.strategies.TryLockSolution;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 데드락 발생률 벤치마크
 */
public class DeadlockBenchmark {

    private final ResourceManager resourceManager;
    private final MetricsCollector metrics;
    private final ConsoleLogger logger;

    public DeadlockBenchmark(ResourceManager resourceManager, MetricsCollector metrics) {
        this.resourceManager = resourceManager;
        this.metrics = metrics;
        this.logger = ConsoleLogger.of("Benchmark");
    }

    /**
     * 데드락 발생률 측정
     */
    public void runDeadlockRateBenchmark(int iterations) {
        logger.info("Running deadlock rate benchmark: " + iterations + " iterations");

        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            LockableResource lockA = resourceManager.createResource("A-" + i, "A");
            LockableResource lockB = resourceManager.createResource("B-" + i, "B");

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch latch = new CountDownLatch(2);

            // Thread 1: A -> B
            executor.submit(() -> {
                try {
                    lockA.lock();
                    Thread.sleep(10);
                    if (lockB.tryLock(100, TimeUnit.MILLISECONDS)) {
                        lockB.unlock();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    if (lockA.isHeldByCurrentThread()) lockA.unlock();
                    latch.countDown();
                }
            });

            // Thread 2: B -> A
            executor.submit(() -> {
                try {
                    lockB.lock();
                    Thread.sleep(10);
                    if (lockA.tryLock(100, TimeUnit.MILLISECONDS)) {
                        lockA.unlock();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    if (lockB.isHeldByCurrentThread()) lockB.unlock();
                    latch.countDown();
                }
            });

            try {
                boolean completed = latch.await(500, TimeUnit.MILLISECONDS);
                if (completed) {
                    successCount.incrementAndGet();
                } else {
                    deadlockCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                deadlockCount.incrementAndGet();
            }

            executor.shutdownNow();
            resourceManager.clear();
        }

        double rate = (deadlockCount.get() * 100.0) / iterations;
        logger.info(String.format("Deadlock rate: %.1f%% (%d/%d)",
                rate, deadlockCount.get(), iterations));

        metrics.increment("benchmark.deadlock.count", deadlockCount.get());
        metrics.increment("benchmark.success.count", successCount.get());
    }

    /**
     * 해결책별 성능 비교
     */
    public void runSolutionComparisonBenchmark(int iterations) {
        logger.info("Running solution comparison benchmark: " + iterations + " iterations");
        System.out.println();

        // 1. No solution (baseline)
        logger.info("Testing: No Solution...");
        long noSolutionTime = benchmarkNoSolution(iterations);
        logger.info("  Time: " + noSolutionTime + "ms");

        // 2. Lock Ordering
        logger.info("Testing: Lock Ordering...");
        long lockOrderingTime = benchmarkLockOrdering(iterations);
        logger.info("  Time: " + lockOrderingTime + "ms");

        // 3. TryLock
        logger.info("Testing: TryLock with Timeout...");
        long tryLockTime = benchmarkTryLock(iterations);
        logger.info("  Time: " + tryLockTime + "ms");

        // 결과 출력
        System.out.println();
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║      SOLUTION PERFORMANCE COMPARISON  ║");
        System.out.println("╠═══════════════════════════════════════╣");
        System.out.printf("║  No Solution     : %6d ms           ║%n", noSolutionTime);
        System.out.printf("║  Lock Ordering   : %6d ms           ║%n", lockOrderingTime);
        System.out.printf("║  TryLock Timeout : %6d ms           ║%n", tryLockTime);
        System.out.println("╚═══════════════════════════════════════╝");
    }

    private long benchmarkNoSolution(int iterations) {
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockableResource a = new LockableResource("A", "A");
            LockableResource b = new LockableResource("B", "B");

            a.lock();
            b.lock();
            b.unlock();
            a.unlock();
        }

        return System.currentTimeMillis() - start;
    }

    private long benchmarkLockOrdering(int iterations) {
        LockOrderingSolution solution = new LockOrderingSolution();
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockableResource a = new LockableResource("A", "A");
            LockableResource b = new LockableResource("B", "B");

            try {
                solution.acquireLocks(List.of(a, b));
                solution.releaseLocks(List.of(a, b));
            } catch (InterruptedException e) {
                // ignore
            }
        }

        return System.currentTimeMillis() - start;
    }

    private long benchmarkTryLock(int iterations) {
        TryLockSolution solution = new TryLockSolution(100, 1);
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockableResource a = new LockableResource("A", "A");
            LockableResource b = new LockableResource("B", "B");

            try {
                solution.acquireLocks(List.of(a, b));
                solution.releaseLocks(List.of(a, b));
            } catch (InterruptedException e) {
                // ignore
            }
        }

        return System.currentTimeMillis() - start;
    }
}

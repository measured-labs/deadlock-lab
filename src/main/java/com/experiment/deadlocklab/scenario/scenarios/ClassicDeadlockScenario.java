package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.detection.domain.DetectionResult;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;
import com.experiment.deadlocklab.solution.strategies.LockOrderingSolution;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 고전적 AB-BA 데드락 시나리오
 * 
 * Thread 1: lock(A) -> lock(B)
 * Thread 2: lock(B) -> lock(A)
 * -> DEADLOCK!
 */
public class ClassicDeadlockScenario implements DeadlockScenario {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public ClassicDeadlockScenario(ResourceManager resourceManager,
                                    DeadlockDetector detector,
                                    ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("Classic");
    }

    @Override
    public String getName() {
        return "Classic AB-BA Deadlock";
    }

    @Override
    public String getDescription() {
        return "Two threads acquire locks in opposite order";
    }

    @Override
    public int getRequiredResourceCount() {
        return 2;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.LOCK_ORDERING;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");

        // 자원 생성
        LockableResource lockA = resourceManager.createResource("A", "Lock-A");
        LockableResource lockB = resourceManager.createResource("B", "Lock-B");

        logger.info("Resources created: Lock-A, Lock-B");
        logger.info("Thread-1 will: lock(A) -> lock(B)");
        logger.info("Thread-2 will: lock(B) -> lock(A)");
        logger.warn("Expected: DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // 데드락 감지 시작
        CompletableFuture<DetectionResult> detectionFuture = detector.startMonitoring(5000);

        // Thread 1: A -> B
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();

                logger.info("Acquiring Lock-A...");
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.WAITING, null, "A");
                lockA.lock();
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.RUNNING, "A", null);
                logger.success("Lock-A acquired ✓");

                Thread.sleep(100);  // 데드락 유발을 위한 지연

                logger.info("Acquiring Lock-B...");
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.BLOCKED, "A", "B");
                detector.recordWaiting(Thread.currentThread(), lockB);
                lockB.lock();  // 여기서 블로킹
                detector.recordAcquired(Thread.currentThread(), lockB);
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.RUNNING, "A,B", null);
                logger.success("Lock-B acquired ✓");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.TERMINATED, null, null);
                endLatch.countDown();
            }
        });

        // Thread 2: B -> A
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();

                logger.info("Acquiring Lock-B...");
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.WAITING, null, "B");
                lockB.lock();
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.RUNNING, "B", null);
                logger.success("Lock-B acquired ✓");

                Thread.sleep(100);  // 데드락 유발을 위한 지연

                logger.info("Acquiring Lock-A...");
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.BLOCKED, "B", "A");
                detector.recordWaiting(Thread.currentThread(), lockA);
                lockA.lock();  // 여기서 블로킹
                detector.recordAcquired(Thread.currentThread(), lockA);
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.RUNNING, "A,B", null);
                logger.success("Lock-A acquired ✓");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.TERMINATED, null, null);
                endLatch.countDown();
            }
        });

        // 시작!
        startLatch.countDown();

        // 데드락 감지 대기
        DetectionResult detectionResult;
        try {
            detectionResult = detectionFuture.get(6, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            detectionResult = DetectionResult.timeout();
        } catch (ExecutionException e) {
            detectionResult = DetectionResult.noDeadlock();
        }

        // 결과 출력
        long elapsed = System.currentTimeMillis() - startTime;

        if (detectionResult.isDeadlockDetected()) {
            visualizer.printDeadlockVisualization(detectionResult.getInvolvedThreads());
            logger.deadlock("DEADLOCK CONFIRMED!");
        }

        // 정리
        detector.stopMonitoring();
        executor.shutdownNow();
        resourceManager.clear();

        return ScenarioResult.deadlock(getName(), Duration.ofMillis(elapsed), detectionResult);
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (Lock Ordering)");

        // 자원 생성
        LockableResource lockA = resourceManager.createResource("A", "Lock-A");
        LockableResource lockB = resourceManager.createResource("B", "Lock-B");

        LockOrderingSolution solution = new LockOrderingSolution();

        logger.info("Resources created: Lock-A, Lock-B");
        logger.info("Solution: Lock Ordering (always acquire in ID order: A -> B)");
        logger.info("Thread-1 will: lock(A) -> lock(B)");
        logger.info("Thread-2 will: lock(A) -> lock(B)  <-- SAME ORDER!");
        logger.success("Expected: NO DEADLOCK");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Thread 1: A -> B (ordered)
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();

                logger.info("Acquiring locks in order (A -> B)...");
                solution.lockInOrder(lockA, lockB);
                logger.success("Both locks acquired ✓");

                Thread.sleep(200);  // 작업 수행
                logger.info("Work done");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                logger.info("Locks released");
                endLatch.countDown();
            }
        });

        // Thread 2: A -> B (ordered) - 같은 순서!
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();

                logger.info("Acquiring locks in order (A -> B)...");
                solution.lockInOrder(lockA, lockB);
                logger.success("Both locks acquired ✓");

                Thread.sleep(200);  // 작업 수행
                logger.info("Work done");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                logger.info("Locks released");
                endLatch.countDown();
            }
        });

        // 시작!
        startLatch.countDown();

        // 완료 대기
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        resourceManager.clear();

        if (completed) {
            logger.success("All threads completed without deadlock!");
            return ScenarioResult.success(getName(), SolutionType.LOCK_ORDERING, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           CLASSIC AB-BA DEADLOCK SCENARIO                     ║");
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

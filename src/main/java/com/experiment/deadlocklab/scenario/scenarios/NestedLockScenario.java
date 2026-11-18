package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 중첩 synchronized 데드락 시나리오
 * 
 * ServiceA.methodA() -> synchronized(this) -> serviceB.methodB()
 * ServiceB.methodB() -> synchronized(this) -> serviceA.callback()
 * -> DEADLOCK!
 */
public class NestedLockScenario implements DeadlockScenario {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public NestedLockScenario(ResourceManager resourceManager,
                               DeadlockDetector detector,
                               ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("NestedLock");
    }

    @Override
    public String getName() {
        return "Nested Lock Deadlock";
    }

    @Override
    public String getDescription() {
        return "Synchronized methods calling each other";
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

        // 문제가 되는 서비스들
        UnsafeServiceA serviceA = new UnsafeServiceA();
        UnsafeServiceB serviceB = new UnsafeServiceB();
        serviceA.setServiceB(serviceB);
        serviceB.setServiceA(serviceA);

        logger.info("UnsafeServiceA.methodA(): synchronized(this) { serviceB.methodB() }");
        logger.info("UnsafeServiceB.methodB(): synchronized(this) { serviceA.callback() }");
        logger.warn("Expected: DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Thread 1: serviceA.methodA() 호출
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();
                logger.info("Calling serviceA.methodA()");
                serviceA.methodA();
                logger.success("methodA completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: serviceB.methodB() 호출
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();
                logger.info("Calling serviceB.methodB()");
                serviceB.methodB();
                logger.success("methodB completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        boolean completed = endLatch.await(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            logger.deadlock("DEADLOCK! Nested synchronized calls blocked!");
            printDeadlockDiagram();
        }

        executor.shutdownNow();

        return completed
                ? ScenarioResult.success(getName(), SolutionType.NONE, Duration.ofMillis(elapsed))
                : ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (Lock Scope Minimization)");

        // 안전한 서비스들
        SafeServiceA serviceA = new SafeServiceA();
        SafeServiceB serviceB = new SafeServiceB();
        serviceA.setServiceB(serviceB);
        serviceB.setServiceA(serviceA);

        logger.info("SafeServiceA.methodA(): { synchronized(this) { prepare } ; serviceB.methodB() }");
        logger.info("SafeServiceB.methodB(): { synchronized(this) { prepare } ; serviceA.callback() }");
        logger.info("Solution: Release lock BEFORE calling external method");
        logger.success("Expected: NO DEADLOCK");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Thread 1
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();
                logger.info("Calling serviceA.methodA()");
                serviceA.methodA();
                logger.success("methodA completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();
                logger.info("Calling serviceB.methodB()");
                serviceB.methodB();
                logger.success("methodB completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();

        if (completed) {
            logger.success("All calls completed! No deadlock!");
            return ScenarioResult.success(getName(), SolutionType.LOCK_ORDERING, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           NESTED LOCK DEADLOCK SCENARIO                       ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printDeadlockDiagram() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────┐");
        System.out.println("  │  Thread-1                Thread-2          │");
        System.out.println("  │     │                       │              │");
        System.out.println("  │     ▼                       ▼              │");
        System.out.println("  │  synchronized(A)       synchronized(B)    │");
        System.out.println("  │     │                       │              │");
        System.out.println("  │     ▼                       ▼              │");
        System.out.println("  │  serviceB.methodB()    serviceA.callback() │");
        System.out.println("  │     │                       │              │");
        System.out.println("  │     ▼                       ▼              │");
        System.out.println("  │  WAITING for B ◄──────► WAITING for A     │");
        System.out.println("  │                 DEADLOCK!                  │");
        System.out.println("  └─────────────────────────────────────────────┘");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }

    // ========== 문제가 되는 서비스들 ==========

    class UnsafeServiceA {
        private UnsafeServiceB serviceB;

        void setServiceB(UnsafeServiceB serviceB) {
            this.serviceB = serviceB;
        }

        synchronized void methodA() throws InterruptedException {
            logger.debug("ServiceA.methodA: acquired lock on A");
            Thread.sleep(100);
            logger.debug("ServiceA.methodA: calling serviceB.methodB()");
            serviceB.methodB();  // B의 락 필요 -> DEADLOCK 가능
        }

        synchronized void callback() {
            logger.debug("ServiceA.callback: executed");
        }
    }

    class UnsafeServiceB {
        private UnsafeServiceA serviceA;

        void setServiceA(UnsafeServiceA serviceA) {
            this.serviceA = serviceA;
        }

        synchronized void methodB() throws InterruptedException {
            logger.debug("ServiceB.methodB: acquired lock on B");
            Thread.sleep(100);
            logger.debug("ServiceB.methodB: calling serviceA.callback()");
            serviceA.callback();  // A의 락 필요 -> DEADLOCK 가능
        }
    }

    // ========== 안전한 서비스들 ==========

    class SafeServiceA {
        private SafeServiceB serviceB;

        void setServiceB(SafeServiceB serviceB) {
            this.serviceB = serviceB;
        }

        void methodA() throws InterruptedException {
            Object data;
            synchronized (this) {
                logger.debug("SafeServiceA.methodA: short lock, preparing data");
                Thread.sleep(50);
                data = "prepared";
            }
            // 락 해제 후 외부 호출
            logger.debug("SafeServiceA.methodA: lock released, calling serviceB");
            serviceB.methodB();
        }

        void callback() {
            synchronized (this) {
                logger.debug("SafeServiceA.callback: executed");
            }
        }
    }

    class SafeServiceB {
        private SafeServiceA serviceA;

        void setServiceA(SafeServiceA serviceA) {
            this.serviceA = serviceA;
        }

        void methodB() throws InterruptedException {
            Object data;
            synchronized (this) {
                logger.debug("SafeServiceB.methodB: short lock, preparing data");
                Thread.sleep(50);
                data = "prepared";
            }
            // 락 해제 후 외부 호출
            logger.debug("SafeServiceB.methodB: lock released, calling serviceA");
            serviceA.callback();
        }
    }
}

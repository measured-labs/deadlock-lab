package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 식사하는 철학자 문제 (Dining Philosophers)
 * 
 * 5명의 철학자, 5개의 포크
 * 각 철학자는 양쪽 포크가 필요
 * 모두 왼쪽 포크를 먼저 집으면 -> DEADLOCK!
 */
public class PhilosopherScenario implements DeadlockScenario {

    private static final int NUM_PHILOSOPHERS = 5;
    private static final String[] NAMES = {"Plato", "Aristotle", "Socrates", "Descartes", "Kant"};

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public PhilosopherScenario(ResourceManager resourceManager,
                                DeadlockDetector detector,
                                ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("Philosopher");
    }

    @Override
    public String getName() {
        return "Dining Philosophers";
    }

    @Override
    public String getDescription() {
        return "Five philosophers, five forks, circular deadlock";
    }

    @Override
    public int getRequiredResourceCount() {
        return NUM_PHILOSOPHERS;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.LOCK_ORDERING;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");
        printTable();

        // 포크 생성
        LockableResource[] forks = new LockableResource[NUM_PHILOSOPHERS];
        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            forks[i] = resourceManager.createResource("Fork-" + i, "Fork-" + i);
        }

        logger.info("Each philosopher picks up LEFT fork first, then RIGHT");
        logger.warn("Expected: All pick left fork -> nobody can pick right -> DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_PHILOSOPHERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(NUM_PHILOSOPHERS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            final int id = i;
            final int left = i;
            final int right = (i + 1) % NUM_PHILOSOPHERS;

            executor.submit(() -> {
                Thread.currentThread().setName(NAMES[id]);
                try {
                    startLatch.await();

                    logger.info("Thinking...");
                    Thread.sleep(50);

                    // 왼쪽 포크 집기
                    logger.info("Picking up left fork (Fork-" + left + ")");
                    forks[left].lock();
                    logger.success("Got left fork ✓");

                    Thread.sleep(100);  // 데드락 유발

                    // 오른쪽 포크 집기
                    logger.info("Picking up right fork (Fork-" + right + ")");
                    detector.recordWaiting(Thread.currentThread(), forks[right]);
                    forks[right].lock();  // 블로킹!
                    logger.success("Got right fork ✓");

                    // 식사
                    logger.info("Eating... 🍝");
                    Thread.sleep(100);

                    // 포크 내려놓기
                    forks[right].unlock();
                    forks[left].unlock();
                    logger.info("Finished eating, forks released");

                } catch (InterruptedException e) {
                    logger.warn("Interrupted");
                } finally {
                    if (forks[right].isHeldByCurrentThread()) forks[right].unlock();
                    if (forks[left].isHeldByCurrentThread()) forks[left].unlock();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = endLatch.await(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            logger.deadlock("DEADLOCK! All philosophers holding left fork, waiting for right!");
            printDeadlockState();
        }

        executor.shutdownNow();
        resourceManager.clear();

        return completed
                ? ScenarioResult.success(getName(), SolutionType.NONE, Duration.ofMillis(elapsed))
                : ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (Resource Ordering)");
        printTable();

        // 포크 생성
        LockableResource[] forks = new LockableResource[NUM_PHILOSOPHERS];
        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            forks[i] = resourceManager.createResource("Fork-" + i, "Fork-" + i);
        }

        logger.info("Solution: Always pick up LOWER numbered fork first");
        logger.info("  Philosopher 0: Fork-0 -> Fork-1");
        logger.info("  Philosopher 1: Fork-1 -> Fork-2");
        logger.info("  ...");
        logger.info("  Philosopher 4: Fork-0 -> Fork-4  <-- Order changed!");
        logger.success("Expected: NO DEADLOCK");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_PHILOSOPHERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(NUM_PHILOSOPHERS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            final int id = i;
            final int left = i;
            final int right = (i + 1) % NUM_PHILOSOPHERS;

            // 자원 순서화: 낮은 번호부터 획득
            final int first = Math.min(left, right);
            final int second = Math.max(left, right);

            executor.submit(() -> {
                Thread.currentThread().setName(NAMES[id]);
                try {
                    startLatch.await();

                    logger.info("Thinking...");
                    Thread.sleep(50 + (int)(Math.random() * 50));

                    // 순서대로 포크 집기
                    logger.info("Picking up Fork-" + first + " (ordered)");
                    forks[first].lock();
                    logger.success("Got Fork-" + first + " ✓");

                    logger.info("Picking up Fork-" + second + " (ordered)");
                    forks[second].lock();
                    logger.success("Got Fork-" + second + " ✓");

                    // 식사
                    logger.info("Eating... 🍝");
                    Thread.sleep(100);

                    // 포크 내려놓기 (역순)
                    forks[second].unlock();
                    forks[first].unlock();
                    logger.info("Finished eating, forks released");

                } catch (InterruptedException e) {
                    logger.warn("Interrupted");
                } finally {
                    if (forks[second].isHeldByCurrentThread()) forks[second].unlock();
                    if (forks[first].isHeldByCurrentThread()) forks[first].unlock();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        resourceManager.clear();

        if (completed) {
            logger.success("All philosophers finished eating! No deadlock!");
            return ScenarioResult.success(getName(), SolutionType.LOCK_ORDERING, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           DINING PHILOSOPHERS SCENARIO                        ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printTable() {
        System.out.println("              [Plato]");
        System.out.println("            🍴      🍴");
        System.out.println("       [Kant]  🍝  [Aristotle]");
        System.out.println("            🍴      🍴");
        System.out.println("     [Descartes]🍴[Socrates]");
        System.out.println();
    }

    private void printDeadlockState() {
        System.out.println();
        System.out.println("  ╔═══════════════════════════════════╗");
        System.out.println("  ║       CIRCULAR WAIT DETECTED      ║");
        System.out.println("  ╠═══════════════════════════════════╣");
        System.out.println("  ║  Plato     : Fork-0 ✓ → Fork-1 ⏳ ║");
        System.out.println("  ║  Aristotle : Fork-1 ✓ → Fork-2 ⏳ ║");
        System.out.println("  ║  Socrates  : Fork-2 ✓ → Fork-3 ⏳ ║");
        System.out.println("  ║  Descartes : Fork-3 ✓ → Fork-4 ⏳ ║");
        System.out.println("  ║  Kant      : Fork-4 ✓ → Fork-0 ⏳ ║");
        System.out.println("  ╚═══════════════════════════════════╝");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}

package com.experiment.deadlocklab.detection.application;

import com.experiment.deadlocklab.detection.domain.DetectionResult;
import com.experiment.deadlocklab.detection.domain.WaitForGraph;
import com.experiment.deadlocklab.detection.monitor.ThreadStateMonitor;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 데드락 탐지기
 * - Wait-For Graph 기반 사이클 탐지
 * - 비동기 모니터링 지원
 */
public class DeadlockDetector {

    private static final long DEFAULT_CHECK_INTERVAL_MS = 100;
    private static final long DEFAULT_TIMEOUT_MS = 10000;

    private final WaitForGraph waitForGraph;
    private final ThreadStateMonitor threadMonitor;
    private final ConsoleLogger logger;

    private volatile boolean monitoring = false;
    private ExecutorService monitorExecutor;

    public DeadlockDetector() {
        this.waitForGraph = new WaitForGraph();
        this.threadMonitor = new ThreadStateMonitor();
        this.logger = ConsoleLogger.of("Detector");
    }

    public DeadlockDetector(WaitForGraph waitForGraph, ThreadStateMonitor threadMonitor) {
        this.waitForGraph = waitForGraph;
        this.threadMonitor = threadMonitor;
        this.logger = ConsoleLogger.of("Detector");
    }

    /**
     * 동기식 데드락 체크
     */
    public DetectionResult checkNow() {
        // 스레드 대기 관계 수집
        Map<Thread, Thread> waitingRelations = threadMonitor.getWaitingRelations();

        // 그래프 업데이트
        waitForGraph.update(waitingRelations);

        // 사이클 탐지
        if (waitForGraph.hasCycle()) {
            List<Thread> cycle = waitForGraph.findCycle();
            logger.deadlock("DEADLOCK DETECTED! Cycle: " + cycle);
            return DetectionResult.deadlockDetected(cycle);
        }

        return DetectionResult.noDeadlock();
    }

    /**
     * 비동기 모니터링 시작
     */
    public CompletableFuture<DetectionResult> startMonitoring() {
        return startMonitoring(DEFAULT_TIMEOUT_MS);
    }

    public CompletableFuture<DetectionResult> startMonitoring(long timeoutMs) {
        monitoring = true;
        monitorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DeadlockMonitor");
            t.setDaemon(true);
            return t;
        });

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            while (monitoring && (System.currentTimeMillis() - startTime) < timeoutMs) {
                try {
                    DetectionResult result = checkNow();
                    if (result.isDeadlockDetected()) {
                        return result;
                    }
                    Thread.sleep(DEFAULT_CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            return DetectionResult.timeout();
        }, monitorExecutor);
    }

    /**
     * 모니터링 중지
     */
    public void stopMonitoring() {
        monitoring = false;
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
        }
    }

    /**
     * Wait-For Graph에 간선 추가
     */
    public void addWaitEdge(Thread waiter, Thread holder) {
        waitForGraph.addEdge(waiter, holder);
    }

    /**
     * Wait-For Graph에서 간선 제거
     */
    public void removeWaitEdge(Thread waiter, Thread holder) {
        waitForGraph.removeEdge(waiter, holder);
    }

    /**
     * 스레드가 자원 대기 시작 기록
     */
    public void recordWaiting(Thread thread, com.experiment.deadlocklab.resource.domain.LockableResource resource) {
        threadMonitor.recordWaiting(thread, resource);

        // 자원을 보유한 스레드가 있으면 그래프에 간선 추가
        Thread holder = resource.getHolder();
        if (holder != null && holder != thread) {
            waitForGraph.addEdge(thread, holder);
        }
    }

    /**
     * 스레드가 자원 획득 기록
     */
    public void recordAcquired(Thread thread, com.experiment.deadlocklab.resource.domain.LockableResource resource) {
        threadMonitor.recordAcquired(thread, resource);
        // 대기 간선 제거
        waitForGraph.removeThread(thread);
    }

    /**
     * 스레드가 자원 해제 기록
     */
    public void recordReleased(Thread thread, com.experiment.deadlocklab.resource.domain.LockableResource resource) {
        threadMonitor.recordReleased(thread, resource);
    }

    /**
     * 현재 Wait-For Graph 반환
     */
    public WaitForGraph getWaitForGraph() {
        return waitForGraph;
    }

    /**
     * 스레드 모니터 반환
     */
    public ThreadStateMonitor getThreadMonitor() {
        return threadMonitor;
    }

    /**
     * 상태 초기화
     */
    public void clear() {
        waitForGraph.clear();
        threadMonitor.clear();
    }
}

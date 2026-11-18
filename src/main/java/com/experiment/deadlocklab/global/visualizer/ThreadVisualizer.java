package com.experiment.deadlocklab.global.visualizer;

import com.experiment.deadlocklab.detection.domain.WaitForGraph;
import com.experiment.deadlocklab.resource.domain.LockableResource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 스레드 상태 시각화
 * - 실시간 스레드 상태 표시
 * - Wait-For Graph 시각화
 */
public class ThreadVisualizer {

    private final Map<String, ThreadStatus> threadStatuses = new ConcurrentHashMap<>();

    /**
     * 스레드 상태 업데이트
     */
    public void updateStatus(String threadName, State state, String holdingLock, String waitingFor) {
        threadStatuses.put(threadName, new ThreadStatus(threadName, state, holdingLock, waitingFor));
    }

    /**
     * 스레드 상태 제거
     */
    public void removeThread(String threadName) {
        threadStatuses.remove(threadName);
    }

    /**
     * 현재 상태 출력
     */
    public void printStatus() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("         DEADLOCK LAB - Thread Status Monitor          ");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        if (threadStatuses.isEmpty()) {
            System.out.println("  No active threads");
        } else {
            threadStatuses.values().forEach(status -> {
                String stateStr = formatState(status.state);
                String lockInfo = formatLockInfo(status);
                System.out.printf("  %-12s [%-8s] %s%n", status.threadName, stateStr, lockInfo);
            });
        }

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
    }

    /**
     * Wait-For Graph 시각화
     */
    public void printWaitForGraph(WaitForGraph graph) {
        System.out.println();
        System.out.println("Wait-For Graph:");

        Map<Thread, Set<Thread>> edges = graph.getEdges();
        if (edges.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }

        edges.forEach((waiter, holders) -> {
            holders.forEach(holder -> {
                System.out.printf("  %s ──────→ %s%n",
                        waiter.getName(), holder.getName());
            });
        });

        if (graph.hasCycle()) {
            System.out.println();
            System.out.println("  🔴 CYCLE DETECTED = DEADLOCK!");
        }
    }

    /**
     * 데드락 상황 시각화
     */
    public void printDeadlockVisualization(List<Thread> cycle) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║              🔴 DEADLOCK DETECTED! 🔴                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════╣");
        System.out.println("║                                                       ║");

        // 사이클 시각화
        if (cycle != null && !cycle.isEmpty()) {
            StringBuilder cycleStr = new StringBuilder("  ");
            for (int i = 0; i < cycle.size(); i++) {
                cycleStr.append(cycle.get(i).getName());
                if (i < cycle.size() - 1) {
                    cycleStr.append(" → ");
                }
            }
            cycleStr.append(" → ").append(cycle.get(0).getName()).append(" (cycle)");

            System.out.println("║  " + padRight(cycleStr.toString(), 53) + "║");
        }

        System.out.println("║                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String formatState(State state) {
        return switch (state) {
            case RUNNING -> "RUNNING ";
            case WAITING -> "WAITING ";
            case BLOCKED -> "BLOCKED ";
            case TERMINATED -> "DONE    ";
        };
    }

    private String formatLockInfo(ThreadStatus status) {
        StringBuilder sb = new StringBuilder();

        if (status.holdingLock != null) {
            sb.append("Lock(").append(status.holdingLock).append(")✓");
        }

        if (status.waitingFor != null) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append("Lock(").append(status.waitingFor).append(")⏳");
        }

        return sb.toString();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }

    public enum State {
        RUNNING, WAITING, BLOCKED, TERMINATED
    }

    private record ThreadStatus(String threadName, State state, String holdingLock, String waitingFor) {}
}

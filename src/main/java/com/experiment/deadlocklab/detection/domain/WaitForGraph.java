package com.experiment.deadlocklab.detection.domain;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wait-For Graph (대기 그래프)
 * - 스레드 간 대기 관계를 그래프로 표현
 * - 사이클 탐지로 데드락 감지
 */
public class WaitForGraph {

    // 간선: waiter -> holders (waiter가 holder들이 가진 자원을 대기)
    private final Map<Thread, Set<Thread>> edges = new ConcurrentHashMap<>();

    /**
     * 간선 추가: waiter가 holder를 대기
     */
    public void addEdge(Thread waiter, Thread holder) {
        if (waiter == null || holder == null || waiter == holder) {
            return;
        }
        edges.computeIfAbsent(waiter, k -> ConcurrentHashMap.newKeySet()).add(holder);
    }

    /**
     * 간선 제거
     */
    public void removeEdge(Thread waiter, Thread holder) {
        Set<Thread> holders = edges.get(waiter);
        if (holders != null) {
            holders.remove(holder);
            if (holders.isEmpty()) {
                edges.remove(waiter);
            }
        }
    }

    /**
     * 특정 스레드의 모든 간선 제거
     */
    public void removeThread(Thread thread) {
        edges.remove(thread);
        edges.values().forEach(holders -> holders.remove(thread));
    }

    /**
     * 그래프 업데이트
     */
    public void update(Map<Thread, Thread> waitingRelations) {
        // 기존 간선 제거
        edges.clear();

        // 새 간선 추가
        waitingRelations.forEach(this::addEdge);
    }

    /**
     * 사이클 존재 여부 (DFS)
     */
    public boolean hasCycle() {
        Set<Thread> visited = new HashSet<>();
        Set<Thread> recursionStack = new HashSet<>();

        for (Thread thread : edges.keySet()) {
            if (detectCycle(thread, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFS로 사이클 탐지
     */
    private boolean detectCycle(Thread current, Set<Thread> visited, Set<Thread> recursionStack) {
        if (recursionStack.contains(current)) {
            return true;  // 사이클 발견!
        }
        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recursionStack.add(current);

        Set<Thread> neighbors = edges.get(current);
        if (neighbors != null) {
            for (Thread neighbor : neighbors) {
                if (detectCycle(neighbor, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(current);
        return false;
    }

    /**
     * 사이클을 구성하는 스레드 목록 찾기
     */
    public List<Thread> findCycle() {
        Set<Thread> visited = new HashSet<>();
        List<Thread> path = new ArrayList<>();

        for (Thread thread : edges.keySet()) {
            path.clear();
            if (findCyclePath(thread, visited, path)) {
                return path;
            }
        }
        return Collections.emptyList();
    }

    private boolean findCyclePath(Thread current, Set<Thread> visited, List<Thread> path) {
        if (path.contains(current)) {
            // 사이클 시작점 찾기
            int startIndex = path.indexOf(current);
            List<Thread> cycle = new ArrayList<>(path.subList(startIndex, path.size()));
            path.clear();
            path.addAll(cycle);
            return true;
        }

        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        path.add(current);

        Set<Thread> neighbors = edges.get(current);
        if (neighbors != null) {
            for (Thread neighbor : neighbors) {
                if (findCyclePath(neighbor, visited, path)) {
                    return true;
                }
            }
        }

        path.remove(path.size() - 1);
        return false;
    }

    /**
     * 간선 정보 반환
     */
    public Map<Thread, Set<Thread>> getEdges() {
        return new HashMap<>(edges);
    }

    /**
     * 그래프 초기화
     */
    public void clear() {
        edges.clear();
    }

    /**
     * 그래프 크기 (간선 수)
     */
    public int edgeCount() {
        return edges.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WaitForGraph{\n");
        edges.forEach((waiter, holders) -> {
            holders.forEach(holder -> {
                sb.append(String.format("  %s -> %s%n", waiter.getName(), holder.getName()));
            });
        });
        sb.append("}");
        return sb.toString();
    }
}

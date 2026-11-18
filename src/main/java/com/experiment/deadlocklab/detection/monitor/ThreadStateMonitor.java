package com.experiment.deadlocklab.detection.monitor;

import com.experiment.deadlocklab.resource.domain.LockableResource;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

/**
 * 스레드 상태 모니터
 * - JMX ThreadMXBean 활용
 * - 스레드 대기 관계 추적
 */
public class ThreadStateMonitor {

    private final ThreadMXBean threadMXBean;
    private final Map<Thread, LockableResource> threadWaiting = new HashMap<>();
    private final Map<Thread, LockableResource> threadHolding = new HashMap<>();

    public ThreadStateMonitor() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    /**
     * 스레드가 자원 대기 시작
     */
    public void recordWaiting(Thread thread, LockableResource resource) {
        threadWaiting.put(thread, resource);
    }

    /**
     * 스레드가 자원 획득
     */
    public void recordAcquired(Thread thread, LockableResource resource) {
        threadWaiting.remove(thread);
        threadHolding.put(thread, resource);
    }

    /**
     * 스레드가 자원 해제
     */
    public void recordReleased(Thread thread, LockableResource resource) {
        threadHolding.remove(thread);
    }

    /**
     * 스레드 대기 관계 반환
     * key: 대기 중인 스레드, value: 자원을 보유한 스레드
     */
    public Map<Thread, Thread> getWaitingRelations() {
        Map<Thread, Thread> relations = new HashMap<>();

        threadWaiting.forEach((waiter, resource) -> {
            Thread holder = resource.getHolder();
            if (holder != null && holder != waiter) {
                relations.put(waiter, holder);
            }
        });

        return relations;
    }

    /**
     * JMX를 통한 데드락 탐지
     */
    public long[] findDeadlockedThreadsViaJMX() {
        return threadMXBean.findDeadlockedThreads();
    }

    /**
     * 스레드 정보 조회
     */
    public ThreadInfo getThreadInfo(long threadId) {
        return threadMXBean.getThreadInfo(threadId);
    }

    /**
     * 모든 스레드 정보 조회
     */
    public ThreadInfo[] getAllThreadInfo() {
        long[] threadIds = threadMXBean.getAllThreadIds();
        return threadMXBean.getThreadInfo(threadIds);
    }

    /**
     * 특정 스레드가 대기 중인 자원
     */
    public LockableResource getWaitingResource(Thread thread) {
        return threadWaiting.get(thread);
    }

    /**
     * 특정 스레드가 보유 중인 자원
     */
    public LockableResource getHoldingResource(Thread thread) {
        return threadHolding.get(thread);
    }

    /**
     * 상태 초기화
     */
    public void clear() {
        threadWaiting.clear();
        threadHolding.clear();
    }

    /**
     * 현재 상태 출력
     */
    public void printStatus() {
        System.out.println("\n[Thread Monitor Status]");
        System.out.println("  Waiting:");
        threadWaiting.forEach((t, r) ->
                System.out.printf("    %s -> %s%n", t.getName(), r.getName()));
        System.out.println("  Holding:");
        threadHolding.forEach((t, r) ->
                System.out.printf("    %s : %s%n", t.getName(), r.getName()));
    }
}

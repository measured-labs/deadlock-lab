package com.experiment.deadlocklab.resource.domain;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 락 가능 자원
 * - ReentrantLock 기반
 * - 타임아웃 지원
 * - 데드락 시나리오에서 사용
 */
public class LockableResource extends Resource {

    private final ReentrantLock lock;
    private volatile Thread holder;
    private volatile Thread waiter;

    public LockableResource(String id, String name) {
        super(id, name);
        this.lock = new ReentrantLock(true);  // fair lock
    }

    /**
     * 락 획득 (무한 대기)
     */
    public void lock() {
        waiter = Thread.currentThread();
        lock.lock();
        holder = Thread.currentThread();
        waiter = null;
    }

    /**
     * 락 획득 시도 (타임아웃)
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        waiter = Thread.currentThread();
        boolean acquired = lock.tryLock(timeout, unit);
        if (acquired) {
            holder = Thread.currentThread();
        }
        waiter = null;
        return acquired;
    }

    /**
     * 락 획득 시도 (즉시)
     */
    public boolean tryLock() {
        boolean acquired = lock.tryLock();
        if (acquired) {
            holder = Thread.currentThread();
        }
        return acquired;
    }

    /**
     * 락 해제
     */
    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            holder = null;
            lock.unlock();
        }
    }

    /**
     * 현재 스레드가 락을 보유 중인지 확인
     */
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * 락이 점유 중인지 확인
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    /**
     * 락 보유 스레드
     */
    public Thread getHolder() {
        return holder;
    }

    /**
     * 락 대기 스레드
     */
    public Thread getWaiter() {
        return waiter;
    }

    /**
     * 내부 락 반환 (고급 사용)
     */
    public ReentrantLock getLock() {
        return lock;
    }

    @Override
    public String toString() {
        String status = isLocked()
                ? "locked by " + (holder != null ? holder.getName() : "unknown")
                : "free";
        return String.format("LockableResource{%s, %s}", name, status);
    }
}

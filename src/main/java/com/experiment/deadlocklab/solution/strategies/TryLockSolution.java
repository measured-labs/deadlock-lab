package com.experiment.deadlocklab.solution.strategies;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TryLock + 타임아웃 해결책
 * - 락 획득 시 타임아웃 설정
 * - 실패 시 모든 락 해제 후 재시도
 */
public class TryLockSolution implements DeadlockSolution {

    private static final long DEFAULT_TIMEOUT_MS = 1000;
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final long timeoutMs;
    private final int maxRetries;
    private final ConsoleLogger logger;

    public TryLockSolution() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES);
    }

    public TryLockSolution(long timeoutMs, int maxRetries) {
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.logger = ConsoleLogger.of("TryLock");
    }

    @Override
    public SolutionType getType() {
        return SolutionType.TRY_LOCK_TIMEOUT;
    }

    @Override
    public List<LockableResource> apply(List<LockableResource> resources) {
        // 순서 변경 없음 (타임아웃으로 해결)
        return new ArrayList<>(resources);
    }

    @Override
    public boolean acquireLocks(List<LockableResource> resources) throws InterruptedException {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            logger.debug("Attempt " + attempt + " of " + maxRetries);

            if (tryAcquireAll(resources)) {
                logger.debug("All locks acquired successfully");
                return true;
            }

            // 실패 시 잠시 대기 후 재시도
            logger.warn("Failed to acquire all locks, retrying...");
            Thread.sleep(100 + (long)(Math.random() * 100));  // 랜덤 백오프
        }

        logger.error("Failed to acquire locks after " + maxRetries + " attempts");
        return false;
    }

    /**
     * 모든 락 획득 시도
     */
    private boolean tryAcquireAll(List<LockableResource> resources) throws InterruptedException {
        List<LockableResource> acquired = new ArrayList<>();

        for (LockableResource resource : resources) {
            logger.debug("Trying to acquire: " + resource.getName());

            boolean success = resource.tryLock(timeoutMs, TimeUnit.MILLISECONDS);

            if (success) {
                acquired.add(resource);
                logger.debug("Acquired: " + resource.getName());
            } else {
                // 실패 시 지금까지 획득한 락 모두 해제
                logger.warn("Timeout acquiring: " + resource.getName());
                releaseAll(acquired);
                return false;
            }
        }

        return true;
    }

    /**
     * 획득한 락 모두 해제
     */
    private void releaseAll(List<LockableResource> acquired) {
        for (int i = acquired.size() - 1; i >= 0; i--) {
            LockableResource resource = acquired.get(i);
            resource.unlock();
            logger.debug("Released (rollback): " + resource.getName());
        }
    }

    @Override
    public void releaseLocks(List<LockableResource> resources) {
        for (int i = resources.size() - 1; i >= 0; i--) {
            LockableResource resource = resources.get(i);
            if (resource.isHeldByCurrentThread()) {
                resource.unlock();
                logger.debug("Released: " + resource.getName());
            }
        }
    }
}

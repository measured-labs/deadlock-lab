package com.experiment.deadlocklab.solution.strategies;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 락 순서 고정 해결책
 * - 항상 자원 ID 순서로 락 획득
 * - 순환 대기 조건을 깨뜨림
 */
public class LockOrderingSolution implements DeadlockSolution {

    private final ConsoleLogger logger;

    public LockOrderingSolution() {
        this.logger = ConsoleLogger.of("LockOrdering");
    }

    @Override
    public SolutionType getType() {
        return SolutionType.LOCK_ORDERING;
    }

    @Override
    public List<LockableResource> apply(List<LockableResource> resources) {
        // ID 순서로 정렬
        List<LockableResource> sorted = new ArrayList<>(resources);
        sorted.sort(Comparator.comparing(LockableResource::getId));
        return sorted;
    }

    @Override
    public boolean acquireLocks(List<LockableResource> resources) throws InterruptedException {
        // ID 순서로 정렬
        List<LockableResource> sorted = apply(resources);

        logger.debug("Acquiring locks in order: " +
                sorted.stream().map(LockableResource::getName).toList());

        // 순서대로 락 획득
        for (LockableResource resource : sorted) {
            logger.debug("Acquiring: " + resource.getName());
            resource.lock();
            logger.debug("Acquired: " + resource.getName());
        }

        return true;
    }

    @Override
    public void releaseLocks(List<LockableResource> resources) {
        // 역순으로 해제 (일반적인 관례)
        List<LockableResource> sorted = apply(resources);
        for (int i = sorted.size() - 1; i >= 0; i--) {
            LockableResource resource = sorted.get(i);
            if (resource.isHeldByCurrentThread()) {
                resource.unlock();
                logger.debug("Released: " + resource.getName());
            }
        }
    }

    /**
     * 두 자원에 대해 순서대로 락 획득
     */
    public void lockInOrder(LockableResource a, LockableResource b) {
        LockableResource first = a.getId().compareTo(b.getId()) < 0 ? a : b;
        LockableResource second = first == a ? b : a;

        first.lock();
        second.lock();
    }
}

package com.experiment.deadlocklab.solution.application;

import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.solution.domain.SolutionType;
import com.experiment.deadlocklab.solution.strategies.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 해결책 적용기
 * - 시나리오에 적절한 해결책 적용
 */
public class SolutionApplier {

    private final Map<SolutionType, DeadlockSolution> solutions = new HashMap<>();

    public SolutionApplier() {
        // 해결책 등록
        solutions.put(SolutionType.LOCK_ORDERING, new LockOrderingSolution());
        solutions.put(SolutionType.TRY_LOCK_TIMEOUT, new TryLockSolution());
        solutions.put(SolutionType.BANKER_ALGORITHM, new BankerAlgorithm());
    }

    /**
     * 해결책 적용
     */
    public List<LockableResource> apply(List<LockableResource> resources, SolutionType type) {
        DeadlockSolution solution = solutions.get(type);
        if (solution == null) {
            return resources;
        }
        return solution.apply(resources);
    }

    /**
     * 해결책으로 락 획득
     */
    public boolean acquireWithSolution(List<LockableResource> resources, SolutionType type)
            throws InterruptedException {
        DeadlockSolution solution = solutions.get(type);
        if (solution == null) {
            // 해결책 없이 순차 획득
            for (LockableResource resource : resources) {
                resource.lock();
            }
            return true;
        }
        return solution.acquireLocks(resources);
    }

    /**
     * 해결책으로 락 해제
     */
    public void releaseWithSolution(List<LockableResource> resources, SolutionType type) {
        DeadlockSolution solution = solutions.get(type);
        if (solution != null) {
            solution.releaseLocks(resources);
        } else {
            for (LockableResource resource : resources) {
                if (resource.isHeldByCurrentThread()) {
                    resource.unlock();
                }
            }
        }
    }

    /**
     * 특정 해결책 반환
     */
    public DeadlockSolution getSolution(SolutionType type) {
        return solutions.get(type);
    }
}

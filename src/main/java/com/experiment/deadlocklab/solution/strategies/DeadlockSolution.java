package com.experiment.deadlocklab.solution.strategies;

import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.util.List;

/**
 * 데드락 해결책 인터페이스
 */
public interface DeadlockSolution {

    /**
     * 해결책 타입
     */
    SolutionType getType();

    /**
     * 자원 목록에 해결책 적용
     */
    List<LockableResource> apply(List<LockableResource> resources);

    /**
     * 락 획득 (해결책 적용)
     */
    boolean acquireLocks(List<LockableResource> resources) throws InterruptedException;

    /**
     * 락 해제
     */
    void releaseLocks(List<LockableResource> resources);
}

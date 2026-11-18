package com.experiment.deadlocklab.solution.domain;

/**
 * 데드락 해결책 타입
 */
public enum SolutionType {

    LOCK_ORDERING("Lock Ordering", "항상 같은 순서로 락 획득"),
    TRY_LOCK_TIMEOUT("TryLock with Timeout", "타임아웃 후 재시도"),
    BANKER_ALGORITHM("Banker's Algorithm", "안전 상태 검사 후 자원 할당"),
    NONE("None", "해결책 미적용");

    private final String name;
    private final String description;

    SolutionType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}

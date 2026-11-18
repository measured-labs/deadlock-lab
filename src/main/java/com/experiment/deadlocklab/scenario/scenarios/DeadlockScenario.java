package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

/**
 * 데드락 시나리오 인터페이스
 */
public interface DeadlockScenario {

    /**
     * 시나리오 이름
     */
    String getName();

    /**
     * 시나리오 설명
     */
    String getDescription();

    /**
     * 필요한 자원 수
     */
    int getRequiredResourceCount();

    /**
     * 추천 해결책
     */
    SolutionType getRecommendedSolution();

    /**
     * 시나리오 실행 (해결책 미적용)
     */
    ScenarioResult runWithoutSolution() throws InterruptedException;

    /**
     * 시나리오 실행 (해결책 적용)
     */
    ScenarioResult runWithSolution() throws InterruptedException;
}

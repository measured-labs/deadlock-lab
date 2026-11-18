package com.experiment.deadlocklab.solution.strategies;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.util.*;

/**
 * 은행원 알고리즘 (Banker's Algorithm)
 * - 자원 할당 전 안전 상태 검사
 * - 데드락 회피
 */
public class BankerAlgorithm implements DeadlockSolution {

    private final ConsoleLogger logger;

    // 자원별 전체 개수
    private final Map<String, Integer> totalResources = new HashMap<>();
    // 자원별 사용 가능 개수
    private final Map<String, Integer> available = new HashMap<>();
    // 프로세스별 할당된 자원
    private final Map<String, Map<String, Integer>> allocation = new HashMap<>();
    // 프로세스별 최대 필요 자원
    private final Map<String, Map<String, Integer>> maximum = new HashMap<>();

    public BankerAlgorithm() {
        this.logger = ConsoleLogger.of("Banker");
    }

    @Override
    public SolutionType getType() {
        return SolutionType.BANKER_ALGORITHM;
    }

    /**
     * 자원 등록
     */
    public void registerResource(String resourceId, int count) {
        totalResources.put(resourceId, count);
        available.put(resourceId, count);
    }

    /**
     * 프로세스의 최대 요구량 등록
     */
    public void registerProcess(String processId, Map<String, Integer> maxNeed) {
        maximum.put(processId, new HashMap<>(maxNeed));
        allocation.put(processId, new HashMap<>());

        // 초기 할당은 0
        for (String resourceId : maxNeed.keySet()) {
            allocation.get(processId).put(resourceId, 0);
        }
    }

    /**
     * 자원 요청 처리
     */
    public boolean requestResource(String processId, String resourceId, int count) {
        // 1. 요청이 최대 요구량 이내인지 확인
        int currentAlloc = allocation.getOrDefault(processId, Map.of())
                .getOrDefault(resourceId, 0);
        int maxNeed = maximum.getOrDefault(processId, Map.of())
                .getOrDefault(resourceId, 0);

        if (currentAlloc + count > maxNeed) {
            logger.warn("Request exceeds maximum need");
            return false;
        }

        // 2. 요청이 가용 자원 이내인지 확인
        int avail = available.getOrDefault(resourceId, 0);
        if (count > avail) {
            logger.warn("Not enough available resources");
            return false;
        }

        // 3. 가상으로 할당해보고 안전 상태인지 확인
        // 가상 할당
        available.put(resourceId, avail - count);
        allocation.get(processId).put(resourceId, currentAlloc + count);

        if (isSafeState()) {
            logger.info(String.format("Resource granted: %s to %s (count=%d)",
                    resourceId, processId, count));
            return true;
        } else {
            // 안전하지 않으면 롤백
            available.put(resourceId, avail);
            allocation.get(processId).put(resourceId, currentAlloc);
            logger.warn("Request denied: would lead to unsafe state");
            return false;
        }
    }

    /**
     * 자원 해제
     */
    public void releaseResource(String processId, String resourceId, int count) {
        int currentAlloc = allocation.getOrDefault(processId, Map.of())
                .getOrDefault(resourceId, 0);
        int avail = available.getOrDefault(resourceId, 0);

        allocation.get(processId).put(resourceId, Math.max(0, currentAlloc - count));
        available.put(resourceId, avail + count);

        logger.info(String.format("Resource released: %s from %s (count=%d)",
                resourceId, processId, count));
    }

    /**
     * 안전 상태 검사
     */
    public boolean isSafeState() {
        // 작업 배열 (가용 자원 복사)
        Map<String, Integer> work = new HashMap<>(available);
        // 완료 여부
        Set<String> finished = new HashSet<>();

        int processCount = maximum.size();

        while (finished.size() < processCount) {
            boolean found = false;

            for (String processId : maximum.keySet()) {
                if (finished.contains(processId)) continue;

                // Need = Maximum - Allocation
                Map<String, Integer> need = calculateNeed(processId);

                // Need <= Work인지 확인
                if (canFinish(need, work)) {
                    // 이 프로세스는 완료 가능
                    // Work += Allocation
                    Map<String, Integer> alloc = allocation.get(processId);
                    for (String resourceId : alloc.keySet()) {
                        work.merge(resourceId, alloc.get(resourceId), Integer::sum);
                    }
                    finished.add(processId);
                    found = true;
                    logger.debug("Process " + processId + " can finish");
                }
            }

            if (!found) {
                // 더 이상 완료 가능한 프로세스 없음 → 안전하지 않음
                logger.debug("No process can finish - UNSAFE state");
                return false;
            }
        }

        logger.debug("All processes can finish - SAFE state");
        return true;
    }

    private Map<String, Integer> calculateNeed(String processId) {
        Map<String, Integer> need = new HashMap<>();
        Map<String, Integer> max = maximum.getOrDefault(processId, Map.of());
        Map<String, Integer> alloc = allocation.getOrDefault(processId, Map.of());

        for (String resourceId : max.keySet()) {
            int n = max.get(resourceId) - alloc.getOrDefault(resourceId, 0);
            need.put(resourceId, n);
        }

        return need;
    }

    private boolean canFinish(Map<String, Integer> need, Map<String, Integer> work) {
        for (String resourceId : need.keySet()) {
            if (need.get(resourceId) > work.getOrDefault(resourceId, 0)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<LockableResource> apply(List<LockableResource> resources) {
        // 은행원 알고리즘은 자원 순서가 아닌 안전 상태 검사로 동작
        return new ArrayList<>(resources);
    }

    @Override
    public boolean acquireLocks(List<LockableResource> resources) throws InterruptedException {
        String processId = Thread.currentThread().getName();

        for (LockableResource resource : resources) {
            if (!requestResource(processId, resource.getId(), 1)) {
                // 실패 시 지금까지 획득한 것 해제
                releaseLocks(resources);
                return false;
            }
            resource.lock();
        }

        return true;
    }

    @Override
    public void releaseLocks(List<LockableResource> resources) {
        String processId = Thread.currentThread().getName();

        for (LockableResource resource : resources) {
            if (resource.isHeldByCurrentThread()) {
                resource.unlock();
                releaseResource(processId, resource.getId(), 1);
            }
        }
    }

    /**
     * 상태 초기화
     */
    public void clear() {
        totalResources.clear();
        available.clear();
        allocation.clear();
        maximum.clear();
    }
}

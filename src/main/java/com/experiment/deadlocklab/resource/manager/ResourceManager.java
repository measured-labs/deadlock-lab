package com.experiment.deadlocklab.resource.manager;

import com.experiment.deadlocklab.resource.domain.LockableResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자원 관리자
 * - 자원 생성 및 관리
 * - 자원 상태 조회
 */
public class ResourceManager {

    private final Map<String, LockableResource> resources = new ConcurrentHashMap<>();

    /**
     * 단일 자원 생성
     */
    public LockableResource createResource(String id, String name) {
        LockableResource resource = new LockableResource(id, name);
        resources.put(id, resource);
        return resource;
    }

    /**
     * 여러 자원 생성
     */
    public List<LockableResource> createResources(int count, String namePrefix) {
        List<LockableResource> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = namePrefix + "_" + i;
            String name = namePrefix + "-" + (char)('A' + i);
            created.add(createResource(id, name));
        }
        return created;
    }

    /**
     * 자원 조회
     */
    public LockableResource getResource(String id) {
        return resources.get(id);
    }

    /**
     * 모든 자원 조회
     */
    public List<LockableResource> getAllResources() {
        return new ArrayList<>(resources.values());
    }

    /**
     * 자원 상태 출력
     */
    public void printStatus() {
        System.out.println("\n[Resource Status]");
        resources.values().forEach(r ->
                System.out.println("  " + r.toString()));
    }

    /**
     * 모든 락 해제
     */
    public void releaseAllLocks() {
        resources.values().forEach(r -> {
            if (r.isHeldByCurrentThread()) {
                r.unlock();
            }
        });
    }

    /**
     * 초기화
     */
    public void clear() {
        releaseAllLocks();
        resources.clear();
    }

    /**
     * 자원 수
     */
    public int size() {
        return resources.size();
    }
}

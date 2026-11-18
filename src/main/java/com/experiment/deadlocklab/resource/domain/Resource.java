package com.experiment.deadlocklab.resource.domain;

/**
 * 자원 추상화
 * - 데드락 시나리오에서 사용되는 자원의 기본 클래스
 */
public abstract class Resource {

    protected final String id;
    protected final String name;

    protected Resource(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", name, id);
    }
}

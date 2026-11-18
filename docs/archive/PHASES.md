# Deadlock Lab - 따라 치기 실습 가이드

> 학습 안내서 원문(2026-11-18 `docs/archive/`로 옮김). 전체 소스가 들어 있어 ADR로 나누지 않았다. 구조 정리는 [ADR-000](../adr/ADR-000-deadlock-lab-structure.md)에 있다. 코드 안의 `Expected:` 로그 문구는 시나리오가 노리는 결과이고 측정값이 아니다.

데드락을 직접 재현하고 해결하며 배우는 프로젝트입니다.
아래 Phase 순서대로 파일을 생성하고 코드를 타이핑하세요.

---

## Phase 0: 프로젝트 설정

프로젝트 빌드 설정과 Spring Boot 기본 구성을 만듭니다.

### `settings.gradle`

```groovy
rootProject.name = 'deadlock-lab'
```

### `build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.experiment'
version = '0.0.1-SNAPSHOT'
description = 'Deadlock scenarios simulation and detection lab for Java concurrency learning'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: deadlock-lab
  main:
    banner-mode: off

logging:
  level:
    root: INFO
    com.experiment.deadlocklab: DEBUG
```

### `src/main/java/com/experiment/deadlocklab/DeadlockLabApplication.java` (스텁 버전)

Phase 6에서 전체 버전을 작성합니다. 우선 기본 뼈대만 만들어 두세요.

```java
package com.experiment.deadlocklab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeadlockLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeadlockLabApplication.class, args);
    }
}
```

---

## Phase 1: 공통 인프라

로깅, 메트릭 수집, 스레드 시각화, Spring Bean 설정 등 전체 프로젝트에서 공통으로 사용하는 유틸리티를 작성합니다.

### `src/main/java/com/experiment/deadlocklab/global/logger/ConsoleLogger.java`

```java
package com.experiment.deadlocklab.global.logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 스레드별 색상을 지원하는 콘솔 로거
 */
public class ConsoleLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ANSI 색상 코드
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";

    private static final String[] THREAD_COLORS = {BLUE, GREEN, PURPLE, CYAN, YELLOW, RED};

    private final String prefix;
    private final boolean useColor;

    public ConsoleLogger(String prefix) {
        this(prefix, true);
    }

    public ConsoleLogger(String prefix, boolean useColor) {
        this.prefix = prefix;
        this.useColor = useColor;
    }

    public void info(String message) {
        log("INFO", message, getThreadColor());
    }

    public void success(String message) {
        log("SUCCESS", message, GREEN);
    }

    public void warn(String message) {
        log("WARN", message, YELLOW);
    }

    public void error(String message) {
        log("ERROR", message, RED);
    }

    public void debug(String message) {
        log("DEBUG", message, CYAN);
    }

    public void deadlock(String message) {
        log("DEADLOCK", message, RED);
    }

    private void log(String level, String message, String color) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String threadName = Thread.currentThread().getName();

        String formattedMessage;
        if (useColor) {
            formattedMessage = String.format("%s[%s] [%-15s] [%s] %s%s",
                    color, timestamp, threadName, prefix, message, RESET);
        } else {
            formattedMessage = String.format("[%s] [%-15s] [%s] %s",
                    timestamp, threadName, prefix, message);
        }

        System.out.println(formattedMessage);
    }

    private String getThreadColor() {
        int hash = Math.abs(Thread.currentThread().getName().hashCode());
        return THREAD_COLORS[hash % THREAD_COLORS.length];
    }

    public static ConsoleLogger of(String prefix) {
        return new ConsoleLogger(prefix);
    }

    public static ConsoleLogger of(Class<?> clazz) {
        return new ConsoleLogger(clazz.getSimpleName());
    }
}
```

### `src/main/java/com/experiment/deadlocklab/global/metrics/MetricsCollector.java`

```java
package com.experiment.deadlocklab.global.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 데드락 실험 메트릭 수집기
 */
public class MetricsCollector {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> timers = new ConcurrentHashMap<>();
    private final Map<String, Long> timerStarts = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void increment(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(delta);
    }

    public long getCount(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    public void startTimer(String name) {
        timerStarts.put(name, System.nanoTime());
    }

    public long stopTimer(String name) {
        Long startTime = timerStarts.remove(name);
        if (startTime == null) return 0;
        long elapsed = System.nanoTime() - startTime;
        timers.put(name, elapsed);
        return elapsed;
    }

    public long getTimerMillis(String name) {
        return timers.getOrDefault(name, 0L) / 1_000_000;
    }

    public void reset() {
        counters.clear();
        timers.clear();
        timerStarts.clear();
    }

    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══════════════════════════════════════\n");
        sb.append("        DEADLOCK LAB METRICS\n");
        sb.append("═══════════════════════════════════════\n");

        if (!counters.isEmpty()) {
            sb.append("\n[Counters]\n");
            counters.forEach((name, value) ->
                    sb.append(String.format("  %-35s : %d%n", name, value.get())));
        }

        if (!timers.isEmpty()) {
            sb.append("\n[Timers]\n");
            timers.forEach((name, nanos) ->
                    sb.append(String.format("  %-35s : %d ms%n", name, nanos / 1_000_000)));
        }

        sb.append("\n═══════════════════════════════════════\n");
        return sb.toString();
    }

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }
}
```

### `src/main/java/com/experiment/deadlocklab/global/visualizer/ThreadVisualizer.java`

```java
package com.experiment.deadlocklab.global.visualizer;

import com.experiment.deadlocklab.detection.domain.WaitForGraph;
import com.experiment.deadlocklab.resource.domain.LockableResource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 스레드 상태 시각화
 * - 실시간 스레드 상태 표시
 * - Wait-For Graph 시각화
 */
public class ThreadVisualizer {

    private final Map<String, ThreadStatus> threadStatuses = new ConcurrentHashMap<>();

    /**
     * 스레드 상태 업데이트
     */
    public void updateStatus(String threadName, State state, String holdingLock, String waitingFor) {
        threadStatuses.put(threadName, new ThreadStatus(threadName, state, holdingLock, waitingFor));
    }

    /**
     * 스레드 상태 제거
     */
    public void removeThread(String threadName) {
        threadStatuses.remove(threadName);
    }

    /**
     * 현재 상태 출력
     */
    public void printStatus() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("         DEADLOCK LAB - Thread Status Monitor          ");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        if (threadStatuses.isEmpty()) {
            System.out.println("  No active threads");
        } else {
            threadStatuses.values().forEach(status -> {
                String stateStr = formatState(status.state);
                String lockInfo = formatLockInfo(status);
                System.out.printf("  %-12s [%-8s] %s%n", status.threadName, stateStr, lockInfo);
            });
        }

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
    }

    /**
     * Wait-For Graph 시각화
     */
    public void printWaitForGraph(WaitForGraph graph) {
        System.out.println();
        System.out.println("Wait-For Graph:");

        Map<Thread, Set<Thread>> edges = graph.getEdges();
        if (edges.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }

        edges.forEach((waiter, holders) -> {
            holders.forEach(holder -> {
                System.out.printf("  %s ──────→ %s%n",
                        waiter.getName(), holder.getName());
            });
        });

        if (graph.hasCycle()) {
            System.out.println();
            System.out.println("  🔴 CYCLE DETECTED = DEADLOCK!");
        }
    }

    /**
     * 데드락 상황 시각화
     */
    public void printDeadlockVisualization(List<Thread> cycle) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║              🔴 DEADLOCK DETECTED! 🔴                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════╣");
        System.out.println("║                                                       ║");

        // 사이클 시각화
        if (cycle != null && !cycle.isEmpty()) {
            StringBuilder cycleStr = new StringBuilder("  ");
            for (int i = 0; i < cycle.size(); i++) {
                cycleStr.append(cycle.get(i).getName());
                if (i < cycle.size() - 1) {
                    cycleStr.append(" → ");
                }
            }
            cycleStr.append(" → ").append(cycle.get(0).getName()).append(" (cycle)");

            System.out.println("║  " + padRight(cycleStr.toString(), 53) + "║");
        }

        System.out.println("║                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String formatState(State state) {
        return switch (state) {
            case RUNNING -> "RUNNING ";
            case WAITING -> "WAITING ";
            case BLOCKED -> "BLOCKED ";
            case TERMINATED -> "DONE    ";
        };
    }

    private String formatLockInfo(ThreadStatus status) {
        StringBuilder sb = new StringBuilder();

        if (status.holdingLock != null) {
            sb.append("Lock(").append(status.holdingLock).append(")✓");
        }

        if (status.waitingFor != null) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append("Lock(").append(status.waitingFor).append(")⏳");
        }

        return sb.toString();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }

    public enum State {
        RUNNING, WAITING, BLOCKED, TERMINATED
    }

    private record ThreadStatus(String threadName, State state, String holdingLock, String waitingFor) {}
}
```

### `src/main/java/com/experiment/deadlocklab/global/config/BeanConfig.java`

```java
package com.experiment.deadlocklab.global.config;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Bean 설정
 */
@Configuration
public class BeanConfig {

    @Bean
    public MetricsCollector metricsCollector() {
        return new MetricsCollector();
    }

    @Bean
    public ConsoleLogger consoleLogger() {
        return new ConsoleLogger("DeadlockLab");
    }

    @Bean
    public ThreadVisualizer threadVisualizer() {
        return new ThreadVisualizer();
    }
}
```

---

## Phase 2: Resource 도메인

데드락 시나리오에서 사용되는 자원(Resource)과 락(Lock)을 추상화합니다.

### `src/main/java/com/experiment/deadlocklab/resource/domain/Resource.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/resource/domain/LockableResource.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/resource/manager/ResourceManager.java`

```java
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
```

---

## Phase 3: 데드락 탐지

Wait-For Graph를 구축하고 DFS로 사이클을 탐지하여 데드락을 감지합니다.

### `src/main/java/com/experiment/deadlocklab/detection/domain/WaitForGraph.java`

```java
package com.experiment.deadlocklab.detection.domain;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wait-For Graph (대기 그래프)
 * - 스레드 간 대기 관계를 그래프로 표현
 * - 사이클 탐지로 데드락 감지
 */
public class WaitForGraph {

    // 간선: waiter -> holders (waiter가 holder들이 가진 자원을 대기)
    private final Map<Thread, Set<Thread>> edges = new ConcurrentHashMap<>();

    /**
     * 간선 추가: waiter가 holder를 대기
     */
    public void addEdge(Thread waiter, Thread holder) {
        if (waiter == null || holder == null || waiter == holder) {
            return;
        }
        edges.computeIfAbsent(waiter, k -> ConcurrentHashMap.newKeySet()).add(holder);
    }

    /**
     * 간선 제거
     */
    public void removeEdge(Thread waiter, Thread holder) {
        Set<Thread> holders = edges.get(waiter);
        if (holders != null) {
            holders.remove(holder);
            if (holders.isEmpty()) {
                edges.remove(waiter);
            }
        }
    }

    /**
     * 특정 스레드의 모든 간선 제거
     */
    public void removeThread(Thread thread) {
        edges.remove(thread);
        edges.values().forEach(holders -> holders.remove(thread));
    }

    /**
     * 그래프 업데이트
     */
    public void update(Map<Thread, Thread> waitingRelations) {
        // 기존 간선 제거
        edges.clear();

        // 새 간선 추가
        waitingRelations.forEach(this::addEdge);
    }

    /**
     * 사이클 존재 여부 (DFS)
     */
    public boolean hasCycle() {
        Set<Thread> visited = new HashSet<>();
        Set<Thread> recursionStack = new HashSet<>();

        for (Thread thread : edges.keySet()) {
            if (detectCycle(thread, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFS로 사이클 탐지
     */
    private boolean detectCycle(Thread current, Set<Thread> visited, Set<Thread> recursionStack) {
        if (recursionStack.contains(current)) {
            return true;  // 사이클 발견!
        }
        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recursionStack.add(current);

        Set<Thread> neighbors = edges.get(current);
        if (neighbors != null) {
            for (Thread neighbor : neighbors) {
                if (detectCycle(neighbor, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(current);
        return false;
    }

    /**
     * 사이클을 구성하는 스레드 목록 찾기
     */
    public List<Thread> findCycle() {
        Set<Thread> visited = new HashSet<>();
        List<Thread> path = new ArrayList<>();

        for (Thread thread : edges.keySet()) {
            path.clear();
            if (findCyclePath(thread, visited, path)) {
                return path;
            }
        }
        return Collections.emptyList();
    }

    private boolean findCyclePath(Thread current, Set<Thread> visited, List<Thread> path) {
        if (path.contains(current)) {
            // 사이클 시작점 찾기
            int startIndex = path.indexOf(current);
            List<Thread> cycle = new ArrayList<>(path.subList(startIndex, path.size()));
            path.clear();
            path.addAll(cycle);
            return true;
        }

        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        path.add(current);

        Set<Thread> neighbors = edges.get(current);
        if (neighbors != null) {
            for (Thread neighbor : neighbors) {
                if (findCyclePath(neighbor, visited, path)) {
                    return true;
                }
            }
        }

        path.remove(path.size() - 1);
        return false;
    }

    /**
     * 간선 정보 반환
     */
    public Map<Thread, Set<Thread>> getEdges() {
        return new HashMap<>(edges);
    }

    /**
     * 그래프 초기화
     */
    public void clear() {
        edges.clear();
    }

    /**
     * 그래프 크기 (간선 수)
     */
    public int edgeCount() {
        return edges.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WaitForGraph{\n");
        edges.forEach((waiter, holders) -> {
            holders.forEach(holder -> {
                sb.append(String.format("  %s -> %s%n", waiter.getName(), holder.getName()));
            });
        });
        sb.append("}");
        return sb.toString();
    }
}
```

### `src/main/java/com/experiment/deadlocklab/detection/domain/DetectionResult.java`

```java
package com.experiment.deadlocklab.detection.domain;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 데드락 탐지 결과
 */
public class DetectionResult {

    private final boolean deadlockDetected;
    private final List<Thread> involvedThreads;
    private final LocalDateTime detectedAt;
    private final String description;

    private DetectionResult(boolean deadlockDetected, List<Thread> involvedThreads, String description) {
        this.deadlockDetected = deadlockDetected;
        this.involvedThreads = involvedThreads;
        this.detectedAt = LocalDateTime.now();
        this.description = description;
    }

    public static DetectionResult deadlockDetected(List<Thread> cycle) {
        String desc = "Deadlock detected! Cycle: " +
                cycle.stream().map(Thread::getName).toList();
        return new DetectionResult(true, cycle, desc);
    }

    public static DetectionResult noDeadlock() {
        return new DetectionResult(false, Collections.emptyList(), "No deadlock detected");
    }

    public static DetectionResult timeout() {
        return new DetectionResult(false, Collections.emptyList(), "Detection timeout");
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public List<Thread> getInvolvedThreads() {
        return involvedThreads;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("DetectionResult{deadlock=%s, threads=%d, desc='%s'}",
                deadlockDetected, involvedThreads.size(), description);
    }
}
```

### `src/main/java/com/experiment/deadlocklab/detection/monitor/ThreadStateMonitor.java`

```java
package com.experiment.deadlocklab.detection.monitor;

import com.experiment.deadlocklab.resource.domain.LockableResource;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

/**
 * 스레드 상태 모니터
 * - JMX ThreadMXBean 활용
 * - 스레드 대기 관계 추적
 */
public class ThreadStateMonitor {

    private final ThreadMXBean threadMXBean;
    private final Map<Thread, LockableResource> threadWaiting = new HashMap<>();
    private final Map<Thread, LockableResource> threadHolding = new HashMap<>();

    public ThreadStateMonitor() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    /**
     * 스레드가 자원 대기 시작
     */
    public void recordWaiting(Thread thread, LockableResource resource) {
        threadWaiting.put(thread, resource);
    }

    /**
     * 스레드가 자원 획득
     */
    public void recordAcquired(Thread thread, LockableResource resource) {
        threadWaiting.remove(thread);
        threadHolding.put(thread, resource);
    }

    /**
     * 스레드가 자원 해제
     */
    public void recordReleased(Thread thread, LockableResource resource) {
        threadHolding.remove(thread);
    }

    /**
     * 스레드 대기 관계 반환
     * key: 대기 중인 스레드, value: 자원을 보유한 스레드
     */
    public Map<Thread, Thread> getWaitingRelations() {
        Map<Thread, Thread> relations = new HashMap<>();

        threadWaiting.forEach((waiter, resource) -> {
            Thread holder = resource.getHolder();
            if (holder != null && holder != waiter) {
                relations.put(waiter, holder);
            }
        });

        return relations;
    }

    /**
     * JMX를 통한 데드락 탐지
     */
    public long[] findDeadlockedThreadsViaJMX() {
        return threadMXBean.findDeadlockedThreads();
    }

    /**
     * 스레드 정보 조회
     */
    public ThreadInfo getThreadInfo(long threadId) {
        return threadMXBean.getThreadInfo(threadId);
    }

    /**
     * 모든 스레드 정보 조회
     */
    public ThreadInfo[] getAllThreadInfo() {
        long[] threadIds = threadMXBean.getAllThreadIds();
        return threadMXBean.getThreadInfo(threadIds);
    }

    /**
     * 특정 스레드가 대기 중인 자원
     */
    public LockableResource getWaitingResource(Thread thread) {
        return threadWaiting.get(thread);
    }

    /**
     * 특정 스레드가 보유 중인 자원
     */
    public LockableResource getHoldingResource(Thread thread) {
        return threadHolding.get(thread);
    }

    /**
     * 상태 초기화
     */
    public void clear() {
        threadWaiting.clear();
        threadHolding.clear();
    }

    /**
     * 현재 상태 출력
     */
    public void printStatus() {
        System.out.println("\n[Thread Monitor Status]");
        System.out.println("  Waiting:");
        threadWaiting.forEach((t, r) ->
                System.out.printf("    %s -> %s%n", t.getName(), r.getName()));
        System.out.println("  Holding:");
        threadHolding.forEach((t, r) ->
                System.out.printf("    %s : %s%n", t.getName(), r.getName()));
    }
}
```

### `src/main/java/com/experiment/deadlocklab/detection/application/DeadlockDetector.java`

```java
package com.experiment.deadlocklab.detection.application;

import com.experiment.deadlocklab.detection.domain.DetectionResult;
import com.experiment.deadlocklab.detection.domain.WaitForGraph;
import com.experiment.deadlocklab.detection.monitor.ThreadStateMonitor;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 데드락 탐지기
 * - Wait-For Graph 기반 사이클 탐지
 * - 비동기 모니터링 지원
 */
public class DeadlockDetector {

    private static final long DEFAULT_CHECK_INTERVAL_MS = 100;
    private static final long DEFAULT_TIMEOUT_MS = 10000;

    private final WaitForGraph waitForGraph;
    private final ThreadStateMonitor threadMonitor;
    private final ConsoleLogger logger;

    private volatile boolean monitoring = false;
    private ExecutorService monitorExecutor;

    public DeadlockDetector() {
        this.waitForGraph = new WaitForGraph();
        this.threadMonitor = new ThreadStateMonitor();
        this.logger = ConsoleLogger.of("Detector");
    }

    public DeadlockDetector(WaitForGraph waitForGraph, ThreadStateMonitor threadMonitor) {
        this.waitForGraph = waitForGraph;
        this.threadMonitor = threadMonitor;
        this.logger = ConsoleLogger.of("Detector");
    }

    /**
     * 동기식 데드락 체크
     */
    public DetectionResult checkNow() {
        // 스레드 대기 관계 수집
        Map<Thread, Thread> waitingRelations = threadMonitor.getWaitingRelations();

        // 그래프 업데이트
        waitForGraph.update(waitingRelations);

        // 사이클 탐지
        if (waitForGraph.hasCycle()) {
            List<Thread> cycle = waitForGraph.findCycle();
            logger.deadlock("DEADLOCK DETECTED! Cycle: " + cycle);
            return DetectionResult.deadlockDetected(cycle);
        }

        return DetectionResult.noDeadlock();
    }

    /**
     * 비동기 모니터링 시작
     */
    public CompletableFuture<DetectionResult> startMonitoring() {
        return startMonitoring(DEFAULT_TIMEOUT_MS);
    }

    public CompletableFuture<DetectionResult> startMonitoring(long timeoutMs) {
        monitoring = true;
        monitorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DeadlockMonitor");
            t.setDaemon(true);
            return t;
        });

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            while (monitoring && (System.currentTimeMillis() - startTime) < timeoutMs) {
                try {
                    DetectionResult result = checkNow();
                    if (result.isDeadlockDetected()) {
                        return result;
                    }
                    Thread.sleep(DEFAULT_CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            return DetectionResult.timeout();
        }, monitorExecutor);
    }

    /**
     * 모니터링 중지
     */
    public void stopMonitoring() {
        monitoring = false;
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
        }
    }

    /**
     * Wait-For Graph에 간선 추가
     */
    public void addWaitEdge(Thread waiter, Thread holder) {
        waitForGraph.addEdge(waiter, holder);
    }

    /**
     * Wait-For Graph에서 간선 제거
     */
    public void removeWaitEdge(Thread waiter, Thread holder) {
        waitForGraph.removeEdge(waiter, holder);
    }

    /**
     * 스레드가 자원 대기 시작 기록
     */
    public void recordWaiting(Thread thread, com.experiment.deadlocklab.resource.domain.LockableResource resource) {
        threadMonitor.recordWaiting(thread, resource);

        // 자원을 보유한 스레드가 있으면 그래프에 간선 추가
        Thread holder = resource.getHolder();
        if (holder != null && holder != thread) {
            waitForGraph.addEdge(thread, holder);
        }
    }

    /**
     * 스레드가 자원 획득 기록
     */
    public void recordAcquired(Thread thread, com.experiment.deadlocklab.resource.domain.LockableResource resource) {
        threadMonitor.recordAcquired(thread, resource);
        // 대기 간선 제거
        waitForGraph.removeThread(thread);
    }

    /**
     * 스레드가 자원 해제 기록
     */
    public void recordReleased(Thread thread, com.experiment.deadlocklab.resource.domain.LockableResource resource) {
        threadMonitor.recordReleased(thread, resource);
    }

    /**
     * 현재 Wait-For Graph 반환
     */
    public WaitForGraph getWaitForGraph() {
        return waitForGraph;
    }

    /**
     * 스레드 모니터 반환
     */
    public ThreadStateMonitor getThreadMonitor() {
        return threadMonitor;
    }

    /**
     * 상태 초기화
     */
    public void clear() {
        waitForGraph.clear();
        threadMonitor.clear();
    }
}
```

---

## Phase 4: 데드락 해결 전략

Lock Ordering, TryLock + Timeout, Banker's Algorithm 등 데드락 해결 전략을 구현합니다.

### `src/main/java/com/experiment/deadlocklab/solution/domain/SolutionType.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/solution/strategies/DeadlockSolution.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/solution/strategies/LockOrderingSolution.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/solution/strategies/TryLockSolution.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/solution/strategies/BankerAlgorithm.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/solution/application/SolutionApplier.java`

```java
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
```

---

## Phase 5: 데드락 시나리오 4종

Classic AB-BA, Database Row Lock, Nested Lock, Dining Philosophers 등 4가지 데드락 시나리오를 구현합니다.

### `src/main/java/com/experiment/deadlocklab/scenario/domain/ScenarioResult.java`

```java
package com.experiment.deadlocklab.scenario.domain;

import com.experiment.deadlocklab.detection.domain.DetectionResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 시나리오 실행 결과
 */
public class ScenarioResult {

    private final String scenarioName;
    private final boolean deadlockOccurred;
    private final SolutionType solutionApplied;
    private final Duration executionTime;
    private final LocalDateTime timestamp;
    private final DetectionResult detectionResult;
    private final String description;

    private ScenarioResult(String scenarioName, boolean deadlockOccurred,
                           SolutionType solutionApplied, Duration executionTime,
                           DetectionResult detectionResult, String description) {
        this.scenarioName = scenarioName;
        this.deadlockOccurred = deadlockOccurred;
        this.solutionApplied = solutionApplied;
        this.executionTime = executionTime;
        this.timestamp = LocalDateTime.now();
        this.detectionResult = detectionResult;
        this.description = description;
    }

    public static ScenarioResult deadlock(String scenarioName, Duration executionTime,
                                           DetectionResult detectionResult) {
        return new ScenarioResult(scenarioName, true, SolutionType.NONE,
                executionTime, detectionResult, "Deadlock occurred");
    }

    public static ScenarioResult success(String scenarioName, SolutionType solution,
                                          Duration executionTime) {
        return new ScenarioResult(scenarioName, false, solution,
                executionTime, DetectionResult.noDeadlock(), "Completed successfully");
    }

    public static ScenarioResult timeout(String scenarioName, Duration executionTime) {
        return new ScenarioResult(scenarioName, true, SolutionType.NONE,
                executionTime, DetectionResult.timeout(), "Execution timeout (likely deadlock)");
    }

    // Getters
    public String getScenarioName() {
        return scenarioName;
    }

    public boolean isDeadlockOccurred() {
        return deadlockOccurred;
    }

    public SolutionType getSolutionApplied() {
        return solutionApplied;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public DetectionResult getDetectionResult() {
        return detectionResult;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("ScenarioResult{scenario=%s, deadlock=%s, solution=%s, time=%dms}",
                scenarioName, deadlockOccurred, solutionApplied, executionTime.toMillis());
    }
}
```

### `src/main/java/com/experiment/deadlocklab/scenario/scenarios/DeadlockScenario.java`

```java
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
```

### `src/main/java/com/experiment/deadlocklab/scenario/scenarios/ClassicDeadlockScenario.java`

```java
package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.detection.domain.DetectionResult;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;
import com.experiment.deadlocklab.solution.strategies.LockOrderingSolution;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 고전적 AB-BA 데드락 시나리오
 *
 * Thread 1: lock(A) -> lock(B)
 * Thread 2: lock(B) -> lock(A)
 * -> DEADLOCK!
 */
public class ClassicDeadlockScenario implements DeadlockScenario {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public ClassicDeadlockScenario(ResourceManager resourceManager,
                                    DeadlockDetector detector,
                                    ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("Classic");
    }

    @Override
    public String getName() {
        return "Classic AB-BA Deadlock";
    }

    @Override
    public String getDescription() {
        return "Two threads acquire locks in opposite order";
    }

    @Override
    public int getRequiredResourceCount() {
        return 2;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.LOCK_ORDERING;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");

        // 자원 생성
        LockableResource lockA = resourceManager.createResource("A", "Lock-A");
        LockableResource lockB = resourceManager.createResource("B", "Lock-B");

        logger.info("Resources created: Lock-A, Lock-B");
        logger.info("Thread-1 will: lock(A) -> lock(B)");
        logger.info("Thread-2 will: lock(B) -> lock(A)");
        logger.warn("Expected: DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // 데드락 감지 시작
        CompletableFuture<DetectionResult> detectionFuture = detector.startMonitoring(5000);

        // Thread 1: A -> B
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();

                logger.info("Acquiring Lock-A...");
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.WAITING, null, "A");
                lockA.lock();
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.RUNNING, "A", null);
                logger.success("Lock-A acquired ✓");

                Thread.sleep(100);  // 데드락 유발을 위한 지연

                logger.info("Acquiring Lock-B...");
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.BLOCKED, "A", "B");
                detector.recordWaiting(Thread.currentThread(), lockB);
                lockB.lock();  // 여기서 블로킹
                detector.recordAcquired(Thread.currentThread(), lockB);
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.RUNNING, "A,B", null);
                logger.success("Lock-B acquired ✓");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                visualizer.updateStatus("Thread-1", ThreadVisualizer.State.TERMINATED, null, null);
                endLatch.countDown();
            }
        });

        // Thread 2: B -> A
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();

                logger.info("Acquiring Lock-B...");
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.WAITING, null, "B");
                lockB.lock();
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.RUNNING, "B", null);
                logger.success("Lock-B acquired ✓");

                Thread.sleep(100);  // 데드락 유발을 위한 지연

                logger.info("Acquiring Lock-A...");
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.BLOCKED, "B", "A");
                detector.recordWaiting(Thread.currentThread(), lockA);
                lockA.lock();  // 여기서 블로킹
                detector.recordAcquired(Thread.currentThread(), lockA);
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.RUNNING, "A,B", null);
                logger.success("Lock-A acquired ✓");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                visualizer.updateStatus("Thread-2", ThreadVisualizer.State.TERMINATED, null, null);
                endLatch.countDown();
            }
        });

        // 시작!
        startLatch.countDown();

        // 데드락 감지 대기
        DetectionResult detectionResult;
        try {
            detectionResult = detectionFuture.get(6, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            detectionResult = DetectionResult.timeout();
        } catch (ExecutionException e) {
            detectionResult = DetectionResult.noDeadlock();
        }

        // 결과 출력
        long elapsed = System.currentTimeMillis() - startTime;

        if (detectionResult.isDeadlockDetected()) {
            visualizer.printDeadlockVisualization(detectionResult.getInvolvedThreads());
            logger.deadlock("DEADLOCK CONFIRMED!");
        }

        // 정리
        detector.stopMonitoring();
        executor.shutdownNow();
        resourceManager.clear();

        return ScenarioResult.deadlock(getName(), Duration.ofMillis(elapsed), detectionResult);
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (Lock Ordering)");

        // 자원 생성
        LockableResource lockA = resourceManager.createResource("A", "Lock-A");
        LockableResource lockB = resourceManager.createResource("B", "Lock-B");

        LockOrderingSolution solution = new LockOrderingSolution();

        logger.info("Resources created: Lock-A, Lock-B");
        logger.info("Solution: Lock Ordering (always acquire in ID order: A -> B)");
        logger.info("Thread-1 will: lock(A) -> lock(B)");
        logger.info("Thread-2 will: lock(A) -> lock(B)  <-- SAME ORDER!");
        logger.success("Expected: NO DEADLOCK");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Thread 1: A -> B (ordered)
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();

                logger.info("Acquiring locks in order (A -> B)...");
                solution.lockInOrder(lockA, lockB);
                logger.success("Both locks acquired ✓");

                Thread.sleep(200);  // 작업 수행
                logger.info("Work done");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                logger.info("Locks released");
                endLatch.countDown();
            }
        });

        // Thread 2: A -> B (ordered) - 같은 순서!
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();

                logger.info("Acquiring locks in order (A -> B)...");
                solution.lockInOrder(lockA, lockB);
                logger.success("Both locks acquired ✓");

                Thread.sleep(200);  // 작업 수행
                logger.info("Work done");

            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                if (lockB.isHeldByCurrentThread()) lockB.unlock();
                if (lockA.isHeldByCurrentThread()) lockA.unlock();
                logger.info("Locks released");
                endLatch.countDown();
            }
        });

        // 시작!
        startLatch.countDown();

        // 완료 대기
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        resourceManager.clear();

        if (completed) {
            logger.success("All threads completed without deadlock!");
            return ScenarioResult.success(getName(), SolutionType.LOCK_ORDERING, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           CLASSIC AB-BA DEADLOCK SCENARIO                     ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}
```

### `src/main/java/com/experiment/deadlocklab/scenario/scenarios/DatabaseDeadlockScenario.java`

```java
package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;
import com.experiment.deadlocklab.solution.strategies.TryLockSolution;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * 데이터베이스 행 락 데드락 시나리오
 *
 * Transaction 1: UPDATE row1, UPDATE row2
 * Transaction 2: UPDATE row2, UPDATE row1
 * -> DEADLOCK!
 */
public class DatabaseDeadlockScenario implements DeadlockScenario {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public DatabaseDeadlockScenario(ResourceManager resourceManager,
                                     DeadlockDetector detector,
                                     ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("Database");
    }

    @Override
    public String getName() {
        return "Database Row Lock Deadlock";
    }

    @Override
    public String getDescription() {
        return "Two transactions update rows in opposite order";
    }

    @Override
    public int getRequiredResourceCount() {
        return 2;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.TRY_LOCK_TIMEOUT;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");

        // 행 락 시뮬레이션
        LockableResource row1001 = resourceManager.createResource("1001", "Row-1001");
        LockableResource row1002 = resourceManager.createResource("1002", "Row-1002");

        logger.info("Database rows: accounts.1001, accounts.1002");
        logger.info("TX-1: UPDATE 1001, then UPDATE 1002");
        logger.info("TX-2: UPDATE 1002, then UPDATE 1001");
        logger.warn("Expected: DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Transaction 1
        executor.submit(() -> {
            Thread.currentThread().setName("TX-1");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");
                logger.info("UPDATE accounts SET balance = balance - 100 WHERE id = 1001");
                row1001.lock();
                logger.success("Row 1001 locked ✓");

                Thread.sleep(100);

                logger.info("UPDATE accounts SET balance = balance + 100 WHERE id = 1002");
                detector.recordWaiting(Thread.currentThread(), row1002);
                row1002.lock();  // 블로킹
                logger.success("Row 1002 locked ✓");

                logger.info("COMMIT");

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                if (row1002.isHeldByCurrentThread()) row1002.unlock();
                if (row1001.isHeldByCurrentThread()) row1001.unlock();
                endLatch.countDown();
            }
        });

        // Transaction 2
        executor.submit(() -> {
            Thread.currentThread().setName("TX-2");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");
                logger.info("UPDATE accounts SET balance = balance - 200 WHERE id = 1002");
                row1002.lock();
                logger.success("Row 1002 locked ✓");

                Thread.sleep(100);

                logger.info("UPDATE accounts SET balance = balance + 200 WHERE id = 1001");
                detector.recordWaiting(Thread.currentThread(), row1001);
                row1001.lock();  // 블로킹
                logger.success("Row 1001 locked ✓");

                logger.info("COMMIT");

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                if (row1001.isHeldByCurrentThread()) row1001.unlock();
                if (row1002.isHeldByCurrentThread()) row1002.unlock();
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        // 데드락 대기
        boolean completed = endLatch.await(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            logger.deadlock("DEADLOCK DETECTED - Transactions stuck!");
            visualizer.printWaitForGraph(detector.getWaitForGraph());
        }

        executor.shutdownNow();
        resourceManager.clear();

        return completed
                ? ScenarioResult.success(getName(), SolutionType.NONE, Duration.ofMillis(elapsed))
                : ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (TryLock + Timeout)");

        LockableResource row1001 = resourceManager.createResource("1001", "Row-1001");
        LockableResource row1002 = resourceManager.createResource("1002", "Row-1002");

        TryLockSolution solution = new TryLockSolution(500, 3);

        logger.info("Database rows: accounts.1001, accounts.1002");
        logger.info("Solution: TryLock with 500ms timeout, 3 retries");
        logger.success("Expected: NO DEADLOCK (timeout & retry)");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Transaction 1
        executor.submit(() -> {
            Thread.currentThread().setName("TX-1");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");

                if (solution.acquireLocks(List.of(row1001, row1002))) {
                    try {
                        logger.success("All rows locked ✓");
                        Thread.sleep(100);
                        logger.info("COMMIT");
                    } finally {
                        solution.releaseLocks(List.of(row1001, row1002));
                    }
                } else {
                    logger.error("Failed to acquire locks - ROLLBACK");
                }

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                endLatch.countDown();
            }
        });

        // Transaction 2
        executor.submit(() -> {
            Thread.currentThread().setName("TX-2");
            try {
                startLatch.await();

                logger.info("BEGIN TRANSACTION");

                if (solution.acquireLocks(List.of(row1002, row1001))) {
                    try {
                        logger.success("All rows locked ✓");
                        Thread.sleep(100);
                        logger.info("COMMIT");
                    } finally {
                        solution.releaseLocks(List.of(row1002, row1001));
                    }
                } else {
                    logger.error("Failed to acquire locks - ROLLBACK");
                }

            } catch (InterruptedException e) {
                logger.warn("ROLLBACK (interrupted)");
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        resourceManager.clear();

        if (completed) {
            logger.success("All transactions completed!");
            return ScenarioResult.success(getName(), SolutionType.TRY_LOCK_TIMEOUT, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           DATABASE ROW LOCK DEADLOCK SCENARIO                 ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}
```

### `src/main/java/com/experiment/deadlocklab/scenario/scenarios/NestedLockScenario.java`

```java
package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 중첩 synchronized 데드락 시나리오
 *
 * ServiceA.methodA() -> synchronized(this) -> serviceB.methodB()
 * ServiceB.methodB() -> synchronized(this) -> serviceA.callback()
 * -> DEADLOCK!
 */
public class NestedLockScenario implements DeadlockScenario {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public NestedLockScenario(ResourceManager resourceManager,
                               DeadlockDetector detector,
                               ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("NestedLock");
    }

    @Override
    public String getName() {
        return "Nested Lock Deadlock";
    }

    @Override
    public String getDescription() {
        return "Synchronized methods calling each other";
    }

    @Override
    public int getRequiredResourceCount() {
        return 2;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.LOCK_ORDERING;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");

        // 문제가 되는 서비스들
        UnsafeServiceA serviceA = new UnsafeServiceA();
        UnsafeServiceB serviceB = new UnsafeServiceB();
        serviceA.setServiceB(serviceB);
        serviceB.setServiceA(serviceA);

        logger.info("UnsafeServiceA.methodA(): synchronized(this) { serviceB.methodB() }");
        logger.info("UnsafeServiceB.methodB(): synchronized(this) { serviceA.callback() }");
        logger.warn("Expected: DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Thread 1: serviceA.methodA() 호출
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();
                logger.info("Calling serviceA.methodA()");
                serviceA.methodA();
                logger.success("methodA completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: serviceB.methodB() 호출
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();
                logger.info("Calling serviceB.methodB()");
                serviceB.methodB();
                logger.success("methodB completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        boolean completed = endLatch.await(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            logger.deadlock("DEADLOCK! Nested synchronized calls blocked!");
            printDeadlockDiagram();
        }

        executor.shutdownNow();

        return completed
                ? ScenarioResult.success(getName(), SolutionType.NONE, Duration.ofMillis(elapsed))
                : ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (Lock Scope Minimization)");

        // 안전한 서비스들
        SafeServiceA serviceA = new SafeServiceA();
        SafeServiceB serviceB = new SafeServiceB();
        serviceA.setServiceB(serviceB);
        serviceB.setServiceA(serviceA);

        logger.info("SafeServiceA.methodA(): { synchronized(this) { prepare } ; serviceB.methodB() }");
        logger.info("SafeServiceB.methodB(): { synchronized(this) { prepare } ; serviceA.callback() }");
        logger.info("Solution: Release lock BEFORE calling external method");
        logger.success("Expected: NO DEADLOCK");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        long startTime = System.currentTimeMillis();

        // Thread 1
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-1");
            try {
                startLatch.await();
                logger.info("Calling serviceA.methodA()");
                serviceA.methodA();
                logger.success("methodA completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2
        executor.submit(() -> {
            Thread.currentThread().setName("Thread-2");
            try {
                startLatch.await();
                logger.info("Calling serviceB.methodB()");
                serviceB.methodB();
                logger.success("methodB completed");
            } catch (InterruptedException e) {
                logger.warn("Interrupted");
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();

        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();

        if (completed) {
            logger.success("All calls completed! No deadlock!");
            return ScenarioResult.success(getName(), SolutionType.LOCK_ORDERING, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           NESTED LOCK DEADLOCK SCENARIO                       ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printDeadlockDiagram() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────┐");
        System.out.println("  │  Thread-1                Thread-2          │");
        System.out.println("  │     │                       │              │");
        System.out.println("  │     ▼                       ▼              │");
        System.out.println("  │  synchronized(A)       synchronized(B)    │");
        System.out.println("  │     │                       │              │");
        System.out.println("  │     ▼                       ▼              │");
        System.out.println("  │  serviceB.methodB()    serviceA.callback() │");
        System.out.println("  │     │                       │              │");
        System.out.println("  │     ▼                       ▼              │");
        System.out.println("  │  WAITING for B ◄──────► WAITING for A     │");
        System.out.println("  │                 DEADLOCK!                  │");
        System.out.println("  └─────────────────────────────────────────────┘");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }

    // ========== 문제가 되는 서비스들 ==========

    class UnsafeServiceA {
        private UnsafeServiceB serviceB;

        void setServiceB(UnsafeServiceB serviceB) {
            this.serviceB = serviceB;
        }

        synchronized void methodA() throws InterruptedException {
            logger.debug("ServiceA.methodA: acquired lock on A");
            Thread.sleep(100);
            logger.debug("ServiceA.methodA: calling serviceB.methodB()");
            serviceB.methodB();  // B의 락 필요 -> DEADLOCK 가능
        }

        synchronized void callback() {
            logger.debug("ServiceA.callback: executed");
        }
    }

    class UnsafeServiceB {
        private UnsafeServiceA serviceA;

        void setServiceA(UnsafeServiceA serviceA) {
            this.serviceA = serviceA;
        }

        synchronized void methodB() throws InterruptedException {
            logger.debug("ServiceB.methodB: acquired lock on B");
            Thread.sleep(100);
            logger.debug("ServiceB.methodB: calling serviceA.callback()");
            serviceA.callback();  // A의 락 필요 -> DEADLOCK 가능
        }
    }

    // ========== 안전한 서비스들 ==========

    class SafeServiceA {
        private SafeServiceB serviceB;

        void setServiceB(SafeServiceB serviceB) {
            this.serviceB = serviceB;
        }

        void methodA() throws InterruptedException {
            Object data;
            synchronized (this) {
                logger.debug("SafeServiceA.methodA: short lock, preparing data");
                Thread.sleep(50);
                data = "prepared";
            }
            // 락 해제 후 외부 호출
            logger.debug("SafeServiceA.methodA: lock released, calling serviceB");
            serviceB.methodB();
        }

        void callback() {
            synchronized (this) {
                logger.debug("SafeServiceA.callback: executed");
            }
        }
    }

    class SafeServiceB {
        private SafeServiceA serviceA;

        void setServiceA(SafeServiceA serviceA) {
            this.serviceA = serviceA;
        }

        void methodB() throws InterruptedException {
            Object data;
            synchronized (this) {
                logger.debug("SafeServiceB.methodB: short lock, preparing data");
                Thread.sleep(50);
                data = "prepared";
            }
            // 락 해제 후 외부 호출
            logger.debug("SafeServiceB.methodB: lock released, calling serviceA");
            serviceA.callback();
        }
    }
}
```

### `src/main/java/com/experiment/deadlocklab/scenario/scenarios/PhilosopherScenario.java`

```java
package com.experiment.deadlocklab.scenario.scenarios;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.solution.domain.SolutionType;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 식사하는 철학자 문제 (Dining Philosophers)
 *
 * 5명의 철학자, 5개의 포크
 * 각 철학자는 양쪽 포크가 필요
 * 모두 왼쪽 포크를 먼저 집으면 -> DEADLOCK!
 */
public class PhilosopherScenario implements DeadlockScenario {

    private static final int NUM_PHILOSOPHERS = 5;
    private static final String[] NAMES = {"Plato", "Aristotle", "Socrates", "Descartes", "Kant"};

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final ThreadVisualizer visualizer;
    private final ConsoleLogger logger;

    public PhilosopherScenario(ResourceManager resourceManager,
                                DeadlockDetector detector,
                                ThreadVisualizer visualizer) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.visualizer = visualizer;
        this.logger = ConsoleLogger.of("Philosopher");
    }

    @Override
    public String getName() {
        return "Dining Philosophers";
    }

    @Override
    public String getDescription() {
        return "Five philosophers, five forks, circular deadlock";
    }

    @Override
    public int getRequiredResourceCount() {
        return NUM_PHILOSOPHERS;
    }

    @Override
    public SolutionType getRecommendedSolution() {
        return SolutionType.LOCK_ORDERING;
    }

    @Override
    public ScenarioResult runWithoutSolution() throws InterruptedException {
        printHeader("WITHOUT Solution (Deadlock Expected)");
        printTable();

        // 포크 생성
        LockableResource[] forks = new LockableResource[NUM_PHILOSOPHERS];
        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            forks[i] = resourceManager.createResource("Fork-" + i, "Fork-" + i);
        }

        logger.info("Each philosopher picks up LEFT fork first, then RIGHT");
        logger.warn("Expected: All pick left fork -> nobody can pick right -> DEADLOCK!");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_PHILOSOPHERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(NUM_PHILOSOPHERS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            final int id = i;
            final int left = i;
            final int right = (i + 1) % NUM_PHILOSOPHERS;

            executor.submit(() -> {
                Thread.currentThread().setName(NAMES[id]);
                try {
                    startLatch.await();

                    logger.info("Thinking...");
                    Thread.sleep(50);

                    // 왼쪽 포크 집기
                    logger.info("Picking up left fork (Fork-" + left + ")");
                    forks[left].lock();
                    logger.success("Got left fork ✓");

                    Thread.sleep(100);  // 데드락 유발

                    // 오른쪽 포크 집기
                    logger.info("Picking up right fork (Fork-" + right + ")");
                    detector.recordWaiting(Thread.currentThread(), forks[right]);
                    forks[right].lock();  // 블로킹!
                    logger.success("Got right fork ✓");

                    // 식사
                    logger.info("Eating... 🍝");
                    Thread.sleep(100);

                    // 포크 내려놓기
                    forks[right].unlock();
                    forks[left].unlock();
                    logger.info("Finished eating, forks released");

                } catch (InterruptedException e) {
                    logger.warn("Interrupted");
                } finally {
                    if (forks[right].isHeldByCurrentThread()) forks[right].unlock();
                    if (forks[left].isHeldByCurrentThread()) forks[left].unlock();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = endLatch.await(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            logger.deadlock("DEADLOCK! All philosophers holding left fork, waiting for right!");
            printDeadlockState();
        }

        executor.shutdownNow();
        resourceManager.clear();

        return completed
                ? ScenarioResult.success(getName(), SolutionType.NONE, Duration.ofMillis(elapsed))
                : ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
    }

    @Override
    public ScenarioResult runWithSolution() throws InterruptedException {
        printHeader("WITH Solution (Resource Ordering)");
        printTable();

        // 포크 생성
        LockableResource[] forks = new LockableResource[NUM_PHILOSOPHERS];
        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            forks[i] = resourceManager.createResource("Fork-" + i, "Fork-" + i);
        }

        logger.info("Solution: Always pick up LOWER numbered fork first");
        logger.info("  Philosopher 0: Fork-0 -> Fork-1");
        logger.info("  Philosopher 1: Fork-1 -> Fork-2");
        logger.info("  ...");
        logger.info("  Philosopher 4: Fork-0 -> Fork-4  <-- Order changed!");
        logger.success("Expected: NO DEADLOCK");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_PHILOSOPHERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(NUM_PHILOSOPHERS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            final int id = i;
            final int left = i;
            final int right = (i + 1) % NUM_PHILOSOPHERS;

            // 자원 순서화: 낮은 번호부터 획득
            final int first = Math.min(left, right);
            final int second = Math.max(left, right);

            executor.submit(() -> {
                Thread.currentThread().setName(NAMES[id]);
                try {
                    startLatch.await();

                    logger.info("Thinking...");
                    Thread.sleep(50 + (int)(Math.random() * 50));

                    // 순서대로 포크 집기
                    logger.info("Picking up Fork-" + first + " (ordered)");
                    forks[first].lock();
                    logger.success("Got Fork-" + first + " ✓");

                    logger.info("Picking up Fork-" + second + " (ordered)");
                    forks[second].lock();
                    logger.success("Got Fork-" + second + " ✓");

                    // 식사
                    logger.info("Eating... 🍝");
                    Thread.sleep(100);

                    // 포크 내려놓기 (역순)
                    forks[second].unlock();
                    forks[first].unlock();
                    logger.info("Finished eating, forks released");

                } catch (InterruptedException e) {
                    logger.warn("Interrupted");
                } finally {
                    if (forks[second].isHeldByCurrentThread()) forks[second].unlock();
                    if (forks[first].isHeldByCurrentThread()) forks[first].unlock();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        resourceManager.clear();

        if (completed) {
            logger.success("All philosophers finished eating! No deadlock!");
            return ScenarioResult.success(getName(), SolutionType.LOCK_ORDERING, Duration.ofMillis(elapsed));
        } else {
            return ScenarioResult.timeout(getName(), Duration.ofMillis(elapsed));
        }
    }

    private void printHeader(String mode) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           DINING PHILOSOPHERS SCENARIO                        ║");
        System.out.println("║                                                               ║");
        System.out.println("║   " + padRight(mode, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printTable() {
        System.out.println("              [Plato]");
        System.out.println("            🍴      🍴");
        System.out.println("       [Kant]  🍝  [Aristotle]");
        System.out.println("            🍴      🍴");
        System.out.println("     [Descartes]🍴[Socrates]");
        System.out.println();
    }

    private void printDeadlockState() {
        System.out.println();
        System.out.println("  ╔═══════════════════════════════════╗");
        System.out.println("  ║       CIRCULAR WAIT DETECTED      ║");
        System.out.println("  ╠═══════════════════════════════════╣");
        System.out.println("  ║  Plato     : Fork-0 ✓ → Fork-1 ⏳ ║");
        System.out.println("  ║  Aristotle : Fork-1 ✓ → Fork-2 ⏳ ║");
        System.out.println("  ║  Socrates  : Fork-2 ✓ → Fork-3 ⏳ ║");
        System.out.println("  ║  Descartes : Fork-3 ✓ → Fork-4 ⏳ ║");
        System.out.println("  ║  Kant      : Fork-4 ✓ → Fork-0 ⏳ ║");
        System.out.println("  ╚═══════════════════════════════════╝");
        System.out.println();
    }

    private String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}
```

---

## Phase 6: ScenarioRunner + 애플리케이션 통합

시나리오 실행기를 만들고 메인 애플리케이션에 CommandLineRunner를 통합합니다.

### `src/main/java/com/experiment/deadlocklab/scenario/application/ScenarioRunner.java`

```java
package com.experiment.deadlocklab.scenario.application;

import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.scenario.scenarios.*;
import com.experiment.deadlocklab.solution.application.SolutionApplier;

import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오 실행기
 */
public class ScenarioRunner {

    private final ResourceManager resourceManager;
    private final DeadlockDetector detector;
    private final SolutionApplier solutionApplier;
    private final ThreadVisualizer visualizer;
    private final MetricsCollector metrics;
    private final ConsoleLogger logger;

    public ScenarioRunner(ResourceManager resourceManager,
                          DeadlockDetector detector,
                          SolutionApplier solutionApplier,
                          ThreadVisualizer visualizer,
                          MetricsCollector metrics) {
        this.resourceManager = resourceManager;
        this.detector = detector;
        this.solutionApplier = solutionApplier;
        this.visualizer = visualizer;
        this.metrics = metrics;
        this.logger = ConsoleLogger.of("Runner");
    }

    /**
     * 단일 시나리오 실행
     */
    public List<ScenarioResult> run(DeadlockScenario scenario, boolean withSolution)
            throws InterruptedException {

        List<ScenarioResult> results = new ArrayList<>();

        if (withSolution) {
            results.add(scenario.runWithSolution());
        } else {
            results.add(scenario.runWithoutSolution());
        }

        return results;
    }

    /**
     * 시나리오 비교 실행 (미적용 vs 적용)
     */
    public List<ScenarioResult> runComparison(DeadlockScenario scenario)
            throws InterruptedException {

        List<ScenarioResult> results = new ArrayList<>();

        // 1. 해결책 미적용
        logger.info("Running WITHOUT solution...");
        results.add(scenario.runWithoutSolution());

        Thread.sleep(1000);

        // 2. 해결책 적용
        logger.info("Running WITH solution...");
        results.add(scenario.runWithSolution());

        return results;
    }

    /**
     * 모든 시나리오 실행
     */
    public List<ScenarioResult> runAll() throws InterruptedException {
        List<ScenarioResult> results = new ArrayList<>();

        // 시나리오 목록
        List<DeadlockScenario> scenarios = List.of(
                new ClassicDeadlockScenario(resourceManager, detector, visualizer),
                new DatabaseDeadlockScenario(resourceManager, detector, visualizer),
                new PhilosopherScenario(resourceManager, detector, visualizer),
                new NestedLockScenario(resourceManager, detector, visualizer)
        );

        for (DeadlockScenario scenario : scenarios) {
            logger.info("===== " + scenario.getName() + " =====");

            // 미적용
            results.add(scenario.runWithoutSolution());
            Thread.sleep(500);

            // 적용
            results.add(scenario.runWithSolution());
            Thread.sleep(1000);

            // 초기화
            resourceManager.clear();
            detector.clear();
        }

        return results;
    }

    /**
     * 결과 요약 출력
     */
    public void printSummary(List<ScenarioResult> results) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SCENARIO RESULTS SUMMARY                   ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");

        for (ScenarioResult result : results) {
            String status = result.isDeadlockOccurred() ? "❌ DEADLOCK" : "✅ SUCCESS";
            String solution = result.getSolutionApplied().getName();
            String time = result.getExecutionTime().toMillis() + "ms";

            System.out.printf("║  %-25s | %-12s | %-15s | %6s ║%n",
                    truncate(result.getScenarioName(), 25),
                    status,
                    truncate(solution, 15),
                    time);
        }

        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String truncate(String s, int length) {
        if (s.length() <= length) return s;
        return s.substring(0, length - 2) + "..";
    }
}
```

### `src/main/java/com/experiment/deadlocklab/DeadlockLabApplication.java` (전체 버전)

Phase 0에서 만든 스텁을 아래 전체 버전으로 교체합니다.

```java
package com.experiment.deadlocklab;

import com.experiment.deadlocklab.benchmark.DeadlockBenchmark;
import com.experiment.deadlocklab.detection.application.DeadlockDetector;
import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.scenario.application.ScenarioRunner;
import com.experiment.deadlocklab.scenario.domain.ScenarioResult;
import com.experiment.deadlocklab.scenario.scenarios.*;
import com.experiment.deadlocklab.solution.application.SolutionApplier;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Deadlock Lab - 메인 애플리케이션
 *
 * 데드락을 직접 재현하고 해결하며 배우는 프로젝트
 */
@SpringBootApplication
public class DeadlockLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeadlockLabApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner() {
        return args -> {
            printBanner();

            // 컴포넌트 초기화
            ResourceManager resourceManager = new ResourceManager();
            DeadlockDetector detector = new DeadlockDetector();
            SolutionApplier solutionApplier = new SolutionApplier();
            ThreadVisualizer visualizer = new ThreadVisualizer();
            MetricsCollector metrics = new MetricsCollector();

            ScenarioRunner runner = new ScenarioRunner(
                    resourceManager, detector, solutionApplier, visualizer, metrics);

            // 시나리오 선택
            String scenario = args.length > 0 ? args[0].toLowerCase() : "all";
            boolean withSolution = args.length > 1 && args[1].equals("--with-solution");

            switch (scenario) {
                case "classic", "c" -> {
                    ClassicDeadlockScenario classic = new ClassicDeadlockScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, classic, withSolution);
                }
                case "database", "db" -> {
                    DatabaseDeadlockScenario database = new DatabaseDeadlockScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, database, withSolution);
                }
                case "philosopher", "p" -> {
                    PhilosopherScenario philosopher = new PhilosopherScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, philosopher, withSolution);
                }
                case "nested", "n" -> {
                    NestedLockScenario nested = new NestedLockScenario(
                            resourceManager, detector, visualizer);
                    runScenario(runner, nested, withSolution);
                }
                case "benchmark", "b" -> {
                    DeadlockBenchmark benchmark = new DeadlockBenchmark(resourceManager, metrics);
                    benchmark.runDeadlockRateBenchmark(20);
                    benchmark.runSolutionComparisonBenchmark(1000);
                }
                case "all" -> {
                    List<ScenarioResult> results = runner.runAll();
                    runner.printSummary(results);
                }
                default -> {
                    printUsage();
                }
            }

            // 메트릭 리포트
            System.out.println(metrics.generateReport());

            printFinalMessage();
        };
    }

    private void runScenario(ScenarioRunner runner, DeadlockScenario scenario, boolean withSolution)
            throws InterruptedException {

        if (withSolution) {
            scenario.runWithSolution();
        } else {
            // 비교 실행 (미적용 -> 적용)
            List<ScenarioResult> results = runner.runComparison(scenario);
            runner.printSummary(results);
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("  ____                 _ _            _      _          _     ");
        System.out.println(" |  _ \\  ___  __ _  __| | | ___   ___| | __ | |    __ _| |__  ");
        System.out.println(" | | | |/ _ \\/ _` |/ _` | |/ _ \\ / __| |/ / | |   / _` | '_ \\ ");
        System.out.println(" | |_| |  __/ (_| | (_| | | (_) | (__|   <  | |__| (_| | |_) |");
        System.out.println(" |____/ \\___|\\__,_|\\__,_|_|\\___/ \\___|_|\\_\\ |_____\\__,_|_.__/ ");
        System.out.println();
        System.out.println("  Deadlock Detection and Resolution Lab");
        System.out.println("  Experience Deadlocks, Don't Just Read About Them");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void printUsage() {
        System.out.println("Usage: ./gradlew bootRun --args='<scenario> [--with-solution]'");
        System.out.println();
        System.out.println("Available scenarios:");
        System.out.println("  classic    (c) - Classic AB-BA deadlock");
        System.out.println("  database  (db) - Database row lock deadlock");
        System.out.println("  philosopher(p) - Dining philosophers problem");
        System.out.println("  nested     (n) - Nested synchronized deadlock");
        System.out.println("  benchmark  (b) - Run performance benchmarks");
        System.out.println("  all            - Run all scenarios (default)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --with-solution  Run only with solution applied");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./gradlew bootRun --args='classic'");
        System.out.println("  ./gradlew bootRun --args='philosopher --with-solution'");
        System.out.println();
    }

    private void printFinalMessage() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    DEADLOCK LAB COMPLETED                     ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                               ║");
        System.out.println("║  Key Lessons:                                                 ║");
        System.out.println("║                                                               ║");
        System.out.println("║  1. Lock Ordering     - Always acquire locks in same order    ║");
        System.out.println("║  2. TryLock + Timeout - Never wait forever                    ║");
        System.out.println("║  3. Lock Scope        - Hold locks for shortest time          ║");
        System.out.println("║  4. Detection         - Wait-For Graph cycle = deadlock       ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Prevention > Detection > Recovery                            ║");
        System.out.println("║                                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
```

---

## Phase 7: 벤치마크

데드락 발생률 측정과 해결책별 성능 비교 벤치마크를 작성합니다.

### `src/main/java/com/experiment/deadlocklab/benchmark/DeadlockBenchmark.java`

```java
package com.experiment.deadlocklab.benchmark;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.resource.domain.LockableResource;
import com.experiment.deadlocklab.resource.manager.ResourceManager;
import com.experiment.deadlocklab.solution.strategies.LockOrderingSolution;
import com.experiment.deadlocklab.solution.strategies.TryLockSolution;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 데드락 발생률 벤치마크
 */
public class DeadlockBenchmark {

    private final ResourceManager resourceManager;
    private final MetricsCollector metrics;
    private final ConsoleLogger logger;

    public DeadlockBenchmark(ResourceManager resourceManager, MetricsCollector metrics) {
        this.resourceManager = resourceManager;
        this.metrics = metrics;
        this.logger = ConsoleLogger.of("Benchmark");
    }

    /**
     * 데드락 발생률 측정
     */
    public void runDeadlockRateBenchmark(int iterations) {
        logger.info("Running deadlock rate benchmark: " + iterations + " iterations");

        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            LockableResource lockA = resourceManager.createResource("A-" + i, "A");
            LockableResource lockB = resourceManager.createResource("B-" + i, "B");

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch latch = new CountDownLatch(2);

            // Thread 1: A -> B
            executor.submit(() -> {
                try {
                    lockA.lock();
                    Thread.sleep(10);
                    if (lockB.tryLock(100, TimeUnit.MILLISECONDS)) {
                        lockB.unlock();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    if (lockA.isHeldByCurrentThread()) lockA.unlock();
                    latch.countDown();
                }
            });

            // Thread 2: B -> A
            executor.submit(() -> {
                try {
                    lockB.lock();
                    Thread.sleep(10);
                    if (lockA.tryLock(100, TimeUnit.MILLISECONDS)) {
                        lockA.unlock();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    if (lockB.isHeldByCurrentThread()) lockB.unlock();
                    latch.countDown();
                }
            });

            try {
                boolean completed = latch.await(500, TimeUnit.MILLISECONDS);
                if (completed) {
                    successCount.incrementAndGet();
                } else {
                    deadlockCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                deadlockCount.incrementAndGet();
            }

            executor.shutdownNow();
            resourceManager.clear();
        }

        double rate = (deadlockCount.get() * 100.0) / iterations;
        logger.info(String.format("Deadlock rate: %.1f%% (%d/%d)",
                rate, deadlockCount.get(), iterations));

        metrics.increment("benchmark.deadlock.count", deadlockCount.get());
        metrics.increment("benchmark.success.count", successCount.get());
    }

    /**
     * 해결책별 성능 비교
     */
    public void runSolutionComparisonBenchmark(int iterations) {
        logger.info("Running solution comparison benchmark: " + iterations + " iterations");
        System.out.println();

        // 1. No solution (baseline)
        logger.info("Testing: No Solution...");
        long noSolutionTime = benchmarkNoSolution(iterations);
        logger.info("  Time: " + noSolutionTime + "ms");

        // 2. Lock Ordering
        logger.info("Testing: Lock Ordering...");
        long lockOrderingTime = benchmarkLockOrdering(iterations);
        logger.info("  Time: " + lockOrderingTime + "ms");

        // 3. TryLock
        logger.info("Testing: TryLock with Timeout...");
        long tryLockTime = benchmarkTryLock(iterations);
        logger.info("  Time: " + tryLockTime + "ms");

        // 결과 출력
        System.out.println();
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║      SOLUTION PERFORMANCE COMPARISON  ║");
        System.out.println("╠═══════════════════════════════════════╣");
        System.out.printf("║  No Solution     : %6d ms           ║%n", noSolutionTime);
        System.out.printf("║  Lock Ordering   : %6d ms           ║%n", lockOrderingTime);
        System.out.printf("║  TryLock Timeout : %6d ms           ║%n", tryLockTime);
        System.out.println("╚═══════════════════════════════════════╝");
    }

    private long benchmarkNoSolution(int iterations) {
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockableResource a = new LockableResource("A", "A");
            LockableResource b = new LockableResource("B", "B");

            a.lock();
            b.lock();
            b.unlock();
            a.unlock();
        }

        return System.currentTimeMillis() - start;
    }

    private long benchmarkLockOrdering(int iterations) {
        LockOrderingSolution solution = new LockOrderingSolution();
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockableResource a = new LockableResource("A", "A");
            LockableResource b = new LockableResource("B", "B");

            try {
                solution.acquireLocks(List.of(a, b));
                solution.releaseLocks(List.of(a, b));
            } catch (InterruptedException e) {
                // ignore
            }
        }

        return System.currentTimeMillis() - start;
    }

    private long benchmarkTryLock(int iterations) {
        TryLockSolution solution = new TryLockSolution(100, 1);
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockableResource a = new LockableResource("A", "A");
            LockableResource b = new LockableResource("B", "B");

            try {
                solution.acquireLocks(List.of(a, b));
                solution.releaseLocks(List.of(a, b));
            } catch (InterruptedException e) {
                // ignore
            }
        }

        return System.currentTimeMillis() - start;
    }
}
```

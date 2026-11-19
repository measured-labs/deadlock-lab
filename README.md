# 🔒 Deadlock Lab - 데드락 실험실

## 상태와 다음 할 일

- 질문: Java에서 데드락은 어떤 코드 모양에서 생기고, Wait-For Graph와 JMX로 어떻게 찾으며, 락 순서 고정 · `tryLock` 타임아웃 · 은행원 알고리즘은 각각 무엇을 깨서 데드락을 막는가.
- 상태: **학습 예정 · 실측 없음**. 따라 치기 안내서([docs/archive/PHASES.md](docs/archive/PHASES.md))를 따라 코드를 쳐 보며 구조를 배우려고 만든 저장소다. 시나리오 4개 · 탐지기 · 해결책 3개 · 벤치마크 코드는 있지만, 테스트와 Gradle wrapper는 없고 실행 결과 기록도 없다.
- 이 README의 발생률 · 오버헤드 수치는 예상(실측 아님)이다. 구조와 원문끼리 다른 곳, 아직 없는 것은 [ADR-000](docs/adr/ADR-000-deadlock-lab-structure.md)에 정리했다.
- 다음 할 일:
  1. 안내서 순서대로 코드를 함께 읽고, 시나리오마다 해결책 없이 · 적용해 돌린 출력을 남긴다.
  2. 은행원 알고리즘 설명과 README · 단계 문서끼리 다른 곳을 정리한다([ADR-000 §3 · §4](docs/adr/ADR-000-deadlock-lab-structure.md#4-선택지와-원문끼리-다른-곳)).
  3. 발생률을 재게 되면 반복 횟수 · 환경 · 원본을 남기고 예상 표를 측정값으로 바꾼다.

Java 동시성 환경에서 Deadlock을 직접 재현하고 해결하며 배우는 프로젝트

## 📖 프로젝트 소개

이 프로젝트는 멀티스레딩 환경의 핵심 문제인 **Deadlock**을 단순 암기가 아닌 **직접 재현과 해결**을 통해 체화하기 위한 실험 프로젝트입니다.

### 학습 방식
- 4가지 대표적인 데드락 시나리오 직접 재현
- 데드락 탐지 알고리즘 (Wait-For Graph) 구현
- 3가지 해결책 구현 및 효과 검증
- **도메인 기반 아키텍처** 적용

### 주요 특징

> 코드가 있는 항목은 "코드 있음"으로 적었다. 실행해서 확인한 기록은 아직 없다. ☐는 아직 하지 않은 항목이다.

- 4가지 데드락 시나리오 재현 — 코드 있음
- 데드락 탐지기 (Wait-For Graph) 구현 — 코드 있음
- 락 순서 고정, tryLock, 은행원 알고리즘 구현 — 코드 있음
- ☐ 데드락 발생률 측정 및 해결책 효과 검증 — 결과 없음
- 실시간 스레드 상태 시각화 — 코드 있음(`ThreadVisualizer`)
- 벤치마크 및 리포트 생성 — 벤치마크 코드 있음(`DeadlockBenchmark`), ☐ 리포트 없음

---

## 🎯 Deadlock이란?

두 개 이상의 스레드가 서로가 가진 자원을 기다리며 **영원히 대기**하는 상태

```
Thread 1: Lock(A) 획득 → Lock(B) 대기 중...
Thread 2: Lock(B) 획득 → Lock(A) 대기 중...
→ 둘 다 영원히 대기 (Deadlock!)
```

### 데드락 발생 4가지 조건 (Coffman Conditions)

| 조건 | 설명 | 예시 |
|------|------|------|
| **상호 배제** (Mutual Exclusion) | 자원은 한 번에 하나의 스레드만 사용 가능 | 락은 동시에 두 스레드가 획득 불가 |
| **점유 대기** (Hold and Wait) | 자원을 가진 채로 다른 자원을 대기 | A 락을 가진 채 B 락 대기 |
| **비선점** (No Preemption) | 다른 스레드의 자원을 강제로 빼앗을 수 없음 | 락 강제 해제 불가 |
| **순환 대기** (Circular Wait) | 스레드들이 원형으로 서로의 자원을 대기 | T1→T2→T3→T1 |

### 이 프로젝트에서 배우는 것

```
❌ 단순 암기: "데드락은 상호배제, 점유대기, 비선점, 순환대기 조건이 모두 만족할 때 발생"

✅ 체화 학습: 
   - 실제로 프로그램이 멈추는 것을 경험
   - Wait-For Graph로 사이클 탐지하는 과정 이해
   - 락 순서 고정만으로 해결되는 것을 직접 확인
   - tryLock 타임아웃의 필요성 체감
```

---

## 🏛️ 아키텍처

### 전체 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                       Deadlock Lab                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   Scenario Domain                        │   │
│  │                                                          │   │
│  │  ClassicDeadlock │ DatabaseLock │ NestedLock │ Philosopher │
│  │     (AB-BA)      │  (Row Lock)  │(synchronized)│ (Dining) │   │
│  └──────────────────────────┬──────────────────────────────┘   │
│                             │                                   │
│              ┌──────────────┼──────────────┐                   │
│              ▼              │              ▼                   │
│  ┌─────────────────────┐    │   ┌─────────────────────────┐   │
│  │  Detection Domain   │    │   │    Solution Domain      │   │
│  │                     │    │   │                         │   │
│  │  DeadlockDetector   │    │   │  LockOrderingSolution   │   │
│  │         │           │    │   │  TryLockSolution        │   │
│  │         ▼           │    │   │  BankerAlgorithm        │   │
│  │  WaitForGraph       │    │   │                         │   │
│  │  (사이클 탐지)       │    │   └─────────────────────────┘   │
│  └─────────────────────┘    │                                   │
│                             ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   Resource Domain                        │   │
│  │         Resource │ LockableResource │ ResourceManager   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Benchmark & Report                    │   │
│  │       데드락 발생률 │ 해결책 효과 │ 성능 측정            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 도메인 간 의존성

```
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│   scenario    │────▶│   resource    │◀────│   solution    │
│               │     │               │     │               │
│ - 시나리오 실행 │     │ - 자원 관리    │     │ - 해결책 적용  │
│ - 데드락 재현  │     │ - 락 관리     │     │ - 락 순서 고정 │
└───────┬───────┘     └───────────────┘     └───────────────┘
        │                     ▲
        │                     │
        ▼                     │
┌───────────────┐             │
│   detection   │─────────────┘
│               │
│ - 데드락 탐지  │
│ - 사이클 분석  │
└───────────────┘

의존성 특징:
- scenario → resource, detection, solution 사용
- detection → resource 모니터링
- solution → resource 래핑
- 각 도메인은 독립적으로 테스트 가능
```

---

## 📁 프로젝트 구조

> 아래 중 `ScenarioService.java`, `SolutionBenchmark.java`, `src/test/`, `results/`는 아직 없다([ADR-000 §7](docs/adr/ADR-000-deadlock-lab-structure.md#7-아직-없는-것)).

```
deadlock-lab/
├── src/main/java/com/experiment/deadlocklab/
│   ├── DeadlockLabApplication.java
│   │
│   ├── scenario/                                   # 시나리오 도메인
│   │   ├── domain/
│   │   │   └── ScenarioResult.java                 # 시나리오 실행 결과
│   │   ├── application/
│   │   │   ├── ScenarioRunner.java                 # 시나리오 실행기
│   │   │   └── ScenarioService.java                # 시나리오 서비스
│   │   └── scenarios/
│   │       ├── DeadlockScenario.java               # 시나리오 인터페이스
│   │       ├── ClassicDeadlockScenario.java        # 고전적 AB-BA 데드락
│   │       ├── DatabaseDeadlockScenario.java       # DB 스타일 행 락
│   │       ├── NestedLockScenario.java             # 중첩 synchronized
│   │       └── PhilosopherScenario.java            # 식사하는 철학자 문제
│   │
│   ├── resource/                                   # 자원 도메인
│   │   ├── domain/
│   │   │   ├── Resource.java                       # 자원 추상화
│   │   │   └── LockableResource.java               # 락 가능 자원
│   │   └── manager/
│   │       └── ResourceManager.java                # 자원 관리자
│   │
│   ├── detection/                                  # 탐지 도메인
│   │   ├── domain/
│   │   │   ├── WaitForGraph.java                   # 대기 그래프
│   │   │   └── DetectionResult.java                # 탐지 결과
│   │   ├── application/
│   │   │   └── DeadlockDetector.java               # 데드락 탐지기
│   │   └── monitor/
│   │       └── ThreadStateMonitor.java             # 스레드 상태 모니터
│   │
│   ├── solution/                                   # 해결책 도메인
│   │   ├── domain/
│   │   │   └── SolutionType.java                   # 해결책 타입 enum
│   │   ├── application/
│   │   │   └── SolutionApplier.java                # 해결책 적용기
│   │   └── strategies/
│   │       ├── DeadlockSolution.java               # 해결책 인터페이스
│   │       ├── LockOrderingSolution.java           # 락 순서 고정
│   │       ├── TryLockSolution.java                # tryLock + 타임아웃
│   │       └── BankerAlgorithm.java                # 은행원 알고리즘
│   │
│   ├── benchmark/                                  # 벤치마크
│   │   ├── DeadlockBenchmark.java                  # 데드락 발생률 측정
│   │   └── SolutionBenchmark.java                  # 해결책 효과 측정
│   │
│   └── global/                                     # 공통
│       ├── config/
│       │   └── BeanConfig.java
│       ├── logger/
│       │   └── ConsoleLogger.java                  # 콘솔 로깅 (스레드별 색상)
│       ├── visualizer/
│       │   └── ThreadVisualizer.java               # 스레드 상태 시각화
│       └── metrics/
│           └── MetricsCollector.java               # 메트릭 수집
│
├── src/main/resources/
│   └── application.yml
│
├── src/test/java/com/experiment/deadlocklab/
│   ├── scenario/
│   ├── resource/
│   ├── detection/
│   └── solution/
│
├── results/                                        # 실험 결과
│   ├── reports/
│   └── logs/
│
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🔗 도메인 간 협력 코드 예시

### ScenarioRunner (시나리오 실행)

```java
// scenario/application/ScenarioRunner.java
@Component
@RequiredArgsConstructor
public class ScenarioRunner {
    
    private final ResourceManager resourceManager;
    private final DeadlockDetector deadlockDetector;
    private final SolutionApplier solutionApplier;
    
    public ScenarioResult run(DeadlockScenario scenario, boolean withSolution) {
        // 1. 자원 초기화
        List<LockableResource> resources = resourceManager.createResources(
            scenario.getRequiredResourceCount()
        );
        
        // 2. 해결책 적용 여부
        if (withSolution) {
            resources = solutionApplier.apply(
                resources, 
                scenario.getRecommendedSolution()
            );
        }
        
        // 3. 시나리오 실행 + 탐지
        CompletableFuture<Void> execution = scenario.execute(resources);
        CompletableFuture<DetectionResult> detection = deadlockDetector.monitor();
        
        // 4. 결과 반환
        return ScenarioResult.of(execution, detection);
    }
}
```

### DeadlockDetector (탐지)

```java
// detection/application/DeadlockDetector.java
@Component
@RequiredArgsConstructor
public class DeadlockDetector {
    
    private final WaitForGraph waitForGraph;
    private final ThreadStateMonitor threadMonitor;
    
    public CompletableFuture<DetectionResult> monitor() {
        return CompletableFuture.supplyAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // 스레드 상태 수집
                Map<Thread, Thread> waitingFor = threadMonitor.getWaitingRelations();
                
                // 그래프 업데이트
                waitForGraph.update(waitingFor);
                
                // 사이클 탐지
                if (waitForGraph.hasCycle()) {
                    List<Thread> cycle = waitForGraph.findCycle();
                    return DetectionResult.deadlockDetected(cycle);
                }
                
                Thread.sleep(100);
            }
            return DetectionResult.noDeadlock();
        });
    }
}
```

### LockOrderingSolution (해결책)

```java
// solution/strategies/LockOrderingSolution.java
@Component
public class LockOrderingSolution implements DeadlockSolution {
    
    @Override
    public SolutionType getType() {
        return SolutionType.LOCK_ORDERING;
    }
    
    @Override
    public List<LockableResource> apply(List<LockableResource> resources) {
        // 자원 ID 기준으로 정렬된 락 순서 강제
        return resources.stream()
            .map(resource -> new OrderedLockResource(resource))
            .collect(Collectors.toList());
    }
    
    // 내부 래퍼 클래스
    private static class OrderedLockResource extends LockableResource {
        private final LockableResource delegate;
        
        @Override
        public void lock() {
            // 항상 낮은 ID부터 락 획득하도록 강제
            // ...
        }
    }
}
```

---

## 🧪 실험 시나리오

### 1. Classic AB-BA Deadlock

**시나리오**: 두 스레드가 반대 순서로 락 획득

```
Thread 1                    Thread 2
─────────                   ─────────
lock(A) ✓                   
                           lock(B) ✓
lock(B) ⏳ 대기...          
                           lock(A) ⏳ 대기...
     ↓                          ↓
   ████████ DEADLOCK! ████████
```

**해결책**: 락 순서 고정 (항상 A → B 순서)

```java
// ❌ 위험한 코드
Thread 1: lock(A) → lock(B)
Thread 2: lock(B) → lock(A)

// ✅ 안전한 코드 (순서 고정)
Thread 1: lock(A) → lock(B)
Thread 2: lock(A) → lock(B)  // 같은 순서!
```

---

### 2. Database Row Lock Deadlock

**시나리오**: 행 단위 락에서 발생하는 데드락

```
Transaction 1               Transaction 2
─────────────               ─────────────
UPDATE accounts            
SET balance = balance - 100
WHERE id = 1001 🔒         
                           UPDATE accounts
                           SET balance = balance - 200
                           WHERE id = 1002 🔒
UPDATE accounts            
SET balance = balance + 100
WHERE id = 1002 ⏳          
                           UPDATE accounts
                           SET balance = balance + 200
                           WHERE id = 1001 ⏳
     ↓                          ↓
   ████████ DEADLOCK! ████████
```

**해결책**: 타임아웃 + 재시도

```java
// tryLock with timeout
if (lock.tryLock(5, TimeUnit.SECONDS)) {
    try {
        // 작업 수행
    } finally {
        lock.unlock();
    }
} else {
    // 타임아웃! 재시도 또는 롤백
}
```

---

### 3. Nested Lock Deadlock

**시나리오**: synchronized 블록 중첩으로 인한 데드락

```java
// 위험한 코드 패턴
class ServiceA {
    synchronized void methodA(ServiceB b) {
        b.methodB(this);  // ServiceB의 락 필요
    }
}

class ServiceB {
    synchronized void methodB(ServiceA a) {
        a.callback();     // ServiceA의 락 필요 → DEADLOCK!
    }
}
```

**해결책**: 락 범위 최소화, 락 분리

```java
// ✅ 락 범위 최소화
class ServiceA {
    void methodA(ServiceB b) {
        Object data;
        synchronized(this) {
            data = prepareData();  // 짧은 락
        }
        b.methodB(data);  // 락 해제 후 호출
    }
}
```

---

### 4. Dining Philosophers (식사하는 철학자)

**시나리오**: 5명의 철학자, 5개의 포크

```
        [P0]
      🍴    🍴
   [P4]  🍝  [P1]
      🍴    🍴
   [P3]  🍴  [P2]

모든 철학자가 왼쪽 포크를 집으면?
→ 아무도 오른쪽 포크를 집을 수 없음
→ 모두 영원히 대기 (DEADLOCK!)
```

**해결책**: 자원 순서화 (낮은 번호 포크부터 획득)

```java
// ❌ 데드락 발생
philosopher[i].pickUp(fork[i]);           // 왼쪽 포크
philosopher[i].pickUp(fork[(i+1) % 5]);   // 오른쪽 포크

// ✅ 자원 순서화
int first = Math.min(i, (i+1) % 5);
int second = Math.max(i, (i+1) % 5);
philosopher[i].pickUp(fork[first]);
philosopher[i].pickUp(fork[second]);
```

---

## 🔍 데드락 탐지 알고리즘

### Wait-For Graph (대기 그래프)

```
스레드 간 대기 관계를 그래프로 표현

T1 → T2: T1이 T2가 가진 자원을 대기 중

사이클이 존재하면 = DEADLOCK!

예시:
T1 → T2 → T3 → T1  (사이클 = 데드락!)
```

**구현 방식**:
```java
// detection/domain/WaitForGraph.java
public class WaitForGraph {
    private Map<Thread, Set<Thread>> edges = new ConcurrentHashMap<>();
    
    // 간선 추가: waiter가 holder를 대기
    public void addEdge(Thread waiter, Thread holder) {
        edges.computeIfAbsent(waiter, k -> new HashSet<>()).add(holder);
    }
    
    // 간선 제거
    public void removeEdge(Thread waiter, Thread holder) {
        edges.getOrDefault(waiter, Collections.emptySet()).remove(holder);
    }
    
    // DFS로 사이클 탐지
    public boolean hasCycle() {
        Set<Thread> visited = new HashSet<>();
        Set<Thread> recursionStack = new HashSet<>();
        
        for (Thread thread : edges.keySet()) {
            if (detectCycle(thread, visited, recursionStack)) {
                return true;  // 데드락 발견!
            }
        }
        return false;
    }
    
    // 사이클을 구성하는 스레드 목록 반환
    public List<Thread> findCycle() {
        // DFS로 사이클 경로 추적
        // ...
    }
}
```

---

## 📈 예상 실험 결과 (예상 · 실측 아님)

> 아래 두 표는 돌려서 잰 값이 아니라 예상이다. 측정 기록은 아직 없다.

### 데드락 발생률 (해결책 미적용)

| 시나리오 | 100회 실행 | 데드락 발생 | 발생률 |
|----------|------------|-------------|--------|
| Classic AB-BA | 100 | 95+ | ~95% |
| Database Lock | 100 | 80+ | ~80% |
| Nested Lock | 100 | 60+ | ~60% |
| Philosophers | 100 | 100 | 100% |

### 해결책 적용 후

| 시나리오 | 해결책 | 데드락 발생 | 성능 오버헤드 |
|----------|--------|-------------|---------------|
| Classic AB-BA | Lock Ordering | 0% | 거의 없음 |
| Database Lock | TryLock + Timeout | 0% (재시도) | ~5% |
| Nested Lock | Lock Scope 최소화 | 0% | 거의 없음 |
| Philosophers | Resource Ordering | 0% | 거의 없음 |

---

## 🛠️ 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.1 |
| Build | Gradle (Groovy) |
| Architecture | 도메인 기반 |
| Concurrency | ReentrantLock, synchronized, ExecutorService |
| Detection | JMX ThreadMXBean, 자체 WaitForGraph |

---

## 📦 Gradle 설정

### build.gradle
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

### settings.gradle
```groovy
rootProject.name = 'deadlock-lab'
```

---

## 🚀 Quick Start

### 요구사항
- Java 25+
- Gradle 8.0+

### 실행 방법

> 아직 없음. Gradle wrapper(`./gradlew`)가 저장소에 없고, 4 · 5번의 `java -cp ... ClassicDeadlockScenario` 명령은 시나리오 클래스에 `main`이 없어서 그대로는 돌지 않는다. 코드에 적힌 진입점은 `DeadlockLabApplication`이고 사용법은 `bootRun --args='<scenario> [--with-solution]'`이다.

```bash
# 1. 클론
git clone https://github.com/junhyeong9812/deadlock-lab.git
cd deadlock-lab

# 2. 빌드
./gradlew clean build

# 3. 전체 시나리오 실행
./gradlew bootRun

# 4. 개별 시나리오 실행 (데드락 체험)
java -cp build/classes/java/main \
  com.experiment.deadlocklab.scenario.scenarios.ClassicDeadlockScenario

java -cp build/classes/java/main \
  com.experiment.deadlocklab.scenario.scenarios.PhilosopherScenario

# 5. 해결책 적용 버전 실행
java -cp build/classes/java/main \
  com.experiment.deadlocklab.scenario.scenarios.ClassicDeadlockScenario --with-solution
```

---

## 🧪 직접 실험해보기 - 데드락 체험

> 아래 명령과 출력은 예시다(위 실행 방법의 주의와 같다). 실제 실행 출력은 아직 남기지 않았다.

```bash
# ⚠️ 주의: 실제로 프로그램이 멈춥니다!

# 해결책 미적용 버전 (데드락 발생)
java -cp build/classes/java/main \
  com.experiment.deadlocklab.scenario.scenarios.ClassicDeadlockScenario --no-solution

# 결과:
# [Thread-1] Lock(A) 획득 ✓
# [Thread-2] Lock(B) 획득 ✓
# [Thread-1] Lock(B) 대기 중... ⏳
# [Thread-2] Lock(A) 대기 중... ⏳
# ...
# 🔴 DEADLOCK DETECTED! (Ctrl+C로 종료)
```

### 스레드 상태 시각화

```
═══════════════════════════════════════════════════════
         DEADLOCK LAB - Thread Status Monitor
═══════════════════════════════════════════════════════

Thread-1 [BLOCKED  ] ████████░░░░ Lock(A)✓ → Lock(B)⏳
Thread-2 [BLOCKED  ] ████████░░░░ Lock(B)✓ → Lock(A)⏳

Wait-For Graph:
  Thread-1 ──────→ Thread-2
      ↑               │
      └───────────────┘
      
🔴 CYCLE DETECTED = DEADLOCK!

═══════════════════════════════════════════════════════
```

---

## 🏗️ 아키텍처 선택 이유

### 왜 도메인 최상위 구조인가?

```
❌ 기능별 플랫 구조
src/
├── scenario/
├── detection/
├── solution/
└── common/

→ 도메인 내부 구조가 불명확
→ domain, application, infrastructure 구분 없음

✅ 도메인 최상위 + 내부 레이어
src/
├── scenario/
│   ├── domain/
│   ├── application/
│   └── scenarios/
├── detection/
│   ├── domain/
│   ├── application/
│   └── monitor/
└── ...

→ 각 도메인 내부가 체계적
→ 테스트 용이
→ ACID Lab과 구조 일관성 유지
```

### 왜 헥사고날(포트/어댑터)은 안 쓰나?

```
ACID Lab: transaction → account 의존 (도메인 간 호출)
         → 포트/어댑터로 의존성 역전 필요

Deadlock Lab: scenario → resource, detection, solution (조합)
             → 단방향 의존, 순환 없음
             → 포트/어댑터 없이도 충분히 느슨한 결합

결론: 프로젝트 특성에 맞는 적절한 수준의 추상화
```

---

## 💡 핵심 교훈

### Deadlock
```
1. 데드락은 예방이 최선 - 발생 후 해결은 비용이 크다
2. 락 순서 고정만으로 대부분 해결 가능
3. 타임아웃은 필수 - 영원히 기다리지 마라
4. 락 범위는 최소화 - 필요한 순간만 잠금
5. 탐지 + 복구 전략도 필요하다
```

### 아키텍처
```
1. 도메인 최상위 구조 → 응집도 높은 코드
2. 도메인 내부 레이어 분리 → 역할 명확
3. 프로젝트 특성에 맞는 추상화 수준 선택
4. 오버엔지니어링 vs 적절한 설계의 균형
```

---

## 🔗 관련 프로젝트

> 💡 DB 트랜잭션에서 락이 어떻게 쓰이는지 궁금하다면?
> → [ACID Lab](https://github.com/junhyeong9812/acid-lab) 참고

---

## 📚 참고 자료

### Deadlock & Concurrency
- [Java Concurrency in Practice](https://jcip.net/) - Brian Goetz
- [The Dining Philosophers Problem](https://en.wikipedia.org/wiki/Dining_philosophers_problem)
- [Deadlock Detection Algorithm](https://www.geeksforgeeks.org/deadlock-detection-algorithm-in-operating-system/)
- [Banker's Algorithm](https://en.wikipedia.org/wiki/Banker%27s_algorithm)

### Architecture
- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [Package by Feature](https://phauer.com/2020/package-by-feature/)

---

## ⚠️ 중요 경고

### 프로덕션에서 절대 하지 말아야 할 것들

```
❌ 락 순서 무시
❌ 타임아웃 없는 락 획득
❌ 중첩 synchronized 남용
❌ 동시성 테스트 없이 배포
```

### 이 프로젝트의 목적

목표(아직 실행 기록 없음 — 맨 위 '상태와 다음 할 일' 참고):

```
- 데드락 문제 상황 직접 체험
- 탐지 알고리즘 이해
- 해결책의 효과 검증

⚠️ 의도적으로 데드락을 발생시키는 코드 포함
⚠️ 프로덕션용 코드가 아닙니다!
```

---

**Made with 💡 for deeper understanding of Deadlock in Java Concurrency**

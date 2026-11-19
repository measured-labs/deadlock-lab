# 구현 페이즈 가이드

> 보존 원문(2026-11-18 `docs/archive/`로 옮김). 정리본은 [ADR-000](../adr/ADR-000-deadlock-lab-structure.md)이다. 체크포인트는 모두 미체크이고, 7단계 "예상 결과"의 발생률은 예상(실측 아님)이다. 4 · 5단계 순서는 [PHASES.md](PHASES.md)와 반대다.

데드락을 직접 발생시키고, 탐지하고, 해결하는 과정을 단계별로 구현한다.

---

## 전체 구조 요약

```
deadlock-lab/
├── global/          # 공통 유틸 (로거, 메트릭, 시각화)
├── resource/        # 락 가능한 자원 도메인
├── detection/       # 데드락 탐지 (Wait-For Graph, JMX)
├── solution/        # 데드락 해결 전략 3종
├── scenario/        # 데드락 시나리오 4종
└── benchmark/       # 성능 측정
```

---

## Phase 1: 공통 인프라

**목표**: 이후 모든 Phase에서 사용할 기반 유틸리티

### 생성 파일
- `global/logger/ConsoleLogger.java`
- `global/metrics/MetricsCollector.java`
- `global/visualizer/ThreadVisualizer.java`
- `global/config/BeanConfig.java`

### 구현 내용

#### ConsoleLogger
```
ConsoleLogger
├── ANSI 컬러 코드 (RED, GREEN, YELLOW, BLUE, PURPLE, CYAN)
├── 스레드별 컬러 자동 할당 (해시 기반)
├── 로그 레벨: info, success, warn, error, debug, deadlock
├── 포맷: [HH:mm:ss.SSS] [threadName] [prefix] message
└── 팩토리: of(String prefix), of(Class<?> clazz)
```

#### MetricsCollector
```
MetricsCollector
├── counters: ConcurrentHashMap<String, AtomicLong>
├── timers: startTimer / stopTimer (나노초 정밀도)
├── generateReport() → 포맷된 메트릭 출력
└── 싱글턴 getInstance()
```

#### ThreadVisualizer
```
ThreadVisualizer
├── enum State { RUNNING, WAITING, BLOCKED, TERMINATED }
├── threadStatuses: ConcurrentHashMap<String, ThreadStatus>
├── updateStatus(threadName, state, holdingLock, waitingFor)
├── printStatus() → 현재 스레드 상태 출력
├── printWaitForGraph(graph) → 그래프 시각화
└── printDeadlockVisualization(cycle) → 데드락 사이클 시각화
```

### 체크포인트
- [ ] 멀티스레드에서 ConsoleLogger 컬러가 스레드별로 다른지 확인
- [ ] MetricsCollector 카운터/타이머 동작
- [ ] BeanConfig에 빈 등록

---

## Phase 2: Resource 도메인

**목표**: 데드락의 대상이 되는 "락 가능한 자원" 구현

### 생성 파일
- `resource/domain/Resource.java` (추상 클래스)
- `resource/domain/LockableResource.java`
- `resource/manager/ResourceManager.java`

### 구현 내용

#### Resource (추상)
```
Resource (abstract)
├── id: String
├── name: String
└── getId(), getName(), toString()
```

#### LockableResource
```
LockableResource extends Resource
├── lock: ReentrantLock(fair=true) — 공정 모드
├── holder: volatile Thread — 현재 락 소유 스레드
├── waiter: volatile Thread — 락 대기 중인 스레드
│
├── lock() — 블로킹 획득, holder 설정
├── tryLock() — 논블로킹 즉시 시도
├── tryLock(timeout, unit) — 타임아웃 시도
├── unlock() — 해제, holder=null
├── isHeldByCurrentThread(), isLocked()
└── getHolder(), getWaiter()
```

#### ResourceManager
```
ResourceManager
├── resources: ConcurrentHashMap<String, LockableResource>
├── createResource(id, name) — 단일 생성
├── createResources(count, prefix) — 일괄 생성 (A, B, C...)
├── getResource(id), getAllResources()
├── printStatus() — 전체 자원 상태 출력
├── releaseAllLocks() — 전체 해제
└── clear()
```

### 핵심 포인트
- **Fair 모드**: FIFO 보장, 기아 방지
- **volatile holder/waiter**: 스레드 간 가시성 보장
- waiter 필드는 이후 Wait-For Graph 구성에 사용됨

### 체크포인트
- [ ] 두 스레드가 같은 자원에 lock() 시 하나가 블로킹되는지 확인
- [ ] tryLock 타임아웃 동작
- [ ] unlock 후 다른 스레드가 획득 가능한지 확인

---

## Phase 3: 데드락 탐지

**목표**: Wait-For Graph 기반 사이클 탐지 + JMX 모니터링

### 생성 파일
- `detection/domain/WaitForGraph.java`
- `detection/domain/DetectionResult.java`
- `detection/monitor/ThreadStateMonitor.java`
- `detection/application/DeadlockDetector.java`

### 구현 내용

#### WaitForGraph
```
WaitForGraph
├── edges: ConcurrentHashMap<Thread, Set<Thread>>
│   └── key=대기 스레드, value=소유 스레드들
│
├── addEdge(waiter, holder) — T1이 T2를 기다림
├── removeEdge(waiter, holder)
├── removeThread(thread)
├── update(waitingRelations) — 전체 재구성
│
├── hasCycle() → boolean
│   └── DFS + 재귀 스택으로 사이클 탐지
│
├── findCycle() → List<Thread>
│   └── 사이클 경로 추출 (시각화용)
│
└── clear(), edgeCount()
```

#### DetectionResult
```
DetectionResult
├── deadlockDetected: boolean
├── involvedThreads: List<Thread>
├── detectedAt: LocalDateTime
├── description: String
│
├── static deadlockDetected(cycle) — 데드락 발견
├── static noDeadlock() — 정상
└── static timeout() — 탐지 시간 초과
```

#### ThreadStateMonitor
```
ThreadStateMonitor
├── threadMXBean: ThreadMXBean — JMX 연동
├── threadWaiting: Map<Thread, LockableResource>
├── threadHolding: Map<Thread, LockableResource>
│
├── recordWaiting(thread, resource) — 대기 기록
├── recordAcquired(thread, resource) — 획득 기록
├── recordReleased(thread, resource) — 해제 기록
├── getWaitingRelations() → Map<Thread, Thread> — 대기→소유 관계
└── findDeadlockedThreadsViaJMX() — JMX 기반 탐지 (백업)
```

#### DeadlockDetector
```
DeadlockDetector
├── waitForGraph: WaitForGraph
├── threadMonitor: ThreadStateMonitor
├── monitorExecutor: ExecutorService
├── monitoring: volatile boolean
│
├── checkNow() → DetectionResult — 즉시 탐지
│   └── threadMonitor에서 관계 가져와 graph 갱신 → hasCycle 확인
│
├── startMonitoring(timeoutMs) → CompletableFuture<DetectionResult>
│   └── 100ms 간격 비동기 폴링
│
├── stopMonitoring()
├── addWaitEdge(), removeWaitEdge()
├── recordWaiting(), recordAcquired(), recordReleased()
└── clear()
```

### 핵심 포인트
- **DFS 사이클 탐지**: visited + recursionStack 두 집합으로 구현
- **JMX 백업**: ThreadMXBean.findDeadlockedThreadIds()는 JVM 레벨 탐지
- **비동기 모니터링**: CompletableFuture로 타임아웃 지원

### 체크포인트
- [ ] 2개 노드 A→B, B→A 사이클 탐지
- [ ] 3개 이상 노드 사이클 탐지
- [ ] 사이클 없는 그래프에서 false 반환
- [ ] findCycle()이 정확한 경로 반환

---

## Phase 4: 데드락 시나리오 (문제 재현)

**목표**: 4가지 대표적 데드락 패턴을 코드로 재현

### 생성 파일
- `scenario/domain/ScenarioResult.java`
- `scenario/scenarios/DeadlockScenario.java` (인터페이스)
- `scenario/scenarios/ClassicDeadlockScenario.java`
- `scenario/scenarios/DatabaseDeadlockScenario.java`
- `scenario/scenarios/NestedLockScenario.java`
- `scenario/scenarios/PhilosopherScenario.java`

### 공통 구조

#### ScenarioResult
```
ScenarioResult
├── scenarioName, deadlockOccurred, solutionApplied
├── executionTime: Duration
├── detectionResult: DetectionResult
├── description: String
│
├── static deadlock(name, duration, detection, desc)
├── static success(name, solution, duration, desc)
└── static timeout(name, duration, desc)
```

#### DeadlockScenario (인터페이스)
```
DeadlockScenario
├── getName(), getDescription()
├── getRequiredResourceCount()
├── getRecommendedSolution() → SolutionType
├── runWithoutSolution() → ScenarioResult
└── runWithSolution() → ScenarioResult
```

### 4-1. ClassicDeadlockScenario (AB-BA 데드락)
```
패턴: Thread1이 A→B 순서, Thread2가 B→A 순서로 락 획득
자원: 2개 (A, B)
추천 해결: LOCK_ORDERING

흐름 (without solution):
├── Thread1: lock(A) → sleep → lock(B) ← B는 Thread2가 소유
├── Thread2: lock(B) → sleep → lock(A) ← A는 Thread1이 소유
└── → 교착 상태!

구현 포인트:
├── ExecutorService + CountDownLatch로 동시 시작 보장
├── DeadlockDetector.startMonitoring()으로 비동기 탐지
└── 5초 타임아웃
```

### 4-2. DatabaseDeadlockScenario (DB 행 락 데드락)
```
패턴: TX1이 row1→row2, TX2가 row2→row1 순서로 락 획득
자원: 2개 (row1001, row1002)
추천 해결: TRY_LOCK_TIMEOUT

흐름:
├── TX1: lock(row1001) → 작업 → lock(row1002) ← TX2가 소유
├── TX2: lock(row1002) → 작업 → lock(row1001) ← TX1이 소유
└── → DB 데드락!

구현 포인트:
├── 실제 DB 트랜잭션 시뮬레이션
└── 3초 타임아웃
```

### 4-3. NestedLockScenario (중첩 락 데드락)
```
패턴: ServiceA.method()가 synchronized 내에서 ServiceB.method() 호출, 반대도 동일
자원: 2개 (ServiceA, ServiceB의 모니터 락)
추천 해결: LOCK_ORDERING (락 범위 최소화)

흐름:
├── Thread1: synchronized(A) { B.method() } → B의 락 필요
├── Thread2: synchronized(B) { A.method() } → A의 락 필요
└── → 중첩 호출 데드락!

구현 포인트:
├── inner class: UnsafeServiceA/B — synchronized 메서드 간 상호 호출
├── inner class: SafeServiceA/B — 락 해제 후 외부 호출
└── 해결: 락 범위 최소화 (synchronized 블록 밖에서 호출)
```

### 4-4. PhilosopherScenario (식사하는 철학자)
```
패턴: 5명 철학자, 5개 포크, 모두 왼쪽 포크 먼저 집음
자원: 5개 (fork0~4)
추천 해결: LOCK_ORDERING

흐름 (without solution):
├── Plato: lock(fork0) → lock(fork1)
├── Aristotle: lock(fork1) → lock(fork2)
├── Socrates: lock(fork2) → lock(fork3)
├── Descartes: lock(fork3) → lock(fork4)
├── Kant: lock(fork4) → lock(fork0) ← Plato가 소유!
└── → 5-way 순환 대기!

해결 (with solution):
├── 항상 번호가 작은 포크 먼저 획득
├── Kant: lock(fork0) → lock(fork4) — 순서 변경!
└── → 순환 대기 깨짐
```

### 체크포인트
- [ ] 각 시나리오 runWithoutSolution()에서 데드락 발생 확인
- [ ] DeadlockDetector가 사이클을 정확히 탐지
- [ ] 타임아웃 내에 탐지 완료

---

## Phase 5: 데드락 해결 전략

**목표**: 3가지 해결 전략 구현

### 생성 파일
- `solution/domain/SolutionType.java` (enum)
- `solution/strategies/DeadlockSolution.java` (인터페이스)
- `solution/strategies/LockOrderingSolution.java`
- `solution/strategies/TryLockSolution.java`
- `solution/strategies/BankerAlgorithm.java`
- `solution/application/SolutionApplier.java`

### 구현 내용

#### SolutionType (enum)
```
LOCK_ORDERING    — "항상 같은 순서로 락 획득"
TRY_LOCK_TIMEOUT — "타임아웃 후 재시도"
BANKER_ALGORITHM — "안전 상태 검사 후 자원 할당"
NONE             — "해결책 미적용"
```

#### DeadlockSolution (인터페이스)
```
DeadlockSolution
├── getType() → SolutionType
├── apply(resources) → List<LockableResource> — 자원 전처리
├── acquireLocks(resources) → boolean — 전략 적용 획득
└── releaseLocks(resources) — 해제
```

#### 5-1. LockOrderingSolution
```
전략: 자원 ID 오름차순으로 정렬 후 획득
├── apply() → ID 기준 정렬된 리스트 반환
├── acquireLocks() → 정렬 순서로 lock()
├── releaseLocks() → 역순 unlock()
└── lockInOrder(a, b) → 두 자원 순서 보장 헬퍼

원리: 순환 대기 조건(Coffman 4번째) 제거
```

#### 5-2. TryLockSolution
```
전략: 타임아웃 + 재시도 + 지수 백오프
├── timeoutMs: 1000 (기본)
├── maxRetries: 3 (기본)
│
├── acquireLocks():
│   └── for retry in 0..maxRetries:
│       ├── tryAcquireAll(resources, timeout) 시도
│       ├── 성공 → return true
│       ├── 실패 → releaseAll() 롤백
│       └── 랜덤 백오프 sleep 후 재시도
│
├── tryAcquireAll() → 하나라도 실패 시 전체 해제
└── releaseLocks() → 전체 해제

원리: 무한 대기 조건 제거 + 점유 후 대기 조건 완화
```

#### 5-3. BankerAlgorithm
```
전략: 자원 할당 전 안전 상태(safe state) 검사
├── totalResources: Map<resourceId, 총량>
├── available: Map<resourceId, 가용량>
├── allocation: Map<processId, Map<resourceId, 할당량>>
├── maximum: Map<processId, Map<resourceId, 최대 필요량>>
│
├── registerResource(id, count) — 자원 등록
├── registerProcess(id, maxNeed) — 프로세스 등록
├── requestResource(processId, resourceId, count):
│   ├── 가상 할당 수행
│   ├── isSafeState() 검사
│   ├── 안전 → 실제 할당
│   └── 불안전 → 거부 + 롤백
│
├── isSafeState():
│   ├── work = available 복사
│   ├── finish = 모든 프로세스 false
│   ├── 반복: need <= work인 프로세스 찾아 work += allocation
│   └── 모든 프로세스 finish=true면 안전
│
└── releaseResource(processId, resourceId, count)

원리: 교착 회피(Deadlock Avoidance) — 4가지 조건 모두 예방
```

#### SolutionApplier
```
SolutionApplier
├── solutions: Map<SolutionType, DeadlockSolution>
├── 생성자에서 3가지 전략 모두 등록
├── apply(resources, type) → 전략 적용
├── acquireWithSolution(resources, type) → boolean
├── releaseWithSolution(resources, type)
└── getSolution(type)
```

### 체크포인트
- [ ] 각 시나리오 runWithSolution()에서 데드락 0% 확인
- [ ] TryLockSolution 재시도 로그 출력
- [ ] BankerAlgorithm 불안전 상태 거부 확인

---

## Phase 6: ScenarioRunner + 애플리케이션 통합

**목표**: 모든 시나리오를 실행하고 결과를 비교하는 러너

### 생성 파일
- `scenario/application/ScenarioRunner.java`
- `DeadlockLabApplication.java` 수정

### 구현 내용

#### ScenarioRunner
```
ScenarioRunner
├── 의존성: ResourceManager, DeadlockDetector, SolutionApplier,
│           ThreadVisualizer, MetricsCollector, ConsoleLogger
│
├── run(scenario, withSolution) → ScenarioResult
│   ├── 자원 초기화
│   ├── 시나리오 실행
│   ├── 결과 출력
│   └── 정리 (자원 해제, 디텍터 초기화)
│
├── runComparison(scenario) → List<ScenarioResult>
│   ├── runWithoutSolution() — 데드락 발생 확인
│   └── runWithSolution() — 해결 확인
│
├── runAll() → List<ScenarioResult>
│   └── 4개 시나리오 × 2 (without/with) = 8회 실행
│
└── printSummary(results) → 결과 테이블 출력
```

#### DeadlockLabApplication
```
DeadlockLabApplication (CommandLineRunner)
├── ASCII 배너 출력
├── CLI 인자 파싱:
│   ├── classic — ClassicDeadlockScenario
│   ├── database — DatabaseDeadlockScenario
│   ├── philosopher — PhilosopherScenario
│   ├── nested — NestedLockScenario
│   ├── benchmark — 벤치마크 실행
│   ├── all — 전체 시나리오 (기본값)
│   └── --with-solution — 해결책 적용 모드
│
├── 사용법: ./gradlew bootRun --args='<scenario> [--with-solution]'
└── 결과 요약 출력
```

### 체크포인트
- [ ] `./gradlew bootRun` — 전체 시나리오 실행
- [ ] `./gradlew bootRun --args='classic'` — 개별 실행
- [ ] 결과 테이블에 DEADLOCK/SUCCESS 표시
- [ ] 자원 정리 후 다음 시나리오 정상 실행

---

## Phase 7: 벤치마크 (선택)

**목표**: 데드락 발생률 및 해결 전략 성능 비교

### 생성 파일
- `benchmark/DeadlockBenchmark.java`

### 구현 내용
```
DeadlockBenchmark
├── runDeadlockRateBenchmark(iterations)
│   └── N회 반복 → 데드락 발생률 측정
│
├── runSolutionComparisonBenchmark(iterations)
│   ├── benchmarkNoSolution() → 기본 데드락률
│   ├── benchmarkLockOrdering() → 해결 후 성능
│   └── benchmarkTryLock() → 해결 후 성능
│
└── 결과: 발생률, 평균 실행시간, 성공률
```

### 예상 결과(실측 아님)
```
| 시나리오      | 해결 미적용 데드락률 | Lock Ordering | TryLock |
|-------------|-------------------|---------------|---------|
| Classic     | ~95%              | 0%            | 0%      |
| Database    | ~80%              | 0%            | 0%      |
| Nested      | ~60%              | 0%            | 0%      |
| Philosopher | ~100%             | 0%            | 0%      |
```

---

## 요약: 페이즈별 의존 관계

```
Phase 1: 공통 인프라 (Logger, Metrics, Visualizer)
    ↓
Phase 2: Resource 도메인 (LockableResource, ResourceManager)
    ↓
Phase 3: 데드락 탐지 (WaitForGraph, Detector)
    ↓
Phase 4: 데드락 시나리오 4종 (문제 재현)
    ↓
Phase 5: 해결 전략 3종 (LockOrdering, TryLock, Banker)
    ↓
Phase 6: ScenarioRunner + 애플리케이션 통합
    ↓
Phase 7: 벤치마크 (선택)
```

Phase 1~6이 **필수**, Phase 7은 **선택**이다.

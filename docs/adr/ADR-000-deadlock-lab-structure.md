# ADR-000: 데드락 재현 · 탐지 · 해결 구조

- 상태: 학습 예정 — 코드는 대부분 있지만 실행 결과 기록이 없다. 나중에 코드를 함께 읽으며 단계마다 돌려 보고, 그때 결과를 남긴다.
- 날짜: 2026-11-18(단계 문서를 이 ADR로 정리한 날. 코드와 원문은 첫 커밋 `580f975`에 있다)
- 성격: 구조 학습용 랩의 설계 · 범위 정리. 원문 두 개는 `docs/archive/`로 옮겼다. 원문 내용은 그대로 두고 머리말 2곳 · 예상 표시 1곳만 바꿨다(§8 대응표).

## 1. 이 랩이 묻는 것

Java에서 데드락은 어떤 코드 모양에서 생기고, Wait-For Graph와 JMX(`ThreadMXBean`)로 어떻게 찾으며, 락 순서 고정 · `tryLock` 타임아웃 · 은행원 알고리즘은 각각 Coffman 네 조건 중 무엇을 깨서 데드락을 막는가.

이 저장소는 따라 치기 안내서(아래 §6)를 따라 코드를 직접 쳐 보며 구조를 배우려고 만들었다. 측정 실험보다는 구조 학습이 목적이다.

## 2. 설계

도메인을 최상위 패키지로 두고, 도메인마다 안에서 `domain` · `application` 등으로 나눈다(README "아키텍처 선택 이유"). 의존은 한 방향이다.

```
scenario ──▶ resource ◀── solution
   │            ▲
   ▼            │
detection ──────┘          global(로거 · 메트릭 · 시각화) · benchmark
```

| 도메인 | 클래스 | 하는 일 |
|--------|--------|--------|
| global | `ConsoleLogger`, `MetricsCollector`, `ThreadVisualizer`, `BeanConfig` | 스레드별 색 로그, 카운터 · 타이머, 스레드 상태 출력, 빈 등록 |
| resource | `Resource`, `LockableResource`, `ResourceManager` | 공정 모드 `ReentrantLock`을 감싼 자원. 누가 잡고 누가 기다리는지(holder · waiter)를 남겨 Wait-For Graph에 쓴다 |
| detection | `WaitForGraph`, `DetectionResult`, `ThreadStateMonitor`, `DeadlockDetector` | DFS(visited + recursion stack)로 사이클 탐지, `ThreadMXBean.findDeadlockedThreadIds()`로 JVM 쪽 확인, `CompletableFuture`로 타임아웃 있는 비동기 감시 |
| scenario | `DeadlockScenario`, `ScenarioResult`, 시나리오 4개, `ScenarioRunner` | 시나리오마다 해결책 없이 · 해결책을 적용해 두 번 돌린다 |
| solution | `SolutionType`, `DeadlockSolution`, 해결책 3개, `SolutionApplier` | 해결 전략 |
| benchmark | `DeadlockBenchmark` | N회 반복해 데드락 발생률 · 평균 실행 시간 · 성공률을 센다 |

진입점은 `DeadlockLabApplication`(Spring Boot `CommandLineRunner`)이다. 코드에 적힌 사용법은 `./gradlew bootRun --args='<classic|database|nested|philosopher|benchmark|all> [--with-solution]'`이다.

## 3. 범위 — 시나리오와 해결책

| 시나리오 | 모양 | 원문이 추천한 해결책 |
|----------|------|-------------------|
| Classic AB-BA | 스레드1은 A→B, 스레드2는 B→A 순서로 락. `CountDownLatch`로 동시 시작, 5초 타임아웃 | 락 순서 고정 |
| Database row lock | 트랜잭션 두 개가 row1001 · row1002를 반대 순서로 잠근다(DB 없이 흉내만 낸다), 3초 타임아웃 | `tryLock` + 타임아웃 |
| Nested lock | `synchronized` 메서드끼리 서로 부른다 | 락 범위 줄이기(락을 놓은 뒤 외부 호출) |
| Dining philosophers | 철학자 5명이 모두 왼쪽 포크부터 집는다 | 번호가 작은 포크부터 집기(락 순서 고정) |

| 해결책 | 하는 일 | 깨는 조건(원문 설명) |
|--------|--------|-------------------|
| `LockOrderingSolution` | 자원 ID 오름차순으로 잡고 역순으로 놓는다 | 순환 대기 |
| `TryLockSolution` | 타임아웃 1초 · 재시도 3회 · 랜덤 백오프, 하나라도 실패하면 잡은 것을 모두 놓는다 | 무한 대기, 점유 대기 |
| `BankerAlgorithm` | 할당 전에 안전 상태인지 검사하고, 아니면 거부한다 | 원문은 "4가지 조건 모두 예방"이라고 적었다. 은행원 알고리즘은 조건을 깨는 예방이 아니라 회피(avoidance)라서, 배울 때 이 설명부터 확인한다 |

넣지 않은 것: 실제 DB 트랜잭션(Database 시나리오는 JVM 안의 락으로만 흉내 낸다), 데드락이 난 뒤 복구, 분산 락.

## 4. 선택지와 원문끼리 다른 곳

| 항목 | 원문 내용 | 지금 상태 |
|------|----------|----------|
| 단계 순서 | `IMPLEMENTATION_PHASES.md`는 4단계 시나리오 → 5단계 해결책, `PHASES.md`는 4단계 해결책 → 5단계 시나리오 | 두 문서의 순서가 반대다. 시나리오 클래스가 해결책 클래스를 import하므로, 해결책을 먼저 만드는 `PHASES.md` 순서가 컴파일 순서와 맞는다 |
| README의 "해결책 적용 후" 표 | Nested Lock에 "Lock Scope 최소화", Philosophers에 "Resource Ordering" | 해결책 클래스는 3개뿐이다. 앞의 둘은 시나리오 안의 Safe 버전 코드나 락 순서 고정에 해당한다 |
| 은행원 알고리즘 | 해결책으로 구현 | 추천 해결책으로 쓰는 시나리오가 없다 |
| 벤치마크 | 7단계, 선택 | `DeadlockBenchmark`는 있다. README의 `SolutionBenchmark`는 없다 |
| 아키텍처 | 도메인 최상위 + 내부 레이어, 헥사고날(포트/어댑터)은 쓰지 않음 | 정했다. 이유는 README "아키텍처 선택 이유"에 있다 |

## 5. 예상 수치

README "예상 실험 결과"와 `IMPLEMENTATION_PHASES.md` 7단계 "예상 결과"의 발생률(Classic ~95%, Database ~80%, Nested ~60%, Philosophers 100%, 해결책 적용 후 0%, TryLock 오버헤드 ~5%)은 **예상(실측 아님)** 이다. 돌려서 잰 기록이 없다. 원문끼리도 Philosophers가 100%와 ~100%로 다르다.

## 6. 학습 안내서

[`docs/archive/PHASES.md`](../archive/PHASES.md)는 0~7단계 순서대로 모든 파일의 전체 소스를 담은 따라 치기 안내서다(3,600줄 남짓). 소스가 통째로 들어 있어 ADR로 나누지 않았다(머리말 한 줄만 더했다). 지금 `src/` 코드는 이 안내서를 따라 친 것이다. 코드를 함께 읽을 때 이 안내서의 단계 순서를 따른다.

[`docs/archive/IMPLEMENTATION_PHASES.md`](../archive/IMPLEMENTATION_PHASES.md)는 같은 단계를 소스 없이 클래스 구조 · 체크포인트로 요약한 문서다. 체크포인트는 모두 미체크다.

## 7. 아직 없는 것

README가 약속하지만 저장소에 없는 것이다.

- `scenario/application/ScenarioService.java`, `benchmark/SolutionBenchmark.java`
- 테스트: `src/test/java/` 전체
- Gradle wrapper(`./gradlew`) — README의 빌드 · 실행 명령은 wrapper가 있어야 그대로 돈다
- 시나리오 클래스의 `main` — README "Quick Start"의 `java -cp ... ClassicDeadlockScenario [--with-solution | --no-solution]` 명령은 시나리오 클래스에 `main`이 없어서 그대로는 돌지 않는다. 실제 진입점은 §2의 `DeadlockLabApplication`이다
- 실행 결과: README 구조의 `results/reports/` · `results/logs/`, 발생률 측정 기록

## 8. 원문과의 대응

| 원문 (`docs/archive/`) | 절 | 이 ADR |
|------------------------|----|--------|
| `IMPLEMENTATION_PHASES.md` | 전체 구조 요약 | §2 |
| | 1단계 공통 인프라 · 2단계 Resource · 3단계 탐지 | §2 표 |
| | 4단계 시나리오 4종 | §3 첫 표 |
| | 5단계 해결 전략 3종 | §3 둘째 표 |
| | 6단계 ScenarioRunner · 애플리케이션 | §2 진입점 |
| | 7단계 벤치마크 · 예상 결과 | §2 표, §5 |
| | 단계별 의존 관계 · 체크포인트 | §4 단계 순서, §6 |
| `PHASES.md` | 0~7단계 전체 소스 | §6 학습 안내서(나누지 않음) |

## 9. 다음 할 일

1. `PHASES.md` 순서대로 코드를 함께 읽고, 시나리오마다 해결책 없이 · 적용해 한 번씩 돌린 출력을 남긴다.
2. 은행원 알고리즘 설명(§3)과 원문끼리 다른 곳(§4)을 정리한다.
3. 발생률을 재게 되면 반복 횟수 · 환경 · 원본을 남기고 §5의 예상을 측정값으로 바꾼다.

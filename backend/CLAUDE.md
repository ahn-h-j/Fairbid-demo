# Backend CLAUDE.md

> 백엔드 코드 작업 시 자동 적용되는 규칙. 루트 CLAUDE.md의 규칙을 상속하며, 여기서 더 구체적으로 정의한다.

---

## 1. Package Structure (파일 배치 규칙)

> 새 파일을 만들 때 반드시 이 구조를 따라라.

```
com.cos.fairbid.{domain}/
├── adapter/
│   ├── in/
│   │   ├── controller/     ← REST Controller
│   │   ├── dto/            ← Request/Response DTO (record)
│   │   ├── event/          ← 이벤트 리스너 (인바운드)
│   │   ├── scheduler/      ← 스케줄러
│   │   └── stream/         ← Redis Stream Consumer
│   └── out/
│       ├── persistence/    ← Entity, JpaRepository, PersistenceAdapter, Mapper
│       ├── cache/          ← Redis Cache Adapter
│       ├── event/          ← 이벤트 발행 Adapter
│       ├── stream/         ← Redis Stream Adapter
│       ├── pubsub/         ← Redis Pub/Sub Adapter
│       ├── websocket/      ← WebSocket Adapter
│       └���─ fcm/            ← Firebase Push Adapter
├── application/
│   ├─��� port/
│   │   ├── in/             ← UseCase 인터페이스
│   │   └── out/            ← Repository/외부 서비스 인터페이스
│   ├── service/            ← UseCase 구현체
│   └── dto/                ← 내부 전달용 DTO
└── domain/
    ├── {Entity}.java       ← 도메인 모델 (POJO, 비즈니스 로직 포함)
    ├── {Enum}.java         ← 도메인 열거형
    ├── policy/             ← 도메인 정책 (계산 로직)
    ├── event/              ← 도메인 이벤트
    └── exception/          ← 도메인 예외
```

### 공통 모듈 위치
```
com.cos.fairbid.common/
├─��� exception/     ← DomainException, GlobalExceptionHandler
├── response/      ← ApiResponse (공통 응답 포맷)
├── pagination/    ← CursorPage (커서 기반 페이징)
├── config/        ← 공통 설정
├── annotation/    ← 커스텀 어노테이션
└── aop/           ← AOP 인터셉터
```

---

## 2. Code Generation Patterns (코드 생성 시 따를 패턴)

### 새 UseCase 추가

```java
// 1. Port In — application/port/in/
public interface DoSomethingUseCase {
    SomeResult doSomething(SomeCommand command);
}

// 2. Service — application/service/
@Service
@RequiredArgsConstructor
public class SomeService implements DoSomethingUseCase {
    private final SomeRepositoryPort someRepositoryPort;

    @Override
    @Transactional
    public SomeResult doSomething(SomeCommand command) {
        // Domain 객체 조합으로 비즈니스 로직 수행
    }
}

// 3. Controller — adapter/in/controller/
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/some")
public class SomeController {
    private final DoSomethingUseCase doSomethingUseCase;  // UseCase로만 의존
}
```

### 새 Entity + Domain 매핑

```java
// Domain (domain/) — 순수 POJO, JPA 금지
public class Some {
    private Long id;
    private String name;
    // 비즈니스 로직 메서드
}

// Entity (adapter/out/persistence/entity/) — JPA 전용
@Entity @Table(name = "some")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SomeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
}

// Mapper (adapter/out/persistence/) — 양방향 변환
public class SomeMapper {
    public static Some toDomain(SomeEntity entity) { ... }
    public static SomeEntity toEntity(Some domain) { ... }
}
```

### 새 예외 추가

```java
// domain/exception/
public class SomeNotFoundException extends DomainException {
    public SomeNotFoundException() {
        super("SOME_NOT_FOUND", "해당 항목을 찾을 수 없습니다.");
    }
}
```

예외 네이밍: `{도메인}{상황}Exception` (예: `AuctionNotFoundException`, `BidTooLowException`)
에러 코드: `UPPER_SNAKE_CASE` (예: `AUCTION_NOT_FOUND`)

### DTO 규칙
- Request/Response DTO는 **`record`** 사용
- 내부 전달용 DTO는 `record` 또는 `@Data`
- DTO에 비즈니스 로직 금지

---

## 3. 입찰(Bid) 처리 규칙 — 성능 크리티컬

입찰은 시스템에서 가장 높은 동시성을 요구하는 영역이다. 반드시 아래 규칙을 따라라:

- **Redis가 실시간 가격의 Source of Truth** — DB의 auction 테이블에 가격을 직접 UPDATE하지 마라
- **입찰 처리**: Redis Stream → BidStreamConsumer → BidService (비동기)
- **경매 목록 조회 시**: RDB에서 경매 정보 조회 후, Redis에서 최신 가격을 오버레이
- **RDB 동기화**: 비동기로 입찰 이력을 RDB에 저장 (실시간 정합성보다 성능 우선)
- **정합성 모니터링**: `BidConsistencyChecker`가 Redis vs RDB를 주기적으로 비교

---

## 4. 도메인 간 통신

도메인 간 통신은 두 가지 방식이 혼용된다:

### 1) Port Out 직접 참조 (주로 사용)
다른 도메인의 Port Out 인터페이스를 직접 주입받아 사용한다. 현재 프로젝트의 주된 방식이다.
```
winning/Service → auction.AuctionRepositoryPort (직접 주입)
trade/Service → auction.AuctionRepositoryPort (직접 주입)
```

### 2) Spring ApplicationEvent (일부 사용)
비동기 알림 등 느슨한 결합이 필요한 곳에서만 사용한다.
```
입찰 발생 → BidPlacedEvent → BidEventListener → 알림 전송
경매 종료 → AuctionClosedEvent → AuctionClosedEventListener → 낙찰 처리
```

> 인프로세스 이벤트다 (같은 JVM). 메시지 브로커(Kafka 등)가 아니다.

### 이벤트 파일 배치 규칙
- 도메인 이벤트 클래스: `{domain}/domain/event/`
- 이벤트 발행: `adapter/out/event/`
- 이벤트 수신: `adapter/in/event/` 또는 `application/event/`

---

## 5. 예외 처리 규칙

- 모든 커스텀 예외는 `DomainException`을 상속
- `GlobalExceptionHandler`에서 일괄 처리 — **Controller에서 try-catch 하지 마라**

---

## 6. 모니터링 메트릭 네이밍

커스텀 메트릭을 추가할 때는 기존 네이밍 패턴을 따라라:

```
fairbid_{도메인}_{측정항목}_{단위}
```

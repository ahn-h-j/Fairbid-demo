# 05. Conventions — PR 통과시키려면 알아야 할 것

> 이 프로젝트는 가드레일이 강함. 위반 시 빌드/CI에서 자동 fail. 미리 숙지 필요.

## 절대 금지 (NEVER)

| 금지 사항 | 이유 / 대안 |
|----------|------------|
| Domain에 JPA 어노테이션 | Domain은 순수 POJO. Entity로 변환은 Mapper |
| Controller에서 Repository 직접 호출 | 반드시 UseCase(Port In) 통해서 |
| Service에서 Entity 직접 반환 | Mapper로 Domain ↔ Entity 변환 |
| Entity를 Controller에 노출 | Response DTO로 변환 |
| `@Autowired` 필드 주입 | `@RequiredArgsConstructor` 생성자 주입 |
| `RuntimeException` 직접 throw | `DomainException` 상속한 커스텀 예외 |
| 클라이언트 시간 신뢰 | 서버 시간(`LocalDateTime.now()`) |
| `auction` 테이블에 가격 직접 UPDATE | Redis가 SoT. Stream으로 동기화 |
| H2 테스트 DB | TestContainers (MySQL, Redis) |

## 코드 패턴

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
    public SomeResult doSomething(SomeCommand command) { ... }
}

// 3. Controller — adapter/in/controller/
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/some")
public class SomeController {
    private final DoSomethingUseCase doSomethingUseCase;
}
```

### Entity + Domain 분리
```java
// Domain — domain/  (POJO, JPA 금지)
public class Some { ... }

// Entity — adapter/out/persistence/entity/  (JPA 전용)
@Entity @Table(name = "some")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SomeEntity { ... }

// Mapper — adapter/out/persistence/  (양방향)
public class SomeMapper {
    public static Some toDomain(SomeEntity e) { ... }
    public static SomeEntity toEntity(Some d) { ... }
}
```

### 예외
```java
// domain/exception/
public class SomeNotFoundException extends DomainException {
    public SomeNotFoundException() {
        super("SOME_NOT_FOUND", "해당 항목을 찾을 수 없습니다.");
    }
}
```
- 네이밍: `{도메인}{상황}Exception` (예: `BidTooLowException`)
- 에러 코드: `UPPER_SNAKE_CASE`
- Controller에서 try-catch 금지 — `GlobalExceptionHandler`가 처리

### DTO
- Request/Response: `record`
- 내부 전달용: `record` 또는 `@Data`
- DTO에 비즈니스 로직 금지

## 가드레일 (자동 검증)

| 도구 | 무엇 | 실패 시 |
|------|------|--------|
| **Checkstyle** | 네이버 컨벤션 + 커스텀, `maxWarnings=0` | 빌드 실패 |
| **SpotBugs** | null 참조, 리소스 누수 (effort=MAX) | 빌드 실패 |
| **ArchUnit** | 헥사고날 레이어 위반 | 테스트 실패 |
| **/check-arch** | 수동 검증 스킬 | 사용자 호출 시 |

빌드 한 번에 다 돌려보려면: `cd backend && ./gradlew build`

## 커밋 / PR

- **커밋은 반드시 `/commit` 스킬 사용** — 직접 `git commit` 금지
  - 레이어별로 자동 분할 (domain → infra → api)
  - 프론트는 별도 커밋
  - 커밋 전 Checkstyle + ArchUnit 자동 실행
- **PR은 반드시 `/pr` 스킬 사용**
- **브랜치 네이밍**: `feat/{feature-name}` (브랜치 생성 전 사용자 컨펌)
- **리뷰는 `code-reviewer` 에이전트** — 결함/도메인/JPA 3관점 통합

## 테스트

| 종류 | 대상 | 도구 |
|------|------|------|
| Unit | Domain (계산 로직: 입찰 단위, 연장 등) | JUnit |
| 인수 (BDD) | Service + Controller | Cucumber + Given-When-Then |
| 통합 | DB/Redis 흐름 | TestContainers (MySQL, Redis) |

규칙:
- Unhappy Path(예외) 최소 1개 이상
- Mock은 외부 API(결제 등)에만 — 그 외는 실제 객체
- 커버리지 숫자보다 **핵심 비즈니스 로직 커버 우선**
- H2 절대 금지

## 설계 우선순위

1. 확장성, 테스트 용이성
2. 유지보수성, 가독성
3. 성능 (단, 가독성 때문에 성능이 급격히 저하되면 성능 우선)

## 모니터링 메트릭 네이밍

```
fairbid_{도메인}_{측정항목}_{단위}
```
예: `fairbid_bid_total`, `fairbid_auction_extension_total`

## 도메인 간 통신

1. **Port Out 직접 참조** (주류) — `winning/Service → auction/AuctionRepositoryPort`
2. **Spring ApplicationEvent** — 알림 같은 느슨한 결합
   - 도메인 이벤트: `{ctx}/domain/event/`
   - 발행: `adapter/out/event/`
   - 수신: `adapter/in/event/`

## 추가 자료
- 루트 `CLAUDE.md` — AI 에이전트용 룰 (사람도 읽기 좋음)
- `backend/CLAUDE.md` — 백엔드 전용 상세 룰
- `.claude/skills/` — 자동화된 워크플로우들

# CLAUDE.md

> 이 파일은 AI 에이전트의 **런타임 설정 파일**이다. 프로젝트에 참여하는 모든 AI는 이 규칙을 자동으로 따른다.

---

## 1. Role & Behavior

- 너는 Java/Spring 생태계에 정통한 미들~시니어 백엔드 개발자다.
- 불확실한 요구사항이 있으면 멋대로 추측하지 말고 나에게 질문해라.
- 주석은 상세하게 작성해라.
- 응답은 간결하게, 추가 요청이 있으면 그때 상세하게 설명해라.
- 기능 개발 브랜치: `feat/{feature-name}` — 브랜치 생성 전 사용자에게 이름 확인
- **커밋할 때 반드시 `/commit` 스킬을 사용해라** — 직접 git commit 하지 마라
- **PR 생성할 때 반드시 `/pr` 스킬을 사용해라**
- **코드 리뷰할 때 `code-reviewer` 에이전트를 사용해라** — 헥사고날/도메인/JPA 3관점 통합 리뷰
- **커밋 시 Checkstyle + ArchUnit 자동 검증** — `/commit` 스킬이 커밋 전 자동 실행
- **하네스 개선할 때 `/evolve` 스킬을 사용해라** — 가드레일 실패 패턴 분석 + 규칙 보강
- **코드 정리할 때 `/gc` 스킬을 사용해라** — dead code, 고아 파일 탐지 + 정리

### 우선순위
1. 확장성, 테스트 용이성
2. 유지보수성, 가독성
3. 성능 (단, 가독성 때문에 성능이 급격히 저하되면 성능 우선)

---

## 2. Project Overview

- **컨셉**: 적정가를 모르는 판매자가 시세 고민 없이 적정가 이상을 받을 수 있는 실시간 경쟁 입찰 시스템
- **슬로건**: "호구 없는 경매" — 깎이는 중고 거래가 아니라 올라가는 경매 거래

### Bounded Contexts
| Context | 도메인 | 역할 |
|---------|--------|------|
| Identity | User, Auth | 계정, 인증/인가 (OAuth2 + JWT) |
| Auction | Auction | 경매 물품 등록/관리 |
| Bidding | Bid | 실시간 입찰 (Redis Stream 기반) |
| Winning | Winning | 낙찰, 경매 종료, 2순위 승계 |
| Trade | Trade, Delivery, DirectTrade | 거래 방식 선택, 배송/직거래 |
| Support | Notification | 알림 (WebSocket, FCM, In-App) |
| Admin | Admin | 관리자 대시보드, 통계 |

### 핵심 비즈니스 규칙
- 경매 기간: 24시간 / 48시간 선택
- 첫 입찰 후 수정 불가, 취소 시 패널티
- 입찰 단위: 가격 구간별 차등 (500원 ~ 50,000원)
- 경매 연장: 종료 5분 전 입찰 시 5분 연장, 3회마다 입찰 단위 50% 증가
- 즉시 구매: 1시간 최종 입찰 기회 제공, 입찰가가 90% 이상이면 비활성화
- 낙찰 후 3시간 내 미결제 시 노쇼, 3회 경고 시 차단
- 2순위 낙찰자 로직 존재
- **입찰 가격은 Redis가 Source of Truth** — auction 테이블에 직접 UPDATE 금지

---

## 3. Architecture Rules (절대 규칙)

> 이 섹션의 규칙을 위반하는 코드는 어떤 경우에도 생성하지 마라.

### 헥사고날 아키텍처 — 의존성 방향

```
Controller → UseCase(Port In) → Service → Domain
                                    ↓
                              Port Out(Interface)
                                    ↓
                              Adapter Out(Implementation)
```

### 절대 금지 사항 (NEVER)
- **Domain에 JPA 어노테이션 사용 금지** — Domain은 순수 POJO
- **Controller에서 Repository 직접 호출 금지** — 반드시 UseCase를 통해서만
- **Service에서 Entity 직접 반환 금지** — Mapper로 Domain ↔ Entity 변환
- **Mapper 없이 Entity ↔ Domain 직접 변환 금지**
- **Entity를 Controller에 노출 금지** — Response DTO로 변환
- **`@Autowired` 필드 주입 금지** — `@RequiredArgsConstructor` 생성자 주입만 사용
- **`RuntimeException` 직접 throw 금지** — 커스텀 예외(`DomainException` 상속) 사용
- **클라이언트 시간 신뢰 금지** — 서버 시간(`LocalDateTime.now()`) 기준 통일
- **auction 테이블에 입찰 가격 직접 UPDATE 금지** — Redis가 실시간 가격 Source of Truth
- **H2 테스트 DB 사용 금지** — TestContainers(MySQL, Redis) 사용

### 의존성 규칙 (Layer Dependencies)
| 레이어 | 의존 가능 | 의존 불가 |
|--------|-----------|-----------|
| Domain | 없음 (순수 POJO) | 모든 외부 기술 |
| Port In (UseCase) | Domain | Adapter, Entity |
| Port Out (Interface) | Domain | Adapter, Entity |
| Service | Domain, Port Out | Adapter, Entity, Controller |
| Controller | UseCase(Port In), DTO | Service 직접 호출, Domain, Entity |
| Adapter Out | Port Out, Entity, Mapper | Domain 직접 변환 |
| Entity | JPA 기술만 | Domain |

---

## 4. Testing Rules

- **Unit Test**: Domain 레이어 (복잡한 계산 로직 — 입찰 단위 계산, 경매 연장 등)
- **인수 테스트 (BDD)**: Service + Controller — Cucumber + Given-When-Then
- **TestContainers** 필수 (MySQL, Redis) — H2 금지
- Unhappy Path(예외 케이스) 최소 1개 이상 포함
- Mock은 외부 API(결제 등)에만 허용, 그 외는 실제 객체
- 커버리지 숫자보다 핵심 비즈니스 로직 커버 우선

---

## 5. Reference Docs

코드가 Source of Truth. 비즈니스 규칙은 Domain 코드, API는 Controller, 스키마는 Entity를 참조.

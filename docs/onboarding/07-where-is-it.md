# 07. Where Is It? — "X 어디 있지?" 인덱스

## 개념별 위치

| 찾는 것 | 위치 |
|---------|------|
| 입찰 단위 계산 (가격 구간별) | `auction/domain/policy/PriceBracket.java`, `BidIncrementPolicy.java` |
| 경매 연장 정책 (5분 룰) | `auction/domain/policy/AuctionExtensionPolicy.java` |
| 입찰 처리 (Lua 스크립트 호출) | `bid/application/service/BidService.java` |
| Lua 스크립트 본문 | `bid/adapter/out/cache/RedisBidCacheAdapter.java` (또는 `resources/scripts/`) |
| Stream Consumer (RDB 동기화) | `bid/adapter/in/stream/BidStreamConsumer.java` |
| 정합성 체크 (Redis vs RDB) | `bid/adapter/in/monitoring/BidConsistencyChecker.java` |
| 1순위/2순위 낙찰 결정 | `winning/domain/Winning.java` (`createFirstRank`, `createSecondRank`, `transferToSecondRank`) |
| 노쇼 처리 (응답 기한) | `winning/domain/Winning.java` (`isResponseExpired`, `RESPONSE_DEADLINE_HOURS`) |
| 경고 누적/차단 | `user/domain/User.java` (`addWarning`, `isBlocked`) |
| 알림 종류 정의 | `notification/domain/NotificationType.java` (15종 enum) |
| FCM 푸시 발송 | `notification/adapter/out/fcm/FcmPushNotificationAdapter.java` |
| WebSocket 가격 브로드캐스트 | `bid/adapter/out/event/BidEventPublisherAdapter.java` |
| OAuth provider별 처리 | `auth/adapter/out/oauth/{Kakao,Naver,Google}OAuthClient.java` |
| JWT 발급/검증 | `auth/infrastructure/security/` |
| 권한 가드 (`@RequireOnboarding`) | `common/annotation/` + `common/aop/` |
| 서버 역할 분기 (`@EnabledOnRole`) | `common/config/serverrole/` |
| 글로벌 예외 핸들러 | `common/exception/GlobalExceptionHandler.java` |
| 공통 응답 포맷 | `common/response/ApiResponse.java` |
| 커서 페이징 | `common/pagination/CursorPage.java` |

## 작업별 위치

| 하려는 일 | 어디 만들고/수정하나 |
|----------|---------------------|
| **새 REST 엔드포인트** | (1) `{ctx}/application/port/in/XxxUseCase.java` 인터페이스 → (2) `{ctx}/application/service/XxxService.java` 구현 → (3) `{ctx}/adapter/in/controller/` 추가 → (4) `dto/` Request/Response 추가 |
| **새 도메인 객체** | `{ctx}/domain/Xxx.java` (POJO) + `adapter/out/persistence/entity/XxxEntity.java` + `adapter/out/persistence/mapper/XxxMapper.java` |
| **새 비즈니스 규칙** | `{ctx}/domain/policy/XxxPolicy.java` (계산 로직 분리) |
| **새 도메인 예외** | `{ctx}/domain/exception/XxxException.java` (`DomainException` 상속) |
| **새 알림 타입** | `notification/domain/NotificationType.java` enum 추가 + 메시지 템플릿 |
| **외부 API 연동** | `{ctx}/application/port/out/XxxPort.java` 인터페이스 → `{ctx}/adapter/out/{외부}/XxxAdapter.java` |
| **새 도메인 이벤트** | `{ctx}/domain/event/XxxEvent.java` + 발행은 `adapter/out/event/`, 수신은 `adapter/in/event/` |
| **스케줄러** | `{ctx}/adapter/in/scheduler/XxxScheduler.java` (`@Scheduled` + ShedLock) |
| **Stream Consumer** | `{ctx}/adapter/in/stream/XxxStreamConsumer.java` |
| **테스트** | Domain → `src/test/.../domain/` (Unit), Service+Controller → `src/test/.../bdd/` (Cucumber) |

## 설정 파일

| 찾는 것 | 위치 |
|---------|------|
| Spring 설정 | `backend/src/main/resources/application.yml` |
| Sentinel 프로필 설정 | `backend/src/main/resources/application-sentinel.yml` |
| Gradle 의존성 | `backend/build.gradle` |
| Docker Compose | `docker-compose.yml` |
| Sentinel 설정 | `redis/sentinel.conf` |
| Prometheus 설정 | `monitoring/prometheus/prometheus.yml` |
| Grafana 프로비저닝 | `monitoring/grafana/provisioning/` |
| AI Monitor 설정 | `monitoring/ai-monitor/config.yaml` |
| Checkstyle 룰 | `backend/config/checkstyle/checkstyle.xml` |
| SpotBugs exclude | `backend/config/spotbugs/exclude-filter.xml` |
| 인프라 코드 | `infra/` |

## 자동화 / 워크플로우

| 찾는 것 | 위치 |
|---------|------|
| AI 에이전트 룰 | `CLAUDE.md` (루트), `backend/CLAUDE.md` |
| 슬래시 커맨드 | `.claude/skills/` |
| 서브에이전트 | `.claude/agents/` |
| 온보딩 키트 | `docs/onboarding/` (이 폴더) |

## 부하 테스트 / 벤치마크

| 찾는 것 | 위치 |
|---------|------|
| k6 시나리오 | `k6/` |
| AI 벤치마크 | `monitoring/ai-monitor/` 또는 `backend/.../benchmark/` |

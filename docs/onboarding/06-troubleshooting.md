# 트러블슈팅

!!! warning "이 문서는 시드만 자동 생성됨"
    실제 장애를 겪을 때마다 아래 형식으로 추가하세요.
    AI는 운영 경험이 없어 자동으로 못 만듭니다.

    ```markdown
    ## 증상 (한 줄)
    ### 원인
    ### 진단
    ### 복구
    ### 재발 방지
    ```

## 진단 도구

<div class="grid cards" markdown>

-   :material-heart-pulse: __Health__

    `GET /actuator/health`

-   :material-chart-line: __Prometheus__

    `GET /actuator/prometheus` · http://localhost:9595

-   :material-monitor-dashboard: __Grafana__

    http://localhost:3001 (admin/admin)

-   :material-connection: __WebSocket__

    `GET /actuator/wsconnections`

-   :material-message-alert: __AI Monitor__

    Discord (`DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL`)

-   :material-database-check: __정합성 체크__

    `BidConsistencyChecker` 로그

</div>

## 핵심 메트릭

| 메트릭 | 의미 | 경고 임계 |
|--------|------|----------|
| `fairbid_bid_total{result="fail"}` | 입찰 실패 건수 | 급증 시 Lua/Stream 점검 |
| `fairbid_auction_extension_total` | 연장 발생 건수 | 평소 대비 급증 |
| `http.server.requests` (p99) | API p99 응답시간 | **200ms 초과** |
| `redis_memory_used_bytes` | Redis 메모리 | maxmemory 80% 초과 |
| `hikaricp_connections_active` | DB 커넥션 사용 | 50(max) 근접 |
| `fairbid_stream_pending_count` | 처리 못 한 Stream 메시지 | 100+ |

---

## 시드 런북

??? danger "A. DB 커넥션 풀 고갈"
    **증상**: API 응답 5초+ 지연, 로그에 `HikariPool`, `Connection is not available`

    **원인 후보**

    - OSIV(`open-in-view: true`)로 요청 전체 커넥션 점유
    - 트랜잭션 길어진 Service 메서드
    - `socketTimeout`(3초) 도달 전 슬로우 쿼리

    **진단**
    ```bash
    curl localhost:8080/actuator/prometheus | grep hikaricp_connections_active
    # Hibernate SQL 로그 (DEBUG 레벨)에서 슬로우 쿼리 식별
    ```

    **복구**

    - 슬로우 쿼리 인덱스 추가 / N+1 제거
    - 임시: `maximum-pool-size` 증가 (근본 해결 X)

    **재발 방지**: OSIV 끄고 Lazy 컬렉션을 Service 레벨에서 명시 로딩

??? danger "B. Redis 장애 (Master 다운)"
    **증상**: 모든 입찰 실패, WebSocket 가격 갱신 멈춤

    **진단**
    ```bash
    docker exec sentinel-1 redis-cli -p 26379 sentinel master mymaster
    docker exec redis redis-cli ping
    ```

    **복구**

    - Sentinel quorum 2/3 자동 failover (5~10초)
    - 자동 안 되면: `redis-cli -p 26380 replicaof no one`
    - 앱: `SPRING_PROFILES_ACTIVE=sentinel` 활성화

    **재발 방지**: Sentinel 항상 3대 정상 유지, `min-replicas-to-write 1`

??? warning "C. Stream 적체 (RDB 동기화 지연)"
    **증상**: 입찰은 성공인데 RDB `bid` 테이블에 안 쌓임. `BidConsistencyChecker` 경고

    **진단**
    ```bash
    redis-cli XLEN bid:save:stream
    redis-cli XPENDING bid:save:stream bid-consumer-group
    ```

    **복구**

    - Consumer 재시작
    - Pending 메시지 수동 ACK 또는 재처리
    - DB 정상화 후 자동 재처리 (Consumer Group PENDING)

    **재발 방지**: Consumer lag 알람, DLQ 설정

??? warning "D. FCM 푸시 미발송"
    **증상**: 인앱 알림은 오는데 모바일 푸시 안 옴

    **원인 후보**: FCM 토큰 만료, 서비스 계정 키 만료, 토큰 미등록

    **복구**: 클라이언트가 토큰 재발급 후 재등록

    **재발 방지**: 토큰 만료 응답 코드 시 자동 무효화

??? warning "E. AI 어시스턴트 503"
    **증상**: `POST /api/v1/ai/auction-assist` → 503

    **원인 후보**

    - API 키 미설정 (`ANTHROPIC_API_KEY`, `GEMINI_API_KEY`)
    - API 호출 timeout (read-timeout 60s)
    - AI provider 장애

    **복구**

    - 키 재설정
    - `AI_PROVIDER` 환경변수로 provider 전환
    - 임시: 기능 비활성화 안내

    **재발 방지**: AI Monitor가 Discord로 자동 알림

??? danger "F. 경매 종료 처리 누락"
    **증상**: `scheduledEndTime` 지났는데 `status`가 아직 `BIDDING`

    **원인 후보**: 종료 스케줄러 실패, ShedLock 락 점유 후 인스턴스 다운

    **진단**: `shedlock` 테이블, 스케줄러 로그

    **복구**: ShedLock row 수동 삭제, 스케줄러 재기동

    **재발 방지**: ShedLock `lock_until` 모니터링

---

## 추가 패턴 누적용 템플릿

```markdown
??? danger "G. {증상 한 줄}"
    **원인 후보**: ...
    **진단**: ...
    **복구**: ...
    **재발 방지**: ...
```

겪을 때마다 여기 추가하면, 다음 사람이 같은 장애에 30분 안에 복구 가능.

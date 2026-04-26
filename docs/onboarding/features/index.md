# Features 카탈로그

> 사용자 행위 단위로 묶은 16개 기능 문서. 각 페이지는 동일한 템플릿 (한눈에 → 왜 → 시나리오 → 진입점 → 에러 → 영향 → 설계 → 기술 메모).

---

## 인증 / 사용자

<div class="grid cards" markdown>

-   :material-login: **[OAuth 로그인](oauth-login.md)**

    카카오/네이버/구글 1번 클릭 로그인.
    `GET /auth/oauth2/{provider}` + 콜백.

-   :material-key-chain: **[토큰 관리](token-management.md)**

    Access 갱신, Token Rotation, 재사용 감지.
    `POST /auth/refresh`, `/logout`.

-   :material-account-plus: **[온보딩](user-onboarding.md)**

    닉네임 + 전화번호 등록.
    `POST /users/me/onboarding`.

-   :material-account-cog: **[프로필](user-profile.md)**

    조회, 닉네임/배송지/계좌 수정, 탈퇴.
    `GET/PUT/DELETE /users/me`.

-   :material-history: **[내 활동 내역](my-history.md)**

    내 판매 + 내 입찰 무한스크롤.
    `GET /users/me/auctions`, `/bids`.

</div>

---

## 경매

<div class="grid cards" markdown>

-   :material-plus-box: **[경매 등록](경매-등록.md)**

    판매자가 물건 올림.
    `POST /auctions`.

-   :material-magnify: **[경매 조회](경매-목록-상세.md)**

    목록 + 상세 (Redis 가격 오버레이).
    `GET /auctions[/{id}]`.

-   :material-clock-end: **[경매 종료 / 낙찰 / 노쇼](경매-종료.md)**

    스케줄러 자동 처리, 2순위 자동 승계.
    (사용자 호출 X)

</div>

---

## 입찰

<div class="grid cards" markdown>

-   :material-gavel: **[입찰](입찰.md)**

    Redis Lua 원자적 처리, 4.5ms 응답.
    `POST /auctions/{id}/bids`.

-   :material-database-sync: **[입찰 비동기 처리](입찰-비동기처리.md)**

    Redis Stream Consumer + 정합성 체커.
    Stream Listener + 재처리 스케줄러.

</div>

---

## 거래

<div class="grid cards" markdown>

-   :material-handshake: **[거래 기본](거래-기본.md)**

    Trade 라이프사이클 (5상태) + 방식 선택 + 완료.
    `POST /trades/{id}/method`, `/complete`.

-   :material-account-switch: **[직거래](직거래.md)**

    시간 제안 / 수락 / 역제안.
    `POST /trades/{id}/direct/*`.

-   :material-truck-delivery: **[택배](택배.md)**

    배송지 → 입금 → 발송 → 수령 (5단계).
    `POST /trades/{id}/delivery/*`.

</div>

---

## 그 외

<div class="grid cards" markdown>

-   :material-bell-ring: **[알림](알림.md)**

    16종 알림, 3채널 (FCM + 인앱 + WebSocket).
    `GET /notifications`, EventListener.

-   :material-shield-account: **[관리자 대시보드](관리자.md)**

    통계 + 경매/유저 관리.
    `GET /admin/*` (ADMIN 전용).

-   :material-robot: **[AI 어시스턴트](AI-경매-어시스턴트.md)**

    시작가 추천 + 마케팅 설명. RAG 패턴.
    `POST /ai/auction-assist`.

</div>

---

## 새 기능 문서 만들거나 갱신

```bash
/explain <기능>                      # 새로 또는 기존 갱신
/onboarding refresh features/X.md    # 코드 변경 후 부분 갱신
```

각 문서 템플릿 명세는 [`.claude/agents/onboarding-architect.md`](https://github.com/ahn-h-j/Fairbid/blob/main/.claude/agents/onboarding-architect.md) 참고.

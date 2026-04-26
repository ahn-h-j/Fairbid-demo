# 환경 셋업

## 사전 요구사항

<div class="grid cards" markdown>

-   :fontawesome-brands-java: __JDK 17__

-   :fontawesome-brands-node-js: __Node 18+__

-   :fontawesome-brands-docker: __Docker Compose__

-   :fontawesome-brands-git-alt: __Git__

</div>

## 실행 방법

=== "Docker Compose (가장 빠름)"

    ```bash
    cp .env.example .env  # 없으면 직접 작성
    docker-compose up -d
    curl http://localhost:8080/actuator/health
    ```

    **포함된 컴포넌트**: 백엔드 + 프론트 + MySQL + Redis(Master+2Slave+3Sentinel) + Prometheus + Grafana + AI Monitor

=== "직접 실행 (개발 모드)"

    ```bash
    # 1. MySQL + Redis만 띄우기
    docker-compose up -d mysql redis

    # 2. 백엔드
    cd backend
    ./gradlew bootRun

    # 3. 프론트 (별도 터미널)
    cd frontend
    npm install
    npm run dev
    ```

## 접속

| 서비스 | URL |
|--------|-----|
| 백엔드 | http://localhost:8080 |
| 프론트 | http://localhost:3000 |
| Grafana | http://localhost:3001 (admin / admin) |
| Prometheus | http://localhost:9595 |

## 환경변수

!!! danger "필수 (없으면 부팅 실패)"
    ```bash
    SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3308/fairbid?serverTimezone=Asia/Seoul
    SPRING_DATASOURCE_USER=fairbid
    SPRING_DATASOURCE_PASSWORD=...
    SPRING_DATASOURCE_ROOT_PASSWORD=...
    SPRING_DATASOURCE_DATABASE=fairbid
    ```

!!! note "OAuth (없으면 해당 provider 로그인 비활성)"
    ```bash
    KAKAO_CLIENT_ID=...
    KAKAO_CLIENT_SECRET=...
    NAVER_CLIENT_ID=...           # OAuth + 검색 API 같은 키 재사용
    NAVER_CLIENT_SECRET=...
    GOOGLE_CLIENT_ID=...
    GOOGLE_CLIENT_SECRET=...
    ```
    - 카카오: https://developers.kakao.com
    - 네이버: https://developers.naver.com
    - 구글: https://console.cloud.google.com

!!! note "AI 어시스턴트 (없으면 503)"
    ```bash
    ANTHROPIC_API_KEY=...           # 가격 추천 (기본)
    ANTHROPIC_MODEL=claude-sonnet-4-5
    GEMINI_API_KEY=...              # 설명 생성
    OPENAI_API_KEY=...              # 옵션 (AI_PROVIDER=openai)
    AI_PROVIDER=claude              # claude | openai
    ```

!!! note "프론트 (Cloudinary 이미지 업로드)"
    ```bash
    VITE_CLOUDINARY_CLOUD_NAME=...
    VITE_CLOUDINARY_UPLOAD_PRESET=...
    ```

??? tip "옵션 (보통 기본값으로 충분)"
    ```bash
    JWT_SECRET_KEY=...                    # 미설정 시 dev 기본값 (운영 금지)
    ADMIN_EMAILS=admin@test.com           # 콤마 구분, ADMIN role 부여
    COOKIE_SECURE=false                   # 로컬 dev는 false
    SERVER_ROLE=all                       # api | ws | all
    DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL=   # AI 모니터링 알림
    ```

자세한 키 사용처: [02-infra.md](02-infra.md)

## 빌드

```bash
cd backend
./gradlew build              # 컴파일 + 테스트 + Checkstyle + SpotBugs
./gradlew test               # 테스트만
./gradlew bootJar            # 실행 가능한 jar
```

빌드 결과: `backend/build/libs/FairBid-0.0.1-SNAPSHOT.jar`

## DDL 자동 생성

`spring.jpa.hibernate.ddl-auto: update` (기본). **마이그레이션 도구 없음**.
Entity 변경 → 재시작이면 끝.

!!! warning "운영 환경 주의"
    Entity 변경 시 인덱스 누락 위험. 추후 Flyway/Liquibase 도입 검토.

## 헬스체크

| URL | 용도 |
|-----|------|
| `GET /actuator/health` | 전체 상태 (UP/DOWN + 디테일) |
| `GET /actuator/prometheus` | Prometheus 메트릭 |
| `GET /actuator/wsconnections` | WebSocket 활성 연결 수 |
| `GET /actuator/info` | 빌드 정보 |

## 자주 막히는 곳

!!! failure "포트 충돌"
    MySQL 3306 점유 시 docker-compose는 **3308**로 매핑됨. JDBC URL 확인.

!!! failure "Sentinel 프로필"
    `application-sentinel.yml` 활성 시 Redis 직접 연결 → Sentinel 토폴로지 전환.
    **로컬은 기본 프로필 권장.**

!!! failure "OSIV"
    `open-in-view: true`는 Lazy 컬렉션 Controller 로딩용.
    고부하 시 HikariCP 고갈 가능성 있음.

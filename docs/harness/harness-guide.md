# FairBid 하네스 운영 가이드

> AI 에이전트가 실수할 수 없는 환경을 만드는 시스템.

---

## 개발 흐름에서 하네스가 개입하는 시점

```
설계 → 코딩 → /commit → /pr → CI
 ↑       ↑        ↑        ↑      ↑
 ⓪       ①        ②       ③     ④
```

---

## ⓪ 설계 — `/spec-interview`

기능 구현 전 설계 파트너. 요구사항을 질문으로 구체화한다.

산출물:
- `docs/spec/{기능명}-SPEC.md` — 비즈니스 규칙, API, 엣지케이스
- `docs/feature/{도메인}/{기능명}.mmd` — 시퀀스 다이어그램
- 작업 분해 — AI가 한 번에 집중할 수 있는 커밋 단위

---

## ① 코딩 중 — 실시간 Hook

| Hook | 트리거 | 동작 |
|------|--------|------|
| `protect-files.sh` | Edit/Write로 보호 파일 수정 시 | 차단 + feedback-log 기록 |
| `protect-deps.sh` | npm install 등 의존성 추가 시 | 차단 + feedback-log 기록 |

보호 대상: SecurityConfig, JwtTokenProvider, application.yml, docker-compose.yml, checkstyle.xml, ArchUnit 테스트 등

설정: `.claude/settings.json`

---

## ② 커밋 시 — `/commit` 스킬

가드레일 검증 → 계층별 분리 커밋.

| 도구 | 잡는 것 |
|------|---------|
| Checkstyle | 네이버 컨벤션 (네이밍, import, @Autowired 금지, RuntimeException 직접 throw 금지) |
| SpotBugs | 기계적 버그 (null, 리소스 누수) |
| ArchUnit | 헥사고날 레이어 의존성 방향 (9개 규칙) |
| ESLint | Airbnb + FairBid 커스텀 (div onClick 금지, transition:all 금지) |
| Prettier | 자동 포매팅 |

하나라도 실패하면 커밋 차단, 수정 후 재실행.

---

## ③ PR 생성 시 — `/pr` 스킬 + code-reviewer 에이전트

PR 생성 전 code-reviewer가 3관점으로 리뷰:

| 관점 | 잡는 것 |
|------|---------|
| Code Defects | 엣지케이스, 인가 누락, 레이스 컨디션, 입력 검증, 로깅, API 설계 |
| Domain Rules | 6개 도메인 체크리스트 + 크로스 컨텍스트 흐름 |
| Persistence | N+1, 트랜잭션 범위, 낙관적 락, readOnly |

- **Block** → PR 생성 중단
- **Warning** → PR 본문에 포함, 계속 진행
- **Warning/Block 시 feedback-log.md에 기록** → `/evolve` 데이터 소스

---

## ④ CI — GitHub Actions

```
push / PR → ci.yml
  ├── ./gradlew build -x test
  ├── ./gradlew test (ArchUnit + Unit + BDD)
  └── Docker 이미지 빌드 (PR 시)
```

커밋 전에 이미 검증했으므로 CI는 최종 안전망 역할.

---

## 피드백 루프 — `/evolve`

feedback-log.md의 open 항목을 분석하여 하네스를 강화한다.

```
feedback-log.md (open)
  ↓
/evolve → 패턴 분류 → 변경 제안 → 승인 후 적용 → resolved
```

데이터 소스:
- Hook 차단 시 자동 기록
- code-reviewer Warning/Block 시 자동 기록

언제 부르나:
- 같은 실수가 반복된다고 느낄 때
- 가드레일에서 잡히지 않는 문제를 발견했을 때

---

## 코드 위생 — `/gc`

| Part | 역할 |
|------|------|
| A. Dead Code | 미참조 클래스, 고아 Entity/Mapper/Port, 주석 코드, 미사용 컴포넌트 |
| B. 규칙↔코드 불일치 | CLAUDE.md 규칙과 실제 코드가 다른 것 |

언제 부르나:
- 리팩토링 후 잔재가 남았을 때
- 기능 삭제 후 관련 코드가 남아있을 때

---

## 책임 분담

| 영역 | 린터 (자동) | code-reviewer (AI) | 피드백 루프 |
|------|------------|-------------------|------------|
| 코드 스타일 | Checkstyle, ESLint, Prettier | — | — |
| 아키텍처 | ArchUnit (9규칙) | — | — |
| 기계적 버그 | SpotBugs | — | — |
| 코드 결함 | — | 관점 1 | Warning/Block → log |
| 도메인 규칙 | — | 관점 2 | Warning/Block → log |
| JPA 설계 | — | 관점 3 | Warning/Block → log |
| 파일 보호 | Hook | — | 차단 → log |
| 의존성 보호 | Hook | — | 차단 → log |
| Dead code | — | — | /gc |
| 규칙 동기화 | — | — | /gc |
| 하네스 진화 | — | — | /evolve |

---

## 스킬 사용 규칙

| 상황 | 스킬 |
|------|------|
| 기능 설계할 때 | `/spec-interview` |
| 커밋할 때 | `/commit` |
| PR 만들 때 | `/pr` |
| 하네스 개선할 때 | `/evolve` |
| 코드 정리할 때 | `/gc` |
| 이슈 만들 때 | `/issue` |

---

## 파일 맵

```
FairBid/
├── CLAUDE.md                              ← 루트 컨텍스트
├── backend/
│   ├── CLAUDE.md                          ← 백엔드 컨텍스트
│   ├── build.gradle                       ← Checkstyle + SpotBugs + ArchUnit 설정
│   ├── config/
│   │   ├── checkstyle/checkstyle.xml      ← Checkstyle 규칙
│   │   └── spotbugs/exclude-filter.xml    ← SpotBugs 제외 필터
│   └── src/test/.../architecture/
│       └── HexagonalArchitectureTest.java ← ArchUnit 9개 규칙
├── frontend/
│   ├── CLAUDE.md                          ← 프론트엔드 컨텍스트
│   ├── eslint.config.js                   ← ESLint 규칙
│   └── .prettierrc                        ← Prettier 설정
├── .claude/
│   ├── settings.json                      ← Hook 설정
│   ├── hooks/
│   │   ├── protect-files.sh               ← 보호 파일 차단
│   │   └── protect-deps.sh                ← 의존성 추가 차단
│   ├── agents/
│   │   └── code-reviewer.md               ← 3관점 코드 리뷰 에이전트
│   └── skills/
│       ├── commit/SKILL.md                ← 계층별 분리 커밋
│       ├── pr/SKILL.md                    ← PR 생성 + 리뷰
│       ├── spec-interview/SKILL.md        ← 기능 설계
│       ├── evolve/SKILL.md                ← 피드백 루프
│       ├── gc/SKILL.md                    ← 코드 위생
│       ├── issue/SKILL.md                 ← 이슈 생성
│       └── init/SKILL.md                  ← 세션 시작
├── .github/workflows/
│   └── ci.yml                             ← CI (빌드 + 테스트 + Docker)
└── docs/harness/
    ├── harness-guide.md                   ← 이 문서
    └── feedback-log.md                    ← 실패 자동 기록
```

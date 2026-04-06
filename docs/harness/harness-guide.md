# FairBid 하네스 운영 가이드

> AI 에이전트가 실수할 수 없는 환경을 만드는 시스템.
> md는 의도를 잡고, hooks는 행동을 잡고, CI는 결과를 잡는다.

---

## 구조 개요

```
[실시간 — 에이전트 작업 중]
  Claude Code hooks → 보호 파일 수정 차단, 의존성 추가 차단

[커밋 시]
  pre-commit hook → Checkstyle + ESLint → 실패 시 자동 수정 후 재시도

[push/PR 시]
  CI (GitHub Actions) → ArchUnit + 테스트 → 실패 시 머지 차단

[사용자 판단 — 필요할 때]
  /evolve → 실패 로그 분석 → 하네스 변경 제안
  /gc → dead code + 나쁜 패턴 + 규칙 불일치 정리
```

---

## 4개 기둥

### 1. 컨텍스트 (부탁)

AI가 매 작업마다 자동으로 참조하는 런타임 설정 파일.

| 파일 | 로드 시점 | 내용 |
|------|-----------|------|
| `CLAUDE.md` | 세션 시작 시 항상 | 비즈니스 규칙, 아키텍처 절대 금지, 스킬 사용 강제 |
| `backend/CLAUDE.md` | 백엔드 파일 접근 시 | 파일 배치 규칙, 코드 생성 패턴, 입찰 처리 규칙 |
| `frontend/CLAUDE.md` | 프론트 파일 접근 시 | 파일 배치 규칙, 금지 패턴, 성능/폼/접근성 규칙 |

**원칙**: 코드에서 유추 가능한 건 안 적는다. 에이전트의 행동을 제어하는 규칙만 적는다.

---

### 2. 가드레일 (강제)

규칙 위반을 물리적으로 차단한다. 부탁이 아니라 차단.

#### 실시간 — Claude Code hooks
| 파일 | 트리거 | 동작 |
|------|--------|------|
| `.claude/hooks/protect-files.sh` | Edit/Write 시 | SecurityConfig, JwtTokenProvider, application.yml 등 → 사용자 승인 요청 |
| `.claude/hooks/protect-deps.sh` | Bash 시 | `npm install` 등 감지 → 사용자 승인 요청 |

설정: `.claude/settings.json`

#### 커밋 전 — pre-commit hook
| 도구 | 규칙 |
|------|------|
| Checkstyle | `@Autowired` 금지, `RuntimeException` 직접 throw 금지, `System.out` 금지, 와일드카드 import 금지 |
| ESLint | `div onClick` 금지, `img alt` 필수, 접근성 규칙 |

설정: `.git/hooks/pre-commit`, `backend/config/checkstyle/checkstyle.xml`, `frontend/eslint.config.js`

#### CI — GitHub Actions
| 도구 | 규칙 |
|------|------|
| ArchUnit | 헥사고날 의존성 방향 9개 규칙 (Domain→JPA 금지, Controller→Repository 금지 등) |
| 테스트 | 단위 + BDD 인수 테스트 |

설정: `.github/workflows/ci.yml`, `backend/src/test/.../HexagonalArchitectureTest.java`

#### 가드레일 실패 시
에이전트가 에러를 읽고 **스스로 수정 후 재시도**한다. 사람이 개입하지 않아도 된다.

---

### 3. 진화 (성장) — `/evolve`

실수에서 배워서 하네스를 강화한다.

#### 자동 기록
| 실패 출처 | 기록 위치 |
|-----------|-----------|
| Claude Code hook 차단 | `docs/harness/feedback-log.md` |
| pre-commit 실패 | `docs/harness/feedback-log.md` |
| CI 테스트 실패 | GitHub Issues (`harness-failure` 라벨) |

#### 사용법
```
/evolve
```

1. 로컬 로그 + GitHub Issue 읽기
2. 패턴 분석 (같은 위반 반복? 새로운 위반? 가드레일 미비?)
3. 하네스 변경 제안 (CLAUDE.md 보강, 가드레일 규칙 추가 등)
4. 사용자 승인 후 적용
5. 반영된 로그 resolved, Issue close

#### 언제 부르나
- 같은 실수가 반복된다고 느낄 때
- 가드레일에서 잡히지 않는 문제를 발견했을 때
- 한동안 안 불렀을 때 (로그가 쌓여있을 수 있음)

---

### 4. 위생 (청소) — `/gc`

에이전트가 남긴 기술 부채를 정리한다.

#### 사용법
```
/gc              # 전체 검사
/gc backend      # 백엔드만
/gc frontend     # 프론트엔드만
```

#### 검사 항목
| Part | 역할 |
|------|------|
| A. Dead Code | 미참조 클래스, 고아 Entity/Mapper/Port, 주석 코드, 미사용 컴포넌트 |
| B. 나쁜 패턴 전파 | 가드레일 위반이 코드에 남아있는 것 → 수정 + 가드레일 미비 시 `/evolve`에 넘김 |
| C. 규칙↔코드 불일치 | CLAUDE.md 규칙과 실제 코드가 다른 것 |

#### `/gc`와 `/evolve` 연결
```
/gc가 나쁜 패턴 발견 → 코드 수정 + feedback-log에 기록
/evolve가 로그 읽음 → 가드레일 규칙 추가

치우고(/gc) → 막는다(/evolve)
```

#### 언제 부르나
- 리팩토링 후 잔재가 남았을 때
- 코드가 지저분하다고 느낄 때
- 기능 삭제 후 관련 코드가 남아있을 때

---

## 스킬 사용 규칙

CLAUDE.md에 명시되어 있으며, 에이전트가 반드시 따른다:

| 상황 | 스킬 |
|------|------|
| 커밋할 때 | `/commit` |
| PR 만들 때 | `/pr` |
| 테스트 실행할 때 | `/test` |
| 아키텍처 검증할 때 | `/check-arch` |
| 하네스 개선할 때 | `/evolve` |
| 코드 정리할 때 | `/gc` |

---

## 파일 맵

```
FairBid/
├── CLAUDE.md                              ← 루트 컨텍스트
├── backend/
│   ├── CLAUDE.md                          ← 백엔드 컨텍스트
│   ├── build.gradle                       ← ArchUnit + Checkstyle 설정
│   ├── config/checkstyle/checkstyle.xml   ← Checkstyle 규칙
│   └── src/test/.../architecture/
│       └── HexagonalArchitectureTest.java ← ArchUnit 테스트
├── frontend/
│   ├── CLAUDE.md                          ← 프론트엔드 컨텍스트
│   └── eslint.config.js                   ← ESLint 규칙
├── .claude/
│   ├── settings.json                      ← Claude Code hooks 설정
│   ├── hooks/
│   │   ├── protect-files.sh               ← No-touch 파일 보호
│   │   └── protect-deps.sh                ← 의존성 추가 감지
│   └── skills/
│       ├── evolve/SKILL.md                ← 진화 스킬
│       └── gc/SKILL.md                    ← 위생 스킬
├── .git/hooks/
│   └── pre-commit                         ← 커밋 전 가드레일
├── .github/workflows/
│   └── ci.yml                             ← CI 가드레일 + Issue 자동 생성
└── docs/harness/
    ├── harness-guide.md                   ← 이 문서
    └── feedback-log.md                    ← 실패 자동 기록
```

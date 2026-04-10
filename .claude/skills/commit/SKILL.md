---
name: commit
description: Use this skill when the user wants to commit changes. Commits are split by layer (domain → infra → api) and frontend is committed separately.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep
argument-hint: [커밋 메시지 힌트 (선택)]
---

# 커밋 생성

$ARGUMENTS

## 필수 규칙

1. **계층별 분리 커밋**: domain → infra → api 순서
2. **프론트엔드 별도 커밋**: frontend/ 변경은 반드시 분리
3. **Co-Authored-By 금지**

## Step 0: 가드레일 검증 (커밋 전 필수)

변경된 영역에 따라 해당 검사를 실행:

**backend/ 변경 시:**
```bash
cd backend && ./gradlew checkstyleMain spotbugsMain test --tests "*ArchitectureTest*" --no-daemon -q
```
- Checkstyle 실패 → 스타일 위반 수정 후 재실행. 커밋 진행 금지.
- SpotBugs 실패 → 버그 패턴 수정 후 재실행. 커밋 진행 금지.
- ArchUnit 실패 → 헥사고날 아키텍처 위반 수정 후 재실행. 커밋 진행 금지.

**frontend/ 변경 시:**
```bash
cd frontend && npm run lint && npm run format:check
```
- ESLint 실패 → `npm run lint:fix`로 자동 수정 시도 후 재검사. 커밋 진행 금지.
- Prettier 실패 → `npm run format`으로 자동 포매팅 후 재검사. 커밋 진행 금지.

**둘 다 통과** → Step 1로 진행.
**docs만 변경** → 이 단계 건너뛰기.

## Step 1: 변경사항 분석

```bash
git status
git diff --stat
```

파일을 아래 기준으로 분류:

| 경로 패턴 | 계층 |
|----------|------|
| `*/domain/**` | domain |
| `*/application/**` | application |
| `*/adapter/out/**` | infra |
| `*/adapter/in/**` | api |
| `frontend/**` | frontend |
| `docs/**` | docs |
| `**/test/**` | test |

## Step 2: 커밋 순서

1. docs (문서)
2. domain (도메인 모델)
3. infra (adapter/out - persistence)
4. application (service, usecase)
5. api (adapter/in - controller, dto)
6. test (테스트)
7. frontend (별도 커밋)

## Step 3: 커밋 메시지 형식

```
<type>(<context>/<layer>): <subject>

- 변경 내용 1
- 변경 내용 2
```

### Type
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `refactor`: 리팩토링
- `docs`: 문서
- `test`: 테스트
- `chore`: 설정

### Context (비즈니스 도메인)
- `identity`: User, 인증
- `auction`: 경매
- `bidding`: 입찰
- `trade`: 거래
- `support`: 알림

### Layer
- `domain`: 도메인 모델
- `infra`: adapter/out (persistence, external)
- `api`: adapter/in (controller, dto)
- `application`: service, usecase

### Subject
- 한글, 50자 이내, 마침표 없음, 명령문

## Step 4: 커밋 실행

```bash
# 스테이징
git add <files>

# 커밋 (HEREDOC)
git commit -m "$(cat <<'EOF'
feat(trade/domain): Trade 도메인 모델 구현

- Trade 엔티티 추가
- TradeStatus enum 정의
EOF
)"
```

## Step 5: 결과 보고

```
## 커밋 완료

1. `abc1234` feat(trade/domain): Trade 도메인 모델 구현
2. `def5678` feat(trade/infra): Trade 영속성 어댑터 구현
3. `ghi9012` feat(trade/api): Trade API 엔드포인트 구현
4. `jkl3456` feat(trade): 거래 페이지 UI 구현 (frontend)

총 4개 커밋
```

## 예시: 실제 커밋 히스토리

```
feat(identity/domain): UserRole enum 및 User 도메인에 role 필드 추가
feat(identity/infra): User role 영속성 계층 구현
feat(identity/infra): JWT role 클레임 및 Spring Security 권한 검증 구현
feat(identity/api): 관리자 API 엔드포인트 구현
chore(config): ADMIN_EMAILS 환경변수 및 관리자 설정 추가
```

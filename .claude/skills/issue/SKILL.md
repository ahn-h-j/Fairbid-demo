---
name: issue
description: Use this skill when the user wants to create a GitHub issue. This handles issue creation using project templates (feature, bug, chore, refactor).
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep
argument-hint: [이슈 타입 또는 설명 (선택)]
---

# Issue 생성

$ARGUMENTS

## Step 1: 이슈 타입 결정

| 타입 | 접두사 | 라벨 | 용도 |
|------|--------|------|------|
| feat | `[FEAT]` | enhancement | 새 기능 |
| bug | `[BUG]` | bug | 버그 |
| chore | `[CHORE]` | chore | 설정/빌드 |
| refactor | `[REFACTOR]` | refactor | 코드 개선 |

사용자가 타입 지정 안 했으면 AskUserQuestion으로 질문.

## Step 2: 이슈 내용 수집

타입별 필요 정보:

**feat**: 기능 개요, 작업 목록, 구현 방식, 완료 조건
**bug**: 버그 상황, 재현 방법, 해결 목표
**chore**: 작업 내용, 필요 이유
**refactor**: 대상 코드, 문제점, 기대 효과

## Step 3: 초안 제시

```markdown
### 이슈 초안

**제목**: [{TYPE}] {제목}
**라벨**: {라벨}

---
{본문}
---

확인 후 생성할까요?
```

## Step 4: 이슈 생성

```bash
gh issue create --title "[{TYPE}] {제목}" --label "{라벨}" --body "$(cat <<'EOF'
{본문}
EOF
)"
```

## Step 5: 브랜치 생성 여부 확인

이슈 생성 후 AskUserQuestion으로 브랜치 생성 여부 질문.

생성 시:

```bash
git fetch origin && git checkout main && git pull origin main && git checkout -b {prefix}/{이슈번호}-{설명}
```

| 타입 | 브랜치 접두사 |
|------|--------------|
| feat | `feat/` |
| bug | `fix/` |
| chore | `chore/` |
| refactor | `refactor/` |

## 본문 템플릿

### feat
```markdown
## 기능 개요
> {설명}

## 작업 목록
- [ ] {작업1}
- [ ] {작업2}

## 상세 내용
- {구현 방식}

## 완료 조건
- [ ] 기능 정상 작동
- [ ] 테스트 코드 작성
```

### bug
```markdown
## 버그 상황
- {문제}

## 재현 방법
1. {단계1}
2. {단계2}

## 해결 목표
- [ ] {정상 동작}
```

### chore
```markdown
## 작업 내용
- {변경/업데이트}

## 상세 이유
- {필요 이유}
```

### refactor
```markdown
## 리팩토링 대상
- {파일/클래스}

## 개선 목표
### 기존 문제점
- {문제}

### 기대 효과
- {효과}

## 체크리스트
- [ ] 기존 기능 정상 작동
- [ ] 테스트 통과
```

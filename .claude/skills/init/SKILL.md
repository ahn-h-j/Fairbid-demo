---
name: init
description: Use this skill when the user wants to start working, begin implementation, or says things like 시작하자, 구현 시작, 작업 시작, let's start, begin coding.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep
---

# 작업 시작

## Step 1: 현재 상태 확인

```bash
git branch --show-current
```

```bash
git status --short
```

## Step 2: 핵심 문서 로드

아래 파일들을 Read로 읽기:

1. `CLAUDE.md` - 프로젝트 규칙 + 비즈니스 규칙
2. `backend/CLAUDE.md` 또는 `frontend/CLAUDE.md` - 영역별 컨벤션 (브랜치에 따라 선택)
3. `docs/todo/Todo.md` - 현재 할 일 목록

## Step 3: 브랜치명에서 도메인 추출

| 브랜치 패턴 | 관련 도메인 | 추가 확인 |
|------------|------------|----------|
| `*bid*`, `*bidding*` | bidding | 입찰 규칙 |
| `*auction*` | auction | 경매 규칙 |
| `*user*`, `*auth*` | identity | 인증 규칙 |
| `*trade*`, `*payment*` | trade | 거래 규칙 |
| `*notif*` | support | 알림 규칙 |

## Step 4: 브리핑 출력

```markdown
## 프로젝트 컨텍스트

**브랜치**: {브랜치명}
**관련 도메인**: {도메인}

### 핵심 비즈니스 규칙
- {해당 도메인의 주요 규칙 3-5개}

### 아키텍처 주의사항
- {헥사고날 아키텍처 관련 주의점}

### 준비 완료. 무엇을 구현할까요?
```

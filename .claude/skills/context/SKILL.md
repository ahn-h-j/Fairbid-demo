---
name: context
description: Use this skill when the user wants to restore context after compact, recover previous work state, or says things like 컨텍스트 복구, 맥락 복구, 이전 작업 뭐였지, what was I working on.
disable-model-invocation: false
allowed-tools: Read, Bash, AskUserQuestion
---

# 컨텍스트 복구

compact 이후 이전 작업 맥락을 복구합니다.

## Step 1: 저장된 컨텍스트 목록 확인

```bash
ls -lt ~/.claude/contexts/context-*.md 2>/dev/null | head -10
```

## Step 2: 사용자에게 선택 요청

AskUserQuestion 도구를 사용해서 어떤 컨텍스트를 복구할지 물어봅니다.

옵션:
- 목록에서 보여지는 컨텍스트 파일들 (타임스탬프 기준)
- "가장 최근 것" 옵션 포함

## Step 3: 선택된 컨텍스트 읽기

사용자가 선택한 파일을 Read로 읽습니다.

경로 예시: `C:/Users/tkgkd/.claude/contexts/context-2026-02-02_21-30-00.md`

## Step 4: 현재 상태와 비교

```bash
git status --short
```

## Step 5: 브리핑 출력

```markdown
## 이전 작업 컨텍스트 복구됨

**저장 시점**: {타임스탬프}
**브랜치**: {브랜치명}
**마지막 커밋**: {커밋 정보}

### 수정 중이던 파일
{파일 목록}

### 이어서 작업할 내용
{추론된 작업 내용}

---
컨텍스트 복구 완료. 계속 진행할까요?
```

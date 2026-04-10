---
name: pr
description: Use this skill when the user wants to create a pull request, make a PR, or push changes for review. This handles PR creation with proper formatting.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep
argument-hint: [PR 제목 힌트 (선택)]
---

# PR 생성

$ARGUMENTS

## Step 1: 상태 확인

```bash
git branch --show-current
```

```bash
git log main..HEAD --oneline 2>/dev/null || git log master..HEAD --oneline
```

```bash
git diff main...HEAD --stat 2>/dev/null || git diff master...HEAD --stat
```

```bash
git remote -v
```

## Step 1.5: 코드 리뷰 (code-reviewer 에이전트)

`code-reviewer` 에이전트를 호출하여 브랜치 전체 변경분을 리뷰한다.

- 입력: `git diff main...HEAD`
- 에이전트가 변경 파일을 분류하고 해당하는 관점(Architecture / Domain Rules / Persistence)으로 리뷰
- **Block 판정이 있으면**: 사용자에게 리뷰 결과를 보여주고 수정 여부 확인. PR 생성 중단 가능.
- **Warning 이하**: 리뷰 결과를 PR 본문에 포함하고 계속 진행.

## Step 2: Push 여부 확인

- remote tracking 없으면 → `git push -u origin {branch}`
- local이 remote보다 앞서면 → `git push`

## Step 3: PR 본문 작성

커밋 히스토리와 diff를 분석하여 아래 형식으로 초안 작성:

```markdown
## 개요
- 핵심 목적 1~2문장

## 변경 사항
- 기술적으로 유의미한 변경 상세
- "무엇이 어떻게 변경되었는지" 구체적으로

## 영향 범위
- 영향받는 모듈/API/UI
- 잠재적 부작용 (있으면)

## 테스트 관점
- 검증해야 할 로직
- 테스트 시나리오
```

## Step 4: 사용자 확인

초안을 사용자에게 제시하고 확인 받기.

## Step 5: PR 생성

```bash
gh pr create --title "{제목}" --body "$(cat <<'EOF'
## 개요
...

## 변경 사항
...

## 영향 범위
...

## 테스트 관점
...
EOF
)"
```

## 작성 규칙

- 문체: 전문적, 간결, 개조식 (~함, ~임)
- 이모지 금지
- 단순 "파일 수정" 금지 → 비즈니스 로직 변화 포착

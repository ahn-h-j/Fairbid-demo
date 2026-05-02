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
- 에이전트가 변경 파일을 분류하고 해당하는 관점(Code Defects / Domain Rules / Persistence)으로 리뷰

### 판정별 처리 정책

- **Block** → PR 생성 중단. 사용자에게 결과 보여주고 즉시 수정 진행. 수정 후 가드레일/커밋 재실행하고 Step 1.5 재호출.
- **Warning** → 항목별로 정리해서 사용자에게 보여주고 **즉시 수정이 디폴트**.
  - 기본 흐름: 코드 수정 → 가드레일/커밋 재실행 → Step 1.5 재호출(또는 사용자 컨펌 후 다음 Step) → PR 생성
  - 사용자가 명시적으로 "별도 이슈로 미룬다"고 결정한 항목만 `gh issue create`로 GitHub Issue를 **먼저 생성**하고, PR 본문 Risks/Follow-ups 섹션에 issue 번호로 링크. 즉 미루려면 issue가 우선 만들어져야 한다.
  - **금지**: Warning 항목을 issue 없이 PR 본문에 글로만 적어놓고 머지하는 것. 트래킹 책임이 없는 글은 곧 잊힌다.
- **Approve** → PR 본문 Risks/Follow-ups 섹션 자체 생략. 본문 안에 "code-reviewer 통과" 한 줄로만 표기 (또는 생략).

### 의도

하네스(code-reviewer)가 잡은 결함을 PR 본문에 "후속 이슈로 트래킹 권장"이라고 적기만 하고 머지하면, 그 글은 곧 파묻혀서 영영 안 고쳐진다. 잡은 시점에 고치는 게 디폴트다.

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

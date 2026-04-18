---
name: review-analysis
description: Use this skill when the user wants to analyze PR reviews, process CodeRabbit feedback, or document review decisions from AI reviewers.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep, Write, AskUserQuestion
argument-hint: <PR 번호 또는 URL>
---

# PR 리뷰 분석

$ARGUMENTS

## Step 1: PR 정보 수집

```bash
gh pr view {번호}
```

```bash
gh api repos/{owner}/{repo}/pulls/{번호}/comments
```

```bash
gh api repos/{owner}/{repo}/pulls/{번호}/reviews
```

## Step 2: 리뷰 내용 분석

각 리뷰 코멘트를 아래 기준으로 판정:

| 판정 | 기준 |
|------|------|
| ✅ 수용 | 명확히 맞음, 반영 필요 |
| ⚠️ 선택적 | 상황에 따라 다름, 맥락 판단 필요 |
| ❌ 거부 | 현재 프로젝트에 맞지 않음 |

### 문서화 제외 대상
- 오타/typo, import 정렬, 포맷팅
- 프로젝트 컨벤션과 일치하는 스타일 제안
- 핵심 아닌 주석 제안
- 네이밍 취향 차이
- 스코프 외 리팩터링
- 과도한 방어적 프로그래밍
- 미미한 마이크로 최적화

### Nitpick 표기
- 코드 동작 영향 없는 스타일 제안
- 현재 문제없는 성능 최적화
- 향후 확장성 제안

## Step 3: 문서 생성

`docs/review/pr-{번호}-{설명}-review.md` 파일 생성:

**중요**: "결정"과 "비고" 필드는 사용자가 직접 작성하는 항목이다. AI는 절대 이 필드를 채우지 말고 비워둘 것.

**금지**: 이 스킬의 역할은 리뷰 분석 문서 생성까지다. 문서 생성 후 코드를 수정하거나 리뷰 항목을 반영하는 행위는 절대 하지 말 것. 사용자가 결정/의견을 작성한 뒤 별도로 반영을 요청할 때만 코드를 수정한다.

```markdown
# PR #{번호} 리뷰 분석

> **PR**: {제목}
> **URL**: {URL}
> **리뷰어**: {리뷰어}
> **분석일**: {YYYY-MM-DD}

---

## {리뷰어명} 리뷰

### 1. {리뷰 제목}
- **파일**: `{파일경로}:{라인}`
- **내용**: {지적 내용 요약}
- **판정**: {✅ 수용 | ⚠️ 선택적 | ❌ 거부} {(Nitpick)}
- **AI 분석**: {기술적 배경 및 영향도}
- **결정**:
- **비고**:

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | {n}개 | {항목} |
| ❌ 거부 | {n}개 | {항목} |

```
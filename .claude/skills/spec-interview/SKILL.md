---
name: spec-interview
description: Conducts a deep, multi-round interview to clarify ambiguous requirements and produces a structured specification document. Automatically discovers requirement files and asks probing, non-obvious questions across technical implementation, UX/UI, trade-offs, edge cases, and architectural decisions.
---

# Spec Interview

$ARGUMENTS

## Step 1: 요구사항 문서 탐색

Glob으로 아래 패턴 검색:
- `**/SPEC.md`, `**/spec.md`
- `**/PRD.md`, `**/prd.md`
- `**/REQUIREMENTS.md`, `**/requirements.md`
- `**/docs/requirements/**`, `**/docs/specs/**`

발견된 파일 + 사용자 지정 파일 모두 Read.

## Step 2: 갭 분석

문서 분석 후 내부적으로 식별:
- 명시 vs 가정 구분
- 논리적 모순
- 구현 차단 누락 정보
- 암묵적 제약 (성능, 확장성, 호환성)
- 다의적 용어

## Step 3: 인터뷰 실행

AskUserQuestion으로 라운드별 3-4개 질문.

### 질문 규칙
- 문서에 있는 내용 재질문 금지
- 갭/모순/모호함에서 도출
- 트레이드오프 노출 ("X와 Y 충돌 시 우선순위?")
- 엣지케이스 탐색 ("...일 때 어떻게?")
- 기술적 불가능 도전

### 인터뷰 영역
- **핵심 의도**: 진짜 해결할 문제? 올바른 접근?
- **기술 아키텍처**: 데이터 모델, 상태 관리, API, 시스템 경계
- **UX/UI**: 인터랙션, 에러/로딩/빈 상태, 접근성
- **엣지케이스/실패**: 뭐가 깨지나? 동시성?
- **트레이드오프**: 성능 vs 정확성, 속도 vs 품질
- **보안/프라이버시**: 데이터 노출, 인증 경계
- **확장성/성능**: 예상 부하, 병목
- **통합/의존성**: 외부 시스템, 버전 호환, 폴백
- **마이그레이션**: 현재 → 목표, 하위 호환

### 인터뷰 플로우
1. 가장 중요한 모호함부터
2. 관련 질문 그룹핑
3. 답변에서 새 모호함 발견 시 추적
4. 커버된 영역 / 미해결 영역 추적

### 종료 조건
- 모든 구현 결정에 명확한 답
- 논리적 모순 해소
- 엣지케이스 동작 정의
- 트레이드오프 우선순위 명시
- 추가 질문 없이 구현 가능

## Step 4: 스펙 문서 작성

인터뷰 완료 후 `SPEC.md` 생성 (원본 문서와 동일 디렉토리).

```markdown
# {프로젝트/기능} Specification

## 1. Overview
- Problem statement
- Solution summary
- Success criteria

## 2. Functional Requirements
### 2.1 Core Features
- 기능 + 수용 기준
### 2.2 User Flows
- 단계별 인터랙션
### 2.3 Edge Cases & Error Handling
- 예외 시나리오 동작

## 3. Technical Architecture
### 3.1 System Design
- 아키텍처 결정, 컴포넌트 경계
### 3.2 Data Model
- 엔티티, 관계
### 3.3 API Design
- 엔드포인트, 컨트랙트, 에러
### 3.4 State Management
- 클라이언트/서버 상태 경계

## 4. UX/UI Specification
### 4.1 Interaction Patterns
### 4.2 States (loading, empty, error, success)
### 4.3 Accessibility

## 5. Non-Functional Requirements
### 5.1 Performance
### 5.2 Security
### 5.3 Scalability

## 6. Constraints & Trade-offs
- 결정 사항 + 근거
- 의도적 제외 + 이유

## 7. Open Questions
- 향후 명확화 필요 항목
```

비관련 섹션은 생략. 모든 내용은 원본 문서 또는 인터뷰 답변에서 추적 가능해야 함.

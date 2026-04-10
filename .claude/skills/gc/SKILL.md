---
name: gc
description: 하네스 위생(가비지 컬렉션). dead code 정리 + CLAUDE.md ↔ 코드 불일치 탐지. 코드베이스가 지저분해졌을 때, 리팩토링 후 잔재를 정리할 때 사용한다.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep, Edit, Write
argument-hint: [검사할 영역: backend | frontend | all (기본)]
---

# 하네스 위생 (Garbage Collection) 스킬

> 쓰레기를 치우고, 규칙과 코드의 불일치를 바로잡는다.

## Step 1: 검사 대상 결정

- 인자가 `backend` → 백엔드만 검사
- 인자가 `frontend` → 프론트엔드만 검사
- 인자 없음 또는 `all` → 둘 다 검사

---

## Part A: Dead Code 탐지 (쓰레기 치우기)

### 백엔드

#### A-1. 사용되지 않는 클래스 탐지
- `backend/src/main/java/` 내 모든 Java 클래스를 스캔
- 각 클래스명이 다른 파일에서 import되거나 참조되는지 확인
- 어디서도 참조되지 않는 클래스 목록 추출
- **제외**: Controller, Configuration, Application, Listener, Scheduler 클래스 (진입점)

#### A-2. 고아 Entity/Mapper 탐지
- `adapter/out/persistence/entity/` 에 Entity가 있는데 대응하는 Domain 클래스가 없는 경우
- `adapter/out/persistence/` 에 Mapper가 있는데 대응하는 Entity 또는 Domain이 없는 경우

#### A-3. 고아 Port 탐지
- `application/port/in/` 에 UseCase가 있는데 구현하는 Service가 없는 경우
- `application/port/out/` 에 Port가 있는데 구현하는 Adapter가 없는 경우

#### A-4. 주석 처리된 코드 탐지
- `//` 또는 `/* */` 로 주석 처리된 코드 블록 탐지 (5줄 이상 연속 주석)
- JavaDoc은 제외

#### A-5. 사용되지 않는 예외 클래스
- `domain/exception/` 에 정의되어 있지만 어디서도 throw되지 않는 예외

### 프론트엔드

#### A-6. 사용되지 않는 컴포넌트/페이지/훅/유틸/API 함수
- `src/components/` — 어디서도 import되지 않는 컴포넌트
- `src/pages/` — App.jsx 라우트에 등록되지 않은 페이지
- `src/hooks/` — 어디서도 import되지 않는 훅
- `src/utils/` — 어디서도 import되지 않는 함수
- `src/api/` — 어디서도 import되지 않는 API 함수

---

## Part C: CLAUDE.md ↔ 실제 코드 불일치 탐지

> 규칙이 있는데 코드가 안 따르거나, 코드가 바뀌었는데 규칙이 안 바뀐 경우.

### C-1. 규칙은 있는데 코드가 안 따르는 경우
- CLAUDE.md에 "record DTO 사용" → 실제로 class DTO가 남아있는지 확인
- CLAUDE.md에 "DomainException 상속" → 실제로 다른 예외를 상속하는 커스텀 예외가 있는지
- backend/CLAUDE.md 코드 생성 패턴과 실제 코드 구조가 맞는지

### C-2. 코드가 바뀌었는데 규칙이 안 바뀐 경우
- 패키지 구조가 변경되었는데 backend/CLAUDE.md 디렉토리 트리가 안 맞는 경우
- 새 도메인이 추가되었는데 CLAUDE.md Bounded Contexts 테이블에 없는 경우
- 새 이벤트가 추가되었는데 backend/CLAUDE.md 이벤트 목록에 없는 경우

---

## Step 2: 결과 출력

```
## GC 스캔 결과

### Part A: Dead Code
| 유형 | 파일 | 이유 |
|------|------|------|
| dead class | SomeOldService.java | 어디서도 참조되지 않음 |
| 고아 Entity | OldEntity.java | 대응하는 Domain 없음 |

### Part B: 규칙 ↔ 코드 불일치
| 불일치 | 상세 |
|--------|------|
| Bounded Contexts 테이블 | Chat 도메인이 추가됐는데 CLAUDE.md에 없음 |

### 요약
- Dead code 정리 대상: N개
- 규칙 불일치 수정 대상: N개
```

## Step 3: 정리 (사용자 승인 후)

**반드시 사용자에게 정리 목록을 보여주고 승인을 받은 후에 실행한다.**

### Part A 정리: Dead Code 삭제
- 파일 전체가 dead → 파일 삭제
- 파일 일부가 dead → 해당 부분만 제거

### Part B 정리: 규칙 업데이트
- CLAUDE.md / backend/CLAUDE.md / frontend/CLAUDE.md 업데이트

### 검증
- 삭제/수정 후 컴파일 체크: `./gradlew compileJava` (백엔드)
- 삭제/수정 후 빌드 체크: `npm run build` (프론트엔드)
- **컴파일/빌드 실패 시 롤백하고 사용자에게 보고**

## Step 4: 완료 요약

```
## GC 완료

### 정리된 항목
- Dead code: N개 삭제
- 규칙 불일치: N개 업데이트

### 검증
- 백엔드 컴파일: ✅/❌
- 프론트엔드 빌드: ✅/❌
```

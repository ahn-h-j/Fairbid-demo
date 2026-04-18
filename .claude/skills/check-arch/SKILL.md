---
name: check-arch
description: Use this skill when the user wants to check architecture rules, verify hexagonal architecture compliance, or review code for architecture violations.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep
argument-hint: [검사할 모듈명 (선택)]
---

# 아키텍처 검사

$ARGUMENTS

## Step 1: 검사 대상 결정

사용자 지정 없으면 전체 모듈: `auction`, `bid`, `user`, `winning`, `notification`, `common`

## Step 2: 규칙별 검사 실행

### Rule 1: Controller → Repository 직접 호출 금지

```
Grep: "Repository" in adapter/in/controller/**/*.java
Grep: "port.out" in adapter/in/controller/**/*.java
```

위반: import에 `Repository` 또는 `port.out` 포함

### Rule 2: Domain → JPA 의존 금지

```
Grep: "@Entity|@Table|@Column" in domain/model/**/*.java
Grep: "jakarta.persistence|javax.persistence" in domain/model/**/*.java
```

위반: JPA 어노테이션 또는 persistence import 포함

### Rule 3: Service → Entity 직접 사용 금지

```
Grep: "adapter.out.persistence.entity" in application/service/**/*.java
Grep: "JpaRepository" in application/service/**/*.java
```

위반: Entity import 또는 JpaRepository 직접 주입

### Rule 4: Adapter → Port 구현 확인

```
Grep: "implements.*Port" in adapter/out/**/*.java
```

정상: Port 인터페이스 구현

## Step 3: 결과 출력

```markdown
## 검사 결과

### Controller 규칙
{✅ 위반 없음 | ❌ 위반 발견}
{위반 시: - `{파일}:{라인}` - {내용}}

### Domain 규칙
{✅ 위반 없음 | ❌ 위반 발견}

### Service 규칙
{✅ 위반 없음 | ❌ 위반 발견}

### Adapter 규칙
{✅ 위반 없음 | ❌ 위반 발견}

---
총 위반: {n}건
```

## Step 4: 수정 방안 (위반 시)

위반 건별로 수정 방안 제시:
- 어떤 코드를 어떻게 변경해야 하는지
- Port 인터페이스 추가 필요 여부
- Mapper 추가 필요 여부

---
name: test
description: Use this skill when the user wants to run tests, execute test suite, check if tests pass, or verify code changes with tests.
disable-model-invocation: false
allowed-tools: Bash, Read, Glob, Grep
argument-hint: [테스트 클래스명 또는 태그 (선택)]
---

# 테스트 실행

$ARGUMENTS

## Step 1: 실행 대상 결정

| 사용자 요청 | 명령어 |
|------------|--------|
| 전체 테스트 / 지정 없음 | `cd backend && ./gradlew test` |
| 클래스 지정 | `cd backend && ./gradlew test --tests "{클래스명}"` |
| 패턴 지정 | `cd backend && ./gradlew test --tests "*{패턴}*"` |
| Cucumber | `cd backend && ./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"` |

## Step 2: 테스트 실행

해당 명령어 실행. Docker 실행 중이어야 함 (TestContainers).

## Step 3: 결과 분석

### 성공 시
```
테스트 통과: {n}개 테스트 성공
```

### 실패 시
아래 항목 분석 후 보고:

1. **실패 테스트**: 클래스명.메서드명
2. **실패 원인**: assertion 실패 / 예외 / 타임아웃
3. **관련 코드**: `{파일경로}:{라인번호}`
4. **해결 방안**: 코드 수정 제안 또는 테스트 수정 필요 여부

## 테스트 구조 참고

```
backend/src/test/resources/features/
├── bid/
│   ├── instant-buy.feature
│   └── ...
└── ...
```

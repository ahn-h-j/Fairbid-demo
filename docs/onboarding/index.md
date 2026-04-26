# FairBid 온보딩

신규 개발자가 이 폴더 하나로 환경 셋업, 기능 이해, 장애 대응, 기능 개선까지 가능하도록 만든 온보딩 키트.

## 추천 읽기 순서

| # | 문서 | 언제 읽나 |
|---|------|----------|
| 1 | [00-overview.md](00-overview.md) | 처음 왔을 때 (10분) |
| 2 | [GLOSSARY.md](GLOSSARY.md) | 도메인 용어 잡으려고 |
| 3 | [01-setup.md](01-setup.md) | 로컬에서 띄우려고 |
| 4 | [03-architecture.md](03-architecture.md) | 코드 구조 잡으려고 |
| 5 | [04-data-model.md](04-data-model.md) | 데이터 흐름 잡으려고 |
| 6 | [features/index.md](features/index.md) | 특정 기능 보려고 |
| 7 | [05-conventions.md](05-conventions.md) | PR 올리기 전에 |
| 참조 | [02-infra.md](02-infra.md) | 외부 의존성 정리 |
| 참조 | [06-troubleshooting.md](06-troubleshooting.md) | 장애 대응 |
| 참조 | [07-where-is-it.md](07-where-is-it.md) | "X 어디 있지?" |

## 전체 파일 목차

```
docs/onboarding/
├── index.md                      ← 지금 이 파일
├── 00-overview.md                ← 프로젝트 컨셉, BC 지도
├── 01-setup.md                   ← 로컬 환경 셋업
├── 02-infra.md                   ← 외부 의존성 토폴로지
├── 03-architecture.md            ← 헥사고날 의존성 그래프
├── 04-data-model.md              ← Entity ERD
├── 05-conventions.md             ← 코딩/커밋/PR/테스트 규칙
├── 06-troubleshooting.md         ← 자주 나는 장애 + 진단/복구
├── 07-where-is-it.md             ← "X 어디 있지?" 인덱스
├── GLOSSARY.md                   ← 도메인 용어집
├── onboarding.profile.yaml       ← 에이전트 설정 (수정 X)
└── features/
    ├── index.md                  ← 기능별 카탈로그
    └── {기능}.md                 ← 기능 흐름 (10 섹션)
```

## 갱신 방법

- 코드 바뀌면 `/onboarding refresh <파일>` 로 부분 갱신
- 기능 새로 만들면 `/explain <기능>` 으로 features/ 추가
- 처음부터 다시 만들려면 `/onboarding`

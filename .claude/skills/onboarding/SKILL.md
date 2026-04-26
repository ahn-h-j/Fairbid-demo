---
name: onboarding
description: 프로젝트 온보딩 키트(`docs/onboarding/`) 생성/갱신. 신규 개발자가 이 폴더 하나로 환경 셋업, 기능 이해, 장애 대응, 기능 개선까지 가능한 수준의 문서 패키지를 만든다. 인자 없이 호출하면 전체 생성, `sync`로 git 기반 증분 갱신, `refresh <파일>`로 특정 파일 강제 갱신, `explain <기능>`으로 기능 단건 생성.
---

# /onboarding

## 사용법

```
/onboarding                    # 전체 키트 생성 (최초 또는 큰 변경 후)
/onboarding sync               # ⭐ git 기반 증분 갱신 (.last-sync 이후 변경분만)
/onboarding sync --since HEAD~10   # 명시 ref부터
/onboarding sync --dry-run     # 변경될 문서 목록만 보고 생성 안 함
/onboarding sync --auto        # 컨펌 없이 자동 갱신
/onboarding refresh 03-architecture.md   # 특정 파일 강제 갱신
/onboarding refresh features/입찰.md      # 특정 기능 강제 갱신
/explain 입찰                  # 기능 1개만 작성/갱신 (단축 명령)
```

## 권장 워크플로우

**일상**: 기능 추가/변경 후 `/onboarding sync` — git commit 기반으로 영향 문서만 빠르게 갱신
**리팩토링 후**: `/onboarding` — 전체 재생성 (구조 크게 바뀔 때)
**특정 기능만**: `/explain <기능>` — 그 문서만 다시

## 동작

`onboarding-architect` 에이전트를 호출해 `docs/onboarding/` 폴더에 다음을 생성/갱신한다:

- `index.md`, `00-overview.md` ~ `07-where-is-it.md`
- `features/*.md` (기능별 흐름)
- `GLOSSARY.md`
- `.last-sync` (sync 모드의 마지막 동기화 commit hash)

## 진행 흐름

1. **프로필 확인** — `docs/onboarding/onboarding.profile.yaml` 없으면 자동 생성 후 사용자 컨펌
2. **정찰** — Explore 서브에이전트로 컨트롤러/엔티티/도메인 병렬 분석
3. **생성** — 11개+ 파일 작성 (각 file:line 인용 포함)
4. **검증** — 인용 위치 실재 확인, 누락 컨트롤러 체크

## 범용성

`onboarding.profile.yaml` 한 파일만 갈아끼우면 Spring/NestJS/Django 어디서든 동일하게 동작.
프로젝트 특화 키워드는 에이전트 프롬프트에 0개.

## 산출물 위치

`docs/onboarding/` (프로젝트 루트). 커밋 여부는 사용자 판단 (FairBid는 `.gitignore`에 `docs/spec` 패턴이 있으나 `docs/onboarding`은 별도 정책).

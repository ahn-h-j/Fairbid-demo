# 온보딩 키트 사용 가이드

이 폴더는 **AI 에이전트가 자동 생성하는 온보딩 키트**다.
신규 개발자는 [INDEX](index.md)부터 보면 되고, **이 문서는 키트를 운영/유지하는 사람**용.

---

## 1. 구성 요소

| 위치 | 역할 |
|------|------|
| `docs/onboarding/*.md` | 산출물 (사람이 읽는 문서) |
| `docs/onboarding/onboarding.profile.yaml` | 프로젝트 프로필 (재사용 핵심) |
| `docs/onboarding/.last-sync` | 마지막 git 동기화 commit hash |
| `.claude/agents/onboarding-architect.md` | 에이전트 정의 (방법론) |
| `.claude/skills/onboarding/SKILL.md` | 슬래시 커맨드 |
| `mkdocs.yml` | HTML 사이트 설정 (사이드바, 테마) |

---

## 2. 처음 셋업 (다른 프로젝트에 적용 시)

### 2-1. 의존성 설치
```bash
pip install mkdocs-material pymdown-extensions
```

### 2-2. 위 4개 파일을 새 프로젝트로 복사
- `.claude/agents/onboarding-architect.md`
- `.claude/skills/onboarding/SKILL.md`
- `docs/onboarding/onboarding.profile.yaml` (프로젝트에 맞게 **수정 필수**)
- `mkdocs.yml`

### 2-3. 프로필 수정
`docs/onboarding/onboarding.profile.yaml`에서:
- `project.name`
- `stack.*` (language, framework, build_tool, architecture)
- `bounded_contexts` 또는 모듈 목록
- `source_layout.*` (디렉토리 패턴)
- `markers.*` (`@RestController` → `@Controller` for NestJS 등)
- `infra_components`

### 2-4. 첫 실행
```
/onboarding
```
→ 전체 키트 생성. 컨펌 받으며 진행.

### 2-5. 사이트 미리보기
```bash
mkdocs serve -a 127.0.0.1:8765
```
→ http://127.0.0.1:8765

---

## 3. 일상 사용 (코드 변경 후)

### 평소: 증분 갱신 (권장)
```
/onboarding sync
```
→ `.last-sync` commit hash 이후 변경된 파일만 분석 → 영향 받는 문서만 재생성 → `.last-sync` 갱신

### 옵션
| 명령 | 용도 |
|------|------|
| `/onboarding sync --dry-run` | 어떤 문서가 갱신될지만 미리보기 |
| `/onboarding sync --since HEAD~10` | 특정 ref부터 |
| `/onboarding sync --auto` | 컨펌 없이 자동 (CI용) |

### 큰 리팩토링 후
```
/onboarding
```
→ 전체 풀스캔으로 재생성

### 특정 기능 단건
```
/explain 입찰
/explain "OAuth 로그인"
```
→ `features/{기능}.md` 만 작성/갱신

### 특정 파일 강제 갱신
```
/onboarding refresh 04-data-model.md
/onboarding refresh features/입찰.md
```

---

## 4. 새 컨트롤러 / 엔드포인트 추가됐을 때

`/onboarding sync`가 자동 감지:
1. 신규 컨트롤러 발견 → "새 features 문서 만들까?" 컨펌
2. 컨펌 → 문서 작성 + `features/index.md` 표 추가 + `mkdocs.yml` nav 자동 추가

수동으로 만들고 싶으면:
```
/explain <새 기능 이름>
```

---

## 5. 컨트롤러 / 엔드포인트 삭제됐을 때

`/onboarding sync`가 자동 감지 → 해당 `features/*.md` 상단에 `> ⚠️ DEPRECATED` 마크 또는 제거 제안.
사용자 컨펌 후 정리.

---

## 6. HTML 사이트 운영

### 로컬 미리보기
```bash
mkdocs serve -a 127.0.0.1:8765
```

### 정적 빌드
```bash
mkdocs build
```
→ `docs/_site/` 폴더에 정적 HTML 생성. 어디든 호스팅 가능.

### GitHub Pages 배포
```bash
mkdocs gh-deploy
```
→ `gh-pages` 브랜치로 자동 배포. 포트폴리오 링크용.

### 사이드바 / nav 수정
`mkdocs.yml`의 `nav:` 섹션 직접 편집. (sync 모드는 새 features만 자동 추가, 기존 그룹핑 변경은 수동)

---

## 7. .gitignore 권장

```gitignore
# 빌드 결과물
docs/_site/

# .last-sync는 커밋 여부 선택:
# - 팀에서 같은 sync 지점 공유 → 커밋
# - 개인 작업 환경별로 다름 → ignore
docs/onboarding/.last-sync
```

---

## 8. 트러블슈팅

| 증상 | 원인 / 해결 |
|------|-------------|
| `mkdocs serve` 후 404 | `index.md`가 없음. 첫 페이지 파일명이 `index.md`(소문자)인지 확인 |
| nav 변경 후 사이드바 그대로 | mkdocs serve의 watchdog이 가끔 못 따라잡음. 서버 재시작 |
| 한글 파일명 깨짐 | nav에서 따옴표로 감싸기 (`- "어디 있지?": 07-where-is-it.md`) |
| `.last-sync` 손상 | 삭제 후 `git rev-parse HEAD > docs/onboarding/.last-sync` |
| Mermaid 안 그려짐 | `mkdocs.yml`의 `superfences` + `mermaid` custom_fence 설정 확인 |
| 404로 빌드 통과해도 한 페이지만 보임 | mkdocs.yml의 nav가 변경된 파일을 안 가리키는 경우. nav 수동 추가 |

---

## 9. 다른 프로젝트로 가져갈 때 체크리스트

- [ ] `.claude/agents/onboarding-architect.md` 복사 (수정 X — 범용)
- [ ] `.claude/skills/onboarding/SKILL.md` 복사 (수정 X)
- [ ] `docs/onboarding/onboarding.profile.yaml` 복사 후 **프로젝트에 맞게 수정**
- [ ] `mkdocs.yml` 복사 후 `site_name`, `nav` 정도 손봄
- [ ] `pip install mkdocs-material pymdown-extensions`
- [ ] `/onboarding` 첫 실행
- [ ] `mkdocs serve` 미리보기
- [ ] `git rev-parse HEAD > docs/onboarding/.last-sync` 로 sync 시작점 기록

---

## 10. 자주 받는 질문

**Q. AI가 잘못된 정보 적으면?**
A. 모든 문서에 file:line 인용이 있음. 의심되면 그 위치 코드 확인 후 수동 수정.

**Q. 도메인 용어 정의가 마음에 안 들면?**
A. `GLOSSARY.md` 직접 편집. 다음 sync 때도 보존됨 (sync는 변경된 파일 관련 섹션만 갱신).

**Q. 트러블슈팅 런북은 자동 안 만들어진다는데?**
A. `06-troubleshooting.md`에 시드 패턴만 있음. 실제 장애 겪으면 그 형식대로 추가. AI는 운영 경험이 없어 자동 못 만듦.

**Q. 프로필을 바꿨는데 반영 안 됨**
A. `/onboarding` 전체 재생성 필요. `/onboarding sync`는 코드 변경만 감지하지 프로필 변경은 못 봄.

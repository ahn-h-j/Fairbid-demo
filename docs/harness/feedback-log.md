# Harness Feedback Log

> 가드레일 실패가 자동으로 기록되는 파일.
> `/evolve` 스킬이 이 파일을 분석하여 하네스 변경을 제안한다.
>
> **상태 설명**
> - `미분석` → 아직 `/evolve`로 분석하지 않은 항목
> - `분석완료` → `/evolve`가 분석했고, 하네스 변경이 적용된 항목
>
> 수동으로 편집하지 마라. hook/pre-commit/gc가 자동으로 append한다.

---

### 2026-04-06 19:30
- **도구**: /gc
- **위반 상세**:
```
transition-all CSS 사용 (31건)
frontend/CLAUDE.md에서 금지하지만 ESLint 규칙이 없었음
```
- **파일**:
  - `AuctionListPage.jsx`
  - `AuctionDetailPage.jsx`
  - `AuctionCreatePage.jsx`
  - `TradeDetailPage.jsx`
  - `LandingPage.jsx`
  - `Pagination.jsx`
  - `ImageUpload.jsx`
  - `ImageGallery.jsx`
- **상태**: 분석완료 — ESLint `no-restricted-syntax` 규칙 추가 + 코드 30건 수정 (2026-04-06)

### 2026-04-06 22:51 ~ 23:48
- **도구**: pre-commit (Checkstyle + ESLint)
- **위반 상세**:
```
하네스 구축 과정에서 발생한 가드레일 실패.
- Checkstyle: 와일드카드 import 26건, 미사용 import 4건
- ESLint: hook 경로 버그로 인한 false positive
```
- **상태**: 분석완료 — 와일드카드 import 전량 수정, hook 버그 수정 (2026-04-06)

### 2026-04-12 11:37
- **도구**: ESLint
- **위반 상세**:
```

Oops! Something went wrong! :(

ESLint: 10.2.0

Error [ERR_MODULE_NOT_FOUND]: Cannot find package 'eslint-config-prettier' imported from C:\Users\tkgkd\Desktop\Workspace\FairBid-ai-assist\frontend\eslint.config.js
    at packageResolve (node:internal/modules/esm/resolve:839:9)
    at moduleResolve (node:internal/modules/esm/resolve:908:18)
    at defaultResolve (node:internal/modules/esm/resolve:1039:11)
    at ModuleLoader.defaultResolve (node:internal/modules/esm/loader:554:12)
    at ModuleLoader.resolve (node:internal/modules/esm/loader:523:25)
    at ModuleLoader.getModuleJob (node:internal/modules/esm/loader:246:38)
    at ModuleJob._link (node:internal/modules/esm/module_job:126:49)
```
- **파일**:
  - `frontend/src/api/mutations.js`
  - `frontend/src/pages/AuctionCreatePage.jsx`
- **상태**: 미분석 → `/evolve`로 분석 필요

### 2026-04-18 06:55
- **단계**: Claude Code hook
- **도구**: Bash
- **위반**: sed -i 백업 확장자 누락 (Windows Git Bash에서 파일 손상 위험)
- **명령어**: sed -i 's/foo/bar/' file.txt
- **상태**: open

### 2026-04-18 06:55
- **단계**: Claude Code hook
- **도구**: Bash
- **위반**: sed -i 백업 확장자 누락 (Windows Git Bash에서 파일 손상 위험)
- **명령어**: sed --in-place 's/foo/bar/' file.txt
- **상태**: open

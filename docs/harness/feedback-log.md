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

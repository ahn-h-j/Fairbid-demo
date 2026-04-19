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

### 2026-04-09 18:33
- **도구**: ESLint
- **위반 상세**:
```
[0m
[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\api\client.js[24m
  [2m64:7[22m   [31merror[39m  'atob' is not defined            [2mno-undef[22m
  [2m66:21[22m  [31merror[39m  Unexpected string concatenation  [2mprefer-template[22m
  [2m66:28[22m  [31merror[39m  Unexpected string concatenation  [2mprefer-template[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\components\Layout.jsx[24m
  [2m147:20[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\components\Timer.jsx[24m
  [2m109:8[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\AuctionCreatePage.jsx[24m
  [2m369:13[22m  [33mwarning[39m  A form label must have accessible text  [2mjsx-a11y/label-has-associated-control[22m
  [2m414:13[22m  [33mwarning[39m  A form label must have accessible text  [2mjsx-a11y/label-has-associated-control[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\AuctionDetailPage.jsx[24m
  [2m603:9[22m  [31merror[39m  Use object destructuring  [2mprefer-destructuring[22m
  [2m604:9[22m  [31merror[39m  Use object destructuring  [2mprefer-destructuring[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\AuctionListPage.jsx[24m
  [2m160:8[22m   [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m
  [2m164:11[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\MyPage.jsx[24m
  [2m303:10[22m  [31merror[39m    Do not nest ternary expressions                 [2mno-nested-ternary[22m
  [2m306:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m316:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m331:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m341:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m351:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m426:10[22m  [31merror[39m    Do not nest ternary expressions                 [2mno-nested-ternary[22m
  [2m429:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m439:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m449:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\OnboardingPage.jsx[24m
   [2m57:11[22m  [31merror[39m  Use object destructuring     [2mprefer-destructuring[22m
  [2m111:11[22m  [31merror[39m  Expected property shorthand  [2mobject-shorthand[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\TradeDetailPage.jsx[24m
  [2m260:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m270:15[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m302:5[22m   [31merror[39m    Do not nest ternary expressions                 [2mno-nested-ternary[22m
  [2m350:21[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m360:21[22m  [33mwarning[39m  A form label must be associated with a control  [2mjsx-a11y/label-has-associated-control[22m
  [2m406:9[22m   [31merror[39m    Use object destructuring                        [2mprefer-destructuring[22m
  [2m651:12[22m  [31merror[39m    Do not nest ternary expressions                 [2mno-nested-ternary[22m
  [2m755:12[22m  [31merror[39m    Do not nest ternary expressions                 [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\TradeListPage.jsx[24m
  [2m263:12[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\admin\AuctionManagePage.jsx[24m
   [2m73:10[22m  [31merror[39m    Unexpected use of 'confirm'        [2mno-restricted-globals[22m
   [2m73:10[22m  [33mwarning[39m  Unexpected confirm                 [2mno-alert[22m
  [2m193:10[22m  [31merror[39m    Do not nest ternary expressions    [2mno-nested-ternary[22m
  [2m285:17[22m  [31merror[39m    Empty components are self-closing  [2mreact/self-closing-comp[22m
  [2m289:16[22m  [31merror[39m    Do not nest ternary expressions    [2mno-nested-ternary[22m
  [2m413:34[22m  [31merror[39m    Unexpected string concatenation    [2mprefer-template[22m
  [2m521:18[22m  [31merror[39m    Do not nest ternary expressions    [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\admin\DashboardPage.jsx[24m
  [2m154:12[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m
  [2m221:12[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\pages\admin\UserManagePage.jsx[24m
   [2m91:10[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m
  [2m164:16[22m  [31merror[39m  Do not nest ternary expressions  [2mno-nested-ternary[22m

[4mC:\Users\tkgkd\Desktop\Workspace\FairBid\frontend\src\utils\formatters.js[24m
  [2m10:10[22m  [31merror[39m  Unexpected string concatenation  [2mprefer-template[22m

[31m[1m✖ 44 problems (29 errors, 15 warnings)[22m[39m
[31m[1m  10 errors and 0 warnings potentially fixable with the `--fix` option.[22m[39m
[0m
```
- **파일**:
  - `frontend/src/api/client.js`
  - `frontend/src/api/mutations.js`
  - `frontend/src/api/useAuction.js`
  - `frontend/src/api/useAuctions.js`
  - `frontend/src/api/useTrade.js`
  - `frontend/src/components/Alert.jsx`
  - `frontend/src/components/AuctionCard.jsx`
  - `frontend/src/components/CategoryBadge.jsx`
  - `frontend/src/components/ImageUpload.jsx`
  - `frontend/src/components/Layout.jsx`
  - `frontend/src/components/NotificationDropdown.jsx`
  - `frontend/src/components/Pagination.jsx`
  - `frontend/src/components/SplashScreen.jsx`
  - `frontend/src/components/StatusBadge.jsx`
  - `frontend/src/components/Timer.jsx`
  - `frontend/src/contexts/AuthContext.jsx`
  - `frontend/src/hooks/useInfiniteScroll.js`
  - `frontend/src/hooks/useWebSocket.js`
  - `frontend/src/main.jsx`
  - `frontend/src/pages/AuctionCreatePage.jsx`
  - `frontend/src/pages/AuctionDetailPage.jsx`
  - `frontend/src/pages/AuctionListPage.jsx`
  - `frontend/src/pages/LandingPage.jsx`
  - `frontend/src/pages/LoginPage.jsx`
  - `frontend/src/pages/MyPage.jsx`
  - `frontend/src/pages/OnboardingPage.jsx`
  - `frontend/src/pages/TradeDetailPage.jsx`
  - `frontend/src/pages/TradeListPage.jsx`
  - `frontend/src/pages/admin/AdminLayout.jsx`
  - `frontend/src/pages/admin/AuctionManagePage.jsx`
  - `frontend/src/pages/admin/DashboardPage.jsx`
  - `frontend/src/pages/admin/UserManagePage.jsx`
  - `frontend/src/utils/constants.js`
  - `frontend/src/utils/formatters.js`
- **상태**: 미분석 → `/evolve`로 분석 필요

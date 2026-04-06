# Frontend CLAUDE.md

> 프론트엔드 코드 작업 시 자동 적용되는 규칙. 루트 CLAUDE.md의 규칙을 상속한다.

---

## 1. 디렉토리 구조 — 파일 배치 규칙

```
src/
├── api/            ← API 통신 (Axios client, SWR hooks, mutations)
├── components/     ← 재사용 가능한 UI 컴포넌트
├── pages/          ← 라우트별 페이지 컴포넌트
│   └── admin/      ← 관리자 페이지
├── contexts/       ← React Context (전역 상태)
├── hooks/          ← 커스텀 훅
└── utils/          ← 유틸리티 함수, 상수
```

### 파일 배치 규칙
- **API 호출**: `api/` 디렉토리에만 작성. 컴포넌트에서 직접 Axios 호출 금지.
- **새 페이지**: `pages/` 에 `{Name}Page.jsx` 형태로 생성
- **재사용 컴포넌트**: `components/` 에 `{Name}.jsx` 형태로 생성
- **커스텀 훅**: `hooks/` 에 `use{Name}.js` 형태로 생성
- **상수/포맷터**: `utils/constants.js`, `utils/formatters.js` 에 추가

---

## 2. 절대 금지 (Anti-patterns)

### HTML/접근성
- `<div onClick>` 금지 → `<button>` 사용
- `<img>` 에 `alt` 없이 사용 금지 (장식용은 `alt=""`)
- 아이콘 버튼에 `aria-label` 없이 사용 금지
- 폼 인풋에 `<label>` 또는 `aria-label` 없이 사용 금지
- `user-scalable=no` 또는 `maximum-scale=1` 줌 비활성화 금지
- `outline-none` without `focus-visible` 대체 금지

### CSS/스타일
- `transition: all` 절대 금지 → 속성 명시적 나열 (`transition: opacity 200ms, transform 200ms`)
- `<img>` 에 `width`/`height` 없이 사용 금지 (CLS 방지)

### JavaScript/React
- 배럴 파일(`index.js`) 직접 import 금지 → 개별 모듈 직접 import
- `useMemo`로 단순 원시값 감싸지 마라
- `onPaste` + `preventDefault`로 붙여넣기 차단 금지
- 하드코딩된 날짜/숫자 포맷 금지 → `Intl.DateTimeFormat`, `Intl.NumberFormat` 사용
- 컴포넌트에서 `axios.get()` 직접 호출 금지 → `api/` 디렉토리의 함수 사용

---

## 3. 성능 규칙

### Waterfall 제거
- 독립적인 API 요청은 `Promise.all()`로 병렬 처리
- `await` 는 실제 사용 시점까지 지연

### Re-render 최적화
- 상태 구독은 필요한 값으로 좁혀라 (전체 객체 구독 금지)
- 비원시값 기본값은 컴포넌트 밖으로 추출
- 함수형 `setState` 사용하여 stale closure 방지
- 비용이 큰 초기값은 `useState(() => compute())`
- 긴급하지 않은 업데이트는 `useTransition` 사용

### 렌더링
- 긴 리스트(50개+)에는 가상화 적용
- 뷰포트 하단 이미지: `loading="lazy"`
- 정적 JSX는 렌더 함수 밖으로 추출
- 조건부 렌더링에 `&&` 대신 삼항 연산자 (falsy 값 `0` 렌더 방지)

---

## 4. 폼 처리 규칙

- `autocomplete`와 의미있는 `name` 속성 필수
- 올바른 `type` 사용 (`email`, `tel`, `url`, `number`) + `inputMode`
- 레이블은 클릭 가능하게 (`htmlFor` 또는 컨트롤 감싸기)
- 제출 버튼: 요청 중 스피너 표시
- 에러: 필드 옆에 인라인 표시, 제출 시 첫 에러 필드에 포커스
- placeholder는 `…`으로 끝내고 예시 패턴 표시
- 미저장 변경사항 있을 때 페이지 이탈 경고

---

## 5. 네비게이션 & 상태

- URL이 상태를 반영해야 함 (필터, 탭, 페이지네이션 → 쿼리 파라미터)
- 파괴적 동작: 확인 모달 또는 undo 제공 (즉시 실행 금지)
- 인터랙티브 요소: 키보드 핸들러 필수
- `hover:` 상태 필수 (버튼, 링크)

---

## 6. 애니메이션

- `prefers-reduced-motion` 존중 (감소 변형 제공 또는 비활성화)
- `transform`/`opacity`만 애니메이션 (compositor-friendly)
- 애니메이션은 사용자 입력에 중단 가능

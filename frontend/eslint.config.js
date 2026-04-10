import js from '@eslint/js';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import jsxA11yPlugin from 'eslint-plugin-jsx-a11y';
// import importPlugin from 'eslint-plugin-import';  // ESLint v10 비호환 — 지원 시 활성화
import prettierConfig from 'eslint-config-prettier';

/**
 * FairBid ESLint 가드레일.
 *
 * Airbnb 스타일 가이드를 flat config 환경에서 적용하기 위해,
 * airbnb/javascript 리포지토리의 핵심 규칙을 수동으로 포팅했다.
 * (eslint-config-airbnb는 legacy .eslintrc 전용이라 flat config와 호환 불가)
 *
 * 적용 범위:
 * 1. Airbnb 핵심 규칙 (Best Practices, Variables, Style, ES6, Imports, React, JSX-A11y)
 * 2. FairBid 커스텀 가드레일 (transition-all 금지, no-console 등)
 * 3. eslint-config-prettier (마지막 — Prettier 충돌 방지)
 */
export default [
    js.configs.recommended,
    {
        files: ['src/**/*.{js,jsx}'],
        plugins: {
            react: reactPlugin,
            'react-hooks': reactHooksPlugin,
            'jsx-a11y': jsxA11yPlugin,
            // import: importPlugin,  // ESLint v10 비호환
        },
        languageOptions: {
            ecmaVersion: 2024,
            sourceType: 'module',
            parserOptions: {
                ecmaFeatures: { jsx: true },
            },
            globals: {
                // 브라우저 전역 변수
                window: 'readonly',
                document: 'readonly',
                navigator: 'readonly',
                console: 'readonly',
                fetch: 'readonly',
                setTimeout: 'readonly',
                clearTimeout: 'readonly',
                setInterval: 'readonly',
                clearInterval: 'readonly',
                URL: 'readonly',
                URLSearchParams: 'readonly',
                localStorage: 'readonly',
                sessionStorage: 'readonly',
                FormData: 'readonly',
                Intl: 'readonly',
                AbortController: 'readonly',
                EventSource: 'readonly',
                WebSocket: 'readonly',
                requestAnimationFrame: 'readonly',
                cancelAnimationFrame: 'readonly',
                HTMLElement: 'readonly',
                IntersectionObserver: 'readonly',
                ResizeObserver: 'readonly',
                MutationObserver: 'readonly',
                CustomEvent: 'readonly',
                Event: 'readonly',
                FileReader: 'readonly',
                Blob: 'readonly',
                crypto: 'readonly',
                process: 'readonly',
                alert: 'readonly',
                confirm: 'readonly',
                Image: 'readonly',
                performance: 'readonly',
                queueMicrotask: 'readonly',
                structuredClone: 'readonly',
                Promise: 'readonly',
                Map: 'readonly',
                Set: 'readonly',
                WeakMap: 'readonly',
                WeakSet: 'readonly',
                Symbol: 'readonly',
                Proxy: 'readonly',
                Reflect: 'readonly',
                globalThis: 'readonly',
            },
        },
        settings: {
            react: { version: '19.0' },
            // 'import/resolver': {  // ESLint v10 비호환
            //     node: { extensions: ['.js', '.jsx'] },
            // },
        },
        rules: {
            // ============================================================
            // Airbnb: Best Practices
            // https://github.com/airbnb/javascript#types
            // ============================================================

            // 변수 선언 시 const 우선, 재할당 필요하면 let (var 금지)
            'no-var': 'error',
            'prefer-const': ['error', { destructuring: 'any', ignoreReadBeforeAssign: true }],

            // == 대신 === 사용 (null 비교 제외)
            eqeqeq: ['error', 'always', { null: 'ignore' }],

            // eval() 금지
            'no-eval': 'error',
            'no-implied-eval': 'error',

            // with 문 금지
            'no-with': 'error',

            // arguments 객체 대신 rest 파라미터 사용
            'prefer-rest-params': 'error',

            // .apply() 대신 spread 연산자 사용
            'prefer-spread': 'error',

            // 문자열 결합 대신 템플릿 리터럴 사용
            'prefer-template': 'error',

            // 불필요한 return await 금지
            'no-return-await': 'error',

            // 선언 전 사용 금지 (함수 선언은 hoisting 허용)
            'no-use-before-define': ['error', { functions: false, classes: true, variables: true }],

            // 불필요한 생성자 금지
            'no-useless-constructor': 'error',

            // 불필요한 rename 금지 (import { foo as foo })
            'no-useless-rename': 'error',

            // 불필요한 computed property key 금지
            'no-useless-computed-key': 'error',

            // Object.assign 대신 spread 사용
            'prefer-object-spread': 'error',

            // 객체 shorthand 사용 권장
            'object-shorthand': ['error', 'always', { ignoreConstructors: false, avoidQuotes: true }],

            // 화살표 함수 콜백 권장
            'prefer-arrow-callback': ['error', { allowNamedFunctions: false, allowUnboundThis: true }],

            // 불필요한 return 금지 (화살표 함수 body)
            'no-useless-return': 'error',

            // 중첩 삼항 연산자 금지
            'no-nested-ternary': 'error',

            // 단항 증감 연산자 금지 (for 루프 제외)
            'no-plusplus': ['error', { allowForLoopAfterthoughts: true }],

            // 하나의 선언문에 여러 변수 금지
            'one-var': ['error', 'never'],

            // 할당 연산자 단축 사용
            'operator-assignment': ['error', 'always'],

            // 불필요한 삼항 연산자 금지
            'no-unneeded-ternary': ['error', { defaultAssignment: false }],

            // 여러 줄 문자열 금지 (백슬래시 연결)
            'no-multi-str': 'error',

            // new로 생성한 객체를 변수에 할당하지 않는 것 금지
            'no-new': 'error',

            // new Function() 금지
            'no-new-func': 'error',

            // new String/Number/Boolean 금지
            'no-new-wrappers': 'error',

            // 파라미터 재할당 금지 (props 수정은 허용)
            'no-param-reassign': ['error', {
                props: true,
                ignorePropertyModificationsFor: [
                    'acc',    // Array.reduce accumulator
                    'e',      // event
                    'ctx',    // context
                    'req',    // express request
                    'res',    // express response
                    'state',  // state
                    'draft',  // immer draft
                ],
            }],

            // 불필요한 조건 할당 금지
            'no-cond-assign': ['error', 'always'],

            // 디버거 금지
            'no-debugger': 'error',

            // alert 금지 (warn으로 설정 — 개발 중 사용 가능)
            'no-alert': 'warn',

            // label 금지
            'no-labels': ['error', { allowLoop: false, allowSwitch: false }],

            // 불필요한 블록 금지
            'no-lone-blocks': 'error',

            // self 비교 금지
            'no-self-compare': 'error',

            // 콤마 연산자 금지
            'no-sequences': 'error',

            // throw 리터럴 금지 (Error 객체만 throw)
            'no-throw-literal': 'error',

            // void 연산자 금지
            'no-void': 'error',

            // iterator 프로토콜 금지 (__iterator__)
            'no-iterator': 'error',

            // proto 금지
            'no-proto': 'error',

            // 변수 shadowing 금지
            'no-shadow': 'error',

            // 제한된 전역 변수 사용 금지
            'no-restricted-globals': ['error',
                'addEventListener', 'blur', 'close', 'closed', 'confirm', 'defaultStatus',
                'defaultstatus', 'event', 'external', 'find', 'focus', 'frameElement',
                'frames', 'history', 'innerHeight', 'innerWidth', 'length', 'location',
                'locationbar', 'menubar', 'moveBy', 'moveTo', 'name', 'onblur', 'onerror',
                'onfocus', 'onload', 'onresize', 'onunload', 'open', 'opener', 'opera',
                'outerHeight', 'outerWidth', 'pageXOffset', 'pageYOffset', 'parent',
                'print', 'removeEventListener', 'resizeBy', 'resizeTo', 'screen',
                'screenLeft', 'screenTop', 'screenX', 'screenY', 'scroll', 'scrollbars',
                'scrollBy', 'scrollTo', 'scrollX', 'scrollY', 'self', 'status',
                'statusbar', 'stop', 'toolbar', 'top',
            ],

            // for-in 루프에서 hasOwnProperty 체크 필수
            'guard-for-in': 'error',

            // 특정 구문 금지 (for-in, for-of, labels, with)
            // 주의: FairBid transition-all 금지 규칙과 병합
            'no-restricted-syntax': [
                'error',
                {
                    selector: 'ForInStatement',
                    message: 'for..in은 프로토타입 체인을 순회합니다. Object.keys().forEach() 또는 for..of를 사용하세요.',
                },
                {
                    selector: 'LabeledStatement',
                    message: 'Labels는 가독성을 해칩니다. 제어 흐름을 재구성하세요.',
                },
                {
                    selector: 'WithStatement',
                    message: 'with 문은 사용 금지입니다.',
                },
                // FairBid 커스텀: transition-all 금지
                {
                    selector: 'Literal[value=/transition-all/], TemplateLiteral[quasis.0.value.raw=/transition-all/]',
                    message: 'transition-all 금지. 속성을 명시적으로 나열하세요 (예: transition: opacity 200ms, transform 200ms)',
                },
            ],

            // 구조 분해 할당 권장
            'prefer-destructuring': ['error', {
                VariableDeclarator: { array: false, object: true },
                AssignmentExpression: { array: false, object: false },
            }, { enforceForRenamedProperties: false }],

            // default case 필수 (switch)
            'default-case': ['error', { commentPattern: '^no default$' }],

            // default case를 마지막에 배치
            'default-case-last': 'error',

            // default parameter를 마지막에 배치
            'default-param-last': 'error',

            // dot notation 사용 권장
            'dot-notation': ['error', { allowKeywords: true }],

            // ============================================================
            // Airbnb: ES6 / Module
            // ============================================================

            // 중복 import 금지
            'no-duplicate-imports': 'off', // import 플러그인에서 처리

            // Symbol 생성 시 description 필수
            'symbol-description': 'error',

            // ============================================================
            // Airbnb: Import 규칙
            // eslint-plugin-import가 ESLint v10 flat config와 호환되지 않아 비활성화.
            // 플러그인이 v10 지원하면 활성화 예정.
            // ============================================================

            // ============================================================
            // Airbnb: React 규칙
            // ============================================================

            // JSX에서 .jsx 확장자 사용
            // jsx-filename-extension: ESLint v10 + eslint-plugin-react 호환 문제로 비활성화
            // 프로젝트가 이미 .jsx 확장자만 사용하므로 실질적 영향 없음
            // 'react/jsx-filename-extension': ['error', { extensions: ['.jsx'] }],

            // self-closing 태그 사용 (자식 없는 컴포넌트)
            'react/self-closing-comp': 'error',

            // JSX에서 불필요한 중괄호 금지
            'react/jsx-curly-brace-presence': ['error', {
                props: 'never',
                children: 'never',
            }],

            // boolean props: <Component visible /> (={true} 생략)
            'react/jsx-boolean-value': ['error', 'never'],

            // JSX에서 bind/화살표 함수 금지 (성능) — warn으로 설정 (실용성)
            'react/jsx-no-bind': ['warn', {
                ignoreRefs: true,
                allowArrowFunctions: true,
                allowBind: false,
            }],

            // Fragment shorthand 사용 (<> 대신 key가 필요하면 Fragment)
            'react/jsx-fragments': ['error', 'syntax'],

            // JSX에서 중복 props 금지
            'react/jsx-no-duplicate-props': ['error', { ignoreCase: true }],

            // target="_blank" 시 rel="noreferrer" 필수
            'react/jsx-no-target-blank': ['error', { enforceDynamicLinks: 'always' }],

            // 문자열 ref 금지 (createRef/useRef 사용)
            'react/no-string-refs': 'error',

            // 직접 state 변경 금지 (setState 사용)
            'react/no-direct-mutation-state': 'error',

            // 사용하지 않는 state 금지
            'react/no-unused-state': 'error',

            // 배열 렌더링 시 key 필수
            'react/jsx-key': ['error', { checkFragmentShorthand: true }],

            // children을 prop으로 전달 금지
            'react/no-children-prop': 'error',

            // dangerouslySetInnerHTML 사용 경고
            'react/no-danger': 'warn',

            // class → className
            'react/no-unknown-property': 'error',

            // void element에 children 금지 (<img>, <br> 등)
            'react/void-dom-elements-no-children': 'error',

            // style prop에 객체 사용
            'react/style-prop-object': 'error',

            // ============================================================
            // FairBid 커스텀: 접근성 가드레일 (기존 규칙 유지)
            // ============================================================

            // <img>에 alt 없이 사용 금지
            'jsx-a11y/alt-text': 'error',

            // 아이콘 버튼에 aria-label 없이 사용 금지
            'jsx-a11y/anchor-has-content': 'error',

            // <div onClick> 금지 → <button> 사용
            'jsx-a11y/click-events-have-key-events': 'error',
            'jsx-a11y/no-static-element-interactions': 'error',

            // 폼 인풋에 label 없이 사용 금지
            'jsx-a11y/label-has-associated-control': 'warn',

            // Airbnb 추가 접근성 규칙
            'jsx-a11y/anchor-is-valid': ['error', {
                aspects: ['noHref', 'invalidHref', 'preferButton'],
            }],
            'jsx-a11y/aria-role': ['error', { ignoreNonDOM: false }],
            'jsx-a11y/aria-props': 'error',
            'jsx-a11y/aria-proptypes': 'error',
            'jsx-a11y/aria-unsupported-elements': 'error',
            'jsx-a11y/heading-has-content': 'error',
            'jsx-a11y/html-has-lang': 'error',
            'jsx-a11y/img-redundant-alt': 'error',
            'jsx-a11y/no-access-key': 'error',
            'jsx-a11y/no-redundant-roles': 'error',
            'jsx-a11y/role-has-required-aria-props': 'error',
            'jsx-a11y/role-supports-aria-props': 'error',
            'jsx-a11y/tabindex-no-positive': 'error',

            // ============================================================
            // FairBid 커스텀: React Hooks 가드레일 (기존 규칙 유지)
            // ============================================================

            'react-hooks/rules-of-hooks': 'error',
            'react-hooks/exhaustive-deps': 'warn',

            // ============================================================
            // FairBid 커스텀: 코드 품질 (기존 규칙 유지)
            // ============================================================

            // 사용하지 않는 변수 금지
            'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],

            // console.log 경고 (디버깅 잔재 방지, warn/error 허용)
            'no-console': ['warn', { allow: ['warn', 'error'] }],
        },
    },
    // Prettier와 충돌하는 ESLint 포매팅 규칙 비활성화 (반드시 마지막)
    prettierConfig,
];

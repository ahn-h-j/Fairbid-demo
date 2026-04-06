import js from '@eslint/js';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import jsxA11yPlugin from 'eslint-plugin-jsx-a11y';

/**
 * FairBid ESLint 가드레일.
 * frontend/CLAUDE.md의 "절대 금지" 패턴을 물리적으로 차단한다.
 */
export default [
    js.configs.recommended,
    {
        files: ['src/**/*.{js,jsx}'],
        plugins: {
            react: reactPlugin,
            'react-hooks': reactHooksPlugin,
            'jsx-a11y': jsxA11yPlugin,
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
            },
        },
        settings: {
            react: { version: 'detect' },
        },
        rules: {
            // === 접근성 가드레일 ===

            // <img>에 alt 없이 사용 금지
            'jsx-a11y/alt-text': 'error',

            // 아이콘 버튼에 aria-label 없이 사용 금지
            'jsx-a11y/anchor-has-content': 'error',

            // <div onClick> 금지 → <button> 사용
            'jsx-a11y/click-events-have-key-events': 'error',
            'jsx-a11y/no-static-element-interactions': 'error',

            // 폼 인풋에 label 없이 사용 금지
            'jsx-a11y/label-has-associated-control': 'warn',

            // === React 가드레일 ===

            // React Hooks 규칙
            'react-hooks/rules-of-hooks': 'error',
            'react-hooks/exhaustive-deps': 'warn',

            // === CSS 가드레일 ===

            // transition-all 금지 → 속성 명시적 나열
            'no-restricted-syntax': ['error', {
                selector: 'Literal[value=/transition-all/], TemplateLiteral[quasis.0.value.raw=/transition-all/]',
                message: 'transition-all 금지. 속성을 명시적으로 나열하세요 (예: transition: opacity 200ms, transform 200ms)',
            }],

            // === 코드 품질 ===

            // 사용하지 않는 변수 금지
            'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],

            // console.log 경고 (디버깅 잔재 방지)
            'no-console': ['warn', { allow: ['warn', 'error'] }],
        },
    },
];

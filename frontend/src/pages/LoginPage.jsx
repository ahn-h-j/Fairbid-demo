import { useEffect } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth, AUTH_STATE } from '../contexts/AuthContext';

/** OAuth Provider별 브랜드 설정 */
const PROVIDERS = [
  {
    id: 'kakao',
    name: '카카오',
    bgColor: 'bg-[#FEE500]',
    textColor: 'text-[#191919]',
    hoverBg: 'hover:bg-[#F5DC00]',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M12 3C6.477 3 2 6.463 2 10.691c0 2.724 1.8 5.113 4.508 6.458-.2.744-.723 2.694-.828 3.112-.13.52.19.512.4.373.163-.109 2.612-1.778 3.671-2.497.733.105 1.49.163 2.249.163 5.523 0 10-3.463 10-7.609C22 6.463 17.523 3 12 3" />
      </svg>
    ),
  },
  {
    id: 'naver',
    name: '네이버',
    bgColor: 'bg-[#03C75A]',
    textColor: 'text-white',
    hoverBg: 'hover:bg-[#02B550]',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M13.37 12.196l-3.104-4.56H6.4v8.728h4.126v-4.564l3.104 4.564H17.6V7.636h-4.23v4.56z" />
      </svg>
    ),
  },
  {
    id: 'google',
    name: '구글',
    bgColor: 'bg-white',
    textColor: 'text-gray-700',
    hoverBg: 'hover:bg-gray-50',
    border: 'border border-gray-300',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
          fill="#4285F4"
        />
        <path
          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
          fill="#34A853"
        />
        <path
          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
          fill="#FBBC05"
        />
        <path
          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
          fill="#EA4335"
        />
      </svg>
    ),
  },
];

/** 에러 코드별 메시지 */
const ERROR_MESSAGES = {
  BLOCKED: '차단된 계정입니다. 관리자에게 문의해주세요.',
  EMAIL_REQUIRED: '이메일 동의가 필요합니다. 이메일 정보 제공에 동의해주세요.',
};

/**
 * 로그인 페이지
 * OAuth Provider 선택 화면을 제공한다.
 */
export default function LoginPage() {
  const { authState } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const error = searchParams.get('error');

  // 이미 로그인된 상태면 적절한 페이지로 리다이렉트
  useEffect(() => {
    if (authState === AUTH_STATE.AUTHENTICATED) {
      navigate('/auctions', { replace: true });
    } else if (authState === AUTH_STATE.ONBOARDING_REQUIRED) {
      navigate('/onboarding', { replace: true });
    }
  }, [authState, navigate]);

  /**
   * OAuth 로그인 시작
   * 이전 페이지 URL을 저장하고 서버 OAuth 엔드포인트로 이동한다.
   */
  const handleLogin = (providerId) => {
    const redirectPath = location.state?.from || '/auctions';
    localStorage.setItem('redirectAfterLogin', redirectPath);
    window.location.href = `/api/v1/auth/oauth2/${providerId}`;
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[70vh] px-4">
      <div className="w-full max-w-sm">
        {/* 로고 영역 */}
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl shadow-lg shadow-blue-500/25 mb-4">
            <svg
              className="w-8 h-8 text-white"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
              />
            </svg>
          </div>
          <h1 className="text-2xl font-extrabold text-gray-900">FairBid</h1>
          <p className="mt-1 text-sm text-gray-500">호구 없는 경매</p>
        </div>

        {/* 에러 메시지 */}
        {error && ERROR_MESSAGES[error] && (
          <div
            className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700"
            role="alert"
            aria-live="polite"
          >
            {ERROR_MESSAGES[error]}
          </div>
        )}

        {/* OAuth 로그인 버튼 */}
        <div className="space-y-3">
          {PROVIDERS.map((provider) => (
            <button
              key={provider.id}
              type="button"
              onClick={() => handleLogin(provider.id)}
              className={`w-full flex items-center justify-center gap-3 px-4 py-3 rounded-xl font-semibold text-[15px] transition-colors duration-200 ${provider.bgColor} ${provider.textColor} ${provider.hoverBg} ${provider.border || ''}`}
              aria-label={`${provider.name}로 로그인`}
            >
              {provider.icon}
              <span>{provider.name}로 로그인</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

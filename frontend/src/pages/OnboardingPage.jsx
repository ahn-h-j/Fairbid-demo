import { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiRequest } from '../api/client';

/**
 * 전화번호 자동 마스킹 (010-XXXX-XXXX)
 * @param {string} value - 입력값
 * @returns {string} 마스킹된 전화번호
 */
function formatPhoneNumber(value) {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

/**
 * 온보딩 페이지
 * 최초 가입 시 닉네임과 전화번호를 수집한다.
 */
export default function OnboardingPage() {
  const { updateAuthFromToken } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [nickname, setNickname] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [errors, setErrors] = useState({});
  const [nicknameStatus, setNicknameStatus] = useState(null); // null | 'checking' | 'available' | 'duplicate'
  const [isSubmitting, setIsSubmitting] = useState(false);

  const debounceTimer = useRef(null);

  /**
   * 닉네임 중복 확인 (debounce 적용)
   */
  const checkNickname = useCallback(async (value) => {
    if (value.length < 2 || value.length > 20) {
      setNicknameStatus(null);
      return;
    }

    setNicknameStatus('checking');
    try {
      const result = await apiRequest(
        `/users/check-nickname?nickname=${encodeURIComponent(value)}`,
      );
      setNicknameStatus(result.available ? 'available' : 'duplicate');
    } catch {
      setNicknameStatus(null);
    }
  }, []);

  /** 닉네임 입력 핸들러 */
  const handleNicknameChange = (e) => {
    const {value} = e.target;
    setNickname(value);
    setErrors((prev) => ({ ...prev, nickname: undefined }));

    // 기존 타이머 클리어 후 500ms debounce
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    if (value.length >= 2 && value.length <= 20) {
      debounceTimer.current = setTimeout(() => checkNickname(value), 500);
    } else {
      setNicknameStatus(null);
    }
  };

  /** 전화번호 입력 핸들러 */
  const handlePhoneChange = (e) => {
    const formatted = formatPhoneNumber(e.target.value);
    setPhoneNumber(formatted);
    setErrors((prev) => ({ ...prev, phoneNumber: undefined }));
  };

  /** 폼 유효성 검증 */
  const validate = () => {
    const newErrors = {};

    if (nickname.length < 2 || nickname.length > 20) {
      newErrors.nickname = '닉네임은 2~20자로 입력해주세요.';
    }
    if (nicknameStatus === 'checking') {
      newErrors.nickname = '닉네임 중복 확인 중입니다. 잠시 후 다시 시도해주세요.';
    }
    if (nicknameStatus === 'duplicate') {
      newErrors.nickname = '이미 사용 중인 닉네임입니다.';
    }

    const phoneDigits = phoneNumber.replace(/\D/g, '');
    if (!/^010\d{8}$/.test(phoneDigits)) {
      newErrors.phoneNumber = '올바른 전화번호를 입력해주세요. (010-XXXX-XXXX)';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  /** 온보딩 제출 */
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    setIsSubmitting(true);
    try {
      const result = await apiRequest('/users/me/onboarding', {
        method: 'POST',
        body: JSON.stringify({
          nickname: nickname.trim(),
          phoneNumber,
        }),
      });

      // 새 Access Token으로 인증 상태 갱신
      if (result.accessToken) {
        updateAuthFromToken(result.accessToken);
      }

      // location.state?.from을 우선 사용하고, 없으면 localStorage fallback
      const redirectPath =
        location.state?.from || localStorage.getItem('redirectAfterLogin') || '/';
      localStorage.removeItem('redirectAfterLogin');
      navigate(redirectPath, { replace: true });
    } catch (err) {
      if (err.code === 'NICKNAME_DUPLICATE') {
        setErrors({ nickname: '이미 사용 중인 닉네임입니다.' });
      } else if (err.code === 'PHONE_NUMBER_DUPLICATE') {
        setErrors({ phoneNumber: '이미 등록된 전화번호입니다.' });
      } else {
        setErrors({ form: err.message || '오류가 발생했습니다.' });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // 컴포넌트 언마운트 시 타이머 정리
  useEffect(() => {
    return () => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
    };
  }, []);

  return (
    <div className="flex flex-col items-center justify-center min-h-[70vh] px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-extrabold text-gray-900">프로필 설정</h1>
          <p className="mt-2 text-sm text-gray-500">서비스 이용을 위해 정보를 입력해주세요.</p>
        </div>

        {/* 폼 레벨 에러 */}
        {errors.form && (
          <div
            className="mb-4 p-3 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700"
            role="alert"
            aria-live="polite"
          >
            {errors.form}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5" noValidate>
          {/* 닉네임 */}
          <div>
            <label htmlFor="nickname" className="block text-sm font-semibold text-gray-700 mb-1.5">
              닉네임
            </label>
            <input
              id="nickname"
              type="text"
              value={nickname}
              onChange={handleNicknameChange}
              placeholder="2~20자 닉네임을 입력…"
              maxLength={20}
              autoComplete="nickname"
              spellCheck={false}
              className={`w-full px-4 py-3 text-sm border rounded-xl transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-blue-500/40 ${
                errors.nickname ? 'border-red-400 bg-red-50/50' : 'border-gray-200 bg-white'
              }`}
              aria-invalid={!!errors.nickname}
              aria-describedby={errors.nickname ? 'nickname-error' : undefined}
            />
            {/* 닉네임 상태 표시 */}
            {nicknameStatus === 'checking' && (
              <p className="mt-1 text-xs text-gray-400">확인 중…</p>
            )}
            {nicknameStatus === 'available' && !errors.nickname && (
              <p className="mt-1 text-xs text-green-600">사용 가능한 닉네임입니다.</p>
            )}
            {nicknameStatus === 'duplicate' && (
              <p className="mt-1 text-xs text-red-600">이미 사용 중인 닉네임입니다.</p>
            )}
            {errors.nickname && (
              <p id="nickname-error" className="mt-1 text-xs text-red-600" role="alert">
                {errors.nickname}
              </p>
            )}
          </div>

          {/* 전화번호 */}
          <div>
            <label
              htmlFor="phoneNumber"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              전화번호
            </label>
            <input
              id="phoneNumber"
              type="tel"
              inputMode="numeric"
              value={phoneNumber}
              onChange={handlePhoneChange}
              placeholder="010-0000-0000"
              autoComplete="tel"
              className={`w-full px-4 py-3 text-sm border rounded-xl transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-blue-500/40 ${
                errors.phoneNumber ? 'border-red-400 bg-red-50/50' : 'border-gray-200 bg-white'
              }`}
              aria-invalid={!!errors.phoneNumber}
              aria-describedby={errors.phoneNumber ? 'phone-error' : undefined}
            />
            {errors.phoneNumber && (
              <p id="phone-error" className="mt-1 text-xs text-red-600" role="alert">
                {errors.phoneNumber}
              </p>
            )}
          </div>

          {/* 제출 버튼 */}
          <button
            type="submit"
            disabled={isSubmitting || nicknameStatus === 'checking'}
            className="w-full py-3 bg-gradient-to-r from-blue-500 to-violet-600 text-white font-semibold text-sm rounded-xl shadow-lg shadow-blue-500/25 hover:shadow-blue-500/40 transition-shadow duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <span className="flex items-center justify-center gap-2">
                <svg
                  className="w-4 h-4 animate-spin"
                  fill="none"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <circle
                    className="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    strokeWidth="4"
                  />
                  <path
                    className="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                  />
                </svg>
                처리 중…
              </span>
            ) : (
              '시작하기'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}

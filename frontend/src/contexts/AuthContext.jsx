import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { setAccessToken, setAuthStateListener, decodeJwtPayload } from '../api/client';

/**
 * 인증 상태 상수
 * LOADING: 초기 세션 복원 중
 * UNAUTHENTICATED: 비로그인 상태
 * ONBOARDING_REQUIRED: 로그인은 됐지만 온보딩 미완료
 * AUTHENTICATED: 로그인 + 온보딩 완료
 */
export const AUTH_STATE = {
  LOADING: 'LOADING',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  ONBOARDING_REQUIRED: 'ONBOARDING_REQUIRED',
  AUTHENTICATED: 'AUTHENTICATED',
};

const AuthContext = createContext(null);

/**
 * 인증 상태 관리 Provider
 * 앱 초기화 시 Refresh Token으로 세션을 복원하고,
 * JWT의 onboarded 클레임으로 인증 상태를 결정한다.
 */
export function AuthProvider({ children }) {
  const [authState, setAuthState] = useState(AUTH_STATE.LOADING);
  const [user, setUser] = useState(null);

  /**
   * Access Token으로부터 사용자 정보와 인증 상태를 갱신한다.
   * @param {string} token - JWT Access Token
   */
  const updateAuthFromToken = useCallback((token) => {
    if (!token) {
      setAccessToken(null);
      setUser(null);
      setAuthState(AUTH_STATE.UNAUTHENTICATED);
      return;
    }

    setAccessToken(token);
    const payload = decodeJwtPayload(token);

    if (!payload) {
      setAccessToken(null);
      setUser(null);
      setAuthState(AUTH_STATE.UNAUTHENTICATED);
      return;
    }

    const userInfo = {
      userId: payload.sub,
      nickname: payload.nickname,
      onboarded: payload.onboarded,
      role: payload.role || 'USER',
    };
    setUser(userInfo);

    if (payload.onboarded) {
      setAuthState(AUTH_STATE.AUTHENTICATED);
    } else {
      setAuthState(AUTH_STATE.ONBOARDING_REQUIRED);
    }
  }, []);

  /**
   * Refresh Token으로 세션을 복원한다.
   * 앱 초기화 시 및 OAuth 콜백에서 호출된다.
   * @returns {Promise<string|null>} Access Token 또는 null
   */
  const restoreSession = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',
      });

      if (!response.ok) {
        setAuthState(AUTH_STATE.UNAUTHENTICATED);
        return null;
      }

      const data = await response.json();
      if (!data.success) {
        setAuthState(AUTH_STATE.UNAUTHENTICATED);
        return null;
      }

      const token = data.data.accessToken;
      updateAuthFromToken(token);
      return token;
    } catch {
      setAuthState(AUTH_STATE.UNAUTHENTICATED);
      return null;
    }
  }, [updateAuthFromToken]);

  /**
   * 로그아웃 처리
   * 서버에 로그아웃 요청 후 클라이언트 상태를 초기화한다.
   */
  const logout = useCallback(async () => {
    try {
      await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // 로그아웃 요청 실패해도 클라이언트는 초기화
    }
    setAccessToken(null);
    setUser(null);
    setAuthState(AUTH_STATE.UNAUTHENTICATED);
  }, []);

  // 앱 초기화 시 세션 복원
  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  // 401 갱신 실패 시 로그아웃 처리를 위한 리스너 등록
  useEffect(() => {
    setAuthStateListener((state) => {
      if (state === 'UNAUTHENTICATED') {
        setUser(null);
        setAuthState(AUTH_STATE.UNAUTHENTICATED);
      }
    });
    return () => setAuthStateListener(null);
  }, []);

  const value = useMemo(
    () => ({
      authState,
      user,
      updateAuthFromToken,
      restoreSession,
      logout,
    }),
    [authState, user, updateAuthFromToken, restoreSession, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * AuthContext 소비용 훅
 * @returns {{ authState, user, updateAuthFromToken, restoreSession, logout }}
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth는 AuthProvider 내부에서만 사용할 수 있습니다.');
  }
  return context;
}

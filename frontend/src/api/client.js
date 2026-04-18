const API_BASE = '/api/v1';

/**
 * 서버-클라이언트 시간 오프셋 (밀리초)
 * 모든 API 응답의 serverTime으로 갱신된다.
 */
let serverTimeOffset = 0;

/** Access Token (메모리 저장) */
let accessToken = null;

/** 토큰 갱신 진행 중인 Promise (중복 갱신 방지) */
let refreshPromise = null;

/** 인증 상태 변경 리스너 (AuthContext에서 등록) */
let onAuthStateChange = null;

/**
 * 서버 시간 기준의 현재 시각을 반환
 * @returns {Date} 서버 시간으로 보정된 현재 시각
 */
export function getServerTime() {
  return new Date(Date.now() + serverTimeOffset);
}

/**
 * Access Token 설정
 * @param {string|null} token
 */
export function setAccessToken(token) {
  accessToken = token;
}

/**
 * 현재 Access Token 반환
 * @returns {string|null}
 */
export function getAccessToken() {
  return accessToken;
}

/**
 * 인증 상태 변경 리스너 등록
 * AuthContext에서 호출하여 401 갱신 실패 시 로그아웃 처리에 사용한다.
 * @param {Function|null} listener
 */
export function setAuthStateListener(listener) {
  onAuthStateChange = listener;
}

/**
 * JWT payload를 디코딩하여 반환
 * 라이브러리 없이 base64url 디코딩으로 처리한다.
 * @param {string} token - JWT 문자열
 * @returns {object|null} 디코딩된 payload 또는 null
 */
export function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1];
    // base64url → base64 변환 후 패딩 추가 (JWT 표준에서 패딩은 생략됨)
    let base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    while (base64.length % 4) base64 += '=';
    const jsonStr = decodeURIComponent(
      globalThis
        .atob(base64)
        .split('')
        .map((c) => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join(''),
    );
    return JSON.parse(jsonStr);
  } catch {
    return null;
  }
}

/**
 * API 에러 클래스
 * 서버에서 반환한 에러 코드와 메시지를 포함한다.
 */
export class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.code = code;
    this.status = status;
    this.name = 'ApiError';
  }
}

/**
 * Refresh Token으로 Access Token 갱신
 * 중복 갱신 방지를 위해 단일 Promise를 공유한다.
 * @returns {Promise<string>} 새 Access Token
 */
async function refreshAccessToken() {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
      });

      if (!response.ok) {
        throw new ApiError('REFRESH_FAILED', '세션이 만료되었습니다.', response.status);
      }

      const data = await response.json();
      if (!data.success) {
        throw new ApiError(
          data.error?.code || 'REFRESH_FAILED',
          data.error?.message || '토큰 갱신에 실패했습니다.',
          401,
        );
      }

      const newToken = data.data.accessToken;
      setAccessToken(newToken);
      return newToken;
    } catch (err) {
      setAccessToken(null);
      if (onAuthStateChange) {
        onAuthStateChange('UNAUTHENTICATED');
      }
      throw err;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

/**
 * API 요청 공통 래퍼
 * - Authorization: Bearer 헤더 자동 주입
 * - serverTime 오프셋 자동 갱신
 * - 401 발생 시 토큰 갱신 후 재시도
 * - 에러 시 ApiError throw
 *
 * @param {string} endpoint - API 엔드포인트 (예: "/auctions")
 * @param {RequestInit} options - fetch 옵션
 * @returns {Promise<any>} 응답 데이터 (data 필드)
 */
export async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;

  const buildConfig = (token) => ({
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  let response;
  try {
    response = await fetch(url, buildConfig(accessToken));
  } catch (err) {
    throw new ApiError('NETWORK_ERROR', `네트워크 오류가 발생했습니다: ${err.message}`);
  }

  // 401 발생 시 토큰 갱신 후 재시도
  if (response.status === 401 && accessToken) {
    try {
      const newToken = await refreshAccessToken();
      response = await fetch(url, buildConfig(newToken));
    } catch {
      throw new ApiError('TOKEN_EXPIRED', '인증이 만료되었습니다. 다시 로그인해주세요.', 401);
    }
  }

  // 비로그인 상태에서 인증이 필요한 API 호출 시 (401/403)
  if ((response.status === 401 || response.status === 403) && !accessToken) {
    throw new ApiError('UNAUTHORIZED', '로그인이 필요합니다.', response.status);
  }

  let data;
  try {
    data = await response.json();
  } catch {
    // JSON 파싱 실패 시 상태코드 기반 에러 처리
    if (response.status === 401 || response.status === 403) {
      throw new ApiError('UNAUTHORIZED', '로그인이 필요합니다.', response.status);
    }
    throw new ApiError('PARSE_ERROR', '서버 응답을 처리할 수 없습니다.', response.status);
  }

  // 서버 시간 오프셋 갱신
  if (data.serverTime) {
    serverTimeOffset = new Date(data.serverTime).getTime() - Date.now();
  }

  if (!data.success) {
    throw new ApiError(
      data.error?.code || 'UNKNOWN',
      data.error?.message || '알 수 없는 오류가 발생했습니다.',
      response.status,
    );
  }

  return data.data;
}

/**
 * SWR용 fetcher 함수
 * @param {string} endpoint - API 엔드포인트
 * @returns {Promise<any>}
 */
export const fetcher = (endpoint) => apiRequest(endpoint);

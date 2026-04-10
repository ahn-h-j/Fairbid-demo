import useSWR from 'swr';
import { fetcher, apiRequest } from './client';

/**
 * 거래 상세 조회 훅
 * 인증은 JWT 토큰으로 처리됨 (서버에서 SecurityUtils로 userId 추출)
 * @param {string|number|null} tradeId - 거래 ID
 */
export function useTrade(tradeId) {
  const { data, error, isLoading, mutate } = useSWR(tradeId ? `/trades/${tradeId}` : null, fetcher);

  return {
    trade: data,
    isLoading,
    isError: error,
    mutate,
  };
}

/**
 * 내 거래 목록 조회 훅
 * 인증은 JWT 토큰으로 처리됨
 */
export function useMyTrades() {
  const { data, error, isLoading, mutate } = useSWR('/trades/my', fetcher);

  return {
    trades: data || [],
    isLoading,
    isError: error,
    mutate,
  };
}

/**
 * 거래 방식 선택
 * @param {string|number} tradeId - 거래 ID
 * @param {string} method - 거래 방식 (DIRECT | DELIVERY)
 */
export async function selectTradeMethod(tradeId, method) {
  return apiRequest(`/trades/${tradeId}/method`, {
    method: 'POST',
    body: JSON.stringify({ method }),
  });
}

/**
 * 거래 완료
 * @param {string|number} tradeId - 거래 ID
 */
export async function completeTrade(tradeId) {
  return apiRequest(`/trades/${tradeId}/complete`, {
    method: 'POST',
  });
}

/**
 * 직거래 시간 제안
 * @param {string|number} tradeId - 거래 ID
 * @param {string} meetingDate - 만남 날짜 (YYYY-MM-DD)
 * @param {string} meetingTime - 만남 시간 (HH:mm)
 */
export async function proposeDirectTrade(tradeId, meetingDate, meetingTime) {
  return apiRequest(`/trades/${tradeId}/direct/propose`, {
    method: 'POST',
    body: JSON.stringify({ meetingDate, meetingTime }),
  });
}

/**
 * 직거래 수락
 * @param {string|number} tradeId - 거래 ID
 */
export async function acceptDirectTrade(tradeId) {
  return apiRequest(`/trades/${tradeId}/direct/accept`, {
    method: 'POST',
  });
}

/**
 * 직거래 역제안
 * @param {string|number} tradeId - 거래 ID
 * @param {string} meetingDate - 만남 날짜 (YYYY-MM-DD)
 * @param {string} meetingTime - 만남 시간 (HH:mm)
 */
export async function counterProposeDirectTrade(tradeId, meetingDate, meetingTime) {
  return apiRequest(`/trades/${tradeId}/direct/counter`, {
    method: 'POST',
    body: JSON.stringify({ meetingDate, meetingTime }),
  });
}

/**
 * 배송지 입력
 * @param {string|number} tradeId - 거래 ID
 * @param {object} addressData - 배송지 정보
 */
export async function submitAddress(tradeId, addressData) {
  return apiRequest(`/trades/${tradeId}/delivery/address`, {
    method: 'POST',
    body: JSON.stringify(addressData),
  });
}

/**
 * 송장 입력
 * @param {string|number} tradeId - 거래 ID
 * @param {object} shippingData - 송장 정보
 */
export async function shipDelivery(tradeId, shippingData) {
  return apiRequest(`/trades/${tradeId}/delivery/ship`, {
    method: 'POST',
    body: JSON.stringify(shippingData),
  });
}

/**
 * 입금 완료 확인 (구매자)
 * @param {string|number} tradeId - 거래 ID
 */
export async function confirmPayment(tradeId) {
  return apiRequest(`/trades/${tradeId}/delivery/payment`, {
    method: 'POST',
  });
}

/**
 * 입금 확인 (판매자)
 * 판매자가 구매자의 입금을 확인한다.
 * @param {string|number} tradeId - 거래 ID
 */
export async function verifyPayment(tradeId) {
  return apiRequest(`/trades/${tradeId}/delivery/payment/verify`, {
    method: 'POST',
  });
}

/**
 * 미입금 처리 (판매자)
 * 판매자가 입금을 확인하지 못한 경우 호출한다.
 * @param {string|number} tradeId - 거래 ID
 */
export async function rejectPayment(tradeId) {
  return apiRequest(`/trades/${tradeId}/delivery/payment/reject`, {
    method: 'POST',
  });
}

/**
 * 수령 확인
 * @param {string|number} tradeId - 거래 ID
 */
export async function confirmDelivery(tradeId) {
  return apiRequest(`/trades/${tradeId}/delivery/confirm`, {
    method: 'POST',
  });
}

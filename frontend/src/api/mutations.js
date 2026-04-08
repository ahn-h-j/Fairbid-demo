import { apiRequest } from './client';

/**
 * 경매 등록 API 호출
 *
 * @param {object} auctionData - 경매 생성 데이터
 * @param {string} auctionData.title - 제목
 * @param {string} [auctionData.description] - 설명
 * @param {string} auctionData.category - 카테고리 코드
 * @param {number} auctionData.startPrice - 시작 가격
 * @param {number} [auctionData.instantBuyPrice] - 즉시 구매가
 * @param {string} auctionData.duration - 경매 기간 (HOURS_24/HOURS_48)
 * @returns {Promise<object>} 생성된 경매 응답
 */
export async function createAuction(auctionData) {
  return apiRequest('/auctions', {
    method: 'POST',
    body: JSON.stringify(auctionData),
  });
}

/**
 * 입찰 API 호출
 *
 * @param {string|number} auctionId - 경매 ID
 * @param {object} bidData - 입찰 데이터
 * @param {string} bidData.bidType - 입찰 타입 (ONE_TOUCH/DIRECT/INSTANT_BUY)
 * @param {number} [bidData.amount] - 입찰 금액 (DIRECT 타입 시 필수)
 * @returns {Promise<object>} 입찰 응답
 */
export async function placeBid(auctionId, bidData) {
  return apiRequest(`/auctions/${auctionId}/bids`, {
    method: 'POST',
    body: JSON.stringify(bidData),
  });
}

/**
 * AI 경매 어시스턴트 호출
 *
 * 이미지와 (선택적인) 카테고리/구조화 메모를 보내면 시작가 추천(low/mid/high)과
 * 상품 설명(Markdown)을 받는다.
 *
 * 폼의 title/category 입력과 독립적으로 동작한다 — 사용자는 이미지만 올리고도
 * 추천을 받을 수 있고, 받은 결과를 자유롭게 수정/무시할 수 있다.
 *
 * @param {object} payload
 * @param {string} [payload.category] - 카테고리 코드 (선택, 미지정 시 AI 가 추론)
 * @param {string} [payload.memo] - 구조화 힌트를 자연어로 조립한 문자열 (선택)
 * @param {string[]} payload.imageUrls - 이미지 URL 배열 (1~5장, 필수)
 * @returns {Promise<{
 *   suggestedPrices: {low: number, mid: number, high: number},
 *   generatedDescription: string
 * }>}
 */
export async function requestAiAuctionAssist(payload) {
  return apiRequest('/ai/auction-assist', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}


import useSWR from 'swr';
import { fetcher } from './client';

/**
 * 단일 경매 상세 조회 SWR 훅
 * WebSocket이 실시간 업데이트를 담당하므로 포커스 시 재검증을 비활성화한다.
 *
 * @param {string|number|null} auctionId - 경매 ID (null이면 요청하지 않음)
 * @returns {{ auction, error, isLoading, mutate }}
 */
export function useAuction(auctionId) {
  const { data, error, isLoading, mutate } = useSWR(
    auctionId ? `/auctions/${auctionId}` : null,
    fetcher,
    {
      revalidateOnFocus: false, // WebSocket이 실시간 업데이트 처리
    },
  );

  return { auction: data, error, isLoading, mutate };
}

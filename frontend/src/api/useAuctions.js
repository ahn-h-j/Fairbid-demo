import useSWR from 'swr';
import { fetcher } from './client';

/**
 * 경매 목록 조회 SWR 훅
 *
 * @param {object} params - 검색 파라미터
 * @param {string} [params.keyword] - 검색 키워드
 * @param {string} [params.status] - 상태 필터 (BIDDING, CLOSED 등)
 * @param {string} [params.category] - 카테고리 필터
 * @param {string} [params.sort] - 정렬 기준 (예: "createdAt,DESC")
 * @param {number} [params.page=0] - 페이지 번호 (0-based)
 * @param {number} [params.size=12] - 페이지 크기
 * @returns {{ auctions, totalPages, totalElements, error, isLoading, mutate }}
 */
export function useAuctions({ keyword, status, category, sort, page = 0, size = 12 } = {}) {
  const params = new URLSearchParams();
  if (keyword) params.set('keyword', keyword);
  if (status) params.set('status', status);
  if (category) params.set('category', category);
  if (sort) params.set('sort', sort);
  if (page > 0) params.set('page', page.toString());
  if (size !== 12) params.set('size', size.toString());

  const queryString = params.toString();
  const key = queryString ? `/auctions?${queryString}` : '/auctions';

  const { data, error, isLoading, mutate } = useSWR(key, fetcher, {
    refreshInterval: 30000, // 30초마다 자동 갱신
    revalidateOnFocus: true, // 탭 복귀 시 재검증
  });

  return {
    auctions: data?.content || data || [],
    totalPages: data?.totalPages || 0,
    totalElements: data?.totalElements || 0,
    error,
    isLoading,
    mutate,
  };
}

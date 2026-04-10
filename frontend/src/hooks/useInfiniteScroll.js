import { useCallback, useEffect, useRef } from 'react';
import useSWRInfinite from 'swr/infinite';
import { fetcher } from '../api/client';

/**
 * Cursor 기반 무한스크롤 훅
 * SWR Infinite를 사용하여 cursor 기반 페이지네이션을 처리한다.
 *
 * @param {string} baseEndpoint - 기본 API 엔드포인트 (예: "/users/me/auctions")
 * @param {object} [params={}] - 추가 쿼리 파라미터 (예: { status: 'ACTIVE' })
 * @param {number} [pageSize=20] - 페이지 사이즈
 * @returns {{ items, isLoading, isLoadingMore, hasMore, loadMore, mutate, error }}
 */
export default function useInfiniteScroll(baseEndpoint, params = {}, pageSize = 20) {
  /**
   * SWR Infinite getKey 함수
   * 이전 페이지 데이터의 마지막 항목 ID를 cursor로 사용한다.
   */
  const getKey = (pageIndex, previousPageData) => {
    // 이전 페이지가 비어있으면 더 이상 페이지 없음
    if (previousPageData && previousPageData.items?.length === 0) return null;

    const searchParams = new URLSearchParams();
    searchParams.set('size', pageSize.toString());

    // 추가 파라미터 설정
    Object.entries(params).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        searchParams.set(key, value);
      }
    });

    // 첫 페이지가 아니면 cursor 설정
    if (pageIndex > 0 && previousPageData?.items?.length > 0) {
      const lastItem = previousPageData.items[previousPageData.items.length - 1];
      searchParams.set('cursor', lastItem.id || lastItem.auctionId);
    }

    return `${baseEndpoint}?${searchParams.toString()}`;
  };

  const { data, error, size, setSize, isLoading, mutate } = useSWRInfinite(getKey, fetcher, {
    revalidateFirstPage: true,
    revalidateOnFocus: true,
  });

  // params 변경 시 페이지네이션을 첫 페이지로 리셋
  const prevParamsRef = useRef(params);
  useEffect(() => {
    const prev = prevParamsRef.current;
    const changed = JSON.stringify(prev) !== JSON.stringify(params);
    if (changed) {
      prevParamsRef.current = params;
      setSize(1);
    }
  }, [params, setSize]);

  // 전체 아이템 목록 평탄화
  const items = data ? data.flatMap((page) => page.items || []) : [];

  // 더 불러올 데이터가 있는지 확인 (백엔드의 hasNext 필드 사용)
  const hasMore = data ? (data[data.length - 1]?.hasNext ?? false) : false;

  // 추가 로딩 중 여부
  const isLoadingMore = isLoading || (size > 0 && data && typeof data[size - 1] === 'undefined');

  /** 다음 페이지 로드 (메모이제이션으로 Observer 재생성 방지) */
  const loadMore = useCallback(() => {
    if (!isLoadingMore && hasMore) {
      setSize((prev) => prev + 1);
    }
  }, [isLoadingMore, hasMore, setSize]);

  return {
    items,
    isLoading: isLoading && !data,
    isLoadingMore,
    hasMore,
    loadMore,
    mutate,
    error,
  };
}

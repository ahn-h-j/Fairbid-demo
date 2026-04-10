import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuctions } from '../api/useAuctions';
import AuctionCard from '../components/AuctionCard';
import Pagination from '../components/Pagination';
import Spinner from '../components/Spinner';
import SplashScreen from '../components/SplashScreen';
import { CATEGORIES, STATUSES } from '../utils/constants';

/**
 * 경매 목록 페이지
 * 필터(상태, 카테고리), 정렬, 검색, 페이지네이션을 URL 쿼리 파라미터로 관리한다.
 */
export default function AuctionListPage() {
  // 세션당 한 번만 스플래시 표시 (sessionStorage 사용)
  const [showSplash, setShowSplash] = useState(() => {
    return !sessionStorage.getItem('fairbid_splash_shown');
  });

  // 스플래시 완료 처리
  const handleSplashComplete = () => {
    sessionStorage.setItem('fairbid_splash_shown', 'true');
    setShowSplash(false);
  };

  const [searchParams, setSearchParams] = useSearchParams();
  const keywordParam = searchParams.get('keyword') || '';
  const [searchInput, setSearchInput] = useState(keywordParam);

  // 뒤로가기/앞으로가기 시 입력값을 URL keyword와 동기화
  useEffect(() => {
    setSearchInput(keywordParam);
  }, [keywordParam]);

  const params = {
    keyword: searchParams.get('keyword') || '',
    status: searchParams.get('status') || '',
    category: searchParams.get('category') || '',
    sort: searchParams.get('sort') || '',
    page: parseInt(searchParams.get('page')) || 0,
  };

  const { auctions, totalPages, isLoading, error } = useAuctions(params);

  const updateParams = (updates) => {
    const newParams = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (value) {
        newParams.set(key, value);
      } else {
        newParams.delete(key);
      }
    });
    if (!('page' in updates)) {
      newParams.delete('page');
    }
    setSearchParams(newParams);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    updateParams({ keyword: searchInput });
  };

  // 스플래시 화면 표시 중이면 스플래시만 렌더링
  if (showSplash) {
    return <SplashScreen onComplete={handleSplashComplete} />;
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* 페이지 헤더 */}
      <div>
        <h1 className="text-[22px] font-bold text-gray-900 tracking-tight">경매 목록</h1>
        <p className="text-[13px] text-gray-400 mt-0.5">실시간 경쟁 입찰로 적정가를 찾아보세요</p>
      </div>

      {/* 검색 + 필터 바 */}
      <div className="bg-white/80 backdrop-blur-sm rounded-2xl p-4 ring-1 ring-black/[0.04] shadow-sm">
        <div className="flex flex-col sm:flex-row gap-3">
          {/* 검색 */}
          <form onSubmit={handleSearch} className="flex-1 flex gap-2">
            <div className="relative flex-1">
              <svg
                className="absolute left-3.5 top-1/2 -translate-y-1/2 w-[15px] h-[15px] text-gray-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2.5}
                  d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                />
              </svg>
              <input
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="경매 검색…"
                className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 input-glow transition-colors duration-200"
                aria-label="경매 검색"
              />
            </div>
            <button
              type="submit"
              className="px-5 py-2.5 bg-gray-900 text-white text-[13px] font-semibold rounded-xl hover:bg-gray-800 transition-colors btn-press shadow-sm"
            >
              검색
            </button>
          </form>

          {/* 필터 */}
          <div className="flex gap-2 flex-wrap">
            <select
              value={params.status}
              onChange={(e) => updateParams({ status: e.target.value })}
              className="px-3 py-2.5 bg-gray-50 border-0 rounded-xl text-[13px] text-gray-600 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-colors duration-200 cursor-pointer"
              aria-label="상태 필터"
            >
              {/* 기본값: 진행중인 경매 (BIDDING + INSTANT_BUY_PENDING) */}
              <option value="">진행중</option>
              <option value="ENDED">{STATUSES.ENDED.label}</option>
              <option value="FAILED">{STATUSES.FAILED.label}</option>
              <option value="CANCELLED">{STATUSES.CANCELLED.label}</option>
            </select>

            <select
              value={params.category}
              onChange={(e) => updateParams({ category: e.target.value })}
              className="px-3 py-2.5 bg-gray-50 border-0 rounded-xl text-[13px] text-gray-600 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-colors duration-200 cursor-pointer"
              aria-label="카테고리 필터"
            >
              <option value="">전체 카테고리</option>
              {Object.entries(CATEGORIES).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>

            <select
              value={params.sort}
              onChange={(e) => updateParams({ sort: e.target.value })}
              className="px-3 py-2.5 bg-gray-50 border-0 rounded-xl text-[13px] text-gray-600 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-colors duration-200 cursor-pointer"
              aria-label="정렬 기준"
            >
              <option value="">최신순</option>
              <option value="currentPrice,DESC">높은 가격순</option>
              <option value="currentPrice,ASC">낮은 가격순</option>
              <option value="totalBidCount,DESC">입찰 많은순</option>
            </select>
          </div>
        </div>
      </div>

      {/* 콘텐츠 */}
      {isLoading && (
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      )}
      {!isLoading && error && (
        <div className="text-center py-24 animate-fade-in">
          <div className="w-16 h-16 mx-auto mb-4 bg-red-50 rounded-2xl flex items-center justify-center">
            <svg
              className="w-7 h-7 text-red-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"
              />
            </svg>
          </div>
          <p className="text-gray-700 font-semibold text-[15px]">경매 목록을 불러올 수 없습니다</p>
          <p className="text-sm text-gray-400 mt-1.5">{error.message}</p>
        </div>
      )}
      {!isLoading && !error && auctions.length === 0 && (
        <div className="text-center py-24 animate-fade-in">
          <div className="w-20 h-20 mx-auto mb-4 bg-gray-50 rounded-3xl flex items-center justify-center">
            <svg
              className="w-9 h-9 text-gray-300"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"
              />
            </svg>
          </div>
          <p className="text-gray-600 font-semibold text-[15px]">등록된 경매가 없습니다</p>
          <p className="text-sm text-gray-400 mt-1.5">새로운 경매를 등록해보세요</p>
        </div>
      )}
      {!isLoading && !error && auctions.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
            {auctions.map((auction) => (
              <AuctionCard key={auction.id} auction={auction} />
            ))}
          </div>

          <Pagination
            currentPage={params.page}
            totalPages={totalPages}
            onPageChange={(page) => updateParams({ page: page.toString() })}
          />
        </>
      )}
    </div>
  );
}

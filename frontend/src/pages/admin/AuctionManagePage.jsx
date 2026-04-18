import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getAdminAuctionList } from '../../api/admin';
import { apiRequest } from '../../api/client';
import StatusBadge from '../../components/StatusBadge';
import LoadingSpinner from '../../components/LoadingSpinner';

/**
 * 경매 관리 페이지
 * 경매 목록 조회 및 관리
 */
export default function AuctionManagePage() {
  // 필터 상태
  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');

  // 페이지네이션 상태
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);

  // 로딩 및 에러 상태
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 노쇼 테스트 상태
  const [noShowResult, setNoShowResult] = useState(null);
  const [noShowLoading, setNoShowLoading] = useState(null);

  // 새로고침 트리거
  const [refreshKey, setRefreshKey] = useState(0);

  // 데이터 로드
  useEffect(() => {
    async function fetchAuctions() {
      setLoading(true);
      setError(null);

      try {
        const data = await getAdminAuctionList({
          status: status || null,
          keyword: searchKeyword || null,
          page,
          size: 15,
        });
        setPageData(data);
      } catch (err) {
        console.error('경매 목록 조회 실패:', err);
        setError(err.message || '경매 목록을 불러오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    }

    fetchAuctions();
  }, [status, searchKeyword, page, refreshKey]);

  // 검색 핸들러
  const handleSearch = (e) => {
    e.preventDefault();
    setSearchKeyword(keyword);
    setPage(0);
  };

  // 상태 변경 핸들러
  const handleStatusChange = (e) => {
    setStatus(e.target.value);
    setPage(0);
  };

  // 노쇼 강제 처리
  const handleForceNoShow = async (auctionId) => {
    if (!window.confirm(`경매 #${auctionId}의 1순위 낙찰자를 노쇼 처리하시겠습니까?`)) return;

    setNoShowLoading(auctionId);
    setNoShowResult(null);

    try {
      const result = await apiRequest(`/test/auctions/${auctionId}/force-noshow`, {
        method: 'POST',
      });
      setNoShowResult({ auctionId, success: true, data: result });
      // 목록 새로고침
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setNoShowResult({ auctionId, success: false, error: err.message });
    } finally {
      setNoShowLoading(null);
    }
  };

  // 낙찰 상태 조회
  const handleCheckWinningStatus = async (auctionId) => {
    setNoShowLoading(auctionId);
    try {
      const result = await apiRequest(`/test/auctions/${auctionId}/winning-status`);
      setNoShowResult({ auctionId, success: true, type: 'status', data: result });
    } catch (err) {
      setNoShowResult({ auctionId, success: false, error: err.message });
    } finally {
      setNoShowLoading(null);
    }
  };

  const auctions = pageData?.content || [];
  const totalPages = pageData?.totalPages || 0;
  const totalElements = pageData?.totalElements || 0;

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">경매 관리</h1>
          <p className="text-sm text-gray-500 mt-1">총 {totalElements.toLocaleString()}건의 경매</p>
        </div>
      </div>

      {/* 검색/필터 */}
      <form onSubmit={handleSearch} className="flex flex-col sm:flex-row gap-2 sm:gap-3">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="경매 제목 검색..."
          className="flex-1 px-4 py-2.5 bg-white rounded-xl ring-1 ring-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <div className="flex gap-2">
          <select
            value={status}
            onChange={handleStatusChange}
            className="flex-1 sm:flex-none px-4 py-2.5 bg-white rounded-xl ring-1 ring-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">전체 상태</option>
            <option value="BIDDING">진행중</option>
            <option value="ENDED">낙찰</option>
            <option value="FAILED">유찰</option>
            <option value="CANCELLED">취소</option>
          </select>
          <button
            type="submit"
            className="px-5 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors"
          >
            검색
          </button>
        </div>
      </form>

      {/* 에러 표시 */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
        </div>
      )}

      {/* 노쇼 테스트 결과 */}
      {noShowResult && (
        <div
          className={`p-4 rounded-lg border ${
            noShowResult.success
              ? 'bg-white border-gray-200'
              : 'bg-red-50 border-red-200 text-red-700'
          }`}
        >
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <p className="font-semibold mb-3 text-gray-900">
                경매 #{noShowResult.auctionId}{' '}
                {noShowResult.type === 'status' ? '상태 조회' : '노쇼 처리'} 결과
              </p>
              {noShowResult.success ? (
                <WinningStatusDisplay
                  data={noShowResult.data}
                  isNoShow={noShowResult.type !== 'status'}
                />
              ) : (
                <p>{noShowResult.error}</p>
              )}
            </div>
            <button
              type="button"
              onClick={() => setNoShowResult(null)}
              className="text-gray-400 hover:text-gray-600 ml-2 text-xl"
            >
              &times;
            </button>
          </div>
        </div>
      )}

      {/* 모바일: 카드 리스트 */}
      <div className="sm:hidden space-y-3">
        {loading && (
          <div className="py-12 text-center">
            <LoadingSpinner />
          </div>
        )}
        {!loading && auctions.length === 0 && (
          <div className="py-12 text-center text-gray-400 text-sm">경매가 없습니다</div>
        )}
        {!loading &&
          auctions.length > 0 &&
          auctions.map((auction) => (
            <div
              key={auction.id}
              className="bg-white rounded-xl p-4 ring-1 ring-gray-100 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-900 truncate">{auction.title}</p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    #{auction.id} · {auction.sellerNickname}
                  </p>
                </div>
                <StatusBadge status={auction.status} />
              </div>
              <div className="flex items-center justify-between text-sm">
                <div>
                  <span className="text-gray-500">현재가</span>
                  <span className="font-semibold text-gray-900 ml-1">
                    {auction.currentPrice.toLocaleString()}원
                  </span>
                </div>
                <div className="text-xs text-gray-500">
                  입찰 {auction.totalBidCount} · 연장 {auction.extensionCount || 0}
                </div>
              </div>
              <div className="flex items-center gap-2 mt-3 pt-3 border-t border-gray-100">
                <Link
                  to={`/auctions/${auction.id}`}
                  className="flex-1 py-2 text-center bg-indigo-50 text-indigo-600 text-xs font-medium rounded-lg"
                >
                  보기
                </Link>
                {auction.status === 'ENDED' && (
                  <>
                    <button
                      type="button"
                      onClick={() => handleCheckWinningStatus(auction.id)}
                      disabled={noShowLoading === auction.id}
                      className="flex-1 py-2 bg-gray-100 text-gray-600 text-xs font-medium rounded-lg disabled:opacity-50"
                    >
                      상태
                    </button>
                    <button
                      type="button"
                      onClick={() => handleForceNoShow(auction.id)}
                      disabled={noShowLoading === auction.id}
                      className="flex-1 py-2 bg-red-50 text-red-600 text-xs font-medium rounded-lg disabled:opacity-50"
                    >
                      {noShowLoading === auction.id ? '...' : '노쇼'}
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
      </div>

      {/* PC: 테이블 */}
      <div className="hidden sm:block bg-white rounded-2xl shadow-sm ring-1 ring-gray-100 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[800px]">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-16">
                  ID
                </th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase whitespace-nowrap">
                  제목
                </th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-24">
                  판매자
                </th>
                <th className="px-3 py-3 text-right text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-28">
                  현재가
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-16">
                  입찰
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-16">
                  연장
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-20">
                  상태
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-20" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center">
                    <LoadingSpinner />
                  </td>
                </tr>
              )}
              {!loading && auctions.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-gray-400 text-sm">
                    경매가 없습니다
                  </td>
                </tr>
              )}
              {!loading &&
                auctions.length > 0 &&
                auctions.map((auction) => (
                  <tr key={auction.id} className="hover:bg-gray-50">
                    <td className="px-3 py-3 text-sm text-gray-500">{auction.id}</td>
                    <td className="px-3 py-3">
                      <span className="text-sm font-medium text-gray-900 line-clamp-1">
                        {auction.title}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-sm text-gray-600 truncate max-w-[100px]">
                      {auction.sellerNickname}
                    </td>
                    <td className="px-3 py-3 text-sm font-semibold text-gray-900 text-right whitespace-nowrap">
                      {auction.currentPrice.toLocaleString()}원
                    </td>
                    <td className="px-3 py-3 text-sm text-gray-600 text-center">
                      {auction.totalBidCount}
                    </td>
                    <td className="px-3 py-3 text-sm text-center">
                      {auction.extensionCount > 0 ? (
                        <span className="text-amber-600 font-medium">{auction.extensionCount}</span>
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </td>
                    <td className="px-3 py-3 text-center">
                      <StatusBadge status={auction.status} />
                    </td>
                    <td className="px-3 py-3 text-center">
                      <div className="flex items-center justify-center gap-1">
                        <Link
                          to={`/auctions/${auction.id}`}
                          className="inline-block px-2 py-1 bg-indigo-50 text-indigo-600 hover:bg-indigo-100 text-xs font-medium rounded-lg transition-colors"
                        >
                          보기
                        </Link>
                        {auction.status === 'ENDED' && (
                          <>
                            <button
                              type="button"
                              onClick={() => handleCheckWinningStatus(auction.id)}
                              disabled={noShowLoading === auction.id}
                              className="px-2 py-1 bg-gray-100 text-gray-600 hover:bg-gray-200 text-xs font-medium rounded-lg transition-colors disabled:opacity-50"
                            >
                              상태
                            </button>
                            <button
                              type="button"
                              onClick={() => handleForceNoShow(auction.id)}
                              disabled={noShowLoading === auction.id}
                              className="px-2 py-1 bg-red-50 text-red-600 hover:bg-red-100 text-xs font-medium rounded-lg transition-colors disabled:opacity-50"
                            >
                              {noShowLoading === auction.id ? '...' : '노쇼'}
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 p-4 border-t border-gray-100">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              이전
            </button>
            <span className="text-sm text-gray-600">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              다음
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * 낙찰 상태 표시 컴포넌트
 */
function WinningStatusDisplay({ data, isNoShow }) {
  const statusLabels = {
    PENDING_RESPONSE: '응답 대기',
    RESPONDED: '응답 완료',
    NO_SHOW: '노쇼',
    FAILED: '실패',
    STANDBY: '대기',
  };

  const tradeStatusLabels = {
    AWAITING_METHOD_SELECTION: '방식 선택 대기',
    AWAITING_ARRANGEMENT: '조율 중',
    ARRANGED: '조율 완료',
    COMPLETED: '완료',
    CANCELLED: '취소',
  };

  const formatPrice = (price) => `${price?.toLocaleString()}원`;

  return (
    <div className="space-y-4">
      {/* 노쇼 처리 결과 */}
      {isNoShow && data.afterFirstWinningStatus && (
        <div className="bg-red-50 rounded-lg p-3 border border-red-200">
          <p className="text-sm font-semibold text-red-800 mb-1">1순위 노쇼 처리됨</p>
          {data.afterSecondWinningStatus === 'PENDING_RESPONSE' && (
            <p className="text-sm text-green-700">→ 2순위에게 승계됨 (응답 대기 중)</p>
          )}
          {data.afterTradeStatus === 'CANCELLED' && !data.afterSecondWinningStatus && (
            <p className="text-sm text-gray-600">→ 2순위 없음, 거래 취소</p>
          )}
        </div>
      )}

      {/* 1순위 정보 */}
      {data.firstWinning && (
        <div className="bg-violet-50 rounded-lg p-3">
          <p className="text-xs font-semibold text-violet-600 mb-2">1순위 낙찰자</p>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="text-gray-500">사용자:</span>{' '}
              <span className="font-medium">#{data.firstWinning.bidderId}</span>
            </div>
            <div>
              <span className="text-gray-500">입찰가:</span>{' '}
              <span className="font-medium">{formatPrice(data.firstWinning.bidAmount)}</span>
            </div>
            <div>
              <span className="text-gray-500">상태:</span>{' '}
              <span
                className={`font-medium ${data.firstWinning.status === 'NO_SHOW' ? 'text-red-600' : ''}`}
              >
                {statusLabels[data.firstWinning.status] || data.firstWinning.status}
              </span>
            </div>
            {data.firstWinning.responseDeadline &&
              data.firstWinning.responseDeadline !== 'null' && (
                <div>
                  <span className="text-gray-500">기한:</span>{' '}
                  <span className="font-medium text-xs">
                    {data.firstWinning.responseDeadline.replace('T', ' ').slice(0, 16)}
                  </span>
                </div>
              )}
          </div>
        </div>
      )}

      {/* 2순위 정보 */}
      {data.secondWinning && (
        <div className="bg-amber-50 rounded-lg p-3">
          <p className="text-xs font-semibold text-amber-600 mb-2">2순위 후보</p>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="text-gray-500">사용자:</span>{' '}
              <span className="font-medium">#{data.secondWinning.bidderId}</span>
            </div>
            <div>
              <span className="text-gray-500">입찰가:</span>{' '}
              <span className="font-medium">{formatPrice(data.secondWinning.bidAmount)}</span>
            </div>
            <div>
              <span className="text-gray-500">상태:</span>{' '}
              <span className="font-medium">
                {statusLabels[data.secondWinning.status] || data.secondWinning.status}
              </span>
            </div>
            {data.secondWinning.isEligibleForTransfer !== undefined && (
              <div>
                <span className="text-gray-500">승계 가능:</span>{' '}
                <span
                  className={`font-medium ${data.secondWinning.isEligibleForTransfer ? 'text-green-600' : 'text-red-600'}`}
                >
                  {data.secondWinning.isEligibleForTransfer ? '예 (90% 이상)' : '아니오'}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Trade 정보 */}
      {data.trade && (
        <div className="bg-blue-50 rounded-lg p-3">
          <p className="text-xs font-semibold text-blue-600 mb-2">거래 정보</p>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="text-gray-500">구매자:</span>{' '}
              <span className="font-medium">#{data.trade.buyerId}</span>
            </div>
            <div>
              <span className="text-gray-500">금액:</span>{' '}
              <span className="font-medium">{formatPrice(data.trade.finalPrice)}</span>
            </div>
            <div>
              <span className="text-gray-500">상태:</span>{' '}
              <span
                className={`font-medium ${data.trade.status === 'CANCELLED' ? 'text-red-600' : ''}`}
              >
                {tradeStatusLabels[data.trade.status] || data.trade.status}
              </span>
            </div>
            <div>
              <span className="text-gray-500">방식:</span>{' '}
              <span className="font-medium">
                {(() => {
                  if (data.trade.method === 'DIRECT') return '직거래';
                  if (data.trade.method === 'DELIVERY') return '택배';
                  return data.trade.method || '미정';
                })()}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* 노쇼 처리 후 변경된 Trade 정보 */}
      {isNoShow && data.afterTradeBuyerId && (
        <div className="bg-green-50 rounded-lg p-3 border border-green-200">
          <p className="text-xs font-semibold text-green-600 mb-1">거래 구매자 변경됨</p>
          <p className="text-sm">
            새 구매자: <span className="font-medium">#{data.afterTradeBuyerId}</span>
          </p>
        </div>
      )}
    </div>
  );
}

import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiRequest } from '../api/client';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import Spinner from '../components/Spinner';
import { formatPrice } from '../utils/formatters';

/**
 * 경매 상태 뱃지
 * @param status 경매 상태
 * @param winnerRank 낙찰 순위 (1: 1순위, 2: 2순위, null: 미낙찰)
 * @param winningStatus Winning 상태 (PENDING_RESPONSE, RESPONDED, NO_SHOW, FAILED, STANDBY)
 */
function AuctionStatusBadge({ status, winnerRank, winningStatus }) {
  // 경매 종료 시 낙찰 순위와 상태에 따라 다른 텍스트 표시
  if (status === 'ENDED') {
    // 노쇼 처리된 경우
    if (winningStatus === 'NO_SHOW') {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-red-100 text-red-700">
          노쇼
        </span>
      );
    }
    // 실패 (2순위 승계 후 미응답 등)
    if (winningStatus === 'FAILED') {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-gray-100 text-gray-500">
          실패
        </span>
      );
    }
    // 2순위 대기 중
    if (winnerRank === 2 && winningStatus === 'STANDBY') {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-100 text-amber-700">
          2순위 대기
        </span>
      );
    }
    // 2순위 승계됨 (응답 대기)
    if (winnerRank === 2 && winningStatus === 'PENDING_RESPONSE') {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-violet-100 text-violet-700">
          낙찰 (승계)
        </span>
      );
    }
    // 1순위 낙찰
    if (winnerRank === 1) {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-violet-100 text-violet-700">
          낙찰
        </span>
      );
    }
    // 2순위 (응답 완료)
    if (winnerRank === 2) {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-violet-100 text-violet-700">
          낙찰
        </span>
      );
    }
    // 미낙찰
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-gray-100 text-gray-500">
        종료
      </span>
    );
  }

  const statusConfig = {
    BIDDING: { text: '진행중', color: 'bg-green-100 text-green-700' },
    INSTANT_BUY_PENDING: { text: '즉구 대기', color: 'bg-blue-100 text-blue-700' },
    FAILED: { text: '유찰', color: 'bg-gray-100 text-gray-500' },
    CANCELLED: { text: '취소', color: 'bg-red-100 text-red-600' },
  };

  const config = statusConfig[status] || { text: status, color: 'bg-gray-100 text-gray-500' };

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${config.color}`}
    >
      {config.text}
    </span>
  );
}

/**
 * 거래 상태 뱃지
 */
function TradeStatusBadge({ status }) {
  const statusConfig = {
    AWAITING_METHOD_SELECTION: { text: '방식 선택', color: 'bg-yellow-100 text-yellow-700' },
    AWAITING_ARRANGEMENT: { text: '조율 중', color: 'bg-blue-100 text-blue-700' },
    ARRANGED: { text: '조율 완료', color: 'bg-purple-100 text-purple-700' },
    COMPLETED: { text: '거래 완료', color: 'bg-green-100 text-green-700' },
    CANCELLED: { text: '취소', color: 'bg-gray-100 text-gray-500' },
  };

  const config = statusConfig[status] || { text: status, color: 'bg-gray-100 text-gray-500' };

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${config.color}`}
    >
      {config.text}
    </span>
  );
}

/**
 * 거래 방식 뱃지
 */
function TradeMethodBadge({ method }) {
  if (!method) return null;

  const methodConfig = {
    DIRECT: { text: '직거래', color: 'text-orange-600' },
    DELIVERY: { text: '택배', color: 'text-blue-600' },
  };

  const config = methodConfig[method] || { text: method, color: 'text-gray-500' };

  return <span className={`text-[11px] font-medium ${config.color}`}>{config.text}</span>;
}

/**
 * 내 거래 페이지
 * 구매/판매 탭으로 구분하여 경매 및 거래 목록을 표시
 */
export default function TradeListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('buy');
  const [trades, setTrades] = useState([]);
  const [tradesLoading, setTradesLoading] = useState(true);

  const observerRef = useRef(null);
  const loadMoreRef = useRef(null);

  // 판매 목록 (내가 등록한 경매)
  const {
    items: salesItems,
    isLoading: salesLoading,
    isLoadingMore: salesLoadingMore,
    hasMore: salesHasMore,
    loadMore: salesLoadMore,
  } = useInfiniteScroll('/users/me/auctions');

  // 구매 목록 (내가 입찰한 경매)
  const {
    items: bidsItems,
    isLoading: bidsLoading,
    isLoadingMore: bidsLoadingMore,
    hasMore: bidsHasMore,
    loadMore: bidsLoadMore,
  } = useInfiniteScroll('/users/me/bids');

  // 내 거래 목록 로드 (판매자 또는 구매자로 참여한 Trade)
  // 인증은 JWT 토큰으로 처리됨
  useEffect(() => {
    const loadTrades = async () => {
      if (!user?.userId) {
        setTradesLoading(false);
        return;
      }
      setTradesLoading(true);
      try {
        const data = await apiRequest('/trades/my');
        setTrades(data || []);
      } catch {
        setTrades([]);
      } finally {
        setTradesLoading(false);
      }
    };
    loadTrades();
  }, [user?.userId]);

  // 무한스크롤 Observer
  useEffect(() => {
    if (observerRef.current) observerRef.current.disconnect();

    const currentLoadMore = activeTab === 'sell' ? salesLoadMore : bidsLoadMore;
    const currentHasMore = activeTab === 'sell' ? salesHasMore : bidsHasMore;

    if (!currentHasMore) return;

    observerRef.current = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          currentLoadMore();
        }
      },
      { threshold: 0.1 },
    );

    if (loadMoreRef.current) {
      observerRef.current.observe(loadMoreRef.current);
    }

    return () => {
      if (observerRef.current) observerRef.current.disconnect();
    };
  }, [activeTab, salesLoadMore, bidsLoadMore, salesHasMore, bidsHasMore]);

  // 경매에 연결된 Trade 찾기
  const findTradeForAuction = (auctionId) => {
    return trades.find((t) => t.auctionId === auctionId);
  };

  // 경매가 종료되고 조율이 필요한지 확인
  const needsAction = (auction, trade) => {
    if (!trade) return false;
    return trade.status !== 'COMPLETED' && trade.status !== 'CANCELLED';
  };

  const isLoading = activeTab === 'sell' ? salesLoading : bidsLoading;
  const isLoadingMore = activeTab === 'sell' ? salesLoadingMore : bidsLoadingMore;
  const items = activeTab === 'sell' ? salesItems : bidsItems;

  return (
    <div className="max-w-2xl mx-auto animate-fade-in">
      {/* 페이지 헤더 */}
      <div className="mb-6">
        <h1 className="text-[22px] font-bold text-gray-900 tracking-tight">내 거래</h1>
        <p className="text-[13px] text-gray-400 mt-0.5">경매 및 거래 현황을 확인하세요</p>
      </div>

      {/* 탭 */}
      <div className="bg-white rounded-2xl ring-1 ring-black/[0.04] overflow-hidden">
        <div className="flex border-b border-gray-100">
          <button
            type="button"
            onClick={() => setActiveTab('buy')}
            className={`flex-1 py-3.5 text-sm font-semibold text-center transition-colors ${
              activeTab === 'buy'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            구매
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('sell')}
            className={`flex-1 py-3.5 text-sm font-semibold text-center transition-colors ${
              activeTab === 'sell'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            판매
          </button>
        </div>

        {/* 콘텐츠 */}
        <div className="divide-y divide-gray-50">
          {(isLoading || tradesLoading) && (
            <div className="flex justify-center py-12">
              <Spinner />
            </div>
          )}
          {!isLoading && !tradesLoading && items.length === 0 && (
            <div className="text-center py-12">
              <div className="text-4xl mb-3">{activeTab === 'buy' ? '🛒' : '📦'}</div>
              <p className="text-gray-500 text-[14px]">
                {activeTab === 'buy' ? '입찰한 경매가 없습니다.' : '등록한 경매가 없습니다.'}
              </p>
              <Link
                to={activeTab === 'buy' ? '/' : '/auctions/create'}
                className="inline-block mt-4 px-4 py-2 bg-gray-900 text-white text-[13px] font-semibold rounded-lg hover:bg-gray-800 transition-colors"
              >
                {activeTab === 'buy' ? '경매 둘러보기' : '경매 등록하기'}
              </Link>
            </div>
          )}
          {!isLoading &&
            !tradesLoading &&
            items.length > 0 &&
            items.map((item) => {
              const auctionId = item.id || item.auctionId;
              const trade = findTradeForAuction(auctionId);
              const hasAction = needsAction(item, trade);

              return (
                <button
                  key={auctionId}
                  type="button"
                  onClick={() => {
                    // Trade가 있고 조율이 필요하면 거래 상세로, 아니면 경매 상세로
                    if (trade && hasAction) {
                      navigate(`/trades/${trade.id}`);
                    } else {
                      navigate(`/auctions/${auctionId}`);
                    }
                  }}
                  className="w-full flex items-center justify-between px-5 py-4 hover:bg-gray-50/80 transition-colors text-left"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <AuctionStatusBadge
                        status={item.status}
                        winnerRank={item.winnerRank}
                        winningStatus={item.winningStatus}
                      />
                      {trade && (
                        <>
                          <TradeStatusBadge status={trade.status} />
                          <TradeMethodBadge method={trade.method} />
                        </>
                      )}
                      {hasAction && (
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded bg-red-500 text-white text-[9px] font-bold">
                          조율 필요
                        </span>
                      )}
                    </div>
                    <p className="text-[14px] font-semibold text-gray-900 truncate">{item.title}</p>
                    <p className="text-[12px] text-gray-400 mt-0.5">
                      {activeTab === 'buy' && item.myHighestBid && (
                        <span>내 입찰가: {formatPrice(item.myHighestBid)} · </span>
                      )}
                      현재가: {formatPrice(item.currentPrice || item.startPrice)}
                    </p>
                  </div>
                  <div className="flex-shrink-0 ml-3">
                    <svg
                      className="w-5 h-5 text-gray-300"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 5l7 7-7 7"
                      />
                    </svg>
                  </div>
                </button>
              );
            })}
        </div>

        {isLoadingMore && (
          <div className="flex justify-center py-4">
            <Spinner />
          </div>
        )}

        {/* 무한스크롤 트리거 */}
        <div ref={loadMoreRef} className="h-1" />
      </div>
    </div>
  );
}

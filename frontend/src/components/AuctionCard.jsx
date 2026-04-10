import { Link } from 'react-router-dom';
import StatusBadge from './StatusBadge';
import { formatPrice } from '../utils/formatters';
import { getServerTime } from '../api/client';

/**
 * 경매 목록 카드 컴포넌트
 * 호버 시 리프트 + 그림자 + 이미지 줌 효과
 */
export default function AuctionCard({ auction }) {
  const {
    id,
    title,
    currentPrice,
    startPrice,
    status,
    totalBidCount,
    scheduledEndTime,
    imageUrls,
    thumbnailUrl: rawThumbnailUrl,
  } = auction;

  // 목록 API는 thumbnailUrl(문자열), 상세 API는 imageUrls(배열) 반환
  const thumbnailUrl = imageUrls?.[0] ?? rawThumbnailUrl;

  // 마감 임박 여부: 진행중이고 종료까지 10분 이내
  const isClosingSoon = (() => {
    if (status !== 'BIDDING' && status !== 'INSTANT_BUY_PENDING') return false;
    if (!scheduledEndTime) return false;
    const endTime = new Date(scheduledEndTime).getTime();
    if (Number.isNaN(endTime)) return false;
    const remaining = endTime - getServerTime().getTime();
    return remaining > 0 && remaining <= 10 * 60 * 1000;
  })();

  return (
    <Link
      to={`/auctions/${id}`}
      className="group block bg-white rounded-2xl overflow-hidden card-hover ring-1 ring-black/[0.04]"
    >
      {/* 썸네일 영역 */}
      <div className="relative aspect-[4/3] bg-gradient-to-br from-gray-100 to-gray-50 overflow-hidden">
        {thumbnailUrl ? (
          <img
            src={thumbnailUrl}
            alt={title}
            className="w-full h-full object-cover transition-transform duration-500 ease-out group-hover:scale-105"
            loading="lazy"
            width={400}
            height={300}
          />
        ) : (
          <div className="flex flex-col items-center justify-center w-full h-full">
            <div className="w-14 h-14 rounded-2xl bg-gray-100 flex items-center justify-center mb-2">
              <svg
                className="w-7 h-7 text-gray-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                />
              </svg>
            </div>
            <span className="text-[11px] text-gray-300 font-medium">이미지 없음</span>
          </div>
        )}

        {/* 상태 뱃지 */}
        <div className="absolute top-3 left-3">
          <StatusBadge status={status} />
        </div>

        {/* 마감 임박 뱃지 (종료 10분 이내) */}
        {isClosingSoon ? (
          <div className="absolute bottom-3 right-3">
            <span className="inline-flex items-center gap-1 px-2.5 py-1 bg-red-500/90 text-white text-[11px] font-bold rounded-lg shadow-sm backdrop-blur-sm">
              <svg
                className="w-3 h-3"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2.5}
                  d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              마감 임박
            </span>
          </div>
        ) : null}
      </div>

      {/* 정보 영역 */}
      <div className="p-4 pb-4.5">
        {/* 제목 */}
        <h3 className="text-[13px] font-semibold text-gray-800 truncate mb-3 group-hover:text-blue-600 transition-colors duration-200">
          {title}
        </h3>

        {/* 가격 + 입찰수 */}
        <div className="flex items-end justify-between">
          <div>
            <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wider mb-0.5">
              현재가
            </p>
            <p className="text-[17px] font-bold text-gray-900 tabular-nums leading-tight">
              {formatPrice(currentPrice ?? startPrice)}
            </p>
          </div>
          {totalBidCount > 0 ? (
            <div className="flex items-center gap-1 px-2 py-1 bg-gray-50 rounded-lg">
              <svg
                className="w-3 h-3 text-gray-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
                />
              </svg>
              <span className="text-[11px] font-semibold text-gray-500 tabular-nums">
                {totalBidCount}회
              </span>
            </div>
          ) : null}
        </div>
      </div>
    </Link>
  );
}

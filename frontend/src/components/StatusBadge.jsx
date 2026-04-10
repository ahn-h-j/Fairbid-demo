import { STATUSES } from '../utils/constants';

/** 상태별 스타일 설정 */
const STATUS_STYLES = {
  BIDDING: {
    bg: 'bg-emerald-50',
    text: 'text-emerald-700',
    dot: 'bg-emerald-500',
    ring: 'ring-emerald-500/20',
  },
  INSTANT_BUY_PENDING: {
    bg: 'bg-blue-50',
    text: 'text-blue-700',
    dot: 'bg-blue-500',
    ring: 'ring-blue-500/20',
  },
  ENDED: {
    bg: 'bg-gray-100',
    text: 'text-gray-600',
    dot: 'bg-gray-400',
    ring: 'ring-gray-400/20',
  },
  CLOSED: {
    bg: 'bg-gray-100',
    text: 'text-gray-600',
    dot: 'bg-gray-400',
    ring: 'ring-gray-400/20',
  },
  FAILED: {
    bg: 'bg-red-50',
    text: 'text-red-700',
    dot: 'bg-red-500',
    ring: 'ring-red-500/20',
  },
  CANCELLED: {
    bg: 'bg-gray-50',
    text: 'text-gray-500',
    dot: 'bg-gray-300',
    ring: 'ring-gray-300/20',
  },
};

const DEFAULT_STYLE = {
  bg: 'bg-gray-100',
  text: 'text-gray-600',
  dot: 'bg-gray-400',
  ring: 'ring-gray-400/20',
};

/**
 * 경매 상태 뱃지 컴포넌트
 * 도트 인디케이터 + 라벨, 진행중일 때 펄스 + 글로우 효과
 */
export default function StatusBadge({ status }) {
  const config = STATUSES[status] || { label: status };
  const style = STATUS_STYLES[status] || DEFAULT_STYLE;
  const isLive = status === 'BIDDING';

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold tracking-wide ${style.bg} ${style.text} ring-1 ${style.ring} ${isLive ? 'animate-glow-green' : ''}`}
    >
      <span className="relative flex h-1.5 w-1.5">
        {isLive ? (
          <span
            className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-60 ${style.dot}`}
          />
        ) : null}
        <span className={`relative inline-flex rounded-full h-1.5 w-1.5 ${style.dot}`} />
      </span>
      {config.label}
    </span>
  );
}
